plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystorePath = providers.environmentVariable("IMERGE_KEYSTORE_PATH").orNull

android {
    namespace = "com.haroldadmin.imerge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.haroldadmin.imerge"
        minSdk = 29
        targetSdk = 36
        versionCode = providers.environmentVariable("IMERGE_VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.environmentVariable("IMERGE_VERSION_NAME").orNull ?: "1.0.0"

        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://github.com/ha6old/iMerge/releases/latest/download/update.json\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (releaseKeystorePath != null) {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("IMERGE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("IMERGE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("IMERGE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
