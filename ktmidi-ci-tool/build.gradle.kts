@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.gradleJavacppPlatform) // required to resolve rtmidi-javacpp-platform appropriately
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "ktmidi-ci-tool.js"
            }
        }
        //nodejs {}
        binaries.executable()
    }

    androidTarget {
    }
    
    jvm("desktop")
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "KtMidiCITool"
            isStatic = true
        }
    }
    
    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(libs.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.runtime)
            implementation(libs.foundation)
            implementation(libs.material3)
            implementation(libs.ui)
            implementation(libs.components.resources)
            implementation(libs.ui.tooling.preview)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.filekit.dialogs.compose)
            //implementation(libs.ktor.io)
            implementation(project(":ktmidi"))
            implementation(project(":ktmidi-ci"))
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(project(":ktmidi-jvm-desktop"))
            // without this, jnirtmidi.so will not be found at runtime.
            //api(libs.rtmidi.javacpp.platform)
            implementation(libs.kotlinx.coroutines.swing)
        }

        val iosMain by creating {
            dependsOn(commonMain.get())
            dependencies {
            }
        }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(libs.filekit.dialogs.compose)
            }
        }
    }
}

android {
    namespace = "dev.atsushieno.ktmidi.citool"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "dev.atsushieno.ktmidi.citool"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    dependencies {
        debugImplementation(compose.uiTooling)
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.atsushieno.ktmidi.citool"
            packageVersion = "1.0.0"
        }
    }
}
