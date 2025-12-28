package com.yifeng.autogml

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.OpenInNew
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale

class FloatingWindowController(private val context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private var floatView: ComposeView? = null
    private var isShowing = false
    private lateinit var windowParams: WindowManager.LayoutParams
    
    // State for the UI
    private var _statusText by mutableStateOf("")
    private var _isTaskRunning by mutableStateOf(true)
    private var _onStopClick: (() -> Unit)? = null
    private var _showLatestReply by mutableStateOf(false)
    
    // TTS for status text
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // Lifecycle components required for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    init {
        _statusText = context.getString(R.string.fw_ready)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        // Initialize TTS
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isTtsReady = true
            }
        }
    }

    fun show(onStop: () -> Unit, onGetLatestReply: () -> String = { "" }) {
        if (isShowing) return
        _onStopClick = onStop
        _isTaskRunning = true
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val metrics = context.resources.displayMetrics
        val screenHeight = metrics.heightPixels

        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        windowParams.gravity = Gravity.BOTTOM or Gravity.START
        windowParams.x = 0
        windowParams.y = 20 // Initial position near bottom

        floatView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowController)
            setViewTreeViewModelStoreOwner(this@FloatingWindowController)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowController)
            
            setContent {
                FloatingWindowContent(
                    status = _statusText,
                    isTaskRunning = _isTaskRunning,
                    showLatestReply = _showLatestReply,
                    onGetLatestReply = onGetLatestReply,
                    onStatusClick = { 
                        _showLatestReply = !_showLatestReply
                    },
                    onAction = {
                        if (_isTaskRunning) {
                            _onStopClick?.invoke()
                            // Do not hide immediately, wait for task to finish or user to click close
                        } else {
                            // Launch App
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    hide()
                                } else {
                                    Log.e("FloatingWindow", "Launch intent not found")
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onDrag = { x, y ->
                        windowParams.x += x.roundToInt()
                        windowParams.y -= y.roundToInt()
                        try {
                            windowManager.updateViewLayout(floatView, windowParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }
        }

        try {
            windowManager.addView(floatView, windowParams)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateStatus(status: String, isTtsEnabled: Boolean = true) {
        val oldStatus = _statusText
        _statusText = status
        
        // 当状态文本发生变化时自动播放TTS，但排除"思考中..."，并检查TTS开关
        if (oldStatus != status && status != "思考中..." && isTtsEnabled) {
            // 播报前检查TTS是否可用，不可用就重新初始化
            if (tts == null || !isTtsReady) {
                tts = TextToSpeech(context) { initStatus ->
                    if (initStatus == TextToSpeech.SUCCESS) {
                        tts?.language = Locale.getDefault()
                        isTtsReady = true
                        // 初始化成功后立即播报
                        tts?.speak(status, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                }
            } else {
                // TTS可用，直接播报
                tts?.stop()
                tts?.speak(status, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    fun setTaskRunning(running: Boolean) {
        val wasRunning = _isTaskRunning
        _isTaskRunning = running
        
        // 如果任务从运行状态变为完成状态，触发震动
        if (wasRunning && !running) {
            vibrate()
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0 及以上版本
                val vibrationEffect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                // Android 8.0 以下版本
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (e: Exception) {
            Log.e("FloatingWindow", "Vibration failed", e)
        }
    }

    fun setScreenshotMode(isScreenshotting: Boolean) {
        if (!isShowing || floatView == null) return
        
        try {
            floatView?.visibility = if (isScreenshotting) android.view.View.GONE else android.view.View.VISIBLE
            
            // Update flags to ensure touches pass through when hidden
            if (isScreenshotting) {
                windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windowParams.width = 0
                windowParams.height = 0
            } else {
                windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
                windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            windowManager.updateViewLayout(floatView, windowParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isOccupyingSpace(x: Float, y: Float): Boolean {
        if (!isShowing || floatView == null || floatView?.visibility != android.view.View.VISIBLE) return false
        
        val location = IntArray(2)
        floatView?.getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]
        val width = floatView?.width ?: 0
        val height = floatView?.height ?: 0
        
        return x >= viewX && x <= (viewX + width) && y >= viewY && y <= (viewY + height)
    }

    fun avoidArea(targetX: Float, targetY: Float) {
        if (!isOccupyingSpace(targetX, targetY)) return
        
        val metrics = context.resources.displayMetrics
        val screenHeight = metrics.heightPixels
        
        // If target is in bottom half, move window to top. Else move to bottom.
        val targetInBottomHalf = targetY > screenHeight / 2
        
        val newY = if (targetInBottomHalf) {
            screenHeight - 300 // Top (distance from bottom)
        } else {
            20 // Bottom (distance from bottom)
        }
        
        // Only update if significantly different
        if (kotlin.math.abs(windowParams.y - newY) > 200) {
            windowParams.y = newY
            try {
                windowManager.updateViewLayout(floatView, windowParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun hide() {
        if (!isShowing) return
        
        Log.d("FloatingWindow", "hide() called", Exception("Stack trace"))

        try {
            windowManager.removeView(floatView)
            isShowing = false
            floatView = null
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            
            // Clean up TTS
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}

@Composable
fun FloatingWindowContent(
    status: String,
    isTaskRunning: Boolean,
    showLatestReply: Boolean,
    onGetLatestReply: () -> String,
    onStatusClick: () -> Unit,
    onAction: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    Surface(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.8f),
        border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.2f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box {
                    val displayText = if (showLatestReply) onGetLatestReply() else status
                    // 描边文字 (背景层)
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            drawStyle = Stroke(width = 6f)
                        ),
                        color = Color.Black.copy(alpha = 0.5f),
                        maxLines = if (showLatestReply) 5 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onStatusClick() }
                    )
                    // 正常文字 (前景层)
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = if (showLatestReply) 5 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onStatusClick() }
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box {
                val buttonText = if (isTaskRunning) stringResource(R.string.fw_stop) else stringResource(R.string.fw_return_app)
                val buttonColor = if (isTaskRunning) Color.Gray else Color.Green.copy(alpha = 0.7f)
                
                // 描边文字 (背景层)
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        drawStyle = Stroke(width = 1f)
                    ),
                    color = Color.Black,
                    modifier = Modifier.clickable { onAction() }
                )
                // 正常文字 (前景层)
                Text(
                    text = buttonText,
                    color = buttonColor,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    modifier = Modifier.clickable { onAction() }
                )
            }
        }
    }
}
