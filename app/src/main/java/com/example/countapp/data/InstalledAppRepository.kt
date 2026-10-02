package com.example.countapp.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.text.Collator

data class InstalledApp(
    val packageName: String,
    val name: String,
    val isSystem: Boolean = false,
    val installed: Boolean = true,
)

/** 只在用戶開啟選擇器時查詢目前使用者的已安裝 App；在背景執行緒呼叫。 */
class InstalledAppRepository(context: Context) {
    private val appContext = context.applicationContext

    fun load(): List<InstalledApp> {
        val manager = appContext.packageManager
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getInstalledApplications(0)
        }
        val collator = Collator.getInstance()
        val excluded = setOf(appContext.packageName, "com.android.settings", "com.android.systemui")
        return apps.filter { it.packageName !in excluded }
            .map { app ->
                InstalledApp(
                    packageName = app.packageName,
                    name = runCatching { manager.getApplicationLabel(app).toString() }.getOrDefault(app.packageName),
                    isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .distinctBy { it.packageName }
            .sortedWith { left, right ->
                collator.compare(left.name, right.name).takeIf { it != 0 }
                    ?: left.packageName.compareTo(right.packageName)
            }
    }
}
