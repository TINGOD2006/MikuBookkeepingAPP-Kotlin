package com.example.countapp.domain

/** MPay 的頁面節點判斷。輸入頁的「成功轉賬後無法撤回」只是提示，不能當完成證據。 */
internal object MpayScreenAnalyzer {
    private val complete = setOf("支付成功", "付款成功", "交易成功", "成功交易", "轉賬成功", "轉帳成功", "收款成功", "轉入成功", "轉出成功", "已到賬", "已到帳")
    private val incomeStatuses = setOf("收款成功", "轉入成功", "已到賬", "已到帳")
    private val expenseStatuses = setOf("支付成功", "付款成功", "轉賬成功", "轉帳成功", "轉出成功")
    private val blocked = Regex("失敗|失败|已取消|待付款|待支付|待確認|轉賬請求|轉帳請求|成功轉賬後無法撤回")
    private val money = Regex("^([+-]?)\\s*(?:MOP\\s*\\$?|澳門幣|HK\\$|\\$)?\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)$", RegexOption.IGNORE_CASE)
    private val amountLabel = Regex("^(?:交易金額|付款金額|支付金額|實付金額|轉賬金額|轉帳金額|收款金額|金額)[：:]?\\s*(.*)$")

    fun decide(text: String): AutoRecordDecision? {
        val lines = text.split('\n', '｜').map(String::trim).filter(String::isNotEmpty)
        if (blocked.containsMatchIn(text) || lines.any { it in setOf("下一步", "確認轉賬", "確認轉帳", "確認付款", "請輸入金額", "輸入金額", "輸入密碼", "退款成功", "交易記錄", "轉賬記錄") }) return null
        val statuses = lines.filter { it in complete }
        // 有些付款收據同時有頁面標題「支付成功」與「成功交易」；其他多筆狀態一律拒絕。
        if (statuses.size != 1 && !(statuses.size == 2 && statuses.toSet() == setOf("支付成功", "成功交易"))) return null
        val status = statuses.firstOrNull { it in expenseStatuses || it in incomeStatuses } ?: statuses.first()
        fun field(labels: Set<String>): String? {
            for ((index, line) in lines.withIndex()) {
                for (label in labels) {
                    if (line == label || line == "$label：" || line == "$label:") return lines.getOrNull(index + 1)
                    if (line.startsWith("$label：") || line.startsWith("$label:")) return line.substring(label.length + 1).trim()
                }
            }
            return null
        }
        fun withCurrencyValue(value: String?, index: Int): String? =
            if (value.equals("MOP", true)) lines.getOrNull(index + 1)?.let { "MOP $it" } else value
        val labelled = lines.mapIndexedNotNull { index, line ->
            amountLabel.matchEntire(line)?.groupValues?.get(1)?.let { value ->
                if (value.isNotBlank()) withCurrencyValue(value, index)
                else withCurrencyValue(lines.getOrNull(index + 1), index + 1)
            }
        }.distinct()
        val candidates = if (labelled.isNotEmpty()) labelled else lines.mapIndexedNotNull { index, line ->
            when {
                line.equals("MOP", true) -> withCurrencyValue(line, index)
                line.startsWith("MOP", true) || line.startsWith("澳門幣") -> line
                else -> null
            }
        }.distinct()
        if (candidates.size != 1) return null
        val match = money.matchEntire(candidates.single()) ?: return null
        val amount = match.groupValues[2].replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 } ?: return null
        val kind = field(setOf("交易類型", "收支類型"))
        val direction = when {
            status in incomeStatuses -> true
            status in expenseStatuses -> false
            match.groupValues[1] == "+" -> true
            match.groupValues[1] == "-" -> false
            kind in setOf("收入", "收款", "轉入", "收到轉賬") -> true
            kind in setOf("支出", "付款", "消費", "轉出") -> false
            field(setOf("付款方式")) != null -> false
            else -> return null // 只有「交易成功」不能推斷轉帳方向。
        }
        val merchant = field(setOf("商戶名稱", "商戶", "收款方", "收款人", "付款方", "付款人")).orEmpty().take(80)
        val id = field(setOf("交易編號", "交易單號", "商戶訂單號"))?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,80}")) }
        return AutoRecordDecision(amount, direction, merchant, merchant.ifBlank { status },
            AutoRecordDecisionMaker.fingerprint(id ?: "$status|$amount|$direction|$merchant"), id)
    }
}
