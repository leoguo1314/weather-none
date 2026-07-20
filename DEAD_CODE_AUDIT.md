# SkyPulse Weather — 死代码/冗余代码全面排查报告

> 排查时间：2025 年 | 排查范围：`app/src/main/java/` 全部 Kotlin 源码

---

## 一、死代码文件（整个文件未被使用）

### 1. `ParticleRenderer.kt`

- **文件路径：** `app/src/main/java/com/skypulse/weather/ui/components/ParticleRenderer.kt`
- **严重程度：** 🔴 高
- **说明：** 该文件定义了 `ParticleRenderer` object，包含 `renderParticles()`、`renderRainDrop()`、`renderSnowflake()` 等 6 个渲染方法。**全局无任何引用**，项目已使用 `WeatherEffectOverlay.kt` 中的 Canvas 方案替代。
- **建议：** 删除整个文件

---

## 二、死代码函数

### 2. `WeatherEffectOverlay.kt` — 3 个未调用的粒子生成函数 + 2 个关联数据类

| 函数 / 数据类 | 行号 | 说明 |
|---|---|---|
| `generateSunnyParticles()` | L1119 | 返回 `List<LightSpot>`，从未被调用 |
| `generateFogParticles()` | L1233 | 返回 `List<FogP>`，从未被调用 |
| `generateClouds()` | L1248 | 返回 `List<Cloud>`，从未被调用 |
| `data class LightSpot` | L970 | 仅被 `generateSunnyParticles()` 使用，同属死代码 |
| `data class FogP` | L1022 | 仅被 `generateFogParticles()` 使用，同属死代码 |

- **建议：** 删除上述 5 项

### 3. `LocationManager.kt` — `requestSystemOrIpLocation()`

- **行号：** L331
- **说明：** 方法定义后全局无任何调用，内容直接委托给 `requestBestLocation()`
- **建议：** 删除

### 4. `WeatherWidgetUpdater.kt` — 2 个私有方法未使用

| 方法 | 行号 | 说明 |
|---|---|---|
| `getWindText()` | L706 | 仅定义一次，无调用 |
| `getWindDirectionOnly()` | L770 | 仅定义一次，无调用 |

- **建议：** 删除上述 2 个方法

### 5. `WidgetRefreshPolicy.kt` — 3 个函数未被外部调用

| 函数 | 行号 | 说明 |
|---|---|---|
| `hasMovedSignificantly()` | L8 | 仅内部互相调用，外部从未使用 |
| `isWeatherCacheStale()` | L12 | 仅内部互相调用，外部从未使用 |
| `shouldFetchWeather()` | L17 | 仅内部互相调用，外部从未使用 |

三个函数互相调用形成闭环，但项目外部仅使用了 `PERIODIC_REFRESH_MINUTES` 常量。

- **建议：** 删除上述 3 个方法，仅保留 `PERIODIC_REFRESH_MINUTES`

---

## 三、空壳模块 / 冗余 DI Module

### 6. `di/DataModule.kt` — 空 Module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
}
```

- **说明：** 内容完全为空，不提供任何绑定或依赖
- **建议：** 删除整个文件

---

## 四、WeatherTheme 中的冗余字段（只写不读）

### 7. `WeatherTheme` data class — 6 个字段仅被赋值，从未被读取

| 字段 | 行号 | 说明 |
|---|---|---|
| `cardFrostColor` | L13 | 在 `getWeatherTheme()` 中赋值 `Color.Transparent`，全局无读取 |
| `cardTopAlpha` | L14 | 始终赋值 `0f`，无读取 |
| `cardMidAlpha` | L15 | 始终赋值 `0f`，无读取 |
| `cardBottomAlpha` | L16 | 始终赋值 `0f`，无读取 |
| `cardBorderBrush` | L17 | 赋值 `Transparent` 渐变，无读取 |
| `cardBorderColor` | L18 | 赋值 `Color.Transparent`，无读取 |

`WeatherUtils.getWeatherTheme()` 中也存在对应的冗余局部变量 `cardFrostColor`、`topAlpha`、`midAlpha`、`bottomAlpha`、`borderBrush`、`borderColor`（L100-108），全部设为零值/透明后传入。

### 8. `WeatherTheme` 默认值中的 `pressedOverlay` 和 `disabledOverlay`（L24-25）

- 定义了默认值引用 `PressedOverlay` 和 `DisabledOverlay`
- 但这两个属性本身从未被读取

- **建议：** 清理上述 8 个字段及相关赋值逻辑

---

## 五、Color.kt 中的未使用颜色常量

### 表面颜色（未使用）

| 常量 | 行号 | 说明 |
|---|---|---|
| `CardSurfaceNight` | L12 | 仅定义，全局无引用 |
| `CardSurfaceNightLight` | L13 | 仅定义，全局无引用 |

### 交互状态颜色（仅作为 WeatherTheme 默认值，属性本身未被读取）

| 常量 | 行号 | 说明 |
|---|---|---|
| `PressedOverlay` | L37 | 仅作为默认值引用，无实际读取 |
| `DisabledOverlay` | L38 | 同上 |

### 背景渐变（未使用）

| 常量 | 行号 | 说明 |
|---|---|---|
| `NightFallbackGradient` | L172 | 仅定义，全局无引用 |

### iOS 风格常量（未使用）

| 常量 | 行号 | 说明 |
|---|---|---|
| `IosCardRadius` | L178 | 仅定义，全局无引用 |
| `IosToggleTrackOn` | L186 | 仅定义，全局无引用 |

### 强调色（未使用）

| 常量 | 行号 | 说明 |
|---|---|---|
| `HumidityBlue` | L211 | 仅定义，全局无引用 |

### 二级页面/对话框颜色组（整组未使用）

| 常量 | 行号 | 说明 |
|---|---|---|
| `SecondaryScreenGradient` | L197 | 仅定义，全局无引用 |
| `SecondaryPanel` | L199 | 仅定义，全局无引用 |
| `SecondaryPanelStrong` | L200 | 仅定义，全局无引用 |
| `SecondaryPanelBorder` | L201 | 仅定义，全局无引用 |
| `SecondaryTextPrimary` | L202 | 仅定义，全局无引用 |
| `SecondaryTextSecondary` | L203 | 仅定义，全局无引用 |
| `SecondaryAccent` | L203 | 仅定义，全局无引用 |
| `SecondaryAlert` | L204 | 仅定义，全局无引用 |
| `DialogPanel` | L205 | 仅定义，全局无引用 |
| `DialogInnerPanel` | L206 | 仅定义，全局无引用 |
| `DialogPanelBorder` | L207 | 仅定义，全局无引用 |
| `DialogTextPrimary` | L208 | 仅定义，全局无引用 |
| `DialogTextSecondary` | L209 | 仅定义，全局无引用 |

- **建议：** 整组删除，如未来需要可从 Git 历史恢复

---

## 六、代码异味 / 设计问题

### 9. `WeatherSyncManager.kt` — 构造器注入了未使用的 `weatherDao`

```kotlin
// L38
@Suppress("unused") private val weatherDao: WeatherDao
```

- 开发者已用 `@Suppress("unused")` 压制了警告，说明这是一个已知的冗余注入
- **建议：** 从构造器中移除

### 10. `WeatherWidgetUpdater.updateMediumAll()` — 绕过 Hilt 直接构造 `MembershipRepository`

```kotlin
// L573
val membershipRepository = MembershipRepository(context)
```

- 在 Widget 更新器中手动 `new` 了一个 `MembershipRepository` 实例，绕过了 Hilt 的单例管理
- 每次渲染都创建新实例（浪费内存），且可能读到不同的 `isPremium` 状态
- **建议：** 通过 Hilt 注入或传参获取

### 11. `WeatherNotificationWorker` 和 `UrgentNotificationWorker` — 大量重复逻辑

以下方法/逻辑在两个 Worker 中**完全相同**（代码拷贝）：

| 重复项 | 说明 |
|---|---|
| `createChannel()` | 完全相同 |
| `isAlertChannelEnabled()` | 完全相同 |
| `buildNotificationTitle()` | 完全相同 |
| `sendNotification()` | 几乎相同 |
| `createMainActivityIntent()` | 完全相同 |
| 降水判断逻辑 | `hasMinutelyRain`、`effectivePrecip`、`precipIntensityDesc` 完全相同 |

- **建议：** 提取公共基类或工具类

### 12. `WeatherWidgetWorker` 和 `WeatherWidgetMediumWorker` — 高度重复

两个 Worker 的以下方法**完全相同**：

| 重复项 |
|---|
| `doWork()` 主体逻辑 |
| `resolveDisplayName()` |
| `canRenderWeatherFrame()` |
| `isDefaultCoordinate()` |
| `isValidLocationName()` |

- **建议：** 提取公共基类 `BaseWeatherWidgetWorker`

### 13. `WeatherWidgetProvider` 和 `WeatherWidgetMediumProvider` — 高度重复

两个 Provider 的以下回调**逻辑结构完全相同**：

| 重复项 |
|---|
| `onReceive()` |
| `onUpdate()` |
| `onEnabled()` |
| `onDisabled()` |
| `enqueueWorker()` |
| `enqueueOneTimeWorker()` |

- **建议：** 提取公共基类或委托对象

---

## 七、汇总

| 类别 | 数量 | 影响 |
|---|---|---|
| 死代码文件 | 1 | `ParticleRenderer.kt` 整文件未使用 |
| 死代码函数 | 8 个函数 + 2 个数据类 | 分布在 4 个文件中 |
| 空壳模块 | 1 | `DataModule.kt` 无内容 |
| 只写不读的字段 | 8 | `WeatherTheme` + `WeatherUtils` 冗余 |
| 未使用颜色常量 | ~21 | `Color.kt` 大量预定义但未使用 |
| 冗余 DI 注入 | 1 | `weatherDao` 被 `@Suppress("unused")` |
| 代码重复 | 3 组 | Widget Worker/Provider + Notification Worker |

---

## 八、整体评价

项目整体架构质量不错，分层清晰（`UseCase → SyncManager → Repository → Room/Network`），Hilt 依赖注入使用规范。

主要问题集中在：
1. **重构残留的死代码** — 天气粒子效果从 `ParticleRenderer` 迁移到 `WeatherEffectOverlay` 后，旧文件未清理；`Color.kt` 中大量预留的二级页面颜色常量从未使用
2. **Widget / Notification 模块存在大量代码拷贝** — 两个尺寸的 Widget Worker/Provider 和两个 Notification Worker 之间有大量重复逻辑，适合提取公共基类

**建议优先级：**
1. 🥇 删除死代码文件和函数（零风险，立即可执行）
2. 🥈 清理 Color.kt 未使用常量和 WeatherTheme 冗余字段（低风险）
3. 🥉 提取 Widget / Notification 重复逻辑的公共基类（中等风险，需回归测试）
