package com.example.countapp.domain

/**
 * 「這段文字該不該自動記一筆？」的判斷結果。
 *
 * @param amount 正數金額
 * @param isIncome true = 收入／收款，false = 支出
 * @param merchant 交易對象（可能為空）
 * @param note 建議備註
 * @param fingerprint 去重用的指紋（同一段文字會得到同一個值）
 */
data class AutoRecordDecision(
    val amount: Double,
    val isIncome: Boolean,
    val merchant: String,
    val note: String,
    val fingerprint: String,
)

/**
 * 自動記帳的共用判斷邏輯（通知來源與無障礙讀屏來源共用）。
 *
 * 這個物件刻意保持純 Kotlin、不碰 Android API，因此可以直接用 JVM 單元測試
 * 驗證「什麼樣的文字會被記成一筆、金額與方向對不對」——而這正是自動記帳最
 * 容易出錯、也最難在裝置上重現的部分。
 *
 * 判斷順序（與 Flutter 版移植過來的規則一致）：
 *   1. 廣告推播 → 不記
 *   2. 必須命中已知的交易結果句型 → 否則不記
 *   3. 必須能取出金額 → 否則不記
 *   4. 判斷收支方向、交易對象，並組出備註
 */
object AutoRecordDecisionMaker {

    /** 轉帳類關鍵字（用來組出「轉帳給 XXX」這種備註）。 */
    private val TRANSFER_KEYWORDS = listOf("轉賬", "轉帳", "转账", "transfer")

    /**
     * 「已完成」的嚴格句型，只有無障礙讀屏會用到。
     *
     * 為什麼讀屏要比通知嚴格：通知是「付款完成後才發送」，但讀屏會在整個操作
     * 過程看到畫面——包括「輸入金額」「確認轉賬」這種**還沒完成**的頁面。
     * 若只要求「轉賬 + 金額」，使用者一打開轉帳頁就會被記一筆。
     * 因此讀屏路徑額外要求明確的成功／完成字樣。
     */
    private val COMPLETION_PATTERNS: List<Regex> = listOf(
        Regex("(?:支付|付款|交易|消費|扣款|刷卡|轉賬|轉帳|转账|匯款|汇款|收款)\\s*(?:成功|完成)"),
        Regex("(?:成功|已)\\s*(?:支付|付款|交易|轉賬|轉帳|转账|消費|扣款|匯款|收款)"),
        Regex("(?:交易|付款|支付|轉賬|轉帳|转账)\\s*(?:已成功|已完成)"),
        // 收到款項：**必須連著款項語意**。
        // ⚠️ 不能只寫「收到」——「收到轉賬請求 MOP 100」是尚未完成的頁面。
        Regex(
            "(?:收到|已收到)[^，。,.!?；;]{0,12}" +
                "(?:轉賬|轉帳|转账|轉入|款項|红包|紅包|MOP|HK|[\\$¥€])",
            RegexOption.IGNORE_CASE,
        ),
        // 明確的入帳完成字樣
        Regex("(?:已入賬|已入帳|已存入|已到賬|已到帐|到賬成功|到帐成功|入賬成功|入帳成功)"),
    )

    /**
     * 「尚未完成」的頁面字樣（只有讀屏路徑會用到）。
     *
     * 讀屏會在整個操作過程看到畫面，例如「轉賬請求／待確認收款／請輸入金額」，
     * 這些都不是已完成的交易，遇到就一律不記。
     */
    private val PENDING_PATTERNS: List<Regex> = listOf(
        Regex("(?:轉賬|轉帳|转账|付款|收款|支付)\\s*(?:請求|要求|邀請)"),
        Regex("(?:請求|要求|邀請)\\s*(?:轉賬|轉帳|转账|付款|收款)"),
        Regex("(?:待確認|待接收|待收款|待付款|待支付|等待對方|等待付款|請收款|請付款)"),
        Regex("(?:請輸入|輸入金額|輸入收款金額|確認轉賬|確認付款|確認收款)"),
    )

    /**
     * 交易**失敗**字樣。
     *
     * ⚠️ [PaymentTextAnalyzer.PAYMENT_SIGNAL_PATTERNS] 為了收斂誤判，把
     * 「支付失敗／交易失敗」也當成交易句型（它確實是交易結果），所以這裡必須
     * 再加一道護欄，否則「支付失敗 ¥100」會被記成一筆 −100 的支出。
     */
    private val FAILURE_PATTERNS: List<Regex> = listOf(
        Regex("(?:支付|付款|交易|消費|扣款|刷卡|轉賬|轉帳|转账|匯款|汇款|收款)\\s*(?:失敗|失败|未成功|不成功)"),
        Regex("(?:失敗|失败)\\s*(?:支付|付款|交易|轉賬|轉帳|转账)"),
        Regex("(?:已取消|已撤銷|已撤销|交易取消|付款取消|已退款|退款成功)"),
    )

    /**
     * 方向判斷前要先中和掉的「欄位標籤」。
     *
     * ⚠️ 付款頁常出現「收款方：麥當勞」這種欄位標籤，而
     * [PaymentTextAnalyzer.isIncomeTransfer] 會把「收款」當成收入關鍵字，
     * 結果一筆支出被記成收入（金額方向與統計全錯）。讀屏看到的是整頁文字，
     * 最容易命中這種標籤，所以判定前先把標籤拿掉。
     */
    private val DIRECTION_LABEL_NOISE: List<String> = listOf(
        "收款方", "付款方", "收款人", "付款人", "收款帳戶", "付款帳戶",
        "收款账户", "付款账户", "收款方式", "付款方式",
    )

    /**
     * 判斷一段畫面／通知文字是否代表一筆已完成的交易。
     *
     * @param requireCompletionSignal 讀屏來源請傳 true（見 [COMPLETION_PATTERNS]）
     * @return null 表示不該自動記帳（廣告、非交易格式、取不到金額、尚未完成、交易失敗）
     */
    fun decide(text: String, requireCompletionSignal: Boolean = false): AutoRecordDecision? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return null

        if (PaymentTextAnalyzer.isAdvertisement(normalized)) return null
        if (!PaymentTextAnalyzer.hasPaymentSignal(normalized)) return null
        if (FAILURE_PATTERNS.any { it.containsMatchIn(normalized) }) return null
        if (requireCompletionSignal) {
            // 未完成的頁面（轉賬請求、待確認、請輸入金額…）一律不記
            if (PENDING_PATTERNS.any { it.containsMatchIn(normalized) }) return null
            if (COMPLETION_PATTERNS.none { it.containsMatchIn(normalized) }) return null
        }

        val amount = PaymentTextAnalyzer.extractAmount(normalized) ?: return null
        if (amount <= 0) return null

        val directionText = neutralizeDirectionLabels(normalized)
        val isIncome = PaymentTextAnalyzer.isIncomeTransfer(directionText)
        val merchant = PaymentTextAnalyzer.extractMerchant(normalized).orEmpty()

        return AutoRecordDecision(
            amount = amount,
            isIncome = isIncome,
            merchant = merchant,
            note = buildNote(normalized, merchant, isIncome),
            fingerprint = fingerprint(normalized),
        )
    }

    /** 把「收款方／付款方」這類欄位標籤拿掉，避免方向被誤判。 */
    private fun neutralizeDirectionLabels(text: String): String {
        var result = text
        DIRECTION_LABEL_NOISE.forEach { label -> result = result.replace(label, " ") }
        return result
    }

    /** 建立備註：優先記錄「轉帳給 XXX / 收到 XXX 轉帳」。 */
    fun buildNote(text: String, merchant: String, isIncome: Boolean): String {
        val isTransfer = TRANSFER_KEYWORDS.any { text.contains(it, ignoreCase = true) }

        return when {
            isTransfer && merchant.isNotEmpty() ->
                if (isIncome) "收到 $merchant 轉帳" else "轉帳給 $merchant"
            isTransfer -> if (isIncome) "轉賬收入" else "轉賬支出"
            merchant.isNotEmpty() -> "$merchant - ${PaymentTextAnalyzer.noteFrom(text)}"
            else -> PaymentTextAnalyzer.noteFrom(text)
        }
    }

    /**
     * 文字指紋（把空白與大小寫正規化後取 email 長度的雜湊）。
     *
     * 用途：無障礙讀屏會對同一個畫面連續收到多個事件，靠指紋 + 時間窗去重，
     * 避免同一筆消費被記成好幾筆。
     */
    fun fingerprint(text: String): String {
        val normalized = text.trim().replace(Regex("\\s+"), " ").lowercase()
        // 不用 MessageDigest：這裡只要穩定、可比較，不需要密碼學強度
        return normalized.hashCode().toUInt().toString(16) + "_" + normalized.length
    }
}
