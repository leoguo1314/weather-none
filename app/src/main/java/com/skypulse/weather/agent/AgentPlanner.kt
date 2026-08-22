package com.skypulse.weather.agent

class AgentPlanner {
    fun createPlan(input: String): AgentPlan {
        val lower = input.lowercase()
        val tools = mutableListOf<String>()

        if (lower.contains("天气") || lower.contains("weather")) {
            tools += "weather.query"
        }
        if (lower.contains("空气") || lower.contains("aqi")) {
            tools += "air_quality.query"
        }

        if (tools.isEmpty()) {
            tools += "weather.query"
        }

        return AgentPlan(tools)
    }
}

data class AgentPlan(
    val tools: List<String>
)
