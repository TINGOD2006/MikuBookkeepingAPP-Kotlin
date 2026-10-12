package com.example.countapp.domain

/** 只接受帶有單號欄位標籤的值，不把電話、餘額或日期當成帳單編號。 */
object TransactionIdentity {
    private val field = Regex("(?:交易編號|交易编号|交易單號|交易单号|轉賬單號|转账单号|訂單號|订单号|商戶訂單號|商户订单号|商戶單號|商户单号)[：:]?\\s*([A-Za-z0-9_-]{8,80})(?![A-Za-z0-9_-])")
    fun extract(text: String): String? = field.find(text)?.groupValues?.get(1)
}
