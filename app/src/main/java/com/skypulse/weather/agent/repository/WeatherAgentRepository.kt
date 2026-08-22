package com.skypulse.weather.agent.repository

/**
 * Agent-facing weather data abstraction.
 * Keeps Agent runtime independent from concrete weather providers.
 */
interface WeatherAgentRepository {

    suspend fun queryWeather(
        input: String
    ): String

    suspend fun queryForecast(
        input: String
    ): String
}
