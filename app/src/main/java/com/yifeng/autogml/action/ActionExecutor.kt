package com.yifeng.autogml.action

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.yifeng.autogml.service.AutoGLMService
import kotlinx.coroutines.delay

class ActionExecutor(private val service: AutoGLMService) {

    suspend fun execute(action: Action): Boolean {
        return when (action) {
            is Action.Tap -> {
                Log.d("ActionExecutor", "Tapping ${action.x}, ${action.y}")
                val success = service.performTap(action.x.toFloat(), action.y.toFloat())
                delay(1000)
                success
            }
            is Action.DoubleTap -> {
                Log.d("ActionExecutor", "Double Tapping ${action.x}, ${action.y}")
                // Execute two taps with a short delay
                val success1 = service.performTap(action.x.toFloat(), action.y.toFloat())
                delay(150) 
                val success2 = service.performTap(action.x.toFloat(), action.y.toFloat())
                delay(1000)
                success1 && success2
            }
            is Action.LongPress -> {
                Log.d("ActionExecutor", "Long Pressing ${action.x}, ${action.y}")
                val success = service.performLongPress(action.x.toFloat(), action.y.toFloat())
                delay(1000)
                success
            }
            is Action.Swipe -> {
                Log.d("ActionExecutor", "Swiping ${action.startX},${action.startY} -> ${action.endX},${action.endY}")
                val success = service.performSwipe(
                    action.startX.toFloat(), action.startY.toFloat(),
                    action.endX.toFloat(), action.endY.toFloat()
                )
                delay(1000)
                success
            }
            is Action.Type -> {
                Log.d("ActionExecutor", "Typing ${action.text}")
                // Simple implementation: try to find focused node or just set text on the first editable node
                val root = service.rootInActiveWindow
                val editableNode = findEditableNode(root)
                if (editableNode != null) {
                    val arguments = Bundle()
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, action.text)
                    val success = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    delay(500)
                    success
                } else {
                    Log.e("ActionExecutor", "No editable node found")
                    false
                }
            }
            is Action.Launch -> {
                Log.d("ActionExecutor", "Launching ${action.appName}")
                // Need a map of App Name -> Package Name. For now, just try generic intent or implement a mapper.
                // Simplified: Assume appName IS packageName for this MVP or implement a small mapper
                // A real implementation needs the package mapper from the original project
                val packageName = AppMapper.getPackageName(action.appName)
                if (packageName != null) {
                    val intent = service.packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        Log.d("ActionExecutor", "Found intent for $packageName, starting activity...")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            service.startActivity(intent)
                            Log.d("ActionExecutor", "Activity started successfully")
                            delay(2000)
                            true
                        } catch (e: Exception) {
                            Log.e("ActionExecutor", "Failed to start activity: ${e.message}")
                            false
                        }
                    } else {
                        Log.e("ActionExecutor", "Launch intent is null for $packageName")
                        false
                    }
                } else {
                    Log.e("ActionExecutor", "Unknown app: ${action.appName} (mapped to null)")
                    false
                }
            }
            is Action.Back -> {
                service.performGlobalBack()
                delay(1000)
                true
            }
            is Action.Home -> {
                service.performGlobalHome()
                delay(1000)
                true
            }
            is Action.Wait -> {
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
            Action.Unknown -> false
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
