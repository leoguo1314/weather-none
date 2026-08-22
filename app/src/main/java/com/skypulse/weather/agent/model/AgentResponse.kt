package com.skypulse.weather.agent.model

/**
 * Unified response returned by Weather Agent runtime.
 */
data class AgentResponse(
    val answer: String,
    val traces: List<ToolTrace> = emptyList(),
    val confidence: Float = 0f
)
