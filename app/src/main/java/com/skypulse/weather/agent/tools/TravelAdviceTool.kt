package com.skypulse.weather.agent.tools

/**
 * Agent tool that combines weather context into travel suggestions.
 */
class TravelAdviceTool : AgentTool {

    override val name: String = "travel.advice"

    override val description: String = "Generate travel recommendations from weather context"

    override suspend fun execute(input: String): String {
        return "Travel advice placeholder: $input"
    }
}
