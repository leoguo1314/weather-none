package com.skypulse.weather.util

import androidx.compose.ui.graphics.Color
import com.skypulse.weather.model.DailyAstro
import com.skypulse.weather.model.DailyForecast
import com.skypulse.weather.model.DailyTemperature
import com.skypulse.weather.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 天空渐变锚点，与 [getWeatherGradient] 返回的 5 色一一对应。
 *
 * 非等距：顶部天顶色（最深的 color[0]）占据上约 40%，压住「天顶」层次，
 * 之后向地平线（最亮的 color[4]）逐步加速提亮——更接近真实天空由深到浅的过渡，
 * 而非把 5 色机械均分在 0/0.25/0.5/0.75/1.0。
 */
val SkyGradientStops: List<Float> = listOf(0.0f, 0.40f, 0.65f, 0.85f, 1.0f)

/**
 * 将天空渐变色与 [SkyGradientStops] 锚点组合为 Brush.verticalGradient(vararg) 所需的颜色停靠对。
 * 调用方用展开运算符传入：Brush.verticalGradient(colorStops = *skyGradientColorStops(colors), ...)。
 */
fun skyGradientColorStops(colors: List<Color>): Array<Pair<Float, Color>> =
    colors.zip(SkyGradientStops) { color, stop -> stop to color }.toTypedArray()

/**
 * 昼夜相位：用于晴天/多云场景在日出、日落前后约 1 小时切换清晨/傍晚主题。
 * - MORNING：日出时刻 ± 1 小时
 * - EVENING：日落时刻 ± 1 小时
 * - DAY / NIGHT：其余时段（由 [WeatherUtils.isCurrentlyDay] 判定）
 */
enum class DayPhase { MORNING, DAY, EVENING, NIGHT }

object WeatherUtils {

    private val hourFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
    }
    private val dateFormat: ThreadLocal<SimpleDateFormat> = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }

    data class WeatherInfo(
        val description: String,
        val icon: String,
        val isDay: Boolean = true
    )

    fun getWeatherTheme(skycon: String?, isDay: Boolean, phase: DayPhase? = null): WeatherTheme {
        val background = getWeatherGradient(skycon, isDay, phase)
        val precipitationIconColor = getPrecipitationIconColor(skycon, isDay)

        // --- 玻璃卡片配色：天气色相派生 ---
        // 取天气渐变中段色作为「天气色相」，先做饱和度拉伸去灰（阴天不再灰蒙蒙），
        // 白天非雨雪向白色提亮 55%（清透有蓝天感），雨雪与夜晚向黑色压暗 55%。
        // 卡片因此保留天气色彩个性，且无需逐天气手工调色；文字恒白可读。
        val hue = background[background.size / 2]
        val precipOrSnow = skycon?.let {
            it.contains("RAIN") || it.contains("STORM") || it.contains("SNOW")
        } == true
        val glass = if (isDay && !precipOrSnow) {
            val light = lerpColor(saturateColor(hue, 1.4f), Color.White, 0.55f)
            GlassColors(
                // 白天玻璃轻量化：降低罩色不透明度，让卡片「溶解」进蓝天、雨丝/星空透出，
                // 接近 iOS 仅以模糊+微弱亮度差分层的观感（夜晚/雨雪分支保持深玻璃不变）。
                // tint 0.16 兼顾通透与文字对比度；fallback 无模糊，略提高到 0.26 保证层次。
                tint = light.copy(alpha = 0.16f),
                tintFallback = light.copy(alpha = 0.26f),
                isLight = true
            )
        } else {
            val dark = lerpColor(saturateColor(hue, 1.2f), Color.Black, 0.55f)
            GlassColors(
                tint = dark.copy(alpha = 0.32f),
                tintFallback = dark.copy(alpha = 0.45f),
                isLight = false
            )
        }

        // --- Chart Colors ---
        val chartColors = if (isDay) {
            WeatherChartColors(
                clear = Color(0xFFFFF9C4).copy(alpha = 0.85f) to Color(0xFFFFF9C4).copy(alpha = 0.20f),
                partlyCloudy = Color(0xFFFFF4B8).copy(alpha = 0.72f) to Color(0xFFFFF4B8).copy(alpha = 0.14f),
                cloudy = Color(0xFFB0CCE0).copy(alpha = 0.70f) to Color(0xFFB0CCE0).copy(alpha = 0.10f),
                rain = Color(0xFF4FC3F7).copy(alpha = 0.70f) to Color(0xFF4FC3F7).copy(alpha = 0.15f),
                snow = Color(0xFFFFFFFF).copy(alpha = 0.85f) to Color(0xFFFFFFFF).copy(alpha = 0.20f),
                wind = Color(0xFF4DB6AC).copy(alpha = 0.65f) to Color(0xFF4DB6AC).copy(alpha = 0.10f),
                haze = Color(0xFFBCAAA4).copy(alpha = 0.65f) to Color(0xFFBCAAA4).copy(alpha = 0.10f),
                storm = Color(0xFF5C6BC0).copy(alpha = 0.75f) to Color(0xFF5C6BC0).copy(alpha = 0.15f)
            )
        } else {
            WeatherChartColors(
                clear = Color(0xFFFFFDE7).copy(alpha = 0.25f) to Color(0xFFFFF9C4).copy(alpha = 0.15f),
                partlyCloudy = Color(0xFFFFF9C4).copy(alpha = 0.22f) to Color(0xFFFFECB3).copy(alpha = 0.12f),
                cloudy = Color(0xFF8898B0).copy(alpha = 0.25f) to Color(0xFF607088).copy(alpha = 0.15f),
                rain = Color(0xFF70A0F0).copy(alpha = 0.30f) to Color(0xFF4070B8).copy(alpha = 0.18f),
                snow = Color(0xFF80B8FF).copy(alpha = 0.32f) to Color(0xFF5090D0).copy(alpha = 0.20f),
                wind = Color(0xFF60C0D0).copy(alpha = 0.28f) to Color(0xFF4090A0).copy(alpha = 0.16f),
                haze = Color(0xFF908878).copy(alpha = 0.25f) to Color(0xFF706858).copy(alpha = 0.15f),
                storm = Color(0xFFB080FF).copy(alpha = 0.35f) to Color(0xFF7040C0).copy(alpha = 0.22f)
            )
        }

        return WeatherTheme(
            isDay = isDay,
            backgroundGradient = background,
            glass = glass,
            chartColors = chartColors,
            precipitationIconColor = precipitationIconColor
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun getPrecipitationIconColor(skycon: String?, isDay: Boolean): Color = Color.White

    /**
     * 计算当前昼夜相位：优先用日出/日落天文时刻 ± 1 小时判定清晨/傍晚，
     * 无天文数据时回退到固定 06:00 / 18:00 节点。
     */
    fun getDayPhase(
        daily: DailyForecast? = null,
        now: Calendar = Calendar.getInstance()
    ): DayPhase {
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val astro = daily?.astro?.firstOrNull { isSameDate(it.date, now) }
        val sunriseMin = parseTimeMinutes(astro?.sunrise?.time) ?: 6 * 60
        val sunsetMin = parseTimeMinutes(astro?.sunset?.time) ?: 18 * 60
        return when {
            minuteOfDay in (sunriseMin - 60) until (sunriseMin + 60) -> DayPhase.MORNING
            minuteOfDay in (sunsetMin - 60) until (sunsetMin + 60) -> DayPhase.EVENING
            isCurrentlyDay(daily, now) -> DayPhase.DAY
            else -> DayPhase.NIGHT
        }
    }

    fun getWeatherInfo(skycon: String?, hour: Int = 12): WeatherInfo {
        val isDay = hour in 6..18
        return when (skycon) {
            "CLEAR_DAY" -> WeatherInfo("晴", "clear-day", true)
            "CLEAR_NIGHT" -> WeatherInfo("晴", "clear-night", false)
            "PARTLY_CLOUDY_DAY" -> WeatherInfo("多云", "partly-cloudy-day", true)
            "PARTLY_CLOUDY_NIGHT" -> WeatherInfo("多云", "partly-cloudy-night", false)
            "CLOUDY" -> WeatherInfo("阴", "overcast", isDay)
            "LIGHT_HAZE" -> WeatherInfo("轻度霾", "haze", isDay)
            "MODERATE_HAZE" -> WeatherInfo("中度霾", "haze", isDay)
            "HEAVY_HAZE" -> WeatherInfo("重度霾", "haze", isDay)
            "LIGHT_RAIN" -> WeatherInfo("小雨", "drizzle", isDay)
            "MODERATE_RAIN" -> WeatherInfo("中雨", "rain", isDay)
            "HEAVY_RAIN" -> WeatherInfo("大雨", "extreme-rain", isDay)
            "STORM_RAIN" -> WeatherInfo("暴雨", "thunderstorms-rain", isDay)
            "FOG" -> WeatherInfo("雾", "fog", isDay)
            "LIGHT_SNOW" -> WeatherInfo("小雪", "snow", isDay)
            "MODERATE_SNOW" -> WeatherInfo("中雪", "snow", isDay)
            "HEAVY_SNOW" -> WeatherInfo("大雪", "extreme-snow", isDay)
            "STORM_SNOW" -> WeatherInfo("暴雪", "extreme-snow", isDay)
            "WIND" -> WeatherInfo("大风", "wind", isDay)
            "DUST" -> WeatherInfo("浮尘", "haze", isDay)
            "SAND" -> WeatherInfo("沙尘", "haze", isDay)
            "SLEET" -> WeatherInfo("雨夹雪", "snow", isDay)
            "THUNDER_SHOWER" -> WeatherInfo("雷阵雨", "thunderstorms-rain", isDay)
            else -> WeatherInfo("未知", "overcast", isDay)
        }
    }

    /**
     * 获取天空渐变。晴天/多云场景在 [phase] 为 MORNING/EVENING 时使用清晨/傍晚主题；
     * [phase] 为 null 时保持原行为（仅按 skycon + isDay 取日夜渐变）。
     */
    fun getWeatherGradient(skycon: String?, isDay: Boolean = true, phase: DayPhase? = null): List<Color> {
        return when {
            // 清晨/傍晚例外主题（仅晴/多云生效）
            phase == DayPhase.MORNING && skycon != null && (skycon.contains("CLEAR") || skycon.contains("PARTLY_CLOUDY")) ->
                if (skycon.contains("PARTLY_CLOUDY")) MorningPartialCloudGradient else MorningSunnyGradient
            phase == DayPhase.EVENING && skycon != null && (skycon.contains("CLEAR") || skycon.contains("PARTLY_CLOUDY")) ->
                if (skycon.contains("PARTLY_CLOUDY")) EveningPartialCloudGradient else EveningSunnyGradient
            skycon == null -> if (isDay) SunnyGradient else SunnyNightGradient
            skycon.contains("CLEAR") -> if (isDay) SunnyGradient else SunnyNightGradient
            skycon.contains("PARTLY_CLOUDY") -> if (isDay) PartialCloudGradient else PartialCloudNightGradient
            skycon.contains("CLOUDY") -> if (isDay) CloudyGradient else CloudyNightGradient
            skycon.contains("RAIN") || skycon.contains("STORM") -> if (isDay) RainyGradient else RainyNightGradient
            skycon.contains("SNOW") -> if (isDay) SnowyGradient else SnowyNightGradient
            skycon.contains("HAZE") || skycon == "FOG" || skycon == "DUST" || skycon == "SAND" -> if (isDay) HazeGradient else HazeNightGradient
            skycon == "WIND" -> if (isDay) WindyGradient else WindyNightGradient
            else -> if (isDay) SunnyGradient else SunnyNightGradient
        }
    }

    fun formatTemperature(temp: Double?): String {
        if (temp == null) return "--"
        return "${kotlin.math.round(temp).toInt()}°"
    }

    fun isBrightBackground(skycon: String?, isDay: Boolean = isCurrentlyDay()): Boolean {
        if (!isDay) return false
        return skycon == null ||
               skycon.contains("CLEAR") ||
               skycon.contains("PARTLY_CLOUDY") ||
               skycon.contains("CLOUDY")
    }

    fun getTemperatureColor(temp: Double?): Color {
        if (temp == null) return Color.White
        val t = temp.toFloat()
        return when {
            t < -10f -> Color(0xFF90CAF9) // Very cold
            t < 0f -> lerpColor(Color(0xFF90CAF9), Color(0xFF64B5F6), (t + 10f) / 10f)
            t < 10f -> lerpColor(Color(0xFF64B5F6), Color(0xFF4FC3F7), t / 10f)
            t < 20f -> lerpColor(Color(0xFF4FC3F7), Color(0xFFFFD54F), (t - 10f) / 10f)
            t < 30f -> lerpColor(Color(0xFFFFD54F), Color(0xFFFFB74D), (t - 20f) / 10f)
            t < 40f -> lerpColor(Color(0xFFFFB74D), Color(0xFFEF5350), (t - 30f) / 10f)
            else -> Color(0xFFEF5350) // Very hot
        }
    }

    private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return Color(
            red = start.red + (end.red - start.red) * f,
            green = start.green + (end.green - start.green) * f,
            blue = start.blue + (end.blue - start.blue) * f,
            alpha = start.alpha + (end.alpha - start.alpha) * f
        )
    }

    /** 饱和度拉伸：以明度中灰为支点向外扩展，factor>1 更鲜艳（去灰），<1 更灰 */
    private fun saturateColor(color: Color, factor: Float): Color {
        val gray = (color.red + color.green + color.blue) / 3f
        fun ch(c: Float) = (gray + (c - gray) * factor).coerceIn(0f, 1f)
        return Color(
            red = ch(color.red),
            green = ch(color.green),
            blue = ch(color.blue),
            alpha = color.alpha
        )
    }

    fun formatHumidity(humidity: Double?): String {
        if (humidity == null) return "--"
        return "${(humidity * 100).toInt()}%"
    }

    fun formatWindSpeed(speed: Double?): String {
        if (speed == null) return "--"
        if (speed < 1) return "0"
        val s = kotlin.math.ceil(speed).toInt()
        return when {
            s < 6 -> "1"
            s < 12 -> "2"
            s < 20 -> "3"
            s < 29 -> "4"
            s < 39 -> "5"
            s < 50 -> "6"
            s < 62 -> "7"
            s < 75 -> "8"
            s < 89 -> "9"
            s < 103 -> "10"
            s < 118 -> "11"
            s < 134 -> "12"
            s < 150 -> "13"
            s < 167 -> "14"
            s < 184 -> "15"
            s < 202 -> "16"
            else -> "17"
        }
    }

    fun formatWindDirection(direction: Double?): String {
        if (direction == null) return ""
        return when {
            direction < 22.5 || direction >= 337.5 -> "北风"
            direction < 67.5 -> "东北风"
            direction < 112.5 -> "东风"
            direction < 157.5 -> "东南风"
            direction < 202.5 -> "南风"
            direction < 247.5 -> "西南风"
            direction < 292.5 -> "西风"
            else -> "西北风"
        }
    }

    fun formatPressure(pressure: Double?): String {
        if (pressure == null) return "--"
        return "${(pressure / 100).toInt()} 百帕"
    }

    fun formatVisibility(visibility: Double?): String {
        if (visibility == null) return "--"
        return if (visibility >= 1000) {
            "${"%.1f".format(visibility / 1000)} km"
        } else {
            "${visibility.toInt()} m"
        }
    }

    fun formatHourShort(datetime: String?): String {
        if (datetime == null) return ""
        return try {
            val date = hourFormat.get()!!.parse(datetime) ?: return ""
            val cal = Calendar.getInstance()
            cal.time = date
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            "${hour}:00"
        } catch (e: Exception) {
            ""
        }
    }

    fun extractHour(datetime: String?): Int {
        if (datetime == null) return -1
        return try {
            val date = hourFormat.get()!!.parse(datetime) ?: return -1
            val cal = Calendar.getInstance()
            cal.time = date
            cal.get(Calendar.HOUR_OF_DAY)
        } catch (e: Exception) {
            -1
        }
    }

    fun formatWeekday(dateStr: String?): String {
        return try {
            val date = parseDateOnly(dateStr) ?: return ""
            val cal = Calendar.getInstance()
            val today = Calendar.getInstance()
            cal.time = date

            when {
                isSameDay(cal, today) -> "今天"

                isTomorrow(dateStr) -> "明天"

                else -> {
                    val weekdays = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
                    weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
                }
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun todayTemperature(daily: DailyForecast?): DailyTemperature? {
        val temperatures = daily?.temperature ?: return null
        return temperatures.firstOrNull { isToday(it.date) } ?: temperatures.firstOrNull()
    }

    fun isToday(dateStr: String?): Boolean = isRelativeDay(dateStr, 0)

    fun isYesterday(dateStr: String?): Boolean = isRelativeDay(dateStr, -1)

    fun isTomorrow(dateStr: String?): Boolean {
        return isRelativeDay(dateStr, 1)
    }

    fun isCurrentlyDay(
        daily: DailyForecast? = null,
        now: Calendar = Calendar.getInstance()
    ): Boolean {
        isDayByAstro(daily?.astro, now)?.let { return it }
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return hour in 6..18
    }

    private fun isDayByAstro(
        astro: List<DailyAstro>?,
        now: Calendar
    ): Boolean? {
        if (astro.isNullOrEmpty()) return null
        val candidate = astro.firstOrNull { isSameDate(it.date, now) }
            ?: astro.firstOrNull { parseTimeMinutes(it.sunrise?.time) != null && parseTimeMinutes(it.sunset?.time) != null }
            ?: return null
        val sunrise = parseTimeMinutes(candidate.sunrise?.time) ?: return null
        val sunset = parseTimeMinutes(candidate.sunset?.time) ?: return null
        if (sunrise >= sunset) return null
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minuteOfDay in sunrise until sunset
    }

    private fun parseTimeMinutes(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        val match = Regex("(\\d{1,2}):(\\d{2})").find(time) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun isSameDate(dateStr: String?, calendar: Calendar): Boolean {
        return try {
            val date = parseDateOnly(dateStr) ?: return false
            val other = Calendar.getInstance()
            other.time = date
            isSameDay(other, calendar)
        } catch (_: Exception) {
            false
        }
    }

    private fun parseDateOnly(dateStr: String?): Date? {
        if (dateStr == null) return null
        val dateText = dateStr.take(10)
        return dateFormat.get()!!.parse(dateText)
    }

    private fun isRelativeDay(dateStr: String?, dayOffset: Int): Boolean {
        return try {
            val date = parseDateOnly(dateStr) ?: return false
            val cal = Calendar.getInstance()
            val target = Calendar.getInstance()
            cal.time = date
            target.add(Calendar.DAY_OF_YEAR, dayOffset)
            isSameDay(cal, target)
        } catch (e: Exception) {
            false
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
