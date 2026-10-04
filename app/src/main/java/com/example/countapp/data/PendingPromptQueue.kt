package com.example.countapp.data

/** 按編輯器原本開啟的 id 移除；刪除帳目已使佇列變動時不會誤移除下一筆。 */
internal object PendingPromptQueue {
    fun dismiss(pending: List<Record>, recordId: String): List<Record> = pending.filterNot { it.id == recordId }
}
