// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // https://kotlinlang.org/docs/releases.html#release-details
    kotlin("jvm") version("1.7.20") apply(false)
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.2")
        classpath("com.google.gms:google-services:4.3.14")
        // https://kotlinlang.org/docs/releases.html#release-details
        classpath(kotlin("gradle-plugin", version = "1.7.20"))

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}