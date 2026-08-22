package com.skypulse.weather.agent.runtime

import com.skypulse.weather.agent.AgentPlanner
import com.skypulse.weather.agent.ToolRegistry
import com.skypulse.weather.agent.model.AgentResponse
import com.skypulse.weather.agent.model.ToolTrace

/**
 * Runtime execution pipeline for SkyPulse Weather Agent.
 *
 * Flow:
 * input -> planner -> tool registry -> response
 */
class DefaultAgentRuntime(
    private val planner: AgentPlanner,
    private val toolRegistry: ToolRegistry
) {

    suspend fun execute(input: String): AgentResponse {
        val plan = planner.createPlan(input)

        val traces = plan.tools.map { toolName ->
            val result = toolRegistry.execute(toolName, input)

            ToolTrace(
                toolName = toolName,
                status = "success",
                result = result
            )
        }

        val answer = traces.joinToString("\n") { it.result }

        return AgentResponse(
            answer = answer,
            traces = traces,
            confidence = if (traces.isNotEmpty()) 0.8f else 0.3f
        )
    }
}
