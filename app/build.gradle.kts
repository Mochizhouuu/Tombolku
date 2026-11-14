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
val keyAliasName = System.getenv("KEY_ALIAS")
    ?: keystoreProps.getProperty("keyAlias")
val keyPassWord = System.getenv("KEY_PASSWORD")
    ?: keystoreProps.getProperty("keyPassword")

println("=== SIGNING DEBUG ===")
println("KEYSTORE_FILE: ${keyStoreFile}")
println("KEYSTORE_PASSWORD: ${keyStorePassword?.let { "set (${it.length} chars)" } ?: "null"}")
println("KEY_ALIAS: ${keyAliasName}")
println("KEY_PASSWORD: ${keyPassWord?.let { "set (${it.length} chars)" } ?: "null"}")

val hasSigning = !keyStoreFile.isNullOrEmpty() &&
    !keyStorePassword.isNullOrEmpty() &&
    !keyAliasName.isNullOrEmpty() &&
    !keyPassWord.isNullOrEmpty()

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
                keyAlias = keyAliasName
                keyPassword = keyPassWord
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