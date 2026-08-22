package com.skypulse.weather.agent

import com.skypulse.weather.model.WeatherResponse
import kotlin.math.roundToInt

data class AgentForecastDay(
    val date: String,
    val skycon: String,
    val high: Double?,
    val low: Double?,
    val precipitationProbability: Double?
)

data class AgentSnapshot(
    val city: String,
    val skycon: String,
    val temperature: Double?,
    val apparentTemperature: Double?,
    val humidity: Double?,
    val windSpeed: Double?,
    val aqi: Double?,
    val forecastKeypoint: String?,
    val alertTitles: List<String>,
    val daily: List<AgentForecastDay>,
    val updatedAtMillis: Long
) {
    val skyDescription: String get() = skyconDescription(skycon)
}

fun WeatherResponse.toAgentSnapshot(
    city: String,
    updatedAtMillis: Long = System.currentTimeMillis()
): AgentSnapshot {
    val result = result
    val realtime = result?.realtime
    val dailyWeather = result?.daily
    val temperatures = dailyWeather?.temperature.orEmpty()
    val skycons = dailyWeather?.skycon.orEmpty()
    val precipitation = dailyWeather?.precipitation.orEmpty()
    val days = skycons.mapIndexed { index, sky ->
        val temperature = temperatures.getOrNull(index)
        val rain = precipitation.getOrNull(index)
        AgentForecastDay(
            date = sky.date.orEmpty(),
            skycon = sky.value.orEmpty(),
            high = temperature?.max,
            low = temperature?.min,
            precipitationProbability = rain?.probability
        )
    }

    return AgentSnapshot(
        city = city,
        skycon = realtime?.skycon.orEmpty(),
        temperature = realtime?.temperature,
        apparentTemperature = realtime?.apparent_temperature,
        humidity = realtime?.humidity?.times(100.0),
        windSpeed = realtime?.wind?.speed,
        aqi = realtime?.air_quality?.aqi?.chn,
        forecastKeypoint = result?.forecastKeypoint,
        alertTitles = result?.alert?.content.orEmpty().mapNotNull { it.title }.take(3),
        daily = days.take(7),
        updatedAtMillis = updatedAtMillis
    )
}

fun skyconDescription(value: String): String = when (value.uppercase()) {
    "CLEAR_DAY" -> "晴"
    "CLEAR_NIGHT" -> "晴夜"
    "PARTLY_CLOUDY_DAY", "PARTLY_CLOUDY_NIGHT" -> "多云"
    "CLOUDY" -> "阴"
    "LIGHT_HAZE", "MODERATE_HAZE", "HEAVY_HAZE" -> "雾霾"
    "LIGHT_RAIN" -> "小雨"
    "MODERATE_RAIN" -> "中雨"
    "HEAVY_RAIN", "STORM_RAIN" -> "大雨"
    "FOG" -> "雾"
    "LIGHT_SNOW" -> "小雪"
    "MODERATE_SNOW" -> "中雪"
    "HEAVY_SNOW", "STORM_SNOW" -> "大雪"
    "DUST", "SAND" -> "沙尘"
    "WIND" -> "大风"
    else -> value.ifBlank { "未知" }
}

internal fun Double?.display(unit: String = ""): String =
    this?.roundToInt()?.let { "$it$unit" } ?: "--"
