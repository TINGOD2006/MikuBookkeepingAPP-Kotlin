package com.example.countapp.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 垃圾桶（軟刪除）與編輯功能的單元測試。
 *
 * 這裡用純記憶體的 [FakeSharedPreferences] 取代 Android 的 SharedPreferences，
 * 因此不需要 Robolectric 也能測到儲存庫真正「讀檔 → 改資料 → 寫回」的行為，
 * 特別是 30 天保留期的邊界。
 */
class RecordTrashTest {

    // ========== 軟刪除 / 垃圾桶 ==========

    @Test
    fun `刪除後記錄離開明細並進入垃圾桶`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val record = sample(amount = -120.0, category = "食物", date = DAY_0)
        repo.add(record)

        assertTrue(repo.moveToTrash(record.id, now = DAY_30))

        assertEquals(0, repo.records.value.size)
        assertEquals(1, repo.trashedRecords.value.size)
        assertEquals(record.id, repo.trashedRecords.value.first().id)
        assertEquals(DAY_30, repo.trashedRecords.value.first().deletedAtMillis)
    }

    @Test
    fun `垃圾桶內的記錄不列入統計`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val food = sample(amount = -100.0, category = "食物", date = DAY_0)
        val salary = sample(amount = 500.0, category = "薪水", date = DAY_0)
        repo.add(food)
        repo.add(salary)

        repo.moveToTrash(food.id, now = DAY_0)

        assertEquals(0.0, repo.totalExpense(), 0.001)
        assertEquals(500.0, repo.totalIncome(), 0.001)
    }

    @Test
    fun `刪除的記錄可以還原`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val record = sample(amount = -50.0, category = "交通", date = DAY_0)
        repo.add(record)
        repo.moveToTrash(record.id, now = DAY_0)

        assertTrue(repo.restore(record.id))

        assertEquals(1, repo.records.value.size)
        assertEquals(0, repo.trashedRecords.value.size)
        assertNull(repo.records.value.first().deletedAtMillis)
    }

    @Test
    fun `重複刪除或還原不存在的記錄回傳 false`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val record = sample(amount = -50.0, category = "交通", date = DAY_0)
        repo.add(record)

        assertFalse(repo.moveToTrash("不存在", now = DAY_0))
        assertTrue(repo.moveToTrash(record.id, now = DAY_0))
        assertFalse(repo.moveToTrash(record.id, now = DAY_0))
        assertFalse(repo.restore("不存在"))
    }

    @Test
    fun `還原後的記錄不再需要第二次還原`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val record = sample(amount = -50.0, category = "交通", date = DAY_0)
        repo.add(record)
        repo.moveToTrash(record.id, now = DAY_0)

        assertTrue(repo.restore(record.id))
        assertFalse(repo.restore(record.id))
    }

    // ========== 30 天保留期 ==========

    @Test
    fun `垃圾桶保留 30 天`() {
        assertEquals(30, RecordRepository.TRASH_RETENTION_DAYS)
    }

    @Test
    fun `第 29 天還在垃圾桶`() {
        val prefs = FakeSharedPreferences()
        val repo = RecordRepository(prefs)
        val record = sample(amount = -50.0, category = "交通", date = DAY_0)
        repo.add(record)
        repo.moveToTrash(record.id, now = DAY_0)

        val removed = repo.purgeExpiredTrash(now = DAY_0 + DAY * 29)

        assertEquals(0, removed)
        assertEquals(1, repo.trashedRecords.value.size)
        // 注意：要拿垃圾桶內的複本，原物件不會被就地修改
        assertEquals(1, repo.trashedRecords.value.single().trashRemainingDays(DAY_0 + DAY * 29))
    }

    @Test
    fun `滿 30 天自動清除`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val record = sample(amount = -50.0, category = "交通", date = DAY_0)
        repo.add(record)
        repo.moveToTrash(record.id, now = DAY_0)

        val trashed = repo.trashedRecords.value.single()
        assertEquals(30, trashed.trashRemainingDays(DAY_0))
        assertFalse(trashed.isTrashExpired(DAY_0 + DAY * 29))

        val removed = repo.purgeExpiredTrash(now = DAY_0 + DAY * 30)

        assertEquals(1, removed)
        assertEquals(0, repo.trashedRecords.value.size)
        assertEquals(0, repo.records.value.size)
        assertTrue(trashed.isTrashExpired(DAY_0 + DAY * 30))
        assertEquals(0, trashed.trashRemainingDays(DAY_0 + DAY * 30))
    }

    @Test
    fun `重新載入時會清掉逾期記錄`() {
        val prefs = FakeSharedPreferences()
        // 用真實時間當基準：儲存庫初始化時會用系統時間判斷逾期
        val now = System.currentTimeMillis()

        val first = RecordRepository(prefs)
        val stale = sample(amount = -10.0, category = "零食", date = now)
        first.add(stale)
        first.moveToTrash(stale.id, now = now)

        // 換一個儲存庫實例重新讀檔（模擬 App 重啟），此時還沒逾期
        val second = RecordRepository(prefs)
        assertEquals(1, second.trashedRecords.value.size)

        // 31 天後再開一次：應該被自動清掉
        val removed = second.purgeExpiredTrash(now = now + DAY * 31)
        assertEquals(1, removed)

        val third = RecordRepository(prefs)
        assertEquals(0, third.trashedRecords.value.size)
        assertEquals(0, third.records.value.size)
    }

    @Test
    fun `垃圾桶依刪除時間新到舊排序`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val old = sample(amount = -10.0, category = "零食", date = DAY_0)
        val new = sample(amount = -20.0, category = "酒", date = DAY_0)
        repo.add(old)
        repo.add(new)

        repo.moveToTrash(old.id, now = DAY_0)
        repo.moveToTrash(new.id, now = DAY_0 + DAY)

        assertEquals(listOf(new.id, old.id), repo.trashedRecords.value.map { it.id })
    }

    @Test
    fun `清空垃圾桶會永久刪除`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val a = sample(amount = -10.0, category = "零食", date = DAY_0)
        val b = sample(amount = -20.0, category = "酒", date = DAY_0)
        repo.add(a)
        repo.add(b)
        repo.moveToTrash(a.id, now = DAY_0)
        repo.moveToTrash(b.id, now = DAY_0)

        assertEquals(2, repo.emptyTrash())
        assertEquals(0, repo.trashedRecords.value.size)
    }

    // ========== 編輯 ==========

    @Test
    fun `編輯會更新分類與資料但保留 id`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val record = sample(amount = -100.0, category = "食物", date = DAY_0)
        repo.add(record)

        val edited = record.edited(
            amount = -250.5,
            category = "購物",
            note = "換季衣服",
            dateMillis = DAY_0 + DAY * 2,
        )
        assertTrue(repo.update(edited))

        val stored = repo.records.value.single()
        assertEquals(record.id, stored.id)
        assertEquals(record.createdAtMillis, stored.createdAtMillis)
        assertEquals(-250.5, stored.amount, 0.001)
        assertEquals("購物", stored.category)
        assertEquals("換季衣服", stored.note)
        assertEquals(DAY_0 + DAY * 2, stored.dateMillis)
    }

    @Test
    fun `收支互換也可以編輯`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val expense = sample(amount = -100.0, category = "食物", date = DAY_0)
        repo.add(expense)

        repo.update(expense.edited(amount = 100.0, category = "獎金", note = "", dateMillis = DAY_0))

        assertEquals(100.0, repo.totalIncome(), 0.001)
        assertEquals(0.0, repo.totalExpense(), 0.001)
    }

    @Test
    fun `編輯結果會寫進儲存區`() {
        val prefs = FakeSharedPreferences()
        val repo = RecordRepository(prefs)
        val record = sample(amount = -100.0, category = "食物", date = DAY_0)
        repo.add(record)
        repo.update(record.edited(-9.0, "娛樂", "電影", DAY_0))

        val reopened = RecordRepository(prefs).records.value.single()
        assertEquals("娛樂", reopened.category)
        assertEquals(-9.0, reopened.amount, 0.001)
        assertEquals("電影", reopened.note)
    }

    @Test
    fun `編輯不存在的記錄回傳 false`() {
        val repo = RecordRepository(FakeSharedPreferences())
        assertFalse(repo.update(sample(amount = -1.0, category = "食物", date = DAY_0)))
    }

    @Test
    fun `編輯垃圾桶內的記錄不會讓它跑回明細`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val record = sample(amount = -100.0, category = "食物", date = DAY_0)
        repo.add(record)
        repo.moveToTrash(record.id, now = DAY_0)

        val trashed = repo.trashedRecords.value.single()
        repo.update(trashed.edited(-80.0, "娛樂", "演唱會", DAY_0))

        assertEquals(0, repo.records.value.size)
        assertEquals(1, repo.trashedRecords.value.size)
        assertEquals(-80.0, repo.trashedRecords.value.single().amount, 0.001)
    }

    @Test
    fun `拿過期複本編輯不會讓垃圾桶記錄復活`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val record = sample(amount = -100.0, category = "食物", date = DAY_0)
        repo.add(record)
        repo.moveToTrash(record.id, now = DAY_0)

        // 模擬編輯畫面拿著「刪除前」的複本（deletedAt = null）去儲存
        val stale = record.copy(amount = -77.0, note = "改過的內容")
        assertTrue(repo.update(stale))

        assertEquals(0, repo.records.value.size)
        assertEquals(1, repo.trashedRecords.value.size)
        assertEquals(-77.0, repo.trashedRecords.value.single().amount, 0.001)
        assertTrue(repo.trashedRecords.value.single().isTrashed)
    }

    @Test
    fun `永久刪除單筆記錄`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val record = sample(amount = -100.0, category = "食物", date = DAY_0)
        repo.add(record)
        repo.moveToTrash(record.id, now = DAY_0)

        assertTrue(repo.deleteById(record.id))
        assertEquals(0, repo.trashedRecords.value.size)
        assertEquals(0, repo.records.value.size)
        assertFalse(repo.deleteById(record.id))
    }

    // ========== JSON 相容性 ==========

    @Test
    fun `沒有 deletedAt 的舊資料視為未刪除`() {
        val json = JSONObject()
            .put("amount", -30.0)
            .put("category", "食物")
            .put("note", "午餐")
            .put("date", DAY_0)
            .put("createdAt", DAY_0)
            .put("id", "legacy-1")

        val record = Record.fromJson(json)

        assertNotNull(record)
        assertNull(record!!.deletedAtMillis)
        assertFalse(record.isTrashed)
    }

    @Test
    fun `垃圾桶記錄的 JSON 可來回轉換`() {
        val record = sample(amount = -30.0, category = "食物", date = DAY_0)
            .copy(deletedAtMillis = DAY_0 + 1234L)

        val restored = Record.fromJson(record.toJson())

        assertEquals(record, restored)
    }

    @Test
    fun `未刪除的記錄不會多寫 deletedAt 欄位`() {
        val json = sample(amount = -30.0, category = "食物", date = DAY_0).toJson()

        assertFalse(json.has("deletedAt"))
    }

    // ========== 輔助 ==========

    private fun sample(amount: Double, category: String, date: Long): Record = Record.create(
        amount = amount,
        category = category,
        note = "",
        dateMillis = date,
        createdAtMillis = date,
    )

    private companion object {
        const val DAY: Long = 24L * 60L * 60L * 1000L
        const val DAY_0: Long = 1_700_000_000_000L
        const val DAY_30: Long = DAY_0 + DAY * 10
    }
}
