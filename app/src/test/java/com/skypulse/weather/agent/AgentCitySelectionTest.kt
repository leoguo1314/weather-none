package com.skypulse.weather.agent

import com.skypulse.weather.model.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentCitySelectionTest {
    private val current = City("current", "深圳", 114.0579, 22.5431, isCurrentLocation = true)
    private val saved = City("saved", "上海", 121.4737, 31.2304)

    @Test
    fun `preferred visible city wins over current location`() {
        assertEquals(saved, selectAgentCity(listOf(current, saved), saved.id))
    }

    @Test
    fun `falls back to current location when preferred city is missing`() {
        assertEquals(current, selectAgentCity(listOf(saved, current), "removed"))
    }

    @Test
    fun `falls back to first city without current location`() {
        assertEquals(saved, selectAgentCity(listOf(saved), null))
    }

    @Test
    fun `returns null when no city exists`() {
        assertNull(selectAgentCity(emptyList(), null))
    }
}
