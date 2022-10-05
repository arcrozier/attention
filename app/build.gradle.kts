plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "com.aracroproducts.attentionv2"
        minSdk = 24
        targetSdk = 33
        versionCode = 28
        versionName = "2.1.0 beta 1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn" + "-Xjvm-default=all"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.3.1"
    }

    namespace = "com.aracroproducts.attentionv2"
}

dependencies {
    val lifecycleVersion = "2.5.2"

    implementation("com.google.firebase:firebase-messaging-ktx:23.0.8")
    implementation("com.google.firebase:firebase-analytics:21.1.1")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:$lifecycleVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$lifecycleVersion")
    implementation("androidx.preference:preference-ktx:1.2.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.work:work-runtime-ktx:2.7.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    // define a BOM and its version
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))

    // define any required OkHttp artifacts without version
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    val gsonVersion = "2.9.0"
    implementation("com.squareup.retrofit2:converter-gson:$gsonVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")


    val archVersion = "2.1.0"

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    // ViewModel utilities for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    // LiveData
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion")
    // Lifecycles only (without ViewModel or LiveData)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")

    // Saved state module for ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:$lifecycleVersion")

    // alternately - if using Java8, use the following instead of lifecycle-compiler
    implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion")

    // optional - helpers for implementing LifecycleOwner in a Service
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")

    // optional - ProcessLifecycleOwner provides a lifecycle for the whole application process
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")

    // optional - ReactiveStreams support for LiveData
    implementation("androidx.lifecycle:lifecycle-reactivestreams-ktx:$lifecycleVersion")

    // optional - Test helpers for LiveData
    testImplementation("androidx.arch.core:core-testing:$archVersion")

    val jetpackComposeVersion = "1.2.1"
    // Integration with activities
    implementation("androidx.activity:activity-compose:1.6.0")
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


    val composeMaterialVersion = "1.0.0-beta03"
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$jetpackComposeVersion")
    implementation("androidx.compose.material3:material3:$composeMaterialVersion")
    implementation("androidx.compose.material3:material3-window-size-class:$composeMaterialVersion")

    val roomVersion = "2.4.3"

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // To use Kotlin annotation processing tool (kapt)
    kapt("androidx.room:room-compiler:$roomVersion")

    // optional - Guava support for Room, including Optional and ListenableFuture
    implementation("androidx.room:room-guava:$roomVersion")

    // optional - Test helpers
    testImplementation("androidx.room:room-testing:$roomVersion")

    // optional - Paging 3 Integration
    implementation("androidx.room:room-paging:$roomVersion")

    // For sharing
    implementation("androidx.sharetarget:sharetarget:1.2.0-rc02")

    val accompanistVersion = "0.25.1"
    implementation("com.google.accompanist:accompanist-swiperefresh:$accompanistVersion")
    implementation("com.google.accompanist:accompanist-systemuicontroller:$accompanistVersion")

    // Sign in with Google
    implementation("com.google.android.gms:play-services-auth:20.3.0")

    implementation("androidx.activity:activity-ktx:1.6.0")

    // image cropping library
    implementation("com.github.yalantis:ucrop:2.2.8")

}
repositories {
    mavenCentral()
}