package com.skypulse.weather.agent

/**
 * Core runtime for SkyPulse AI Weather Agent.
 * Coordinates user intent, tools and LLM response generation.
 */
class AgentRuntime(
    private val planner: AgentPlanner,
    private val registry: ToolRegistry,
    private val llmClient: LLMClient
) {
    suspend fun execute(input: String): String {
        val plan = planner.createPlan(input)
        val results = plan.tools.map { toolName ->
            registry.execute(toolName, input)
        }
        return llmClient.generate(
            """
            User request:
            $input

            Tool results:
            ${results.joinToString("\n")}

            Provide concise weather advice.
            """.trimIndent()
        )
    }
}
