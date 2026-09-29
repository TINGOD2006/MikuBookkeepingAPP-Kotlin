# 目前未啟用 minify（release 的 isMinifyEnabled = false），
# 保留此檔案以便日後開啟混淆時使用。
#
# 若日後開啟 minify，請注意以下項目必須保留：
#   - NotificationListenerService / AccessibilityService 由系統以類名實例化，
#     不能被混淆或移除。
-keep class com.example.countapp.notification.** { *; }
-keep class com.example.countapp.MainActivity { *; }
