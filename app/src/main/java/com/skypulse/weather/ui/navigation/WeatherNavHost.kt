package com.skypulse.weather.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skypulse.weather.ui.agent.AgentChatScreen
import com.skypulse.weather.ui.screen.WeatherScreen

@Composable
fun WeatherNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Weather.route
    ) {
        composable(AppRoute.Weather.route) {
            WeatherScreen(
                onOpenAiAssistant = {
                    navController.navigate(AppRoute.AgentChat.route)
                }
            )
        }

        composable(AppRoute.AgentChat.route) {
            AgentChatScreen()
        }
    }
}
