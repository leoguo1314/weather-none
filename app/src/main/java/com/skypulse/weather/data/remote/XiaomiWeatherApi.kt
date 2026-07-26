package com.skypulse.weather.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 小米天气实况接口。
 *
 * 用于校准彩云天气的"阴天"和"多云"偏差：
 * - 当彩云返回 CLOUDY 时，调用此接口获取中国气象局的实况天气编码。
 * - 当彩云返回 PARTLY_CLOUDY_DAY 或 PARTLY_CLOUDY_NIGHT 时，同样进行校准。
 * 若气象局判定为"晴"或不同天气，则覆盖彩云的 skycon。
 *
 * 数据源：中国气象局（weatherbj），与彩云独立。
 */
interface XiaomiWeatherApi {

    /**
     * 地理编码反查：根据经纬度获取 locationKey。
     * 用于后续天气请求的前置参数。
     */
    @GET("wtr-v3/location/city/geo")
    suspend fun getLocationKey(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("appKey") appKey: String,
        @Query("sign") sign: String,
        @Query("romVersion") romVersion: String = "eng.localh.20231105.141708",
        @Query("appVersion") appVersion: String = "17000318",
        @Query("alpha") alpha: Boolean = false,
        @Query("isGlobal") isGlobal: Boolean = false,
        @Query("device") device: String = "dandelion",
        @Query("modDevice") modDevice: String = "dandelion",
        @Query("locale") locale: String = "zh_cn",
        @Query("oaid") oaid: String = ""
    ): List<XiaomiGeoResult>

    /**
     * 获取实况天气数据。
     * locationKey 通过前置 geo 接口获取。
     */
    @GET("wtr-v3/weather/all")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("isLocated") isLocated: Boolean = false,
        @Query("locationKey") locationKey: String,
        @Query("days") days: Int = 15,
        @Query("appKey") appKey: String,
        @Query("sign") sign: String,
        @Query("romVersion") romVersion: String = "eng.localh.20231105.141708",
        @Query("appVersion") appVersion: String = "17000318",
        @Query("alpha") alpha: Boolean = false,
        @Query("isGlobal") isGlobal: Boolean = false,
        @Query("device") device: String = "dandelion",
        @Query("modDevice") modDevice: String = "dandelion",
        @Query("locale") locale: String = "zh_cn",
        @Query("oaid") oaid: String = ""
    ): XiaomiWeatherResponse
}

/**
 * 小米地理编码响应体。
 */
@JsonClass(generateAdapter = true)
data class XiaomiGeoResult(
    val name: String? = null,
    val key: String? = null,
    @Json(name = "locationKey") val locationKey: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    @Json(name = "affiliation") val affiliation: String? = null,
    val status: Int? = null,
    val timeZoneShift: Int? = null
)

/**
 * 小米天气响应体 — 仅解析 current.weather 字段。
 *
 * current.weather 为中国气象局标准编码（字符串格式的数字）：
 * - "0" = 晴
 * - "1" = 多云
 * - "2" = 阴
 * - "3" = 阵雨
 * - "4" = 雷阵雨
 * - "7" = 小雨
 * - "8" = 中雨
 * - 等等
 */
@JsonClass(generateAdapter = true)
data class XiaomiWeatherResponse(
    val current: XiaomiCurrentWeather? = null
)

@JsonClass(generateAdapter = true)
data class XiaomiCurrentWeather(
    @Json(name = "weather") val weatherCode: String? = null
)

