# Miku 記帳 — Android 原生版（Kotlin + Jetpack Compose）

## 📥 下載 APK

[![最新版本](https://img.shields.io/github/v/release/TINGOD2006/MikuBookkeepingAPP-Kotlin?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=2f81f7)](https://github.com/TINGOD2006/MikuBookkeepingAPP-Kotlin/releases/latest)

| | |
|---|---|
| **最新版 APK（直接下載）** | [`app-release.apk`](https://github.com/TINGOD2006/MikuBookkeepingAPP-Kotlin/releases/latest/download/app-release.apk) |
| **所有版本 / 更新說明** | [Releases](https://github.com/TINGOD2006/MikuBookkeepingAPP-Kotlin/releases) |

不需要自己編譯：每次推送 `v*` 標籤（例如 `v2.0.0`）時，[GitHub Actions](.github/workflows/release-apk.yml) 會自動建置 release APK 並附加到 GitHub Release 供下載。想自行建置可 clone 後執行 `./gradlew assembleRelease`（產物在 `app/build/outputs/apk/release/`）。

> **安裝提示**
> 若裝置上已裝過**不同簽章**的同一款 App（例如你在自己電腦上編譯的版本），請先解除安裝再安裝 Release 版，否則 Android 會以「簽章不符」拒絕安裝（解除安裝會一併清除已儲存的記帳資料，請先備份）。

## 2026-10-04 類別設定與鍵盤布局

數字鍵盤的 0 對齊 1、2、3 三欄，保存移至同列右側。記帳分類網格的「設定」開啟類別設定頁，支出／收入分開排列，每列可編輯、刪除及拖曳排序，底部固定新增類別。預設與自訂分類都可管理，排序立即保存；改名同步歷史帳目、垃圾桶、各月份預算及分類規則，刪除分類保留歷史資料。分類對話框避讓系統鍵盤，操作按鈕保持可見。

224 個單元測試、Debug APK 與 lint（0 errors）通過；已在 Samsung SM-S9210 驗證拖曳、跨屏自動捲動、新增／改名／刪除、系統鍵盤與返回草稿，帳目內容未變。詳細結果見 [驗證紀錄](docs/2026-10-04-category-settings-keypad-verification.md)。

## 2026-10-04 MPay 與系統浮球修正

五個按鈕同列放在底部，中央新增按鈕縮小，間距由中央向外增加。MPay 讀屏改用專用完成頁判斷，修正轉帳輸入提示與錢包餘額造成的誤記；浮球提示持久保存，恢復服務後可重新顯示。背景監聽採用 specialUse 前景服務，並說明 Android 仍可停止 App 或滑除通知。

215 個測試、Debug APK 與 lint（0 errors）通過，已在 Samsung SM-S9210 重現及驗證輸入頁誤記修正。實機浮球、持久化及通知結果見 [本次驗證紀錄](docs/2026-10-04-mpay-overlay-background-verification.md)。

## 2026-10-03 設定與分類改進

底部四個導向項目等分寬度；自動記錄應用改為有圖示、包名與即時保存開關的列表。設定開關可點擊整列，規則編輯涵蓋全部支出、收入及自訂分類，並支援明確清空詞條。預設分類補齊常見固定支出，「關於」新增權限、資料傳輸及使用影響說明。

已通過 208 個單元測試、Debug APK 建置與 lint（0 errors），並在 Samsung SM-S9210 驗證介面互動。詳細範圍與限制見 [驗證紀錄](docs/2026-10-03-settings-category-review.md)。

這是 [`MikuBookkeepingAPP-main`](../MikuBookkeepingAPP-main)（Flutter 版）的 **Android 原生改寫版**。

原本的 Flutter 專案**完全保留、未被修改**；本目錄是獨立的新專案。


## 本次調整：編輯器與自動記錄應用清單（2026-10-02）

- 直向編輯／添加頁固定標題與底部鍵盤，分類區獨立捲動；橫向矮視窗改用左右分欄。
- 添加／編輯使用獨立全螢幕視窗，右上關閉及 Android 返回手勢作用於當前編輯器；系統備註鍵盤顯示時避免重疊數字鍵盤。
- 「我的 → 自動記錄應用 → 管理」列出目前使用者已安裝的應用名稱與包名，支援搜尋、系統 App 篩選、重新掃描和勾選保存。
- 預設只包含 MPay、微信和中國支付寶；保留既有用戶選擇，可按「恢復預設」重置。通知和讀屏都精確比對所選包名，取消所有勾選會保持空清單。
- 拆分分類網格、輸入區、新增分類與應用清單元件，保留頁籤及資料儲存結構。

199 項單元測試、Debug APK 建置與 lint 通過；模擬器已檢查返回、關閉、分類捲動、系統鍵盤與應用選擇，本次沒有連線實機。詳見 [本次驗證與檔案分工](docs/2026-10-02-editor-app-selection.md)。

## 本次調整：明細、支付浮球與預算

- 明細按交易時間由早到晚、由上到下排列；同一时间按建立時間和 id 穩定排序。
- 微信、支付寶、AlipayHK 和 MPay 自動記帳後，啟用的無障礙服務會在當前支付介面顯示可拖動浮球。點擊開啟原有分類／備註編輯器，返回記帳 App 時外部浮球隱藏，使用 App 內提示。沒有無障礙服務時仍可點通知或 App 內浮球。
- 使用 `TYPE_ACCESSIBILITY_OVERLAY`，由系統已授權的無障礙服務建立，不需要另開懸浮窗權限。系統敏感畫面或廠商限制可能遮住浮球。
- 本機規則先記帳與顯示提示，AI 分類改在背景補充；背景結果不覆蓋使用者已修改或已刪除的記錄。
- 預算的支出／剩餘、百分比、警示與進度條合併成一張卡片，資訊和超支提示置於進度條上方；移除重複警示卡。

在「我的」開啟自動記錄、允許對應支付 App，再從「無障礙自動記錄」開啟系統服務。識別必須有獨立的完成狀態及可確定的金額；實付欄位優先於其他金額，失敗、取消、待付款和多筆交易畫面不記錄。微信／支付寶自繪畫面若不提供無障礙文字，無法透過本次讀屏識別；可查看讀屏診斷，再利用原有通知來源。

參考來源：AutoAccounting 的 [微信 WebView Hook](https://github.com/AutoAccountingOrg/AutoAccounting/blob/master/app/src/main/java/net/ankio/auto/xposed/hooks/wechat/hooks/WebViewHooker.kt)、[支付寶 WebView Hook](https://github.com/AutoAccountingOrg/AutoAccounting/blob/master/app/src/main/java/net/ankio/auto/xposed/hooks/alipay/hooks/WebViewHooker.kt) 和 [無障礙頁面識別](https://github.com/AutoAccountingOrg/AutoAccounting/blob/master/app/src/main/java/com/google/android/accessibility/selecttospeak/SelectToSpeakService.kt)。本次獨立實作節點解析，參考其狀態／金額／對象／單號與頁面去重設計，未移植 Xposed Hook。

驗證：`gradlew.bat testDebugUnitTest assembleDebug lintDebug`。實機需驗證微信／支付寶付款後留在支付介面時浮球可見、拖動不阻擋支付操作、點擊可編輯，並確認讀屏與通知沒有重複記帳。

---

## 這個版本解決了什麼

Flutter 版最複雜的部分，全部來自「UI 在 Dart、通知在 Android 原生」這個分裂：

| Flutter 版的複雜度 | 原生版 |
|---|---|
| `saved / duplicate / disabled / ignored / failed` 五種狀態回報協定 | **不需要**，服務直接寫入儲存庫 |
| Dart 端 5 秒沒回應就由原生端補存一份 | **不需要** |
| App 未執行時把記錄暫存到 SharedPreferences 等下次匯入 | **不需要** |
| `cleanUpFlutterEngine()` 清空 methodChannel 參照 | **不需要** |
| 白名單規則要在 Dart 與 Kotlin 各維護一份 | **只有一份**（`SettingsStore`） |
| Flutter 版的 Dart 端改動無法用 Widget 測試涵蓋原生邏輯 | 全部都是 Kotlin，JVM 測試直接覆蓋 |

實測規模：**37 個 Kotlin 檔、8,006 行主程式碼、1,621 行測試**（161 個單元測試全數通過）。

---

## 本次改動（v2.5：月份選擇器字級、自動記錄圓球、分類預算自行新增、分類預算百分比）

| 目標 | 作法 | 相關檔案 |
|---|---|---|
| 左上角月份選擇器的**年份／月份看不清楚** | 兩個真正的原因一起修：(1) `MaterialTheme` 用預設 Typography 時 `LocalTextStyle = typography.bodyLarge`（已核對 material3 1.3.2 原始碼：`fontSize = 16.sp`、`lineHeight = 24.0.sp`），只給 `fontSize` 的 Text 一律沿用 24sp 行高 → 舊版年份 9sp＋月份 16sp 兩行就要 56dp，卻被外框 `height(48dp)` 硬切，量測時第二行只分到 16dp（**「9月」被上下裁掉**）；改成每個 Text 都給相稱的 `lineHeight`，外框改用 `heightIn(min = 48dp)`（字放大或系統字體調大時會把外框撐高，不再硬切）。(2) 字級本身放大：年份 9→12sp（Medium 字重、alpha 0.85→0.9）、月份 16→17sp；對稱佔位與下拉箭頭由 12dp 收到 11dp，膠囊寬度變成 `T + 26`（仍小於舊版的 `T + 30`），320dp 螢幕＋fontScale 1.3 也不會被壓縮裁字 | `ui/bookkeeping/BookkeepingScreen.kt` |
| **MPay 自動記錄時彈出一顆可點的圓球** | 自動記帳發生在「使用者正在別的 App 付款」的當下，直接蓋上整頁確認頁會打斷他手上的操作。改成在右下角（底部導覽列之上）浮出一顆 60dp 的藍色圓球：輕微呼吸動畫（1.0→1.08，`scale` 只影響繪製、可點範圍仍是完整的 60dp）、右上角顯示待處理筆數徽章，**點一下才開那筆記錄的編輯頁**；不理它，記錄也已經寫入不會漏。佇列一次處理一筆，關掉後圓球帶著新筆數再浮出。通知被點擊時（使用者已明確要看那一筆）仍**直接開編輯頁**，兩種入口由 `AppContainer` 的一次性訊號 `autoRecordPromptOpenRequest` 區分 | `ui/MikuApp.kt`、`AppContainer.kt`、`MainActivity.kt` |
| 預算頁**不預設所有分類的預算** | 分類預算改成「使用者自己挑」：畫面只列**已設定預算**與**剛按新增、還沒填金額**的分類，其餘分類完全不出現（也就不會畫出 0% 的假進度）。新增走「新增分類預算」按鈕 → 對話框只列尚未設定的分類（可捲動、每列 48dp、帶分類圖示），選完直接把那一列加進清單並進入輸入狀態；取消未填金額的草稿＝該列消失，清除已設定的預算＝該列消失。排序／去重／「分類被刪掉但仍有預算」的規則抽成純函式 `visibleCategoryBudgetNames()`，另有 8 個單元測試 | `ui/budget/BudgetScreen.kt`、`domain/CategoryBudgetSelection.kt` |
| 分類預算**顯示使用百分比** | 每個已設定的分類，名稱列右側顯示 `NN%`（顏色與進度條一致：主色／≥80% 次要藍／>100% 支出紅），進度條上方顯示「$已花費 / $預算」與「剩 $X」（超支時改為「超出 $X」），名稱左側加上分類圖示 | `ui/budget/BudgetScreen.kt` |

> v2.3 的「MPay 自動記錄時彈出確認頁」由本版的圓球取代；通知點擊的行為不變（仍直接跳確認頁）。「我的 → 無障礙自動記錄」的說明文字也同步更新。

驗證：`./gradlew --offline :app:testDebugUnitTest :app:assembleDebug` 通過（161 個單元測試，含本版新增的 8 個）。

---

## 上一個版本（v2.4：記帳頁返回手勢與數字鍵盤、卡片高度、月份置中、分類預算、我的頁整理）

| 目標 | 作法 | 相關檔案 |
|---|---|---|
| **FAB 記帳頁的數字鍵盤按不到、返回手勢回不去明細頁** | 三個根因一起修：(1) 編輯頁是全 App 唯一沒處理 insets 的全螢幕覆蓋層 → 補 `navigationBarsPadding()`＋`imePadding()`；(2) 根 `Column` 不可捲動，系統鍵盤一開固定高度子項無法壓縮，輸入區被推到畫面外（Compose 不對超出父層的子項做 hit test＝「鍵盤看得到卻點不到」）→ 改成可捲動，分類九宮格由 `LazyVerticalGrid` 換成自動高度的 `CategoryGrid`（捲動容器內不能有 `weight(1f)` 高度子項，Lazy 元件也不能被無限高度量測）；(3) 用自製鍵盤時主動 `hide()` 系統 IME、備註欄改 `ImeAction.Done`，不再讓 IME 蓋住鍵盤（系統鍵盤開著時返回手勢會先被 IME 吃掉）。另外 Manifest 明確宣告 `android:enableOnBackInvokedCallback="true"`，讓 API 33+ 的返回（含手勢）一律走 `OnBackInvokedCallback` 這條可預期的路徑 | `ui/bookkeeping/RecordEditorScreen.kt`、`AndroidManifest.xml` |
| 明細頁帳單記錄的**備註省略成一行**、**卡片豎直方向高度縮減** | 備註 `maxLines = 1` + `TextOverflow.Ellipsis`；垂直內距 10→8dp、圖示 36→32dp。關鍵是 `MaterialTheme` 用預設 Typography 時 `LocalTextStyle = bodyLarge`（lineHeight 24sp），只給 `fontSize` 的文字全都繼承 24sp 行高——為每個 `Text` 補上相稱的 `lineHeight` 後，**有無備註的卡片高度都收斂到 48dp**（修改前 95dp／56dp，差 39dp） | `ui/bookkeeping/RecordCard.kt` |
| **月份顯示器的月份文字沒有正中** | 膠囊內原本是 `[N月][▾]`，整組置中的結果讓文字被右側 12dp 的箭頭往左推 7dp。改成左右對稱的 `[12dp 佔位][N月][▾ 12dp]`，並把水平內距 8→2dp 讓膠囊總寬 `T+28 ≤` 舊版 `T+30`（不會在 320dp 螢幕或 fontScale 1.3 被壓縮裁字） | `ui/bookkeeping/BookkeepingScreen.kt` |
| 預算頁**可設定各分類的每月預算（也可以完全不設）** | 新增 `BudgetRepository` 的分類預算 API（單一鍵 `category_budgets`，JSON `{"YYYY-MM":{"分類":金額}}`，刻意不共用 `budget_` 前綴以免污染整月預算的 `allMonths()`）；**未設定＝`null`／空 map，不補 0**，UI 就不畫進度條（不用 0% 假進度）；每個分類可設定／修改／清除，未設定時只顯示「未設定＋設定」 | `data/BudgetRepository.kt`、`ui/budget/BudgetScreen.kt` |
| 預算警告**顯示在各分類與月份的進度條** | 使用率／超支／警示等級抽成純函式 `calculateBudgetStatus()`（`domain/BudgetStatus.kt`，可 JVM 測試）；月份進度條與每個已設定預算的分類進度條共用同一套規則：`>=80%` 次要藍＋「即將超過預算！已使用 N%」、`>100%` 支出紅＋「已超過預算！超出 $X」，一律**顏色＋圖示＋文字**（不是只換顏色） | `domain/BudgetStatus.kt`、`ui/budget/BudgetScreen.kt` |
| 我的頁**分類規則表的字條**優化 | 第一層每個分類顯示前 3 個詞條預覽＋「N 個詞條」徽章＋箭頭；第二層把詞條變成獨立字條（chip），每張右側 ✕ 可單獨刪除，並提供輸入框逐條新增（半形／全形逗號、空白、全形空白都支援；trim＋忽略大小寫去重，新詞附加尾端）。**儲存語意不變**：只有按「儲存」才寫回 `settings.customRules`，「取消」不寫入 | `ui/profile/ProfileScreen.kt` |
| 我的頁**設定選項排序優化並把垃圾桶加入其中** | 設定卡片依用途分成「自動記錄與分類」（自動記錄→AI 分類→分類方式→AI API 設定→目前 AI 設定→包名白名單→無障礙自動記錄→分類規則表）、「通知與提醒」（後台常駐通知→預算提醒）、「資料」（垃圾桶）、「關於」四組（小標題＋分隔線）；原本獨立在上方的垃圾桶卡片移除，改成設定清單中**整列可點**的一列（≥48dp，文案不變），仍開啟同一個 `TrashDialog` | `ui/profile/ProfileScreen.kt` |

### 這次修掉的「看得到卻點不到」

編輯頁的數字鍵盤之所以在實機上「壞掉」，不是繪製問題而是**命中測試**問題：
`enableEdgeToEdge()` 之後三鍵導覽列是另一個視窗、系統鍵盤則直接蓋在視窗上；編輯頁既沒有 insets 處理、內容也不能捲動，
系統鍵盤一開就把固定高度的輸入區推出畫面外——Compose 完全不會對超出父層 bounds 的子項做 hit test，
所以使用者看到的是「數字鍵盤明明在那裡，按了卻沒反應」。修法就是把輸入區永遠留在可視範圍內（insets＋捲動＋主動收 IME）。

---

## 上一個版本（v2.3：明細頁版面、自動記錄確認頁、無障礙自動記錄、圓餅圖佔比）

| 目標 | 作法 | 相關檔案 |
|---|---|---|
| 上方 bar 改**四等分**：月份選擇器(左) \| 支出 \| 收入 \| 結餘 | 月份選擇器移回最左邊，與三個收支統計各佔 `weight(1f)`（中間保留細分隔線）；月份仍是半透明白膠囊（獨立突顯）＋下拉箭頭，可點範圍 48dp | `ui/bookkeeping/BookkeepingScreen.kt`（`BookkeepingHeader`／`MonthCell`） |
| 移除帳目右側的**編輯筆**；**縮小記錄寬度** | 記錄卡片右側只留金額（編輯筆與按鈕移除，點卡片或左滑的【編輯】仍可編輯）；清單左右留白由 16dp 加到 26dp，滑動方塊由 72dp 縮到 62dp 讓比例協調 | `ui/bookkeeping/RecordCard.kt`、`ui/bookkeeping/BookkeepingScreen.kt` |
| **MPay 自動記錄時彈出確認頁**（選分類＋寫備註） | 自動記錄命中 MPay 時，服務把該筆記錄放進 `pendingAutoRecordPrompt`（StateFlow）；App 觀察到就在最上層開啟共用的編輯器（標題「已自動記錄」＋提示「直接關閉＝保留自動分類」），可挑分類、寫備註、改金額/日期。背景時則以通知「已自動記錄，點一下選分類」呈現，點擊帶記錄 id 直接跳到確認頁（Android 10+ 背景服務不能直接開畫面，通知的 PendingIntent 是標準做法） | `AppContainer.kt`、`ui/MikuApp.kt`、`notification/RecordNotifier.kt`、`MainActivity.kt`、`ui/bookkeeping/RecordEditorScreen.kt` |
| **無障礙自動記錄**（微信／支付寶／AlipayHK／MPay） | 讀屏服務不再只是探針：解析付款結果畫面的節點文字 → 共用判斷邏輯 → 寫入記錄 → 通知／確認頁／預算提醒；同時保留讀屏診斷紀錄。設計參考公開實作（見下方引用） | `notification/PaymentAccessibilityService.kt`、`domain/AutoRecordDecision.kt`、`notification/AutoRecordGuard.kt` |
| 分析頁圓餅圖標註**各分類佔比百分比** | 每個扇形在色帶中線位置畫出百分比（`rememberTextMeasurer` + `drawText`）；文字顏色依扇形亮度自動選黑／白；佔比 < 4% 或塞不下的扇形略過；扇形之間留 1.5° 縫隙讓區隔更明顯 | `ui/analysis/AnalysisScreen.kt` |

### 自動記錄的三道防線（避免重複與誤記）

1. **廣告過濾 ＋ 交易句型 ＋ 金額**：`PaymentTextAnalyzer` 的既有規則（廣告推播、非交易文字一律不記）。
2. **讀屏比通知更嚴格**：通知是「付款完成才發送」，但讀屏會看到「輸入金額／確認轉賬」這種**尚未完成**的頁面，因此讀屏路徑額外要求成功／完成字樣（`AutoRecordDecisionMaker.decide(requireCompletionSignal = true)`）。
3. **跨來源去重**：同一筆消費可能同時被通知與讀屏看到，`AutoRecordGuard` 用「包名＋**收支方向**＋金額＋90 秒時間窗」確保只記一次（方向一定要進鍵，否則「付 50」與「收到 50」會被誤認為同一筆）；讀屏另外用「畫面文字指紋＋時間窗」擋掉同一個畫面的事件風暴，並把指紋寫進 SharedPreferences，所以服務被系統重啟也不會重複記錄。
4. **交易失敗一律不記**：`支付失敗／交易失敗／已取消／已退款` 都有護欄（`FAILURE_PATTERNS`）。這是保守取捨：退款入帳目前不會自動記成收入，需要的話請手動新增。

> 設計參考（公開的 Android 自動記帳實作，做法一致：讀屏／通知 → 解析付款結果文字 → 寫入本機資料庫，並在付款當下彈出快速記帳對話框）：
> [kangkaipeng/Biller-Android](https://github.com/kangkaipeng/Biller-Android)、
> [KlingNaA/daily-ledger](https://github.com/KlingNaA/daily-ledger)、
> [Auto-Accounting/AutoAccounting](https://github.com/AutoAccountingOrg/AutoAccounting)

---

## 上一個版本（v2.2：明細頁版面與互動重做）

| 目標 | 作法 | 相關檔案 |
|---|---|---|
| 帳目**左滑停在定點**，右側露出【紅色刪除】【藍色編輯】兩個方塊 | 改用自製的水平拖曳＋定點動畫：卡片跟手左移，最多露出 150dp；放開時「拖過 40% 或快速左滑」就**停在開啟位置**（不會把整列滑掉），否則自動收回；開啟狀態下點卡片本身＝收回，避免誤觸編輯；一次只允許一列開啟（開啟別列時原本那列自動收回）；動作方塊只在真的滑開時組裝，因此收合時不可能被誤點、也不會出現在無障礙順序中。`SwipeToDismissBox` 的定位點是整列寬度，做不到停在中間，因此換掉 | `ui/bookkeeping/BookkeepingScreen.kt`（`SwipeActionRecord`／`SwipeActionBlock`） |
| 明細頁上方 bar **不要垃圾桶按鈕** | 移除 bar 上的垃圾桶入口與筆數徽章；垃圾桶仍可從「我的」頁面開啟（刪除後的 Snackbar「復原」也還在） | `ui/bookkeeping/BookkeepingScreen.kt`、`ui/profile/ProfileScreen.kt` |
| 上方 bar 與**金額卡片融入一齊** | 標題列、月份選擇器、支出／收入／結餘統計全部放在同一塊藍色底（`BookkeepingHeader`），中間沒有黑色縫隙、也不再是內縮的圓角卡片，只用分隔線區分三欄 | `ui/bookkeeping/BookkeepingScreen.kt` |
| **獨立突顯月份選擇器**（但仍與上方 bar 同一塊） | 半透明白膠囊＋日曆圖示＋下拉箭頭，置中放在藍底上，可點範圍 48dp；點擊開啟既有的 `MonthPickerDialog`。選定的月份提升到 `MikuApp` 保存（`rememberSaveable`），切換頁籤或轉螢幕都不會被重置 | `ui/bookkeeping/BookkeepingScreen.kt`（`MonthChip`）、`ui/MikuApp.kt` |
| 放大鏡 → **獨立的尋找頁** | 新增全螢幕 `SearchScreen`：返回鍵／返回鈕、自動聚焦的搜尋框、清除鈕；**不分月份**搜尋全部記錄（分類／備註／金額／日期，比對邏輯抽成純 Kotlin 的 `RecordSearch` 並有單元測試）；點結果直接在搜尋頁之上開編輯畫面。搜尋頁鋪滿全螢幕並阻擋觸控，是真正獨立的一頁 | `ui/bookkeeping/SearchScreen.kt`、`domain/RecordSearch.kt`、`ui/MikuApp.kt` |
| 共用元件抽出 | 記錄卡片抽成 `RecordCard.kt`（明細頁與尋找頁共用，尋找頁多顯示日期） | `ui/bookkeeping/RecordCard.kt` |

---

## 上一個版本（v2.1）

| 需求 | 作法 | 相關檔案 |
|---|---|---|
| 圓形 FAB 打開記帳頁後，右上角 X 的觸控範圍太小 | X 圖示外框從 24dp 放大到 **48dp**（Material 最小觸控邊長），標題列加高到 56dp；明細頁的搜尋／關閉搜尋圖示一併修正 | `ui/Common.kt`（`MinTouchTarget`）、`ui/bookkeeping/RecordEditorScreen.kt`、`ui/bookkeeping/BookkeepingScreen.kt` |
| 明細頁帳目單下方有紅色方框 | 滑動刪除的紅色背景原本佔滿整個項目高度，而卡片上下各有 3dp 留白，閒置時就露出一圈紅邊。改成紅色區塊與卡片**同高同寬**（同樣的 3dp 垂直留白＋同樣圓角），只有真的滑動時才會被拉出來看到 | `ui/bookkeeping/BookkeepingScreen.kt` |
| 明細頁帳目可以編輯 | 點一下帳目 → 開啟編輯頁（新增／編輯共用同一個編輯器），可改**收支類型、分類、金額、備註、日期**；儲存走 `RecordRepository.update()`，id 與建立時間保留，因此不影響垃圾桶狀態與自動記帳去重 | `ui/bookkeeping/RecordEditorScreen.kt`（取代 `AddRecordScreen.kt`）、`data/RecordRepository.kt` |
| 刪除的記錄保留垃圾桶 30 天，可還原 | 改為**軟刪除**：記錄加上 `deletedAt` 時間戳後移出明細與統計，放進垃圾桶保留 30 天；逾期在 App 啟動或回到前景時自動清除。刪除後 Snackbar 可直接「復原」，垃圾桶對話框可逐筆還原／永久刪除／清空 | `data/Record.kt`、`data/RecordRepository.kt`、`ui/trash/TrashDialog.kt` |
| 「改善UI介面」延伸：分析頁／預算頁 | 自訂分類在圖表與清單不再退成灰色預設圖示（改用含自訂分類的查表快取，`CategoryCatalog.find` 只看預設分類）；分析頁收支切換、預算頁月份切換與「編輯」列的觸控範圍統一拉到 48dp；順手清掉 `BudgetScreen` 的 always-true 編譯警告 | `ui/analysis/AnalysisScreen.kt`、`ui/budget/BudgetScreen.kt` |

> 資料格式向後相容：沒有 `deletedAt` 欄位的舊 JSON 一律視為「未刪除」，
> 未刪除的記錄也不會多寫這個欄位。Flutter 版舊資料移轉不受影響。

---

## 建置與安裝

```bash
cd MikuBookkeepingAPP-Kotlin

# 建置 debug APK
./gradlew :app:assembleDebug

# 執行單元測試
./gradlew :app:testDebugUnitTest
```

產物：`app/build/outputs/apk/debug/app-debug.apk`（約 18 MB）

安裝：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 版本組合（已實測可建置）

| 元件 | 版本 |
|---|---|
| Gradle | 9.6.0 |
| AGP | 9.4.1（**內建 Kotlin 支援**，不可再套用 `org.jetbrains.kotlin.android`） |
| Kotlin Compose 編譯器插件 | 2.3.20 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Compose BOM | 2025.09.00 |

> ⚠️ `root build.gradle.kts` 與 `app/build.gradle.kts` 的註解仍寫著舊版
> 「Gradle 9.3.1 / AGP 9.1.0」（AGP 已升到 9.4.1、wrapper 已指向 Gradle 9.6.0），
> 內容僅註解過時，不影響建置。

⚠️ **不要升級到最新的 Compose BOM（2026.09）或 androidx.core 1.19**：它們要求
`compileSdk 37`，而本專案的 `compileSdk` 目前固定在 36，會直接建置失敗。

⚠️ `material-icons-extended` 已被 Google 凍結在 **1.7.8** 且不再納入 Compose BOM，
必須明確指定版本。

---

## 資料移轉（升級後不會掉資料）

本專案刻意沿用與 Flutter 版**相同的 `applicationId`（`com.example.countapp`）**，
因此兩者共用同一個私有儲存空間，首次啟動時可以直接把舊資料搬過來。

```
App 啟動
  └─ MikuApplication.onCreate()
       ├─ AppContainer(建立儲存庫)
       └─ startMigration()  → 背景執行緒
            └─ FlutterPreferencesMigrator.migrateIfNeeded()
                 ├─ 記錄      flutter.records（StringList）＋ flutter.native_record_*
                 ├─ 預算      flutter.budget_YYYY_MM（Double）
                 ├─ 自訂分類  flutter.custom_expense_categories / income
                 ├─ 白名單    flutter.allowed_package_names
                 ├─ 規則表    flutter.custom_rules
                 └─ 開關與 AI 設定
```

### ⚠️ 為什麼需要 `FlutterLegacyCodec`

Flutter 的 `shared_preferences` 在 Android 上**不是**用 JSON 存 StringList：

```
"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu" + Base64(Java ObjectOutputStream 序列化)
```

Double 則是 `"VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu" + 數字`。

Flutter 版的原生程式碼就是直接 `JSONArray(字串)` 解析，**一定拋例外**，然後靜默
退回預設白名單——這就是「使用者自訂的包名白名單在原生端完全失效」的根因。
`FlutterLegacyCodec` 正確解碼這兩種格式，並有單元測試釘住（含 JSON 編碼的變體）。

**舊資料不會被刪除**，轉檔失敗時可以退回 Flutter 版。

> 想新舊版同時安裝做比較？把 `app/build.gradle.kts` 的 `applicationId` 改成別的
> 包名即可，但如此一來就讀不到舊資料。

---

## 專案結構

```
app/src/main/java/com/example/countapp/
├── MikuApplication.kt          進入點：建立容器、啟動資料移轉
├── MainActivity.kt             唯一的 Activity（Compose）
├── AppContainer.kt             手寫的服務容器（刻意不用 Hilt）
│
├── domain/                     純 Kotlin，無 Android 依賴 → 可 JVM 測試
│   ├── AmountFormatter.kt      金額格式化（小數、去尾零、千分位）
│   ├── PaymentTextAnalyzer.kt  廣告過濾 + 交易格式 + 金額/商家/方向提取
│   ├── AutoRecordDecision.kt   ⭐ 自動記帳判斷（通知與讀屏共用，可單元測試）
│   ├── RecordSearch.kt         尋找頁的比對邏輯
│   ├── ClassificationRules.kt  分類規則表引擎
│   └── CategoryCatalog.kt      預設分類（名稱／圖示／顏色）
│
├── data/
│   ├── Record.kt               記錄模型（JSON 進出）
│   ├── RecordRepository.kt     記錄儲存庫（StateFlow，Compose 自動重繪）
│   ├── BudgetRepository.kt     每月預算
│   ├── CategoryStore.kt        自訂分類
│   ├── SettingsStore.kt        所有設定 + 白名單預設值（唯一來源）
│   ├── FlutterLegacyCodec.kt   ⚠️ Flutter 舊資料解碼器
│   ├── FlutterPreferencesMigrator.kt
│   └── AccessibilityProbeLog.kt
│
├── notification/
│   ├── PaymentNotificationListenerService.kt  自動記帳主來源（通知）
│   ├── PaymentAccessibilityService.kt         ⭐ 無障礙讀屏自動記帳 + 讀屏診斷
│   ├── AutoRecordGuard.kt                     跨來源去重（同一筆只記一次）
│   ├── RecordNotifier.kt                      系統通知與預算提醒
│   └── AiClassifier.kt                        AI 分類（失敗自動退回規則表）
│
└── ui/
    ├── MikuApp.kt              主題、底部導覽、中央記帳按鈕、全螢幕覆蓋層（編輯／尋找）
    ├── Common.kt               共用元件（SectionCard、MonthPickerDialog、MinTouchTarget…）
    ├── CategoryIcons.kt        iconKey → ImageVector
    ├── theme/Theme.kt          配色（與 Flutter 版一致）
    ├── bookkeeping/            明細頁 + 記帳／編輯頁 + 尋找頁
    │   ├── BookkeepingScreen.kt    融合式上方 bar（標題／月份／統計）、日期分組、左滑動作
    │   ├── RecordCard.kt           記錄卡片（明細頁與尋找頁共用）
    │   ├── SearchScreen.kt         獨立的尋找頁（不分月份搜尋全部記錄）
    │   └── RecordEditorScreen.kt   新增／編輯共用（editing = null 即為新增）
    ├── trash/TrashDialog.kt    垃圾桶（還原／永久刪除／清空；入口在「我的」頁）
    ├── budget/                 預算頁
    ├── analysis/               分析頁（Canvas 圓環圖）
    └── profile/                我的頁 + 各種設定對話框
```

---

## 功能對照

| 功能 | 狀態 |
|---|---|
| 記帳明細（月份篩選、依日期分組、每日小計、**左滑露出刪除／編輯方塊**） | ✅ |
| 上方 bar 四等分：月份選擇器(左) ｜ 支出 ｜ 收入 ｜ 結餘（同一塊藍底） | ✅ |
| **尋找頁**（獨立全螢幕頁面，不分月份搜尋分類／備註／金額／日期） | ✅ |
| 新增記錄（支出／收入、分類九宮格、**支援小數的數字鍵盤**、備註、日期） | ✅ |
| **編輯記錄**（點一下帳目 → 改收支／分類／金額／備註／日期） | ✅ |
| **自動記錄確認頁**（MPay 自動記帳後跳出，補分類／備註；關閉＝保留自動分類） | ✅ 需實機驗證 |
| **刪除進垃圾桶**（軟刪除保留 30 天、可還原、可永久刪除、可清空；入口在「我的」頁） | ✅ |
| 自訂分類（名稱／圖示／顏色，可刪除） | ✅ |
| 預算（整月預算設定、進度、剩餘、超支警示） | ✅ |
| **各分類預算**（可只設定要管控的分類；不設定就不顯示進度條）與**進度條上的警示**（80% 警示／超支，顏色＋圖示＋文字） | ✅ |
| **分類規則表字條**（詞條以字條呈現、可單獨刪除／逐條新增）與**我的頁設定分組排序**（垃圾桶已併入「資料」群組） | ✅ |
| 分析（支出／收入、圓環圖、**扇形佔比標註**、分類佔比清單） | ✅ |
| 通知監聽自動記帳 | ✅（已改為直接寫入，無握手協定） |
| **無障礙讀屏自動記帳**（微信／支付寶／AlipayHK／MPay，含讀屏診斷紀錄） | ✅ 程式路徑完整，**未經實機驗證** |
| 廣告推播過濾（三段式：白名單 → 廣告字樣 → 交易格式） | ✅ |
| 支付 App 包名白名單（含微信、支付寶、MPay 預設值） | ✅ |
| 分類規則表（內建 + 自訂，可編輯詞條） | ✅ |
| 系統通知 + 預算提醒 | ✅ |
| 背景常駐通知 | ✅ |
| AI 分類（OpenAI 相容端點，失敗退回規則表） | ✅ 已實作，未對真實端點驗證 |
| **大頭貼上傳（image_picker）** | ❌ 未移植 |
| 隱私設定頁 | ❌ 未移植（Flutter 版本來就是空殼項目） |

---

## 測試

```bash
./gradlew :app:testDebugUnitTest
```

| 測試檔 | 涵蓋 |
|---|---|
| `AmountFormatterTest` | 小數、去尾零、四捨五入、浮點誤差、千分位、輸入中字串 |
| `PaymentTextAnalyzerTest` | **實際 MPay 廣告全文**不會被記錄、9 種真實交易通知不會被誤殺、金額提取、時間/日期/訂單號不誤判 |
| `ClassificationRulesTest` | 內建規則、自訂覆蓋、合併順序 |
| `FlutterLegacyCodecTest` | 平台編碼與 JSON 編碼的 StringList、Double、日期、記錄 JSON 來回轉換 |
| `SettingsAndBudgetTest` | 三個預設 App、精確包名比對、空清單持久化、清單正規化、損壞設定及預算鍵名格式 |
| `RecordTrashTest` | **軟刪除／還原／垃圾桶統計隔離／30 天邊界（第 29 天保留、第 30 天清除）／編輯更新與持久化／JSON 相容性**（用純記憶體的 `SharedPreferences` 假物件，不需要 Robolectric） |
| `CategoryStoreTest` | 自訂分類的新增／刪除／持久化，以及 `findAny` 必須找得到自訂分類（`CategoryCatalog.find` 找不到——這是原本圖示變灰色的根因） |
| `RecordSearchTest` | 尋找頁的比對邏輯：分類／備註／金額／日期、大小寫、前後空白、支出負號、維持原排序、找不到時空清單 |
| `AutoRecordDecisionTest` | 自動記帳判斷（通知與讀屏共用）：支付成功→支出、轉賬收入→收入、廣告不記、非交易不記、取不到金額不記、**交易失敗不記**、**付款頁的「收款方／付款方」欄位不會讓收支方向顛倒**、**讀屏必須有成功字樣（輸入中／轉賬請求／待確認收款等未完成頁面都不可記）**、備註組法、去重指紋 |
| `AutoRecordGuardTest` | 自動記帳去重：同時間窗同金額只記一次、**收入與支出同金額不會被誤認為同一筆**、不同金額／App 各自獨立、超過時間窗可再記、畫面指紋紀錄可跨服務重啟、過期可再記 |
| `BudgetStatusTest` | 預算警示門檻（v2.4）：未設定／0／負數／NaN／無限大一律 UNKNOWN 且不顯示進度、花費為負或 NaN 視為 0、剛好 80% 算警示、99% 仍警示、**剛好 100% 是警示不是超支**、>100% 超支並算出超出金額、進度比例夾在 0..1、小額預算百分比捨去、月份與分類共用同一套門檻 |
| `CategoryBudgetTest` | 分類每月預算儲存（v2.4）：**未設定回 null／空 map（不是 0）**、單分類設定／覆蓋／多分類並存、四捨五入 2 位、個別清除不影響其他分類、清空後不留空殼、0 或負數視為清除、空白名稱不寫入、**與整月預算（`budget_YYYY_MM` Float）互不覆蓋且不污染 `allMonths()`**、JSON 損毀時當未設定且之後仍可修復 |

目前共 **199 個測試，全數通過**（`:app:testDebugUnitTest`）。

### 獨立驗收

`docs/驗收報告.md` 是獨立驗收者（另一位 agent）針對這次四項需求所做的逐項查核，
含 PASS/PARTIAL/FAIL 判定、缺陷清單與建置證據。驗收過程抓到的缺陷（編輯畫面觸控穿透、
還原後項目卡在已滑出狀態、日期選擇器時區少一天、`update()` 可能讓垃圾桶記錄復活…）
都已修正並補上對應測試。

> ⚠️ 單元測試需要 `testImplementation("org.json:json")`：Android 的 `org.json`
> 在 JVM 測試中只是 empty stub，呼叫就拋例外。

---

## 已知限制

1. **無障礙自動記錄未經實機驗證**。判定邏輯（`AutoRecordDecision`／
   `PaymentTextAnalyzer`）已有單元測試，但「微信／支付寶／AlipayHK／MPay 的付款
   結果畫面到底讀不讀得到文字」只能在實機確認：請開啟服務後完成一次付款，再到
   「我的 → 無障礙自動記錄」看診斷紀錄（✅ 已自動記帳／🟡 看到類似交易但沒有
   成功字樣／❌ 節點樹沒有文字）。若某個 App 是 ❌（自繪視圖／WebView），
   該 App 只能靠通知監聽。
2. **通知與讀屏同時開啟時靠時間窗去重**。`AutoRecordGuard` 用「包名＋金額＋90 秒」
   判斷，同一筆消費在 90 秒內只會記一次；若兩個來源相隔超過 90 秒（極端情況）
   仍可能各記一筆，發現時可從垃圾桶刪除。
3. **MPay 確認頁依賴通知點擊**（App 在背景時）。Android 10+ 不允許背景服務直接
   啟動畫面，因此背景情境是「通知 → 點擊 → 確認頁」。
4. **AI 分類未經真實端點驗證**。程式碼路徑完整（含逾時與錯誤退回），但沒有
   實際打過 API。
5. **儲存用 SharedPreferences + JSON**，不是 Room。以個人記帳的資料量
   （數百到數千筆）足夠，且讓資料移轉單純很多。若資料量成長到上萬筆，
   建議改用 Room。
6. **`android:isAccessibilityTool="false"`**：誠實標示本 App 不是無障礙輔助工具。
   Android 13+ 的 sideload 安裝需要使用者額外允許「限制設定」才能啟用無障礙服務。
7. **未上架 Google Play**：使用無障礙 API 讀取其他 App 的付款畫面屬於
   Play 政策限制用途，此版本以 sideload 為前提。
8. **圓餅圖標籤的位置是近似最佳解**：標籤水平的畫在色帶中線；
   當分類剛好落在 3 點／9 點方向時，該處的水平空間只有色帶厚度那麼窄，
   標籤可能稍微壓到相鄰扇形（< 4% 或塞不下的扇形一律不標）。
9. **自動記錄只能認字，不能理解畫面**：若使用者事後回頭重看同一張交易結果頁，
   靠畫面指紋（30 分鐘）與時間窗可以擋掉大部分重複；但隔很久（或頁面文字
   因時間戳而不同）再重看仍可能多記一筆，發現時可從垃圾桶刪除。
10. **新增／編輯頁本次未經實機驗證**：已在 Pixel_10a 模擬器驗證關閉鈕、返回手勢、分類獨立捲動與備註鍵盤避讓；實機廠商差異及 Android 26–29 仍需確認。
11. **完整應用清單限目前 Android 使用者**：工作資料夾或其他使用者的 App 不會跨使用者列出；本 App、系統設定與 System UI 排除。為列出任意已安裝來源使用 `QUERY_ALL_PACKAGES`，此版以側載為前提，Google Play 發佈前需要另外審核權限用途。
12. **分類預算只能對目前存在的支出分類設定**：已刪除的分類或收入分類不會出現在
   「各分類預算」清單（只是顯示不到，不影響其他功能）。
