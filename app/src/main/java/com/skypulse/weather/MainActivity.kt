package com.skypulse.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.skypulse.weather.ui.screen.WeatherScreen
import com.skypulse.weather.ui.theme.SkyPulseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 启动屏退出交叉淡化：此时主页（天空渐变）已在启动屏下方完成首帧渲染，
        // 启动屏渐隐即露出同色天空，消除“色块硬切”的割裂感。
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.view.animate()
                .alpha(0f)
                .setDuration(450L)
                .withEndAction { splashScreenView.remove() }
                .start()
        }

        setContent {
            SkyPulseTheme {
                WeatherScreen()
            }
        }
    }
}
