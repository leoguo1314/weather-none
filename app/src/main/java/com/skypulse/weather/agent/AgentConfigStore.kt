package com.skypulse.weather.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

enum class ModelProviderPreset(
    val displayName: String,
    val defaultBaseUrl: String,
    val suggestedModels: List<String>
) {
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        suggestedModels = listOf("gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini")
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        suggestedModels = listOf("deepseek-chat", "deepseek-reasoner")
    ),
    SILICON_FLOW(
        displayName = "硅基流动",
        defaultBaseUrl = "https://api.siliconflow.cn/v1",
        suggestedModels = listOf("deepseek-ai/DeepSeek-V3.1", "Qwen/Qwen3-8B")
    ),
    DASHSCOPE(
        displayName = "阿里云百炼",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        suggestedModels = listOf("qwen-plus", "qwen-max", "qwen-turbo")
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        suggestedModels = listOf("openai/gpt-4.1-mini", "google/gemini-2.5-flash")
    ),
    OLLAMA(
        displayName = "Ollama",
        defaultBaseUrl = "http://127.0.0.1:11434/v1",
        suggestedModels = listOf("qwen3:8b", "llama3.2", "deepseek-r1:8b")
    ),
    LM_STUDIO(
        displayName = "LM Studio",
        defaultBaseUrl = "http://127.0.0.1:1234/v1",
        suggestedModels = emptyList()
    ),
    ONE_API(
        displayName = "OneAPI / New API",
        defaultBaseUrl = "https://your-oneapi.example.com/v1",
        suggestedModels = emptyList()
    ),
    CUSTOM(
        displayName = "自定义兼容厂商",
        defaultBaseUrl = "",
        suggestedModels = emptyList()
    );

    companion object {
        fun infer(baseUrl: String): ModelProviderPreset {
            val value = baseUrl.lowercase()
            return when {
                "api.openai.com" in value -> OPENAI
                "api.deepseek.com" in value -> DEEPSEEK
                "siliconflow" in value -> SILICON_FLOW
                "dashscope" in value -> DASHSCOPE
                "openrouter" in value -> OPENROUTER
                ":11434" in value -> OLLAMA
                ":1234" in value -> LM_STUDIO
                else -> CUSTOM
            }
        }
    }
}

data class AgentProviderConfig(
    val id: String,
    val preset: ModelProviderPreset = ModelProviderPreset.CUSTOM,
    val name: String = preset.displayName,
    val baseUrl: String = preset.defaultBaseUrl,
    val apiKey: String = "",
    val models: List<String> = preset.suggestedModels,
    val enabled: Boolean = true
) {
    fun normalized(): AgentProviderConfig = copy(
        name = name.trim().ifBlank { preset.displayName },
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        models = models.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    )

    fun modelConfig(model: String, enabled: Boolean = true): AgentModelConfig = AgentModelConfig(
        providerId = id,
        providerName = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        enabled = enabled && this.enabled
    )
}

data class AgentModelSettings(
    val providers: List<AgentProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
    val activeModel: String? = null,
    val externalModelEnabled: Boolean = false
) {
    val activeProvider: AgentProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId }

    val activeConfig: AgentModelConfig
        get() {
            val provider = activeProvider
            val selectedModel = activeModel?.takeIf { it in provider?.models.orEmpty() }.orEmpty()
            return if (provider == null) {
                AgentModelConfig()
            } else {
                provider.modelConfig(selectedModel, enabled = externalModelEnabled)
            }
        }

    fun normalized(): AgentModelSettings {
        val normalizedProviders = providers.map(AgentProviderConfig::normalized).distinctBy { it.id }
        val provider = normalizedProviders.firstOrNull { it.id == activeProviderId }
            ?: normalizedProviders.firstOrNull()
        val model = activeModel?.takeIf { it in provider?.models.orEmpty() }
            ?: provider?.models?.firstOrNull()
        return copy(
            providers = normalizedProviders,
            activeProviderId = provider?.id,
            activeModel = model,
            externalModelEnabled = externalModelEnabled && provider != null && model != null
        )
    }
}

data class AgentModelConfig(
    val providerId: String = "",
    val providerName: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val enabled: Boolean = false
) {
    val isUsable: Boolean
        get() = enabled && baseUrl.isNotBlank() && model.isNotBlank()
}

@Singleton
class AgentConfigStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private data class PreferencesHolder(
        val preferences: SharedPreferences,
        val encrypted: Boolean
    )

    private val holder: PreferencesHolder = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        PreferencesHolder(
            preferences = EncryptedSharedPreferences.create(
                context,
                "ai_agent_config",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ),
            encrypted = true
        )
    }.getOrElse {
        PreferencesHolder(
            preferences = context.getSharedPreferences("ai_agent_config_fallback", Context.MODE_PRIVATE),
            encrypted = false
        )
    }
    private val preferences: SharedPreferences = holder.preferences
    private var sessionSettings: AgentModelSettings? = null

    val storesApiKeySecurely: Boolean
        get() = holder.encrypted

    init {
        if (!holder.encrypted) {
            preferences.edit().remove(KEY_API_KEY).apply()
        }
    }

    fun loadSettings(): AgentModelSettings {
        sessionSettings?.let { return it }
        val stored = preferences.getString(KEY_SETTINGS_V2, null)
            ?.takeIf { it.isNotBlank() }
            ?.let(::decodeSettings)
        val settings = (stored ?: loadLegacySettings()).normalized()
        sessionSettings = settings
        return settings
    }

    fun load(): AgentModelConfig = loadSettings().activeConfig

    fun saveSettings(settings: AgentModelSettings) {
        val normalized = settings.normalized()
        sessionSettings = normalized
        preferences.edit()
            .putString(KEY_SETTINGS_V2, encodeSettings(normalized, includeSecrets = holder.encrypted))
            .remove(KEY_BASE_URL)
            .remove(KEY_API_KEY)
            .remove(KEY_MODEL)
            .remove(KEY_ENABLED)
            .apply()
    }

    /** Backward-compatible single-provider save for callers outside the settings screen. */
    fun save(config: AgentModelConfig) {
        val preset = ModelProviderPreset.infer(config.baseUrl)
        val provider = AgentProviderConfig(
            id = config.providerId.ifBlank { LEGACY_PROVIDER_ID },
            preset = preset,
            name = config.providerName.ifBlank { preset.displayName },
            baseUrl = config.baseUrl,
            apiKey = config.apiKey,
            models = listOf(config.model)
        )
        saveSettings(
            AgentModelSettings(
                providers = listOf(provider),
                activeProviderId = provider.id,
                activeModel = config.model,
                externalModelEnabled = config.enabled
            )
        )
    }

    private fun loadLegacySettings(): AgentModelSettings {
        val baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty()
        val model = preferences.getString(KEY_MODEL, "").orEmpty()
        if (baseUrl.isBlank() && model.isBlank()) return AgentModelSettings()
        val preset = ModelProviderPreset.infer(baseUrl)
        val provider = AgentProviderConfig(
            id = LEGACY_PROVIDER_ID,
            preset = preset,
            name = preset.displayName,
            baseUrl = baseUrl,
            apiKey = if (holder.encrypted) preferences.getString(KEY_API_KEY, "").orEmpty() else "",
            models = listOf(model)
        )
        return AgentModelSettings(
            providers = listOf(provider),
            activeProviderId = provider.id,
            activeModel = model,
            externalModelEnabled = preferences.getBoolean(KEY_ENABLED, false)
        )
    }

    private fun encodeSettings(settings: AgentModelSettings, includeSecrets: Boolean): String = JSONObject()
        .put("externalModelEnabled", settings.externalModelEnabled)
        .put("activeProviderId", settings.activeProviderId.orEmpty())
        .put("activeModel", settings.activeModel.orEmpty())
        .put(
            "providers",
            JSONArray().apply {
                settings.providers.forEach { provider ->
                    put(
                        JSONObject()
                            .put("id", provider.id)
                            .put("preset", provider.preset.name)
                            .put("name", provider.name)
                            .put("baseUrl", provider.baseUrl)
                            .put("apiKey", if (includeSecrets) provider.apiKey else "")
                            .put("enabled", provider.enabled)
                            .put("models", JSONArray(provider.models))
                    )
                }
            }
        )
        .toString()

    private fun decodeSettings(raw: String): AgentModelSettings? = runCatching {
        val root = JSONObject(raw)
        val providersJson = root.optJSONArray("providers") ?: JSONArray()
        val providers = buildList {
            for (index in 0 until providersJson.length()) {
                val item = providersJson.optJSONObject(index) ?: continue
                val preset = runCatching {
                    ModelProviderPreset.valueOf(item.optString("preset"))
                }.getOrDefault(ModelProviderPreset.CUSTOM)
                val modelsJson = item.optJSONArray("models") ?: JSONArray()
                val models = buildList {
                    for (modelIndex in 0 until modelsJson.length()) {
                        modelsJson.optString(modelIndex).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                add(
                    AgentProviderConfig(
                        id = item.optString("id").ifBlank { "provider-$index" },
                        preset = preset,
                        name = item.optString("name").ifBlank { preset.displayName },
                        baseUrl = item.optString("baseUrl"),
                        apiKey = if (holder.encrypted) item.optString("apiKey") else "",
                        models = models,
                        enabled = item.optBoolean("enabled", true)
                    )
                )
            }
        }
        AgentModelSettings(
            providers = providers,
            activeProviderId = root.optString("activeProviderId").takeIf { it.isNotBlank() },
            activeModel = root.optString("activeModel").takeIf { it.isNotBlank() },
            externalModelEnabled = root.optBoolean("externalModelEnabled", false)
        )
    }.getOrNull()

    private companion object {
        const val KEY_SETTINGS_V2 = "model_settings_v2"
        const val LEGACY_PROVIDER_ID = "legacy-provider"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_ENABLED = "enabled"
    }
}
