// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            when ("${requested.group}:${requested.name}") {
                "com.google.guava:guava" -> useVersion("33.7.2-jre")
                "org.bouncycastle:bcprov-jdk18on",
                "org.bouncycastle:bcpkix-jdk18on",
                "org.bouncycastle:bctls-jdk18on",
                "org.bouncycastle:bcutil-jdk18on",
                -> useVersion("1.85")
                "ch.qos.logback:logback-core",
                "ch.qos.logback:logback-classic",
                -> useVersion("1.5.34")
                "org.bitbucket.b_c:jose4j" -> useVersion("0.9.6")
                "org.jdom:jdom2" -> useVersion("2.0.6.1")
                "org.apache.commons:commons-lang3" -> useVersion("3.18.0")
                "org.apache.httpcomponents:httpclient" -> useVersion("4.5.14")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.room) apply false
}

allprojects {
    fun ConfigurationContainer.enforcePatchedTransitiveDependencies() {
        configureEach {
            resolutionStrategy.eachDependency {
                when ("${requested.group}:${requested.name}") {
                    "com.google.guava:guava" -> {
                        val current = requested.version.orEmpty()
                        useVersion(if (current.endsWith("-android")) "33.7.2-android" else "33.7.2-jre")
                    }
                    "org.bouncycastle:bcprov-jdk18on",
                    "org.bouncycastle:bcpkix-jdk18on",
                    "org.bouncycastle:bctls-jdk18on",
                    "org.bouncycastle:bcutil-jdk18on",
                    -> useVersion("1.85")
                    "ch.qos.logback:logback-core",
                    "ch.qos.logback:logback-classic",
                    -> useVersion("1.5.34")
                    "org.bitbucket.b_c:jose4j" -> useVersion("0.9.6")
                    "org.jdom:jdom2" -> useVersion("2.0.6.1")
                    "org.apache.commons:commons-lang3" -> useVersion("3.18.0")
                    "org.apache.httpcomponents:httpclient" -> useVersion("4.5.14")
                }
            }
        }
    }

    buildscript.configurations.enforcePatchedTransitiveDependencies()
    configurations.enforcePatchedTransitiveDependencies()
}
