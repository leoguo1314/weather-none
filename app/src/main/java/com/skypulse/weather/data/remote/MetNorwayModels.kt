package com.skypulse.weather.data.remote

import com.skypulse.weather.model.DailyForecast
import com.skypulse.weather.model.DailyPrecipitation
import com.skypulse.weather.model.DailySkycon
import com.skypulse.weather.model.DailyTemperature
import com.skypulse.weather.model.DailyValue
import com.skypulse.weather.model.DailyWind
import com.skypulse.weather.model.HourlyForecast
import com.skypulse.weather.model.HourlySkycon
import com.skypulse.weather.model.HourlyValue
import com.skypulse.weather.model.HourlyWind
import com.skypulse.weather.model.Precipitation
import com.skypulse.weather.model.PrecipitationLocal
import com.skypulse.weather.model.RealtimeWeather
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.model.WeatherResult
import com.skypulse.weather.model.Wind
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class MetNorwayResponse(
    val properties: MetNorwayProperties? = null
)

@JsonClass(generateAdapter = true)
data class MetNorwayProperties(
    val timeseries: List<MetNorwayTimeSeries>? = null
)

@JsonClass(generateAdapter = true)
data class MetNorwayTimeSeries(
    val time: String? = null,
    val data: MetNorwayData? = null
)

@JsonClass(generateAdapter = true)
data class MetNorwayData(
    val instant: MetNorwayInstant? = null,
    @Json(name = "next_1_hours") val next1Hours: MetNorwayPeriod? = null,
    @Json(name = "next_6_hours") val next6Hours: MetNorwayPeriod? = null,
    @Json(name = "next_12_hours") val next12Hours: MetNorwayPeriod? = null
)

@JsonClass(generateAdapter = true)
data class MetNorwayInstant(
    val details: MetNorwayDetails? = null
)

@JsonClass(generateAdapter = true)
data class MetNorwayDetails(
    @Json(name = "air_temperature") val temperature: Double? = null,
    @Json(name = "relative_humidity") val humidity: Double? = null,
    @Json(name = "air_pressure_at_sea_level") val pressure: Double? = null,
    @Json(name = "wind_speed") val windSpeed: Double? = null,
    @Json(name = "wind_from_direction") val windDirection: Double? = null,
    @Json(name = "cloud_area_fraction") val cloudCover: Double? = null,
    @Json(name = "ultraviolet_index_clear_sky") val uvIndex: Double? = null
)

@JsonClass(generateAdapter = true)
data class MetNorwayPeriod(
    val summary: MetNorwaySummary? = null,
    val details: MetNorwayPeriodDetails? = null
)

@JsonClass(generateAdapter = true)
data class MetNorwaySummary(
    @Json(name = "symbol_code") val symbolCode: String? = null
)

@JsonClass(generateAdapter = true)
data class MetNorwayPeriodDetails(
    @Json(name = "precipitation_amount") val precipitationAmount: Double? = null,
    @Json(name = "probability_of_precipitation") val precipitationProbability: Double? = null
)

fun MetNorwayResponse.toWeatherResponse(
    requestedLongitude: Double,
    requestedLatitude: Double
): WeatherResponse {
    val entries = properties?.timeseries.orEmpty().filter { it.time != null && it.data?.instant?.details != null }
    if (entries.isEmpty()) error("MET Norway 返回的预报为空")
    val first = entries.first()
    val firstDetails = requireNotNull(first.data?.instant?.details)
    val firstPeriod = first.preferredPeriod()
    val hourly = HourlyForecast(
        status = "ok",
        description = "MET Norway 逐小时预报",
        precipitation = entries.map { item ->
            HourlyValue(
                datetime = item.time,
                value = item.preferredPeriod()?.details?.precipitationAmount,
                probability = item.preferredPeriod()?.details?.precipitationProbability
            )
        },
        temperature = entries.map { item -> HourlyValue(item.time, item.details()?.temperature) },
        apparent_temperature = entries.map { item -> HourlyValue(item.time, item.details()?.temperature) },
        wind = entries.map { item ->
            HourlyWind(item.time, item.details()?.windSpeed?.times(METERS_PER_SECOND_TO_KM_H), item.details()?.windDirection)
        },
        humidity = entries.map { item -> HourlyValue(item.time, item.details()?.humidity?.div(100.0)) },
        cloudrate = entries.map { item -> HourlyValue(item.time, item.details()?.cloudCover?.div(100.0)) },
        skycon = entries.map { item ->
            HourlySkycon(item.time, metNorwaySymbolToSkycon(item.preferredPeriod()?.summary?.symbolCode))
        },
        pressure = entries.map { item -> HourlyValue(item.time, item.details()?.pressure?.times(100.0)) }
    )

    val dailyGroups = entries.groupBy { it.time.orEmpty().take(10) }
        .filterKeys { it.length == 10 }
        .entries
        .take(9)
    val daily = DailyForecast(
        status = "ok",
        precipitation = dailyGroups.map { (date, values) ->
            val amounts = values.mapNotNull { it.preferredPeriod()?.details?.precipitationAmount }
            val probabilities = values.mapNotNull { it.preferredPeriod()?.details?.precipitationProbability }
            val total = amounts.takeIf { it.isNotEmpty() }?.sum()
            DailyPrecipitation(
                date = date,
                max = amounts.maxOrNull(),
                min = amounts.minOrNull(),
                avg = total,
                probability = probabilities.maxOrNull()
            )
        },
        temperature = dailyGroups.map { (date, values) ->
            val temperatures = values.mapNotNull { it.details()?.temperature }
            DailyTemperature(
                date = date,
                max = temperatures.maxOrNull(),
                min = temperatures.minOrNull(),
                avg = temperatures.averageOrNull()
            )
        },
        wind = dailyGroups.map { (date, values) ->
            val winds = values.mapNotNull { item ->
                val detail = item.details() ?: return@mapNotNull null
                Wind(detail.windSpeed?.times(METERS_PER_SECOND_TO_KM_H), detail.windDirection)
            }
            val maxWind = winds.maxByOrNull { it.speed ?: Double.NEGATIVE_INFINITY }
            DailyWind(date = date, max = maxWind, min = winds.minByOrNull { it.speed ?: Double.MAX_VALUE }, avg = maxWind)
        },
        humidity = dailyGroups.map { (date, values) ->
            val data = values.mapNotNull { it.details()?.humidity?.div(100.0) }
            DailyValue(date, data.maxOrNull(), data.minOrNull(), data.averageOrNull())
        },
        cloudrate = dailyGroups.map { (date, values) ->
            val data = values.mapNotNull { it.details()?.cloudCover?.div(100.0) }
            DailyValue(date, data.maxOrNull(), data.minOrNull(), data.averageOrNull())
        },
        pressure = dailyGroups.map { (date, values) ->
            val data = values.mapNotNull { it.details()?.pressure?.times(100.0) }
            DailyValue(date, data.maxOrNull(), data.minOrNull(), data.averageOrNull())
        },
        skycon = dailyGroups.map { (date, values) ->
            val representative = values.minByOrNull { item ->
                val hour = item.time.orEmpty()
                    .substringAfter('T', "12")
                    .take(2)
                    .toIntOrNull() ?: 12
                kotlin.math.abs(hour - 12)
            }
            DailySkycon(date, metNorwaySymbolToSkycon(representative?.preferredPeriod()?.summary?.symbolCode))
        }
    )

    val currentSkycon = metNorwaySymbolToSkycon(firstPeriod?.summary?.symbolCode)
    return WeatherResponse(
        status = "ok",
        api_version = "met-no-locationforecast-2.0",
        api_status = "active",
        lang = "zh_CN",
        unit = "metric",
        tzshift = 0,
        timezone = "UTC",
        server_time = first.time?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }
            ?: Instant.now().epochSecond,
        location = listOf(requestedLongitude, requestedLatitude),
        result = WeatherResult(
            realtime = RealtimeWeather(
                status = "ok",
                temperature = firstDetails.temperature,
                humidity = firstDetails.humidity?.div(100.0),
                cloudrate = firstDetails.cloudCover?.div(100.0),
                skycon = currentSkycon,
                wind = Wind(
                    speed = firstDetails.windSpeed?.times(METERS_PER_SECOND_TO_KM_H),
                    direction = firstDetails.windDirection
                ),
                pressure = firstDetails.pressure?.times(100.0),
                apparent_temperature = firstDetails.temperature,
                precipitation = Precipitation(
                    local = PrecipitationLocal(
                        status = "ok",
                        datasource = "MET Norway",
                        intensity = firstPeriod?.details?.precipitationAmount
                    )
                )
            ),
            hourly = hourly,
            daily = daily,
            forecastKeypoint = "MET Norway：未来 ${dailyGroups.size} 日天气已更新"
        )
    )
}

fun metNorwaySymbolToSkycon(symbol: String?): String {
    val value = symbol.orEmpty().lowercase()
    val isNight = value.endsWith("_night") || value.endsWith("_polartwilight")
    return when {
        "thunder" in value -> "THUNDER_SHOWER"
        "sleet" in value -> "SLEET"
        "heavysnow" in value -> "HEAVY_SNOW"
        "snow" in value -> "LIGHT_SNOW"
        "heavyrain" in value -> "HEAVY_RAIN"
        "rain" in value || "drizzle" in value -> "LIGHT_RAIN"
        "fog" in value -> "FOG"
        "cloudy" == value.substringBefore('_') -> "CLOUDY"
        "partlycloudy" in value || "fair" in value -> if (isNight) "PARTLY_CLOUDY_NIGHT" else "PARTLY_CLOUDY_DAY"
        "clearsky" in value -> if (isNight) "CLEAR_NIGHT" else "CLEAR_DAY"
        else -> "CLOUDY"
    }
}

private fun MetNorwayTimeSeries.details(): MetNorwayDetails? = data?.instant?.details

private fun MetNorwayTimeSeries.preferredPeriod(): MetNorwayPeriod? =
    data?.next1Hours ?: data?.next6Hours ?: data?.next12Hours

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

private const val METERS_PER_SECOND_TO_KM_H = 3.6
