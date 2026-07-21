package com.skypulse.weather.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SkyPulseDesignSystem {
    object Colors {
        val settingsBackground = IosSettingsBg
        val settingsSurface = IosCardBg
        val settingsDivider = IosDividerColor
    }

    object Radius {
        val card = 20.dp
        val cityCard = 18.dp
        val settingsCard = 16.dp
        val pill = 50.dp
    }

    object Border {
        val hairline = 0.5.dp
    }

    object Spacing {
        val screenHorizontal = 16.dp
        val homeHorizontal = 20.dp
        val sectionGap = 8.dp
        val contentGap = 12.dp
    }

    object TypographyScale {
        val temperature = 100.sp
        val temperatureDegree = 48.sp
    }

    object Motion {
        const val fastMillis = 200
        const val cardEnterMillis = 600
        const val heroEnterMillis = 800
        const val cardEnterDelayMillis = 300
        const val lifecycleSkipMillis = 1200L
    }

    object TouchTarget {
        val default = 48.dp
        val listRow = 52.dp
    }
}
