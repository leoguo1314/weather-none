package com.skypulse.weather.agent

/**
 * Agent execution pipeline skeleton.
 *
 * Flow:
 * User Input -> Context -> Planner -> Tool Registry -> LLM -> Memory
 */
class AgentChain {
    suspend fun execute(input: String): String {
        return "Agent pipeline initialized: $input"
    }
}
