package com.skypulse.weather.data.remote

import com.skypulse.weather.data.WeatherSource
import com.skypulse.weather.data.WeatherSourceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherSourceMappingTest {

    @Test
    fun `there are exactly three built in sources plus custom`() {
        assertEquals(3, WeatherSource.entries.count { it != WeatherSource.CUSTOM })
    }

    @Test
    fun `custom source requires https and coordinate placeholders`() {
        assertTrue(
            WeatherSourceConfig(
                source = WeatherSource.CUSTOM,
                customUrlTemplate = "http://example.com/weather?lat={lat}&lon={lon}"
            ).validationError()?.contains("HTTPS") == true
        )
        assertTrue(
            WeatherSourceConfig(
                source = WeatherSource.CUSTOM,
                customUrlTemplate = "https://example.com/weather"
            ).validationError()?.contains("{lat}") == true
        )
        assertNull(
            WeatherSourceConfig(
                source = WeatherSource.CUSTOM,
                customUrlTemplate = "https://example.com/weather?lat={lat}&lon={lon}"
            ).validationError()
        )
    }

    @Test
    fun `open meteo maps units and forecast into shared model`() {
        val response = OpenMeteoResponse(
            longitude = 116.4,
            latitude = 39.9,
            timezone = "Asia/Shanghai",
            utcOffsetSeconds = 8 * 3600,
            current = OpenMeteoCurrent(
                time = "2026-09-05T08:00",
                temperature = 26.5,
                humidity = 68.0,
                apparentTemperature = 27.2,
                precipitation = 0.2,
                weatherCode = 2,
                pressure = 1008.0,
                windSpeed = 12.0,
                windDirection = 90.0,
                isDay = 1
            ),
            hourly = OpenMeteoHourly(
                time = listOf("2026-09-05T07:00", "2026-09-05T08:00"),
                temperature = listOf(25.0, 26.5),
                humidity = listOf(70.0, 68.0),
                apparentTemperature = listOf(25.8, 27.2),
                precipitation = listOf(0.0, 0.2),
                precipitationProbability = listOf(10.0, 30.0),
                weatherCode = listOf(1, 2),
                pressure = listOf(1009.0, 1008.0),
                cloudCover = listOf(20.0, 42.0),
                visibility = listOf(15000.0, 12000.0),
                windSpeed = listOf(8.0, 12.0),
                windDirection = listOf(80.0, 90.0),
                windGusts = listOf(12.0, 18.0),
                uvIndex = listOf(1.0, 7.2)
            ),
            daily = OpenMeteoDaily(
                time = listOf("2026-09-05"),
                weatherCode = listOf(2),
                temperatureMax = listOf(30.0),
                temperatureMin = listOf(21.0),
                precipitationSum = listOf(1.2),
                precipitationProbability = listOf(45.0),
                sunrise = listOf("2026-09-05T05:48"),
                sunset = listOf("2026-09-05T18:39"),
                windSpeedMax = listOf(20.0),
                windDirection = listOf(100.0),
                uvIndexMax = listOf(6.0)
            )
        ).toWeatherResponse(116.4, 39.9)

        assertEquals("ok", response.status)
        assertEquals(0.68, response.result?.realtime?.humidity ?: 0.0, 0.0001)
        assertEquals(100800.0, response.result?.realtime?.pressure ?: 0.0, 0.0001)
        assertEquals("PARTLY_CLOUDY_DAY", response.result?.realtime?.skycon)
        assertEquals("2026-09-05T07:00+08:00", response.result?.hourly?.temperature?.first()?.datetime)
        assertEquals("7", response.result?.realtime?.life_index?.ultraviolet?.index)
        assertEquals(45.0, response.result?.daily?.precipitation?.first()?.probability ?: 0.0, 0.0001)
    }

    @Test
    fun `met norway maps metres per second and symbols`() {
        val response = MetNorwayResponse(
            properties = MetNorwayProperties(
                timeseries = listOf(
                    MetNorwayTimeSeries(
                        time = "2026-09-05T00:00:00Z",
                        data = MetNorwayData(
                            instant = MetNorwayInstant(
                                MetNorwayDetails(
                                    temperature = 20.0,
                                    humidity = 75.0,
                                    pressure = 1012.0,
                                    windSpeed = 5.0,
                                    windDirection = 180.0,
                                    cloudCover = 80.0
                                )
                            ),
                            next1Hours = MetNorwayPeriod(
                                summary = MetNorwaySummary("rainshowers_day"),
                                details = MetNorwayPeriodDetails(1.5, 60.0)
                            )
                        )
                    )
                )
            )
        ).toWeatherResponse(116.4, 39.9)

        assertEquals(18.0, response.result?.realtime?.wind?.speed ?: 0.0, 0.0001)
        assertEquals("LIGHT_RAIN", response.result?.realtime?.skycon)
        assertEquals(0.75, response.result?.realtime?.humidity ?: 0.0, 0.0001)
        assertEquals(1, response.result?.daily?.temperature?.size)
    }
}
