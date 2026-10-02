plugins {
    id("com.android.application")
}

android {
    namespace = "com.narayanan.piggybank"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.narayanan.piggybank"
        minSdk = 29          // Android 10+ (Moto G73 and Redmi Pad are newer)
        targetSdk = 34
        versionCode = 1      // raise by 1 for every update you install
        versionName = "1.0"
    }

    signingConfigs {
        create("family") {
            storeFile = file("piggybank.keystore")
            storePassword = "piggybank123"
            keyAlias = "piggybank"
            keyPassword = "piggybank123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("family")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
