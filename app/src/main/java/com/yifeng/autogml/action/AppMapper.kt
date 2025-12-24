package com.yifeng.autogml.action

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object AppMapper {
    private var appMap: Map<String, String> = emptyMap()
    private const val CONFIG_FILE_NAME = "app_map.json"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadMap()
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
        // 1. Exact match
        appMap[appName]?.let { return it }
        
        // 2. Case insensitive match
        appMap.entries.find { it.key.equals(appName, ignoreCase = true) }?.let { return it.value }
        
        // 3. Partial match (optional, but risky if names are short)
        // appMap.entries.find { it.key.contains(appName, ignoreCase = true) }?.let { return it.value }
        
        return null 
    }
}
