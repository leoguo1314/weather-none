# SkyPulse HarmonyOS

原生 ArkTS + ArkUI 天气应用，面向 HarmonyOS 6.0（API 20）及后续系统。

- 编译 SDK：HarmonyOS 6.1.1（API 24）
- 最低兼容 SDK：HarmonyOS 6.0.0（API 20）
- 数据：Open-Meteo 免 Key 实时天气与 7 日预报
- AI：本地 Weather Agent（天气、趋势、穿衣、出行风险工具）

## 构建

```bash
cd harmony
ohpm install --all
hvigorw assembleHap --mode module -p product=default -p module=entry@default -p buildMode=debug --no-daemon
```

构建产物默认位于 `entry/build/default/outputs/default/`。

> HarmonyOS 商用手机真机安装需要使用华为开发者账号签发、且与设备/应用匹配的调试或发布证书与 Profile。CI 同时输出未签名 HAP 和 OpenHarmony 自签名 HAP；商用 HarmonyOS 设备请在 DevEco Studio 中开启自动签名后重新构建。
