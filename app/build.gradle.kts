plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tdaude.app"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.tdaude.app"
        minSdk = 26
        targetSdk = 28
        versionCode = 4
        versionName = "0.4"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    // Fixed key: every CI build is signed the same, so updates install over the old app.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Keep native libs extracted on disk (needed later to run bundled executables).
    packaging {
        jniLibs {
            useLegacyPackaging = true
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
    implementation("androidx.annotation:annotation:1.7.1")
    implementation(project(":terminal-view"))
}
