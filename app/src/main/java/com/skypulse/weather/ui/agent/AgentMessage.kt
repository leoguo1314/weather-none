package com.skypulse.weather.ui.agent

/**
 * Message model for AI weather assistant conversation.
 */
data class AgentMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val traces: List<ToolTrace> = emptyList()
)

enum class MessageRole {
    USER,
    ASSISTANT
}

data class ToolTrace(
    val toolName: String,
    val status: String,
    val result: String
)
