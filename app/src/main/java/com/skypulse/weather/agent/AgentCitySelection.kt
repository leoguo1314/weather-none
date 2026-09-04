package com.skypulse.weather.agent

import com.skypulse.weather.model.City

internal fun selectAgentCity(cities: List<City>, preferredCityId: String?): City? =
    preferredCityId
        ?.let { id -> cities.firstOrNull { it.id == id } }
        ?: cities.firstOrNull { it.isCurrentLocation }
        ?: cities.firstOrNull()
