// ⚠️ 不套用 org.jetbrains.kotlin.android：AGP 9 已內建 Kotlin 支援，
//    重複套用會導致 "Cannot add extension with name 'kotlin'" 建置失敗。
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ── release 簽章來源 ────────────────────────────────────────────
// CI 由 GitHub Actions 把 Secrets 解碼成 keystore 檔，再用環境變數傳進來；
// 本機也可以在 PowerShell 設定同樣的環境變數，產生與 CI 相同簽章的 APK。
// 沒設定時 hasMikuKeystore 為 false，release 會退回 debug 簽章。
val mikuKeystorePath: String? = System.getenv("MIKU_KEYSTORE_FILE")
val mikuKeystorePassword: String? = System.getenv("MIKU_KEYSTORE_PASSWORD")
val mikuKeyAlias: String = System.getenv("MIKU_KEY_ALIAS") ?: "miku"
val mikuKeyPassword: String? = System.getenv("MIKU_KEY_PASSWORD") ?: mikuKeystorePassword
val hasMikuKeystore: Boolean = mikuKeystorePath != null && file(mikuKeystorePath).exists()

android {
    namespace = "com.example.countapp"

    // ⚠️ compileSdk 固定 36：
    //    AGP 9.1.0 建議的最高 compileSdk 就是 36；再上去（例如最新的
    //    Compose BOM 2026.09）會要求 compileSdk 37 而建置失敗。
    //    因此這裡改用與 36 相容的 AndroidX 版本，詳見下方 dependencies。
    compileSdk = 36

    defaultConfig {
        // ⚠️ 刻意沿用與 Flutter 版相同的 applicationId：
        //    這樣新舊版會共用同一個私有儲存空間（SharedPreferences 檔案
        //    "FlutterSharedPreferences"），首次啟動就能直接把舊資料搬過來，
        //    使用者不需要手動匯出／匯入。
        //
        //    若你想「新舊版同時安裝」做比較，把這裡改成別的包名即可
        //    （但如此一來就讀不到舊資料，見 FlutterPreferencesMigrator）。
        applicationId = "com.example.countapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 只有環境變數齊全時才建立；否則由下面的 buildTypes.release 退回 debug。
        if (hasMikuKeystore) {
            create("release") {
                storeFile = file(mikuKeystorePath!!)
                storePassword = mikuKeystorePassword
                keyAlias = mikuKeyAlias
                keyPassword = mikuKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // ⚠️ 固定簽章：CI 由 GitHub Secrets 提供 keystore，確保每一版簽章一致，
            //    使用者才能直接覆蓋升級（否則每台機器／每次 CI 的 debug key 都不同）。
            //    本機沒設環境變數時退回 debug 簽章，方便直接試編。
            signingConfig = if (hasMikuKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// 不需要設定 kotlin.compilerOptions.jvmTarget：
// AGP 內建 Kotlin 會直接沿用 compileOptions.targetCompatibility（上面已設為 17）。

dependencies {
    // ⚠️ 版本都刻意壓在「compileSdk 36 可用」的區間。
    //    最新的 androidx.core 1.19 / Compose BOM 2026.09 都要求
    //    compileSdk 37（AGP 9.1.0 不支援），所以不能用最新版。
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // 分類圖示需要 extended 圖示集。
    // ⚠️ 這個套件已被 Google 凍結在 1.7.8 且不再納入 Compose BOM，
    //    因此必須明確指定版本，不能靠 BOM 管理。
    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // 單元測試。
    // ⚠️ org.json 在 Android 上由系統提供，但 JVM 單元測試只會拿到
    //    android.jar 的 empty stub（呼叫就拋例外），因此測試時必須
    //    另外帶入真正的實作。
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
