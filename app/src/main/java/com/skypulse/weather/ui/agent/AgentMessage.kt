package com.skypulse.weather.ui.agent

/**
 * Message model for AI weather assistant conversation.
 */
data class AgentMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val traces: List<ToolTrace> = emptyList(),
    val source: ResponseSource = ResponseSource.LOCAL_AGENT
)

enum class MessageRole {
    USER,
    ASSISTANT
}

enum class ResponseSource {
    LOCAL_AGENT,
    EXTERNAL_MODEL
}

data class ToolTrace(
    val toolName: String,
    val status: String,
    val result: String
)
