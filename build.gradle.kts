// Top-level build file where you can add configuration options common to all sub-projects/modules.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // https://kotlinlang.org/docs/releases.html#release-details
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.gms) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

buildscript {
    dependencies {
//        compileOnly(libs.android.gradlePlugin)
//        classpath(libs.gradle)
//        classpath("com.google.gms:google-services:4.4.2")
        // https://kotlinlang.org/docs/releases.html#release-details
//        classpath(kotlin("gradle-plugin", version = "1.8.21"))

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}