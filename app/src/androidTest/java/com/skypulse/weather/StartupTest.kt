package com.skypulse.weather

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class StartupTest {
    @get:Rule
    val permissions = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    @Test
    fun authorizedStartupLeavesLoadingAndShowsWeatherOrRecoveryActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                val onboarding = device.wait(Until.findObject(By.text("开启权限并继续")), 10_000)
                onboarding?.click()
                // Read the displayed screen without requiring particle animations to become idle.
                val ready = device.wait(
                    Until.findObject(By.res(Pattern.compile("weather-(content|error)"))), 45_000
                )
                assertNotNull("授权后仍停在加载状态，未出现天气或恢复操作", ready)
            } finally {
                val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "validation").apply { mkdirs() }
                device.takeScreenshot(File(directory, "authorized-startup.png"))
                device.dumpWindowHierarchy(File(directory, "authorized-startup.xml"))
            }
        }
    }
}
