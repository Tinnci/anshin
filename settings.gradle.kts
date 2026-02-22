pluginManagement {
    repositories {
        // 国内镜像仅在本地使用（CI 环境直连官方源）
        if (System.getenv("CI") != "true") {
            maven { url = uri("https://repo.huaweicloud.com/repository/maven") }
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
        // 国内镜像仅在本地使用（CI 环境直连官方源）
        if (System.getenv("CI") != "true") {
            maven { url = uri("https://repo.huaweicloud.com/repository/maven") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Anshin"
include(":app")
