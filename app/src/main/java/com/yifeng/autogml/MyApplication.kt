package com.yifeng.autogml

import android.app.Application
import com.yifeng.autogml.action.AppMapper

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppMapper.init(this)
    }
}
