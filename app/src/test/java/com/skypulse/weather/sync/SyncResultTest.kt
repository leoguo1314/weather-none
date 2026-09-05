package com.skypulse.weather.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncResultTest {

    @Test
    fun `location failure explains how to recover`() {
        assertEquals(
            "无法获取当前位置，请确认系统定位已开启后重试",
            SyncResult.LocationFailed.failureMessageOrNull()
        )
    }

    @Test
    fun `provider error keeps its original message`() {
        assertEquals(
            "天气源连接失败",
            SyncResult.Error("天气源连接失败").failureMessageOrNull()
        )
    }

    @Test
    fun `success has no failure message`() {
        assertNull(
            SyncResult.Success(
                weather = com.skypulse.weather.model.WeatherResponse(status = "ok")
            ).failureMessageOrNull()
        )
    }
}
