package com.skypulse.weather.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModelSettingsTest {

    private val openAi = AgentProviderConfig(
        id = "openai",
        preset = ModelProviderPreset.OPENAI,
        name = "OpenAI 主账号",
        baseUrl = "https://api.openai.com/v1",
        apiKey = "secret",
        models = listOf("gpt-4.1-mini", "gpt-4.1")
    )

    private val deepSeek = AgentProviderConfig(
        id = "deepseek",
        preset = ModelProviderPreset.DEEPSEEK,
        models = listOf("deepseek-chat")
    )

    @Test
    fun `active provider and model create usable runtime config`() {
        val settings = AgentModelSettings(
            providers = listOf(openAi, deepSeek),
            activeProviderId = "deepseek",
            activeModel = "deepseek-chat",
            externalModelEnabled = true
        ).normalized()

        assertTrue(settings.activeConfig.isUsable)
        assertEquals("DeepSeek", settings.activeConfig.providerName)
        assertEquals("deepseek-chat", settings.activeConfig.model)
    }

    @Test
    fun `missing selection falls back to first provider and model`() {
        val settings = AgentModelSettings(
            providers = listOf(openAi, deepSeek),
            activeProviderId = "missing",
            activeModel = "missing",
            externalModelEnabled = true
        ).normalized()

        assertEquals("openai", settings.activeProviderId)
        assertEquals("gpt-4.1-mini", settings.activeModel)
    }

    @Test
    fun `provider normalization trims and deduplicates models`() {
        val normalized = openAi.copy(
            models = listOf(" gpt-4.1-mini ", "gpt-4.1-mini", "", "gpt-4.1")
        ).normalized()

        assertEquals(listOf("gpt-4.1-mini", "gpt-4.1"), normalized.models)
    }

    @Test
    fun `external model is disabled when provider has no models`() {
        val settings = AgentModelSettings(
            providers = listOf(openAi.copy(models = emptyList())),
            activeProviderId = "openai",
            externalModelEnabled = true
        ).normalized()

        assertFalse(settings.externalModelEnabled)
        assertFalse(settings.activeConfig.isUsable)
    }

    @Test
    fun `legacy endpoint infers known provider`() {
        assertEquals(
            ModelProviderPreset.OLLAMA,
            ModelProviderPreset.infer("http://192.168.1.8:11434/v1")
        )
        assertEquals(
            ModelProviderPreset.LM_STUDIO,
            ModelProviderPreset.infer("http://10.0.0.8:1234/v1")
        )
    }
}
