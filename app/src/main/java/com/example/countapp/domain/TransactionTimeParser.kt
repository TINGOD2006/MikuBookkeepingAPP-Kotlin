package com.example.countapp.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 從付款結果畫面／通知文字裡抓出「這筆交易的時間」。
 *
 * 為什麼需要它（無障礙讀屏的日期問題）：
 *   無障礙服務讀到的是**畫面**，而服務只在使用者操作支付 App 時才看到畫面，
 *   所以舊版直接把「讀到畫面的當下」當成交易時間。一旦同一個畫面被重複讀到
 *   （事件風暴、或使用者稍後回頭重看同一張交易結果頁），第二筆就會帶著
 *   **後面的日期**寫進明細——同一筆消費看起來像兩筆、而且日期還不一樣。
 *   畫面本身其實就寫著交易時間，優先用它，重複讀到才會落在同一天。
 *
 * 設計原則：
 *   1. **寧可回 null，不要猜錯**。回 null 時呼叫端會退回「當下時間」，
 *      至少還記得到帳；猜錯則會把一筆消費記到錯誤的日期，使用者更難察覺。
 *   2. **必須通過合理性檢查**。畫面常同時有「優惠券到期日」「帳單繳費期限」
 *      這類其他日期，因此解析結果必須落在「現在」附近的窗內才算數：
 *      不可比現在晚超過 [MAX_FUTURE_MS]（容忍對方畫面與本機的些微時差），
 *      也不可比現在早超過 [MAX_PAST_MS]。
 *   3. 純 Kotlin、不碰 Android API，因此可以用 JVM 單元測試釘住行為
 *      （見 `TransactionTimeParserTest`）。
 *
 * 支援的寫法（畫面上常見者）：
 *   - `2026-06-30 14:32:05`、`2026/06/30 14:32`、`2026年6月30日 14:32`、`2026.06.30 14:32`
 *   - `6月30日 14:32`（沒有年份 → 先用今年，若變成未來就改用去年）
 *   - `今天 14:32`、`昨天 21:05`
 *   - 日期與時間在節點樹裡是**兩個節點**（中間只有換行）也配得起來
 *
 * 刻意不支援：只有時間沒有日期（`14:32`）、12 小時制（`下午 2:32`）。
 * 這兩種在不確定當下日期／上下午時寧可放棄，交由呼叫端退回當下時間。
 */
object TransactionTimeParser {

    /** 解析結果最多可以比「現在」晚這麼多（容忍時差與裝置時鐘誤差）。 */
    private const val MAX_FUTURE_MS: Long = 2 * 60 * 1000L

    /**
     * 解析結果最多可以比「現在」早這麼多。
     *
     * 為什麼要有上限：讀屏看到的是「當下正在操作的畫面」，畫面上的交易時間
     * 若與現在差距過大（例如優惠券到期日、帳單期限），幾乎可以確定不是這筆
     * 交易的時間。超過就放棄解析（退回當下時間），而不是把消費記到別的日期。
     */
    private const val MAX_PAST_MS: Long = 7L * 24 * 60 * 60 * 1000L

    /** 日期與時間之間允許的字元數（兩個節點中間通常只有一個換行）。 */
    private const val MAX_GAP = 12

    /** 日期前面幾個字內出現這些字，就視為「有標籤的交易時間」，優先採用。 */
    private const val LABEL_WINDOW = 12

    private val absoluteDate = Regex(
        "(?<![\\d])(\\d{4})\\s*[-/年.]\\s*(\\d{1,2})\\s*[-/月.]\\s*(\\d{1,2})\\s*日?",
    )

    private val monthDayDate = Regex("(?<![\\d])(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日")

    /** 24 小時制時鐘：`14:32`、`14：32`、`14:32:05`。 */
    private val clock = Regex(
        "(?<![\\d:])([01]?\\d|2[0-3])\\s*[：:]\\s*([0-5]\\d)(?:\\s*[：:]\\s*([0-5]\\d))?(?![\\d:])",
    )

    private val relativeDay = Regex("(今天|今日|昨天|昨日)")

    /** 時間標籤（用來判斷這組日期時間是不是「交易時間」）。 */
    private val timeLabels = listOf("時間", "时间", "日期")

    /**
     * 解析交易時間。
     *
     * @param now 現在的毫秒數（可注入，方便測試）
     * @param zone 判讀日期時使用的時區
     * @return 交易時間的毫秒數；取不到或不合理時回傳 null（呼叫端應退回 [now]）
     */
    fun parse(
        text: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (text.isBlank()) return null

        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        for (candidate in candidates(text, today)) {
            val millis = toMillis(candidate.time, zone) ?: continue
            if (millis < now - MAX_PAST_MS) continue
            if (millis > now + MAX_FUTURE_MS) continue
            return millis
        }
        return null
    }

    // ========== 內部 ==========

    /** 一個候選時間（清單本身的順序就是優先順序）。 */
    private class Candidate(val time: LocalDateTime)

    /** 畫面文字裡的一組日期。 */
    private class DateHit(val start: Int, val end: Int, val date: LocalDate, val labelled: Boolean)

    /**
     * 依優先順序排出所有候選時間。
     *
     * 順序：有標籤的絕對日期 → 無標籤的絕對日期 → 只有月日 → 相對日期（今天／昨天）。
     * 呼叫端會逐個做合理性檢查，第一個通過的就是答案（例如畫面上先出現
     * 「優惠券 2026-07-30」再出現「交易時間 2026-06-30 14:32」時，
     * 前者會被窗內檢查刷掉，後者才是答案）。
     */
    private fun candidates(text: String, today: LocalDate): List<Candidate> {
        val result = mutableListOf<Candidate>()

        val hits = mutableListOf<DateHit>()
        absoluteDate.findAll(text).forEach { match ->
            val date = localDate(
                match.groupValues[1].toIntOrNull(),
                match.groupValues[2].toIntOrNull(),
                match.groupValues[3].toIntOrNull(),
            ) ?: return@forEach
            hits += DateHit(
                start = match.range.first,
                end = match.range.last + 1,
                date = date,
                labelled = hasTimeLabel(text, match.range.first),
            )
        }

        // 只有「6月30日」沒有年份：絕對日期已經吃掉的範圍要跳過，免得同一組重複配對
        monthDayDate.findAll(text).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            if (hits.any { it.start <= start && end <= it.end }) return@forEach
            val month = match.groupValues[1].toIntOrNull() ?: return@forEach
            val day = match.groupValues[2].toIntOrNull() ?: return@forEach

            // 年份先用今年；若這一天還沒到（跨年期間），去年才是對的。
            // 兩個都放進候選，讓合理性窗自己挑一個（一年只會有一個落在窗內）。
            val thisYear = localDate(today.year, month, day)
            val lastYear = localDate(today.year - 1, month, day)
            val labelled = hasTimeLabel(text, start)
            if (thisYear != null) {
                hits += DateHit(start, end, thisYear, labelled)
            }
            if (lastYear != null && lastYear != thisYear) {
                hits += DateHit(start, end, lastYear, labelled)
            }
        }

        hits.sortedWith(compareByDescending<DateHit> { it.labelled }.thenBy { it.start })
            .forEach { hit ->
                val time = timeAfter(text, hit.end) ?: return@forEach
                result += Candidate(LocalDateTime.of(hit.date, time))
            }

        // 相對日期（今天／昨天）優先度最低：真的沒有絕對日期才用
        relativeDay.findAll(text).forEach { match ->
            val time = timeAfter(text, match.range.last + 1) ?: return@forEach
            val offset = if (match.groupValues[1].startsWith("昨")) -1L else 0L
            result += Candidate(LocalDateTime.of(today.plusDays(offset), time))
        }

        return result
    }

    /**
     * 找出緊接在日期後面（允許換行等非數字字元）的時間。
     *
     * 為什麼要允許中間夾字元：節點樹常把「2026-06-30」與「14:32:05」放在
     * 兩個節點，讀出來會是兩行。反過來，中間若還有數字（例如
     * `2026-06-30 12345678 14:32`）就不是同一組，直接跳過。
     */
    private fun timeAfter(text: String, dateEnd: Int): LocalTime? {
        clock.findAll(text).forEach { match ->
            if (match.range.first < dateEnd) return@forEach
            val gap = text.substring(dateEnd, match.range.first)
            if (gap.length > MAX_GAP) return null // 後面的時間只會更遠
            if (gap.any { it.isDigit() }) return@forEach

            val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
            val minute = match.groupValues[2].toIntOrNull() ?: return@forEach
            val second = match.groupValues[3].toIntOrNull() ?: 0
            return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
        }
        return null
    }

    private fun hasTimeLabel(text: String, dateStart: Int): Boolean {
        val from = (dateStart - LABEL_WINDOW).coerceAtLeast(0)
        val prefix = text.substring(from, dateStart)
        return timeLabels.any { prefix.contains(it) }
    }

    private fun localDate(year: Int?, month: Int?, day: Int?): LocalDate? {
        if (year == null || month == null || day == null) return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun toMillis(time: LocalDateTime, zone: ZoneId): Long? =
        runCatching { time.atZone(zone).toInstant().toEpochMilli() }.getOrNull()
}
