package com.skypulse.weather.ui.navigation

/**
 * Application routes.
 *
 * Kept independent from Compose navigation so Android and future HarmonyOS
 * adapters can share the same route model.
 */
sealed interface AppRoute {
    val route: String

    data object Weather : AppRoute {
        override val route: String = "weather"
    }

    data object AgentChat : AppRoute {
        override val route: String = "agent_chat"
    }
}
