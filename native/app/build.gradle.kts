import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp) // Phase 4 : génération Room
    alias(libs.plugins.kotlin.serialization) // Phase 5 : kotlinx.serialization
}

/** Clé de signature des APK release publiés. */
data class ReleaseKey(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

/**
 * CI : variables RELEASE_* (keystore décodé depuis les secrets GitHub).
 * En local : ~/.android-keys/localstream-release.properties, ou le fichier désigné par
 * RELEASE_SIGNING_PROPERTIES. Sans clé, le release est signé avec la clé debug.
 */
val releaseKey: ReleaseKey? = run {
    val env = System.getenv()
    val envStoreFile = env["RELEASE_KEYSTORE_PATH"]?.let { rootProject.file(it) }
    if (envStoreFile != null) {
        return@run envStoreFile.takeIf { it.exists() }?.let {
            ReleaseKey(
                storeFile = it,
                storePassword = env.getValue("RELEASE_STORE_PASSWORD"),
                keyAlias = env.getValue("RELEASE_KEY_ALIAS"),
                keyPassword = env.getValue("RELEASE_KEY_PASSWORD"),
            )
        }
    }
    val propsFile = file(
        env["RELEASE_SIGNING_PROPERTIES"]
            ?: "${System.getProperty("user.home")}/.android-keys/localstream-release.properties",
    )
    if (!propsFile.exists()) return@run null
    val props = Properties().apply { propsFile.inputStream().use { load(it) } }
    ReleaseKey(
        storeFile = file(props.getProperty("storeFile")),
        storePassword = props.getProperty("storePassword"),
        keyAlias = props.getProperty("keyAlias"),
        keyPassword = props.getProperty("keyPassword"),
    )
}

android {
    namespace = "com.localstream.app"
    compileSdk = 36

    signingConfigs {
        getByName("debug") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val key = releaseKey
            if (key != null) {
                storeFile = key.storeFile
                storePassword = key.storePassword
                keyAlias = key.keyAlias
                keyPassword = key.keyPassword
            } else {
                // Fallback vers le debug keystore si aucune clé release n'est fournie (permet le build local)
                storeFile = file("${rootDir}/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        // Phase 10 : applicationId final hérité de l'app Capacitor existante.
        applicationId = "com.localstream.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 10202
        versionName = "1.2.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
        getByName("test").assets.srcDirs("$projectDir/schemas")
    }

    buildFeatures {
        compose = true
    }
}


// Rotation de clé (APK Signature Scheme v3) : les versions ≤ 1.2.1 étaient signées avec la clé
// debug. L'APK release porte la preuve, signée par cette clé debug, que la clé release lui
// succède : Android 9+ l'installe en mise à jour, sans désinstaller ni perdre les données.
// v1/v2 (Android < 9) restent signés par la clé debug, seule clé que ces versions connaissent.
releaseKey?.let { key ->
    val signReleaseApkWithRotation = tasks.register<Exec>("signReleaseApkWithRotation") {
        val sdkDir = androidComponents.sdkComponents.sdkDirectory.get().asFile
        val isWindows = System.getProperty("os.name").startsWith("Windows")
        val apksigner = sdkDir.resolve("build-tools/${android.buildToolsVersion}/apksigner${if (isWindows) ".bat" else ""}")
        environment("LS_RELEASE_STORE_PASSWORD", key.storePassword)
        environment("LS_RELEASE_KEY_PASSWORD", key.keyPassword)
        commandLine(
            apksigner.absolutePath, "sign",
            "--ks", rootProject.file("debug.keystore").absolutePath,
            "--ks-key-alias", "androiddebugkey",
            "--ks-pass", "pass:android",
            "--key-pass", "pass:android",
            "--next-signer",
            "--ks", key.storeFile.absolutePath,
            "--ks-key-alias", key.keyAlias,
            "--ks-pass", "env:LS_RELEASE_STORE_PASSWORD",
            "--key-pass", "env:LS_RELEASE_KEY_PASSWORD",
            "--lineage", rootProject.file("signing/release-rotation.lineage").absolutePath,
            "--rotation-min-sdk-version", "28",
            "--v4-signing-enabled", "false",
            layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile.absolutePath,
        )
    }
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        finalizedBy(signReleaseApkWithRotation)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    // Analyse statique du code Kotlin. La config de base est enrichie par les
    // réglages adaptés à Compose dans config/detekt/detekt.yml.
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose) // Phase 7
    implementation(libs.androidx.lifecycle.runtime.compose) // Phase 7
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)

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

    // Phase 5 — API TMDB, Retrofit, Serialization, OkHttp, Coil
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.coil.compose)

    // Phase 9 — ExoPlayer (Media3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    testImplementation(libs.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.room.testing)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
