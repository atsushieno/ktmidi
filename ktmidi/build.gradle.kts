buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
}

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
    id("me.tylerbwong.gradle.metalava")
}

kotlin {
    jvm {
        jvmToolchain(17)
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    android {
        publishLibraryVariantsGroupedByFlavor = true
        publishLibraryVariants("debug", "release")
    }
    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    //webpackConfig.cssSupport.enabled = true
                }
            }
        }
        nodejs {
        }
    }

    iosArm64 { binaries { framework { baseName = "ktmidi" } } }
    iosX64 { binaries { framework { baseName = "ktmidi" } } }
    iosSimulatorArm64 { binaries {framework { baseName = "ktmidi" } } }

    macosArm64()
    macosX64()
    // kotlinx-datetime is not built enough to make it possible...
    //linuxArm64()
    linuxX64()
    mingwX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.io)
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
        val jvmMain by getting
        val androidMain by getting {
            dependencies {
                implementation(libs.core.ktx)
            }
        }
        val androidTest by getting {
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
                implementation(npm("jzz", "1.4.7"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        val nativeMain by creating
        val nativeTest by creating
        val macosArm64Main by getting
        val macosX64Main by getting
        // kotlinx-datetime is not built enough to make it possible...
        //val linuxArm64Main by getting
        val linuxX64Main by getting
        val mingwX64Main by getting
        val iosArm64Main by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Main by getting
        val iosSimulatorArm64Test by getting
        val iosX64Main by getting
        val iosX64Test by getting
    }
}

metalava {
    filename = "api/$name-api.txt"
    outputKotlinNulls = false
    includeSignatureVersion = false
}

android {
    namespace = "dev.atsusheno.ktmidi"
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["test"].assets.srcDir("src/commonTest/resources") // kind of hack...
    defaultConfig {
        compileSdk = 33
        minSdk = 23
    }
    buildTypes {
        val debug by getting
        val release by getting
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar)
        pom {
            name.set("ktmidi")
            description.set("Kotlin Multiplatform library for MIDI 1.0 and MIDI 2.0")
            url.set("https://github.com/atsushieno/ktmidi")
            scm {
                url.set("https://github.com/atsushieno/ktmidi")
            }
            licenses {
                license {
                    name.set("the MIT License")
                    url.set("https://github.com/atsushieno/ktmidi/blob/main/LICENSE")
                }
            }
            developers {
                developer {
                    id.set("atsushieno")
                    name.set("Atsushi Eno")
                    email.set("atsushieno@gmail.com")
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

// keep it as is. It is replaced by CI release builds
signing {}
