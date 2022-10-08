import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
}

plugins {
    kotlin("multiplatform")
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val isMacOSX64 = hostOs == "Mac OS X"
    val isLinuxX64 = hostOs == "Linux"
    val nativeTarget = when {
        isMacOSX64 -> macosX64("native") { binaries.executable { } }
        isLinuxX64 -> linuxX64("native") { binaries.executable { } }
        isMingwX64 -> mingwX64("native") { binaries.executable { } }
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation(project(":ktmidi"))
                implementation(project(":player-sample-lib"))
            }
        }
    }
}
