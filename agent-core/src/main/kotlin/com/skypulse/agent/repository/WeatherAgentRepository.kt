package com.skypulse.agent.repository

/**
 * Agent-facing weather data abstraction.
 *
 * Keeps Agent Runtime independent from concrete weather providers.
 */
interface WeatherAgentRepository {
    suspend fun currentWeather(location: String): String

    suspend fun forecast(location: String, days: Int = 7): String
}
