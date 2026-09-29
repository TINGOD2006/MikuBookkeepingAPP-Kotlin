package com.example.countapp.data

import android.content.SharedPreferences

/**
 * 只存在記憶體的 [SharedPreferences] 實作，供 JVM 單元測試使用。
 *
 * `android.content.SharedPreferences` 在 JVM 測試裡只是 empty stub（呼叫就拋例外），
 * 但因為它本身是介面，測試可以自己實作——這樣就能在不引入 Robolectric 的前提下，
 * 真正測到儲存庫「讀檔 → 改資料 → 寫回」的行為。
 */
internal class FakeSharedPreferences : SharedPreferences {

    private val map = mutableMapOf<String, Any?>()

    /** 目前寫入的內容（測試斷言用）。 */
    val stored: Map<String, Any?> get() = map.toMap()

    override fun getAll(): MutableMap<String, Any?> = map.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        map[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (map[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (map[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            if (key != null) pending[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            flush()
            return true
        }

        override fun apply() {
            flush()
        }

        private fun flush() {
            if (clearRequested) map.clear()
            map.putAll(pending)
        }
    }
}
