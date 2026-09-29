package com.example.countapp

import android.app.Application

/**
 * Application 進入點。
 *
 * 建立全 App 共用的服務容器，並在背景啟動 Flutter 版資料移轉
 * （見 [com.example.countapp.data.FlutterPreferencesMigrator]）。
 */
class MikuApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannels()
        container.startMigration()
    }
}
