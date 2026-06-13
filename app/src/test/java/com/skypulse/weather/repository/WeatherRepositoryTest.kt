package com.skypulse.weather.repository

import com.skypulse.weather.api.CaiyunApi
import com.skypulse.weather.model.DailyForecast
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.model.WeatherResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WeatherRepositoryTest {

    private lateinit var api: CaiyunApi
    private lateinit var repository: WeatherRepository

    @Before
    fun setup() {
        api = mockk()
        repository = WeatherRepository(api)
    }

    @Test
    fun `getWeather returns success when API returns ok status`() = runTest {
        val mockResponse = WeatherResponse(status = "ok")
        coEvery {
            api.getWeather(
                token = any(),
                longitude = 116.4074,
                latitude = 39.9042,
                alert = true,
                dailySteps = 15,
                hourlySteps = 48,
                begin = null
            )
        } returns mockResponse

        val result = repository.getWeather(116.4074, 39.9042)

        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull()?.status)
    }

    @Test
    fun `getWeather returns failure when API returns non-ok status`() = runTest {
        val mockResponse = WeatherResponse(status = "error")
        coEvery {
            api.getWeather(
                token = any(),
                longitude = any(),
                latitude = any(),
                alert = any(),
                dailySteps = any(),
                hourlySteps = any(),
                begin = null
            )
        } returns mockResponse

        val result = repository.getWeather(116.4074, 39.9042)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API error") == true)
    }

    @Test
    fun `getWeather returns failure when API throws exception`() = runTest {
        coEvery {
            api.getWeather(
                token = any(),
                longitude = any(),
                latitude = any(),
                alert = any(),
                dailySteps = any(),
                hourlySteps = any(),
                begin = null
            )
        } throws RuntimeException("Network error")

        val result = repository.getWeather(116.4074, 39.9042)

        assertTrue(result.isFailure)
    }

    @Test
    fun `CAIYUN_TOKEN is not empty`() {
        assertTrue(WeatherRepository.CAIYUN_TOKEN.isNotBlank())
    }

    @Test
    fun `getWeather keeps current request and only merges yesterday daily`() = runTest {
        val currentDaily = DailyForecast(status = "current")
        val yesterdayDaily = DailyForecast(status = "yesterday")
        val mockResponse = WeatherResponse(
            status = "ok",
            result = WeatherResult(daily = currentDaily)
        )
        val dailyResponse = WeatherResponse(
            status = "ok",
            result = WeatherResult(daily = yesterdayDaily)
        )
        coEvery {
            api.getWeather(
                token = any(),
                longitude = 116.4074,
                latitude = 39.9042,
                alert = true,
                dailySteps = 15,
                hourlySteps = 48,
                begin = null
            )
        } returns mockResponse
        coEvery {
            api.getWeather(
                token = any(),
                longitude = 116.4074,
                latitude = 39.9042,
                alert = false,
                dailySteps = 16,
                hourlySteps = 1,
                begin = any()
            )
        } returns dailyResponse

        val result = repository.getWeather(116.4074, 39.9042, includeYesterday = true)

        assertTrue(result.isSuccess)
        assertEquals("yesterday", result.getOrNull()?.result?.daily?.status)
        coVerify(exactly = 1) {
            api.getWeather(
                token = any(),
                longitude = 116.4074,
                latitude = 39.9042,
                alert = true,
                dailySteps = 15,
                hourlySteps = 48,
                begin = null
            )
        }
        coVerify(exactly = 1) {
            api.getWeather(
                token = any(),
                longitude = 116.4074,
                latitude = 39.9042,
                alert = false,
                dailySteps = 16,
                hourlySteps = 1,
                begin = any()
            )
        }
    }
}
