package com.skypulse.weather.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypulse.weather.agent.AgentConfigStore
import com.skypulse.weather.agent.AgentModelConfig
import com.skypulse.weather.agent.OpenAiCompatibleClient
import com.skypulse.weather.agent.WeatherAgentEngine
import com.skypulse.weather.agent.toAgentSnapshot
import com.skypulse.weather.repository.CityRepository
import com.skypulse.weather.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AgentChatViewModel @Inject constructor(
    private val cityRepository: CityRepository,
    private val weatherRepository: WeatherRepository,
    private val configStore: AgentConfigStore,
    private val modelClient: OpenAiCompatibleClient
) : ViewModel() {
    private val engine = WeatherAgentEngine()

    private val welcome = AgentMessage(
        id = "welcome",
        role = MessageRole.ASSISTANT,
        content = "你好，我是 SkyPulse AI 天气助手。我会读取当前城市的实时天气，分析趋势、空气质量、穿衣和出行风险。即使没有配置大模型，也可以离线完成天气推理。"
    )
    private val _state = MutableStateFlow(
        AgentUiState(
            messages = listOf(welcome),
            config = configStore.load()
        )
    )
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    fun saveConfig(config: AgentModelConfig) {
        configStore.save(config)
        _state.value = _state.value.copy(config = config, errorMessage = null)
    }

    fun sendMessage(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank() || _state.value.isThinking) return
        val userMessage = AgentMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = trimmed
        )
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMessage,
            isThinking = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { loadSnapshot() }
                val localResult = engine.execute(trimmed, snapshot)
                val config = _state.value.config
                var source = ResponseSource.LOCAL_AGENT
                var modelWarning: String? = null
                val answer = if (config.isUsable) {
                    runCatching {
                        modelClient.complete(
                            config = config,
                            userInput = trimmed,
                            snapshot = snapshot,
                            toolContext = localResult.traces.joinToString("\n") {
                                "${it.toolName}: ${it.result}"
                            }
                        )
                    }.onSuccess {
                        source = ResponseSource.EXTERNAL_MODEL
                    }.getOrElse { error ->
                        modelWarning = "模型连接失败，已切换为本地 Agent：${error.message.orEmpty()}"
                        localResult.answer
                    }
                } else {
                    localResult.answer
                }
                val assistantMessage = AgentMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    content = answer,
                    traces = localResult.traces.map {
                        ToolTrace(it.toolName, it.status, it.result)
                    },
                    source = source
                )
                _state.value = _state.value.copy(
                    messages = _state.value.messages + assistantMessage,
                    isThinking = false,
                    errorMessage = modelWarning
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isThinking = false,
                    errorMessage = e.message ?: "天气分析失败，请先返回主页刷新天气。"
                )
            }
        }
    }

    fun clearConversation() {
        _state.value = _state.value.copy(messages = listOf(welcome), errorMessage = null)
    }

    private suspend fun loadSnapshot() = cityRepository.getCities()
        .let { cities ->
            val city = cities.firstOrNull { it.isCurrentLocation } ?: cities.firstOrNull()
                ?: error("还没有可用城市，请先返回主页完成定位。")
            var weather = weatherRepository.getWeatherFromCache(city.id)
            if (weather == null || weatherRepository.isCacheStale(city.id, 15 * 60 * 1000L)) {
                weatherRepository.getWeather(city.longitude, city.latitude)
                    .getOrNull()
                    ?.also {
                        weatherRepository.saveWeatherToCache(city.id, it)
                        weather = it
                    }
            }
            val resolved = weather ?: error("未找到天气数据，请检查网络并在主页下拉刷新。")
            resolved.toAgentSnapshot(
                city = city.name,
                updatedAtMillis = weatherRepository.getLastFetchTime(city.id)
            )
        }
}
