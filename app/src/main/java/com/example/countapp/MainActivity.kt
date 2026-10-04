package com.example.countapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.countapp.notification.RecordNotifier
import com.example.countapp.ui.MikuApp

/**
 * 唯一的 Activity。
 *
 * 與 Flutter 版不同：不再有 method channel、不需要在 engine 銷毀時清空參照，
 * 也不需要處理「Dart 端與原生端各自存活」的競態。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        val container = (application as MikuApplication).container
        setContent {
            MikuApp(container)
        }

        // 從「已自動記錄，點一下選分類」通知進來時，直接跳編輯頁
        handleAutoRecordPromptIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoRecordPromptIntent(intent)
    }

    /**
     * 通知帶來的記錄 id → 請 UI 直接開啟「選分類／寫備註」編輯頁。
     *
     * 這裡傳 `openEditor = true`：使用者是**主動點了通知**才進來的，
     * 直接開編輯頁（App 內自動記錄只用圓球提示，不打斷操作）。
     *
     * 記錄可能已被使用者刪除（找不到 id），這種情況什麼都不做。
     */
    private fun handleAutoRecordPromptIntent(intent: Intent?) {
        val recordId = intent?.getStringExtra(RecordNotifier.EXTRA_PROMPT_RECORD_ID) ?: return
        // 同一個 Intent 只處理一次（Activity 重建時不要再跳一次）
        intent.removeExtra(RecordNotifier.EXTRA_PROMPT_RECORD_ID)

        val container = (application as MikuApplication).container
        container.recordRepository.snapshot()
            .firstOrNull { it.id == recordId }
            ?.let { container.requestAutoRecordPrompt(it, openEditor = true) }
    }

    /**
     * 每次回到前景時在背景重新載入記錄。
     *
     * 主要是讓垃圾桶的 30 天保留期在長時間不重啟的程序裡也保持正確
     * （逾期的記錄會被清掉，不會一直掛著「剩餘 0 天」）。
     */
    override fun onResume() {
        super.onResume()
        if (com.example.countapp.notification.PaymentNotificationListenerService.isNotificationAccessGranted(this)) {
            com.example.countapp.notification.PaymentNotificationListenerService.requestRebindService(this)
        }
        (application as MikuApplication).container.setAppInForeground(true)
        (application as MikuApplication).container.refreshRecordsInBackground()
    }

    override fun onPause() {
        (application as MikuApplication).container.setAppInForeground(false)
        super.onPause()
    }

    /** Android 13+ 發通知需要執行時權限。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }
    }

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
