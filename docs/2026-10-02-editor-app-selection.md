# 編輯器與自動記錄應用清單

## 行為

- 直向新增／編輯頁：標題固定，分類網格獨立捲動，數字鍵盤、備註、日期及保存固定在底部。
- 橫向矮視窗：分類與輸入分欄，使用較矮的數字鍵，避免分類或保存超出畫面。
- 備註的系統鍵盤開啟時，暫時替代數字鍵盤；返回手勢先收鍵盤，再次返回離開編輯器。右上關閉鈕可直接離開。
- 編輯器使用獨立全螢幕 Dialog，阻擋底部導覽／FAB 觸控；新增、一般編輯與自動記錄確認同時只呈現一個視窗。
- 「我的 → 自動記錄應用 → 管理」查詢目前 Android 使用者的已安裝 App，顯示名稱及完整包名；支援搜尋、系統 App 篩選、重新掃描、恢復預設。取消不寫入，儲存才生效。
- 本 App、系統設定及 System UI 為不可記錄來源，從選擇器排除。尚未安裝的預設／既有選擇仍顯示，避免選擇被默默丟棄。
- 預設只有微信 `com.tencent.mm`、支付寶 `com.eg.android.AlipayGphone`、MPay `com.macaupass.rechargeEasy`。保留升級前的用戶選擇，按「恢復預設」才重置。
- 通知與無障礙來源共同精確比對包名；無障礙服務即時更新監聽清單。明確取消所有勾選代表空清單，不恢復預設；空清單不會讓系統改成監聽所有 App。
- 選取應用只決定允許的來源，仍需開啟自動記錄及相應系統權限，且交易文字必須符合原有識別規則。

## 檔案分工

| 檔案 | 責任 |
| --- | --- |
| `ui/bookkeeping/RecordEditorScreen.kt` | 編輯狀態、視窗、返回、版面配置及保存 |
| `ui/bookkeeping/RecordCategoryGrid.kt` | 可捲動分類網格 |
| `ui/bookkeeping/RecordEditorInput.kt` | 備註、日期、數字键盤及金額輸入規則 |
| `ui/bookkeeping/AddCategoryDialog.kt` | 新增自訂分類 |
| `data/InstalledAppRepository.kt` | 背景查詢應用名稱及包名 |
| `ui/profile/AutoRecordAppsDialog.kt` | 清單載入、搜尋與暫存勾選 |
| `data/SettingsStore.kt` | 持久保存預設／選擇及可觀察清單 |
| `notification/PaymentAccessibilityService.kt` | 即時套用來源篩選，讀取與寫入前再次確認 |

原有頁籤、SharedPreferences 儲存格式、交易識別與通知服務保留。

## 驗證

- `:app:testDebugUnitTest`：199 項通過，包含三個預設、精確比對、空清單跨實例持久化、清單正規化與設定損壞處理。
- `:app:assembleDebug` 及 `:app:lintDebug` 通過。既有 lint 警告仍保留；只對下述必要權限抑制 `QueryAllPackagesPermission` 提示。
- Pixel_10a 模擬器：分類捲動前後數字鍵與保存的畫面座標相同；關閉鈕及邊緣返回均返回原頁。備註 IME 開啟仍保留保存／關閉，返回先收鍵盤。
- 2424×1080 橫向測試視窗：分類、所有數字鍵和保存均在畫面內，測試後恢復原尺寸。
- 應用選擇器：三個預設、系統應用列舉、包名搜尋、空清單保存及恢復預設已檢查；搜尋 Chrome 時鍵盤保持開啟，仍可勾選並保存，重開顯示已選四個，再恢復三個預設。
- 從搜尋開啟既有記錄：金額與分類預填正確，顯示刪除與儲存變更；關閉回到原搜尋頁，未修改記錄。
- 本次 ADB 未偵測到實機；未驗證實際微信、支付寶或 MPay 付款。

## 套件可見性

為满足「列出全部已安裝 App」而使用 `QUERY_ALL_PACKAGES`；清單僅在用戶開啟選擇器時於本機查詢。此側載版本不能以固定 `<queries>` 列出用戶尚未選取的任意來源。若日後上架 Google Play，需先審核此權限的政策適用性，或改為只列有啟動入口的 App。

參考：[Android 套件可見性](https://developer.android.com/training/package-visibility)、[宣告套件可見性](https://developer.android.com/training/package-visibility/declaring)。
