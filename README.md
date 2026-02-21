# Anshin — 智能用药管理

> **安心 · 安心 · 안심** — 三语语义统一的用药记录应用名称  
> *Adherence & Notification System for Health Intelligence*

[![CI Build](https://github.com/Tinnci/anshin/actions/workflows/build.yml/badge.svg)](https://github.com/Tinnci/anshin/actions/workflows/build.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Material 3](https://img.shields.io/badge/Material%203-Expressive-6750A4?logo=material-design&logoColor=white)](https://m3.material.io)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-brightgreen?logo=android&logoColor=white)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

> 原生 Android 应用，使用 **Kotlin · Jetpack Compose · Material 3 Expressive** 构建，
> 帮助用户追踪每日用药、管理药品库存、并通过精准闹钟按时提醒服药。

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 引导欢迎页 | 首次启动 4 页弹簧动画引导，帮助用户完成作息时间设置 |
| 今日用药 | 首页展示当天全部药品及服药进度，支持一键全部标记；已完成分组可自动折叠 |
| 状态管理 | 每张药品卡片可标记「已服 / 跳过 / 撤销」，自动扣减/恢复库存 |
| 精准提醒 | AlarmManager 精准闹钟，通知栏直接操作「已服 / 跳过」 |
| 作息时段 | 11 种模糊时段（晨起/餐前/餐后/睡前等），自动匹配用户作息设置 |
| 服药历史 | 近 30 天记录按日期分组，热力图展示每日达标率 |
| 药品管理 | 添加 / 编辑 / 归档 / 删除；备注、剂量、频率全量管理 |
| 库存追踪 | 库存量 + 补药阈值，存量不足时主动提醒 |
| 个性化设置 | 作息时间（晨起·三餐·就寝）、持续提醒开关、提醒间隔 |
| 自适应布局 | 手机底部栏 → 平板侧边栏 → 大屏抽屉（参考 Reply Sample） |
| 开机恢复 | 设备重启后自动还原全部闹钟 |

---

## 技术栈

| 层 | 技术 |
|----|------|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose · Material 3 Expressive (1.5.0-alpha14) |
| 自适应导航 | `material3-adaptive-navigation-suite` |
| 状态 | Kotlin Coroutines · StateFlow · `collectAsStateWithLifecycle` |
| 数据库 | Room 2.7 (KSP) |
| 持久化偏好 | Jetpack DataStore Preferences |
| 依赖注入 | Hilt 2.59 + HiltViewModel |
| 后台任务 | WorkManager + Hilt |
| 导航 | Navigation Compose 2.8（类型安全序列化路由） |
| 动画 | Compose `Animatable` · Spring physics · `AnimatedVisibility` |
| 代码质量 | ktlint · Android Lint · EditorConfig |
| Min/Target SDK | 26 / 36 |

---

## 项目结构

```
app/src/main/java/com/example/medlog/
├── data/
│   ├── local/         # Room DAO · Database · TypeConverters
│   ├── model/         # Medication · MedicationLog · TimePeriod
│   └── repository/    # MedicationRepository · UserPreferencesRepository (DataStore SSOT)
├── di/              # Hilt AppModule
├── notification/    # NotificationHelper · AlarmReceiver · BootReceiver
└── ui/
    ├── components/    # MedicationCard · ProgressHeader
    ├── screen/        # welcome / home / history / drugs / detail / addmedication / settings
    └── theme/          # Color · Type · Theme (M3 Dynamic Color)
```

---

## 架构说明

遵循 **Clean Architecture + MVVM + SSOT (Single Source of Truth)**：

```
┌─────────────────────────────────────────────┐
│  UI Layer                                   │
│  Compose Screens  ←→  ViewModels            │
│                        ↑ StateFlow<UiState> │
├─────────────────────────────────────────────┤
│  Domain Layer                               │
│  MedicationRepository (interface)           │
│  UserPreferencesRepository (DataStore SSOT) │
├─────────────────────────────────────────────┤
│  Data Layer                                 │
│  Room DAOs · RepositoryImpl · DataStore     │
└─────────────────────────────────────────────┘
```

### SSOT 关键原则

- `SettingsPreferences` 是用户偏好的**唯一数据源**，所有 ViewModel（`SettingsViewModel`、`AddMedicationViewModel`、`WelcomeViewModel`）均通过 `UserPreferencesRepository.settingsFlow` 读取
- `MedLogAppViewModel` 根据 `hasSeenWelcome` 决定 `startDestination`，避免 UI 层产生条件分支
- 所有写操作通过 `updateXxx()` 挂起函数进行，确保线程安全

### 自适应导航

根据 `WindowWidthSizeClass` 自动切换：

| 屏幕宽度 | 导航组件 |
|---------|---------|
| Compact | BottomNavigationBar |
| Medium | NavigationRail |
| Expanded | PermanentNavigationDrawer |

---

## Material 3 Expressive 使用清单

| 组件 | 使用位置 |
|------|---------|
| `ButtonGroup` + `FilledIconButton` / `OutlinedIconButton` | MedicationCard 操作区 |
| `FilledTonalButton` | HomeScreen「一键服用全部」 |
| `ExtendedFloatingActionButton` | HomeScreen / DrugsScreen |
| `SuggestionChip` | AddMedicationScreen 作息时间自动带入提示 |
| `AnimatedVisibility` + Spring physics | WelcomeScreen 入场动画 |
| `HorizontalPager` + `PagerDefaults.flingBehavior(spring)` | WelcomeScreen 弹性翻页 |
| `ElevatedCard` / `Card` (surfaceContainerLow, 0dp elevation) | 全应用卡片统一样式 |
| `LinearProgressIndicator` | ProgressHeader 今日进度 |
| `CircularProgressIndicator` | 启动时 DataStore 加载等待 |
| `calendarWarning` color token | HistoryScreen 热力图 · DetailScreen 达标率 |

---


## 构建与运行

### 前置条件

- **Android Studio** Meerkat 2024.3.2 或更高版本
- **JDK 17**（推荐使用 jenv 管理）
- **Android SDK 36**

### 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/Tinnci/MedLogAndroid.git
cd MedLogAndroid

# 2. 初始化 Git Hooks（每位成员只需执行一次）
./setup-hooks.sh

# 3. 命令行构建
export JAVA_HOME=$(jenv javahome)   # 或手动设置 JDK 17 路径
./gradlew assembleDebug

# 4. 安装到设备/模拟器
./gradlew installDebug
```

> **权限提示**：
> - `POST_NOTIFICATIONS`：Android 13+ 需运行时申请
> - `SCHEDULE_EXACT_ALARM`：Android 12+ 需在系统设置中开启「精确闹钟」权限

---

## 代码质量

### ktlint（Kotlin 代码风格）

```bash
# 检查
./gradlew ktlintCheck

# 自动修复
./gradlew ktlintFormat
```

风格配置见 [.editorconfig](.editorconfig)，主要规则：
- `android_studio` 代码风格
- 最大行宽 120 字符
- 允许尾随逗号

### Android Lint

```bash
# 运行 Lint（debug 变体）
./gradlew lintDebug

# 报告位置
# app/build/reports/lint-results-debug.html
```

Lint 规则配置见 [app/lint.xml](app/lint.xml)。

### Pre-commit Hooks

安装后（`./setup-hooks.sh`），每次 `git commit` 前自动运行 `ktlintCheck`；每次 `git push` 前自动运行 `lintDebug`。

---

## CI / CD

| Workflow | 触发条件 | 产物 |
|----------|---------|------|
| [CI Build](.github/workflows/build.yml) | push / PR → master | Debug APK artifact |
| [Release](.github/workflows/release.yml) | push `v*.*.*` tag | 签名 Release APK + GitHub Release |

**发布流程**：

```bash
git tag v1.0.0
git push origin v1.0.0   # 自动触发 Release 工作流
```

Release 工作流自动提取版本号、解码 Keystore、编译签名 APK、生成 Changelog 并创建 GitHub Release。  
签名配置方法见 [.github/SIGNING.md](.github/SIGNING.md)。

---

## License

```
Copyright 2025 MedLog Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0
```
