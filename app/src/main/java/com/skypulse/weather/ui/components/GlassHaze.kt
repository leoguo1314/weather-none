package com.skypulse.weather.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState

/**
 * 主页玻璃模糊状态：由 [WeatherBackground] 提供，[GlassCard] 消费。
 * 为 null 时（省电模式等降级场景）卡片自动使用增强底色。
 */
val LocalGlassHazeState = compositionLocalOf<HazeState?> { null }

/**
 * 监听系统省电模式（粒子引擎与玻璃模糊降级共用）。
 * 系统保护广播，无需 EXPORTED/NOT_EXPORTED 标志。
 */
@Composable
internal fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val powerManager = remember { appContext.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var powerSave by remember { mutableStateOf(powerManager.isPowerSaveMode) }
    DisposableEffect(appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                powerSave = powerManager.isPowerSaveMode
            }
        }
        appContext.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { appContext.unregisterReceiver(receiver) }
    }
    return powerSave
}
