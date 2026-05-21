pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
            content {
                includeGroup("org.jlleitschuh.gradle")
                includeGroup("org.jlleitschuh.gradle.ktlint")
            }
        }
        gradlePluginPortal()
        if (System.getenv("USE_CHINA_MAVEN_MIRRORS") == "true") {
            maven { url = uri("https://repo.huaweicloud.com/repository/maven") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        fun org.gradle.api.artifacts.dsl.RepositoryHandler.addChinaMirrorFallbacks() {
            maven {
                url = uri("https://maven.aliyun.com/repository/public")
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("com\\.microsoft.*")
                    includeGroupByRegex("org\\.jetbrains.*")
                    includeGroupByRegex("app\\.cash.*")
                    includeGroupByRegex("com\\.squareup.*")
                    includeGroupByRegex("junit.*")
                    includeGroupByRegex("org\\.mockito.*")
                    includeGroupByRegex("net\\.bytebuddy.*")
                    includeGroupByRegex("org\\.objenesis.*")
                }
            }
            maven {
                url = uri("https://repo.huaweicloud.com/repository/maven")
                content {
                    includeGroupByRegex("androidx.*")
                    includeGroupByRegex("com\\.android.*")
                    includeGroupByRegex("com\\.google.*")
                    includeGroupByRegex("com\\.microsoft.*")
                    includeGroupByRegex("org\\.jetbrains.*")
                    includeGroupByRegex("app\\.cash.*")
                    includeGroupByRegex("com\\.squareup.*")
                    includeGroupByRegex("junit.*")
                    includeGroupByRegex("org\\.mockito.*")
                    includeGroupByRegex("net\\.bytebuddy.*")
                    includeGroupByRegex("org\\.objenesis.*")
                }
            }
        }

        if (System.getenv("CI") == "true") {
            google()
            mavenCentral()
            addChinaMirrorFallbacks()
        } else {
            addChinaMirrorFallbacks()
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "Anshin"
include(":app")
