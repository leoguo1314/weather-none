package com.skypulse.weather.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import java.util.concurrent.TimeUnit

internal fun resolveOpenAiChatEndpoint(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    val parsed = trimmed.toHttpUrlOrNull() ?: error("模型服务地址格式不正确")
    require(parsed.username.isBlank() && parsed.password.isBlank()) {
        "请把凭据填写在 API Key 字段，不要放在服务地址中"
    }
    require(parsed.isHttps || parsed.isPrivateHttpEndpoint()) {
        "请使用 HTTPS，或填写局域网/本机 HTTP 地址"
    }

    val path = parsed.encodedPath.trimEnd('/')
    require(!path.endsWith("/api/chat") && !path.endsWith("/api/generate")) {
        "Ollama 请使用 OpenAI 兼容地址：http://电脑IP:11434/v1"
    }
    require(!path.endsWith("/responses") && !path.endsWith("/messages")) {
        "当前支持 Chat Completions 协议，请填写该协议的 Base URL 或完整接口"
    }
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
    val octets = hostValue.split('.').map { it.toIntOrNull() ?: return false }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    return octets[0] == 10 ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 172 && octets[1] in 16..31)
}

internal fun modelEndpointDisplay(baseUrl: String): String {
    val endpoint = resolveOpenAiChatEndpoint(baseUrl).toHttpUrlOrNull()!!
    val builder = endpoint.newBuilder().query(null)
    endpoint.queryParameterNames.forEach { name ->
        endpoint.queryParameterValues(name).forEach { value ->
            builder.addQueryParameter(name, if (name == "api-version") value else "[hidden]")
        }
    }
    return builder.build().toString()
}

@Singleton
class OpenAiCompatibleClient @Inject constructor(
    client: OkHttpClient
) {
    // Model inference can take longer than a weather fetch. Keep credentials out of URL logs.
    private val modelClient = client.newBuilder()
        .apply { interceptors().removeAll { it is HttpLoggingInterceptor } }
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
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

    private suspend fun executeChat(config: AgentModelConfig, messages: JSONArray): String {
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
        val request = requestBuilder.build()
        return suspendCancellableCoroutine { continuation ->
            val call = modelClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use { readChatResponse(it, config) } }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
    }

    private fun readChatResponse(response: Response, config: AgentModelConfig): String {
        val endpoint = modelEndpointDisplay(config.baseUrl)
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val serverMessage = extractServerError(body).let {
                if (config.apiKey.isBlank()) it else it.replace(config.apiKey, "[hidden]")
            }
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
    private fun extractAssistantContent(body: String): String {
        val message = runCatching {
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
        }.getOrNull() ?: return ""
        val content = message.opt("content")
        return when (content) {
            is String -> content.trim()
            is JSONArray -> buildList {
                for (index in 0 until content.length()) {
                    val item = content.optJSONObject(index) ?: continue
                    item.optString("text").takeIf { it.isNotBlank() }?.let(::add)
                }
            }.joinToString("\n").trim()
            else -> ""
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
