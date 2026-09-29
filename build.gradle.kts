// 頂層建置檔：只宣告插件版本，實際套用到 app/build.gradle.kts
//
// Gradle 9.3.1 / AGP 9.1.0（與原本的 Flutter 專案一致，可直接重用快取）
//
// ⚠️ 不需要 org.jetbrains.kotlin.android：
//    AGP 9.0 起內建 Kotlin 支援，再套用該插件會直接建置失敗
//    （"no longer required for Kotlin support since AGP 9.0"）。
//    Compose 編譯器插件則仍需要另外套用。
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
