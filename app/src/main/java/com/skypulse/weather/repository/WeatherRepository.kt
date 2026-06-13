package com.skypulse.weather.repository

import com.skypulse.weather.api.CaiyunApi
import com.skypulse.weather.model.WeatherResponse
import java.util.Calendar
import javax.inject.Inject

class WeatherRepository @Inject constructor(
    private val api: CaiyunApi
) {

    companion object {
        const val CAIYUN_TOKEN = "Y2FpeXVuIGFuZHJpb2QgYXBp"
    }

    suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        includeYesterday: Boolean = false
    ): Result<WeatherResponse> {
        return try {
            val response = api.getWeather(
                token = CAIYUN_TOKEN,
                longitude = longitude,
                latitude = latitude
            )
            if (response.status == "ok") {
                if (includeYesterday) {
                    Result.success(response.withYesterdayDaily(longitude, latitude))
                } else {
                    Result.success(response)
                }
            } else {
                Result.failure(Exception("API error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun WeatherResponse.withYesterdayDaily(
        longitude: Double,
        latitude: Double
    ): WeatherResponse {
        val dailyResponse = api.getWeather(
            token = CAIYUN_TOKEN,
            longitude = longitude,
            latitude = latitude,
            alert = false,
            dailySteps = 16,
            hourlySteps = 1,
            begin = yesterdayStartTimestamp()
        )
        val yesterdayDaily = dailyResponse.result?.daily
        if (dailyResponse.status != "ok" || yesterdayDaily == null) return this
        return copy(result = result?.copy(daily = yesterdayDaily))
    }

    private fun yesterdayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis / 1000L
    }
}
