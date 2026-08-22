package com.skypulse.weather.agent.tools

/**
 * Agent tool for air quality analysis.
 *
 * Adapter layer between Agent Runtime and AQI data provider.
 */
class AQITool(
    private val queryAQI: suspend (String) -> String
) : AgentTool {

    override val name: String = "air_quality.query"

    override val description: String =
        "Query air quality and health advice"

    override suspend fun execute(input: String): String {
        return queryAQI(input)
    }
}
