package com.skypulse.weather.agent.tools

interface AgentTool {
    val name: String
    val description: String

    suspend fun execute(input: String): String
}
