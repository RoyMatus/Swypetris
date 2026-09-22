plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.itoltec.swypetris"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ru.itoltec.swypetris"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val signingFile = System.getenv("SWYPETRIS_STORE_FILE")
    val signingPassword = System.getenv("SWYPETRIS_STORE_PASSWORD")
    val signingAlias = System.getenv("SWYPETRIS_KEY_ALIAS")
    val signingReady = !signingFile.isNullOrBlank() && !signingPassword.isNullOrBlank() && !signingAlias.isNullOrBlank()
    if (gradle.startParameter.taskNames.any { it.endsWith("assembleRelease") || it.endsWith("bundleRelease") } && !signingReady) {
        throw GradleException("Use tools/release/Build-Release.ps1 or supply SWYPETRIS_STORE_FILE, SWYPETRIS_STORE_PASSWORD, SWYPETRIS_KEY_ALIAS")
    }
    signingConfigs {
        if (signingReady) create("distribution") {
            storeFile = file(signingFile!!)
            storePassword = signingPassword
            keyAlias = signingAlias
            keyPassword = signingPassword
            storeType = "PKCS12"
        }
    }
    buildTypes {
        release {
            if (signingReady) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.1")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.activity:activity-compose:1.8.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
