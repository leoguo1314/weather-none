package com.skypulse.weather

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class StartupTest {
    @get:Rule(order = 0)
    val permissions = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun authorizedStartupLeavesLoadingAndShowsWeatherOrRecoveryActions() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("开启权限并继续").fetchSemanticsNodes().isNotEmpty() ||
                hasReadyScreen()
        }
        if (compose.onAllNodesWithText("开启权限并继续").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("开启权限并继续").performClick()
        }
        // Weather may be unavailable in CI, but recovery actions must replace the loading screen.
        compose.waitUntil(45_000) { hasReadyScreen() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "validation").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "authorized-startup.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun hasReadyScreen(): Boolean =
        compose.onAllNodesWithTag("weather-content").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithTag("weather-error").fetchSemanticsNodes().isNotEmpty()
}
