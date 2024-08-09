plugins {
    id("com.android.application")
    id("kotlin-android")
    // https://mvnrepository.com/artifact/com.google.devtools.ksp/symbol-processing-api
    id("com.google.devtools.ksp") version "2.0.10-1.0.24"
    id("com.google.gms.google-services")
}

android {
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aracroproducts.attentionv2"
        minSdk = 24
        targetSdk = 35
        versionCode = 39
        versionName = "2.1.7-beta2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        signingConfig = signingConfigs.getByName("debug")
    }

    buildFeatures {
        compose = true
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

    kotlinOptions {
        jvmTarget = "18"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn" + "-Xjvm-default=all"
    }

    composeOptions {
        // https://developer.android.com/jetpack/androidx/releases/compose-compiler
        kotlinCompilerExtensionVersion = "1.4.7"
    }

    namespace = "com.aracroproducts.attentionv2"
}

dependencies {
    val lifecycleVersion = "2.8.4"

    implementation("com.google.firebase:firebase-messaging-ktx:24.0.0")
    implementation("com.google.firebase:firebase-analytics:22.0.2")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.preference:preference-ktx:1.2.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-google-shortcuts:1.1.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")

    // define a BOM and its version
    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.0.0-alpha.14"))

    // define any required OkHttp artifacts without version
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    val gsonVersion = "2.11.0"
    implementation("com.squareup.retrofit2:converter-gson:$gsonVersion")
    implementation("com.google.code.gson:gson:2.11.0")


    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    // ViewModel utilities for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")

    // Saved state module for ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycleVersion")

    // optional - helpers for implementing LifecycleOwner in a Service
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")

    // optional - ProcessLifecycleOwner provides a lifecycle for the whole application process
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")

    // optional - Test helpers for LiveData
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    val jetpackComposeVersion = "1.6.8"
    // Integration with activities
    implementation("androidx.activity:activity-compose:1.9.1")
    // Compose Material Design
    implementation("androidx.compose.runtime:runtime-livedata:$jetpackComposeVersion")
    implementation("androidx.compose.material:material:$jetpackComposeVersion")
    // Animations
    implementation("androidx.compose.animation:animation:$jetpackComposeVersion")
    implementation("androidx.compose.material:material-icons-extended:$jetpackComposeVersion")
    // Tooling support (Previews, etc.)
    implementation("androidx.compose.ui:ui-tooling:$jetpackComposeVersion")
    // Integration with ViewModels
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    // UI Tests


    val composeMaterialVersion = "1.2.1"
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$jetpackComposeVersion")
    implementation("androidx.compose.material3:material3:$composeMaterialVersion")
    implementation("androidx.compose.material3:material3-window-size-class:$composeMaterialVersion")

    val roomVersion = "2.6.1"

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // To use Kotlin annotation processing tool (kapt)
    ksp("androidx.room:room-compiler:$roomVersion")

    // optional - Guava support for Room, including Optional and ListenableFuture
    implementation("androidx.room:room-guava:$roomVersion")

    // optional - Test helpers
    testImplementation("androidx.room:room-testing:$roomVersion")

    // optional - Paging 3 Integration
    implementation("androidx.room:room-paging:$roomVersion")

    // For sharing
    implementation("androidx.sharetarget:sharetarget:1.2.0")

    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")

    // Sign in with Google
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation("androidx.activity:activity-ktx:1.9.1")

    // image cropping library
    implementation("com.github.yalantis:ucrop:2.2.8")

    // must match ksp version at top of file
    implementation("com.google.devtools.ksp:symbol-processing-api:2.0.10-1.0.24")

    // Preferences DataStore (SharedPreferences like APIs)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}

repositories {
    mavenCentral()
}