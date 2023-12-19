import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath(libs.metalava.gradle)
    }
}

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
    // FIXME: re-enable metalava when we could migrate to Gradle 8.x
    //id("me.tylerbwong.gradle.metalava")
}

kotlin {
    /* we need more deps to support wasm target (in coroutines, ktor-io, datetime, etc.)
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "midi-ci-tool"
        browser {
        }
        nodejs {
        }
    }
    */

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

    iosArm64 { binaries { framework { baseName = "ktmidi" } } }
    iosX64 { binaries { framework { baseName = "ktmidi" } } }
    iosSimulatorArm64 { binaries {framework { baseName = "ktmidi" } } }

    macosArm64()
    macosX64()
    linuxArm64()
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
                implementation(libs.androidx.core.ktx)
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
                implementation(npm("jzz", "1.7.6"))
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
        val linuxArm64Main by getting
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

// FIXME: re-enable metalava when we could migrate to Gradle 8.x
//metalava {}

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

afterEvaluate {
    publishing {
        publications.withType<MavenPublication> {
            // https://github.com/gradle/gradle/issues/26091#issuecomment-1681343496
            val dokkaJar = project.tasks.register("${name}DokkaJar", Jar::class) {
                group = JavaBasePlugin.DOCUMENTATION_GROUP
                description = "Assembles Kotlin docs with Dokka into a Javadoc jar"
                archiveClassifier.set("javadoc")
                from(tasks.named("dokkaHtml"))

                // Each archive name should be distinct, to avoid implicit dependency issues.
                // We use the same format as the sources Jar tasks.
                // https://youtrack.jetbrains.com/issue/KT-46466
                archiveBaseName.set("${archiveBaseName.get()}-${name}")
            }
            artifact(dokkaJar)

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
}
