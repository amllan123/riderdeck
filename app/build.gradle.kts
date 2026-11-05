plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
// Google Maps key comes from the MAPS_API_KEY GitHub secret; without it the app uses its own map.
val mapsKey = System.getenv("MAPS_API_KEY") ?: ""

android {
    namespace = "com.riderdeck.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.riderdeck.app"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("boolean", "HAS_MAPS_KEY", mapsKey.isNotBlank().toString())
        // Same key, for Google place search (it is already inside the app via the manifest).
        buildConfigField("String", "MAPS_KEY", "\"${mapsKey.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}\"")
        // The map engine ships native code; keep only what phones like the iQOO 7 use.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // One fixed key, so every new build installs over the previous one.
    signingConfigs {
        create("rider") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("rider")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("rider")
        }
    }

    // "full" has notification access (music info, Maps turns, call names, read-aloud).
    // "lite" leaves it out so it installs even where Play Protect blocks that permission.
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
        }
        create("lite") {
            dimension = "edition"
            versionNameSuffix = "-lite"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.maplibre.gl:android-sdk:11.8.2")
    implementation("com.google.maps.android:maps-compose:6.1.0")
}
