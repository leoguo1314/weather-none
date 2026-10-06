package com.skypulse.weather.agent

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class OpenAiCompatibleClientTest {
    private val config = AgentModelConfig(
        providerName = "测试厂商",
        baseUrl = "https://gateway.example.com/v1/chat/completions",
        apiKey = "fixture-secret",
        model = "test-model",
        enabled = true
    )

    private fun client(
        body: String,
        code: Int = 200,
        request: AtomicReference<Request> = AtomicReference()
    ) = OpenAiCompatibleClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            request.set(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fixture")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
    )

    @Test
    fun `connection test posts selected model exactly once to full endpoint`() = runTest {
        val request = AtomicReference<Request>()
        val answer = client("""{"choices":[{"message":{"content":"连接成功"}}]}""", request = request)
            .testConnection(config)

        assertEquals("连接成功", answer)
        assertEquals("POST", request.get().method)
        assertEquals("/v1/chat/completions", request.get().url.encodedPath)
        assertEquals("Bearer fixture-secret", request.get().header("Authorization"))
        val payload = JSONObject(Buffer().also { request.get().body!!.writeTo(it) }.readUtf8())
        assertEquals("test-model", payload.getString("model"))
        assertFalse(payload.getBoolean("stream"))
        assertEquals(2, payload.getJSONArray("messages").length())
        assertFalse(payload.has("temperature"))
    }

    @Test
    fun `models address becomes POST chat endpoint and query is preserved`() = runTest {
        val request = AtomicReference<Request>()
        client("""{"choices":[{"message":{"content":"ok"}}]}""", request = request)
            .testConnection(config.copy(baseUrl = "https://gateway.example.com/api/v3/models?api-version=2026-01-01", apiKey = ""))
        assertEquals("/api/v3/chat/completions", request.get().url.encodedPath)
        assertEquals("2026-01-01", request.get().url.queryParameter("api-version"))
        assertNull(request.get().header("Authorization"))
    }

    @Test
    fun `405 gives actionable endpoint guidance and hides credentials`() = runTest {
        val failure = runCatching {
            client("""{"error":{"message":"Method not allowed fixture-secret"}}""", code = 405)
                .testConnection(config.copy(baseUrl = "https://gateway.example.com/v1?token=fixture-secret"))
        }.exceptionOrNull()!!
        assertTrue(failure.message!!.contains("HTTP 405"))
        assertTrue(failure.message!!.contains("可 POST"))
        assertTrue(failure.message!!.contains("/v1/chat/completions"))
        assertFalse(failure.message!!.contains("fixture-secret"))
    }

    @Test
    fun `structured content arrays are joined into an answer`() = runTest {
        assertEquals("晴天\n适合出行", client("""{"choices":[{"message":{"content":[{"type":"text","text":"晴天"},{"type":"text","text":"适合出行"}]}}]}""").testConnection(config))
    }

    @Test
    fun `reasoning without final answer is not treated as connection success`() = runTest {
        val failure = runCatching {
            client("""{"choices":[{"message":{"content":null,"reasoning_content":"thinking"}}]}""")
                .testConnection(config)
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("未返回有效内容"))
    }
}
