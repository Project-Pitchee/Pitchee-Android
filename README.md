# Pitchee Android

Pitchee 是一款面向跨性别语音训练的 Android 应用，用于观察基频、分析声音，并长期记录自己的变化。

## 下载

正式版：

<https://github.com/project-pitchee/Pitchee-Android/releases>

Nightly 测试版也发布在同一页面，标识为 **Pre-release**。

每个版本提供三个 APK：

| APK | 适用设备 |
| --- | --- |
| `app-arm64-v8a-release.apk` | 大多数现代 Android 手机 |
| `app-x86_64-release.apk` | x86_64 模拟器或少数 Intel 设备 |
| `app-universal-release.apk` | 不确定设备架构时使用，文件最大 |

下载与设备匹配的 APK 后直接安装。Android 首次安装时可能要求允许当前浏览器或文件管理器“安装未知应用”。

## 用户指南

### 仪表盘

查看实时基频监测的平均 F0，以及保存的声音分析记录。

- 可以查看 F0、自然度、标准音色和综合分轨迹。
- 分析记录支持置顶、左滑删除和重新打开查看。
- “我的平均 F0”可以重置。

### 基频

实时查看当前基频和最近一段时间的 F0 轨迹。

- 点击开始键后开始监测。
- 阅读界面下方的示范语料。
- 支持播放刚才记录的声音。
- 回退按钮可以重新播放之前的音频窗口。
- 点击暂停/结束键可结束本次监测。

### 频谱

实时查看声音的频谱变化。

- 默认显示最近 5 秒。
- 播放按钮会切换为回放工具栏。
- 可以后退 5 秒并回放已录制内容。
- 回放结束后会自动继续实时分析。

### 声音分析

录制一段语音并分析综合分、标准音色、自然度和平均 F0。

- 有效说话时间至少需要 5 秒。
- 录音结束后可播放、缩放和拖动波形。
- 波形点击可切换示范语料页面。
- 分析结果会解释命中的提升或限制规则。
- 可以查看完整评分公式与规则细则。
- 历史记录会保存在本机。

### 隐私

- 应用只在录音功能启用时使用麦克风。
- 声音分析在本机完成，音频不会上传到服务器。
- 分析和录音历史保存在设备本地。
- 应用更新通过 GitHub Release 获取。

## 作者

- [Lvy Zhan](https://github.com/HaoduyouduDev)
- [Ryo Feng](https://github.com/RyouDYFZ)

## 项目仓库

- Pitchee Android：<https://github.com/project-pitchee/Pitchee-Android>
- PitcheeCore：<https://github.com/project-pitchee/Pitchee-core>

## 开源引用与许可证

Pitchee Android 整体以 **GPL-3.0-only** 发布，许可证全文见 [`LICENSE`](LICENSE)。

本项目使用了以下开源项目：

| 项目 | 许可证 | 地址 |
| --- | --- | --- |
| PitcheeCore | GPL-3.0-only | <https://github.com/project-pitchee/Pitchee-core> |
| SwiftF0 | MIT | <https://github.com/lars76/swift-f0> |
| Silero VAD | MIT | <https://github.com/snakers4/silero-vad> |
| SpeechBrain | Apache-2.0 | <https://github.com/speechbrain/speechbrain> |
| ONNX Runtime | MIT | <https://github.com/microsoft/onnxruntime> |
| AndroidX / Jetpack Compose | Apache-2.0 | <https://github.com/androidx/androidx> |
| Markwon | Apache-2.0 | <https://github.com/noties/Markwon> |
| Material Symbols | Apache-2.0 | <https://github.com/google/material-design-icons> |
| JLaTeXMath-Android | GPL-2.0 | <https://github.com/noties/jlatexmath-android> |
