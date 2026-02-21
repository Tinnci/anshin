# Anshin 安心

<p align="center">
  <strong>A personal medication tracker that follows your daily rhythm.</strong><br>
  <em>安心 · 安心 · 안심 — Peace of mind, in every dose.</em>
</p>

<p align="center">
  <a href="https://github.com/Tinnci/anshin/actions/workflows/build.yml">
    <img src="https://github.com/Tinnci/anshin/actions/workflows/build.yml/badge.svg" alt="CI Build">
  </a>
  <img src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Material%203-Expressive-6750A4?logo=material-design&logoColor=white" alt="Material 3">
  <img src="https://img.shields.io/badge/Min%20SDK-26-brightgreen?logo=android&logoColor=white" alt="Min SDK">
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue" alt="License">
</p>

> **Anshin** (*an-shin*) — *Adherence & Notification System for Health Intelligence*  
> 中文：安心用药，不再遗忘 · 日本語：安心して服薬管理 · 한국어：안심하고 복약 관리

A native Android app built with **Kotlin · Jetpack Compose · Material 3 Expressive**,  
helping users track daily medication, manage inventory, and stay on schedule with precise alarms.

---

## Features / 功能特性

| Feature | Description |
|---------|-------------|
| **Onboarding** | 6-page spring-animated guide — routine times, feature toggles, theme selection |
| **Today's Doses** | Dashboard with all medications and progress; one-tap mark-all; completed groups auto-collapse |
| **Status Tracking** | Cards support Taken / Skipped / Undo with automatic stock deduction and restoration |
| **Precise Reminders** | AlarmManager exact alarms; direct Taken / Skip actions in the notification |
| **Routine-based Timing** | 11 fuzzy time periods (morning, before/after meals, bedtime…) auto-mapped to personal schedule |
| **History & Heatmap** | 30-day log grouped by date; heatmap shows daily adherence rate |
| **Medication Management** | Add / Edit / Archive / Delete; notes, dose, form, frequency, PRN support |
| **Stock Tracking** | Inventory + refill threshold; proactive low-stock alert |
| **Health Records** | Blood pressure, blood sugar, weight, and other vital sign logging |
| **Drug Database** | Browse built-in Chinese/TCM drug catalog for quick add |
| **Interaction Check** | Automatic multi-drug co-administration risk detection |
| **QR Share** | Export today's medication status as a QR code for backup / sharing |
| **OEM Widget Support** | Three homescreen widgets; MIUI / ColorOS / OriginOS permission guidance built-in |
| **Adaptive Layout** | Phone → Tablet → Large screen auto-switching (bottom bar / rail / drawer) |
| **Alarm Recovery** | All alarms re-scheduled automatically after device reboot |
| **Travel Mode** | Keep hometown-timezone reminders when traveling across time zones |
| **Personalization** | Routine times, early reminder offset, persistent reminder toggle, dark/light/auto theme, dynamic color |

---

## Tech Stack / 技术栈

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose · Material 3 Expressive 1.5.0-alpha14 |
| Adaptive navigation | `material3-adaptive-navigation-suite` |
| State | Kotlin Coroutines · StateFlow · `collectAsStateWithLifecycle` |
| Database | Room 2.7 (KSP) |
| Preferences | Jetpack DataStore |
| DI | Hilt 2.59 + HiltViewModel |
| Background | WorkManager + AlarmManager (exact) |
| Navigation | Navigation Compose 2.8 (type-safe serialized routes) |
| Animation | `Animatable` · Spring physics · `AnimatedVisibility` |
| QR Code | ZXing core 3.5.3 |
| Code Quality | ktlint · Android Lint · EditorConfig |
| Min / Target SDK | 26 / 36 |

---

## Project Structure / 项目结构

```
app/src/main/java/com/example/medlog/
├── data/
│   ├── local/          # Room DAOs · Database · TypeConverters
│   ├── model/          # Medication · MedicationLog · TimePeriod · HealthRecord
│   └── repository/     # MedicationRepository · UserPreferencesRepository
├── di/                 # Hilt AppModule (all DB migrations registered)
├── domain/             # ResyncRemindersUseCase
├── notification/       # NotificationHelper · AlarmScheduler · BootReceiver
├── ui/
│   ├── components/     # MedicationCard · ProgressHeader
│   ├── navigation/     # NavGraph · Adaptive navigation components
│   ├── screen/         # welcome / home / history / drugs / diary / health / detail / addmedication / settings
│   ├── theme/          # Color · Type · Theme (M3 Dynamic Color)
│   └── utils/          # QrCodeUtils · OemWidgetHelper
└── widget/             # MedLogWidget · NextDoseWidget · StreakWidget
```

---

## Architecture / 架构

Clean Architecture + MVVM + SSOT (Single Source of Truth):

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

**Key SSOT principles:**
- `SettingsPreferences` is the single source of truth for all user preferences.  
  All ViewModels read from `UserPreferencesRepository.settingsFlow`.
- `MedLogAppViewModel` resolves `startDestination` from `hasSeenWelcome`, eliminating UI-layer branching.
- All writes go through typed `updateXxx()` suspend functions to guarantee thread safety.

**Adaptive navigation** auto-selects based on `WindowWidthSizeClass`:

| Screen width | Navigation component |
|-------------|----------------------|
| Compact | `BottomNavigationBar` |
| Medium | `NavigationRail` |
| Expanded | `PermanentNavigationDrawer` |

---

## Material 3 Expressive Components

| Component | Used in |
|-----------|---------|
| `ButtonGroup` + `FilledIconButton` / `OutlinedIconButton` | MedicationCard action row |
| `FilledTonalButton` | HomeScreen "Mark all taken" |
| `ExtendedFloatingActionButton` | HomeScreen / DrugsScreen |
| `SuggestionChip` | AddMedicationScreen routine-time auto-fill hint |
| `AnimatedVisibility` + Spring physics | WelcomeScreen entrance animation |
| `HorizontalPager` + `PagerDefaults.flingBehavior(spring)` | WelcomeScreen elastic swipe |
| `ElevatedCard` / `Card` (surfaceContainerLow, 0dp elevation) | Unified card style throughout |
| `LinearProgressIndicator` | ProgressHeader today's progress |
| `CircularProgressIndicator` | DataStore loading state on startup |
| `calendarWarning` color token | HistoryScreen heatmap · DetailScreen adherence rate |

---

## Internationalization Roadmap / 多语言支持规划

Anshin targets Chinese, Japanese, Korean, and English speakers.  
Current status: **Chinese (zh) only** — all UI strings are in Chinese.

### Implementation Plan

**Phase 1 — Foundation (current sprint)**
- [x] Brand name "Anshin" unified across all locales
- [ ] Audit all hardcoded strings in Compose screens → move to `strings.xml`
- [ ] Set English as the default locale (`values/strings.xml`) for translatable strings

**Phase 2 — English (EN)**
- [ ] `app/src/main/res/values-en/strings.xml`
- [ ] Translate all 200+ UI strings to English
- [ ] Update notification copy and widget labels

**Phase 3 — Japanese (JA)**
- [ ] `app/src/main/res/values-ja/strings.xml`
- [ ] 服薬管理、今日の服薬、服薬履歴…
- [ ] ja locale date/time format adjustment

**Phase 4 — Korean (KO)**
- [ ] `app/src/main/res/values-ko/strings.xml`
- [ ] 복약 관리、오늘의 복약、복약 기록…

**Phase 5 — RTL & Accessibility**
- [ ] Verify Arabic/Hebrew RTL mirrors for adaptive navigation
- [ ] TalkBack content description completeness audit

### String Coverage (Current)

| Category | Count | Location |
|----------|-------|----------|
| `strings.xml` resources | ~40 | `values/strings.xml` |
| Hardcoded in Compose screens | ~200+ | Various `*Screen.kt` files |

> **Contributions welcome:** To translate Anshin into your language,  
> open an issue or PR with `app/src/main/res/values-{lang}/strings.xml`.

---


## Getting Started / 构建与运行

### Prerequisites / 前置条件

- **Android Studio** Meerkat 2024.3.2+
- **JDK 17** (recommended: manage via `jenv`)
- **Android SDK 36**

### Quick Start

```bash
# 1. Clone the repository / 克隆仓库
git clone https://github.com/Tinnci/anshin.git
cd anshin

# 2. Initialize Git hooks (once per contributor) / 初始化 Git Hooks（每位成员只需执行一次）
./setup-hooks.sh

# 3. Build / 构建
export JAVA_HOME=$(jenv javahome)   # or set JDK 17 path manually / 或手动设置 JDK 17 路径
./gradlew assembleDebug

# 4. Install on device/emulator / 安装到设备或模拟器
./gradlew installDebug
```

> **Required permissions / 权限提示:**
> - `POST_NOTIFICATIONS` — runtime request on Android 13+ / Android 13+ 需运行时申请
> - `SCHEDULE_EXACT_ALARM` — enable "Alarms & Reminders" in system settings (Android 12+) / Android 12+ 需在系统设置中开启「精确闹钟」权限

---

## Code Quality / 代码质量

### ktlint

```bash
./gradlew ktlintCheck    # lint
./gradlew ktlintFormat   # auto-fix
```

Config: [.editorconfig](.editorconfig) — Android Studio style, max line 120, trailing commas allowed.

### Android Lint

```bash
./gradlew lintDebug
# Report: app/build/reports/lint-results-debug.html
```

### Pre-commit / Pre-push Hooks

After running `./setup-hooks.sh`:
- `git commit` → runs `ktlintCheck` automatically
- `git push` → runs `lintDebug` automatically

---

## CI / CD

| Workflow | Trigger | Artifact |
|----------|---------|---------|
| [CI Build](.github/workflows/build.yml) | push / PR → master | Debug APK artifact |
| [Release](.github/workflows/release.yml) | push `v*.*.*` tag | Signed release APK + GitHub Release |

**Release flow:**

```bash
git tag v1.0.4
git push origin v1.0.4   # triggers Release workflow automatically
```

The release workflow extracts the version, decodes the Keystore, builds a signed APK, generates a changelog, and creates a GitHub Release automatically.  
See [.github/SIGNING.md](.github/SIGNING.md) for signing configuration.

---

## Contributing / 贡献指南

Contributions are welcome in any language.  
For translations, see the [i18n roadmap](#internationalization-roadmap--多语言支持规划) above.

1. Fork → feature branch → PR
2. Pass `ktlintCheck` and `lintDebug` before submitting
3. Follow the existing MVVM + SSOT patterns

---

## License

```
Copyright 2025 Anshin Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
