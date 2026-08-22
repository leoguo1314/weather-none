package com.skypulse.weather.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AgentChatViewModel(
    private val runtime: suspend (String) -> AgentMessage
) : ViewModel() {

    private val _state = MutableStateFlow<AgentUiState>(AgentUiState.Idle)
    val state: StateFlow<AgentUiState> = _state

    private val messages = mutableListOf<AgentMessage>()

    fun sendMessage(input: String) {
        viewModelScope.launch {
            _state.value = AgentUiState.Thinking

            try {
                val response = runtime(input)
                messages.add(response)
                _state.value = AgentUiState.Success(messages.toList())
            } catch (e: Exception) {
                _state.value = AgentUiState.Error(e.message ?: "unknown error")
            }
        }
    }
}
