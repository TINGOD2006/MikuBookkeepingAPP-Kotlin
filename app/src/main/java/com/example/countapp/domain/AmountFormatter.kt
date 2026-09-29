package com.example.countapp.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * 金額格式化工具。
 *
 * 由 Flutter 版 `lib/utils/amount_formatter.dart` 移植，規則完全相同：
 *   - 最多保留 [DECIMAL_PLACES] 位小數（四捨五入）
 *   - 整數金額不顯示小數（12.00 → "12"）
 *   - 有小數時移除尾端多餘的 0（12.50 → "12.5"）
 *
 * 這個物件刻意不依賴任何 Android API，因此可以直接用 JVM 單元測試驗證。
 */
object AmountFormatter {

    /** 金額的最大小數位數。 */
    const val DECIMAL_PLACES: Int = 2

    /**
     * 把金額四捨五入到 [DECIMAL_PLACES] 位小數。
     *
     * 使用 [BigDecimal] 而非 `value * 100` 再取整：後者在
     * 0.1 + 0.2 這類浮點誤差上會產生 0.30000000000000004 這種值，
     * 存檔前先正規化可避免誤差隨加總累積。
     */
    @JvmStatic
    fun round(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP)
            .toDouble()
    }

    /** 格式化金額（不含千分位），例如 `1234.5`、`12`、`-0.75`。 */
    @JvmStatic
    fun format(value: Double): String {
        if (!value.isFinite()) return "0"

        var text = String.format(Locale.US, "%.${DECIMAL_PLACES}f", round(value))
        if (text.contains('.')) {
            text = text.trimEnd('0')
            if (text.endsWith('.')) text = text.dropLast(1)
        }
        // 避免出現 "-0"
        return if (text.isEmpty() || text == "-0") "0" else text
    }

    /** 格式化金額並加上千分位，例如 `1,234.5`。 */
    @JvmStatic
    fun formatWithSeparator(value: Double): String {
        val text = format(value)
        val negative = text.startsWith("-")
        val body = if (negative) text.substring(1) else text
        val dot = body.indexOf('.')
        val integerPart = if (dot < 0) body else body.substring(0, dot)
        val decimalPart = if (dot < 0) "" else body.substring(dot)
        val grouped = groupThousands(integerPart)
        return (if (negative) "-" else "") + grouped + decimalPart
    }

    /**
     * 為「輸入中」的金額字串加上千分位。
     *
     * 與 [formatWithSeparator] 不同，這裡處理的是使用者正在輸入的內容，
     * 可能長成 `1234.` 這種結尾帶小數點、還無法 parse 成 Double 的字串，
     * 因此只對整數部分分組，小數部分原樣保留。
     */
    @JvmStatic
    fun formatInput(input: String): String {
        if (input.isEmpty()) return "0"

        val dot = input.indexOf('.')
        val integerPart = if (dot < 0) input else input.substring(0, dot)
        val decimalPart = if (dot < 0) "" else input.substring(dot)
        return groupThousands(integerPart) + decimalPart
    }

    /** 對整數部分每三位加一個逗號。 */
    private fun groupThousands(integerPart: String): String {
        if (integerPart.length <= 3) return integerPart

        val builder = StringBuilder()
        val firstGroup = integerPart.length % 3
        if (firstGroup > 0) {
            builder.append(integerPart, 0, firstGroup)
        }
        var index = firstGroup
        while (index < integerPart.length) {
            if (builder.isNotEmpty()) builder.append(',')
            builder.append(integerPart, index, index + 3)
            index += 3
        }
        return builder.toString()
    }
}
