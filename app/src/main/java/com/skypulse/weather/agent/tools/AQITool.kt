package com.skypulse.weather.agent.tools

/**
 * Agent tool for air quality analysis.
 */
class AQITool : AgentTool {

    override val name: String = "air_quality.query"

    override val description: String = "Query air quality and health advice"

    override suspend fun execute(input: String): String {
        // TODO connect to AQI data source
        return "AQI query placeholder: $input"
    }
}
