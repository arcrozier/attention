plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    // https://mvnrepository.com/artifact/com.google.devtools.ksp/symbol-processing-api
    alias(libs.plugins.gms)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose)
}

android {
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aracroproducts.attentionv2"
        minSdk = 24
        targetSdk = 35
        versionCode = 39
        versionName = "2.2.0-beta2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        signingConfig = signingConfigs.getByName("debug")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        getByName("debug") {
            versionNameSuffix = ".debug"
            resValue("string", "version_name", "${defaultConfig.versionName}${versionNameSuffix}")
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/v2/\"")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro")
            resValue("string", "version_name", "${defaultConfig.versionName}")
            buildConfigField("String", "BASE_URL", "\"https://attention.aracroproducts" +
            ".com/api/v2/\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_18
        targetCompatibility = JavaVersion.VERSION_18
    }

    composeOptions {
        // https://developer.android.com/jetpack/androidx/releases/compose-compiler
        kotlinCompilerExtensionVersion = "1.4.7"
    }

    namespace = "com.aracroproducts.attentionv2"

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn" + "-Xjvm-default=all"
    }
}

dependencies {

    compileOnly(libs.android.gradlePlugin)

    implementation(libs.bundles.compose)
    implementation(libs.bundles.room)
    implementation(libs.bundles.lifecycle)

    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.analytics)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.preference.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.core.ktx)
    implementation(libs.core.google.shortcuts)

    implementation(libs.retrofit)

    // define a BOM and its version
    implementation(platform(libs.okhttp.bom))

    // define any required OkHttp artifacts without version
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    implementation(libs.converter.gson)
    implementation(libs.gson)


    // ViewModel
    implementation(libs.lifecycle.viewmodel.ktx)
    // ViewModel utilities for Compose
    implementation(libs.lifecycle.viewmodel.compose)

    // Saved state module for ViewModel
    implementation(libs.lifecycle.viewmodel.savedstate)

    // optional - helpers for implementing LifecycleOwner in a Service
    implementation(libs.lifecycle.service)

    // optional - ProcessLifecycleOwner provides a lifecycle for the whole application process
    implementation(libs.lifecycle.process)

    // optional - Test helpers for LiveData
    testImplementation(libs.core.testing)

    // Integration with activities
    implementation(libs.activity.compose)
    // Compose Material Design
    implementation(libs.runtime.livedata)
    implementation(libs.androidx.material)
    // Animations
    implementation(libs.androidx.animation)
    implementation(libs.androidx.material.icons.extended)
    // Tooling support (Previews, etc.)
    implementation(libs.androidx.ui.tooling)
    // Integration with ViewModels
    implementation(libs.lifecycle.viewmodel.compose)
    // UI Tests


    androidTestImplementation(libs.androidx.ui.test.junit4)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)


    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // To use Kotlin annotation processing tool (kapt)
    ksp(libs.androidx.room.compiler)

    // optional - Guava support for Room, including Optional and ListenableFuture
    implementation(libs.androidx.room.guava)

    // optional - Test helpers
    testImplementation(libs.androidx.room.testing)

    // optional - Paging 3 Integration
    implementation(libs.androidx.room.paging)

    // For sharing
    implementation(libs.androidx.sharetarget)

    implementation(libs.accompanist.systemuicontroller)

    // Sign in with Google
    implementation(libs.play.services.auth)

    implementation(libs.androidx.activity.ktx)

    // image cropping library
    implementation(libs.ucrop)

    // must match ksp version at top of file
    implementation(libs.symbol.processing.api)

    // Preferences DataStore (SharedPreferences like APIs)
    implementation(libs.androidx.datastore.preferences)
}