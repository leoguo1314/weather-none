package com.skypulse.weather.agent

import com.skypulse.weather.agent.model.ToolTrace

data class AgentEngineResult(
    val answer: String,
    val traces: List<ToolTrace>
)

/**
 * Key-free local agent. It plans weather tools and turns live weather data into
 * actionable advice even when no external LLM endpoint has been configured.
 */
class WeatherAgentEngine(
    private val planner: AgentPlanner = AgentPlanner()
) {
    fun execute(input: String, snapshot: AgentSnapshot): AgentEngineResult {
        val plan = planner.createPlan(input)
        val traces = plan.tools.map { tool ->
            ToolTrace(
                toolName = tool,
                status = "success",
                result = runTool(tool, snapshot)
            )
        }
        return AgentEngineResult(
            answer = buildOfflineAnswer(input, snapshot, traces),
            traces = traces
        )
    }

    private fun runTool(tool: String, snapshot: AgentSnapshot): String = when (tool) {
        "weather.query" -> buildString {
            append("${snapshot.city}当前${snapshot.skyDescription}，")
            append("气温${snapshot.temperature.display("℃")}，")
            append("体感${snapshot.apparentTemperature.display("℃")}，")
            append("湿度${snapshot.humidity.display("%")}，")
            append("风速${snapshot.windSpeed.display("km/h")}。")
        }
        "forecast.query" -> snapshot.daily.take(4).joinToString("；") { day ->
            "${day.date.takeLast(5)} ${skyconDescription(day.skycon)} ${day.low.display("℃")}~${day.high.display("℃")} 降水${day.precipitationProbability.display("%")}" 
        }.ifBlank { "暂无逐日预报数据。" }
        "air_quality.query" -> {
            val aqi = snapshot.aqi
            val level = when {
                aqi == null -> "暂无 AQI 数据"
                aqi <= 50 -> "优"
                aqi <= 100 -> "良"
                aqi <= 150 -> "轻度污染"
                aqi <= 200 -> "中度污染"
                else -> "重度污染"
            }
            "AQI ${aqi.display()}，空气质量$level。"
        }
        "travel.advice" -> travelAdvice(snapshot)
        "clothing.advice" -> clothingAdvice(snapshot)
        "alert.query" -> snapshot.alertTitles.joinToString("；").ifBlank { "当前没有有效气象预警。" }
        else -> "工具 $tool 暂不可用。"
    }

    private fun buildOfflineAnswer(
        input: String,
        snapshot: AgentSnapshot,
        traces: List<ToolTrace>
    ): String {
        val headline = snapshot.forecastKeypoint?.takeIf { it.isNotBlank() }
            ?: "${snapshot.city}当前${snapshot.skyDescription}，${snapshot.temperature.display("℃")}。"
        val advice = linkedSetOf<String>()
        advice += clothingAdvice(snapshot)
        advice += travelAdvice(snapshot)

        if (snapshot.aqi == null || snapshot.aqi <= 100) {
            advice += "空气条件允许时可正常开窗和户外活动。"
        } else {
            advice += "空气质量欠佳，敏感人群建议减少长时间户外活动并佩戴口罩。"
        }
        if (snapshot.alertTitles.isNotEmpty()) {
            advice += "有气象预警：${snapshot.alertTitles.first()}，请留意官方更新。"
        }

        val requestedDetails = traces
            .filterNot { it.toolName == "weather.query" }
            .joinToString("\n") { "• ${it.result}" }
        return buildString {
            append(headline)
            append("\n\n")
            append(advice.joinToString("\n") { "• $it" })
            if (requestedDetails.isNotBlank()) {
                append("\n\n针对“${input.take(24)}”的分析：\n")
                append(requestedDetails)
            }
        }
    }

    private fun clothingAdvice(snapshot: AgentSnapshot): String {
        val feelsLike = snapshot.apparentTemperature ?: snapshot.temperature
        val base = when {
            feelsLike == null -> "建议按体感分层穿衣"
            feelsLike <= 5 -> "建议羽绒服或厚外套，注意保暖"
            feelsLike <= 14 -> "建议夹克或针织外套"
            feelsLike <= 22 -> "建议长袖或薄外套"
            feelsLike <= 29 -> "建议轻薄透气衣物"
            else -> "天气炎热，建议速干透气衣物并及时补水"
        }
        val precipitationProbability = snapshot.daily.firstOrNull()?.precipitationProbability
        val likelyRain = precipitationProbability?.let {
            it >= if (it <= 1.0) 0.4 else 40.0
        } ?: false
        val rain = snapshot.skycon.contains("RAIN", ignoreCase = true) || likelyRain
        return if (rain) "$base；随身带伞并选择防滑鞋。" else "$base。"
    }

    private fun travelAdvice(snapshot: AgentSnapshot): String {
        val reasons = mutableListOf<String>()
        if (snapshot.skycon.contains("RAIN", ignoreCase = true)) reasons += "路面湿滑"
        if (snapshot.skycon.contains("SNOW", ignoreCase = true)) reasons += "可能积雪结冰"
        if ((snapshot.windSpeed ?: 0.0) >= 30) reasons += "风力较强"
        if ((snapshot.temperature ?: 20.0) >= 35) reasons += "高温"
        if ((snapshot.aqi ?: 0.0) > 150) reasons += "空气污染"
        return if (reasons.isEmpty()) {
            "当前天气对通勤和常规户外活动影响较小，出发前再刷新一次天气即可。"
        } else {
            "出行需注意${reasons.joinToString("、")}，建议预留时间并优先选择公共交通。"
        }
    }
}
