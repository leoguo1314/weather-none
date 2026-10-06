package com.skypulse.weather.agent

import org.junit.Assert.assertEquals
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
}
