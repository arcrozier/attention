plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.gms)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose)
    alias(libs.plugins.firebase.crashlytics)
}


android {
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aracroproducts.attentionv2"
        minSdk = 24
        targetSdk = 35
        versionCode = 44
        versionName = "2.2.1"

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
        debug {
            versionNameSuffix = ".debug"
            resValue("string", "version_name", "${defaultConfig.versionName}${versionNameSuffix}")
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/v2/\"")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "version_name", "${defaultConfig.versionName}")
            buildConfigField("String", "BASE_URL", "\"https://attention.aracroproducts" +
                    ".com/api/v2/\""
            )

            ndk {
                debugSymbolLevel = "full"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    composeOptions {
        // https://developer.android.com/jetpack/androidx/releases/compose-compiler
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    configurations {
        create("cleanedAnnotations")
        implementation {
            exclude(group = "org.jetbrains", module = "annotations")
        }
    }

    namespace = "com.aracroproducts.attentionv2"

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
        freeCompilerArgs += "-Xjvm-default=all"
    }
}

dependencies {

    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.googleid)
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.bundles.compose)
    implementation(libs.bundles.room)
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.credentials)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.appcompat)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.preference.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    implementation(libs.core.ktx)
    implementation(libs.core.google.shortcuts)

    implementation(libs.retrofit)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    implementation(libs.converter.gson)
    implementation(libs.gson)

    // optional - Test helpers for LiveData
    testImplementation(libs.core.testing)

    // For sharing
    implementation(libs.androidx.sharetarget)

    // Sign in with Google
    implementation(libs.play.services.auth)


    // image cropping library
    implementation(libs.ucrop)

    // must match ksp version at top of file
    implementation(libs.symbol.processing.api)

    // Preferences DataStore (SharedPreferences like APIs)
    implementation(libs.androidx.datastore.preferences)
}