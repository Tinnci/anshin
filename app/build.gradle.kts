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

// ── 签名属性读取：优先 env var（CI），回退到 local.properties（本地开发）──────────
private fun signingProp(key: String): String? {
    System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it }
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        val p = Properties().apply { load(f.inputStream()) }
        p.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}

android {
    namespace = "com.example.medlog"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.medlog"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()) ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0"

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        lintConfig = file("lint.xml")
        baseline = file("lint-baseline.xml")
        abortOnError = false // CI 中可改为 true
        htmlReport = true
        xmlReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
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
    implementation(libs.androidx.material.icons.extended)
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

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

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

    // Baseline Profile installer（让 ART 在首次安装时即时应用 baseline-prof.txt 中的预编译规则）
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
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
