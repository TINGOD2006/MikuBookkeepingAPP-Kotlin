package com.example.countapp.domain

/** 只使用可解析收據的明確對象，成功提示與金額不當作自動備註。 */
object MpayNoteExtractor {
    fun fromScreen(text: String): String? {
        val receipt = MpayScreenAnalyzer.receipt(text) ?: return null
        return describe(receipt, text)
    }

    fun describe(receipt: AutoRecordDecision, text: String): String? {
        val name = receipt.merchant.trim().takeIf { it.isNotBlank() && it !in NON_PARTY_TEXT } ?: return null
        // 詳情頁也有「再次轉賬」等操作鈕；只採交易項目或完成狀態，不能掃整頁猜方式。
        val lines = text.split('\n', '｜').map(String::trim).filter(String::isNotEmpty)
        val transfer = lines.any {
            it.matches(Regex("(?:轉[賬帳]|轉入|轉出)成功")) ||
                it.matches(Regex("您已向.+轉[賬帳]"))
        } || lines.withIndex().any { (index, line) ->
            val label = Regex("^(?:付款項目|收款項目|交易類型)[：:]?\\s*(.*)$").matchEntire(line)
            val value = label?.groupValues?.get(1)?.ifBlank { lines.getOrNull(index + 1).orEmpty() }
            value in setOf("轉賬", "轉帳", "轉入", "轉出", "转账")
        }
        return when {
            transfer && receipt.isIncome -> "收到 $name 轉帳"
            transfer -> "轉帳給 $name"
            receipt.isIncome -> "收到 $name 收款"
            else -> "於 $name 消費"
        }
    }

    // 收據欄位缺值時，其下一行可能只是另一個標籤；保留原始備註供用戶手填。
    private val NON_PARTY_TEXT = setOf(
        "商戶名稱", "商戶", "收款方", "收款人", "付款方", "付款人", "付款方式", "收款方式",
        "付款項目", "收款項目", "對方賬號", "交易類型", "付款時間", "收款時間", "交易編號",
        "交易詳情", "錢包餘額", "MOP", "支付成功", "付款成功", "收款成功", "交易成功",
        "成功交易", "轉賬成功", "轉帳成功", "轉入成功", "轉出成功", "完成", "轉賬", "轉帳",
    )
}
