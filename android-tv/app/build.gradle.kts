plugins {
    id("com.android.application")
}
val signingStore = System.getenv("GHARTV_SIGNING_STORE")
val signingStorePassword = System.getenv("GHARTV_SIGNING_STORE_PASSWORD")
val signingKeyAlias = System.getenv("GHARTV_SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("GHARTV_SIGNING_KEY_PASSWORD")
val hasReleaseSigning = listOf(signingStore, signingStorePassword, signingKeyAlias, signingKeyPassword).all { !it.isNullOrBlank() }
// Update-path test only (debug copies): -PupdateTestBase=http://10.0.2.2:PORT points the debug app at a test feed on the Mac, and -PtestVersionCode=N sets the test copy's versionCode. Both are ignored unless a debug task is being built and updateTestBase is a valid test address; release builds always use 67 and an empty test address.
val updateTestBase = (project.findProperty("updateTestBase") as String?)?.takeIf { gradle.startParameter.taskNames.any { t -> t.contains("debug", ignoreCase = true) } && it.matches(Regex("http://10\\.0\\.2\\.2:[0-9]{2,5}")) } ?: ""
android {
    namespace = "in.ghartv.nova"
    compileSdk = 36
    defaultConfig {
        applicationId = "in.ghartv.nova"
        minSdk = 26
        targetSdk = 35
        versionCode = if (updateTestBase.isNotEmpty()) ((project.findProperty("testVersionCode") as String?)?.toIntOrNull() ?: 77) else 77
        versionName = "0.7.3-phone-first-review"
        buildConfigField("String", "UPDATE_TEST_BASE", "\"\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
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
    if ((project.findProperty("sideBySide") as String?) != null) {
        sourceSets.getByName("debug").res.srcDir("src/sideBySide/res")
    }
    buildTypes {
        // Side-by-side test copy: gradle -PsideBySide=fixes installs as in.ghartv.nova.fixes named "GharTV fixes". Default build is unchanged.
        val sideBySide = (project.findProperty("sideBySide") as String?)?.takeIf { it.matches(Regex("[a-z]+")) }
        debug { applicationIdSuffix = if (sideBySide != null) ".$sideBySide" else ""; versionNameSuffix = "-debug"; buildConfigField("String", "UPDATE_TEST_BASE", "\"$updateTestBase\"") }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) { signingConfig = signingConfigs.getByName("ghartvRelease") }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.webkit:webkit:1.17.1")
    implementation(files("libs/zxing-core-3.5.3.jar"))
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("info.guardianproject:tor-android:0.4.8.22")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}

// Local review only: run Gradle 8.11 with JDK17, compile dependencies built for JDK21
// using Android Studio's existing newer javac, without installing a JDK or mutating caches.
tasks.withType<JavaCompile>().configureEach {
    options.isFork = true
    options.forkOptions.executable = "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/javac"
}
