package com.example.countapp.domain

/**
 * 預算警示等級。
 *
 * 刻意分成四級而不是「有沒有超支」的布林值：預算頁要在進度條上同時表達
 * 「還沒事」、「快滿了（>=80%）」與「已經超支（>100%）」三種狀態，
 * 而且必須用文字／圖示講清楚，不能只靠顏色（色盲使用者只看顏色分不出來）。
 */
enum class BudgetAlertLevel {
    /** 沒有設定預算（或預算金額無效），不該顯示進度、也不該顯示警示。 */
    UNKNOWN,

    /** 已設定預算且使用率 < 80%。 */
    NORMAL,

    /** 使用率 >= 80% 但還沒超過 100%（含剛好 100%）。 */
    WARNING,

    /** 使用率 > 100%，已超支。 */
    OVER,
}

/**
 * 某個預算（整月或單一分類）的使用狀態。
 *
 * [budget] 為 null 代表「未設定」——這是一個合法的狀態，不是錯誤：
 * 使用者可以完全不設定分類預算，UI 就不該畫出進度條，也不能用 0% 假裝有預算。
 */
data class BudgetStatus(
    /** 有效預算金額；null 表示未設定（或金額為 0／負數／非有限值）。 */
    val budget: Double?,
    /** 已花費金額（已正規化：非有限值或負數一律視為 0）。 */
    val spent: Double,
    /** 剩餘金額（`預算 - 花費`，可能為負）；未設定預算時為 0。 */
    val remaining: Double,
    /** 使用率（`花費 / 預算`），可能大於 1；未設定預算時為 0。 */
    val ratio: Double,
    /** 進度條用比例，已夾在 0..1；未設定預算時為 0。 */
    val progressFraction: Float,
    /** 顯示用百分比（無條件捨去，例如 79.9% → 79）；未設定預算時為 0。 */
    val usedPercent: Int,
    /** 超出預算的金額（未超支時為 0）。 */
    val overspent: Double,
    /** 警示等級。 */
    val level: BudgetAlertLevel,
) {
    /** 是否有可計算的預算（未設定時 false，UI 不該顯示進度條）。 */
    val isConfigured: Boolean get() = level != BudgetAlertLevel.UNKNOWN

    /** 即將超支（>=80% 且 <=100%）。 */
    val isWarning: Boolean get() = level == BudgetAlertLevel.WARNING

    /** 已超支（>100%）。 */
    val isOver: Boolean get() = level == BudgetAlertLevel.OVER
}

/** 警示門檻：使用率 >= 80% 就算「即將超支」。 */
const val BUDGET_WARNING_RATIO: Double = 0.8

/** 超支門檻：使用率 > 100% 才算「已超支」（剛好 100% 只是警示）。 */
const val BUDGET_OVER_RATIO: Double = 1.0

/**
 * 計算預算使用狀態（純函式，不依賴任何 Android API，可直接 JVM 單元測試）。
 *
 * 規則：
 *   - [budget] 為 null、0、負數或非有限值 → [BudgetAlertLevel.UNKNOWN]，
 *     不可拿來做除數，也不能回報 0% 的假進度。
 *   - [spent] 為負數或非有限值 → 視為 0（花費不可能是負的，異常輸入不該讓整頁
 *     顯示成負的使用率）。
 *   - 使用率 >= 0.8 → [BudgetAlertLevel.WARNING]；> 1.0 → [BudgetAlertLevel.OVER]。
 *     因此「剛好 80%」與「剛好 100%」都算警示，只有真的超過才叫超支。
 */
fun calculateBudgetStatus(spent: Double, budget: Double?): BudgetStatus {
    val safeSpent = if (spent.isFinite() && spent > 0) spent else 0.0
    val safeBudget = budget?.takeIf { it.isFinite() && it > 0 }

    if (safeBudget == null) {
        return BudgetStatus(
            budget = null,
            spent = safeSpent,
            remaining = 0.0,
            ratio = 0.0,
            progressFraction = 0f,
            usedPercent = 0,
            overspent = 0.0,
            level = BudgetAlertLevel.UNKNOWN,
        )
    }

    val ratio = safeSpent / safeBudget
    val level = when {
        ratio > BUDGET_OVER_RATIO -> BudgetAlertLevel.OVER
        ratio >= BUDGET_WARNING_RATIO -> BudgetAlertLevel.WARNING
        else -> BudgetAlertLevel.NORMAL
    }

    return BudgetStatus(
        budget = safeBudget,
        spent = safeSpent,
        remaining = safeBudget - safeSpent,
        ratio = ratio,
        progressFraction = ratio.coerceIn(0.0, 1.0).toFloat(),
        usedPercent = (ratio * 100.0).toInt(),
        overspent = (safeSpent - safeBudget).coerceAtLeast(0.0),
        level = level,
    )
}
