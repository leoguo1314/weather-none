package com.skypulse.weather.widget

object WidgetRefreshPolicy {
    const val PERIODIC_REFRESH_MINUTES = 15L

    private const val SIGNIFICANT_MOVE_METERS = 1_000f
    private const val MAX_CACHE_AGE_MILLIS = 30 * 60 * 1000L

    fun shouldFetchWeather(
        distanceMeters: Float,
        lastFetchTimeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val cacheAge = (nowMillis - lastFetchTimeMillis).coerceAtLeast(0L)
        return lastFetchTimeMillis <= 0L ||
            distanceMeters >= SIGNIFICANT_MOVE_METERS ||
            cacheAge >= MAX_CACHE_AGE_MILLIS
    }
}
