# Pitchee-core Android 集成记录

## 1. 集成基线

- 上游仓库：`https://github.com/project-pitchee/Pitchee-core`
- 集成提交：`8613b6a0c4c969ff41a2fdfdc7666c79b1109a2d`（Voice Health 判定线调整）
- 上游版本：PitcheeCore `0.5.0`
- 模型版本：`2026-10-ncnn-fp16`
- Android 推理后端：ncnn（不再依赖 ONNX Runtime）
- Android NDK：`26.3.11579264`
- CMake：`3.22.1`
- 最低 Android API：24
- 当前打包 ABI：`arm64-v8a`、`x86_64`

上游源码和模型被放在 `third_party/Pitchee-core/`。当前基线已经包含：
- `8759d93` / `9bbcdb1`：Android 迁移到 ncnn，统一为 ModelRuntime。
- `036fb7a` / `b08bb75`：ECAPA ncnn 权重改为 FP16，并修正体积校验。
- `438884a`：优化 ncnn SwiftF0 STFT。
- `deed876`：增加 HNR 声质分析结果。
- `16d86e0`：增加实时 F0 options、metadata、warmup 和 stats 接口。
- `8abafd8` / `8885783` / `d83b8ed`：加入独立 Voice Health 分析，并从综合
  bundle JSON 中拆出。
- `c7a1aa3` / `8613b6a`：Voice Health 判定线调整到 50 分对应阈值，最终以
  health score `30` 作为是否沙哑的判断线。

上游项目没有 Git tag，因此提交号是当前唯一可复现的版本标识。

## 2. 工程结构

```text
app/src/main/
├── cpp/
│   ├── CMakeLists.txt                 # Android NDK 构建入口
│   └── PitcheeCoreJNI.cpp             # Kotlin <-> C ABI JNI 桥
├── java/io/rovly/pitchee/
│   ├── data/
│   │   ├── PitcheeRepository.kt       # 对上层公开的 Kotlin 集成接口
│   │   ├── AudioRecorder.kt            # MediaRecorder 录音
│   │   ├── RealtimeF0AudioRecorder.kt  # AudioRecord 16 kHz 实时 PCM
│   │   ├── AudioFileDecoder.kt         # Uri -> Float32 PCM
│   │   ├── RecordedAudio.kt            # 波形摘要与录音文件
│   │   ├── FeminineTimeline.kt         # VFP 时间窗 -> 原录音时间轴
│   │   ├── PitcheeResult.kt            # 强类型 JSON 契约
│   │   └── PcmAudio.kt
│   ├── ui/
│   │   ├── PitcheeApp.kt               # Compose 三页界面与底部导航
│   │   ├── AudioTimeline.kt            # 波形、播放、缩放和拖拽
│   │   ├── ScoreResult.kt              # 综合分、规则和短板卡片
│   │   ├── ScoreInsight.kt             # 评分规则解释和瓶颈判断
│   │   ├── RecordViewModel.kt          # 录音和分析状态
│   │   ├── PitchViewModel.kt           # 实时 F0 采集与状态
│   │   ├── RealtimePitchScreen.kt      # 实时 F0 曲线
│   │   └── theme/PitcheeTheme.kt       # 深粉色 Material 3 配色
│   └── MainActivity.kt                 # Compose 宿主
└── java/space/pitchee/core/
    └── PitcheeAnalyzer.kt              # PitcheeCore JNI 封装

third_party/Pitchee-core/               # 上游 C++、JNI 示例、模型和许可证
```

MVVM 对应关系：

- **Model**：`PitcheeRepository`、`AudioRecorder`、`AudioFileDecoder`、`PitcheeResult`
- **ViewModel**：`RecordViewModel`、`PitchViewModel`
- **View**：`PitcheeApp.kt`、`MainActivity.kt` 和三个 Compose 目的地

没有引入 Hilt/Koin 等 DI 框架。当前只有一个 Repository 和一个 ViewModel，
直接使用 `ViewModelProvider.Factory` 已足够；出现多页面、多数据源后再增加 DI。

## 3. Kotlin 集成接口

公开入口：

```kotlin
val repository = PitcheeRepository(context)
```

分析用户选择的音频文件：

```kotlin
val result = repository.analyze(
    uri = contentUri,
    maxSeconds = 20,
    onProgress = { progress ->
        // progress.stage / completed / total / stageFraction
    },
)
```

分析调用方已有的 Float32 PCM：

```kotlin
val result = repository.analyzePcm(
    samples = interleavedSamples, // [-1, 1]
    sampleRate = 48_000,
    channels = 2,
)
```

不再使用时关闭 native analyzer：

```kotlin
repository.close()
```

`analyze` 在 IO 线程解码音频，`analyzePcm` 在 CPU 线程执行模型推理。
Repository 会串行化 native 调用，因为同一个 `pitchee_analyzer_t` 不能并发使用。

只需要三指标综合分时，可以直接调用 Core 的便捷接口：

```kotlin
val score = PitcheeAnalyzer.compositeScoreValue(
    vfpStandardScore = 72.0,
    naturalnessScore = 80.0,
    f0Hz = 178.0,
)
```

没有可靠 F0 时传 `null`。Android 端会将其转换为 `NAN` 后调用
`pitchee_composite_score_value`。

实时 F0 流复用 Repository 中已加载的 analyzer：

```kotlin
val stream = repository.createRealtimeF0()
stream.process(floatSamples16kMono) { timestamp, f0Hz, confidence, voiced ->
    // timestamp 单位为秒；voiced=false 时 f0Hz 不应展示
}
stream.close()
```

实时频谱流独立于 analyzer，直接接收 16 kHz 单声道 Float32 PCM：

```kotlin
val spectrum = PitcheeSpectrum.create()
spectrum.process(floatSamples16kMono) {
        timestamp, magnitudes, firstBinIndex, binHz, peakHz, centroidHz, rolloffHz, flatness,
    ->
    // magnitudes 只在回调期间有效，需要异步使用时必须复制
}
spectrum.close()
```

返回类型为 `PitcheeResult`，包含：

- `audio`：源采样率、声道数、输入和实际分析时长
- `vad`：语音区间
- `f0`：平均 F0、标准差、0.05 秒时间窗
- `vfp`：VFP 标准分和源音频时间轴窗口
- `naturalness`：整段自然度分和局部自然度窗口
- `composite`：规则处理前后的综合分
- `rawJson`：完整上游 JSON，便于升级时排查字段兼容性

## 4. 集成时遇到的问题

### 4.1 上游没有 Android Maven 依赖

`Pitchee-core` 是 C++17/CMake 项目，只提供 Android JNI 示例，没有发布
GitHub Release、AAR 或 Maven 坐标，不能直接写：

```kotlin
implementation("...:pitchee-core:...")
```

处理方式：保留上游源码，在 `app/src/main/cpp/CMakeLists.txt` 中以
`add_subdirectory` 方式构建，并额外生成 `libpitchee_core_jni.so`。

### 4.2 ONNX Runtime 必须单独提供

PitcheeCore 只定义 `PITCHEE_ORT_INCLUDE_DIR` 和
`PITCHEE_ORT_LIBRARY`，不会下载或打包 ONNX Runtime。

处理方式：使用官方
`com.microsoft.onnxruntime:onnxruntime-android:1.24.2` AAR。该 AAR 虽然包含
头文件和四种 ABI 的 `libonnxruntime.so`，但没有 Prefab 配置，CMake 无法
通过 `find_package` 直接发现它。因此 Gradle 中增加了
`prepareOnnxRuntime` 任务，在编译 native 代码前自动提取头文件和当前 ABI
的 library。ONNX Runtime 必须与上游验证版本保持一致，不能随意升级。

### 4.3 模型路径不是 Android Asset 路径

PitcheeCore 通过普通文件路径读取 ONNX 模型，不能直接读取
`asset://` 或压缩资源流。

处理方式：首次使用 `PitcheeRepository` 时，将模型从 APK Asset 复制到
`context.noBackupFilesDir/pitchee-models/`，再将目录路径传给 native 层。
使用 `noBackupFilesDir` 是为了避免约 45 MB 模型进入 Android 自动备份；
`.ready` 文件记录模型版本，后续启动不会重复复制。

### 4.4 APK 体积和 ABI

完整模型约 45 MB，ONNX Runtime 单 ABI 约 25 至 31 MB。最初验证的全 ABI
Debug APK 约 106 MB。若加入 `armeabi-v7a` 和 `x86`，体积还会明显增加。

处理方式：当前只构建和打包 `arm64-v8a`、`x86_64`。前者覆盖现代真机，
后者覆盖常见模拟器。若发布时不需要模拟器，只保留 `arm64-v8a` 可继续减小
APK。

### 4.5 Android 15+ 的 16 KB 页对齐

上游 CMake 默认链接出的 `libpitchee_core.so` 和 JNI library 是 4 KB
`LOAD` 段对齐，而当前项目 `targetSdk` 为 37。部分 Android 15+ 设备使用
16 KB 内存页，未对齐的 native library 可能无法加载。

处理方式：Android CMake 中对两个自建 `.so` 增加：

```text
-Wl,-z,max-page-size=16384
```

构建后已确认 `libpitchee_core.so`、`libpitchee_core_jni.so` 的 `LOAD`
段对齐为 `0x4000`。ONNX Runtime 1.24.2 自带 library 也已经是 `0x4000`。

### 4.6 同一个 Analyzer 不能并发调用

上游 C API 明确要求同一个 analyzer 的调用必须串行化，或为每个并发 worker
创建独立 analyzer。Android 示例本身没有处理这个问题。

处理方式：`PitcheeAnalyzer` 对 `analyze/close` 使用同步保护，
`PitcheeRepository` 使用同一把锁串行化整个 native 生命周期。当前没有增加
analyzer 池；只有出现多任务并行分析需求时才需要池化。

### 4.7 长音频不再由 Core 截断

上游 `18e13c8` 移除了 `kMaximumSeconds = 20.0`，Core 现在会分析完整输入，
并使用多相 Kaiser 滤波器重采样到 16 kHz。长音频不再被 Core 静默截断，但会
带来更高的内存与计算时间，需要由调用方按产品场景控制时长。

处理方式：`PitcheeRepository.decode/analyze` 默认不再传入时长上限，以匹配
新版 Core。录音界面仍保留 20 秒自动停止，这是移动端交互限制，不是 Core
限制；如果调用方已有 Float32 PCM，也应自行评估 buffer 大小和等待时间。

### 4.8 Android 音频格式差异

PitcheeCore 只接收 Float32 PCM，而用户选择的是压缩或容器音频。
Android 的 `MediaCodec` 输出编码和格式可能因设备、编解码器而不同。

处理方式：`AudioFileDecoder` 支持：

- `audio/raw`：直接从 `MediaExtractor` 读取
- 其他音轨：使用 `MediaCodec` 解码
- 解码输出：PCM 16-bit 或 PCM Float

当前不支持解码器输出 PCM 24-bit 等格式。遇到时抛出明确错误，不让错误数据
进入模型计算。

### 4.9 UI 初始状态和测试缺失

原工程 `MainActivity` 是空页面，Manifest 也没有 LAUNCHER intent-filter，
即使完成 native 集成也无法直接启动验证。

处理方式：改造为 Jetpack Compose Material 3 应用壳，提供“实时基频 / 声音分析 /
设置”三个底部导航目的地；补上 `MAIN/LAUNCHER`。声音分析页使用
`RECORD_AUDIO` 和 `MediaRecorder`，录音停止后直接交给 Repository 分析。

### 4.10 许可证风险

上游 `Pitchee-core` 使用 GPL-3.0。把它作为同一个 Android 应用的一部分构建
和分发，通常会影响整个应用的许可证与源码分发义务，需要法务确认。

此外，上游只为 `SwiftF0.onnx` 提供了独立许可证；`ECAPA`、`VFPHead`、
`Naturalness`、`SileroVAD` 的模型许可和训练数据来源没有在仓库中逐项说明。

这不是代码问题，也不会由 Gradle 构建报错，但在发布或商业化前必须解决。
若应用不能采用 GPL-3.0，需要重新评估独立进程、动态下载方案是否足够，或
替换为许可兼容的推理库/模型。

### 4.11 Debug 原生代码未优化导致推理极慢

Android CMake 的 Debug 配置默认不优化 C++。实测同一个约 8.5 秒 AAC 语音在
模拟器上需要约 44 秒才能完成推理，界面虽然最终会显示结果，但很容易被理解
成“预测后没有结果”。

处理方式：Debug 构建中仅为 `pitchee_core` 和 `pitchee_core_jni` 增加 `-O2`，
保留调试符号和 Kotlin 调试能力。优化后同一设备测试总耗时降到约 5 至 14 秒。
Release 构建原本已经是优化构建。

### 4.12 录音和 Compose 集成

声音分析需要 `RECORD_AUDIO`。当前使用 `MediaRecorder` 生成 AAC/M4A，最长录制
20 秒并自动停止；Core 返回的 VAD 有效语音少于 5 秒时不进入结果页。录音页提供
《北风与太阳》《乌鸦喝水》《小马过河》三段可点击切换的朗读语料。停止录音后由
`AudioFileDecoder -> PitcheeCore` 处理。权限只在用户
点击“开始录音”时申请，未录音时不会占用麦克风。录音过程中轮询
`MediaRecorder.maxAmplitude` 绘制实时波形。采样间隔为 16 ms，并对相邻峰值做
轻度平滑，避免仅以 10 FPS 刷新造成卡顿；波形使用固定幅度，不按当前窗口自动
归一化，新采样从右侧进入并向左滚动。停止后解码 PCM，生成固定数量峰值桶用于
缩放显示，同时保留 M4A 文件给 `MediaPlayer` 播放。

模型缓存独立使用 `MODEL_CACHE_VERSION`。虽然 Core 的 `modelVersion` 仍为
`2026-09`，但 `VFPHead.onnx` 的字节已经变化，因此缓存版本提升为
`2026-09-native-windows`，已有安装会重新复制模型，避免继续使用旧缓存。

schema 2 中 `f0.windows`、`vfp.windows`、`naturalness.windows` 都直接提供源音频
时间轴位置，不再需要在 Android 侧反向映射 VAD 拼接时间。`FeminineTimeline`
按时间桶加权合并重叠 VFP 窗口、局部自然度窗口和 F0 窗口，并复刻综合分公式与
封顶规则，生成时间级综合指数；没有 VFP 覆盖或不属于 VAD 语音段的区间保留为
灰色。

时间轴支持点击定位、单指/双指平移、手势缩放以及按钮缩放。播放头下方显示当前
时间桶的局部综合指数；它由当前覆盖该时间桶的 VFP、自然度和 F0 窗口计算，并
应用同一套连续分和封顶规则，但整段聚合自然度可能与局部值不同。

界面采用 Compose Material 3，品牌主题固定为暖橙色，不启用动态色，避免系统
壁纸覆盖品牌色。声音分析页在结果生成前只保留标题、圆键、状态文案和底部
PitcheeCore 标识；录音键在空闲、录音和分析状态之间使用尺寸、颜色、缩放和
内容切换动画。

### 4.13 综合分规则解释

综合分和封顶逻辑直接复刻自 `src/scoring.cpp`，界面会显示基础连续分、规则限制、
最终得分和实际压低分值。具体规则包括：

- `pass_boost`：满足高 F0、高自然度、标准音色分门槛时提升到 60–100 区间。
- `high_f0_stylized_cap`：F0 高但自然度低于 50，最高 30。
- `low_f0_natural_cap`：自然度达标但 F0 不高于 165 Hz，最高 59。
- `low_f0_stylized_cap`：F0 和自然度都偏低，最高 20。
- `high_f0_male_cap`：F0 达标但模型音色标准分低于 50，最高 59。
- `f0_unavailable`：没有可靠 F0，直接使用标准音色分。

“最需要改善”卡片根据命中的规则和连续评分中的最低组成项给出一个明确短板，
对应指标会显示“重点”标记，避免只给出一个没有解释的总分。

### 4.14 推理进度和 Android 硬件加速

上游 C ABI 新增 `pitchee_analyzer_analyze_pcm_with_progress` 和 13 个细粒度
阶段。Android JNI 使用该接口，把 `completed/total/fraction` 原样传给 Kotlin。
圆键外环仍反映跨阶段整体进度，但界面只显示转换后的自然语言阶段，例如
“正在提取音色特征”，不显示百分比或 `x/x`。旧 phase callback 仍保留用于兼容。
`AudioFileDecoder` 还增加了 60 秒超时，避免损坏音频导致永久停留在“分析中”。

Android 版本目前没有启用 NNAPI 等 execution provider，仍使用 CPU。
需要硬件加速时，需要先验证 ONNX Runtime Android 的 NNAPI/其他 provider
与所有模型算子兼容性。

### 4.15 Android 实时 F0 流集成

Core `e9bdd3c` 新增 `pitchee_realtime_f0_create/process/reset/destroy`。Android
侧在 JNI 中增加对应桥接，并通过 `PitcheeRealtimeF0` 暴露同步回调。

Android 的 `MediaRecorder` 不提供实时 PCM，因此实时页使用单独的
`RealtimeF0AudioRecorder`：

- `AudioRecord`
- 16 kHz
- 单声道
- `ENCODING_PCM_FLOAT`
- `READ_BLOCKING`

`PitchViewModel` 在后台持续读取 512 个样本的 PCM，交给 Core 的实时流；Core
默认使用 5120 样本上下文和 256 样本 hop。界面只保留最近 480 个 F0 点，绘制
约 6 秒滚动曲线。离开实时页时先取消采集任务，再关闭实时流，避免 analyzer
生命周期结束前仍持有 native session。

## 5. 已验证内容

当前环境已执行并通过：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

验证结果：

- `arm64-v8a`、`x86_64` 两套 C++/JNI 均完成编译和链接
- APK 中包含 `libonnxruntime.so`、`libpitchee_core.so`、
  `libpitchee_core_jni.so`
- APK 中包含全部六个 ONNX 模型和 `manifest.json`
- 两个自建 native library 已检查为 16 KB 页对齐
- 单元测试通过强类型 JSON 契约解析
- Android API 32 arm64 模拟器上通过 native 模型加载、Float32 PCM 分析和
  静音 WAV 文件分析；静音按预期被 VAD 以 `no speech` 拒绝
- 通过 8.5 秒 AAC 合成语音的完整 `Uri -> MediaCodec -> PitcheeCore ->
  PitcheeResult` 成功路径，验证综合分位于 `0..100` 且 VFP 窗口数大于 0
- 验证 Kotlin 可以收到 native `DETECTING_SPEECH`、VFP 特征提取和 `COMPLETED`
  等细粒度进度回调
- 设备测试确认能收到 `DETECTING_SPEECH`、VFP 特征提取、自然度评分和
  `COMPLETED` 等细粒度进度
- 设备测试使用 16 kHz 单声道 180 Hz 测试音验证实时 F0 流，确认能够返回
  voiced 帧
- 新版原生时间轴窗口和设备测试中，7.53 秒语音产生 45 个 VFP/自然度窗口，
  完整成功路径约 8.5 秒，综合分约 `45.98`
- 在 Android API 32 arm64 模拟器上手动验证了麦克风权限、录音、停止分析、
  Material 3 三页导航和设置页
- 手动验证录音过程实时波形、录音后播放、时间轴缩放，以及五色指数和灰色无语音区间
- 手动验证综合分圆环、封顶路径、命中规则、短板建议和重点指标标记
- 手动验证新版录音页仅保留圆形录音键、红色停止键动画、右侧进入的固定幅度波形

自动测试中的语音是由 macOS `say` 生成的合成测试音频，只用于验证接口和
数据链路，不代表评分模型的质量或业务阈值。

## ncnn 迁移（Android 统一改为 ncnn 推理）

上游在 `8759d93`（Android backend ncnn-only）、`9bbcdb1`（runtime 抽象改名）两个
提交里把 Android 端从 ONNX Runtime 换成了 ncnn，`include/pitchee/pitchee.h` 的
公开 C API 没有变化，所以 JNI 和 Kotlin 绑定不需要改，改动集中在构建和模型。

### 1. 推理后端

- `src/ort_runtime.*` 被删除，换成 `src/model_runtime.*` 加
  `src/ncnn_pitchee_layers.*`（Core 自己注册的轻量自定义层）。
- Android 构建会强制 `PITCHEE_ENABLE_ORT=OFF`，必须
  `-DPITCHEE_ENABLE_NCNN=ON`，否则 CMake 直接报错。
- 桌面端仍然可以用 ONNX Runtime 构建，两端不再共用同一套后端。

### 2. ncnn 依赖

Core 只通过三个变量找 ncnn，不负责下载：

| 变量 | 本项目取值 |
| --- | --- |
| `PITCHEE_NCNN_INCLUDE_DIR` | `third_party/ncnn-android/<abi>/include` |
| `PITCHEE_NCNN_LIBRARY` | `third_party/ncnn-android/<abi>/lib/libncnn.a` |
| `PITCHEE_NCNN_ROOT` | `third_party/ncnn-android`（由 app 的 CMakeLists 展开到 ABI 子目录） |

用的是官方发布包 `ncnn-20260526-android.zip`（静态库、无 Vulkan），只保留
`arm64-v8a` 和 `x86_64` 两个 ABI，`libncnn.a` 随 APK 静态链接进
`libpitchee_core.so`，不需要额外打包 `.so`。

### 3. OpenMP

Core 在 ncnn 分支里链接 `-fopenmp`，而 NDK 的 OpenMP 运行时是共享库，直接链接
会要求 APK 里再带一个 `libomp.so`。这里改成加 `-static-openmp` 把运行时静态
链接进 `libpitchee_core.so`，省掉一个额外 `.so`，也避免和系统库冲突。

### 4. 模型

- 六个模型全部换成 `.ncnn.param` + `.ncnn.bin`，manifest 版本是 `2026-10-ncnn`。
- Android 只打包 ncnn 模型；仓库里的 `.onnx` 原文件已从
  `third_party/Pitchee-core/models/` 删除，否则它们会跟着 assets 一起进 APK，
  体积翻倍。
- 模型缓存版本同步改成 `2026-10-ncnn`，升级后会重新拷贝一次模型。
- 上游在 `036fb7a` 把 `ECAPA.ncnn.bin` 改成 FP16 存储（其余 ncnn 模型仍是
  FP32）：83 MB → 42 MB，ncnn 加载时会展开成 FP32 参与计算，属于存储压缩
  而非 FP16 推理。manifest 版本随之变成 `2026-10-ncnn-fp16`。
- 上游用同一批 10 段 WAV 在 ANA-AN00 上对比过两种权重，综合分平均偏差
  0.0082、最大 0.0579（见 `docs/ECAPA_FP16_VALIDATION.md`），所以精度差异
  可以忽略。
- `arm64-v8a` debug APK 从 104 MB 降到 **66 MB**，和迁移前的 ONNX 版本
  （约 60 MB）基本持平。

### 5. 验证

- 构建：`arm64-v8a` 与 `x86_64` 均可通过 `:app:assembleDebug`。
- APK 内容：`assets/` 只有 `.ncnn.param/bin` 和 manifest，`lib/` 只有
  `libpitchee_core.so`、`libpitchee_core_jni.so`，没有 `libonnxruntime.so`
  和 `libomp.so`。
- 模拟器上用 macOS `say` 合成的语音跑通完整链路：
  `ncnn backend: ...` 六个模型全部加载，`Analysis completed` 报出
  6.77 秒有效语音、40 个窗口和综合分。
- 实时音高频谱：把置信度阈值调低后能看到稳定输出的基频轨迹，说明 ncnn 版
  SwiftF0 正常工作。

## 实时 F0 的 ncnn 性能修复（`438884a`）

换到 ncnn 之后，真机上实时音高会卡顿（模拟器正常）。原因是实时流的推理耗时
超过了 hop：

- 实时流按 SwiftF0 的 hop 触发（默认 256 采样 = 16ms），每次都要把整个
  `context_samples`（默认 5120 = 320ms）跑一遍模型；
- 真机上单次推理一度需要 45ms（埋点当时写错了除数，修正后约 22ms），远超
  16ms 预算，读循环追不上麦克风，数据就积压成一阵一阵的更新。

上游 `438884a` 优化了 ncnn 的 SwiftF0 STFT 自定义层：预计算窗函数、旋转因子
表和位反转索引，FFT 从 `std::complex<double>` 改成 float 实现，去掉了每次调用
的堆分配。

用同一台真机（VEG0220811002435，静音，16ms hop）实测单次推理耗时：

| 版本 | 单次推理 | 相对 16ms 预算 |
| --- | --- | --- |
| 修复前 `b08bb75` | 17–18ms | 超预算，会积压 |
| 修复后 `438884a` | 14–16ms（偶发 20ms） | 刚好卡在预算内 |

也就是说修复把实时推理从"追不上"推进到"刚好跟上"，真机上不再积压。余量只有
1～2ms，如果之后再加 UI 负担又出现卡顿，可以按显示帧率放大 hop（30Hz → hop
512 采样），把推理次数直接减半。

埋点位置：`PitchViewModel` 里的 `PitchPerf` 日志（每 60 次推理输出一行），
不需要时删掉即可。

## 实时 F0 输出契约与 Voice Health（`16d86e0` → `8613b6a`）

上游新增 `pitchee_realtime_f0_create_with_options()`、metadata、warmup 和 stats
接口，并加入 HNR 声质分析数据。Android 侧的处理如下：

- 分析器创建实时流时传入 `PITCHEE_REALTIME_F0_OPTIONS_VERSION`、上下文窗口、
  推理 hop、用户设置的 `output_rate_hz` 和起始时间戳。
- 回调帧率由 Core 按 `output_rate_hz` 稳定选择，不再由 App 读取固定的
  `256 / 16000` 模型 hop 后自行降采样。
- 创建流后先调用 `pitchee_realtime_f0_warmup()`，避免第一次录音时把 ncnn
  初始化延迟暴露给用户。
- `PitcheeRealtimeF0` 暴露 `metadata()`、`warmup()` 和 `stats()`，后续可以直接
  读取 Core 的模型帧间隔、有效输出帧率以及 preprocess/model/postprocess 耗时。
- 实时流的上下文窗口在创建后固定为 160ms。Core 文档也明确：改变 context 或
  inference hop 必须重新创建 stream，不能在运行中修改输入 shape。
- HNR 已改为独立模块。综合 bundle JSON 不再固定包含 `voice_quality`；
  `PitcheeResult` 仍保留可选解析，兼容之前生成的历史 Schema 4 数据。
- Android JNI 已接入 `pitchee_analyzer_detect_vad()` 和
  `pitchee_analysis_run_voice_health()`。`PitcheeRepository` 提供
  `detectVad()` 和 `analyzeVoiceHealth()`，返回 Health Score、
  disorder_probability、decision_threshold 和 14 项声学特征。

真机 `VEG0220811002435`、ncnn、160ms 上下文、默认 30Hz 输出实测：

```text
F0 output ≈ 30–31/s
12 秒 UI 采样：726 帧，Janky 3 帧（0.41%）
```

Voice Health 的 Android 数据层已经接入，UI 尚未接入；后续可以直接在
`PitcheeRepository.analyzeVoiceHealth()` 上做页面和结果展示。
