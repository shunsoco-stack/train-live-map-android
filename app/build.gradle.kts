import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val apiBaseUrl = localProperties
    .getProperty("API_BASE_URL", "https://train-live-map.vercel.app")
    .trim()
    .trimEnd('/')
val releaseAdMobAppId = localProperties.getProperty("ADMOB_APP_ID", "").trim()
val releaseBannerId = localProperties.getProperty("ADMOB_BANNER_AD_UNIT_ID", "").trim()
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdaptiveBannerId = "ca-app-pub-3940256099942544/9214589741"

android {
    namespace = "com.shunsoco.trainlivemap"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shunsoco.trainlivemap"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "API_BASE_URL", apiBaseUrl.asBuildConfigString())
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "ADMOB_APP_ID", testAdMobAppId.asBuildConfigString())
            buildConfigField(
                "String",
                "ADMOB_BANNER_AD_UNIT_ID",
                testAdaptiveBannerId.asBuildConfigString(),
            )
            manifestPlaceholders["admobAppId"] = testAdMobAppId
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("String", "ADMOB_APP_ID", releaseAdMobAppId.asBuildConfigString())
            buildConfigField(
                "String",
                "ADMOB_BANNER_AD_UNIT_ID",
                releaseBannerId.asBuildConfigString(),
            )
            manifestPlaceholders["admobAppId"] = releaseAdMobAppId
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.maplibre.android)
    implementation(libs.google.mobile.ads)
    implementation(libs.google.ump)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
