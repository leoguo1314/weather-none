package com.skypulse.weather.agent

import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgentConfigStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        EncryptedSharedPreferences.create(
            context, "ai_agent_config",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Before fun clear() { preferences.edit().clear().commit() }
    @After fun cleanUp() { preferences.edit().clear().commit() }

    @Test
    fun legacyConfigurationMigratesWithoutLosingKeyAndSelection() {
        preferences.edit().putString("base_url", "https://api.deepseek.com/v1")
            .putString("api_key", "fixture-secret").putString("model", "custom-existing-model")
            .putBoolean("enabled", true).commit()
        val store = AgentConfigStore(context)
        assertTrue(store.storesApiKeySecurely)
        val settings = store.loadSettings()
        assertEquals(1, settings.providers.size)
        assertEquals("fixture-secret", settings.activeConfig.apiKey)
        assertEquals("custom-existing-model", settings.activeModel)
        assertTrue(settings.activeConfig.isUsable)
        store.saveSettings(settings)
        assertFalse(preferences.contains("api_key"))
        assertEquals(settings, AgentConfigStore(context).loadSettings())
    }

    @Test
    fun multipleProvidersAndTheirKeysPersistAcrossStoreInstances() {
        val store = AgentConfigStore(context)
        assertTrue(store.storesApiKeySecurely)
        val settings = AgentModelSettings(
            providers = listOf(
                AgentProviderConfig("one", name = "厂商一", baseUrl = "https://one.example.com/v1", apiKey = "key-one", models = listOf("a", "b")),
                AgentProviderConfig("two", name = "厂商二", baseUrl = "https://two.example.com/v1", apiKey = "key-two", models = listOf("c"))
            ), activeProviderId = "two", activeModel = "c", externalModelEnabled = true
        )
        store.saveSettings(settings)
        assertEquals(settings, AgentConfigStore(context).loadSettings())
    }
}
