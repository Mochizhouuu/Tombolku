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

val hasSigning = !keyStoreFile.isNullOrEmpty() &&
    !keyStorePassword.isNullOrEmpty() &&
    !keyAliasName.isNullOrEmpty() &&
    !keyPassWord.isNullOrEmpty()

if (!hasSigning) {
    println("::error::Release signing tidak tersedia. Set env KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.")
}

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
        create("release") {
            storeFile = file(keyStoreFile ?: "")
            storePassword = keyStorePassword ?: ""
            keyAlias = keyAliasName ?: ""
            keyPassword = keyPassWord ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Fail fast: tanpa release signing, task release gagal dengan
                // pesan jelas. Tidak ada fallback ke debug key.
                signingConfig = null
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
