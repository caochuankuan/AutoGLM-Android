package com.yifeng.autogml.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset

import android.net.Uri
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import com.yifeng.autogml.R

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.util.Log
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import java.util.*

fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    
    // 记录消息列表的状态，用于判断是否有新消息
    var lastMessageCount by remember { mutableStateOf(0) }
    var lastMessageId by remember { mutableStateOf("") }

    // Voice Review State
    var showVoiceReview by remember { mutableStateOf(false) }
    var voiceResultText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()

    val scope = rememberCoroutineScope()
    
    // TTS initialization at ChatScreen level
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isTtsReady = true
            }
        }
        
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Check service status when app resumes (e.g. returning from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkServiceStatus()
                viewModel.checkOverlayPermission(context)
                // 回到应用时滚动到底部
                scope.launch {
                    if (uiState.messages.isNotEmpty()) {
                        listState.animateScrollToItem(uiState.messages.size - 1)
                        lastMessageId = uiState.messages.lastOrNull()?.id ?: ""
                        lastMessageCount = uiState.messages.size
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initial check on composition and scroll to bottom
    LaunchedEffect(Unit) {
        viewModel.checkServiceStatus()
        viewModel.checkOverlayPermission(context)
        // 首次进入时滚动到底部
        if (uiState.messages.isNotEmpty()) {
            listState.scrollToItem(uiState.messages.size - 1)
            lastMessageId = uiState.messages.lastOrNull()?.id ?: ""
            lastMessageCount = uiState.messages.size
        }
    }
    
    // 当有新消息时自动滚动到底部（不包括加载历史消息）
    LaunchedEffect(uiState.messages.lastOrNull()?.id, uiState.messages.size) {
        val currentLastMessageId = uiState.messages.lastOrNull()?.id ?: ""
        val currentMessageCount = uiState.messages.size
        
        Log.d("ChatScreen", "LaunchedEffect triggered - currentLastMessageId: $currentLastMessageId, lastMessageId: $lastMessageId, currentCount: $currentMessageCount, lastCount: $lastMessageCount, isLoadingHistory: ${uiState.isLoadingHistory}")
        
        // 只有在最后一条消息ID改变时才滚动（表示有新消息添加到末尾）
        // 如果只是消息数量增加但最后消息ID没变，说明是在开头添加了历史消息
        if (uiState.messages.isNotEmpty() && 
            currentLastMessageId != lastMessageId && 
            currentLastMessageId.isNotEmpty() &&
            !uiState.isLoadingHistory &&
            lastMessageId.isNotEmpty()) { // 确保不是初始加载
            // 有新消息添加到末尾，滚动到底部
            Log.d("ChatScreen", "Scrolling to bottom due to new message at end")
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
        
        lastMessageId = currentLastMessageId
        lastMessageCount = currentMessageCount
    }
    
    // 当切换会话时滚动到底部
    LaunchedEffect(uiState.currentSession?.id) {
        if (uiState.messages.isNotEmpty()) {
            listState.scrollToItem(uiState.messages.size - 1)
            // 更新最后消息ID和数量
            lastMessageId = uiState.messages.lastOrNull()?.id ?: ""
            lastMessageCount = uiState.messages.size
        }
    }

    val view = LocalView.current
    val activity = view.context as Activity
    val window = activity.window

    SideEffect {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            scope.launch {
                                // 点击标题滚动到最新消息（底部）
                                if (uiState.messages.isNotEmpty()) {
                                    listState.animateScrollToItem(uiState.messages.size - 1)
                                }
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.chat_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.Black
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(id = R.drawable.settings),
                            contentDescription = stringResource(R.string.settings_title),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    var showClearDialog by remember { mutableStateOf(false) }

                    if (showClearDialog) {
                        Dialog(
                            onDismissRequest = { showClearDialog = false }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.clear_chat_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.Black,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                Text(
                                    text = stringResource(R.string.clear_chat_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showClearDialog = false },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.Gray
                                        ),
                                        border = BorderStroke(1.dp, Color.Gray)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.cancel),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.clearMessages()
                                            showClearDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF5722)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.confirm),
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.delete),
                            contentDescription = stringResource(R.string.clear_chat),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xffededed))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {

                // Message List Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                        reverseLayout = false,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 加载更多历史消息的指示器
                        if (uiState.hasMoreMessages) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isLoadingHistory) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else {
                                        TextButton(
                                            onClick = { viewModel.loadMoreMessages() }
                                        ) {
                                            Text("加载更多历史消息")
                                        }
                                    }
                                }
                            }
                        }
                        
                        items(uiState.messages) { message ->
                            MessageItem(
                                message = message,
                                tts = tts,
                                isTtsReady = isTtsReady,
                                isTtsEnabled = uiState.isTtsEnabled
                            )
                        }
                    }

                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                // Input Area (Bottom)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xfff7f7f7))
                        .padding(bottom = 16.dp),
                ) {
                    // 进入动画配置（较慢，更平滑）
                    val enterFloatAnimationSpec = tween<Float>(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                    
                    val enterSizeAnimationSpec = tween<IntSize>(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                    
                    val enterOffsetAnimationSpec = tween<IntOffset>(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                    
                    // 退出动画配置（更快，立即响应）
                    val exitFloatAnimationSpec = tween<Float>(
                        durationMillis = 150,
                        easing = LinearEasing
                    )
                    
                    val exitSizeAnimationSpec = tween<IntSize>(
                        durationMillis = 150,
                        easing = LinearEasing
                    )
                    
                    val exitOffsetAnimationSpec = tween<IntOffset>(
                        durationMillis = 150,
                        easing = LinearEasing
                    )
                    
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .animateContentSize(animationSpec = enterSizeAnimationSpec),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacer(modifier = Modifier.width(1.dp))
                        
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    stringResource(R.string.input_placeholder),
                                    color = Color.Gray
                                )
                            },
                            shape = MaterialTheme.shapes.medium,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                disabledContainerColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            maxLines = 3
                        )

                        // Send Button with Animation
                        AnimatedVisibility(
                            visible = !uiState.isLoading && inputText.isNotBlank(),
                            enter = slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = enterOffsetAnimationSpec
                            ) + fadeIn(animationSpec = enterFloatAnimationSpec) + scaleIn(
                                initialScale = 0.8f,
                                animationSpec = enterFloatAnimationSpec
                            ),
                            exit = slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = exitOffsetAnimationSpec
                            ) + fadeOut(animationSpec = exitFloatAnimationSpec) + scaleOut(
                                targetScale = 0.8f,
                                animationSpec = exitFloatAnimationSpec
                            )
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""

                                    // 播放欢迎语音
                                    if (isTtsReady && tts != null && uiState.isTtsEnabled) {
                                        tts!!.stop()
                                        tts!!.speak(
                                            "欢迎使用遇见手机助手，马上开始为你执行",
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            null
                                        )
                                    }
                                },
                                enabled = inputText.isNotBlank()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.send),
                                    contentDescription = stringResource(R.string.send_button),
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        // Spacer when send button is not visible to maintain consistent spacing
                        if (uiState.isLoading || inputText.isBlank()) {
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }
            }

            // Permission Status Overlay
            if (uiState.error != null || uiState.missingAccessibilityService || uiState.missingOverlayPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 12.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 错误信息
                            if (uiState.error != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = uiState.error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.clearError() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text(stringResource(R.string.close), color = Color.White)
                                }
                            } else {
                                // 权限说明
                                Text(
                                    text = "应用需要以下权限才能正常工作：",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                // 无障碍服务权限
                                PermissionRow(
                                    title = "无障碍服务",
                                    description = "用于自动化操作和屏幕内容识别",
                                    isGranted = !uiState.missingAccessibilityService,
                                    onActionClick = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(intent)
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // 悬浮窗权限
                                PermissionRow(
                                    title = "悬浮窗权限",
                                    description = "用于显示悬浮控制面板和操作提示",
                                    isGranted = !uiState.missingOverlayPermission,
                                    onActionClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        context.startActivity(intent)
                                    }
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = "Tips: 在MIUI/HyperOS上，电池优化改为无限制，开启自启动，可避免每次打开app 都需要授权无障碍服务",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(
    message: UiMessage,
    tts: TextToSpeech?,
    isTtsReady: Boolean,
    isTtsEnabled: Boolean = true
) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) Color(0xFF07C160) else Color.White
    val contentColor = Color.Black

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = if (isUser)
                MaterialTheme.shapes.large.copy(
                    bottomEnd = androidx.compose.foundation.shape.CornerSize(
                        0.dp
                    )
                )
            else
                MaterialTheme.shapes.large.copy(
                    bottomStart = androidx.compose.foundation.shape.CornerSize(
                        0.dp
                    )
                ),
            color = containerColor,
            shadowElevation = if (isUser) 0.dp else 0.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // Single tap to play TTS - stop current and play new immediately
                            if (isTtsReady && tts != null && isTtsEnabled) {
                                tts.stop() // 立即停止当前播放
                                tts.speak(
                                    message.content,
                                    TextToSpeech.QUEUE_FLUSH, // 清空队列并立即播放
                                    null,
                                    null
                                )
                            }
                        },
                        onLongPress = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            Toast.makeText(
                                context,
                                context.getString(R.string.text_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.image != null) {
                    // Image display logic if needed
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String? = null,
    isGranted: Boolean,
    onActionClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black
                )

                // 描述文本
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            
            // 状态或按钮
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isGranted) {
                    Text(
                        text = "已授权",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                } else {
                    Button(
                        onClick = onActionClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "去设置",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}