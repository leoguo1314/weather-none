package com.skypulse.weather.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAgentEngineTest {
    private val rainySnapshot = AgentSnapshot(
        city = "深圳",
        skycon = "HEAVY_RAIN",
        temperature = 31.0,
        apparentTemperature = 35.0,
        humidity = 88.0,
        windSpeed = 35.0,
        aqi = 42.0,
        forecastKeypoint = "未来两小时有强降雨",
        alertTitles = listOf("深圳市暴雨橙色预警"),
        daily = listOf(
            AgentForecastDay("2026-08-23", "HEAVY_RAIN", 33.0, 27.0, 80.0)
        ),
        updatedAtMillis = 1L
    )

    @Test
    fun `plans weather travel clothing and alert tools`() {
        val result = WeatherAgentEngine().execute("今天出门怎么穿，要带伞吗，有没有预警？", rainySnapshot)

        val tools = result.traces.map { it.toolName }
        assertTrue(tools.contains("weather.query"))
        assertTrue(tools.contains("travel.advice"))
        assertTrue(tools.contains("clothing.advice"))
        assertTrue(tools.contains("alert.query"))
        assertTrue(result.answer.contains("带伞"))
        assertTrue(result.answer.contains("暴雨橙色预警"))
    }

    @Test
    fun `uses air quality tool for health question`() {
        val result = WeatherAgentEngine().execute("空气质量怎么样，需要口罩吗？", rainySnapshot.copy(aqi = 165.0))

        assertTrue(result.traces.any { it.toolName == "air_quality.query" })
        assertTrue(result.answer.contains("口罩"))
    }
}
