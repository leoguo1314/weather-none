package com.skypulse.weather.agent.tools

/**
 * Agent tool for forecast queries.
 * Bridges future forecast capability into Agent Runtime.
 */
class ForecastTool : AgentTool {

    override val name: String = "forecast.query"

    override val description: String = "Query multi-day weather forecast"

    override suspend fun execute(input: String): String {
        // TODO connect to ForecastRepository
        return "Forecast query placeholder: $input"
    }
}
