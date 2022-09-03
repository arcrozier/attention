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
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("debug") {
            versionNameSuffix = ".debug"
            resValue("string", "version_name", "${defaultConfig.versionName}${versionNameSuffix}")
            buildConfigField("String", "BASE_URL", "\"http://attention.aracroproducts" +
            ".com:1080/api/v2/\"")
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
        kotlinCompilerExtensionVersion = "1.1.1"
    }

    signingConfigs {
        // We need to sign debug builds with a debug key to make firebase auth happy
        getByName("debug") {
            storeFile = file("../debug.keystore")
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
        }
    }

    namespace = "com.aracroproducts.attentionv2"
}

dependencies {
    val lifecycleVersion = "2.5.1"

    implementation("com.google.firebase:firebase-messaging-ktx:23.0.8")
    implementation("com.google.firebase:firebase-analytics:21.1.1")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.appcompat:appcompat:1.5.0")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:$lifecycleVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$lifecycleVersion")
    implementation("androidx.preference:preference-ktx:1.2.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.7.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    // define a BOM and its version
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.9.3"))

    // define any required OkHttp artifacts without version
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    val gson_version = "2.9.0"
    implementation("com.squareup.retrofit2:converter-gson:$gson_version")
    implementation("com.google.code.gson:gson:$gson_version")


    val arch_version = "2.1.0"

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
    testImplementation("androidx.arch.core:core-testing:$arch_version")

    val jetpack_compose_version = "1.2.1"
    // Integration with activities
    implementation("androidx.activity:activity-compose:1.5.1")
    // Compose Material Design
    implementation("androidx.compose.runtime:runtime-livedata:$jetpack_compose_version")
    implementation("androidx.compose.material:material:$jetpack_compose_version")
    // Animations
    implementation("androidx.compose.animation:animation:$jetpack_compose_version")
    implementation("androidx.compose.material:material-icons-extended:$jetpack_compose_version")
    // Tooling support (Previews, etc.)
    implementation("androidx.compose.ui:ui-tooling:$jetpack_compose_version")
    // Integration with ViewModels
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    // UI Tests


    val compose_material_version = "1.0.0-beta01"
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:$jetpack_compose_version")
    implementation("androidx.compose.material3:material3:$compose_material_version")
    implementation("androidx.compose.material3:material3-window-size-class:$compose_material_version")

    val room_version = "2.4.3"

    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    // To use Kotlin annotation processing tool (kapt)
    kapt("androidx.room:room-compiler:$room_version")

    // optional - Guava support for Room, including Optional and ListenableFuture
    implementation("androidx.room:room-guava:$room_version")

    // optional - Test helpers
    testImplementation("androidx.room:room-testing:$room_version")

    // optional - Paging 3 Integration
    implementation("androidx.room:room-paging:$room_version")

    // For sharing
    implementation("androidx.sharetarget:sharetarget:1.2.0-rc02")

    val accompanist_ver = "0.25.1"
    implementation("com.google.accompanist:accompanist-swiperefresh:$accompanist_ver")
    implementation("com.google.accompanist:accompanist-systemuicontroller:$accompanist_ver")

    // Sign in with Google
    implementation("com.google.android.gms:play-services-auth:20.3.0")

    implementation("androidx.activity:activity-ktx:1.6.0-rc01")

    // image cropping library
    implementation("com.github.yalantis:ucrop:2.2.8")

}
repositories {
    mavenCentral()
}