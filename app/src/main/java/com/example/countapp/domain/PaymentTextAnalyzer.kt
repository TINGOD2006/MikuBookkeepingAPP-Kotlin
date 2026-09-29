package com.example.countapp.domain

/**
 * 支付通知文字分析。
 *
 * 由 Flutter 版的 PaymentTextAnalyzer.kt 移植（規則完全相同），
 * 負責判斷「這則文字像不像一筆交易」、「金額是多少」、
 * 「是收入還是支出」、「對象是誰」。
 *
 * 呼叫端：
 *   - PaymentNotificationListenerService：讀取通知欄的支付通知
 *   - PaymentAccessibilityService：無障礙讀屏探針
 *
 * ⚠️ 規則刻意收得很緊。舊版只要求「白名單包名 + 任一寬鬆關鍵字」，
 *    而關鍵字包含單獨的「成功」「交易」「$」以及 App 名稱「MPay」，
 *    這些在廣告文案裡同樣會出現，導致「海鮮自助晚餐 268起」這類
 *    廣告被記成一筆 268 元的消費。
 */
object PaymentTextAnalyzer {

    /**
     * 硬廣告字樣：真實的交易通知（付款／轉賬成功）不會出現這些行銷用語。
     *
     * ⚠️ 只收錄「明確屬於推廣文案」的詞，避免誤殺真實交易。
     *    例如「訂閱」「自助餐」「元」「全單」等可能出現在正常消費描述中的詞
     *    刻意不收錄。
     */
    val AD_MARKERS: List<String> = listOf(
        "秒殺", "搶購", "限量", "特價", "抵價", "至抵", "震撼價", "優惠價",
        "優惠券", "優惠碼", "限時優惠", "獨家優惠", "會員優惠", "生日優惠",
        "折扣", "半價", "五折", "折上折", "買一送一",
        "抽獎", "中獎", "恭喜", "著數", "快閃", "期間限定", "有獎活動",
        "免費領取", "立即下載", "立即搶", "即刻入", "新品上市",
        "尊享", "專享", "推廣", "廣告", "推薦好友", "邀請碼", "填問卷",
        "積分兌換", "限量發售",
    )

    /**
     * 已知的交易通知格式。必須命中其中一項，才可能是真實交易。
     *
     * 刻意只收錄「交易結果」的固定語句，而不是單獨的「支付」「交易」
     * 「成功」等字，因為那些字在廣告文案中也會出現。
     */
    val PAYMENT_SIGNAL_PATTERNS: List<Regex> = listOf(
        // 支付／交易／轉賬／收款 + 結果：支付成功、交易成功、轉賬成功、收款成功
        Regex(
            "(?:支付|付款|交易|消費|扣款|刷卡|匯款|汇款|收款|轉賬|轉帳|转账|轉出|轉入)\\s*(?:成功|完成|已成功|失敗|失败)"
        ),
        // 結果 + 動作：成功交易、成功轉賬、成功付款、成功收款
        Regex("成功\\s*(?:支付|付款|交易|轉賬|轉帳|转账|消費|扣款|匯款|轉出|轉入|收款)"),
        // 已支付／已扣款／已轉賬
        Regex("(?:已|經|经)\\s*(?:支付|付款|扣款|轉賬|轉帳|转账|匯出|匯入|收款|消費)"),
        // 轉賬動詞本身（廣告文案不會出現「轉賬／轉帳」）
        Regex("(?:轉賬|轉帳|转账|轉出|轉入|匯出|匯入|匯款|汇款)"),
        // 收到款項：收到 XXX 轉賬、入賬 MOP100
        Regex("(?:收到|入賬|入帳).{0,12}(?:轉賬|轉帳|转账|款項|金額|MOP|HK)"),
        // 支付名詞 + 帶貨幣標記的金額，例如「消費 HK$1,234.50」「交易 MOP50」
        // （銀行／支付 App 的對帳通知常沒有「成功」字樣；
        //   但金額必須帶貨幣代碼或符號，廣告的「268起」這類裸數字不算）
        Regex(
            "(?:消費|交易|付款|支付|扣款|刷卡|轉賬|轉帳|转账|匯款|汇款|金額)\\s*[：:]?\\s*" +
                "(?:MOP|HK|RMB|CNY|USD|TWD|NTD|NT|澳門幣|人民幣|港幣|[\\$¥€£])\\s*\\$?\\s*\\d",
            RegexOption.IGNORE_CASE,
        ),
        // 明確的金額欄位
        Regex("(?:交易金額|付款金額|消費金額|扣款金額|轉賬金額|金額)\\s*[：:]"),
        // 常見支付 App 的交易描述
        Regex("(?:你已支付|您已支付|已成功付款|付款給|支付給|轉賬給)"),
        // 英文
        Regex(
            "(?:payment|transaction|transfer)\\s+(?:successful|completed|sent|received|of)",
            RegexOption.IGNORE_CASE,
        ),
        Regex("you\\s+(?:paid|received|sent)", RegexOption.IGNORE_CASE),
    )

    /** 是否為行銷／廣告推播 */
    @JvmStatic
    fun isAdvertisement(text: String): Boolean =
        AD_MARKERS.any { text.contains(it, ignoreCase = true) }

    /** 是否符合已知的交易通知格式 */
    @JvmStatic
    fun hasPaymentSignal(text: String): Boolean =
        PAYMENT_SIGNAL_PATTERNS.any { it.containsMatchIn(text) }

    /**
     * 這則文字是否「像一筆交易」：不是廣告，且符合已知交易格式。
     *
     * 金額是否存在由呼叫端另外確認（符合格式但取不到金額要視為失敗，
     * 而不是當成廣告丟掉）。
     */
    @JvmStatic
    fun isPaymentText(text: String): Boolean {
        if (text.isBlank()) return false
        if (isAdvertisement(text)) return false
        return hasPaymentSignal(text)
    }

    /** 判斷是否為「收入」轉帳（收到錢）；否則視為支出（付錢出去） */
    @JvmStatic
    fun isIncomeTransfer(text: String): Boolean {
        val incomeKeywords = listOf(
            "收到", "入賬", "入帳", "轉入", "转入", "收款", "來自", "来自",
            "匯入", "汇入", "進賬", "進帳", "收入", "credited", "received", "deposit",
        )
        return incomeKeywords.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * 從文字中提取金額。
     *
     * 依序嘗試：貨幣代碼／符號在前的格式、貨幣單位在後、金額欄位、
     * 裸小數、緊跟在交易動詞後的數字。
     */
    @JvmStatic
    fun extractAmount(text: String): Double? {
        val currencyPatterns = listOf(
            // 1) 貨幣代碼／符號在數字前，例如「成功轉賬MOP1.00」「HK$1,234.5」
            Regex(
                "(?:MOP|HK|RMB|CNY|USD|EUR|TWD|NTD|JPY|澳門幣|澳门币|人民幣|人民币|港幣|港币|美元|歐元|欧元|台幣|台币|元)" +
                    "\\s*\\$?\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)",
                RegexOption.IGNORE_CASE,
            ),
            // 2) 貨幣單位在數字後，例如「12.00 元」「1,234.50 澳門幣」
            Regex(
                "((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)\\s*" +
                    "(?:元|圓|块|塊|澳門幣|澳门币|人民幣|人民币|港幣|港币|美元|歐元|欧元|台幣|台币)"
            ),
            // 3) 明確的金額欄位，例如「金額 268」「交易金額：MOP 30」
            Regex(
                "(?:交易金額|付款金額|消費金額|扣款金額|轉賬金額|金額)\\s*[：:]?\\s*\\$?\\s*" +
                    "((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)"
            ),
            // 4) 通用貨幣符號
            Regex("[\\$¥€£]\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)"),
        )
        for (pattern in currencyPatterns) {
            for (match in pattern.findAll(text)) {
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull()
                if (amount != null && amount > 0) return amount
            }
        }

        // 5) 裸數字（含小數），但要排除時間(HH:mm:ss)、日期(2026-09-10)、
        //    訂單號(2026091003453572166504)、電話號碼等。
        val bareNumber = Regex(
            "(?<![\\d:./-])((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{1,2})(?![\\d:./-])"
        )
        for (match in bareNumber.findAll(text)) {
            val amount = match.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null && amount > 0) return amount
        }

        // 6) 最後手段：緊跟在交易動詞後的數字（整數也接受），例如「支付 50」
        //
        // ⚠️ 舊版是「支付/成功/MOP 等關鍵字後 40 字內的第一個整數」，
        //    範圍過寬，會把廣告文案裡的價格（例如「海鮮自助晚餐 268起」）
        //    當成交易金額。這裡改成要求數字必須緊接在交易動詞之後。
        val afterVerb = Regex(
            "(?:轉賬|轉帳|转账|轉出|轉入|支付|付款|消費|扣款|匯款|汇款)\\s*(?:了|給|至|金額)?\\s*" +
                "(?:MOP|HK\\$|RMB|CNY|USD|¥|\\$)?\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)(?!\\d)",
            RegexOption.IGNORE_CASE,
        )
        for (match in afterVerb.findAll(text)) {
            val amount = match.groupValues[1].replace(",", "").toDoubleOrNull()
            if (amount != null && amount > 0) return amount
        }
        return null
    }

    /**
     * 從通知中提取商家名稱／轉帳對象。
     *
     * 依語意優先順序匹配：轉出對象 → 轉入來源 → 泛用「給 XXX」→「- XXX」。
     */
    @JvmStatic
    fun extractMerchant(text: String): String? {
        val patterns = listOf(
            Regex("(?:轉賬|轉帳|转账|转帐|付款|支付|匯款|汇款|畀)\\s*給\\s*([^\\s,，。]+)"),
            Regex("向\\s*([^\\s,，。]+)\\s*(?:轉賬|轉帳|转账|转帐|付款|支付|匯款|汇款)"),
            Regex("(?:收到|來自|来自)\\s*([^\\s,，。]+)\\s*(?:轉賬|轉帳|转账|转帐|匯款|汇款|的)"),
            Regex("給\\s*([^\\s,，。]+)"),
            Regex("由\\s*([^\\s,，。]+)\\s*(?:轉賬|轉帳|轉账|转账|匯款|汇款)"),
            Regex("-\\s*([^\\s,，。]+)"),
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val name = match.groupValues[1].trim()
            if (name.isNotEmpty()) return name
        }
        return null
    }

    /**
     * 清理文字：移除多餘空格與特殊字符，保留中文、英文、數字。
     *
     * 取不到有效內容時回傳「無備註」。
     */
    @JvmStatic
    fun cleanText(text: String): String {
        if (text.isEmpty()) return "無備註"
        var cleaned = text.replace(Regex("\\s+"), " ").trim()
        cleaned = cleaned.replace(Regex("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]"), " ")
        cleaned = cleaned.trim()
        return cleaned.ifEmpty { "無備註" }
    }

    /** 備註用的文字：清理後截斷到 [maxLength] 個字。 */
    @JvmStatic
    fun noteFrom(text: String, maxLength: Int = 80): String {
        val cleaned = cleanText(text)
        return if (cleaned.length <= maxLength) cleaned else cleaned.substring(0, maxLength)
    }
}
