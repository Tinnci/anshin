import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

// Java 与 Kotlin 必须使用同一 JVM target，否则在 JDK 21 的 CI 上会编译失败。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}

ktlint {
    ignoreFailures.set(false)
}
