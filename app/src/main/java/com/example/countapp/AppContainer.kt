package com.example.countapp

import android.content.Context
import com.example.countapp.data.BudgetRepository
import com.example.countapp.data.CategoryStore
import com.example.countapp.data.FlutterPreferencesMigrator
import com.example.countapp.data.Record
import com.example.countapp.data.RecordRepository
import com.example.countapp.data.SettingsStore
import com.example.countapp.notification.AiClassifier
import com.example.countapp.notification.RecordNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全 App 共用的服務容器。
 *
 * 刻意不使用 DI 框架：依賴數量少，手寫容器更好讀，也不必額外賣註解處理器的
 * 建置時間與相容性風險。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** 原生版自己的儲存檔（與 Flutter 版分開，舊資料只讀不寫）。 */
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Flutter 版留下的舊檔案（僅用於資料移轉）。 */
    private val legacyPrefs = appContext.getSharedPreferences(
        FlutterPreferencesMigrator.LEGACY_PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    val settingsStore: SettingsStore = SettingsStore(prefs)
    val recordRepository: RecordRepository = RecordRepository(prefs)
    val budgetRepository: BudgetRepository = BudgetRepository(prefs)
    val categoryStore: CategoryStore = CategoryStore(prefs)
    val notifier: RecordNotifier = RecordNotifier(appContext)

    /** 通知文字分類器（AI 失敗時自動退回規則表）。 */
    val aiClassifier: AiClassifier = AiClassifier()

    private val migrator = FlutterPreferencesMigrator(
        legacyPrefs = legacyPrefs,
        recordRepository = recordRepository,
        budgetRepository = budgetRepository,
        categoryStore = categoryStore,
        settingsStore = settingsStore,
    )

    private val _migrationResult =
        MutableStateFlow<FlutterPreferencesMigrator.MigrationResult?>(null)

    /** 最近一次資料移轉的結果（沒有轉檔時為 null）。 */
    val migrationResult: StateFlow<FlutterPreferencesMigrator.MigrationResult?> =
        _migrationResult.asStateFlow()

    /**
     * 自動記錄後「等待使用者補分類／備註」的記錄佇列（目前是 MPay）。
     *
     * 由通知監聽或無障礙讀屏服務加入，UI 觀察到之後浮出圓球（見 `ui/MikuApp.kt`
     * 的 `AutoRecordBall`），使用者點了才開編輯頁；
     * 使用者關閉頁面就代表「保留自動分類」，所以從佇列移除即可。
     *
     * ⚠️ 用佇列而不是單一值：MPay 常常「通知」與「讀屏」幾乎同時各記一筆，
     *    單一值會被後來的覆蓋，導致其中一筆永遠等不到使用者確認。
     */
    private val _pendingAutoRecordPrompts = MutableStateFlow<List<Record>>(emptyList())

    val pendingAutoRecordPrompts: StateFlow<List<Record>> = _pendingAutoRecordPrompts.asStateFlow()

    private val _appInForeground = MutableStateFlow(false)
    val appInForeground: StateFlow<Boolean> = _appInForeground.asStateFlow()

    fun setAppInForeground(value: Boolean) { _appInForeground.value = value }

    /**
     * 「請直接開啟確認頁」的一次性訊號。
     *
     * App 內的自動記錄只會浮出圓球（不打斷操作），但**通知**被點擊時使用者
     * 已經明確表達要看那一筆，這時就該直接開編輯頁（跟舊行為一致）。
     * UI 讀到 true 並開啟畫面後呼叫 [consumeAutoRecordPromptOpenRequest] 歸零。
     */
    private val _autoRecordPromptOpenRequest = MutableStateFlow(false)

    val autoRecordPromptOpenRequest: StateFlow<Boolean> =
        _autoRecordPromptOpenRequest.asStateFlow()

    /**
     * 請 UI 跳出「選分類／寫備註」的圓球（同一筆只排一次）。
     *
     * @param openEditor 通知被點擊時傳 true → UI 直接開啟編輯頁，不必再點一次圓球。
     */
    fun requestAutoRecordPrompt(record: Record, openEditor: Boolean = false) {
        // 用 update（CAS 重試）而不是先讀再寫：通知服務與讀屏服務幾乎同時
        // 各自呼叫時，先讀再寫會少排一筆確認頁。
        _pendingAutoRecordPrompts.update { pending ->
            val others = pending.filterNot { it.id == record.id }
            if (openEditor) (listOf(record) + others).take(MAX_PENDING_PROMPTS)
            else (others + record).takeLast(MAX_PENDING_PROMPTS)
        }
        if (openEditor) _autoRecordPromptOpenRequest.value = true
    }

    /** UI 已開啟（或決定不開）確認頁，把一次性訊號歸零。 */
    fun consumeAutoRecordPromptOpenRequest() {
        _autoRecordPromptOpenRequest.value = false
    }

    /** 使用者已處理（或略過）佇列中的第一筆，換下一筆。 */
    fun clearAutoRecordPrompt() {
        _pendingAutoRecordPrompts.update { it.drop(1) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 先本機記帳並顯示浮球，AI 分類在背景補上，不延遲付款提示。 */
    fun refineAutoRecordCategory(record: Record, text: String) {
        if (!settingsStore.useAiClassification || !settingsStore.isAiConfigured) return
        scope.launch {
            val category = aiClassifier.classify(text, settingsStore)
            if (category == record.category) return@launch
            val refined = record.copy(category = category)
            if (recordRepository.updateIfUnchanged(record, refined)) {
                _pendingAutoRecordPrompts.update { pending ->
                    pending.map { if (it == record) refined else it }
                }
            }
        }
    }

    /**
     * 在背景執行 Flutter 版資料移轉。
     *
     * 放在背景是因為要讀檔＋解析 JSON：記錄多的使用者若在主執行緒做，
     * 啟動時會卡頓。完成後 [recordRepository] 的 StateFlow 會自動更新畫面。
     */
    fun startMigration() {
        scope.launch {
            val result = migrator.migrateIfNeeded()
            if (result.ran) {
                _migrationResult.value = result
            }
        }
    }

    fun acknowledgeMigration() {
        _migrationResult.value = null
    }

    /**
     * 回到前景時呼叫：在背景重新載入記錄。
     *
     * App 內有常駐的通知監聽服務，程序可能好幾天不重啟，若只在啟動時清一次，
     * 垃圾桶裡逾期（超過 30 天）的記錄會一直顯示「剩餘 0 天」。重新載入會順便
     * 清掉它們，而且放在背景執行，不會卡到 UI 執行緒。
     */
    fun refreshRecordsInBackground() {
        scope.launch { recordRepository.reload() }
    }

    companion object {
        /** 原生版的儲存檔名。刻意不叫 FlutterSharedPreferences，避免與舊資料混淆。 */
        const val PREFS_NAME: String = "miku_bookkeeping"

        /** 自動記錄確認頁最多排幾筆（避免極端情況下無限成長）。 */
        private const val MAX_PENDING_PROMPTS: Int = 10
    }
}

/** 從任何 Context 取得服務容器。 */
val Context.appContainer: AppContainer
    get() = (applicationContext as MikuApplication).container
