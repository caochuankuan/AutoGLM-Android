package com.yifeng.autogml.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AppInfo(
    val name: String,
    val packageName: String,
)

object AppMapper {
    private var appMap: Map<String, String> = emptyMap()
    private var dynamicAppMap: Map<String, String> = emptyMap()
    private const val CONFIG_FILE_NAME = "app_map.json"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadDynamicAppList()
        loadMap()
    }

    private fun loadDynamicAppList() {
        val context = appContext ?: return
        try {
            val pm = context.packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(main, PackageManager.MATCH_ALL)
            
            val appList = resolveInfos.distinctBy { it.activityInfo.packageName }
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo.packageName
                    
                    AppInfo(
                        name = ri.loadLabel(pm).toString(),
                        packageName = pkg,
                    )
                }
                .sortedBy { it.name.lowercase() }

            // 构建动态app映射
            dynamicAppMap = appList.associate { it.name to it.packageName }
            
        } catch (e: Exception) {
            Log.e("AppMapper", "Error loading dynamic app list", e)
            dynamicAppMap = emptyMap()
        }
    }

    private fun loadMap() {
        val context = appContext ?: return
        val gson = Gson()
        val type = object : TypeToken<Map<String, String>>() {}.type

        try {
            context.assets.open(CONFIG_FILE_NAME).bufferedReader().use { reader ->
                appMap = gson.fromJson(reader, type) ?: emptyMap()
            }
        } catch (e: Exception) {
            Log.e("AppMapper", "Error loading app map from assets.", e)
            appMap = emptyMap()
        }
    }

    fun getPackageName(appName: String): String? {
        // 1. 优先从动态获取的app列表中查找 - Exact match
        dynamicAppMap[appName]?.let { return it }
        
        // 2. 动态app列表 - Case insensitive match
        dynamicAppMap.entries.find { it.key.equals(appName, ignoreCase = true) }?.let { return it.value }
        
        // 3. Fallback到app_map.json - Exact match
        appMap[appName]?.let { return it }
        
        // 4. Fallback到app_map.json - Case insensitive match
        appMap.entries.find { it.key.equals(appName, ignoreCase = true) }?.let { return it.value }
        
        // 5. Partial match (optional, but risky if names are short)
        // dynamicAppMap.entries.find { it.key.contains(appName, ignoreCase = true) }?.let { return it.value }
        // appMap.entries.find { it.key.contains(appName, ignoreCase = true) }?.let { return it.value }
        
        return null 
    }
}
