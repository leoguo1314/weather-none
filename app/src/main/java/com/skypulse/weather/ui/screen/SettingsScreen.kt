package com.skypulse.weather.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.skypulse.weather.ui.components.LucideIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.skypulse.weather.BuildConfig
import com.skypulse.weather.data.ActivationResult
import com.skypulse.weather.data.DebugWeatherPreset
import com.skypulse.weather.data.ThemeMode
import com.skypulse.weather.data.WeatherSettings
import com.skypulse.weather.ui.components.FreeUserCard
import com.skypulse.weather.ui.components.MembershipDialog
import com.skypulse.weather.ui.components.MembershipRole
import com.skypulse.weather.ui.components.RoleCompareCard
import com.skypulse.weather.ui.components.VipBadge
import com.skypulse.weather.ui.components.VipStatusCard
import com.skypulse.weather.ui.components.VipUpgradeButton
import com.skypulse.weather.ui.theme.*
import com.skypulse.weather.viewmodel.UpdateCheckResult

/** 设置页内部子页面（局部导航，不新增全局 AppScreen） */
private enum class SettingsSubPage { MembershipCompare, ContactAuthor }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    updateState: UpdateCheckResult?,
    onClearUpdateState: () -> Unit,
    settings: WeatherSettings,
    onRainAlertChange: (Boolean) -> Unit,
    onWarningAlertChange: (Boolean) -> Unit,
    onTempChangeAlertChange: (Boolean) -> Unit,
    onWindAlertChange: (Boolean) -> Unit,
    onTyphoonAlertChange: (Boolean) -> Unit,
    onShowHourlyAqiChange: (Boolean) -> Unit,
    onShowHourlyUvChange: (Boolean) -> Unit,
    onShowHourlyWindChange: (Boolean) -> Unit,
    onShowHourlyWindGustChange: (Boolean) -> Unit,
    onShowCardDetailChange: (Boolean) -> Unit,
    onShowCardSunriseSunsetChange: (Boolean) -> Unit,
    onShowCardMinutelyChange: (Boolean) -> Unit,
    onShowCardTyphoonChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onVersionDoubleTap: () -> Boolean = { false },
    onDeveloperModeToggle: (Boolean) -> Unit = {},
    onDebugWeatherPresetChange: (DebugWeatherPreset?) -> Unit = {},
    isPremium: Boolean = false,
    activatedAt: Long = 0L,
    deviceId: String = "",
    onActivateCode: (String) -> ActivationResult = { _ -> ActivationResult.INVALID_CODE }
) {
    val context = LocalContext.current
    val page = LocalSecondaryPageTheme.current
    var showMembershipDialog by remember { mutableStateOf(false) }

    // 设置页内部子页面导航（权益对比 / 联系作者）
    var subPage by remember { mutableStateOf<SettingsSubPage?>(null) }

    // 开发者选项彩蛋：三连击版本号开启（防止误触），500ms 内连续 3 次点击
    var versionTapCount by remember { mutableStateOf(0) }
    var lastVersionTapTime by remember { mutableLongStateOf(0L) }

    // 开发者选项：天气背景调试展开状态
    var weatherDebugExpanded by remember { mutableStateOf(false) }

    val isChecking = updateState is UpdateCheckResult.Checking
    // 仅在检查更新期间驱动旋转动画；停止检查后协程自动取消，不再每帧空转耗电
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isChecking) {
        if (!isChecking) return@LaunchedEffect
        rotation = 0f
        var lastFrameTime = 0L
        while (true) {
            withFrameMillis { frameTime ->
                if (lastFrameTime > 0L) {
                    val delta = (frameTime - lastFrameTime) / 1000f
                    rotation = (rotation + delta * 360f) % 360f
                }
                lastFrameTime = frameTime
            }
        }
    }

    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateCheckResult.UpToDate -> {
                Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                onClearUpdateState()
            }
            is UpdateCheckResult.Error -> {
                Toast.makeText(context, updateState.message, Toast.LENGTH_SHORT).show()
                onClearUpdateState()
            }
            else -> {}
        }
    }
    if (showMembershipDialog) {
        MembershipDialog(
            onDismiss = { showMembershipDialog = false },
            onActivate = onActivateCode,
            deviceId = deviceId
        )
    }

    // 子页面返回：系统返回键先退回设置主列表
    BackHandler(enabled = subPage != null) {
        subPage = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(page.background)
            .navigationBarsPadding()
    ) {
        AnimatedContent(
            targetState = subPage,
            transitionSpec = {
                // 与设置/城市管理/预警详情页相同的滑入淡入过渡
                val direction = if (targetState != null) 1 else -1
                (slideInHorizontally(
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) { width -> direction * width / 8 } +
                    fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 40, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.985f, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    ) { width -> -direction * width / 16 } +
                        fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)) +
                        scaleOut(targetScale = 0.995f, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)))
            },
            label = "settingsSubPage"
        ) { target ->
            when (target) {
                SettingsSubPage.MembershipCompare -> MembershipCompareScreen(
                    isPremium = isPremium,
                    onUpgradeClick = { showMembershipDialog = true },
                    onBack = { subPage = null }
                )
                SettingsSubPage.ContactAuthor -> ContactAuthorScreen(
                    onBack = { subPage = null }
                )
                null -> {
                    Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("设置", color = page.textPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        LucideIcon(
                            name = "arrow-left",
                            contentDescription = "返回",
                            tint = page.backArrow
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = page.background
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 会员卡片（统一形态：非会员引导卡 / 会员金色状态卡）
                // 点击右侧按钮进入权益对比页
                Box(modifier = Modifier.padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)) {
                    if (isPremium) {
                        VipStatusCard(
                            deviceId = deviceId,
                            onMembershipCenter = { subPage = SettingsSubPage.MembershipCompare }
                        )
                    } else {
                        FreeUserCard(
                            onUpgrade = { subPage = SettingsSubPage.MembershipCompare }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Developer options（彩蛋开启，点击会员卡片 7 次）
                if (settings.developerModeEnabled) {
                    SectionHeader("开发者选项")
                    IosCard {
                        SimpleItem(
                            title = "天气背景调试",
                            subtitle = buildAnnotatedString {
                                append("当前：")
                                withStyle(SpanStyle(color = page.accentBlue, fontWeight = FontWeight.Medium)) {
                                    append(settings.debugWeatherPreset?.displayName ?: "自动（跟随实际天气）")
                                }
                            },
                            trailing = {
                                LucideIcon(
                                    name = "chevron-down",
                                    contentDescription = null,
                                    size = 18.dp,
                                    tint = page.textSecondary,
                                    modifier = Modifier.rotate(if (weatherDebugExpanded) 180f else 0f)
                                )
                            },
                            onClick = { weatherDebugExpanded = !weatherDebugExpanded }
                        )
                        if (weatherDebugExpanded) {
                            IosDivider()
                            RadioItem(
                                title = "自动（跟随实际天气）",
                                selected = settings.debugWeatherPreset == null,
                                onClick = { onDebugWeatherPresetChange(null) }
                            )
                            DebugWeatherPreset.entries.forEach { preset ->
                                IosDivider()
                                RadioItem(
                                    title = preset.displayName,
                                    selected = settings.debugWeatherPreset == preset,
                                    onClick = { onDebugWeatherPresetChange(preset) }
                                )
                            }
                        }
                        IosDivider()
                        SimpleItem(
                            title = "版本信息",
                            subtitle = AnnotatedString("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
                            onClick = { }
                        )
                        IosDivider()
                        SimpleItem(
                            title = "关闭开发者选项",
                            titleColor = page.textSecondary,
                            trailing = {
                                LucideIcon(
                                    name = "power",
                                    contentDescription = null,
                                    size = 18.dp,
                                    tint = page.textSecondary
                                )
                            },
                            onClick = { onDeveloperModeToggle(false) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Appearance settings（仅城市管理/预警详情/设置三页生效，主页与小组件不变）
                SectionHeader("外观")
                IosCard {
                    RadioItem(
                        title = "浅色",
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                    )
                    IosDivider()
                    RadioItem(
                        title = "深色",
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) }
                    )
                    IosDivider()
                    RadioItem(
                        title = "跟随系统",
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Notification settings
                SectionHeader("通知设置")
                IosCard {
                    ToggleItem(
                        title = "短临降水提醒",
                        checked = settings.rainAlert,
                        onCheckedChange = onRainAlertChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "气象预警",
                        checked = settings.warningAlert,
                        onCheckedChange = onWarningAlertChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem("变温提醒", settings.tempChangeAlert, onTempChangeAlertChange)
                    IosDivider()
                    ToggleItem("大风提醒", settings.windAlert, onWindAlertChange)
                    IosDivider()
                    ToggleItem(
                        title = "极端天气",
                        checked = settings.typhoonAlert,
                        onCheckedChange = onTyphoonAlertChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Home display settings（逐小时 + 卡片，合并为一张卡）
                SectionHeader("首页显示")
                IosCard {
                    ToggleItem(
                        title = "逐小时空气质量",
                        checked = settings.showHourlyAqi,
                        onCheckedChange = onShowHourlyAqiChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "逐小时紫外线",
                        checked = settings.showHourlyUv,
                        onCheckedChange = onShowHourlyUvChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "逐小时风力",
                        checked = settings.showHourlyWind,
                        onCheckedChange = onShowHourlyWindChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "逐小时阵风",
                        checked = settings.showHourlyWindGust,
                        onCheckedChange = onShowHourlyWindGustChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "分钟级降水",
                        checked = settings.showCardMinutely,
                        onCheckedChange = onShowCardMinutelyChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "气象详情",
                        checked = settings.showCardDetail,
                        onCheckedChange = onShowCardDetailChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "日出日落",
                        checked = settings.showCardSunriseSunset,
                        onCheckedChange = onShowCardSunriseSunsetChange,
                        locked = !isPremium,
                        onLockedClick = { showMembershipDialog = true }
                    )
                    IosDivider()
                    ToggleItem(
                        title = "台风雷达图",
                        checked = settings.showCardTyphoon,
                        onCheckedChange = onShowCardTyphoonChange
                        // 免费用户和会员均解锁
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // About section
                SectionHeader("关于")
                IosCard {
                    SimpleItem(
                        title = "检查更新",
                        trailing = {
                            if (isChecking) {
                                // 检查更新期间：版本号位置换成旋转图标
                                LucideIcon(
                                    name = "refresh-cw",
                                    contentDescription = "正在检查更新",
                                    size = 20.dp,
                                    tint = page.textSecondary,
                                    modifier = Modifier.rotate(rotation)
                                )
                            } else {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = page.textSecondary,
                                    modifier = Modifier.pointerInput(Unit) {
                                        // 开发者选项彩蛋：三连击版本号开启（防止误触）
                                        detectTapGestures(
                                            onTap = {
                                                val now = System.currentTimeMillis()
                                                if (now - lastVersionTapTime > 500L) {
                                                    versionTapCount = 1
                                                } else {
                                                    versionTapCount++
                                                }
                                                lastVersionTapTime = now
                                                if (versionTapCount >= 3) {
                                                    versionTapCount = 0
                                                    lastVersionTapTime = 0L
                                                    if (onVersionDoubleTap()) {
                                                        Toast.makeText(context, "已开启开发者选项", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "开发者选项已开启", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        },
                        onClick = { if (!isChecking) onCheckUpdate() }
                    )
                    IosDivider()
                    SimpleItem("联系作者") {
                        subPage = SettingsSubPage.ContactAuthor
                    }
                }

                // Update result
                if (updateState is UpdateCheckResult.UpdateAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    IosCard {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, updateState.url.toUri())
                                    context.startActivity(intent)
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = "前往下载 v${updateState.version}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = page.accentBlue
                            )
                        }
                    }
                }

                // 底部留出 32dp：与预警详情/台风路径页底部间距保持一致（navigationBarsPadding 之上再抬 32dp）
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
            }
        }
        }
    }
}

@Composable
private fun IosCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val page = LocalSecondaryPageTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            .clip(RoundedCornerShape(SkyPulseDesignSystem.Radius.settingsCard))
            .background(page.cardBackground),
        content = content
    )
}


@Composable
private fun IosDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = SkyPulseDesignSystem.Border.hairline,
        color = LocalSecondaryPageTheme.current.divider
    )
}
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = LocalSecondaryPageTheme.current.textSecondary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    locked: Boolean = false,
    onLockedClick: (() -> Unit)? = null
) {
    val page = LocalSecondaryPageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SkyPulseDesignSystem.TouchTarget.listRow)
            .clickable {
                if (locked) onLockedClick?.invoke()
                else onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (locked) page.textSecondary else page.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (locked) {
            LucideIcon(
                name = "lock",
                contentDescription = "会员功能",
                size = 18.dp,
                tint = page.textSecondary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Switch(
            checked = if (locked) false else checked,
            onCheckedChange = if (locked) null else onCheckedChange,
            enabled = !locked,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = page.accentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = page.switchOffTrack,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedThumbColor = Color.White,
                disabledCheckedTrackColor = page.switchOffTrack,
                disabledUncheckedThumbColor = Color.White,
                disabledUncheckedTrackColor = page.switchOffTrack
            )
        )
    }
}


@Composable
private fun RadioItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val page = LocalSecondaryPageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SkyPulseDesignSystem.TouchTarget.listRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = page.textPrimary,
            modifier = Modifier.weight(1f)
        )
        // 圆形单选勾选框
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (selected) page.accentBlue else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // 外圈描边
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                drawCircle(
                    color = if (selected) page.accentBlue else page.textSecondary.copy(alpha = 0.6f),
                    radius = radius,
                    center = this.center,
                    style = Stroke(width = strokeWidth)
                )
            }
            // 选中时内部填充圆点
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}


@Composable
private fun SimpleItem(
    title: String,
    subtitle: AnnotatedString? = null,
    titleColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val page = LocalSecondaryPageTheme.current
    val resolvedTitleColor = if (titleColor == Color.Unspecified) page.textPrimary else titleColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = SkyPulseDesignSystem.TouchTarget.listRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = resolvedTitleColor
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = page.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * 权益对比页：左侧非会员权益 / 右侧会员权益，底部升级大按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembershipCompareScreen(
    isPremium: Boolean,
    onUpgradeClick: () -> Unit,
    onBack: () -> Unit
) {
    val page = LocalSecondaryPageTheme.current
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("权益对比", color = page.textPrimary, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    LucideIcon(
                        name = "arrow-left",
                        contentDescription = "返回",
                        tint = page.backArrow
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = page.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 身份对比卡（非会员 vs 永久会员）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoleCompareCard(
                    role = MembershipRole.Free,
                    selected = !isPremium,
                    modifier = Modifier.weight(1f)
                )
                RoleCompareCard(
                    role = MembershipRole.Premium,
                    selected = isPremium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 权益对比表
            IosCard {
                CompareHeaderRow()
                IosDivider()
                CompareRow("免广告", free = true)
                IosDivider()
                CompareRow("天气动效", free = true)
                IosDivider()
                CompareRow("台风雷达图", free = true)
                IosDivider()
                CompareRow("多城市天气", free = false)
                IosDivider()
                CompareRow("全球地区天气", free = false)
                IosDivider()
                CompareRow("街道/小区级定位", free = false)
                IosDivider()
                CompareRow("15日预报", free = false)
                IosDivider()
                CompareRow("AI天气校准", free = false)
                IosDivider()
                CompareRow("多功能小组件", free = false)
                IosDivider()
                CompareRow("短临降水提醒", free = false)
                IosDivider()
                CompareRow("气象预警", free = false)
                IosDivider()
                CompareRow("极端天气", free = false)
                IosDivider()
                CompareRow("逐小时空气质量", free = false)
                IosDivider()
                CompareRow("逐小时紫外线", free = false)
                IosDivider()
                CompareRow("逐小时风力", free = false)
                IosDivider()
                CompareRow("逐小时阵风", free = false)
                IosDivider()
                CompareRow("分钟级降水", free = false)
                IosDivider()
                CompareRow("气象详情", free = false)
                IosDivider()
                CompareRow("日出日落", free = false)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 底部大按钮：非会员「19.9升级永久会员」/ 会员「已解锁全部高级功能」（置灰）
            VipUpgradeButton(
                text = if (isPremium) "已解锁全部高级功能" else "19.9升级永久会员",
                enabled = !isPremium,
                onClick = onUpgradeClick,
                modifier = Modifier.padding(horizontal = SkyPulseDesignSystem.Spacing.screenHorizontal)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 权益对比表头：功能 / 非会员 / 会员 */
@Composable
private fun CompareHeaderRow() {
    val page = LocalSecondaryPageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "功能",
            style = MaterialTheme.typography.labelMedium,
            color = page.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "非会员",
            style = MaterialTheme.typography.labelMedium,
            color = page.textSecondary,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = "会员",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFB8860B),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(64.dp)
        )
    }
}

/** 权益对比行：标题 + 非会员列（✓/—）+ 会员列（金色✓） */
@Composable
private fun CompareRow(title: String, free: Boolean) {
    val page = LocalSecondaryPageTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SkyPulseDesignSystem.TouchTarget.listRow)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = page.textPrimary,
            modifier = Modifier.weight(1f)
        )
        // 非会员列
        Box(
            modifier = Modifier.width(64.dp),
            contentAlignment = Alignment.Center
        ) {
            if (free) {
                LucideIcon(
                    name = "check",
                    contentDescription = "免费可用",
                    size = 18.dp,
                    tint = page.accentGreen
                )
            } else {
                LucideIcon(
                    name = "x",
                    contentDescription = "会员专属",
                    size = 18.dp,
                    tint = page.textSecondary.copy(alpha = 0.5f)
                )
            }
        }
        // 会员列
        Box(
            modifier = Modifier.width(64.dp),
            contentAlignment = Alignment.Center
        ) {
            LucideIcon(
                name = "check",
                contentDescription = "会员已解锁",
                size = 18.dp,
                tint = Color(0xFFB8860B)
            )
        }
    }
}

/**
 * 联系作者页：条目式展示 QQ群 / 邮箱，点击复制
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactAuthorScreen(
    onBack: () -> Unit
) {
    val page = LocalSecondaryPageTheme.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("联系作者", color = page.textPrimary, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    LucideIcon(
                        name = "arrow-left",
                        contentDescription = "返回",
                        tint = page.backArrow
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = page.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            IosCard {
                SimpleItem(
                    title = "QQ一群",
                    subtitle = AnnotatedString("758426293"),
                    trailing = {
                        LucideIcon(
                            name = "copy",
                            contentDescription = "复制",
                            size = 18.dp,
                            tint = page.textSecondary
                        )
                    },
                    onClick = {
                        clipboardManager.setText(AnnotatedString("758426293"))
                        Toast.makeText(context, "QQ一群群号已复制", Toast.LENGTH_SHORT).show()
                    }
                )
                IosDivider()
                SimpleItem(
                    title = "QQ二群",
                    subtitle = AnnotatedString("1093158930"),
                    trailing = {
                        LucideIcon(
                            name = "copy",
                            contentDescription = "复制",
                            size = 18.dp,
                            tint = page.textSecondary
                        )
                    },
                    onClick = {
                        clipboardManager.setText(AnnotatedString("1093158930"))
                        Toast.makeText(context, "QQ二群群号已复制", Toast.LENGTH_SHORT).show()
                    }
                )
                IosDivider()
                SimpleItem(
                    title = "邮箱",
                    subtitle = AnnotatedString("1096005725@qq.com"),
                    trailing = {
                        LucideIcon(
                            name = "copy",
                            contentDescription = "复制",
                            size = 18.dp,
                            tint = page.textSecondary
                        )
                    },
                    onClick = {
                        clipboardManager.setText(AnnotatedString("1096005725@qq.com"))
                        Toast.makeText(context, "邮箱已复制", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "已付费成功后，可通过上述方式联系作者",
                style = MaterialTheme.typography.bodySmall,
                color = page.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

