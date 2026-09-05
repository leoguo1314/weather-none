package com.skypulse.weather.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypulse.weather.data.ActivationResult
import com.skypulse.weather.data.DebugWeatherPreset
import com.skypulse.weather.data.MembershipRepository
import com.skypulse.weather.data.SettingsRepository
import com.skypulse.weather.data.ThemeMode
import com.skypulse.weather.data.WeatherSettings
import com.skypulse.weather.data.WeatherSourceConfig
import com.skypulse.weather.data.WeatherSourceConfigStore
import com.skypulse.weather.data.remote.WeatherSourceClient
import com.skypulse.weather.notification.WeatherNotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class WeatherSourceTestState {
    data object Idle : WeatherSourceTestState()
    data object Testing : WeatherSourceTestState()
    data class Success(val message: String) : WeatherSourceTestState()
    data class Error(val message: String) : WeatherSourceTestState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val membershipRepository: MembershipRepository,
    private val weatherSourceConfigStore: WeatherSourceConfigStore,
    private val weatherSourceClient: WeatherSourceClient
) : ViewModel() {

    val settings: StateFlow<WeatherSettings> = settingsRepository.settings

    val isPremium: StateFlow<Boolean> = membershipRepository.isPremium

    val weatherSourceConfig: StateFlow<WeatherSourceConfig> = weatherSourceConfigStore.config
    val weatherSourceSecretsSecure: Boolean
        get() = weatherSourceConfigStore.storesSecretsSecurely

    private val _weatherSourceTestState = MutableStateFlow<WeatherSourceTestState>(WeatherSourceTestState.Idle)
    val weatherSourceTestState: StateFlow<WeatherSourceTestState> = _weatherSourceTestState.asStateFlow()
    private var weatherSourceTestJob: Job? = null

    fun activateCode(code: String): ActivationResult {
        return membershipRepository.activateCode(code)
    }

    fun getDeviceId(): String = membershipRepository.getDeviceId()

    fun getActivatedAt(): Long = membershipRepository.getActivatedAt()

    fun setRainAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setRainAlert(enabled)
    }

    fun setWarningAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setWarningAlert(enabled)
    }

    fun setTempChangeAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setTempChangeAlert(enabled)
    }

    fun setWindAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setWindAlert(enabled)
    }

    fun setTyphoonAlert(enabled: Boolean) = updateAlertSetting {
        settingsRepository.setTyphoonAlert(enabled)
    }

    fun setShowHourlyAqi(enabled: Boolean) = settingsRepository.setShowHourlyAqi(enabled)
    fun setShowHourlyUv(enabled: Boolean) = settingsRepository.setShowHourlyUv(enabled)
    fun setShowHourlyWind(enabled: Boolean) = settingsRepository.setShowHourlyWind(enabled)
    fun setShowHourlyWindGust(enabled: Boolean) = settingsRepository.setShowHourlyWindGust(enabled)
    fun setShowCardDetail(enabled: Boolean) = settingsRepository.setShowCardDetail(enabled)
    fun setShowCardSunriseSunset(enabled: Boolean) = settingsRepository.setShowCardSunriseSunset(enabled)
    fun setShowCardMinutely(enabled: Boolean) = settingsRepository.setShowCardMinutely(enabled)
    fun setShowCardTyphoon(enabled: Boolean) = settingsRepository.setShowCardTyphoon(enabled)
    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun saveWeatherSourceConfig(config: WeatherSourceConfig) {
        config.validationError()?.let { error(it) }
        weatherSourceTestJob?.cancel()
        weatherSourceConfigStore.save(config)
        _weatherSourceTestState.value = WeatherSourceTestState.Idle
    }

    fun testWeatherSource(config: WeatherSourceConfig) {
        val validationError = config.validationError()
        if (validationError != null) {
            _weatherSourceTestState.value = WeatherSourceTestState.Error(validationError)
            return
        }
        weatherSourceTestJob?.cancel()
        _weatherSourceTestState.value = WeatherSourceTestState.Testing
        weatherSourceTestJob = viewModelScope.launch {
            _weatherSourceTestState.value = runCatching {
                weatherSourceClient.test(config)
            }.fold(
                onSuccess = { result ->
                    val temperature = result.temperature?.let { "，北京 ${it.toInt()}℃" }.orEmpty()
                    WeatherSourceTestState.Success(
                        "${result.sourceName} 连接成功$temperature，${result.forecastDays} 日预报可用"
                    )
                },
                onFailure = { error ->
                    WeatherSourceTestState.Error(error.message ?: "天气源连接失败")
                }
            )
        }
    }

    fun resetWeatherSourceTest() {
        weatherSourceTestJob?.cancel()
        weatherSourceTestJob = null
        _weatherSourceTestState.value = WeatherSourceTestState.Idle
    }

    /** 设置页底部版本号双击开启开发者选项，返回是否刚刚开启 */
    fun onVersionDoubleTap(): Boolean = settingsRepository.enableDeveloperMode()

    fun setDeveloperModeEnabled(enabled: Boolean) = settingsRepository.setDeveloperModeEnabled(enabled)

    fun setDebugWeatherPreset(preset: DebugWeatherPreset?) = settingsRepository.setDebugWeatherPreset(preset)

    private fun updateAlertSetting(update: () -> Unit) {
        update()
        WeatherNotificationScheduler.scheduleIfNeeded(appContext)
    }
}
