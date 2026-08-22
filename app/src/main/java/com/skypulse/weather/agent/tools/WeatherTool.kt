package com.skypulse.weather.agent.tools

/**
 * Weather capability exposed to the AI Agent.
 * This adapter isolates the LLM layer from weather data providers.
 */
class WeatherTool(
    private val queryWeather: suspend (String) -> String
) : AgentTool {

    override val name: String = "weather.query"

    override val description: String =
        "Query current weather and forecast information by location"

    override suspend fun execute(input: String): String {
        return queryWeather(input)
    }
}
