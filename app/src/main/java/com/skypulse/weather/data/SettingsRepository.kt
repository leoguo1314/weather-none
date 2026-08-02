package com.skypulse.weather.data

import android.content.Context
import android.content.SharedPreferences
import com.skypulse.weather.notification.WeatherNotificationScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class WeatherSettings(
    val rainAlert: Boolean = true,
    val warningAlert: Boolean = true,
    val tempChangeAlert: Boolean = false,
    val windAlert: Boolean = false,
    val typhoonAlert: Boolean = true,
    val showHourlyAqi: Boolean = true,
    val showHourlyUv: Boolean = true,
    val showHourlyWind: Boolean = true,
    val showHourlyWindGust: Boolean = false,
    val showCardDetail: Boolean = true,
    val showCardSunriseSunset: Boolean = true,
    val showCardMinutely: Boolean = true,
    val showCardTyphoon: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val developerModeEnabled: Boolean = false,
    val debugWeatherPreset: DebugWeatherPreset? = null
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(
        WeatherNotificationScheduler.PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<WeatherSettings> = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = readSettings()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setRainAlert(enabled: Boolean) = updateBoolean(KEY_RAIN_ALERT, enabled)
    fun setWarningAlert(enabled: Boolean) = updateBoolean(KEY_WARNING_ALERT, enabled)
    fun setTempChangeAlert(enabled: Boolean) = updateBoolean(KEY_TEMP_CHANGE_ALERT, enabled)
    fun setWindAlert(enabled: Boolean) = updateBoolean(KEY_WIND_ALERT, enabled)
    fun setTyphoonAlert(enabled: Boolean) = updateBoolean(KEY_TYPHOON_ALERT, enabled)
    fun setShowHourlyAqi(enabled: Boolean) = updateBoolean(KEY_SHOW_HOURLY_AQI, enabled)
    fun setShowHourlyUv(enabled: Boolean) = updateBoolean(KEY_SHOW_HOURLY_UV, enabled)
    fun setShowHourlyWind(enabled: Boolean) = updateBoolean(KEY_SHOW_HOURLY_WIND, enabled)
    fun setShowHourlyWindGust(enabled: Boolean) = updateBoolean(KEY_SHOW_HOURLY_WIND_GUST, enabled)
    fun setShowCardDetail(enabled: Boolean) = updateBoolean(KEY_SHOW_CARD_DETAIL, enabled)
    fun setShowCardSunriseSunset(enabled: Boolean) = updateBoolean(KEY_SHOW_CARD_SUNRISE_SUNSET, enabled)
    fun setShowCardMinutely(enabled: Boolean) = updateBoolean(KEY_SHOW_CARD_MINUTELY, enabled)
    fun setShowCardTyphoon(enabled: Boolean) = updateBoolean(KEY_SHOW_CARD_TYPHOON, enabled)
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = readSettings()
    }

    fun setDeveloperModeEnabled(enabled: Boolean) = updateBoolean(KEY_DEVELOPER_MODE_ENABLED, enabled)

    fun setDebugWeatherPreset(preset: DebugWeatherPreset?) {
        prefs.edit().putString(KEY_DEBUG_WEATHER_PRESET, preset?.name).apply()
        _settings.value = readSettings()
    }

    /**
     * 开启开发者选项（设置页底部版本号双击触发）。
     * @return 本次是否恰好从关闭状态变为开启（用于提示 Toast）
     */
    fun enableDeveloperMode(): Boolean {
        val alreadyEnabled = prefs.getBoolean(KEY_DEVELOPER_MODE_ENABLED, false)
        if (!alreadyEnabled) {
            prefs.edit().putBoolean(KEY_DEVELOPER_MODE_ENABLED, true).apply()
            _settings.value = readSettings()
        }
        return !alreadyEnabled
    }

    private fun updateBoolean(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
        _settings.value = readSettings()
    }

    private fun readSettings(): WeatherSettings = WeatherSettings(
        rainAlert = prefs.getBoolean(KEY_RAIN_ALERT, true),
        warningAlert = prefs.getBoolean(KEY_WARNING_ALERT, true),
        tempChangeAlert = prefs.getBoolean(KEY_TEMP_CHANGE_ALERT, false),
        windAlert = prefs.getBoolean(KEY_WIND_ALERT, false),
        typhoonAlert = prefs.getBoolean(KEY_TYPHOON_ALERT, true),
        showHourlyAqi = prefs.getBoolean(KEY_SHOW_HOURLY_AQI, true),
        showHourlyUv = prefs.getBoolean(KEY_SHOW_HOURLY_UV, true),
        showHourlyWind = prefs.getBoolean(KEY_SHOW_HOURLY_WIND, true),
        showHourlyWindGust = prefs.getBoolean(KEY_SHOW_HOURLY_WIND_GUST, false),
        showCardDetail = prefs.getBoolean(KEY_SHOW_CARD_DETAIL, true),
        showCardSunriseSunset = prefs.getBoolean(KEY_SHOW_CARD_SUNRISE_SUNSET, true),
        showCardMinutely = prefs.getBoolean(KEY_SHOW_CARD_MINUTELY, true),
        showCardTyphoon = prefs.getBoolean(KEY_SHOW_CARD_TYPHOON, true),
        themeMode = try {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        } catch (_: Exception) { ThemeMode.SYSTEM },
        developerModeEnabled = prefs.getBoolean(KEY_DEVELOPER_MODE_ENABLED, false),
        debugWeatherPreset = DebugWeatherPreset.fromName(prefs.getString(KEY_DEBUG_WEATHER_PRESET, null))
    )

    companion object {
        private const val KEY_RAIN_ALERT = "rain_alert"
        private const val KEY_WARNING_ALERT = "warning_alert"
        private const val KEY_TEMP_CHANGE_ALERT = "temp_change_alert"
        private const val KEY_WIND_ALERT = "wind_alert"
        private const val KEY_TYPHOON_ALERT = "typhoon_alert"
        private const val KEY_SHOW_HOURLY_AQI = "show_hourly_aqi"
        private const val KEY_SHOW_HOURLY_UV = "show_hourly_uv"
        private const val KEY_SHOW_HOURLY_WIND = "show_hourly_wind"
        private const val KEY_SHOW_HOURLY_WIND_GUST = "show_hourly_wind_gust"
        private const val KEY_SHOW_CARD_DETAIL = "show_card_detail"
        private const val KEY_SHOW_CARD_SUNRISE_SUNSET = "show_card_sunrise_sunset"
        private const val KEY_SHOW_CARD_MINUTELY = "show_card_minutely"
        private const val KEY_SHOW_CARD_TYPHOON = "show_card_typhoon"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEVELOPER_MODE_ENABLED = "developer_mode_enabled"
        private const val KEY_DEBUG_WEATHER_PRESET = "debug_weather_preset"
    }
}