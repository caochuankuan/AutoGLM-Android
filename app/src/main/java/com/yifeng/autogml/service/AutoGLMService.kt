package com.yifeng.autogml.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.yifeng.autogml.FloatingWindowController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AutoGLMService : AccessibilityService() {

    companion object {
        private val _serviceInstance = MutableStateFlow<AutoGLMService?>(null)
        val serviceInstance = _serviceInstance.asStateFlow()

        fun getInstance(): AutoGLMService? = _serviceInstance.value
    }

    private val _currentApp = MutableStateFlow<String?>(null)
    val currentApp = _currentApp.asStateFlow()

    private var floatingWindowController: FloatingWindowController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        _serviceInstance.value = this
        Log.d("AutoGLMService", "Service connected")

        // Initialize Floating Window Controller
        floatingWindowController = FloatingWindowController(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        floatingWindowController?.hide()
        floatingWindowController = null
        _serviceInstance.value = null
        return super.onUnbind(intent)
    }

    fun showFloatingWindow(onStop: () -> Unit) {
        Handler(Looper.getMainLooper()).post {
            if (floatingWindowController == null) {
                floatingWindowController = FloatingWindowController(this)
            }
            floatingWindowController?.show(onStop)
        }
    }

    fun hideFloatingWindow() {
        Handler(Looper.getMainLooper()).post {
            floatingWindowController?.hide()
        }
    }

    fun updateFloatingStatus(text: String) {
        Handler(Looper.getMainLooper()).post {
            // 获取TTS设置
            val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val isTtsEnabled = prefs.getBoolean("is_tts_enabled", true)
            floatingWindowController?.updateStatus(text, isTtsEnabled)
        }
    }

    fun setTaskRunning(running: Boolean) {
        Handler(Looper.getMainLooper()).post {
            floatingWindowController?.setTaskRunning(running)
        }
    }

    fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.let {
            _currentApp.value = it.toString()
        }
    }

    override fun onInterrupt() {
        Log.w("AutoGLMService", "Service interrupted")
    }

    fun getScreenHeight(): Int {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        return windowManager.currentWindowMetrics.bounds.height()
    }

    fun getScreenWidth(): Int {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        return windowManager.currentWindowMetrics.bounds.width()
    }

    private fun showGestureAnimation(startX: Float, startY: Float, endX: Float? = null, endY: Float? = null, duration: Long = 1000) {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val view = object : View(this) {
            private val paint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.FILL
                alpha = 150
            }

            // For swipe trail
            private val trailPaint = Paint().apply {
                color = Color.YELLOW
                style = Paint.Style.STROKE
                strokeWidth = 20f
                alpha = 100
                strokeCap = Paint.Cap.ROUND
            }

            private var currentX = startX
            private var currentY = startY
            private var currentRadius = 30f

            init {
                if (endX != null && endY != null) {
                    // Swipe Animation
                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.duration = duration
                    animator.addUpdateListener { animation ->
                        val fraction = animation.animatedValue as Float
                        currentX = startX + (endX - startX) * fraction
                        currentY = startY + (endY - startY) * fraction
                        invalidate()
                    }
                    animator.start()
                } else {
                    // Tap Animation: Pulse effect
                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.duration = duration
                    animator.addUpdateListener { animation ->
                        val fraction = animation.animatedValue as Float
                        // Expand and fade
                        currentRadius = 30f + 30f * fraction
                        paint.alpha = (150 * (1 - fraction)).toInt()
                        invalidate()
                    }
                    animator.start()
                }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if (endX != null && endY != null) {
                    // Draw trail from start to current
                    canvas.drawLine(startX, startY, currentX, currentY, trailPaint)
                }
                canvas.drawCircle(currentX, currentY, currentRadius, paint)
            }
        }

        val params = WindowManager.LayoutParams(
            getScreenWidth(),
            getScreenHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0

        try {
            windowManager.addView(view, params)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    // Ignore
                }
            }, duration + 200)
        } catch (e: Exception) {
            Log.e("AutoGLMService", "Failed to show gesture animation", e)
        }
    }

    suspend fun takeScreenshot(): Bitmap? {
        // 1. Hide Floating Window
        Handler(Looper.getMainLooper()).post {
            floatingWindowController?.setScreenshotMode(true)
        }

        // 2. Wait for UI update (small delay)
        delay(150)

        // 3. Take Screenshot
        val screenshot = suspendCoroutine<Bitmap?> { continuation ->
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val displayId = windowManager.defaultDisplay.displayId

            takeScreenshot(
                displayId,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            // Copy to software bitmap for processing
                            val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            screenshot.hardwareBuffer.close()
                            continuation.resume(softwareBitmap)
                        } catch (e: Exception) {
                            Log.e("AutoGLMService", "Error processing screenshot", e)
                            continuation.resume(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val errorMsg = when (errorCode) {
                            ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "INTERNAL_ERROR"
                            ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "NO_ACCESSIBILITY_ACCESS"
                            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "INTERVAL_TIME_SHORT"
                            ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "INVALID_DISPLAY"
                            else -> "UNKNOWN($errorCode)"
                        }
                        Log.e("AutoGLMService", "Screenshot failed: $errorMsg")
                        continuation.resume(null)
                    }
                }
            )
        }

        // 4. Restore Floating Window
        Handler(Looper.getMainLooper()).post {
            floatingWindowController?.setScreenshotMode(false)
        }

        return screenshot
    }

    private fun setInteractionHidden(hidden: Boolean) {
        if (hidden) {
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                floatingWindowController?.setScreenshotMode(true)
                latch.countDown()
            }
            try {
                latch.await(200, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            Handler(Looper.getMainLooper()).post {
                floatingWindowController?.setScreenshotMode(false)
            }
        }
    }

    fun performTap(x: Float, y: Float): Boolean {
        val serviceWidth = getScreenWidth()
        val serviceHeight = getScreenHeight()
        Log.d("AutoGLMService", "performTap: Request($x, $y) vs Screen($serviceWidth, $serviceHeight)")

        if (x < 0 || x > serviceWidth || y < 0 || y > serviceHeight) {
            Log.w("AutoGLMService", "Tap coordinates ($x, $y) out of bounds")
            return false
        }

        Log.d("AutoGLMService", "Dispatching Gesture: Tap at ($x, $y)")

        // Check overlap and move if necessary
        Handler(Looper.getMainLooper()).post {
             floatingWindowController?.avoidArea(x, y)
        }

        // Hide floating window to prevent blocking the tap
        setInteractionHidden(true)

        // Show visual indicator on UI thread
        Handler(Looper.getMainLooper()).post {
            showGestureAnimation(x, y)
        }

        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val builder = GestureDescription.Builder()
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        val result = dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("AutoGLMService", "Gesture Completed: Tap at ($x, $y)")
                setInteractionHidden(false)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w("AutoGLMService", "Gesture Cancelled: Tap at ($x, $y)")
                setInteractionHidden(false)
            }
        }, null)

        if (!result) {
            setInteractionHidden(false)
        }

        Log.d("AutoGLMService", "dispatchGesture result: $result")
        return result
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 1000): Boolean {
        // Check overlap and move if necessary (using start position)
        Handler(Looper.getMainLooper()).post {
             floatingWindowController?.avoidArea(startX, startY)
        }

        // Show visual indicator on UI thread
        Handler(Looper.getMainLooper()).post {
             showGestureAnimation(startX, startY, endX, endY, duration)
        }

        setInteractionHidden(true)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val builder = GestureDescription.Builder()
        // Use a fixed shorter duration (500ms) for the actual gesture to ensure it registers as a fling/scroll
        // The animation will play slower (duration) to be visible to the user
        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 500))

        val result = dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                setInteractionHidden(false)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                setInteractionHidden(false)
            }
        }, null)

        if (!result) {
            setInteractionHidden(false)
        }

        return result
    }

    fun performLongPress(x: Float, y: Float, duration: Long = 1000): Boolean {
        Log.d("AutoGLMService", "Dispatching Gesture: Long Press at ($x, $y)")
        Handler(Looper.getMainLooper()).post {
            showGestureAnimation(x, y, null, null, duration)
        }
        // Long press is effectively a swipe from x,y to x,y with long duration
        return performSwipe(x, y, x, y, duration)
    }

    fun performGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performGlobalHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
}