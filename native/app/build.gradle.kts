plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp) // Phase 4 : g\u00e9n\u00e9ration Room
}

android {
    namespace = "com.localstream.app"
    compileSdk = 36

    defaultConfig {
        // applicationId PROVISOIRE : le vrai id (com.localstream.app) sera repris en
        // Phase 10 pour h\u00e9riter des installations de l'app Capacitor existante.
        applicationId = "com.localstream.app.native"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-native"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    }
}

detekt {
    // Analyse statique du code Kotlin. La config de base est enrichie par les
    // r\u00e9glages adapt\u00e9s \u00e0 Compose dans config/detekt/detekt.yml.
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Phase 4 — Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Phase 4 — DataStore
    implementation(libs.datastore.preferences)

    // Phase 4 — Jetpack Security (EncryptedSharedPreferences)
    implementation(libs.security.crypto)

    // Phase 4 — Coroutines
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.room.testing)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
