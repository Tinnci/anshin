import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

private val localProperties: Properties by lazy {
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
}

// ── 签名属性读取：优先 env var（CI），回退到缓存的 local.properties ────────────
private fun signingProp(key: String): String? {
    System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it }
    return localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
}

val enableAbiSplits = providers.gradleProperty("enableAbiSplits").map(String::toBoolean).orElse(false)

private data class GitVersion(val versionCode: Int, val versionName: String)

private val gitDescribeOutput: String? =
    providers.exec {
        commandLine("git", "describe", "--tags", "--long", "--match", "v[0-9]*.[0-9]*.[0-9]*", "--abbrev=7")
        isIgnoreExitValue = true
    }.standardOutput.asText.map(String::trim).orNull?.takeIf { it.isNotBlank() }

private fun parseGitVersion(describe: String?): GitVersion {
    val fallback = GitVersion(versionCode = 12600099, versionName = "1.26.0")
    if (describe == null) return fallback
    val match = Regex("""^v(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)(\d+))?-(\d+)-g[0-9a-f]+$""").matchEntire(describe)
        ?: return fallback
    val (majorStr, minorStr, patchStr, channel, counterStr, aheadStr) = match.destructured
    val major = majorStr.toInt()
    val minor = minorStr.toInt()
    val patch = patchStr.toInt()
    val commitsAhead = aheadStr.toInt()
    val stage = when (channel) {
        "" -> 99
        "alpha" -> counterStr.toInt()
        "beta" -> 20 + counterStr.toInt()
        "rc" -> 40 + counterStr.toInt()
        else -> 99
    }
    val code = major * 10_000_000 + minor * 100_000 + patch * 100 + stage
    val baseName = buildString {
        append("$major.$minor.$patch")
        if (channel.isNotEmpty()) append("-$channel$counterStr")
    }
    val name = if (commitsAhead > 0) "$baseName-dev.$commitsAhead" else baseName
    return GitVersion(versionCode = code, versionName = name)
}

private val fallbackGitVersion = parseGitVersion(gitDescribeOutput)

private val gitShortSha: String =
    providers.exec {
        commandLine("git", "rev-parse", "--short=7", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map(String::trim).orNull?.takeIf { it.isNotBlank() }
        ?: System.getenv("GITHUB_SHA")?.take(7)?.takeIf { it.isNotBlank() }
        ?: ""

private val gitCommitCount: String =
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map(String::trim).orNull?.takeIf { it.isNotBlank() }
        ?: ""

android {
    namespace = "com.driezy.medlog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.driezy.medlog"
        minSdk = 26
        targetSdk = 37
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()) ?: fallbackGitVersion.versionCode
        versionName = System.getenv("VERSION_NAME") ?: fallbackGitVersion.versionName
        buildConfigField("String", "GIT_SHA", "\"$gitShortSha\"")
        buildConfigField("String", "GIT_REV_COUNT", "\"$gitCommitCount\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ── 签名配置（env var = CI，local.properties = 本地，均缺失 = 仅 Debug）──────
    signingConfigs {
        create("release") {
            storeFile = signingProp("KEYSTORE_PATH")?.let { file(it) }
            storePassword = signingProp("KEYSTORE_PASSWORD") ?: ""
            keyAlias = signingProp("KEY_ALIAS") ?: ""
            keyPassword = signingProp("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            // R8 混淆 + 资源压缩（与 isShrinkResources 配合，ProGuard 规则由 proguard-rules.pro 管理）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 当 KEYSTORE_PASSWORD 可从任意来源读取时才应用签名
            if (signingProp("KEYSTORE_PASSWORD")?.isNotBlank() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Debug 构建可选启用部分混淆以便提前发现 ProGuard 问题
            isMinifyEnabled = false
        }
    }

    // ABI split 默认关闭，避免日常 Debug 同时打包三份大型 APK；发布流水线显式开启。
    splits {
        abi {
            isEnable = enableAbiSplits.get()
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        localeFilters += listOf("zh", "en", "ja", "ko")
    }
    lint {
        lintConfig = file("lint.xml")
        baseline = file("lint-baseline.xml")
        disable += "VectorPath"
        abortOnError = false // CI 中可改为 true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":core:ui"))
    implementation(project(":capability:reminders"))
    implementation(project(":feature:onboarding"))
    testImplementation(project(":core:testing"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.splashscreen)
    implementation(libs.okhttp)
    implementation(libs.androidx.photopicker.compose)

    // Room runtime is exported by :core:database; implementation and KSP live there.

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Glance AppWidget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // QR code generation
    implementation(libs.zxing.core)

    // QR code scanning (CameraX + ML Kit)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // OCR (ML Kit Text Recognition — 非捆绑库，按需下载模型)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.cjk)
    implementation(libs.mlkit.text.recognition.ja)
    implementation(libs.mlkit.text.recognition.ko)

    // ONNX Runtime (七段数码管自定义 OCR 模型)
    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        // 排除自动生成文件
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}
