package com.skypulse.weather.agent

class AgentPlanner {
    fun createPlan(input: String): AgentPlan {
        val lower = input.lowercase()
        val tools = linkedSetOf("weather.query")

        if (
            listOf("明天", "后天", "未来", "一周", "七天", "趋势", "forecast", "tomorrow", "week")
                .any(lower::contains)
        ) {
            tools += "forecast.query"
        }
        if (listOf("空气", "污染", "口罩", "aqi", "pm2.5").any(lower::contains)) {
            tools += "air_quality.query"
        }
        if (listOf("出门", "出行", "通勤", "旅行", "户外", "运动", "跑步", "travel").any(lower::contains)) {
            tools += "travel.advice"
        }
        if (listOf("穿", "衣", "带伞", "防晒", "clothes", "umbrella").any(lower::contains)) {
            tools += "clothing.advice"
        }
        if (listOf("预警", "台风", "暴雨", "雷电", "大风", "alert").any(lower::contains)) {
            tools += "alert.query"
        }

        return AgentPlan(tools.toList())
    }
}

data class AgentPlan(
    val tools: List<String>
)
