package com.skypulse.weather.ui.agent

import com.skypulse.weather.agent.AgentModelConfig

data class AgentUiState(
    val messages: List<AgentMessage> = emptyList(),
    val isThinking: Boolean = false,
    val errorMessage: String? = null,
    val config: AgentModelConfig = AgentModelConfig(),
    val activeCityName: String? = null,
    val secureStorageAvailable: Boolean = true
)
