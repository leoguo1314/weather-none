package com.skypulse.weather.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiCompatibleClient @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun complete(
        config: AgentModelConfig,
        userInput: String,
        snapshot: AgentSnapshot,
        toolContext: String
    ): String = withContext(Dispatchers.IO) {
        require(config.isUsable) { "模型配置不完整" }
        val endpoint = normalizeEndpoint(config.baseUrl)
        val payload = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.2)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "你是 SkyPulse AI 天气助手。只能依据工具返回的实时天气数据回答，" +
                                    "不得编造数值。用简洁中文给出结论、风险和可执行建议。"
                            )
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put(
                                "content",
                                "城市：${snapshot.city}\n用户问题：$userInput\n工具结果：\n$toolContext"
                            )
                    )
            )
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "模型服务返回 HTTP ${response.code}" }
            val content = JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            check(content.isNotBlank()) { "模型服务未返回有效内容" }
            content
        }
    }

    private fun normalizeEndpoint(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.startsWith("https://") || isPrivateHttpEndpoint(trimmed)) {
            "请使用 HTTPS，或填写局域网/本机 HTTP 地址"
        }
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    private fun isPrivateHttpEndpoint(url: String): Boolean =
        url.startsWith("http://127.0.0.1") ||
            url.startsWith("http://localhost") ||
            url.startsWith("http://10.") ||
            url.startsWith("http://192.168.") ||
            (16..31).any { url.startsWith("http://172.$it.") }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
