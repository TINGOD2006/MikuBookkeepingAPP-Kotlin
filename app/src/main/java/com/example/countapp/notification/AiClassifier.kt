package com.example.countapp.notification

import android.util.Log
import com.example.countapp.data.SettingsStore
import com.example.countapp.domain.ClassificationRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通知文字的分類器。
 *
 * 對應 Flutter 版的 `AIService`：
 *   - 關閉 AI 分類（或未設定 API）→ 使用內建／自訂規則表
 *   - 開啟且已設定 → 呼叫使用者自行填寫的 OpenAI 相容端點
 *
 * ⚠️ 任何遠端失敗（逾時、HTTP 錯誤、回傳格式不符）都會自動退回規則表，
 *    分類結果絕不會因為網路問題而讓記帳失敗。
 */
class AiClassifier {

    /**
     * 分類文字。
     *
     * ⚠️ 這會做網路 I/O，**必須在背景執行緒呼叫**。
     */
    fun classify(text: String, settings: SettingsStore): String {
        val rules = settings.effectiveRules()
        val fallback = ClassificationRules.classifyWith(text, rules)

        if (!settings.useAiClassification) return fallback
        if (!settings.isAiConfigured) return fallback

        return try {
            classifyRemote(text, settings) ?: fallback
        } catch (e: Exception) {
            Log.w(TAG, "AI 分類失敗，改用規則表: ${e.message}")
            fallback
        }
    }

    private fun classifyRemote(text: String, settings: SettingsStore): String? {
        val categories = ClassificationRules.defaultRules.keys.joinToString("、")

        val body = JSONObject().apply {
            put("model", settings.aiModel)
            put("temperature", 0)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put(
                                "content",
                                "你是記帳分類助手。請只從以下分類中挑選最合適的一個，" +
                                    "並只回覆分類名稱本身，不要任何其他文字：$categories",
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", text)
                        }
                    )
                },
            )
        }

        val connection = (URL(settings.aiApiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${settings.aiApiKey}")
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "AI 端點回應 HTTP $code")
                return null
            }

            val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            return parseCategory(response, settings)
        } finally {
            connection.disconnect()
        }
    }

    /** 從 OpenAI 相容回應中取出分類名稱。 */
    private fun parseCategory(response: String, settings: SettingsStore): String? {
        val content = JSONObject(response)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()
            ?: return null

        if (content.isEmpty()) return null

        // 只接受「已知分類」或使用者自訂的分類，避免模型亂回一個新分類
        val known = settings.effectiveRules().keys
        return known.firstOrNull { content == it || content.contains(it) }
    }

    companion object {
        private const val TAG = "AiClassifier"

        /** 連線與讀取逾時。AI 分類只是加值功能，逾時就退回規則表。 */
        private const val TIMEOUT_MS = 8_000
    }
}
