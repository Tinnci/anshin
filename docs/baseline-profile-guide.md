# Baseline Profile — 生成指南

## 概述

Baseline Profile 通过预编译关键代码路径，可将冷启动时间降低约 **30%**、减少界面卡顿。

## 前提条件

- Android Gradle Plugin ≥ 8.0
- `androidx.profileinstaller:profileinstaller` 已添加（✅ 已完成）
- 连接一台 API 28+ 的真机或 Emulator（非 x86_64 Host 加速）

## 生成步骤

### 1. 添加 MacroBenchmark 模块

```bash
# 在 Android Studio: File → New → New Module → Baseline Profile Generator
# 或手动创建 :baselineprofile 模块
```

### 2. 编写 BaselineProfileGenerator

```kotlin
// :baselineprofile/src/androidTest/java/.../BaselineProfileGenerator.kt
@RunWith(AndroidJUnit4::class)
class MedLogBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.example.medlog",
        profileBlock = {
            // 1. 冷启动 → 首屏
            pressHome()
            startActivityAndWait()

            // 2. 导航至主要页面
            device.findObject(By.text("用药")).click()
            device.waitForIdle()
            device.findObject(By.text("历史")).click()
            device.waitForIdle()
        }
    )
}
```

### 3. 运行生成

```bash
./gradlew :baselineprofile:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

### 4. 应用到 app 模块

生成结果自动写入 `app/src/main/baseline-prof.txt`。

### 5. 验证

```bash
./gradlew assembleRelease
# 检查 APK: unzip -p app-release.apk assets/dexopt/baseline.prof | head
```

## 注意事项

- `baseline-prof.txt` 需要 **重新生成** 当核心业务代码发生重大变化后
- CI 中可定期运行（推荐每次发布前更新一次）
- 建议使用真机，Emulator 生成的 Profile 对真机效果有限
