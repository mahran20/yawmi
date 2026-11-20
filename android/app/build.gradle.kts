plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.yawmi"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.yawmi"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "4.4"
    }

    signingConfigs {
        create("release") {
            // The key is decrypted by the GitHub build from yawmi.jks.enc using the
            // SIGNING_PASSWORD repository secret. Neither is ever stored in plain form.
            storeFile = file("yawmi.jks")
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = "yawmi"
            keyPassword = System.getenv("SIGNING_PASSWORD")
            storeType = "PKCS12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.print:print:1.0.0")

    // "photograph your sink" alarm method: camera + on-device image labels. The labeling model comes
    // from Google Play services (downloaded once, then works offline); bundling it made the APK 47 MB.
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.android.gms:play-services-mlkit-image-labeling:16.0.8")

    // plain JVM unit tests; the real org.json replaces Android's stub version
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
