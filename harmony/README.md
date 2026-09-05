# SkyPulse HarmonyOS

原生 ArkTS + ArkUI 天气应用，面向 HarmonyOS 6.0（API 20）及后续系统。

- 编译 SDK：HarmonyOS 6.1.1（API 24）
- 最低兼容 SDK：HarmonyOS 6.0.0（API 20）
- 数据：Open-Meteo、MET Norway、彩云天气三个内置源，并支持自定义 Open-Meteo 兼容源
- AI：本地 Weather Agent（天气、趋势、穿衣、出行风险工具）

## 天气源配置

应用顶部提供“天气源”页：

- Open-Meteo：免密钥，默认源。
- MET Norway：免密钥，全球九日预报。
- 彩云天气：需输入 Token。
- 自定义兼容源：配置 HTTPS URL 模板，必须包含 `{lat}`、`{lon}`，可选 `{days}`、`{key}`。

天气源类型、自定义名称和 URL 会持久化；Token 与自定义密钥只保存在本次运行内存中，避免明文写入设备，重启后需重新输入。配置完成后点击“测试并应用”，成功后天气页和 AI Agent 会共同使用新来源。

## 构建

```bash
cd harmony
ohpm install --all
hvigorw assembleHap --mode module -p product=default -p module=entry@default -p buildMode=debug --no-daemon
```

构建产物默认位于 `entry/build/default/outputs/default/`。

> HarmonyOS 商用手机真机安装需要使用华为开发者账号签发、且与设备/应用匹配的调试或发布证书与 Profile。CI 同时输出未签名 HAP 和 OpenHarmony 自签名 HAP；商用 HarmonyOS 设备请在 DevEco Studio 中开启自动签名后重新构建。
