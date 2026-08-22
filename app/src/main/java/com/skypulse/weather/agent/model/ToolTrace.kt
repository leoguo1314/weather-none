package com.skypulse.weather.agent.model

/**
 * Execution trace for tools used by Weather Agent.
 */
data class ToolTrace(
    val toolName: String,
    val status: String,
    val result: String
)
