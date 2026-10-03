# Android UI 与依赖升级分析

核对日期：2026-10-03。范围：当前源码、Gradle 配置、Google 官方发布说明；运行验证结果见末尾。此次分析不修改应用源码或依赖版本。

## 结论

项目已采用现代 Android 技术栈：Compose、Material 3、Expressive 动效、类型安全导航、生命周期感知状态收集、Edge-to-edge、自适应导航。AGP 已是当前正式版本。升级重点应是几项落后的稳定库、Material 3 alpha 的迁移，以及可读性和自适应内容布局。

## 版本核对

配置来源：`gradle/libs.versions.toml`、`app/build.gradle.kts`、Gradle wrapper。

| 项目 | 当前配置 | 核实的正式版 / 候选 | 建议 |
| --- | --- | --- | --- |
| AGP | 9.4.1 | 正式版 9.4.1；预览版 9.5.0-alpha08 | 保持正式版 |
| Gradle | 9.6.0 | AGP 9.4 的最低与默认要求均为 9.6.0 | 满足要求，不必为了 UI 升级 |
| JDK | 本地 17；CI 21；字节码目标 17 | AGP 最低 JDK 17 | 组合合理；运行 JDK 与字节码目标无需相同 |
| compile / target SDK | 37 / 37 | AGP 9.4 支持最高 API 37 | 保持；需要在 API 37 验证运行行为 |
| Kotlin | 2.4.20 | 正式版 2.4.20 | 保持 |
| Compose BOM | 2026.09.00 | 官方当前示例 2026.09.00 | 保持；BOM 不负责升级 Kotlin 编译器 |
| Material 3 | 1.5.0-alpha20 | 稳定线 1.4.0；alpha 线 alpha29 | 保留 Expressive 需求时，单独评估 alpha29 |
| Material Adaptive 三个库 | 声明 1.2.0；实际 1.3.0 | 1.3.0 | 同步版本声明；运行时已经是新版 |
| Lifecycle | 声明 2.10.0；实际 2.11.0 | 2.11.0 | 同步版本声明；运行时已经是新版 |
| Glance | 1.1.1 | 1.2.0 | AppWidget 与 Material3 适配库一起升级 |
| CameraX | 1.6.1 | 1.6.2 | 四个 CameraX 依赖一起升级，验证扫码与 OCR |
| Core KTX | 1.19.1 | 1.19.1 | 保持 |
| Activity Compose | 1.13.0 | 1.13.0 | 保持 |
| Navigation Compose | 2.10.2 | 2.10.2 | 保持；Navigation 3 是架构迁移 |
| Room 2 | 2.8.5 | 2.8.5 | 保持；Room 3 是独立迁移议题 |
| DataStore | 1.2.1 | 1.2.1 | 保持 |
| WorkManager | 2.12.0 | 2.12.0 | 保持 |
| AndroidX Hilt | 1.4.0 | 1.4.0 | 保持 |
| Dagger / Hilt | 2.60.1 | 2.60.1 | 保持 |
| Window | 1.5.1 | 1.5.1 | 保持；该变量名 windowSizeClass 实际对应 androidx.window:window |
| Embedded PhotoPicker | 1.0.0-alpha02 | 仍是 alpha02 | 暂无新版；继续保留系统 Picker 回退 |

来源：[AGP 当前版本](https://developer.android.com/reference/tools/gradle-api)、[AGP 9.4 兼容表](https://developer.android.com/build/releases/agp-9-4-0-release-notes)、[AndroidX 发布表](https://developer.android.com/jetpack/androidx/versions)、[Compose BOM](https://developer.android.google.cn/develop/ui/compose/bom?hl=en)、[Kotlin 发布记录](https://kotlinlang.org/docs/releases.html)、[Dagger 发布记录](https://github.com/google/dagger/releases)。

## 对照 Google 最佳实践

### 已经做得好的部分

- `Theme.kt` 使用完整 Material 色彩角色、明暗主题、可选动态色及 `MotionScheme.expressive()`。默认品牌色是合理的产品选择，动态色无需强制默认开启。
- `MainActivity.kt` 已启用 Edge-to-edge；导航容器显式处理底部 Insets。
- `MedLogApp.kt` 使用可序列化类型安全 Route；顶层导航保存与恢复状态并使用 singleTop。
- 页面通过 `collectAsStateWithLifecycle()` 收集状态，多处已拆分 Screen、Content、ViewModel。
- `MedLogNavigationComponents.kt` 根据窗口信息选择导航形式；默认五个顶层入口，有文字标签与选中状态。
- 设置具有分类入口；操作图标多使用资源化描述，装饰图标的描述为 null 是正确做法。
- Android 模块已经使用 AGP 内置 Kotlin、KSP 和 `compilerOptions`，无需重复应用 kotlin-android 或迁回 kapt。
- 当前代码已经在健康记录、日记和扫码页面使用统一的 `rememberBottomSheetState` API。

对照：[Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)、[Compose 无障碍默认行为](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)、[AGP 内置 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)。

### 优先改进：可读性与真实操作

1. **已处理药品卡片整体透明度为 0.60。** `MedicationCard.kt:74` 与 `:104` 会连药名、剂量、状态及低库存提醒一起变淡。建议保持关键文字和警告的不透明度，只调整背景、装饰或层级。是否违反对比度要求还需要按具体主题测量，不能仅凭 alpha 数字宣判不合格。
2. **三个 SuggestionChip 使用空的 onClick。** `MedicationCard.kt:172`、`:188`、`:211` 的展示标签会暴露无实际结果的交互。展示用途应改为非交互标签，或实现点击动作。其 20/24dp 高度还应检查文本裁切与触控区域重叠；Compose 可扩展触控区域，不能仅凭视觉高度断言实际目标小于 48dp。
3. **全局缩放 Density 会改变控件和窗口断点。** `Theme.kt:148` 将 density 乘以 0.94/1.08。紧凑模式的 48dp 约为系统 45.1dp，还可能改变自适应导航的尺寸判断。建议用间距、内容密度和组件尺寸控制布局，保留系统 Density，并保护最小交互尺寸。现有 fontScale 是乘法，不是直接覆盖系统字号。
4. **大字号时设置页隐藏说明。** `SettingsLayoutProfile.kt` 在 fontScale≥1.3 或小窗口时关闭 supportingText，`SettingsRowsComponents.kt:97` 按此删除说明。建议允许内容换行、增加行高或提供详情入口，避免放大字体的用户失去帮助信息。
5. **趋势图缺少图表本身的语义。** `HealthBmiTrendComponents.kt:157` 使用 Canvas；轴文字为 9/10sp，并通过 nativeCanvas 绘制。记录列表仍可提供原始数据，但屏幕阅读器无法从图表本身获取趋势。建议添加时间范围、最近值和变化摘要，提供可访问的数据列表，并检查字号放大时固定 180dp 图表高度的表现。

对照：[触控区域与正确交互语义](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)、[自定义组件 Semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics)。上述源码事实不等于 TalkBack 实测结论。

### 后续改进：布局与组件维护

- **内容自适应目前弱于导航自适应。** 已有导航 Rail 等逻辑，但源码未使用 ListDetailPaneScaffold / SupportingPaneScaffold；健康页面仍是填满窗口的单列 LazyColumn。优先考虑“我的药品列表 + 药品详情”、“健康趋势 + 记录列表”。仅引入自适应库不会自动得到双栏。
- **窗口 API 有实际编译警告。** `MedLogNavigationComponents.kt:48` 使用已弃用的 `currentWindowAdaptiveInfo()`，应迁移到 `currentWindowAdaptiveInfoV2()`，让 L / XL 宽度类别默认参与计算。这是此次当前源码编译直接给出的警告。[官方自适应建议](https://developer.android.com/develop/adaptive-apps/guides/adaptive-dos-and-donts)。
- **窗口判断与内容密度分开。** `PriorityTopBarActions` 使用 actions 槽位的 BoxWithConstraints 判断 600dp，它反映局部剩余宽度。应明确该判断是局部可用空间还是整个窗口级别，避免把二者混为一谈。
- **Typography 已有重复维护。** `Type.kt` 自定义一套 EmphasizedTypography。新版 Material 3 已提供更多原生 Typography 能力，可评估迁移，同时保留应用特有的 editorial 数字样式。
- **输入组件可逐步迁移到 TextFieldState。** 当前表单主要使用 value/onValueChange。对有输入变换、语音/OCR 回填、选区保留需求的字段优先采用 state-based 文本框；无需一次重写所有简单输入框。
- **预测性返回应实测。** 普通 NavHost 与编辑页脏数据 BackHandler 都存在，不能因为没有手写 PredictiveBackHandler 就判定不支持。应验证返回进度、取消返回和未保存编辑确认。

对照：[标准自适应布局](https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts)、[窗口尺寸类别](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)、[state-based 文本框](https://developer.android.com/develop/ui/compose/text/user-input)、[预测性返回](https://developer.android.com/develop/ui/compose/system/predictive-back)。

## 推荐升级顺序

1. 保持 AGP / Gradle / Kotlin / BOM；先建立当前构建和界面的实际验证结果。
2. 升级 CameraX 1.6.2、Glance 1.2.0，按功能分批验证；同步 Lifecycle 2.11.0 与 Adaptive 1.3.0 的版本声明。
3. 单独升级 Material 3 至 alpha29。检查 Slider / RangeSlider 签名、BottomSheet 半展开行为、日期时间输入、ListItem 布局及动画。
4. 修复空点击标签、文字透明度和图表语义，再检查字号 100% / 130% / 200%、紧凑密度、深浅主题和窄窗口。
5. 独立实现内容双栏，最后再决定是否需要 Navigation 3 或 Room 3 迁移。

Material 3 的稳定线当前为 1.4.0；项目已实际使用 Expressive API，不能只删除显式版本就声称稳定化完成。当前 material3 显式覆盖 BOM，而 adaptive-navigation-suite 无显式版本。已经运行 dependencyInsight 与 dependencies，确认两者最终都解析为 alpha20，同组约束对齐生效，没有证据表明二者当前混用不同版本。Compose UI 实际为 1.12.1，Adaptive 实际为 1.3.0，Lifecycle 实际为 2.11.0；版本表中的旧声明不代表实际运行时仍使用旧版。

来源：[Material 3 发布说明](https://developer.android.com/jetpack/androidx/releases/compose-material3)、[Adaptive 1.3](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive)、[Glance 1.2](https://developer.android.com/jetpack/androidx/releases/glance)、[Navigation 3](https://developer.android.com/jetpack/androidx/releases/navigation3)。

## 验证范围

- `:app:dependencyInsight` 与 `:app:dependencies --configuration debugRuntimeClasspath --offline` 成功，已核实实际解析版本。
- `:app:compileDebugKotlin` 完成，输出窗口 API 的弃用警告。
- `:app:assembleDebug` 失败于 `mergeExtDexDebug`：另一条分析前已经存在的 Gradle 进程（PID 10540）占用 Artifact transforms cache，等待缓存锁超时。没有终止该进程，也没有删除缓存；不能声称完整构建通过。
- 启动本地 API 35 模拟器，安装仓库已有的 1.22.0 Debug APK（产物修改时间 2026-09-20），本轮实际查看了今日用药和健康页。该 APK 无法确认与当前源码一致，画面仅作为运行参考，不作为当前源码视觉验收。
- 默认模拟器为 1080×2340、440dpi、系统字号 1.0；没有完成 API 37、平板、200% 字号、TalkBack 或全部主题的实测。
- 因新 APK 打包受阻，此次没有完成 Product Design 意义上的当前源码全流程截图审查；上述 UI 改进项主要来自源码检查。
- 仓库已有的 wrapper 与 settings.gradle.kts 修改属于分析开始前的工作，未覆盖。
