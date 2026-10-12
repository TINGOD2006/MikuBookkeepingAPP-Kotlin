package com.example.countapp.domain

import com.example.countapp.data.Record
import java.time.YearMonth
import java.time.ZoneId

/** 圓環圖、總額與列表共用同一篩選；null 代表全部月份。 */
fun recordsForAnalysis(records: List<Record>, type: String, month: YearMonth?,
                       zone: ZoneId = ZoneId.systemDefault()): List<Record> = records.filter {
    !it.isTrashed && (if (type == CategoryCatalog.TYPE_EXPENSE) it.amount < 0 else it.amount > 0) &&
        (month == null || YearMonth.from(it.localDate(zone)) == month)
}
