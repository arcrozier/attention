plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose)
}

android {
    namespace = "com.aracroproducts.attentionv2.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aracroproducts.attentionv2.wear"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        debug {
            versionNameSuffix = ".debug"
            resValue("string", "version_name", "${defaultConfig.versionName}${versionNameSuffix}")
            buildConfigField(
                "String",
                "BASE_URL",
                "\"http://10.0.2.2:8000/v2/\""
            )
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "version_name", "${defaultConfig.versionName}")
            buildConfigField(
                "String", "BASE_URL", "\"https://attention.aracroproducts" +
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}