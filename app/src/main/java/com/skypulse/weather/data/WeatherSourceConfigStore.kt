package com.skypulse.weather.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class WeatherSource(
    val displayName: String,
    val description: String
) {
    OPEN_METEO("Open-Meteo", "免密钥，全球多模型预报，默认推荐"),
    MET_NORWAY("MET Norway", "免密钥，挪威气象研究所全球九日预报"),
    CAIYUN("彩云天气", "分钟级降水和中国气象预警能力更完整"),
    CUSTOM("自定义兼容源", "使用 Open-Meteo 兼容 JSON 的 HTTPS 接口")
}

data class WeatherSourceConfig(
    val source: WeatherSource = WeatherSource.OPEN_METEO,
    val caiyunToken: String = "",
    val customName: String = "自定义天气源",
    val customUrlTemplate: String = DEFAULT_CUSTOM_URL_TEMPLATE,
    val customHeaderName: String = "",
    val customHeaderValue: String = ""
) {
    val displayName: String
        get() = if (source == WeatherSource.CUSTOM) {
            customName.trim().ifBlank { source.displayName }
        } else {
            source.displayName
        }

    fun validationError(): String? = when (source) {
        WeatherSource.CUSTOM -> when {
            customName.isBlank() -> "请输入天气源名称"
            !customUrlTemplate.trim().startsWith("https://") -> "自定义地址必须使用 HTTPS"
            !customUrlTemplate.contains("{lat}") || !customUrlTemplate.contains("{lon}") ->
                "URL 模板必须包含 {lat} 和 {lon}"
            customHeaderName.isBlank() && customHeaderValue.isNotBlank() && !customUrlTemplate.contains("{key}") ->
                "已填写密钥，请配置请求头名称或在 URL 中使用 {key}"
            else -> null
        }
        else -> null
    }

    fun normalized(): WeatherSourceConfig = copy(
        caiyunToken = caiyunToken.trim(),
        customName = customName.trim().ifBlank { "自定义天气源" },
        customUrlTemplate = customUrlTemplate.trim(),
        customHeaderName = customHeaderName.trim(),
        customHeaderValue = customHeaderValue.trim()
    )

    companion object {
        const val DEFAULT_CUSTOM_URL_TEMPLATE =
            "https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation," +
                "weather_code,pressure_msl,wind_speed_10m,wind_direction_10m,is_day" +
                "&hourly=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation," +
                "precipitation_probability,weather_code,pressure_msl,cloud_cover,visibility," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "precipitation_probability_max,sunrise,sunset,wind_speed_10m_max,wind_direction_10m_dominant,uv_index_max" +
                "&forecast_days={days}&timezone=auto"
    }
}

@Singleton
class WeatherSourceConfigStore @Inject constructor(
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
                "weather_source_config",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ),
            encrypted = true
        )
    }.getOrElse {
        PreferencesHolder(
            preferences = context.getSharedPreferences("weather_source_config_fallback", Context.MODE_PRIVATE),
            encrypted = false
        )
    }

    private val preferences = holder.preferences
    private var sessionCaiyunToken = ""
    private var sessionCustomHeaderValue = ""
    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<WeatherSourceConfig> = _config.asStateFlow()

    val storesSecretsSecurely: Boolean
        get() = holder.encrypted

    init {
        if (!holder.encrypted) {
            preferences.edit()
                .remove(KEY_CAIYUN_TOKEN)
                .remove(KEY_CUSTOM_HEADER_VALUE)
                .apply()
        }
    }

    fun save(config: WeatherSourceConfig) {
        val normalized = config.normalized()
        sessionCaiyunToken = normalized.caiyunToken
        sessionCustomHeaderValue = normalized.customHeaderValue
        val editor = preferences.edit()
            .putString(KEY_SOURCE, normalized.source.name)
            .putString(KEY_CUSTOM_NAME, normalized.customName)
            .putString(KEY_CUSTOM_URL, normalized.customUrlTemplate)
            .putString(KEY_CUSTOM_HEADER_NAME, normalized.customHeaderName)
        if (holder.encrypted) {
            editor
                .putString(KEY_CAIYUN_TOKEN, normalized.caiyunToken)
                .putString(KEY_CUSTOM_HEADER_VALUE, normalized.customHeaderValue)
        } else {
            editor
                .remove(KEY_CAIYUN_TOKEN)
                .remove(KEY_CUSTOM_HEADER_VALUE)
        }
        editor.apply()
        _config.value = normalized
    }

    private fun readConfig(): WeatherSourceConfig {
        val source = runCatching {
            WeatherSource.valueOf(preferences.getString(KEY_SOURCE, WeatherSource.OPEN_METEO.name).orEmpty())
        }.getOrDefault(WeatherSource.OPEN_METEO)
        return WeatherSourceConfig(
            source = source,
            caiyunToken = if (holder.encrypted) {
                preferences.getString(KEY_CAIYUN_TOKEN, "").orEmpty()
            } else {
                sessionCaiyunToken
            },
            customName = preferences.getString(KEY_CUSTOM_NAME, "自定义天气源").orEmpty(),
            customUrlTemplate = preferences.getString(
                KEY_CUSTOM_URL,
                WeatherSourceConfig.DEFAULT_CUSTOM_URL_TEMPLATE
            ).orEmpty(),
            customHeaderName = preferences.getString(KEY_CUSTOM_HEADER_NAME, "").orEmpty(),
            customHeaderValue = if (holder.encrypted) {
                preferences.getString(KEY_CUSTOM_HEADER_VALUE, "").orEmpty()
            } else {
                sessionCustomHeaderValue
            }
        ).normalized()
    }

    private companion object {
        const val KEY_SOURCE = "source"
        const val KEY_CAIYUN_TOKEN = "caiyun_token"
        const val KEY_CUSTOM_NAME = "custom_name"
        const val KEY_CUSTOM_URL = "custom_url"
        const val KEY_CUSTOM_HEADER_NAME = "custom_header_name"
        const val KEY_CUSTOM_HEADER_VALUE = "custom_header_value"
    }
}
