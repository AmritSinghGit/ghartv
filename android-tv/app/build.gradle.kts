plugins {
    id("com.android.application")
}

val signingStore = System.getenv("GHARTV_SIGNING_STORE")
val signingStorePassword = System.getenv("GHARTV_SIGNING_STORE_PASSWORD")
val signingKeyAlias = System.getenv("GHARTV_SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("GHARTV_SIGNING_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    signingStore,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "in.ghartv.nova"
    compileSdk = 36

    defaultConfig {
        applicationId = "in.ghartv.nova"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.5.3-observability"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("ghartvRelease") {
                storeFile = file(signingStore!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("ghartvRelease")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.work:work-runtime:2.11.2")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
