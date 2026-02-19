# MedLog Android (Kotlin Compose)

原生 Android 版用药记录应用，使用 **Kotlin + Jetpack Compose + Material 3** 完全重写，架构参考 [Reply Compose Sample](https://github.com/android/compose-samples/tree/main/Reply)。

---

## 功能特性

| 功能 | 说明 |
|------|------|
| 今日用药 | 首页展示当天所有药品及服药状态，一键"全部标记已服" |
| 状态切换 | 每张药品卡片可标记已服 / 跳过 / 撤销，自动扣减/恢复库存 |
| 精准提醒 | AlarmManager 精准闹钟，通知栏支持直接"已服"/"跳过"操作 |
| 模糊时段 | 早餐前/后、午餐前/后、睡前等 11 种时段，匹配日常习惯 |
| 服药历史 | 近 30 天记录按日期分组，直观展示服药/跳过/遗漏 |
| 药品管理 | 支持添加/编辑/归档/删除，内置搜索过滤 |
| 库存追踪 | 设置库存量与补药阈值，存量不足时推送提醒 |
| 设置页面 | 持续提醒开关、提醒间隔、晨起/三餐/就寝时间个性化 |
| 自适应布局 | 手机端底部导航栏 → 平板端侧边导航栏 → 大屏导航抽屉（参考 Reply 方案）|
| 开机恢复 | 设备重启后自动恢复所有闹钟 |

---

## 技术栈

- **语言**: Kotlin 2.1.0
- **UI**: Jetpack Compose + Material 3 (with dynamic color)
- **自适应导航**: `androidx.compose.material3.adaptive.navigationsuite`
- **数据库**: Room 2.7.0 (KSP)
- **依赖注入**: Hilt 2.54
- **异步**: Kotlin Coroutines + StateFlow
- **后台任务**: WorkManager（配合 Hilt）
- **Splash Screen**: AndroidX Core Splash Screen
- **导航**: Navigation Compose 2.8.9（类型安全路由）
- **Min SDK**: 26 (Android 8.0)  |  **Target SDK**: 35

---

## 项目结构

```
app/src/main/java/com/example/medlog/
├── data/
│   ├── local/          # Room DAOs、Database、TypeConverters
│   ├── model/          # Medication、MedicationLog、TimePeriod
│   └── repository/     # MedicationRepository 接口及实现
├── di/                 # Hilt DI 模块 (AppModule)
├── notification/       # NotificationHelper、AlarmReceiver、BootReceiver
└── ui/
    ├── components/     # MedicationCard、ProgressHeader
    ├── navigation/     # 路由定义、自适应导航组件
    ├── screen/
    │   ├── home/       # 今日用药
    │   ├── history/    # 历史记录
    │   ├── drugs/      # 药品列表
    │   ├── settings/   # 设置
    │   ├── detail/     # 药品详情
    │   └── addmedication/ # 新增/编辑药品
    └── theme/          # 颜色、字体、主题
```

---

## 构建运行

### 前置条件

- Android Studio Ladybug (2024.2.1) 或更高版本
- JDK 17+
- Android SDK 35

### 步骤

```bash
# 1. 克隆仓库
git clone <repo-url>
cd MedLogAndroid

# 2. 用 Android Studio 打开，或命令行构建
./gradlew assembleDebug

# 3. 安装到设备/模拟器
./gradlew installDebug
```

> **注意**: `POST_NOTIFICATIONS` 权限在 Android 13+ 需要在运行时向用户请求。  
> `SCHEDULE_EXACT_ALARM` 权限在 Android 12+ 需要引导用户在系统设置中开启。

---

## 架构说明

遵循 **Clean Architecture + MVVM** 分层：

```
UI Layer (Compose Screens + ViewModels)
        ↓ StateFlow<UiState>
Domain Layer (Repository Interface)
        ↓ suspend fun / Flow
Data Layer (Room DAOs + RepositoryImpl)
```

导航采用 Reply Sample 的 **自适应导航套件** 模式，根据 `WindowWidthSizeClass` 自动切换：
- **Compact** → BottomNavigationBar
- **Medium** → NavigationRail
- **Expanded** → PermanentNavigationDrawer

---

## License

Apache 2.0
