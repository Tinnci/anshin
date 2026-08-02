pluginManagement {
    repositories {
        if (System.getenv("CI") != "true" && System.getenv("USE_CHINA_MAVEN_MIRRORS") != "false") {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI") != "true" && System.getenv("USE_CHINA_MAVEN_MIRRORS") != "false") {
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
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Anshin"
include(":app")
include(":core:model")
include(":core:database")
include(":core:preferences")
include(":core:testing")
include(":core:ui")
include(":capability:reminders")
include(":feature:onboarding")
