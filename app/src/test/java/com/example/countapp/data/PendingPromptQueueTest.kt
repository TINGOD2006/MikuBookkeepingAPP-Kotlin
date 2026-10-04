package com.example.countapp.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingPromptQueueTest {
    @Test fun `刪除中的提示已先移除時關閉編輯器不能吞掉下一筆`() {
        val queue = listOf(Record(-1.0, "其他", "test", 0, 0, "b"))
        assertEquals(queue, PendingPromptQueue.dismiss(queue, "a"))
        assertEquals(emptyList<Record>(), PendingPromptQueue.dismiss(queue, "b"))
    }
}
