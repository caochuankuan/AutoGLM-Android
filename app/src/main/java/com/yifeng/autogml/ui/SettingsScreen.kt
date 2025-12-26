package com.yifeng.autogml.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yifeng.autogml.BuildConfig
import com.yifeng.autogml.R
import com.yifeng.autogml.shizuku.ShizukuHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKey: String,
    baseUrl: String,
    isGemini: Boolean,
    modelName: String,
    isTtsEnabled: Boolean,
    isShizukuEnabled: Boolean,
    onSave: (String, String, Boolean, String, Boolean, Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenDocumentation: () -> Unit
) {
    val isDefaultKey = apiKey == BuildConfig.DEFAULT_API_KEY && BuildConfig.DEFAULT_API_KEY.isNotEmpty()

    // If we have an existing key, start in "View Mode" (not editing), otherwise "Edit Mode"
    var isEditing by remember { mutableStateOf(apiKey.isEmpty()) }
    // The key being typed in Edit Mode. If default key, start empty to avoid revealing it.
    var newKey by remember { mutableStateOf(if (isDefaultKey) "" else apiKey) }
    var newBaseUrl by remember { mutableStateOf(baseUrl) }
    var newIsGemini by remember { mutableStateOf(isGemini) }
    var newModelName by remember { mutableStateOf(modelName) }
    var newIsTtsEnabled by remember { mutableStateOf(isTtsEnabled) }
    var newIsShizukuEnabled by remember { mutableStateOf(isShizukuEnabled) }
    
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Visibility toggle only for the input field in Edit Mode
    var isInputVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {}
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isEditing) {
                // View Mode: Show masked key + Edit button
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.api_key_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = if (isDefaultKey) stringResource(R.string.api_key_default_masked) else getMaskedKey(apiKey),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.model_name_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.api_type_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isGemini) stringResource(R.string.api_type_gemini) else stringResource(R.string.api_type_openai),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.base_url_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = baseUrl,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = { 
                                isEditing = true 
                                newKey = if (isDefaultKey) "" else apiKey
                                newBaseUrl = baseUrl
                                newIsGemini = isGemini
                                newModelName = modelName
                                newIsTtsEnabled = isTtsEnabled
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(stringResource(R.string.edit_api_key))
                        }
                    }
                }
            } else {
                // Edit Mode: Input field
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_title), // Changed label since we moved API Key
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 1. API Type Selection (Dropdown)
                        var typeExpanded by remember { mutableStateOf(false) }
                        val currentTypeLabel = if (newIsGemini) stringResource(R.string.api_type_gemini) else stringResource(R.string.api_type_openai_title)

                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = !typeExpanded }
                        ) {
                            OutlinedTextField(
                                value = currentTypeLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.api_type_label)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )

                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.api_type_openai_title),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.api_type_openai_desc),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        newIsGemini = false
                                        newBaseUrl = "https://open.bigmodel.cn/api/paas/v4"
                                        newModelName = "autoglm-phone"
                                        typeExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.api_type_gemini)) },
                                    onClick = {
                                        newIsGemini = true
                                        newBaseUrl = "https://generativelanguage.googleapis.com"
                                        newModelName = "gemini-2.0-flash-exp"
                                        typeExpanded = false
                                    }
                                )
                            }
                        }

                        // 2. Base URL Input
                        OutlinedTextField(
                            value = newBaseUrl,
                            onValueChange = { 
                                newBaseUrl = it
                                // Optional: Auto-detect if user manually types a google url
                                if (it.contains("googleapis.com")) newIsGemini = true
                            },
                            label = { Text(stringResource(R.string.enter_base_url)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { keyboardController?.hide() }
                            ),
                            placeholder = { Text(stringResource(R.string.base_url_placeholder)) },
                            supportingText = {
                                Text(
                                    text = if (newIsGemini) stringResource(R.string.base_url_hint_gemini) 
                                           else stringResource(R.string.base_url_hint_openai),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        // 3. Model Name Input
                        if (newIsGemini) {
                            var modelExpanded by remember { mutableStateOf(false) }
                            val geminiModels = listOf(
                                "gemini-2.0-flash-exp",
                                "gemini-2.5-flash-lite",
                                "gemini-3-flash-preview",
                                "gemini-3-pro-preview",
                                "gemini-1.5-flash",
                                "gemini-1.5-pro"
                            )

                            ExposedDropdownMenuBox(
                                expanded = modelExpanded,
                                onExpandedChange = { modelExpanded = !modelExpanded }
                            ) {
                                OutlinedTextField(
                                    value = newModelName,
                                    onValueChange = { newModelName = it },
                                    label = { Text(stringResource(R.string.enter_model_name)) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = { keyboardController?.hide() }
                                    )
                                )

                                ExposedDropdownMenu(
                                    expanded = modelExpanded,
                                    onDismissRequest = { modelExpanded = false }
                                ) {
                                    geminiModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                newModelName = model
                                                modelExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = newModelName,
                                onValueChange = { newModelName = it },
                                label = { Text(stringResource(R.string.enter_model_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { keyboardController?.hide() }
                                ),
                                placeholder = { Text(stringResource(R.string.model_name_placeholder)) }
                            )
                        }

                        // 4. API Key Input (Moved to bottom)
                        OutlinedTextField(
                            value = newKey,
                            onValueChange = { newKey = it },
                            label = { Text(stringResource(R.string.enter_api_key)) },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (isInputVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (isInputVisible)
                                    Icons.Filled.Visibility
                                else
                                    Icons.Filled.VisibilityOff

                                val description = if (isInputVisible) stringResource(R.string.hide_api_key) else stringResource(R.string.show_api_key)

                                IconButton(onClick = { isInputVisible = !isInputVisible }) {
                                    Icon(imageVector = image, contentDescription = description)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { keyboardController?.hide() }
                            ),
                            placeholder = { 
                                Text(
                                    if (isDefaultKey) stringResource(R.string.api_key_default_edit_placeholder) 
                                    else stringResource(R.string.api_key_placeholder)
                                ) 
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(onClick = {
                                if (apiKey.isNotEmpty()) {
                                    isEditing = false
                                    newKey = if (isDefaultKey) "" else apiKey
                                    newBaseUrl = baseUrl
                                    newIsGemini = isGemini
                                    newModelName = modelName
                                    newIsTtsEnabled = isTtsEnabled
                                    newIsShizukuEnabled = isShizukuEnabled
                                    newIsShizukuEnabled = isShizukuEnabled
                                } else {
                                    onBack()
                                }
                            }) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { 
                                    // Allow saving empty key (to restore default) or valid key
                                    onSave(newKey, newBaseUrl, newIsGemini, newModelName, newIsTtsEnabled, newIsShizukuEnabled)
                                    onBack() 
                                },
                                // Enable save button if key is not blank OR if user cleared it (to reset to default)
                                // Actually, if user clears it, we interpret it as reset to default.
                                enabled = true 
                            ) {
                                Text(stringResource(R.string.save))
                            }
                        }
                    }
                }
            }

            // TTS Settings Card (独立的设置项)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tts_enabled),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.tts_enabled_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = newIsTtsEnabled,
                        onCheckedChange = { 
                            newIsTtsEnabled = it
                            // 立即保存TTS设置
                            onSave(apiKey, baseUrl, isGemini, modelName, it, isShizukuEnabled)
                        }
                    )
                }
            }

            // Shizuku Settings Card (独立的设置项)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shizuku模式",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "使用Shizuku执行操作，需要先安装并启动Shizuku服务",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = newIsShizukuEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    // 开启Shizuku模式时，先检查并申请权限
                                    if (ShizukuHelper.isShizukuAvailable()) {
                                        if (!ShizukuHelper.hasShizukuPermission()) {
                                            ShizukuHelper.requestShizukuPermission()
                                        }
                                        newIsShizukuEnabled = enabled
                                        onSave(apiKey, baseUrl, isGemini, modelName, isTtsEnabled, enabled)
                                    } else {
                                        // Shizuku不可用，保持关闭状态
                                        newIsShizukuEnabled = false
                                    }
                                } else {
                                    newIsShizukuEnabled = enabled
                                    onSave(apiKey, baseUrl, isGemini, modelName, isTtsEnabled, enabled)
                                }
                            }
                        )
                    }
                    
                    // Shizuku状态指示器
                    if (newIsShizukuEnabled) {
                        val statusText = when {
                            !ShizukuHelper.isShizukuAvailable() -> "状态: Shizuku服务未运行"
                            !ShizukuHelper.hasShizukuPermission() -> "状态: 等待权限授予"
                            else -> "状态: Shizuku已就绪"
                        }
                        val statusColor = when {
                            !ShizukuHelper.isShizukuAvailable() -> MaterialTheme.colorScheme.error
                            !ShizukuHelper.hasShizukuPermission() -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                    }
                }
            }

            // Documentation Link
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = onOpenDocumentation
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.view_documentation),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun getMaskedKey(key: String): String {
    if (key.length <= 8) return "******"
    return "${key.take(4)}...${key.takeLast(4)}"
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreen(
        apiKey = "sk-...",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        isGemini = false,
        modelName = "autoglm-phone",
        isTtsEnabled = true,
        isShizukuEnabled = false,
        onSave = { _, _, _, _, _, _ -> },
        onBack = {},
        onOpenDocumentation = {}
    )
}
