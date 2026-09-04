package com.skypulse.weather.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AgentModelConfig(
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

    val storesApiKeySecurely: Boolean
        get() = holder.encrypted

    init {
        if (!holder.encrypted) {
            preferences.edit().remove(KEY_API_KEY).apply()
        }
    }

    fun load(): AgentModelConfig = AgentModelConfig(
        baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
        apiKey = if (holder.encrypted) preferences.getString(KEY_API_KEY, "").orEmpty() else "",
        model = preferences.getString(KEY_MODEL, "").orEmpty(),
        enabled = preferences.getBoolean(KEY_ENABLED, false)
    )

    fun save(config: AgentModelConfig) {
        val editor = preferences.edit()
            .putString(KEY_BASE_URL, config.baseUrl.trim())
            .putString(KEY_MODEL, config.model.trim())
            .putBoolean(KEY_ENABLED, config.enabled)
        if (holder.encrypted) {
            editor.putString(KEY_API_KEY, config.apiKey.trim())
        } else {
            editor.remove(KEY_API_KEY)
        }
        editor.apply()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_ENABLED = "enabled"
    }
}
