import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath("me.tylerbwong.gradle:metalava-gradle:0.2.3")
    }
}

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("maven-publish")
    id("signing")
    id("me.tylerbwong.gradle.metalava") version "0.2.3"
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    android {
        publishLibraryVariantsGroupedByFlavor = true
        publishLibraryVariants("debug", "release")
    }
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
    }
    iosX64("ios") {
        binaries {
            framework {
                baseName = "library"
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val isMacOSX64 = hostOs == "Mac OS X"
    val isLinuxX64 = hostOs == "Linux"
    val nativeTarget = when {
        isMacOSX64 -> macosX64("native") {
            binaries {
                staticLib {}
                sharedLib {}
            }
        }
        isLinuxX64 -> linuxX64("native") {
            binaries {
                staticLib {}
                sharedLib {}
            }
        }
        isMingwX64 -> mingwX64("native") {
            binaries {
                staticLib {}
                sharedLib {}
            }
        }
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("io.ktor:ktor-io:2.1.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
            }
        }
        val jvmMain by getting
        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.9.0")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
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
        val nativeMain by getting
        val nativeTest by getting
        val iosMain by getting
        val iosTest by getting
    }
}

metalava {
    filename = "api/$name-api.txt"
    outputKotlinNulls = false
    includeSignatureVersion = false
}

android {
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["test"].assets.srcDir("src/commonTest/resources") // kind of hack...
    defaultConfig {
        compileSdk = 33
        minSdk = 24
        targetSdk = 33
    }
    buildTypes {
        val debug by getting
        val release by getting
    }
}

afterEvaluate {
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
            /*
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/atsushieno/ktmidi")
                    credentials {
                        username = project.findProperty("gpr.user") ?: System.getenv("USERNAME")
                        password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
                    }
                }
                */
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
