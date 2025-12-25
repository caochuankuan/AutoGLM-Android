package com.yifeng.autogml.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

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

import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.util.Log
import android.speech.tts.TextToSpeech
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
                        modifier = Modifier.clickable {
                            scope.launch {
                                // 点击标题滚动到最新消息（底部）
                                if (uiState.messages.isNotEmpty()) {
                                    listState.animateScrollToItem(uiState.messages.size - 1)
                                }
                            }
                        }
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
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
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text(stringResource(R.string.clear_chat_title)) },
                            text = { Text(stringResource(R.string.clear_chat_message)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.clearMessages()
                                        showClearDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.confirm))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearDialog = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
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
                .background(Color(0xFFF7F8FA)) // Light gray background like Doubao
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
                                isTtsReady = isTtsReady
                            )
                        }
                    }

                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                // Input Area (Bottom)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 0.dp,
                    color = Color(0xFFA4F3B9).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacer(modifier = Modifier.width(1.dp))
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f),
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

                        // Send Button
                        IconButton(
                            onClick = {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            },
                            enabled = !uiState.isLoading && inputText.isNotBlank()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.send),
                                contentDescription = stringResource(R.string.send_button),
                                tint = if (!uiState.isLoading && inputText.isNotBlank()) Color.Unspecified else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Error / Service Check Overlay (Topmost)
            if (uiState.error != null || uiState.missingAccessibilityService || uiState.missingOverlayPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (uiState.missingAccessibilityService) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 8.dp
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = stringResource(R.string.accessibility_error),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                val intent =
                                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Text(stringResource(R.string.enable_action))
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.missingOverlayPermission) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 8.dp
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = stringResource(R.string.overlay_error),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = {
                                                val intent = Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Text(stringResource(R.string.grant_action))
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.error != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                shadowElevation = 8.dp
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = uiState.error!!,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = { viewModel.clearError() }) {
                                            Text(stringResource(R.string.close))
                                        }
                                    }
                                }
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
    isTtsReady: Boolean
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
            shadowElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // Single tap to play TTS - stop current and play new immediately
                            if (isTtsReady && tts != null) {
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