package com.example.countapp.domain

/** APK 包名與活動的 Java 命名空間可能不同；事件與 root 包名由服務先核對。 */
internal class PaymentWindowContext {
    private val activities = mutableMapOf<String, String>()
    fun clear() { activities.clear() }
    fun observe(packageName: String, eventClass: String, windowChanged: Boolean): String {
        if (windowChanged && eventClass.isNotBlank() && !eventClass.startsWith("android.") && !eventClass.startsWith("androidx.")) {
            activities[packageName] = eventClass
        }
        return activities[packageName] ?: eventClass
    }
}
