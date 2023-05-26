import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests

plugins {
    kotlin("multiplatform")
    id("maven-publish")
    id("signing")
}

kotlin {
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
    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val rtmidi by creating {
                    packageName("dev.atsushieno.rtmidicinterop")
                    includeDirs.allHeaders("../external/rtmidi")
                }
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
                implementation("io.ktor:ktor-io:2.1.0")
                implementation(project(":ktmidi"))
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
        val nativeMain by getting
        val nativeTest by getting
        val iosMain by getting
        val iosTest by getting
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
                description.set("Kotlin Multiplatform library for MIDI 1.0 and MIDI 2.0 - Native specific")
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
