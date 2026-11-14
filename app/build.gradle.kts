import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

val keyStoreFile = System.getenv("KEYSTORE_FILE")
    ?: keystoreProps.getProperty("storeFile")
val keyStorePassword = System.getenv("KEYSTORE_PASSWORD")
    ?: keystoreProps.getProperty("storePassword")
val keyAlias = System.getenv("KEY_ALIAS")
    ?: keystoreProps.getProperty("keyAlias")
val keyPassword = System.getenv("KEY_PASSWORD")
    ?: keystoreProps.getProperty("keyPassword")

println("=== SIGNING DEBUG ===")
println("KEYSTORE_FILE: ${keyStoreFile}")
println("KEYSTORE_PASSWORD: ${keyStorePassword?.let { "set (${it.length} chars)" } ?: "null"}")
println("KEY_ALIAS: ${keyAlias}")
println("KEY_PASSWORD: ${keyPassword?.let { "set (${it.length} chars)" } ?: "null"}")

val hasSigning = !keyStoreFile.isNullOrEmpty() &&
    !keyStorePassword.isNullOrEmpty() &&
    !keyAlias.isNullOrEmpty() &&
    !keyPassword.isNullOrEmpty()

println("HAS_SIGNING: $hasSigning")

android {
    namespace = "com.tombolku.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tombolku.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keyStoreFile)
                storePassword = keyStorePassword
                keyAlias = keyAlias
                keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasSigning) {
                signingConfigs.getByName("release")
            } else {
                println("::warning::Signing tidak tersedia, menggunakan debug key")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}