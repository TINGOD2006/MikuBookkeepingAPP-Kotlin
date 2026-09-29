package com.example.countapp.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

/**
 * Flutter 舊資料解碼的單元測試。
 *
 * 這是整個改寫專案風險最高的一段：Flutter 的 shared_preferences 在 Android 上
 * 把 StringList 存成 `LIST_PREFIX + Base64(Java 序列化)`，而不是 JSON。
 * 解錯的話使用者的記帳記錄會在升級後全部消失，因此必須有測試釘住格式。
 */
class FlutterLegacyCodecTest {

    /** 依 Flutter 的實際編碼方式產生測試資料。 */
    private fun encodeLikeFlutter(list: List<String>): String {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(ArrayList(list)) }
        val base64 = Base64.getEncoder().encodeToString(bytes.toByteArray())
        return FlutterLegacyCodec.LIST_PREFIX + base64
    }

    // ========== StringList ==========

    @Test
    fun `可以解碼 Flutter 的平台編碼 StringList`() {
        val raw = encodeLikeFlutter(listOf("com.tencent.mm", "com.eg.android.AlipayGphone"))

        val decoded = FlutterLegacyCodec.decodeStringList(raw)

        assertEquals(listOf("com.tencent.mm", "com.eg.android.AlipayGphone"), decoded)
    }

    @Test
    fun `可以解碼 JSON 編碼的 StringList`() {
        val raw = FlutterLegacyCodec.JSON_LIST_PREFIX + JSONArray(listOf("a", "b")).toString()

        assertEquals(listOf("a", "b"), FlutterLegacyCodec.decodeStringList(raw))
    }

    @Test
    fun `不是 Flutter 編碼的字串回傳 null`() {
        assertNull(FlutterLegacyCodec.decodeStringList("[\"a\",\"b\"]"))
        assertNull(FlutterLegacyCodec.decodeStringList(null))
        assertNull(FlutterLegacyCodec.decodeStringList(""))
    }

    @Test
    fun `損毀的 Base64 不會拋出例外`() {
        assertNull(FlutterLegacyCodec.decodeStringList(FlutterLegacyCodec.LIST_PREFIX + "!!!not-base64!!!"))
    }

    // ========== Double ==========

    @Test
    fun `可以解碼 Flutter 的 Double`() {
        val raw = FlutterLegacyCodec.DOUBLE_PREFIX + "5000.0"

        assertEquals(5000.0, FlutterLegacyCodec.decodeDouble(raw)!!, 1e-9)
    }

    @Test
    fun `非 Double 前綴回傳 null`() {
        assertNull(FlutterLegacyCodec.decodeDouble("5000.0"))
        assertNull(FlutterLegacyCodec.decodeDouble(null))
    }

    // ========== 日期 ==========

    @Test
    fun `可以解析帶 Z 的 ISO 時間`() {
        val millis = FlutterLegacyCodec.parseLegacyDate("2026-09-10T03:45:39.000Z")

        assertNotNull(millis)
        val expected = java.time.Instant.parse("2026-09-10T03:45:39.000Z").toEpochMilli()
        assertEquals(expected, millis)
    }

    @Test
    fun `可以解析不帶 Z 的本地時間`() {
        val millis = FlutterLegacyCodec.parseLegacyDate("2026-03-05T00:00:00.000")

        assertNotNull(millis)
        val expected = LocalDateTime.parse("2026-03-05T00:00:00.000")
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, millis)
    }

    @Test
    fun `無法解析的日期回傳 null`() {
        assertNull(FlutterLegacyCodec.parseLegacyDate("不是日期"))
        assertNull(FlutterLegacyCodec.parseLegacyDate(null))
    }

    // ========== 記錄 ==========

    @Test
    fun `可以解析 Flutter 版的記錄 JSON`() {
        val json = """
            {"amount":-12.34,"category":"食物","note":"午餐",
             "date":"2026-03-05T00:00:00.000","createdAt":"2026-03-05T00:00:00.000","id":"legacy-1"}
        """.trimIndent()

        val record = FlutterLegacyCodec.parseLegacyRecord(json)

        assertNotNull(record)
        assertEquals(-12.34, record!!.amount, 1e-9)
        assertEquals("食物", record.category)
        assertEquals("午餐", record.note)
        assertEquals("legacy-1", record.id)
    }

    @Test
    fun `舊版的整數金額也能讀取`() {
        val json = """{"amount":-12,"category":"食物","note":"","date":"2026-03-05T00:00:00.000","id":"x"}"""

        val record = FlutterLegacyCodec.parseLegacyRecord(json)

        assertEquals(-12.0, record!!.amount, 1e-9)
    }

    @Test
    fun `缺少分類的記錄會被跳過`() {
        assertNull(FlutterLegacyCodec.parseLegacyRecord("""{"amount":-12}"""))
        assertNull(FlutterLegacyCodec.parseLegacyRecord("不是 JSON"))
        assertNull(FlutterLegacyCodec.parseLegacyRecord(null))
    }

    // ========== 記錄的新版 JSON ==========

    @Test
    fun `新版的 Record JSON 來回轉換保留小數`() {
        val record = Record.create(
            amount = -12.34,
            category = "食物",
            note = "午餐",
            dateMillis = 1_770_000_000_000,
            createdAtMillis = 1_770_000_000_000,
            id = "round-trip-1",
        )

        val restored = Record.fromJson(record.toJson())

        assertNotNull(restored)
        assertEquals(-12.34, restored!!.amount, 1e-9)
        assertEquals("round-trip-1", restored.id)
        assertEquals(1_770_000_000_000, restored.dateMillis)
    }

    @Test
    fun `新版 Record 遇到壞資料回傳 null`() {
        assertNull(Record.fromJson(org.json.JSONObject("""{"amount":-12}""")))
        assertNull(Record.fromJson(org.json.JSONObject("{}")))
    }
}
