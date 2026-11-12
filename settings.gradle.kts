rootProject.name = "ActivityCamera"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// model      : data contract shared by app and server
// shared     : Compose Multiplatform UI + logic (Android & iOS)
// androidApp : Android entry point
// server     : Ktor sync API (NAS + SQL Server)
include(":model", ":shared", ":androidApp", ":server")
