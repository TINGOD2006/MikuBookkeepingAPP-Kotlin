# MPay、系統浮球與背景運作驗證

## 修正

- 底部依序為明細、預算、新增、分析、我的，五個按鈕均在同一 72dp 欄位。中心位置為畫面寬的 10%、34%、50%、66%、90%；內側中心距 16%，外側 24%。新增圓形縮為 44dp，保留 56dp 點擊範圍。
- MPay 不再走通知用途的通用文字判斷。讀屏必須有獨立完成節點、唯一可確認的交易金額和明確方向；提示、輸入、失敗、退款、多筆交易及方向不明的畫面不記。
- 捕捉到的原始誤記原因：轉帳輸入頁的「成功轉賬後無法撤回」被當成成功，錢包餘額被當成金額。此結構已加入遮蔽個人資料的回歸測試。
- 提示佇列只保存記錄 ID，重新啟動後從帳目庫取得目前資料。修改或刪除帳目同步更新提示；按編輯器原記錄 ID 關閉，避免刪除中的 A 已消失時誤關閉下一筆 B。
- 有背景待處理提示時，無障礙服務每兩秒核對浮球；中斷、視窗被移除、暫時建立失敗後可恢復。沒有待處理提示、App 在前景或浮球關閉時不執行重試；鎖定或螢幕關閉時隱藏。
- 防重記仍在背景執行，重複略過訊息不再寫入診斷；介面同時過濾既有重複訊息。Logcat 不再輸出付款全文。
- 長期通知監聽由 dataSync 改為 specialUse，附用途說明；通知使用 ongoing、不可點擊自動取消、只提醒一次及 App 入口，並顯示目前自動記錄狀態。斷線或 App 啟動時請求系統重綁通知服務。
- 增加電池最佳化狀態與背景運作設定入口；「關於」說明 Android 的停止及通知限制。

## 自動檢查

`gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --console=plain`

- 最終 215 個單元測試，0 失敗，Debug APK 建置成功。
- Lint：0 errors、42 warnings、3 hints，沒有宣稱清除全部警告。
- 回歸案例先失敗後修正，涵蓋 MPay 輸入頁、方向不明、多筆金額、付款單號、收入、雙完成節點次序、提示持久化及刪除提示的競態。
- 獨立檢查提出提示關閉競態及完成節點遍歷次序問題，兩者已修正並再次核對。

## Samsung SM-S9210，Android 16 / API 36

使用 Android SDK ADB 安裝及測試，無實際付款或轉帳操作。

- 使用者在舊版打開 MPay 轉帳輸入頁，捕捉到誤記事件。
- 安裝修正版後，使用者重複該操作。近期 8 筆 MPay 診斷均未記帳，包含相同不可撤回提示的輸入頁。
- 本次重現新增、取自錢包餘額的誤記已精確定位並移到垃圾桶，可還原。
- 導向圖示中心 x 約 109、368、713、973；新增按鈕中心 x 540（畫面寬 1080），位於同一底部欄。新增可開啟編輯器，Android 返回可關閉。
- 無障礙列表的可見重複略過文字數為 0。
- 使用既有帳目的通知入口建立測試提示，沒有新增或修改帳目。手機桌面上存在 `ACCESSIBILITY_OVERLAY`，`mHasSurface=true`、`isReadyForDisplay=true`，可拖動、點擊開編輯器及關閉。測試提示最後清空。
- 強制停止再啟動 App，App 內提示仍可還原。這部 Samsung 的強制停止會關閉無障礙授權；恢復使用者先前啟用的服務後，已保存提示再次建立系統浮球。未宣稱可自動繞過強制停止或權限撤銷。
- 常駐通知啟動後，服務 `isForeground=true`、`foregroundId=1001`、`types=0x40000000`（specialUse）。在 Android 16 手動滑除通知後，服務仍保持前景狀態；測試後恢復通知與原本開關值。
- 最終 APK 已再次安裝；暫時的 USB 保持喚醒設定恢復為測試前 0。

## 系統限制與未驗證範圍

一般 App 無法保證永不被系統或使用者停止。Android 14 起一般 ongoing 通知允許使用者個別滑除；系統通知權限、強制停止和無障礙授權仍由系統控制。未使用偽裝通話／媒體通知或其他規避方式。

- [Android 14 通知行為](https://developer.android.com/about/versions/14/behavior-changes-all)
- [使用者停止前景服務](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping)
- [前景服務類型](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [dataSync 時限](https://developer.android.com/develop/background-work/services/fgs/timeout)

本輪未執行真實付款、遠端 AI、重開機、大字體、橫向及長時間省電模式測試。MPay 完成頁的正向案例目前由節點樣本單元測試驗證，不能等同於真實交易完成的端到端驗證。

APK：`app/build/outputs/apk/debug/app-debug.apk`。
