buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
}

plugins {
    id("application")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.gradleJavacppPlatform) // required to resolve rtmidi-javacpp-platform appropriately
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
        java {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    /* TODO
    js(BOTH) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport.enabled = true
                }
            }
        }
        nodejs {
        }
    }*/
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("macosArm64")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("macosX64")
        // I figured Kotlin-Native is not ready enough for linking third-party libraries.
        // FIXME: revisit it when this issue got resolved https://youtrack.jetbrains.com/issue/KT-47061/Cant-compile-project-with-OpenAL-dependecy-Kotlin-Native#focus=Comments-27-4947040.0-0
        //hostOs == "Linux" && isArm64 -> linuxArm64("linuxX64")
        //hostOs == "Linux" && !isArm64 -> linuxX64("linuxArm64")
        isMingwX64 -> mingwX64("native")
        else -> null //throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    nativeTarget?.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.io)
                implementation(project(":ktmidi"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":ktmidi-jvm-desktop"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        /* TODO
        val jsMain by getting
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        */
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(project(":ktmidi-native-ext"))
            }
        }
        val nativeTest by creating
        val linuxCommonMain by creating {
            dependsOn(nativeMain)
        }
        val appleMain by creating {
            dependsOn(nativeMain)
        }
        val appleTest by creating {
            dependsOn(nativeTest)
        }
        when (nativeTarget?.name) {
            "linuxArm64" -> {
                val linuxArm64Main by getting { dependsOn(linuxCommonMain) }
            }

            "linuxX64" -> {
                val linuxX64Main by getting { dependsOn(linuxCommonMain) }
            }

            "mingwX64" -> {
                val mingwX64Main by getting { dependsOn(nativeMain) }
            }

            "macosArm64" -> {
                val macosArm64Main by getting { dependsOn(appleMain) }
            }

            "macosX64" -> {
                val macosX64Main by getting { dependsOn(appleMain) }
            }
        }
    }
}
