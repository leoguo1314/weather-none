package com.skypulse.weather.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRefreshPresentationPolicyTest {

    @Test
    fun `cold start never hides a refresh failure`() {
        assertFalse(
            effectiveRefreshSilence(
                requestedSilent = true,
                hasCachedWeather = false
            )
        )
    }

    @Test
    fun `background refresh stays silent while cached weather is visible`() {
        assertTrue(
            effectiveRefreshSilence(
                requestedSilent = true,
                hasCachedWeather = true
            )
        )
    }

    @Test
    fun `user initiated refresh always surfaces failures`() {
        assertFalse(
            effectiveRefreshSilence(
                requestedSilent = false,
                hasCachedWeather = true
            )
        )
    }
}
