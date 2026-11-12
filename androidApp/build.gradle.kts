import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.KeyStore

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "id.tirtapatriot.activitycamera"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "id.tirtapatriot.activitycamera"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    // Release key from the environment (CI secrets). Use the same key as the other in-house apps
    // (e.g. attendance) so Android gives them the same ANDROID_ID on a phone.
    // Unset secrets arrive in CI as empty strings, so treat blank as missing.
    // Trailing newlines appear when a secret is piped in (e.g. from `glab variable get`).
    fun env(name: String) = System.getenv(name)?.trimEnd('\n', '\r')?.takeIf { it.isNotBlank() }
    val releaseKeystore = env("ANDROID_KEYSTORE_PATH")?.takeIf { file(it).exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = env("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = resolveKeyAlias(file(releaseKeystore), env("ANDROID_KEYSTORE_PASSWORD"), env("ANDROID_KEY_ALIAS"))
                // Optional: most keystores use the same password for the key.
                keyPassword = env("ANDROID_KEY_PASSWORD") ?: env("ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
}

/**
 * The configured alias when it exists in the keystore; otherwise the only key in it.
 * Lists the aliases in the build log when it cannot decide, to help fix the ANDROID_KEY_ALIAS secret.
 */
fun resolveKeyAlias(keystore: File, password: String?, configured: String?): String? {
    val aliases = runCatching {
        val store = KeyStore.getInstance(KeyStore.getDefaultType())
        keystore.inputStream().use { store.load(it, password?.toCharArray()) }
        store.aliases().toList().filter { store.isKeyEntry(it) }
    }.getOrElse {
        logger.warn("Could not read the release keystore: ${it.message}")
        return configured
    }
    configured?.let { wanted -> aliases.firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { return it } }
    if (aliases.size == 1) {
        logger.warn("ANDROID_KEY_ALIAS not found in the keystore; using its only key '${aliases.single()}'.")
        return aliases.single()
    }
    logger.error("ANDROID_KEY_ALIAS not found. Keys in the keystore: ${aliases.joinToString()}")
    return configured
}
