pluginManagement {
    repositories {
        // Optional Google Maven fallback for networks where dl.google.com returns 404.
        // Restricted to AndroidX; upstream dependency versions remain unchanged.
        if (providers.gradleProperty("heartMavenMirror").orNull == "true") {
            maven("https://repo.huaweicloud.com/repository/maven") {
                content { includeGroupByRegex("androidx\\..*") }
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Vendored copy of io.github.libxposed:*:102.0.0. The artifacts are
        // not in the local Gradle cache and pulling them from Maven Central
        // would make every clean build depend on the network.
        maven {
            url = uri(rootDir.resolve(".tools/maven"))
            metadataSources {
                mavenPom()
                artifact()
            }
        }
        // Optional Google Maven fallback for networks where dl.google.com returns 404.
        // Restricted to AndroidX; upstream dependency versions remain unchanged.
        if (providers.gradleProperty("heartMavenMirror").orNull == "true") {
            maven("https://repo.huaweicloud.com/repository/maven") {
                content { includeGroupByRegex("androidx\\..*") }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "HyperDuo"
include(":app")
