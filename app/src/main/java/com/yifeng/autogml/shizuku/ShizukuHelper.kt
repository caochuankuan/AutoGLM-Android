package com.yifeng.autogml.shizuku

import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku辅助类，用于执行需要系统权限的操作
 */
object ShizukuHelper {
    
    private const val TAG = "ShizukuHelper"
    
    // 权限监听器
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "🔔 Shizuku权限请求结果: requestCode=$requestCode, granted=$granted")
        if (granted) {
            Log.i(TAG, "✅ Shizuku权限已授予，可以使用Shizuku模式")
        } else {
            Log.w(TAG, "❌ Shizuku权限被拒绝，将回退到无障碍服务模式")
        }
    }
    
    init {
        // 添加权限监听器
        try {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add permission listener", e)
        }
    }
    
    /**
     * 检查Shizuku是否可用
     */
    fun isShizukuAvailable(): Boolean {
        return try {
            val available = Shizuku.pingBinder()
            Log.d(TAG, "🔍 Shizuku服务可用性检查: $available")
            available
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Shizuku服务不可用: ${e.message}")
            false
        }
    }
    
    /**
     * 检查是否有Shizuku权限
     */
    fun hasShizukuPermission(): Boolean {
        return if (isShizukuAvailable()) {
            val hasPermission = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "🔐 Shizuku权限检查: $hasPermission")
            hasPermission
        } else {
            Log.d(TAG, "🔐 Shizuku权限检查: false (服务不可用)")
            false
        }
    }
    
    /**
     * 请求Shizuku权限
     */
    fun requestShizukuPermission() {
        if (isShizukuAvailable() && !hasShizukuPermission()) {
            Log.i(TAG, "📋 请求Shizuku权限...")
            Shizuku.requestPermission(0)
        } else {
            Log.d(TAG, "📋 跳过权限请求 - 服务不可用或已有权限")
        }
    }
    
    /**
     * 执行点击操作
     */
    fun performTap(x: Float, y: Float): Boolean {
        Log.d(TAG, "🎯 准备执行点击: ($x, $y)")
        val result = executeInputCommand("input tap $x $y")
        Log.d(TAG, "🎯 点击操作完成，结果: $result")
        return result
    }
    
    /**
     * 执行长按操作
     */
    fun performLongPress(x: Float, y: Float, duration: Long = 1000): Boolean {
        // 使用input swipe模拟长按，起点和终点相同
        return executeInputCommand("input swipe $x $y $x $y $duration")
    }
    
    /**
     * 执行滑动操作
     */
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300): Boolean {
        return executeInputCommand("input swipe $startX $startY $endX $endY $duration")
    }
    
    /**
     * 输入文本
     */
    fun inputText(text: String): Boolean {
        // 对于包含空格的文本，需要特殊处理
        val escapedText = text.replace("\"", "\\\"").replace("'", "\\'")
        return executeInputCommand("input text \"$escapedText\"")
    }
    
    /**
     * 按下返回键
     */
    fun performBack(): Boolean {
        return executeInputCommand("input keyevent 4")
    }
    
    /**
     * 按下Home键
     */
    fun performHome(): Boolean {
        return executeInputCommand("input keyevent 3")
    }
    
    /**
     * 启动应用
     */
    fun launchApp(packageName: String): Boolean {
        return executeInputCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove permission listener", e)
        }
    }
    
    /**
     * 执行输入命令的通用方法
     */
    private fun executeInputCommand(command: String): Boolean {
        return try {
            if (!hasShizukuPermission()) {
                Log.e(TAG, "❌ Shizuku权限检查失败，无法执行命令: $command")
                return false
            }
            
            Log.i(TAG, "🚀 [Shizuku模式] 执行命令: $command")
            
            val startTime = System.currentTimeMillis()
            
            // 使用反射调用Shizuku的newProcess方法
            val result = try {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                
                val argv = arrayOf("sh", "-c", command)
                val process = method.invoke(null, argv, null, null) as Process
                
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                
                val duration = System.currentTimeMillis() - startTime
                
                Log.d(TAG, "命令执行完成: exitCode=$exitCode (耗时: ${duration}ms)")
                if (stdout.isNotEmpty()) {
                    Log.d(TAG, "标准输出: $stdout")
                }
                if (stderr.isNotEmpty()) {
                    Log.w(TAG, "错误输出: $stderr")
                }
                
                // 对于input命令，即使exitCode不是0也可能成功
                // 主要看是否有严重错误
                val success = exitCode == 0 || stderr.isEmpty() || !stderr.contains("Permission denied")
                
                if (success) {
                    Log.i(TAG, "✅ [Shizuku模式] 命令执行成功: $command")
                } else {
                    Log.e(TAG, "❌ [Shizuku模式] 命令执行失败: $command, 错误: $stderr")
                }
                
                success
            } catch (e: Exception) {
                Log.e(TAG, "💥 [Shizuku模式] 反射调用失败: $command", e)
                
                // 回退到普通方法
                Log.w(TAG, "回退到普通Runtime.exec方法")
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    val exitCode = process.waitFor()
                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "普通方法退出码: $exitCode (耗时: ${duration}ms)")
                    exitCode == 0
                } catch (e2: Exception) {
                    Log.e(TAG, "普通方法也失败: $command", e2)
                    false
                }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "💥 [Shizuku模式] 命令执行异常: $command", e)
            false
        }
    }
}