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
    linuxX64()
    linuxArm64()
    macosArm64()
    macosX64()
    mingwX64()
    /*
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "PlayerSampleLib"
            isStatic = true
        }
    }*/

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
        val linuxArm64Main by getting {
            dependsOn(linuxCommonMain)
        }
        val linuxX64Main by getting {
            dependsOn(linuxCommonMain)
        }
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }
        val appleMain by creating {
            dependsOn(nativeMain)
        }
        val appleTest by creating { dependsOn(nativeTest) }
        val macosArm64Main by getting { dependsOn(appleMain) }
        val macosX64Main by getting { dependsOn(appleMain) }
        /*
        val iosMain by creating { dependsOn(appleMain) }
        val iosTest by creating { dependsOn(appleTest) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosArm64Test by getting { dependsOn(iosTest) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Test by getting { dependsOn(iosTest) }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosX64Test by getting { dependsOn(iosTest) }
         */
    }
}
