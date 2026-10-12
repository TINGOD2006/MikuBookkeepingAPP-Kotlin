# APK 發布與版本更新說明（交接文檔）

> **一句話總結**：本專案的 APK 由 GitHub Actions 在推送 `v*` 標籤時自動建置，用**固定簽章的 release keystore** 簽名，並在**簽章指紋硬性驗證通過後**才附到 GitHub Release 供人下載。發新版只要做三件事：改版本號 → 推 commit → 推 tag。

**這份文件適用對象**：新的 AI session／新的 agent／未來的自己。內容**不含任何機密**，可以直接複製到任何對話、或 commit 進 repo。

---

## 0. 先回答一個常見問題：只給 `signing/SECRETS.txt` 夠嗎？

**不夠，而且不建議。**

| | `.signing/SECRETS.txt` | 這份文件 |
|---|---|---|
| 3 個 secret 的名稱與**值** | ✅ 有 | ❌ 刻意不寫（只寫名稱與取得方式） |
| 簽章憑證指紋 | ✅ 有 | ✅ 有 |
| 改版流程（versionCode／tag／CI 行為） | ❌ **完全沒有** | ✅ 完整 |
| 升級路徑規則、地雷、歷史事故 | ❌ 沒有 | ✅ 完整 |
| 能不能安全貼進對話／commit | ❌ **絕對不行** | ✅ 可以 |

`.signing/SECRETS.txt` 裡面有 **keystore 的 base64 與密碼**。把它貼進任何對話、issue、或 commit，就等於把那把 keystore 的私鑰公開——任何人拿到就能簽出「可以覆蓋你使用者手機上 App」的更新檔。**要交給 agent 的是這份文件，不是那份。**

需要重建 GitHub secrets 時，正確做法：把**第 8 節的操作步驟**告訴 agent（或自己照著做），值由你自己在 GitHub 網頁上填。

---

## 1. 給新 session／新 agent 的貼上區塊（可直接複製）

```text
[專案] Miku 記帳 — Android 原生版（Kotlin + Jetpack Compose，AGP 9.4.1 / Gradle 9.6.0）
[repo] https://github.com/TINGOD2006/MikuBookkeepingAPP-Kotlin  （分支 main）
[本機] C:\Users\TINGOD\Desktop\CodeHome\MikuBookkeepingAPP-Kotlin

[發布機制] APK 不進 git。推送 v* 標籤（例如 v2.0.3）時，
          .github/workflows/release-apk.yml 會自動建置 release APK、
          驗證簽章指紋、附到 GitHub Release。不要手動上傳 APK。

[發新版三步驟]
  1) 改 app/build.gradle.kts：versionCode 必須 +1（例如 4 → 5）、versionName 改成新版號
  2) git commit（作者必須是 TINGOD2006 <tinchiocherrychio@gmail.com>）→ git push origin main
  3) git tag -a v2.0.3 -m "Miku 記帳 2.0.3：<說明>" && git push origin v2.0.3
  之後 CI 約 3.5 分鐘跑完，Release 自動出現並附上 app-release.apk。

[簽章] 固定 release keystore，憑證 SHA-256 指紋 =
       ab2a2f4bef6d753aab49a05f57cf34469b88669cbebf16dff41c9e13b3745154
       keystore 與密碼放在 .signing/（整個資料夾已被 .gitignore 排除）；
       CI 需要 3 個 repository secrets：KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS。
       ⚠️ 不要把這些值貼進任何對話或 commit。

[紅線]
  - 不要「刪除 tag 再重推」來重跑 CI（Release 會暫時消失約數分鐘）
  - versionCode 不可倒退或重複（否則使用者無法覆蓋升級）
  - 更換 keystore 時，必須同步更新 workflow 裡的 EXPECTED_SHA256，否則 CI 會正確地失敗

[完整細節] docs/APK發布與版本更新說明.md
```

---

## 2. 目前狀態快照（2026-10-04）

| 項目 | 值 |
|---|---|
| 最新版本 | **v2.0.2**（versionCode **4**、versionName `"2.0.2"`、applicationId `com.example.countapp`） |
| `main` 最新 commit | `f535ee4`「簽章驗證改為硬性關卡（指紋不符即失敗）+ 版本 2.0.2」 |
| 簽章指紋（SHA-256） | `ab2a2f4bef6d753aab49a05f57cf34469b88669cbebf16dff41c9e13b3745154` |
| 永久下載連結 | <https://github.com/TINGOD2006/MikuBookkeepingAPP-Kotlin/releases/latest/download/app-release.apk> |
| APK 大小 | 11,944,335 bytes（約 11.4 MB）；debug APK 約 17.7 MB |
| 最近一次 CI | run **#8** `success`（11 個步驟全綠，10:23:19Z → 10:27:00Z，**3 分 41 秒**） |

### 三個 Release

| Tag | 指向 commit | Release id | APK sha256 |
|---|---|---|---|
| `v2.0.0` | `ff8ddd5` | 402935445 | `09e7923eb4840c8b1d1de3047ca4d1768588ab3344520a6b46b8409dfff0fcaa` |
| `v2.0.1` | `1379782` | 402952132 | `defd8b20bbb89ffa2ce2dee14c49e1f1096db13e6ef30320db5bd2df682e042b` |
| `v2.0.2` | `f535ee4` | 402955558 | `b2134dc357e9348807e89b0be0fde661aca9667fa174eff16033566f270d8dbf` |

> `v2.0.0` 是**舊的 debug 簽章**（指紋 `d2b010f75eaaa28e9d0ddd5647f319eb50f3b0ef963120db54dcf12ccb357d11`、DN `CN=Android Debug`），從 `v2.0.1` 起才改用固定 release keystore。詳見第 6 節的升級路徑。

---

## 3. 為什麼是這樣的設計（決策與理由）

| 決策 | 理由 |
|---|---|
| **APK 不 commit 進 git**，改放 GitHub Release 附件 | 二進位檔每改一版就多存一份完整歷史，repo 會膨脹、無法 diff、clone 變慢。`.gitignore` 一直排除 `/app/build`。 |
| **用固定的 release keystore**，不用 debug 簽章 | debug keystore 是每台機器各自產生的（`~/.android/debug.keystore`），而 GitHub-hosted runner 每次都是全新 VM。所以每次 CI 建出來的 APK 簽章都不同 → 使用者的新版**無法覆蓋**舊版，只能解除安裝（記帳資料會一起消失）。 |
| **keystore 放 GitHub Secrets，不放 repo** | repository secret 不會出現在 log、不會被 checkout 到、不會進版控歷史。 |
| **「Decode signing keystore」缺 secret 就讓 job 失敗** | 寧可紅燈，也不要默默發出一個簽章不同、使用者無法覆蓋升級的 APK。 |
| **「Verify APK signature」指紋不符就讓 job 失敗** | 只檢查 Build 成功是不夠的：若 `MIKU_*` 環境變數沒傳進 Gradle，`app/build.gradle.kts` 會**默默退回 debug 簽章**，Build 照樣成功。而且 GitHub 的 job log 端點需要 admin 權限才讀得到，把指紋印在 log 裡等於沒有防護——所以改成硬性關卡。 |
| **用 JDK 21** | 與本機已驗證可建置的環境一致。 |
| **鎖定 `android-actions/setup-android@v4.0.4`** | v3 是 Node 20 時代的產物，runner 強制改用 Node 24 後會直接失敗（2026-10-04 run #1 就是死在這裡），且它依賴已被 Google 移除的 `tools` 套件。 |

---

## 4. 檔案地圖：哪些檔案屬於「發布機制」

| 檔案 | 作用 | 改動時要注意 |
|---|---|---|
| `.github/workflows/release-apk.yml` | 整個 CI 流程（117 行） | 換 keystore 要改 `EXPECTED_SHA256`；`setup-android` 不要降回 v3 |
| `app/build.gradle.kts` | `versionCode`／`versionName`（第 41–42 行）、`signingConfigs`（第 47–57 行）、`buildTypes.release`（第 59–75 行） | 讀 `MIKU_KEYSTORE_FILE` / `MIKU_KEYSTORE_PASSWORD` / `MIKU_KEY_ALIAS`（預設 `miku`）/ `MIKU_KEY_PASSWORD`；環境變數不齊時**退回 debug 簽章** |
| `.gitignore` | 第 14 行 `/.signing/`，另有 `*.jks`、`*.keystore`、`/app/build` | 不要把 `.signing` 加進版控 |
| `README.md` | 第 3–15 行的「📥 下載 APK」區塊 | 用 `releases/latest` 永久連結，換版不用改 |
| `.signing/`（**不進版控**） | `miku-release.jks`（2724 bytes）、`KEYSTORE_BASE64.txt`（3632 字元）、`SECRETS.txt`、`miku-backup-before-rewrite.bundle` | 這是唯一的簽章來源，務必異地備份 |

`.signing/` 目前的內容（**全部被 git 排除，`git status` 乾淨**）：

| 檔案 | 大小 | 說明 |
|---|---|---|
| `miku-release.jks` | 2724 B | keystore 本體（PKCS12、RSA 2048、validity 10000 天、alias `miku`、store 與 key 同密碼） |
| `KEYSTORE_BASE64.txt` | 3632 字元（單行無換行） | 要填進 `KEYSTORE_BASE64` secret 的值 |
| `SECRETS.txt` | 4666 B | 給人看的設定說明＋三個 secret 的值 **⚠️ 不要外傳** |
| `miku-backup-before-rewrite.bundle` | 644080 B | 2026-10-04 改寫歷史前的完整 `git bundle` 備份 |

---

## 5. 發新版 SOP

### 5.1 前置檢查

```powershell
# 身分必須正確（這個 repo 的 local config 已固定，且已啟用 useConfigOnly）
git config --local user.name    # 應為 TINGOD2006
git config --local user.email   # 應為 tinchiocherrychio@gmail.com
git status --short              # 應為乾淨
```

### 5.2 改版本號（**最容易出錯的一步**）

編輯 `app/build.gradle.kts`：

```kotlin
// 2.0.1 起改用固定簽章的 release keystore（見下方 signingConfigs）。
// versionCode 必須比已發布的上一版大，安裝在手機上的舊版才能被直接覆蓋升級。
versionCode = 5          // ← 必須嚴格遞增：4 → 5
versionName = "2.0.3"    // ← 只是顯示用
```

| `versionCode` 規則 | 說明 |
|---|---|
| 必須**嚴格遞增** | Android 只比較數字；相同或更小的版本無法覆蓋安裝 |
| 每次發布都要 +1 | 即使只是改錯字 |
| 與 `versionName` 無關 | `versionName` 純顯示，可以是 `2.0.3`、`2.1`、`3.0.0-beta1` |

### 5.3 commit 並推 main

```powershell
git add app/build.gradle.kts
git commit -m "版本 2.0.3（versionCode 5）：<這次改了什麼>"
git push origin main
```

> 推 `main` **不會**觸發 CI（workflow 只在 `v*` tag 上跑），這是刻意的。

### 5.4 打 tag 並推上去（**這一步才會觸發建置**）

```powershell
git tag -a v2.0.3 -m "Miku 記帳 2.0.3：<一句話說明>"
git push origin v2.0.3
```

必須是 **annotated tag**（`-a -m`）。`lightweight tag` 也能觸發，但沒有 tag 訊息、不好辨識。

### 5.5 等待與驗證

CI 約 **3～4 分鐘**（其中 `Build release APK` 佔約 3 分 20 秒）。完成後：

```powershell
$repo = "https://api.github.com/repos/TINGOD2006/MikuBookkeepingAPP-Kotlin"

# 最新一次執行
curl.exe -s -L "$repo/actions/runs?per_page=3"

# Release 與附件
curl.exe -s -L "$repo/releases"

# 把最新 APK 抓下來獨立驗簽（最強的驗證）
curl.exe -s -L -o "$env:TEMP\miku.apk" "https://github.com/TINGOD2006/MikuBookkeepingAPP-Kotlin/releases/latest/download/app-release.apk"
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs "$env:TEMP\miku.apk" |
    Select-String "Signer #1 certificate DN|SHA-256 digest"
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\aapt2.exe" dump badging "$env:TEMP\miku.apk" | Select-String "^package:"
Remove-Item "$env:TEMP\miku.apk"
```

**驗收標準**：
1. CI 全部步驟 `success`（任一紅燈都要查明，不要只看「Release 有沒有出現」）。
2. `apksigner` 印出的 `SHA-256 digest` = `ab2a2f4bef6d753aab49a05f57cf34469b88669cbebf16dff41c9e13b3745154`。
3. `aapt2 dump badging` 的 `versionCode` 是你剛設的值。
4. `releases/latest` 已指向新版本（README 的下載連結會自動跟著變）。

成功的 run 裡，第 9 步會產生一則公開的 notice，可直接當證據：
> `APK 簽章正確：與固定 keystore 指紋一致（ab2a2f4b…5154），使用者可覆蓋升級。`
> 讀取方式：`curl.exe -s -L "$repo/check-runs/<job_id>/annotations"`

---

## 6. 版本號與升級路徑

| 手機上已安裝的版本 | 能否直接覆蓋升級 | 說明 |
|---|---|---|
| `v2.0.1`、`v2.0.2`（或未來用同一把 keystore 的版本） | ✅ 可以 | 簽章相同，Android 直接更新，**記帳資料保留** |
| `v2.0.0`（舊 debug 簽章） | ❌ 不行 | 必須先解除安裝再安裝 → **記帳資料會清空，請先備份** |
| 你自己在電腦上 `./gradlew assembleDebug` 編的 debug 版 | ❌ 不行 | 同上（每台機器的 debug key 都不同） |
| Flutter 版（`MikuBookkeepingAPP-main`） | ⚠️ 簽章不同 | 兩者刻意共用同一個 `applicationId`（`com.example.countapp`）以便**資料移轉**；但簽章不同，安裝 Release 版前需先處理簽章衝突（解除安裝會清掉舊資料，請先匯出） |

> README 第 14–15 行已經用使用者看得懂的話寫了這個提醒。

---

## 7. CI 的 11 個步驟

`.github/workflows/release-apk.yml` 的具名步驟，依序為：

| # | 步驟 | 做什麼／為什麼 |
|---|---|---|
| 1 | `Set up job` | runner 初始化 |
| 2 | `Checkout` | `actions/checkout@v4` |
| 3 | `Set up JDK 21` | `actions/setup-java@v4`，temurin，`java-version: "21"` |
| 4 | `Set up Android SDK` | `android-actions/setup-android@v4.0.4`（**不可用 v3**） |
| 5 | `Install SDK components` | `yes \| sdkmanager --licenses` 後安裝 `"platforms;android-36" "build-tools;36.0.0"`（因為 `compileSdk = 36`） |
| 6 | `Set up Gradle` | `gradle/actions/setup-gradle@v4`（含快取） |
| 7 | `Decode signing keystore` | 檢查兩個必要 secret 是否為空（空就 `::error::` + `exit 1`）；把 base64 解到 `$RUNNER_TEMP/miku-release.jks`，再透過 `$GITHUB_ENV` 傳出 `MIKU_KEYSTORE_FILE` / `MIKU_KEYSTORE_PASSWORD` / `MIKU_KEY_ALIAS` / `MIKU_KEY_PASSWORD` |
| 8 | `Build release APK` | `chmod +x ./gradlew`（**git 裡的 `gradlew` 是 100644，沒有執行權限**）然後 `./gradlew assembleRelease --console=plain --stacktrace` |
| 9 | `Verify APK signature` | **硬性關卡**：`apksigner verify --print-certs` 後比對 `EXPECTED_SHA256`；相符印 `::notice::`，不符印 `::error::` 並 `exit 1` |
| 10 | `Upload APK artifact` | `actions/upload-artifact@v4`，名稱 `miku-bookkeeping-release-apk`，`if-no-files-found: error` |
| 11 | `Publish to GitHub Release` | `softprops/action-gh-release@v2`，`files: app/build/outputs/apk/release/*.apk`、`generate_release_notes: true`、`fail_on_unmatched_files: true`；**有 `if: startsWith(github.ref, 'refs/tags/v')`**，所以手動 `workflow_dispatch` 只上傳 Artifact、不建 Release |

---

## 8. 機密（repository secrets）與安全規則

### 需要的 3 個 secret

位置：`https://github.com/TINGOD2006/MikuBookkeepingAPP-Kotlin/settings/secrets/actions` → `New repository secret`

| 名稱 | 值從哪來 |
|---|---|
| `KEYSTORE_BASE64` | `.signing/KEYSTORE_BASE64.txt` 的**整串**（3632 字元、單行、無換行） |
| `KEYSTORE_PASSWORD` | keystore 密碼（在 `.signing/SECRETS.txt`） |
| `KEY_ALIAS` | `miku` |

> `KEYSTORE_BASE64` 不能有換行或空白。若貼上時被自動折行，CI 第 7 步會失敗。

### 安全規則

1. **不要把 `.signing/` 任何內容貼進對話、issue、PR、或 commit。** 要重建 secret 時，只傳「步驟」不傳「值」。
2. **異地備份**：目前已備份到 `C:\Users\TINGOD\Desktop\Miku-簽章備份\`（`SECRETS.txt`、`miku-release.jks`、`KEYSTORE_BASE64.txt`）。建議再放一份到雲端硬碟／密碼管理器。
3. **弄丟這把 keystore 的後果**：再也做不出能覆蓋升級的 APK。所有已安裝的使用者都必須解除安裝重裝（記帳資料會清空）。
4. **keystore 一旦洩漏**：任何人都能簽出可覆蓋你使用者手機的「更新檔」。屆時只能換新 keystore、並接受一次「全部使用者都要重裝」的代價。

### 換 keystore 的完整步驟（萬一需要）

1. 產生新的 keystore（沿用 `.signing/` 位置與 `miku` alias）。
2. 重新產生 base64，更新 GitHub 的 `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS`。
3. **同步更新 `.github/workflows/release-apk.yml` 第 92 行的 `EXPECTED_SHA256`**（用 `apksigner verify --print-certs` 取得新指紋，小寫十六進位、無冒號）。
4. 更新 `.signing/SECRETS.txt` 裡的指紋與密碼。
5. **`versionCode` 要再 +1**，並在 Release 說明裡明確公告「此版起簽章變更，需先解除安裝（請先備份資料）」。

---

## 9. 地雷與歷史事故（都是實際踩過的）

| # | 症狀 | 根因 | 現在的防護 |
|---|---|---|---|
| 1 | run #1（`v2.0.0`）23 秒就失敗，死在 `Set up Android SDK`，後續 8 個步驟全部 `skipped`，Release 完全沒出現 | `android-actions/setup-android@v3` 是 Node 20 時代的產物，runner 強制改用 Node 24 後失敗；且它依賴 Google 已移除的 `tools` 套件 | 改用並鎖定 `@v4.0.4`（v4.0.0 遷移 Node 24、v4.0.2 修掉 tools 問題） |
| 2 | 「刪 tag 再重推」重跑 CI 後，**Release 在頁面上消失約 4 分鐘** | tag 不存在時，GitHub 不把該 Release 列為「已發布」，即使 Release 物件還在（id 未變） | **不要用刪 tag 重推來重跑**。要重跑請用 `POST /actions/runs/{id}/rerun`。若只是要改 tag 指向，用 `git push --force` 更新既有 tag ref（不是刪除再建） |
| 3 | 連續兩版 APK 的簽章不同 → 使用者無法覆蓋升級 | 每次 CI 都是全新 VM，debug keystore 每次重新產生 | 固定 release keystore + Secrets（v2.0.1 起） |
| 4 | `Verify APK signature` 明明有印指紋，卻什麼都擋不住 | job log 端點 `GET /actions/jobs/{id}/logs` 對非 admin 回 `403 Must have admin rights to Repository.` | 改成指紋不符就 `exit 1`（透過 `check-runs/{id}/annotations` 公開可讀） |
| 5 | 忘了設 secret 時，CI 一路成功卻發出 debug 簽章的 APK | `app/build.gradle.kts` 在環境變數不齊時會退回 debug 簽章 | `Decode signing keystore` 先檢查、空值就 `exit 1`；第 9 步再硬性比對指紋（雙重） |
| 6 | 2026-10-04 發現 GitHub 的 Contributors 多了一個陌生人 `tingod`（Pugu Niu） | 我（agent）當時建立的兩個 commit 署名是 `TINGOD <tingod@users.noreply.github.com>`，而該 noreply email 正好屬於那個帳號；GitHub 以 commit email 比對帳號 | 已**改寫歷史**修正署名並 force push（含 tag 重指）；repo local config 固定身分，並設 `user.useConfigOnly=true`（git 無明確身分時直接報錯，不再自動猜測） |
| 7 | 加 `.git/hooks/pre-commit` 之後**所有 commit 都失敗** | Git for Windows 的 `sh.exe` 在受限環境無法啟動：`fatal error - couldn't create signal pipe, Win32 error 5` | 移除 shell hook，改用 `user.useConfigOnly=true` 這種不依賴 shell 的防護 |
| 8 | 每次 CI 都有 Node 20 棄用警告 | `actions/checkout@v4`、`actions/setup-java@v4`、`actions/upload-artifact@v4`、`gradle/actions/setup-gradle@v4`、`softprops/action-gh-release@v2` 仍標 Node 20 | **只是警告，實測可正常運作**（run #4–#8 步驟 1–6 全 success）。要消除需逐一升級並重測 |
| 9 | `pwsh` 的 `Invoke-RestMethod` 連不上 GitHub API | 該環境沒有外網；但 `curl.exe` 可以 | 查 GitHub 狀態一律用 `curl.exe`（或 `web_fetch`） |

---

## 10. 待辦 / 已知限制

| 項目 | 狀態 |
|---|---|
| Node 20 棄用警告 | 未處理（不影響功能）。`setup-java` 官方建議遷移到 `@v5` |
| `ubuntu-latest` 將於 2026-10-19 遷移到 Ubuntu 26 | 觀察中，屆時可能需要重測 |
| REST `GET /contributors` 快取 | 改寫歷史後仍回舊的 `tingod contributions=2`（`Last-Modified` 停在推送那一刻）；**網頁版已乾淨**，待 GitHub 重算 |
| 目前 release 沒有 `signingConfig` 以外的 secret | CI 不需要任何其他密鑰 |
| Release 附件固定命名 `app-release.apk` | `softprops/action-gh-release` 會以同名更新既有附件，不需要先刪 |

---

## 11. 快速指令備忘

```powershell
# 目前版本號
Select-String -Path app\build.gradle.kts -Pattern 'versionCode|versionName'

# 本機建 debug APK（不影響 CI）
.\gradlew.bat :app:assembleDebug

# 本機建「與 CI 相同簽章」的 release APK（需先設好 MIKU_* 環境變數）
$env:MIKU_KEYSTORE_FILE     = "C:\Users\TINGOD\Desktop\CodeHome\MikuBookkeepingAPP-Kotlin\.signing\miku-release.jks"
$env:MIKU_KEYSTORE_PASSWORD = "<keystore 密碼>"
$env:MIKU_KEY_ALIAS         = "miku"
.\gradlew.bat assembleRelease

# 驗任兩顆 APK 是否同一把 key（不看檔案雜湊，看 Signer 指紋）
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs <apk 路徑>
```

> 補充：即使兩顆 APK 的 Signer 指紋相同，**檔案 sha256 也不會相同**（時間戳、壓縮順序、簽章區塊都可能不同）。要比對「能不能覆蓋升級」，永遠看 Signer 指紋，不要看檔案雜湊。
