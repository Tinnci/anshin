import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:preferences"))
    testImplementation(libs.junit)
}

ktlint {
    ignoreFailures.set(false)
}
