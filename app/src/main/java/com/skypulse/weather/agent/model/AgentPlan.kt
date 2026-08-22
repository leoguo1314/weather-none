package com.skypulse.weather.agent.model

/**
 * Agent execution plan generated from user intent.
 */
data class AgentPlan(
    val intent: String,
    val tools: List<String>,
    val parameters: Map<String, String> = emptyMap()
)
