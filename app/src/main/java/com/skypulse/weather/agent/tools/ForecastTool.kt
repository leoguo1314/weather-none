package com.skypulse.weather.agent.tools

/**
 * Agent tool for forecast queries.
 * Bridges future forecast capability into Agent Runtime.
 */
class ForecastTool(
    private val queryForecast: suspend (String) -> String
) : AgentTool {

    override val name: String = "forecast.query"

    override val description: String =
        "Query multi-day weather forecast"

    override suspend fun execute(input: String): String {
        return queryForecast(input)
    }
}
