package com.skypulse.weather.agent

class ToolRegistry(
    private val tools: Map<String, AgentTool>
) {
    suspend fun execute(name: String, input: String): String {
        return tools[name]?.execute(input)
            ?: "Tool not found: $name"
    }
}

interface AgentTool {
    val name: String
        get() = "unknown"

    val description: String
        get() = ""

    suspend fun execute(input: String): String
}
