import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.dokka)
    alias(libs.plugins.binaryCompatibilityValidatorPlugin)
    id("maven-publish")
    id("signing")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "ktmidi"
        browser {}
        //nodejs {}
    }

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    androidTarget {
        publishLibraryVariantsGroupedByFlavor = true
        publishLibraryVariants("debug", "release")
    }
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    webpackConfig.cssSupport {}
                }
            }
        }
        nodejs {
        }
    }

    val iosTargets = listOf(
        iosArm64(),
        iosX64(),
        iosSimulatorArm64()
    ).onEach {
        it.binaries {
            framework { baseName = "ktmidi" }
        }
    }

    val appleTargets = listOf(
        macosArm64(),
        macosX64()
    ) + iosTargets
    linuxArm64()
    linuxX64()
    mingwX64()

    /*
    appleTargets.onEach {
        it.compilations.getByName("main") {
            cinterops {
                val coremidi by creating {
                    defFile = File(project.projectDir, "src/appleMain/coremidi.def")
                    packageName("dev.atsushieno.ktmidi.coremidi")
                    compilerOpts("-FCoreMIDI")
                }
            }
        }
    }*/

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-annotations-common"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.io)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.ktor.io)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(npm("jzz", "1.7.7"))
                implementation(libs.ktor.io)
            }
        }
        val jsTest by getting
        val wasmJsMain by getting {
            dependencies {
                implementation(npm("jzz", "1.7.7"))
            }
        }
        val wasmJsTest by getting
        val nativeMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.ktor.io)
            }
        }
        val nativeTest by creating
        val linuxArm64Main by getting {
            dependsOn(nativeMain)
        }
        val linuxX64Main by getting {
            dependsOn(nativeMain)
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
        val iosMain by creating { dependsOn(appleMain) }
        val iosTest by creating { dependsOn(appleTest) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosArm64Test by getting { dependsOn(iosTest) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Test by getting { dependsOn(iosTest) }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosX64Test by getting { dependsOn(iosTest) }
    }
}

android {
    namespace = "dev.atsushieno.ktmidi"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    kotlin {
        jvmToolchain(17)
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["test"].assets.srcDir("src/commonTest/resources") // kind of hack...

    defaultConfig {
        // FIXME: replace this constant with valid value once Gradle/AGP fixed the relevant crasher bug.
        minSdk = 23//libs.versions.android.minSdk.toString().toInt()
    }
    buildTypes {
        val debug by getting
        val release by getting
    }
}

ext["moduleDescription"] = "Kotlin Multiplatform library for MIDI 1.0 and MIDI 2.0"

apply { from("../publish-pom.gradle") }
