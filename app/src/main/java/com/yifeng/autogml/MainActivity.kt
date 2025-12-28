package com.yifeng.autogml

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yifeng.autogml.ui.ChatScreen
import com.yifeng.autogml.ui.ChatViewModel
import com.yifeng.autogml.ui.SettingsScreen
import com.yifeng.autogml.ui.MarkdownViewerScreen

class  MainActivity : ComponentActivity() {
    
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved locale before super.onCreate
        val config = resources.configuration
        resources.updateConfiguration(config, resources.displayMetrics)

        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val uiState by viewModel.uiState.collectAsState()

                    NavHost(navController = navController, startDestination = "chat") {
                        composable("chat") {
                            ChatScreen(
                                viewModel = viewModel,
                                onOpenSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                apiKey = uiState.apiKey,
                                baseUrl = uiState.baseUrl,
                                isGemini = uiState.isGemini,
                                modelName = uiState.modelName,
                                isTtsEnabled = uiState.isTtsEnabled,
                                isShizukuEnabled = uiState.isShizukuEnabled,
                                onSave = { newKey, newBaseUrl, newIsGemini, newModelName, newIsTtsEnabled, newIsShizukuEnabled ->
                                    viewModel.updateSettings(newKey, newBaseUrl, newIsGemini, newModelName, newIsTtsEnabled, newIsShizukuEnabled)
                                },
                                onBack = { navController.popBackStack() },
                                onOpenDocumentation = { navController.navigate("documentation") }
                            )
                        }
                        composable("documentation") {
                            MarkdownViewerScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}