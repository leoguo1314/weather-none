package com.skypulse.weather.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.skypulse.weather.ui.agent.AgentChatScreen
import com.skypulse.weather.ui.screen.WeatherScreen

@Composable
fun WeatherNavHost() {
    var route by rememberSaveable { mutableStateOf(AppRoute.WEATHER) }
    var agentCityId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = route == AppRoute.AGENT_CHAT) {
        route = AppRoute.WEATHER
    }

    Crossfade(targetState = route, label = "top_level_route") { target ->
        when (target) {
            AppRoute.WEATHER -> {
                WeatherScreen(
                    onOpenAiAssistant = { cityId ->
                        agentCityId = cityId
                        route = AppRoute.AGENT_CHAT
                    }
                )
            }

            AppRoute.AGENT_CHAT -> {
                AgentChatScreen(
                    cityId = agentCityId,
                    onBack = { route = AppRoute.WEATHER }
                )
            }
        }
    }
}
