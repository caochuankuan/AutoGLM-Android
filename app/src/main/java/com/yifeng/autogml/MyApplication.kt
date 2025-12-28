package com.yifeng.autogml

import android.app.Application
import com.yifeng.autogml.action.AppMapper
import com.yifeng.autogml.database.ChatHistoryManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppMapper.init(this)
        
        ChatHistoryManager.getInstance().initialize(this)
    }
}
