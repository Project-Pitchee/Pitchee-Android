# 项目结构

源码按职责和功能拆分，新增文件优先放入已有模块，不直接堆在根包。

```text
app/src/main/java/
├── io/rovly/pitchee/
│   ├── MainActivity.kt                 # 应用入口
│   ├── data/
│   │   ├── audio/                      # 录音、解码、PCM 与录音文件
│   │   ├── history/                    # 历史记录持久化
│   │   ├── model/                      # Core JSON、评分结果与时间轴模型
│   │   └── repository/                 # Core 与模型资源仓库
│   ├── feature/
│   │   ├── analysis/                   # 声音分析、结果页、播放器与分析状态
│   │   ├── dashboard/                  # Dashboard / Insights 与历史列表
│   │   ├── onboarding/                 # 首次启动引导
│   │   ├── pitch/                      # 实时基频
│   │   ├── settings/                   # 设置、语料、语言与声音目标
│   │   └── spectrum/                   # 实时频谱
│   ├── ui/
│   │   ├── PitcheeApp.kt               # 顶层层级与导航
│   │   ├── common/                     # 跨功能共享的 Compose 小组件
│   │   └── theme/                      # Material 3 主题
│   └── update/                         # GitHub Release 检查与安装
└── space/pitchee/core/                 # PitcheeCore JNI 绑定，保持独立命名空间
```

测试目录与主源码对应：

```text
app/src/test/java/io/rovly/pitchee/
app/src/androidTest/java/io/rovly/pitchee/
```

放置规则：

- 只在单个功能中使用的代码放进 `feature/<module>`。
- 音频、历史、模型和仓库实现放进 `data` 对应子包。
- 两个以上功能共用的 UI 放进 `ui/common`。
- `space.pitchee.core` 只放 Core 绑定，不混入业务逻辑。
- 新增测试放进与被测代码对应的测试包。
