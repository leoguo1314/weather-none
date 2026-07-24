package com.skypulse.weather.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews
import com.skypulse.weather.MainActivity
import com.skypulse.weather.R
import com.skypulse.weather.model.WeatherResponse
import com.skypulse.weather.ui.components.WeatherSvgRenderer
import com.skypulse.weather.util.FileLogger
import androidx.compose.ui.graphics.toArgb
import com.skypulse.weather.util.WeatherUtils
import com.skypulse.weather.data.MembershipRepository
import java.text.SimpleDateFormat
import java.util.*

object WeatherWidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private val iconCache = android.util.LruCache<String, Bitmap>(14)

    fun updateLoading(context: Context, cityName: String? = null) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) {
                FileLogger.w(TAG, "updateLoading: \u65e0\u6d3b\u8dc3 widget\uff0c\u8df3\u8fc7\u6e32\u67d3")
                return
            }

            val cityText = shortenLocation(cityName ?: "\u5b9a\u4f4d\u4e2d...")
            val iconBitmap = renderIcon(context, "partly-cloudy-day")
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_small)
                views.setTextViewText(R.id.widget_city, cityText)
                views.setTextViewText(R.id.widget_temp, "--")
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, iconBitmap)
                }
                // Set placeholder for forecast items
                views.setTextViewText(R.id.widget_time_now, "\u73b0\u5728")
                views.setTextViewText(R.id.widget_time_1h, "--")
                views.setTextViewText(R.id.widget_time_2h, "--")
                views.setTextViewText(R.id.widget_temp_now, "--")
                views.setTextViewText(R.id.widget_temp_1h, "--")
                views.setTextViewText(R.id.widget_temp_2h, "--")

                val (w, h) = getWidgetSizePx(context, widgetId)
                val sizedBg = buildGradientBitmap(context, null, w, h)
                views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                                views.setBoolean(R.id.widget_container, "setClipToOutline", true)
                views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_rounded_bg)

                val intent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pending)
                manager.updateAppWidget(widgetId, views)
            }
            FileLogger.i(TAG, "updateLoading: \u6e32\u67d3\u5b9a\u4f4d\u5360\u4f4d\u6001\u5b8c\u6210, widgetCount=${ids.size}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "updateLoading: \u6e32\u67d3\u5f02\u5e38", e)
        }
    }

    fun updateAll(context: Context, weather: WeatherResponse?, cityName: String?) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
            if (ids.isEmpty()) {
                FileLogger.w(TAG, "updateAll: \u65e0\u6d3b\u8dc3 widget\uff0c\u8df3\u8fc7\u6e32\u67d3")
                return
            }

            val realtime = weather?.result?.realtime
            val daily = weather?.result?.daily
            val hourly = weather?.result?.hourly
            val skycon = realtime?.skycon
            val isDay = WeatherUtils.isCurrentlyDay(daily)
            val info = WeatherUtils.getWeatherInfo(skycon)
            val tempText = WeatherUtils.formatTemperature(realtime?.temperature)
            val cityText = shortenLocation(cityName ?: "--")

            // Get hourly forecast for now, +1h, +2h
            val hourlyTemps = hourly?.temperature
            val hourlySkycons = hourly?.skycon
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)

            // Find current hour index in hourly data
            val nowIndex = findHourlyIndex(hourlyTemps, currentHour)
            val h1Index = if (nowIndex >= 0) nowIndex + 1 else -1
            val h2Index = if (nowIndex >= 0) nowIndex + 2 else -1

            // Get temperatures
            val tempNow = realtime?.temperature
            val temp1h = getHourlyValue(hourlyTemps, h1Index)
            val temp2h = getHourlyValue(hourlyTemps, h2Index)

            // Get skycons
            val skycon1h = getHourlySkycon(hourlySkycons, h1Index)
            val skycon2h = getHourlySkycon(hourlySkycons, h2Index)

            // Get weather info for each hour
            val info1h = WeatherUtils.getWeatherInfo(skycon1h)
            val info2h = WeatherUtils.getWeatherInfo(skycon2h)

            // Format times
            val timeNow = "\u73b0\u5728"
            val time1h = formatHour(currentHour + 1)
            val time2h = formatHour(currentHour + 2)

            FileLogger.i(TAG, "updateAll: \u6e32\u67d3\u6570\u636e \u2014 city=$cityText, temp=$tempText, skycon=$skycon, isDay=$isDay")
            FileLogger.d(TAG, "updateAll: \u5c0f\u65f6\u9884\u62a5 now=$tempNow/${info.icon}, 1h=$temp1h/${info1h.icon}, 2h=$temp2h/${info2h.icon}")

            val precipitationColor = WeatherUtils.getPrecipitationIconColor(skycon, isDay).toArgb()
            val iconBitmap = renderIcon(context, info.icon, precipitationColor)
            val icon1hBitmap = renderIcon(context, info1h.icon, precipitationColor)
            val icon2hBitmap = renderIcon(context, info2h.icon, precipitationColor)

            if (iconBitmap == null) {
                FileLogger.w(TAG, "updateAll: \u56fe\u6807\u6e32\u67d3\u5931\u8d25 skycon=$skycon, icon=${info.icon}")
            }

            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_small)
                // Main temperature and city
                views.setTextViewText(R.id.widget_temp, tempText)
                views.setTextViewText(R.id.widget_city, cityText)

                // Main icon
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon, iconBitmap)
                }

                // Forecast row - Now
                views.setTextViewText(R.id.widget_time_now, timeNow)
                if (iconBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon_now, iconBitmap)
                }
                views.setTextViewText(R.id.widget_temp_now, WeatherUtils.formatTemperature(tempNow))

                // Forecast row - +1h
                views.setTextViewText(R.id.widget_time_1h, time1h)
                if (icon1hBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon_1h, icon1hBitmap)
                }
                views.setTextViewText(R.id.widget_temp_1h, WeatherUtils.formatTemperature(temp1h))

                // Forecast row - +2h
                views.setTextViewText(R.id.widget_time_2h, time2h)
                if (icon2hBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_icon_2h, icon2hBitmap)
                }
                views.setTextViewText(R.id.widget_temp_2h, WeatherUtils.formatTemperature(temp2h))

                val (w, h) = getWidgetSizePx(context, widgetId)
                val sizedBg = buildGradientBitmap(context, skycon, w, h, isDay)
                views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                                views.setBoolean(R.id.widget_container, "setClipToOutline", true)
                views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_rounded_bg)

                val intent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pending)
                manager.updateAppWidget(widgetId, views)
            }
            FileLogger.i(TAG, "updateAll: \u6e32\u67d3\u5b8c\u6210, widgetCount=${ids.size}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "updateAll: \u6e32\u67d3\u5f02\u5e38", e)
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
                ids.forEach { widgetId ->
                    try {
                        val views = RemoteViews(context.packageName, R.layout.widget_small)
                        views.setTextViewText(R.id.widget_city, "--")
                        views.setTextViewText(R.id.widget_temp, "--")
                        views.setTextViewText(R.id.widget_time_now, "\u73b0\u5728")
                        views.setTextViewText(R.id.widget_time_1h, "--")
                        views.setTextViewText(R.id.widget_time_2h, "--")
                        views.setTextViewText(R.id.widget_temp_now, "--")
                        views.setTextViewText(R.id.widget_temp_1h, "--")
                        views.setTextViewText(R.id.widget_temp_2h, "--")
                        val (w, h) = getWidgetSizePx(context, widgetId)
                        val sizedBg = buildGradientBitmap(context, null, w, h)
                        views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                                                views.setBoolean(R.id.widget_container, "setClipToOutline", true)
                        views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_rounded_bg)
                        val intent = Intent(context, MainActivity::class.java)
                        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        views.setOnClickPendingIntent(R.id.widget_container, pending)
                        manager.updateAppWidget(widgetId, views)
                    } catch (_: Exception) {}
                }
                FileLogger.i(TAG, "updateAll: \u9ed8\u8ba4\u72b6\u6001\u6e32\u67d3\u5b8c\u6210")
            } catch (e2: Exception) {
                FileLogger.e(TAG, "updateAll: \u9ed8\u8ba4\u72b6\u6001\u6e32\u67d3\u4e5f\u5931\u8d25", e2)
            }
        }
    }

    private fun findHourlyIndex(hourlyTemps: List<com.skypulse.weather.model.HourlyValue>?, targetHour: Int): Int {
        if (hourlyTemps == null) return -1
        val targetSuffix = String.format("T%02d:", targetHour)
        return hourlyTemps.indexOfFirst { it.datetime?.contains(targetSuffix) == true }
    }

    private fun getHourlyValue(hourlyTemps: List<com.skypulse.weather.model.HourlyValue>?, index: Int): Double? {
        if (hourlyTemps == null || index < 0 || index >= hourlyTemps.size) return null
        return hourlyTemps[index].value
    }

    private fun getHourlySkycon(hourlySkycons: List<com.skypulse.weather.model.HourlySkycon>?, index: Int): String? {
        if (hourlySkycons == null || index < 0 || index >= hourlySkycons.size) return null
        return hourlySkycons[index].value
    }

    private fun formatHour(hour: Int): String {
        val h = ((hour % 24) + 24) % 24
        return String.format("%02d:00", h)
    }

    private fun shortenLocation(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return "--"
        if (value == "\u5b9a\u4f4d\u4e2d...") return value

        val districtMatch = Regex("([\u5e02\u533a\u53bf]+[\u533a\u53bf])").find(value)
        if (districtMatch != null) return districtMatch.groupValues[1]

        val cityMatch = Regex("([\u5e02]+[\u5e02])").find(value)
        if (cityMatch != null) return cityMatch.groupValues[1]

        val segment = value.split(Regex("[\u3001\u3002\uff0c]")).firstOrNull { it.length >= 2 } ?: value
        return if (segment.length > 4) segment.substring(0, 4) else segment
    }

    private fun getWidgetSizePx(context: Context, widgetId: Int): Pair<Int, Int> {
        val manager = AppWidgetManager.getInstance(context)
        val options = manager.getAppWidgetOptions(widgetId)
        val density = context.resources.displayMetrics.density

        // Use MIN width/height for more consistent sizing across launchers
        // MIN is closer to the actual cell size, MAX can be much larger
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

        // Convert to pixels with reasonable bounds
        val width = (widthDp * density).toInt().coerceIn(300, 600)
        val height = (heightDp * density).toInt().coerceIn(300, 600)

        FileLogger.d(TAG, "getWidgetSizePx: widgetId=$widgetId, widthDp=$widthDp, heightDp=$heightDp, width=$width, height=$height, density=$density")
        return width to height
    }

    private fun renderIcon(context: Context, icon: String, precipitationColor: Int? = null): Bitmap? {
        val cacheKey = if (precipitationColor == null) icon else "$icon:$precipitationColor"
        iconCache.get(cacheKey)?.let { return it }
        val bitmap = when (icon) {
            "clear-night" -> renderMoonBitmap(context)
            else -> renderSvgIcon(context, icon, precipitationColor)
        }
        if (bitmap != null) {
            iconCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun renderSvgIcon(context: Context, icon: String, precipitationColor: Int?): Bitmap? {
        return try {
            val sizePx = (48 * context.resources.displayMetrics.density).toInt()
            WeatherSvgRenderer.renderBitmap(context, icon, sizePx, precipitationColor)
        } catch (e: Exception) {
            FileLogger.e(TAG, "renderSvgIcon failed: icon=$icon", e)
            null
        }
    }

    /**
     * Hand-drawn moon icon matching the Compose MoonIcon in WeatherIcon.kt.
     * Uses the same preserved Meteocons moon bezier path with warm golden gradient.
     */
    private fun renderMoonBitmap(context: Context): Bitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val size = (96 * density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val scale = size / 128f

            // Preserved Meteocons moon path (128x128 canvas)
            val v = arrayOf(
                floatArrayOf(60.3018f, 32.582f),
                floatArrayOf(95.3252f, 72.5146f),
                floatArrayOf(64.5361f, 95.5f),
                floatArrayOf(32.5f, 63.8984f),
                floatArrayOf(60.3018f, 32.582f)
            )
            val o = arrayOf(
                floatArrayOf(-5.0201f, 21.1179f),
                floatArrayOf(-3.8059f, 13.2556f),
                floatArrayOf(-17.6986f, 0f),
                floatArrayOf(0f, -16.0296f),
                floatArrayOf(0f, 0f)
            )
            val inn = arrayOf(
                floatArrayOf(0f, 0f),
                floatArrayOf(-21.7251f, 1.8331f),
                floatArrayOf(14.6625f, -0.0002f),
                floatArrayOf(0.0001f, 17.446f),
                floatArrayOf(-15.6952f, 2.0458f)
            )

            val path = Path().apply {
                moveTo(v[0][0] * scale, v[0][1] * scale)
                for (i in 0 until 4) {
                    val p0 = v[i]
                    val p1 = v[i + 1]
                    cubicTo(
                        (p0[0] + o[i][0]) * scale,
                        (p0[1] + o[i][1]) * scale,
                        (p1[0] + inn[i + 1][0]) * scale,
                        (p1[1] + inn[i + 1][1]) * scale,
                        p1[0] * scale,
                        p1[1] * scale
                    )
                }
                close()
            }

            // Gradient fill: warm yellow matching WeatherIcon MoonIcon
            val gradient = LinearGradient(
                0f, 32f * scale,
                0f, 96f * scale,
                intArrayOf(Color.parseColor("#FFFFD54F"), Color.parseColor("#FFFFCA28")),
                null,
                Shader.TileMode.CLAMP
            )
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = gradient
            }
            canvas.drawPath(path, fillPaint)

            // Gold stroke matching original
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.parseColor("#FFF9AF03")
                strokeWidth = 1f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(path, strokePaint)

            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Build a weather-appropriate gradient background bitmap.
     *
     * Uses 4 visual layers for natural sky simulation:
     *   1. Radial gradient base center biased upward for natural sky depth
     *   2. Top highlight focused light simulating zenith sun / moon glow
     *   3. Bottom shadow subtle ground-level darkening for contrast
     *   4. Rain streaks (optional) decorative rain lines for rainy weather
     */
    private fun buildGradientBitmap(context: Context, skycon: String?, width: Int, height: Int, isDay: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = context.resources.displayMetrics.density
        val radius = 18f * density
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val isRain = isRainSkycon(skycon)
        val gradientColors = weatherWidgetGradient(skycon, isDay)

        // Layer 1: Radial gradient base
        val cx = width * 0.48f
        val cy = height * 0.32f
        val gradRadius = maxOf(width, height) * 0.82f
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, gradRadius, gradientColors, null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        // Layer 2: Top highlight
        val highlightAlpha = if (isDay) 48 else 22
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height * 0.38f,
                intArrayOf(Color.argb(highlightAlpha, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        // Layer 3: Bottom shadow
        val shadowAlpha = if (isDay) 38 else 56
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height * 0.55f, 0f, height.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(shadowAlpha, 0, 0, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, this)
        }

        if (isRain) {
            drawStaticRainStreaks(canvas, rect, radius, density, isDay)
        }

        return bitmap
    }

    private fun isRainSkycon(skycon: String?): Boolean {
        return skycon?.let {
            it.contains("RAIN") || it.contains("STORM") || it == "THUNDER_SHOWER"
        } == true
    }

    private fun weatherWidgetGradient(skycon: String?, isDay: Boolean): IntArray {
        return WeatherUtils.getWeatherGradient(skycon, isDay).map { it.toArgb() }.toIntArray()
    }

    private fun drawStaticRainStreaks(canvas: Canvas, rect: RectF, radius: Float, density: Float, isDay: Boolean) {
        val width = rect.width()
        val height = rect.height()
        val mask = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        val fan = Path().apply {
            moveTo(width, 0f)
            arcTo(RectF(width - 142f * density, -22f * density, width + 42f * density, 162f * density), -92f, -110f, false)
            lineTo(width, height * 0.54f)
            close()
        }
        fan.op(mask, Path.Op.INTERSECT)

        canvas.save()
        canvas.clipPath(fan)
        val streaks = arrayOf(
            floatArrayOf(width - 18f * density, 8f * density, width - 48f * density, 46f * density, 1.35f * density),
            floatArrayOf(width - 42f * density, 8f * density, width - 76f * density, 54f * density, 1.1f * density),
            floatArrayOf(width - 64f * density, 18f * density, width - 94f * density, 56f * density, 1.0f * density),
            floatArrayOf(width - 30f * density, 44f * density, width - 66f * density, 88f * density, 1.15f * density)
        )
        streaks.forEachIndexed { index, streak ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = streak[4]
                strokeCap = Paint.Cap.ROUND
                shader = LinearGradient(
                    streak[0], streak[1], streak[2], streak[3],
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.argb(if (isDay) 84 - index * 10 else 72 - index * 8, 224, 242, 255),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawLine(streak[0], streak[1], streak[2], streak[3], this)
            }
        }
        canvas.restore()
    }

    fun updateMediumLoading(context: Context, cityName: String? = null) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetMediumProvider::class.java))
            if (ids.isEmpty()) {
                FileLogger.w(TAG, "updateMediumLoading: 无活跃 widget，跳过渲染")
                return
            }

            val cityText = shortenLocation(cityName ?: "定位中...")
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_medium)
                views.setTextViewText(R.id.widget_city, cityText)
                views.setTextViewText(R.id.widget_temp, "--")

                // Set placeholder for wind, humidity, AQI, UV
                views.setTextViewText(R.id.widget_wind, "--")
                views.setTextViewText(R.id.widget_humidity, "--")
                views.setTextViewText(R.id.widget_aqi, "--")
                views.setTextViewText(R.id.widget_uv, "--")

                // Set placeholder for weather
                views.setTextViewText(R.id.widget_weather_desc, "")

                // Set placeholder for daily forecast items
                for (i in 1..5) {
                    val dayNameId = context.resources.getIdentifier("widget_day_name_$i", "id", context.packageName)
                    val dayTempId = context.resources.getIdentifier("widget_day_temp_$i", "id", context.packageName)
                    views.setTextViewText(dayNameId, "--")
                    views.setTextViewText(dayTempId, "--")
                }

                val (w, h) = getWidgetSizePx(context, widgetId)
                val sizedBg = buildGradientBitmap(context, null, w, h)
                views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                views.setBoolean(R.id.widget_container, "setClipToOutline", true)
                views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_rounded_bg)

                val intent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pending)
                manager.updateAppWidget(widgetId, views)
            }
            FileLogger.i(TAG, "updateMediumLoading: 渲染定位占位态完成, widgetCount=${ids.size}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "updateMediumLoading: 渲染异常", e)
        }
    }

    fun updateMediumAll(context: Context, weather: WeatherResponse?, cityName: String?) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidgetMediumProvider::class.java))
            if (ids.isEmpty()) {
                FileLogger.w(TAG, "updateMediumAll: 无活跃 widget，跳过渲染")
                return
            }

            // Check premium status
            val membershipRepository = MembershipRepository(context)
            val isPremium = membershipRepository.isPremium.value
            
            // If not premium, show locked state
            if (!isPremium) {
                FileLogger.i(TAG, "updateMediumAll: 用户未付费，显示锁定状态")
                ids.forEach { widgetId ->
                    try {
                        val views = RemoteViews(context.packageName, R.layout.widget_medium)
                        views.setTextViewText(R.id.widget_city, "需要付费解锁")
                        views.setTextViewText(R.id.widget_temp, "")
                        views.setTextViewText(R.id.widget_wind, "此功能需要付费解锁")
                        views.setTextViewText(R.id.widget_humidity, "")
                        views.setTextViewText(R.id.widget_aqi, "")
                        views.setTextViewText(R.id.widget_uv, "")
                        views.setBoolean(R.id.widget_container, "setClipToOutline", true)
                        views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_rounded_bg)
                        
                        val intent = Intent(context, MainActivity::class.java)
                        val pending = PendingIntent.getActivity(
                            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_container, pending)
                        manager.updateAppWidget(widgetId, views)
                    } catch (_: Exception) {}
                }
                FileLogger.i(TAG, "updateMediumAll: 渲染锁定状态完成, widgetCount=${ids.size}")
                return
            }

            val realtime = weather?.result?.realtime
            val daily = weather?.result?.daily
            val skycon = realtime?.skycon
            val isDay = WeatherUtils.isCurrentlyDay(daily)
            FileLogger.i(TAG, "updateMediumAll: isDay=$isDay, daily=${daily != null}, astro=${daily?.astro?.size}")
            val tempText = WeatherUtils.formatTemperature(realtime?.temperature)
            val cityText = shortenLocation(cityName ?: "--")

            // Get weather info (contains icon name and description)
            val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
            val weatherIcon = weatherInfo.icon
            val weatherDesc = weatherInfo.description

            // Get wind direction and level ("南风 2级" format)
            val windSpeed = realtime?.wind?.speed
            val windDirection = realtime?.wind?.direction
            val windText = getWindDirectionText(windDirection, windSpeed)

            // Get humidity
            val humidity = realtime?.humidity
            val humidityText = if (humidity != null) "湿度 ${(humidity * 100).toInt()}%" else "湿度 --"

            // Get AQI
            val aqi = realtime?.air_quality?.aqi?.chn
            val aqiText = if (aqi != null) {
                val aqiDesc = when {
                    aqi <= 50 -> "优"
                    aqi <= 100 -> "良"
                    aqi <= 150 -> "轻度"
                    aqi <= 200 -> "中度"
                    aqi <= 300 -> "重度"
                    else -> "严重"
                }
                "空气 $aqiDesc"
            } else "空气 --"

            // Get UV index
            val uvIndex = realtime?.life_index?.ultraviolet?.desc
            val uvText = if (!uvIndex.isNullOrBlank()) "紫外线 $uvIndex" else "紫外线 --"

            // Get daily forecast
            val dailyTemps = daily?.temperature
            val dailySkycons = daily?.skycon

            // Find today's index in the daily data
            // API returns dates like "2026-07-19T00:00+08:00", so we need to match by prefix
            val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val todayTempIndex = dailyTemps?.indexOfFirst { it.date?.startsWith(todayDate) == true }?.coerceAtLeast(0) ?: 0
            val todaySkyconIndex = dailySkycons?.indexOfFirst { it.date?.startsWith(todayDate) == true }?.coerceAtLeast(0) ?: 0
            FileLogger.i(TAG, "updateMediumAll: todayDate=$todayDate, todayTempIndex=$todayTempIndex, todaySkyconIndex=$todaySkyconIndex, firstTempDate=${dailyTemps?.firstOrNull()?.date}")

            // White color for rain icons
            val whitePrecipColor = android.graphics.Color.WHITE

            ids.forEach { widgetId ->
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_medium)

                    // Top left: city and temperature
                    views.setTextViewText(R.id.widget_city, cityText)
                    views.setTextViewText(R.id.widget_temp, tempText)

                    // Middle: wind, humidity, AQI, UV
                    views.setTextViewText(R.id.widget_wind, windText)
                    views.setTextViewText(R.id.widget_humidity, humidityText)
                    views.setTextViewText(R.id.widget_aqi, aqiText)
                    views.setTextViewText(R.id.widget_uv, uvText)

                    // Right: weather icon and description (with white rain)
                    val weatherIconBitmap = renderIcon(context, weatherIcon, whitePrecipColor)
                    if (weatherIconBitmap != null) {
                        views.setImageViewBitmap(R.id.widget_weather_icon, weatherIconBitmap)
                    }
                    views.setTextViewText(R.id.widget_weather_desc, weatherDesc)

                    // Bottom: 5-day forecast with min/max temperature
                    val calendar = Calendar.getInstance()
                    val today = calendar.get(Calendar.DAY_OF_WEEK)
                    val dayNames = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

                    for (i in 0 until minOf(5, dailyTemps?.size ?: 0)) {
                        val dayIndex = i + 1
                        val dayNameId = context.resources.getIdentifier("widget_day_name_$dayIndex", "id", context.packageName)
                        val dayIconId = context.resources.getIdentifier("widget_day_icon_$dayIndex", "id", context.packageName)
                        val dayTempId = context.resources.getIdentifier("widget_day_temp_$dayIndex", "id", context.packageName)

                        // Get day name
                        val dayName = if (i == 0) {
                            "今天"
                        } else {
                            val dayOfWeek = (today + i - 1) % 7
                            dayNames[dayOfWeek]
                        }

                        // Get icon (convert skycon to icon name, with white rain)
                        val daySkycon = dailySkycons?.getOrNull(todaySkyconIndex + i)?.value
                        val dayWeatherInfo = if (daySkycon != null) WeatherUtils.getWeatherInfo(daySkycon) else null
                        val dayIcon = dayWeatherInfo?.icon ?: "overcast"
                        val iconBitmap = renderIcon(context, dayIcon, whitePrecipColor)

                        // Get temperature (min/max for the day)
                        val minTemp = dailyTemps?.getOrNull(todayTempIndex + i)?.min
                        val maxTemp = dailyTemps?.getOrNull(todayTempIndex + i)?.max
                        val tempStr = if (minTemp != null && maxTemp != null) {
                            "${WeatherUtils.formatTemperature(minTemp)} ${WeatherUtils.formatTemperature(maxTemp)}"
                        } else {
                            WeatherUtils.formatTemperature(maxTemp)
                        }

                        views.setTextViewText(dayNameId, dayName)
                        if (iconBitmap != null) {
                            views.setImageViewBitmap(dayIconId, iconBitmap)
                        }
                        views.setTextViewText(dayTempId, tempStr)
                    }

                    val (w, h) = getWidgetSizePx(context, widgetId)
                    val sizedBg = buildGradientBitmap(context, skycon, w, h, isDay)
                    views.setImageViewBitmap(R.id.widget_bg, sizedBg)
                    views.setBoolean(R.id.widget_container, "setClipToOutline", true)
                    views.setInt(R.id.widget_container, "setBackgroundResource", R.drawable.widget_rounded_bg)

                    val intent = Intent(context, MainActivity::class.java)
                    val pending = PendingIntent.getActivity(
                        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_container, pending)
                    manager.updateAppWidget(widgetId, views)
                } catch (_: Exception) {}
            }
            FileLogger.i(TAG, "updateMediumAll: 渲染完成, widgetCount=${ids.size}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "updateMediumAll: 渲染异常", e)
        }
    }

    private fun getWindDirectionText(direction: Double?, speed: Double?): String {
        val dir = when {
            direction == null -> ""
            direction < 22.5 || direction >= 337.5 -> "北风"
            direction < 67.5 -> "东北风"
            direction < 112.5 -> "东风"
            direction < 157.5 -> "东南风"
            direction < 202.5 -> "南风"
            direction < 247.5 -> "西南风"
            direction < 292.5 -> "西风"
            else -> "西北风"
        }
        val level = if (speed != null) {
            val l = when {
                speed < 1 -> "0"
                speed < 6 -> "1"
                speed < 12 -> "2"
                speed < 20 -> "3"
                speed < 29 -> "4"
                speed < 39 -> "5"
                speed < 50 -> "6"
                speed < 62 -> "7"
                speed < 75 -> "8"
                speed < 89 -> "9"
                speed < 103 -> "10"
                speed < 117 -> "11"
                else -> "12"
            }
            " ${l}级"
        } else ""
        return "$dir$level"
    }

    fun update4x2Loading(context: Context, cityName: String? = null) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidget4x2Provider::class.java))
            if (ids.isEmpty()) {
                FileLogger.w(TAG, "update4x2Loading: 无活跃 widget，跳过渲染")
                return
            }

            val cityText = shortenLocation(cityName ?: "定位中...")

            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_4x2)
                views.setTextViewText(R.id.widget_city, cityText)
                views.setTextViewText(R.id.widget_weather_desc, "")
                views.setTextViewText(R.id.widget_temp_range, "--")

                // Set placeholder for daily forecast items
                for (i in 1..3) {
                    val dayNameId = context.resources.getIdentifier("widget_day_name_$i", "id", context.packageName)
                    val dayTempId = context.resources.getIdentifier("widget_day_temp_$i", "id", context.packageName)
                    views.setTextViewText(dayNameId, "--")
                    views.setTextViewText(dayTempId, "--")
                }

                views.setInt(R.id.widget_container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)

                val intent = Intent(context, MainActivity::class.java)
                val pending = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pending)
                manager.updateAppWidget(widgetId, views)
            }
            FileLogger.i(TAG, "update4x2Loading: 渲染定位占位态完成, widgetCount=${ids.size}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "update4x2Loading: 渲染异常", e)
        }
    }

    fun update4x2All(context: Context, weather: WeatherResponse?, cityName: String?) {
        try {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WeatherWidget4x2Provider::class.java))
            if (ids.isEmpty()) {
                FileLogger.w(TAG, "update4x2All: 无活跃 widget，跳过渲染")
                return
            }

            // Check premium status
            val membershipRepository = MembershipRepository(context)
            val isPremium = membershipRepository.isPremium.value
            
            // If not premium, show locked state
            if (!isPremium) {
                FileLogger.i(TAG, "update4x2All: 用户未付费，显示锁定状态")
                ids.forEach { widgetId ->
                    try {
                        val views = RemoteViews(context.packageName, R.layout.widget_4x2)
                        views.setTextViewText(R.id.widget_city, "需要付费解锁")
                        views.setTextViewText(R.id.widget_weather_desc, "此功能需要付费解锁")
                        views.setTextViewText(R.id.widget_temp_range, "")
                        views.setInt(R.id.widget_container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                        
                        val intent = Intent(context, MainActivity::class.java)
                        val pending = PendingIntent.getActivity(
                            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_container, pending)
                        manager.updateAppWidget(widgetId, views)
                    } catch (_: Exception) {}
                }
                FileLogger.i(TAG, "update4x2All: 渲染锁定状态完成, widgetCount=${ids.size}")
                return
            }

            val realtime = weather?.result?.realtime
            val daily = weather?.result?.daily
            val skycon = realtime?.skycon
            val cityText = shortenLocation(cityName ?: "--")

            // Get weather info
            val weatherInfo = WeatherUtils.getWeatherInfo(skycon)
            val weatherIcon = weatherInfo.icon
            val weatherDesc = weatherInfo.description

            // Get daily temperature range for today
            val dailyTemps = daily?.temperature
            val dailySkycons = daily?.skycon
            val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val todayTempIndex = dailyTemps?.indexOfFirst { it.date?.startsWith(todayDate) == true }?.coerceAtLeast(0) ?: 0
            val todaySkyconIndex = dailySkycons?.indexOfFirst { it.date?.startsWith(todayDate) == true }?.coerceAtLeast(0) ?: 0

            val todayMinTemp = dailyTemps?.getOrNull(todayTempIndex)?.min
            val todayMaxTemp = dailyTemps?.getOrNull(todayTempIndex)?.max
            val tempRangeText = if (todayMinTemp != null && todayMaxTemp != null) {
                "${WeatherUtils.formatTemperature(todayMinTemp)} ${WeatherUtils.formatTemperature(todayMaxTemp)}"
            } else "--"

            // White color for rain icons
            val whitePrecipColor = android.graphics.Color.WHITE

            // Day names for forecast
            val dayNames = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

            FileLogger.i(TAG, "update4x2All: 渲染数据 — city=$cityText, weather=$weatherDesc, tempRange=$tempRangeText")

            ids.forEach { widgetId ->
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_4x2)

                    // Top left: City (Clock and Date are TextClock, auto-updated)
                    views.setTextViewText(R.id.widget_city, cityText)

                    // Top right: Weather icon, description, temp range
                    val weatherIconBitmap = renderIcon(context, weatherIcon, whitePrecipColor)
                    if (weatherIconBitmap != null) {
                        views.setImageViewBitmap(R.id.widget_weather_icon, weatherIconBitmap)
                    }
                    views.setTextViewText(R.id.widget_weather_desc, weatherDesc)
                    views.setTextViewText(R.id.widget_temp_range, tempRangeText)

                    // Bottom: 3-day forecast
                    val calendar = java.util.Calendar.getInstance()
                    val today = calendar.get(java.util.Calendar.DAY_OF_WEEK)

                    for (i in 0 until minOf(3, dailyTemps?.size ?: 0)) {
                        val dayIndex = i + 1
                        val dayNameId = context.resources.getIdentifier("widget_day_name_$dayIndex", "id", context.packageName)
                        val dayIconId = context.resources.getIdentifier("widget_day_icon_$dayIndex", "id", context.packageName)
                        val dayTempId = context.resources.getIdentifier("widget_day_temp_$dayIndex", "id", context.packageName)

                        // Get day name
                        val dayName = if (i == 0) {
                            "今天"
                        } else {
                            val dayOfWeekIndex = (today + i - 1) % 7
                            dayNames[dayOfWeekIndex]
                        }

                        // Get icon
                        val daySkycon = dailySkycons?.getOrNull(todaySkyconIndex + i)?.value
                        val dayWeatherInfo = if (daySkycon != null) WeatherUtils.getWeatherInfo(daySkycon) else null
                        val dayIcon = dayWeatherInfo?.icon ?: "overcast"
                        val iconBitmap = renderIcon(context, dayIcon, whitePrecipColor)

                        // Get temperature
                        val minTemp = dailyTemps?.getOrNull(todayTempIndex + i)?.min
                        val maxTemp = dailyTemps?.getOrNull(todayTempIndex + i)?.max
                        val tempStr = if (minTemp != null && maxTemp != null) {
                            "${WeatherUtils.formatTemperature(minTemp)} ${WeatherUtils.formatTemperature(maxTemp)}"
                        } else {
                            WeatherUtils.formatTemperature(maxTemp)
                        }

                        views.setTextViewText(dayNameId, dayName)
                        if (iconBitmap != null) {
                            views.setImageViewBitmap(dayIconId, iconBitmap)
                        }
                        views.setTextViewText(dayTempId, tempStr)
                    }

                    views.setInt(R.id.widget_container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)

                    val intent = Intent(context, MainActivity::class.java)
                    val pending = PendingIntent.getActivity(
                        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_container, pending)
                    manager.updateAppWidget(widgetId, views)
                } catch (_: Exception) {}
            }
            FileLogger.i(TAG, "update4x2All: 渲染完成, widgetCount=${ids.size}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "update4x2All: 渲染异常", e)
        }
    }

}

