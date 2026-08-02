import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}

ktlint {
    ignoreFailures.set(false)
}
