package com.skypulse.weather.agent.tools

class ToolRegistry(
    private val tools: List<AgentTool>
) {
    fun find(name: String): AgentTool? =
        tools.firstOrNull { it.name == name }

    fun availableTools(): List<String> =
        tools.map { it.name }
}
