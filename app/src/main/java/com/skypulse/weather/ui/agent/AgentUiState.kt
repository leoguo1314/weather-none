package com.skypulse.weather.ui.agent

import com.skypulse.weather.agent.AgentModelConfig
import com.skypulse.weather.agent.AgentModelSettings

data class AgentUiState(
    val messages: List<AgentMessage> = emptyList(),
    val isThinking: Boolean = false,
    val errorMessage: String? = null,
    val config: AgentModelConfig = AgentModelConfig(),
    val modelSettings: AgentModelSettings = AgentModelSettings(),
    val isTestingModel: Boolean = false,
    val modelTestMessage: String? = null,
    val activeCityName: String? = null,
    val secureStorageAvailable: Boolean = true
)
