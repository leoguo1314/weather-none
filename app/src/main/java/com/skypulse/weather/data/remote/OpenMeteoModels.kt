package com.skypulse.weather.data.remote

import com.skypulse.weather.model.AstroTime
import com.skypulse.weather.model.DailyAstro
import com.skypulse.weather.model.DailyForecast
import com.skypulse.weather.model.DailyLifeIndex
import com.skypulse.weather.model.DailyPrecipitation
import com.skypulse.weather.model.DailySkycon
import com.skypulse.weather.model.DailyTemperature
import com.skypulse.weather.model.DailyWind
import com.skypulse.weather.model.HourlyForecast
import com.skypulse.weather.model.HourlyLifeIndex
import com.skypulse.weather.model.HourlySkycon
import com.skypulse.weather.model.HourlyUvItem
import com.skypulse.weather.model.HourlyValue
import com.skypulse.weather.model.HourlyWind
import com.skypulse.weather.model.LifeIndex
import com.skypulse.weather.model.LifeIndexDay
import com.skypulse.weather.model.LifeIndexItem
import com.skypulse.weather.model.Precipitation
import com.skypulse.weather.model.PrecipitationLocal
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.model.WeatherResult
import com.skypulse.weather.model.Wind
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    val current: OpenMeteoCurrent? = null,
    val hourly: OpenMeteoHourly? = null,
    val daily: OpenMeteoDaily? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoCurrent(
    val time: String? = null,
    @Json(name = "temperature_2m") val temperature: Double? = null,
    @Json(name = "relative_humidity_2m") val humidity: Double? = null,
    @Json(name = "apparent_temperature") val apparentTemperature: Double? = null,
    val precipitation: Double? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "pressure_msl") val pressure: Double? = null,
    @Json(name = "wind_speed_10m") val windSpeed: Double? = null,
    @Json(name = "wind_direction_10m") val windDirection: Double? = null,
    @Json(name = "is_day") val isDay: Int? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoHourly(
    val time: List<String>? = null,
    @Json(name = "temperature_2m") val temperature: List<Double?>? = null,
    @Json(name = "relative_humidity_2m") val humidity: List<Double?>? = null,
    @Json(name = "apparent_temperature") val apparentTemperature: List<Double?>? = null,
    val precipitation: List<Double?>? = null,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Double?>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int?>? = null,
    @Json(name = "pressure_msl") val pressure: List<Double?>? = null,
    @Json(name = "cloud_cover") val cloudCover: List<Double?>? = null,
    val visibility: List<Double?>? = null,
    @Json(name = "wind_speed_10m") val windSpeed: List<Double?>? = null,
    @Json(name = "wind_direction_10m") val windDirection: List<Double?>? = null,
    @Json(name = "wind_gusts_10m") val windGusts: List<Double?>? = null,
    @Json(name = "uv_index") val uvIndex: List<Double?>? = null
)

@JsonClass(generateAdapter = true)
data class OpenMeteoDaily(
    val time: List<String>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int?>? = null,
    @Json(name = "temperature_2m_max") val temperatureMax: List<Double?>? = null,
    @Json(name = "temperature_2m_min") val temperatureMin: List<Double?>? = null,
    @Json(name = "precipitation_sum") val precipitationSum: List<Double?>? = null,
    @Json(name = "precipitation_probability_max") val precipitationProbability: List<Double?>? = null,
    val sunrise: List<String>? = null,
    val sunset: List<String>? = null,
    @Json(name = "wind_speed_10m_max") val windSpeedMax: List<Double?>? = null,
    @Json(name = "wind_direction_10m_dominant") val windDirection: List<Double?>? = null,
    @Json(name = "uv_index_max") val uvIndexMax: List<Double?>? = null
)

fun OpenMeteoResponse.toWeatherResponse(
    requestedLongitude: Double,
    requestedLatitude: Double,
    sourceName: String = "Open-Meteo"
): WeatherResponse {
    val currentData = current ?: error("天气源缺少 current 数据")
    val hourlyData = hourly ?: error("天气源缺少 hourly 数据")
    val dailyData = daily ?: error("天气源缺少 daily 数据")
    val hourlyTimes = hourlyData.time.orEmpty()
    if (hourlyTimes.isEmpty() || dailyData.time.isNullOrEmpty()) {
        error("天气源返回的预报为空")
    }
    val offsetSeconds = utcOffsetSeconds ?: 0
    val currentHourlyIndex = currentData.time
        ?.let(hourlyTimes::indexOf)
        ?.takeIf { it >= 0 }
        ?: 0
    val currentIsDay = currentData.isDay?.let { it == 1 }
        ?: currentData.time?.let(::hourFromIso)?.let { it in 6..18 }
        ?: true
    val currentUv = hourlyData.uvIndex.valueAt(currentHourlyIndex)
    val currentVisibility = hourlyData.visibility.valueAt(currentHourlyIndex)
    val currentCloudCover = hourlyData.cloudCover.valueAt(currentHourlyIndex)
    val currentPrecipitation = currentData.precipitation ?: hourlyData.precipitation.valueAt(currentHourlyIndex)

    val hourlyForecast = HourlyForecast(
        status = "ok",
        description = "$sourceName 逐小时预报",
        precipitation = hourlyTimes.mapIndexed { index, time ->
            HourlyValue(
                datetime = normalizeIsoOffset(time, offsetSeconds),
                value = hourlyData.precipitation.valueAt(index),
                probability = hourlyData.precipitationProbability.valueAt(index)
            )
        },
        temperature = hourlyTimes.mapIndexed { index, time ->
            HourlyValue(normalizeIsoOffset(time, offsetSeconds), hourlyData.temperature.valueAt(index))
        },
        apparent_temperature = hourlyTimes.mapIndexed { index, time ->
            HourlyValue(normalizeIsoOffset(time, offsetSeconds), hourlyData.apparentTemperature.valueAt(index))
        },
        wind = hourlyTimes.mapIndexed { index, time ->
            HourlyWind(
                datetime = normalizeIsoOffset(time, offsetSeconds),
                speed = hourlyData.windSpeed.valueAt(index),
                direction = hourlyData.windDirection.valueAt(index)
            )
        },
        gust = hourlyTimes.mapIndexed { index, time ->
            HourlyValue(normalizeIsoOffset(time, offsetSeconds), hourlyData.windGusts.valueAt(index))
        },
        humidity = hourlyTimes.mapIndexed { index, time ->
            HourlyValue(normalizeIsoOffset(time, offsetSeconds), hourlyData.humidity.valueAt(index)?.div(100.0))
        },
        cloudrate = hourlyTimes.mapIndexed { index, time ->
            HourlyValue(normalizeIsoOffset(time, offsetSeconds), hourlyData.cloudCover.valueAt(index)?.div(100.0))
        },
        skycon = hourlyTimes.mapIndexed { index, time ->
            val hour = hourFromIso(time)
            HourlySkycon(
                datetime = normalizeIsoOffset(time, offsetSeconds),
                value = openMeteoCodeToSkycon(hourlyData.weatherCode.valueAt(index), hour in 6..18)
            )
        },
        pressure = hourlyTimes.mapIndexed { index, time ->
            HourlyValue(normalizeIsoOffset(time, offsetSeconds), hourlyData.pressure.valueAt(index)?.times(100.0))
        },
        visibility = hourlyTimes.mapIndexed { index, time ->
            HourlyValue(normalizeIsoOffset(time, offsetSeconds), hourlyData.visibility.valueAt(index))
        },
        life_index = HourlyLifeIndex(
            ultraviolet = hourlyTimes.mapIndexed { index, time ->
                val value = hourlyData.uvIndex.valueAt(index)
                HourlyUvItem(
                    datetime = normalizeIsoOffset(time, offsetSeconds),
                    index = value?.roundToInt()?.toString(),
                    desc = uvDescription(value)
                )
            }
        )
    )

    val dailyTimes = dailyData.time.orEmpty().take(16)
    val dailyForecast = DailyForecast(
        status = "ok",
        astro = dailyTimes.mapIndexed { index, date ->
            DailyAstro(
                date = date,
                sunrise = AstroTime(time = dailyData.sunrise.valueAt(index)?.timePart()),
                sunset = AstroTime(time = dailyData.sunset.valueAt(index)?.timePart())
            )
        },
        precipitation = dailyTimes.mapIndexed { index, date ->
            val amount = dailyData.precipitationSum.valueAt(index)
            DailyPrecipitation(
                date = date,
                max = amount,
                min = 0.0,
                avg = amount,
                probability = dailyData.precipitationProbability.valueAt(index)
            )
        },
        temperature = dailyTimes.mapIndexed { index, date ->
            val high = dailyData.temperatureMax.valueAt(index)
            val low = dailyData.temperatureMin.valueAt(index)
            DailyTemperature(date = date, max = high, min = low, avg = averageOf(low, high))
        },
        wind = dailyTimes.mapIndexed { index, date ->
            val wind = Wind(
                speed = dailyData.windSpeedMax.valueAt(index),
                direction = dailyData.windDirection.valueAt(index)
            )
            DailyWind(date = date, max = wind, min = wind, avg = wind)
        },
        skycon = dailyTimes.mapIndexed { index, date ->
            DailySkycon(date, openMeteoCodeToSkycon(dailyData.weatherCode.valueAt(index), true))
        },
        life_index = DailyLifeIndex(
            ultraviolet = dailyTimes.mapIndexed { index, date ->
                val value = dailyData.uvIndexMax.valueAt(index)
                LifeIndexDay(date, value?.roundToInt()?.toString(), uvDescription(value))
            }
        )
    )

    val skycon = openMeteoCodeToSkycon(currentData.weatherCode, currentIsDay)
    return WeatherResponse(
        status = "ok",
        api_version = "open-meteo-compatible",
        api_status = "active",
        lang = "zh_CN",
        unit = "metric",
        tzshift = offsetSeconds,
        timezone = timezone,
        server_time = parseEpochSeconds(currentData.time, offsetSeconds) ?: Instant.now().epochSecond,
        location = listOf(longitude ?: requestedLongitude, latitude ?: requestedLatitude),
        result = WeatherResult(
            realtime = RealtimeWeather(
                status = "ok",
                temperature = currentData.temperature,
                humidity = currentData.humidity?.div(100.0),
                cloudrate = currentCloudCover?.div(100.0),
                skycon = skycon,
                visibility = currentVisibility,
                wind = Wind(currentData.windSpeed, currentData.windDirection),
                pressure = currentData.pressure?.times(100.0),
                apparent_temperature = currentData.apparentTemperature,
                precipitation = Precipitation(
                    local = PrecipitationLocal("ok", sourceName, currentPrecipitation)
                ),
                life_index = LifeIndex(
                    ultraviolet = LifeIndexItem(
                        index = currentUv?.roundToInt()?.toString(),
                        desc = uvDescription(currentUv)
                    )
                )
            ),
            hourly = hourlyForecast,
            daily = dailyForecast,
            forecastKeypoint = "$sourceName：未来 ${dailyTimes.size} 日天气已更新"
        )
    )
}

fun openMeteoCodeToSkycon(code: Int?, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "CLEAR_DAY" else "CLEAR_NIGHT"
    1, 2 -> if (isDay) "PARTLY_CLOUDY_DAY" else "PARTLY_CLOUDY_NIGHT"
    3 -> "CLOUDY"
    45, 48 -> "FOG"
    51, 53 -> "LIGHT_RAIN"
    55 -> "MODERATE_RAIN"
    56, 57, 66, 67 -> "SLEET"
    61, 80 -> "LIGHT_RAIN"
    63, 81 -> "MODERATE_RAIN"
    65, 82 -> "HEAVY_RAIN"
    71, 77, 85 -> "LIGHT_SNOW"
    73 -> "MODERATE_SNOW"
    75, 86 -> "HEAVY_SNOW"
    95, 96, 99 -> "THUNDER_SHOWER"
    else -> "CLOUDY"
}

private fun <T> List<T?>?.valueAt(index: Int): T? = this?.getOrNull(index)

private fun averageOf(first: Double?, second: Double?): Double? = when {
    first != null && second != null -> (first + second) / 2.0
    else -> first ?: second
}

private fun String.timePart(): String = substringAfter('T', this).take(5)

private fun hourFromIso(value: String): Int = value.substringAfter('T', "12").take(2).toIntOrNull() ?: 12

private fun normalizeIsoOffset(value: String, offsetSeconds: Int): String {
    if (value.endsWith("Z") || OFFSET_SUFFIX.containsMatchIn(value)) return value
    return value + ZoneOffset.ofTotalSeconds(offsetSeconds).id
}

private fun parseEpochSeconds(value: String?, offsetSeconds: Int): Long? {
    if (value == null) return null
    return runCatching {
        if (value.endsWith("Z") || OFFSET_SUFFIX.containsMatchIn(value)) {
            OffsetDateTime.parse(value).toEpochSecond()
        } else {
            LocalDateTime.parse(value).toEpochSecond(ZoneOffset.ofTotalSeconds(offsetSeconds))
        }
    }.getOrNull()
}

private fun uvDescription(value: Double?): String = when {
    value == null -> "暂无"
    value < 3 -> "弱"
    value < 6 -> "中等"
    value < 8 -> "较强"
    value < 11 -> "很强"
    else -> "极强"
}

private val OFFSET_SUFFIX = Regex("[+-]\\d{2}:\\d{2}$")
