plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ee.tooming.mondeomirror"
    compileSdk = 35

    defaultConfig {
        applicationId = "ee.tooming.mondeomirror"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-phase1"
    }

    // Stable signing key so repeat installs on a friend's phone update in
    // place instead of needing an uninstall every time (CI runners have no
    // persistent debug.keystore, so a new one would be generated every run
    // without this). Keystore itself is never committed - CI decodes it from
    // the KEYSTORE_BASE64 secret; see .github/workflows/android.yml.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
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
