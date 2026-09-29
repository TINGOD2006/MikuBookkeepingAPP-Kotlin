package com.example.countapp.data

import org.junit.Assert.*
import org.junit.Test

class AutoCategoryUpdateTest {
    private fun record() = Record.create(amount = -50.0, category = "其他", note = "商店")

    @Test fun `背景分類能更新未修改的記錄`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val original = record()
        repo.add(original)
        assertTrue(repo.updateIfUnchanged(original, original.copy(category = "食物")))
        assertEquals("食物", repo.snapshot().single().category)
    }

    @Test fun `背景分類不能覆蓋使用者編輯`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val original = record()
        repo.add(original)
        repo.update(original.copy(note = "自訂備註", category = "交通"))
        assertFalse(repo.updateIfUnchanged(original, original.copy(category = "食物")))
        assertEquals("交通", repo.snapshot().single().category)
        assertEquals("自訂備註", repo.snapshot().single().note)
    }

    @Test fun `背景分類不能修改垃圾桶或不存在的記錄`() {
        val repo = RecordRepository(FakeSharedPreferences())
        val original = record()
        assertFalse(repo.updateIfUnchanged(original, original.copy(category = "食物")))
        repo.add(original)
        repo.moveToTrash(original.id)
        assertFalse(repo.updateIfUnchanged(original, original.copy(category = "食物")))
        assertTrue(repo.snapshot().isEmpty())
        assertEquals("其他", repo.trashSnapshot().single().category)
    }
}
