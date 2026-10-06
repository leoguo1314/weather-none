package com.skypulse.weather.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

internal fun resolveOpenAiChatEndpoint(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    val parsed = trimmed.toHttpUrlOrNull() ?: error("模型服务地址格式不正确")
    require(parsed.isHttps || parsed.isPrivateHttpEndpoint()) {
        "请使用 HTTPS，或填写局域网/本机 HTTP 地址"
    }

    val path = parsed.encodedPath.trimEnd('/')
    val endpointPath = when {
        path.endsWith("/chat/completions", ignoreCase = true) ->
            path.dropLast("/chat/completions".length) + "/chat/completions"
        path.endsWith("/models", ignoreCase = true) ->
            path.dropLast("/models".length) + "/chat/completions"
        path.isBlank() -> "/v1/chat/completions"
        else -> "$path/chat/completions"
    }
    return parsed.newBuilder()
        .encodedPath(endpointPath)
        .fragment(null)
        .build()
        .toString()
}

private fun HttpUrl.isPrivateHttpEndpoint(): Boolean {
    if (isHttps) return true
    if (scheme != "http") return false
    val hostValue = host.lowercase()
    if (hostValue == "localhost" || hostValue == "127.0.0.1" || hostValue == "::1") return true
    if (hostValue.startsWith("10.") || hostValue.startsWith("192.168.")) return true
    if (hostValue.startsWith("172.")) {
        val second = hostValue.substringAfter("172.").substringBefore('.').toIntOrNull()
        if (second in 16..31) return true
    }
    return false
}

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
        executeChat(
            config = config,
            messages = JSONArray()
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
    }

    suspend fun testConnection(config: AgentModelConfig): String = withContext(Dispatchers.IO) {
        require(config.baseUrl.isNotBlank() && config.model.isNotBlank()) { "模型配置不完整" }
        executeChat(
            config = config.copy(enabled = true),
            messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", "你是连接测试助手。"))
                .put(JSONObject().put("role", "user").put("content", "只回复：连接成功"))
        )
    }

    private fun executeChat(config: AgentModelConfig, messages: JSONArray): String {
        val endpoint = resolveOpenAiChatEndpoint(config.baseUrl)
        val payload = JSONObject()
            .put("model", config.model)
            .put("stream", false)
            .put("messages", messages)
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        if (config.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val serverMessage = extractServerError(body)
                val guidance = if (response.code == 405) {
                    "请求地址不是可 POST 的 Chat Completions 接口，请检查 Base URL；实际请求：$endpoint"
                } else {
                    "实际请求：$endpoint"
                }
                error(
                    buildString {
                        append("模型服务返回 HTTP ${response.code}")
                        if (serverMessage.isNotBlank()) append("：$serverMessage")
                        append("。$guidance")
                    }
                )
            }
            val content = extractAssistantContent(body)
            check(content.isNotBlank()) { "模型服务未返回有效内容；实际请求：$endpoint" }
            return content
        }
    }

    private fun extractAssistantContent(body: String): String {
        val message = runCatching {
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
        }.getOrNull() ?: return ""
        val content = message.opt("content")
        return when (content) {
            is String -> content.trim().ifBlank { message.optString("reasoning_content").trim() }
            is JSONArray -> buildList {
                for (index in 0 until content.length()) {
                    val item = content.optJSONObject(index) ?: continue
                    item.optString("text").takeIf { it.isNotBlank() }?.let(::add)
                }
            }.joinToString("\n").trim()
            else -> message.optString("reasoning_content").trim()
        }
    }

    private fun extractServerError(body: String): String {
        val parsed = runCatching { JSONObject(body) }.getOrNull()
        val message = parsed?.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: parsed?.optString("message")?.takeIf { it.isNotBlank() }
            ?: body.takeIf { it.isNotBlank() && !it.trimStart().startsWith("<") }
        return message.orEmpty().replace(Regex("\\s+"), " ").take(240)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
