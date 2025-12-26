package com.yifeng.autogml.action

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.yifeng.autogml.service.AutoGLMService
import com.yifeng.autogml.shizuku.ShizukuHelper
import kotlinx.coroutines.delay

class ActionExecutor(private val service: AutoGLMService) {

    suspend fun execute(action: Action): Boolean {
        // 检查是否启用Shizuku模式
        val isShizukuEnabled = service.getSharedPreferences("app_settings", 0)
            .getBoolean("is_shizuku_enabled", false)
        
        // 详细的模式检查和日志
        val executionMode = when {
            !isShizukuEnabled -> "ACCESSIBILITY_SERVICE"
            !ShizukuHelper.isShizukuAvailable() -> "ACCESSIBILITY_SERVICE (Shizuku不可用)"
            !ShizukuHelper.hasShizukuPermission() -> "ACCESSIBILITY_SERVICE (Shizuku无权限)"
            else -> "SHIZUKU"
        }
        
        Log.i("ActionExecutor", "=== 执行模式: $executionMode ===")
        Log.d("ActionExecutor", "Shizuku设置启用: $isShizukuEnabled")
        Log.d("ActionExecutor", "Shizuku服务可用: ${ShizukuHelper.isShizukuAvailable()}")
        Log.d("ActionExecutor", "Shizuku权限状态: ${ShizukuHelper.hasShizukuPermission()}")
        
        val useShizuku = isShizukuEnabled && ShizukuHelper.hasShizukuPermission()
        
        return when (action) {
            is Action.Tap -> {
                Log.d("ActionExecutor", "Tapping ${action.x}, ${action.y} [模式: ${if (useShizuku) "Shizuku" else "无障碍服务"}]")
                val success = if (useShizuku) {
                    Log.d("ActionExecutor", "使用Shizuku执行点击")
                    ShizukuHelper.performTap(action.x.toFloat(), action.y.toFloat())
                } else {
                    Log.d("ActionExecutor", "使用无障碍服务执行点击")
                    service.performTap(action.x.toFloat(), action.y.toFloat())
                }
                Log.d("ActionExecutor", "点击操作结果: $success")
                delay(1000)
                success
            }
            is Action.DoubleTap -> {
                Log.d("ActionExecutor", "Double Tapping ${action.x}, ${action.y} [模式: ${if (useShizuku) "Shizuku" else "无障碍服务"}]")
                val success = if (useShizuku) {
                    Log.d("ActionExecutor", "使用Shizuku执行双击")
                    // Execute two taps with a short delay
                    val success1 = ShizukuHelper.performTap(action.x.toFloat(), action.y.toFloat())
                    delay(150) 
                    val success2 = ShizukuHelper.performTap(action.x.toFloat(), action.y.toFloat())
                    success1 && success2
                } else {
                    Log.d("ActionExecutor", "使用无障碍服务执行双击")
                    // Execute two taps with a short delay
                    val success1 = service.performTap(action.x.toFloat(), action.y.toFloat())
                    delay(150) 
                    val success2 = service.performTap(action.x.toFloat(), action.y.toFloat())
                    success1 && success2
                }
                Log.d("ActionExecutor", "双击操作结果: $success")
                delay(1000)
                success
            }
            is Action.LongPress -> {
                Log.d("ActionExecutor", "Long Pressing ${action.x}, ${action.y} [模式: ${if (useShizuku) "Shizuku" else "无障碍服务"}]")
                val success = if (useShizuku) {
                    Log.d("ActionExecutor", "使用Shizuku执行长按")
                    ShizukuHelper.performLongPress(action.x.toFloat(), action.y.toFloat())
                } else {
                    Log.d("ActionExecutor", "使用无障碍服务执行长按")
                    service.performLongPress(action.x.toFloat(), action.y.toFloat())
                }
                Log.d("ActionExecutor", "长按操作结果: $success")
                delay(1000)
                success
            }
            is Action.Swipe -> {
                Log.d("ActionExecutor", "Swiping ${action.startX},${action.startY} -> ${action.endX},${action.endY} [模式: ${if (useShizuku) "Shizuku" else "无障碍服务"}]")
                val success = if (useShizuku) {
                    Log.d("ActionExecutor", "使用Shizuku执行滑动")
                    ShizukuHelper.performSwipe(
                        action.startX.toFloat(), action.startY.toFloat(),
                        action.endX.toFloat(), action.endY.toFloat()
                    )
                } else {
                    Log.d("ActionExecutor", "使用无障碍服务执行滑动")
                    service.performSwipe(
                        action.startX.toFloat(), action.startY.toFloat(),
                        action.endX.toFloat(), action.endY.toFloat()
                    )
                }
                Log.d("ActionExecutor", "滑动操作结果: $success")
                delay(1000)
                success
            }
            is Action.Type -> {
                Log.d("ActionExecutor", "Typing ${action.text} [模式: ${if (useShizuku) "Shizuku" else "无障碍服务"}]")
                val success = if (useShizuku) {
                    Log.d("ActionExecutor", "使用Shizuku执行文本输入")
                    ShizukuHelper.inputText(action.text)
                } else {
                    Log.d("ActionExecutor", "使用无障碍服务执行文本输入")
                    // Simple implementation: try to find focused node or just set text on the first editable node
                    val root = service.rootInActiveWindow
                    val editableNode = findEditableNode(root)
                    if (editableNode != null) {
                        val arguments = Bundle()
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text)
                        editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    } else {
                        Log.e("ActionExecutor", "No editable node found")
                        false
                    }
                }
                Log.d("ActionExecutor", "文本输入操作结果: $success")
                delay(500)
                success
            }
            is Action.Launch -> {
                Log.d("ActionExecutor", "Launching ${action.appName} [模式: ${if (useShizuku) "Shizuku" else "无障碍服务"}]")
                // Need a map of App Name -> Package Name. For now, just try generic intent or implement a mapper.
                // Simplified: Assume appName IS packageName for this MVP or implement a small mapper
                // A real implementation needs the package mapper from the original project
                val packageName = AppMapper.getPackageName(action.appName)
                if (packageName != null) {
                    val success = if (useShizuku) {
                        Log.d("ActionExecutor", "使用Shizuku启动应用: $packageName")
                        ShizukuHelper.launchApp(packageName)
                    } else {
                        Log.d("ActionExecutor", "使用无障碍服务启动应用: $packageName")
                        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            Log.d("ActionExecutor", "Found intent for $packageName, starting activity...")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                service.startActivity(intent)
                                Log.d("ActionExecutor", "Activity started successfully")
                                true
                            } catch (e: Exception) {
                                Log.e("ActionExecutor", "Failed to start activity: ${e.message}")
                                false
                            }
                        } else {
                            Log.e("ActionExecutor", "Launch intent is null for $packageName")
                            false
                        }
                    }
                    Log.d("ActionExecutor", "应用启动操作结果: $success")
                    delay(2000)
                    success
                } else {
                    Log.e("ActionExecutor", "Unknown app: ${action.appName} (mapped to null)")
                    false
                }
            }
            is Action.Back -> {
                Log.d("ActionExecutor", "Back [模式: ${if (useShizuku) "Shizuku" else "无障碍服务"}]")
                val success = if (useShizuku) {
                    Log.d("ActionExecutor", "使用Shizuku执行返回")
                    ShizukuHelper.performBack()
                } else {
                    Log.d("ActionExecutor", "使用无障碍服务执行返回")
                    service.performGlobalBack()
                }
                Log.d("ActionExecutor", "返回操作结果: $success")
                delay(1000)
                success
            }
            is Action.Home -> {
                Log.d("ActionExecutor", "Home [模式: ${if (useShizuku) "Shizuku" else "无障碍服务"}]")
                val success = if (useShizuku) {
                    Log.d("ActionExecutor", "使用Shizuku执行Home")
                    ShizukuHelper.performHome()
                } else {
                    Log.d("ActionExecutor", "使用无障碍服务执行Home")
                    service.performGlobalHome()
                }
                Log.d("ActionExecutor", "Home操作结果: $success")
                delay(1000)
                success
            }
            is Action.Wait -> {
                Log.d("ActionExecutor", "Wait ${action.durationMs}ms")
                delay(action.durationMs)
                true
            }
            is Action.Finish -> {
                Log.i("ActionExecutor", "Task Finished: ${action.message}")
                true
            }
            is Action.Error -> {
                Log.e("ActionExecutor", "Error: ${action.reason}")
                false
            }
            Action.Unknown -> {
                Log.w("ActionExecutor", "Unknown action")
                false
            }
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findEditableNode(child)
            if (result != null) return result
        }
        return null
    }
}
