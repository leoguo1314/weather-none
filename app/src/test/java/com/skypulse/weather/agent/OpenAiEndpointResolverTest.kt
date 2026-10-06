package com.skypulse.weather.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiEndpointResolverTest {

    @Test
    fun `bare host receives v1 chat completions path`() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            resolveOpenAiChatEndpoint("https://example.com")
        )
    }

    @Test
    fun `versioned base url receives chat completions path`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            resolveOpenAiChatEndpoint("https://api.openai.com/v1/")
        )
    }

    @Test
    fun `models endpoint is converted to chat completions`() {
        assertEquals(
            "https://gateway.example.com/v1/chat/completions",
            resolveOpenAiChatEndpoint("https://gateway.example.com/v1/models")
        )
    }

    @Test
    fun `complete endpoint and query are preserved`() {
        assertEquals(
            "https://gateway.example.com/api/v3/chat/completions?api-version=2026-01-01",
            resolveOpenAiChatEndpoint(
                "https://gateway.example.com/api/v3/chat/completions?api-version=2026-01-01"
            )
        )
    }

    @Test
    fun `private network http endpoint is supported`() {
        assertEquals(
            "http://192.168.1.8:11434/v1/chat/completions",
            resolveOpenAiChatEndpoint("http://192.168.1.8:11434/v1")
        )
    }

    @Test
    fun `public http endpoint is rejected`() {
        val result = runCatching { resolveOpenAiChatEndpoint("http://example.com/v1") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("HTTPS"))
    }

    @Test
    fun `domain that looks like private IP is rejected`() {
        assertTrue(runCatching { resolveOpenAiChatEndpoint("http://10.example.com/v1") }.isFailure)
        assertTrue(runCatching { resolveOpenAiChatEndpoint("http://192.168.example.com/v1") }.isFailure)
    }

    @Test
    fun `native Ollama route receives compatible protocol guidance`() {
        val failure = runCatching { resolveOpenAiChatEndpoint("http://192.168.1.8:11434/api/chat") }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("11434/v1"))
    }

    @Test
    fun `URL credentials must be moved to key field`() {
        assertTrue(runCatching { resolveOpenAiChatEndpoint("https://user:secret@example.com/v1") }.isFailure)
    }

    @Test
    fun `display hides query credentials while retaining API version`() {
        val display = modelEndpointDisplay("https://example.com/v1?token=secret&api-version=2026-01-01")
        assertFalse(display.contains("secret"))
        assertTrue(display.contains("api-version=2026-01-01"))
    }
}
