package com.skypulse.weather.data.remote

import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.WeatherSource
import com.skypulse.weather.data.WeatherSourceConfig
import com.skypulse.weather.data.WeatherSourceConfigStore
import com.skypulse.weather.model.WeatherResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherSourceProbe(
    val sourceName: String,
    val temperature: Double?,
    val forecastDays: Int
)

@Singleton
class WeatherSourceClient @Inject constructor(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val configStore: WeatherSourceConfigStore
) {
    suspend fun getWeather(
        longitude: Double,
        latitude: Double,
        includeYesterday: Boolean,
        config: WeatherSourceConfig = configStore.config.value
    ): WeatherResponse = withContext(Dispatchers.IO) {
        when (config.source) {
            WeatherSource.OPEN_METEO -> fetchOpenMeteo(
                url = buildOpenMeteoUrl(longitude, latitude, includeYesterday),
                longitude = longitude,
                latitude = latitude,
                sourceName = config.source.displayName
            )
            WeatherSource.MET_NORWAY -> fetchMetNorway(longitude, latitude)
            WeatherSource.CAIYUN -> fetchCaiyun(config, longitude, latitude, includeYesterday)
            WeatherSource.CUSTOM -> fetchCustom(config, longitude, latitude, includeYesterday)
        }
    }

    suspend fun test(
        config: WeatherSourceConfig,
        longitude: Double = 116.4074,
        latitude: Double = 39.9042
    ): WeatherSourceProbe {
        val response = getWeather(longitude, latitude, includeYesterday = false, config = config)
        return WeatherSourceProbe(
            sourceName = config.displayName,
            temperature = response.result?.realtime?.temperature,
            forecastDays = response.result?.daily?.temperature.orEmpty().size
        )
    }

    private fun fetchOpenMeteo(
        url: String,
        longitude: Double,
        latitude: Double,
        sourceName: String,
        headerName: String = "",
        headerValue: String = ""
    ): WeatherResponse {
        val raw = execute(url, headerName, headerValue)
        val response = moshi.adapter(OpenMeteoResponse::class.java).fromJson(raw)
            ?: error("天气源响应为空")
        return response.toWeatherResponse(longitude, latitude, sourceName)
    }

    private fun fetchMetNorway(longitude: Double, latitude: Double): WeatherResponse {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
            "?lat=${latitude.coordinate()}&lon=${longitude.coordinate()}"
        val raw = execute(url)
        val response = moshi.adapter(MetNorwayResponse::class.java).fromJson(raw)
            ?: error("MET Norway 响应为空")
        return response.toWeatherResponse(longitude, latitude)
    }

    private fun fetchCaiyun(
        config: WeatherSourceConfig,
        longitude: Double,
        latitude: Double,
        includeYesterday: Boolean
    ): WeatherResponse {
        val token = config.caiyunToken.ifBlank { BuildConfig.CAIYUN_TOKEN }
        require(token.isNotBlank()) { "彩云天气需要配置 Token" }
        val baseUrl = BuildConfig.WEATHER_BASE_URL.trimEnd('/')
        require(baseUrl.startsWith("https://")) { "彩云天气地址必须使用 HTTPS" }
        val url = "$baseUrl/v2.7/${token.urlEncode()}/${longitude.coordinate()},${latitude.coordinate()}/weather" +
            "?span=16&alert=false" +
            (if (includeYesterday) "&dailystart=-1&hourlysteps=72" else "&hourlysteps=24") +
            "&lang=zh_CN&version=7.59.0"
        val raw = execute(url)
        val response = moshi.adapter(WeatherResponse::class.java).fromJson(raw)
            ?: error("彩云天气响应为空")
        if (response.status != "ok") error("彩云天气返回 ${response.status}")
        return response
    }

    private fun fetchCustom(
        config: WeatherSourceConfig,
        longitude: Double,
        latitude: Double,
        includeYesterday: Boolean
    ): WeatherResponse {
        config.validationError()?.let { error(it) }
        val secret = config.customHeaderValue
        val url = config.customUrlTemplate
            .replace("{lat}", latitude.coordinate())
            .replace("{lon}", longitude.coordinate())
            .replace("{days}", if (includeYesterday) "16" else "7")
            .replace("{key}", secret.urlEncode())
        return fetchOpenMeteo(
            url = url,
            longitude = longitude,
            latitude = latitude,
            sourceName = config.displayName,
            headerName = config.customHeaderName,
            headerValue = secret
        )
    }

    private fun buildOpenMeteoUrl(
        longitude: Double,
        latitude: Double,
        includeYesterday: Boolean
    ): String {
        val template = WeatherSourceConfig.DEFAULT_CUSTOM_URL_TEMPLATE
        return template
            .replace("{lat}", latitude.coordinate())
            .replace("{lon}", longitude.coordinate())
            .replace("{days}", "16") +
            if (includeYesterday) "&past_hours=24" else ""
    }

    private fun execute(url: String, headerName: String = "", headerValue: String = ""): String {
        require(url.startsWith("https://")) { "天气源地址必须使用 HTTPS" }
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        if (headerName.isNotBlank() && headerValue.isNotBlank()) {
            requestBuilder.header(headerName, headerValue)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("天气服务返回 HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("天气服务响应为空")
            if (body.contentLength() > MAX_RESPONSE_BYTES) {
                throw IOException("天气服务响应过大")
            }
            return body.string().also {
                if (it.toByteArray().size.toLong() > MAX_RESPONSE_BYTES) throw IOException("天气服务响应过大")
            }
        }
    }

    private fun Double.coordinate(): String = String.format(java.util.Locale.US, "%.6f", this)

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private companion object {
        const val MAX_RESPONSE_BYTES = 6L * 1024L * 1024L
    }
}
