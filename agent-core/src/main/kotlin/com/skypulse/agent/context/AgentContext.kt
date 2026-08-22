package com.skypulse.agent.context

/**
 * Runtime context passed between planner, tools and LLM.
 */
data class AgentContext(
    val userInput: String,
    val location: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
