plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "id.tirtapatriot.activitycamera"
version = "0.1.0"

application {
    mainClass.set("id.tirtapatriot.activitycamera.server.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("activity-camera-server.jar")
    }
}

dependencies {
    implementation(projects.model)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    implementation(libs.hikaricp)
    implementation(libs.mssql.jdbc)
}
