import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
}

plugins {
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
        hostOs == "Linux" && isArm64 -> linuxArm64("linuxX64")
        hostOs == "Linux" && !isArm64 -> linuxX64("linuxArm64")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
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
        when (nativeTarget.name) {
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
