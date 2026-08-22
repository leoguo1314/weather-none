package com.skypulse.weather.ui.agent

sealed interface AgentUiState {
    data object Idle : AgentUiState

    data object Thinking : AgentUiState

    data class Success(
        val messages: List<AgentMessage>
    ) : AgentUiState

    data class Error(
        val message: String
    ) : AgentUiState
}
