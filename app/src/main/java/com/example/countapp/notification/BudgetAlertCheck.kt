package com.example.countapp.notification

import com.example.countapp.AppContainer
import com.example.countapp.data.Record
import java.time.YearMonth

/**
 * 自動記帳後檢查該月是否超支並發出提醒。
 *
 * 通知監聽與無障礙讀屏只差在「怎麼發現一筆交易」，記錄之後的加值行為
 * （系統通知、預算提醒）應該完全一致，因此抽成共用函式。
 */
internal fun notifyIfBudgetExceeded(container: AppContainer, record: Record) {
    if (!container.settingsStore.budgetNotificationEnabled) return
    if (!record.isExpense) return

    val month: YearMonth = YearMonth.from(record.localDate())
    val budget = container.budgetRepository.load(month) ?: return
    if (budget <= 0) return

    val expense = container.recordRepository.totalExpense(
        container.recordRepository.forMonth(month)
    )
    container.notifier.showBudgetAlert(month.year, month.monthValue, expense, budget)
}
