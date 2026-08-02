package com.skypulse.weather.data

import com.skypulse.weather.util.DayPhase

/**
 * 开发者选项 - 天气背景调试预设
 *
 * 每个预设对应项目中的一个 skycon 与昼夜组合，选中后强制主页呈现该主题的
 * 天气背景渐变、玻璃配色与粒子动效，便于调试各天气主题的视觉效果。
 * 列表与 [com.skypulse.weather.util.WeatherUtils.getWeatherInfo] 中的
 * 天气现象以及 Color.kt 中的主题渐变一一对应。
 */
enum class DebugWeatherPreset(
    val displayName: String,
    val skycon: String,
    val isDay: Boolean,
    val phase: DayPhase? = null
) {
    SUNNY_MORNING("晴天清晨", "CLEAR_DAY", true, DayPhase.MORNING),
    SUNNY_NOON("晴天正午", "CLEAR_DAY", true),
    SUNNY_EVENING("晴天傍晚", "CLEAR_DAY", true, DayPhase.EVENING),
    SUNNY_NIGHT("晴天夜晚", "CLEAR_NIGHT", false),
    PARTLY_CLOUDY_MORNING("多云清晨", "PARTLY_CLOUDY_DAY", true, DayPhase.MORNING),
    PARTLY_CLOUDY_NOON("多云正午", "PARTLY_CLOUDY_DAY", true),
    PARTLY_CLOUDY_EVENING("多云傍晚", "PARTLY_CLOUDY_DAY", true, DayPhase.EVENING),
    PARTLY_CLOUDY_NIGHT("多云夜晚", "PARTLY_CLOUDY_NIGHT", false),
    CLOUDY_DAY("阴天", "CLOUDY", true),
    CLOUDY_NIGHT("阴天夜晚", "CLOUDY", false),
    LIGHT_RAIN("小雨", "LIGHT_RAIN", true),
    HEAVY_RAIN("大雨", "HEAVY_RAIN", true),
    STORM_RAIN_NIGHT("暴雨夜晚", "STORM_RAIN", false),
    THUNDER_SHOWER("雷阵雨", "THUNDER_SHOWER", true),
    LIGHT_SNOW("小雪", "LIGHT_SNOW", true),
    HEAVY_SNOW("大雪", "HEAVY_SNOW", true),
    STORM_SNOW_NIGHT("暴雪夜晚", "STORM_SNOW", false),
    SLEET("雨夹雪", "SLEET", true),
    FOG("雾", "FOG", true),
    HEAVY_HAZE("重度霾", "HEAVY_HAZE", true),
    WIND_DAY("大风", "WIND", true),
    WIND_NIGHT("大风夜晚", "WIND", false);

    companion object {
        /** 从 SharedPreferences 存储的枚举名解析，解析失败返回 null（跟随实际天气） */
        fun fromName(name: String?): DebugWeatherPreset? =
            entries.firstOrNull { it.name == name }
    }
}
