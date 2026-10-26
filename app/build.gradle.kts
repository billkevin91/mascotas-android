plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.billkevin91.mascota"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.billkevin91.mascota"
        minSdk = 24
        targetSdk = 35
        versionCode = 11
        versionName = "1.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // Pruebas del motor de movimientos (app/src/test), sin Android
    testImplementation("junit:junit:4.13.2")
}
