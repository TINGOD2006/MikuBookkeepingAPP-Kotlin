package com.example.countapp.domain

/**
 * 微信／支付寶的可讀付款結果節點。參考 AutoAccounting 的狀態、金額、對象、
 * 單號欄位設計；不使用其 Xposed Hook 或注入 JavaScript。
 * https://github.com/AutoAccountingOrg/AutoAccounting/tree/master/app/src/main/java/net/ankio/auto/xposed/hooks
 */
object PaymentScreenAnalyzer {
    private val packages = setOf("com.tencent.mm", "com.eg.android.AlipayGphone", "hk.alipay.wallet", "com.alipay.android.app")
    private val completion = Regex("^(?:支付成功|付款成功|交易成功|轉賬成功|轉帳成功|收款成功|支付完成|付款完成|已支付|已付款|已收款|已存入零錢|已到賬|已到帳)$")
    private val blocked = Regex("(?:支付|付款|交易|轉賬|轉帳)(?:失敗|失败|取消)|待支付|待付款|待確認|等待付款|輸入密碼|輸入金額|確認付款|退款成功|已退款")
    private const val NUMBER = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?"
    private val money = Regex("^[+\\-]?\\s*(?:[¥￥$]|RMB|CNY|HK\\$)?\\s*($NUMBER)\\s*(?:元|圓)?$", RegexOption.IGNORE_CASE)
    private val amountLabel = Regex("^(?:實付金額|實際付款|實付|付款金額|支付金額|交易金額|轉賬金額|金額)[：:]?\\s*(.*)$")
    private val merchantLabel = Regex("^(?:商戶全稱|商戶名稱|商戶|收款方|收款人|轉賬對象)[：:]?\\s*(.*)$")
    private val idLabel = Regex("^(?:交易單號|轉賬單號|訂單號|商戶單號)[：:]?\\s*(.*)$")

    fun decide(packageName: String, text: String): AutoRecordDecision? {
        if (packageName !in packages) return AutoRecordDecisionMaker.decide(text, requireCompletionSignal = true)
        val normalized = normalize(text)
        val lines = normalized.split('\n', '｜').map { it.trim() }.filter { it.isNotEmpty() }
        // 必須有獨立的完成節點；聊天文字或多筆帳單列表不能充當付款結果。
        val statuses = lines.filter { completion.matches(it) }
        if (statuses.size != 1 || blocked.containsMatchIn(normalized)) return null
        val status = statuses.single()
        fun field(pattern: Regex): String? {
            lines.forEachIndexed { index, line ->
                val match = pattern.matchEntire(line)
                if (match != null) return match.groupValues[1].ifBlank { lines.getOrNull(index + 1).orEmpty() }
            }
            return null
        }
        val labelledAmount = field(amountLabel)
        val candidates = if (labelledAmount != null) listOf(labelledAmount) else lines.filter {
            money.matches(it) && (it.contains('.') || it.any { char -> char in "¥￥$+-元圓" } ||
                it.startsWith("RMB", true) || it.startsWith("CNY", true))
        }.distinct()
        // 多個未標註的金額可能是餘額、優惠或列表，無法確定時不記。
        if (candidates.size != 1) return null
        val amount = money.matchEntire(candidates.single())?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0 } ?: return null
        val income = status in setOf("收款成功", "已收款", "已存入零錢", "已到賬", "已到帳") || candidates.single().startsWith("+")
        val merchant = field(merchantLabel).orEmpty().take(80)
        val transactionId = field(idLabel)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,80}")) }
        val note = if (merchant.isNotBlank()) {
            if (status.contains("轉")) (if (income) "收到 $merchant 轉帳" else "轉帳給 $merchant") else merchant
        } else status
        return AutoRecordDecision(amount, income, merchant, note,
            AutoRecordDecisionMaker.fingerprint(transactionId ?: "$status|$amount|$merchant"))
    }

    private fun normalize(text: String): String {
        val simplified = "转账额实户称单订败确认输码钱号际"
        val traditional = "轉賬額實戶稱單訂敗確認輸碼錢號際"
        return text.map { char ->
            val index = simplified.indexOf(char)
            if (index >= 0) traditional[index] else char
        }.joinToString("")
    }
}
