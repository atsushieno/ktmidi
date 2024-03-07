import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.libsDirectory

plugins {
    kotlin("multiplatform")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka")
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val rtmidi by creating {
                    packageName("dev.atsushieno.rtmidicinterop")
                    includeDirs.allHeaders("../external/rtmidi/dist-shared/include")
                }
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
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
    }
}


// https://github.com/gradle/gradle/issues/26091#issuecomment-1722947958
tasks.withType<AbstractPublishToMaven>().configureEach {
    val signingTasks = tasks.withType<Sign>()
    mustRunAfter(signingTasks)
}

tasks {
    val dokkaOutputDir = "${layout.buildDirectory}/dokka"

    dokkaHtml {
        outputDirectory.set(file(dokkaOutputDir))
    }

    val deleteDokkaOutputDir by registering(Delete::class) {
        delete(dokkaOutputDir)
    }

    register<Jar>("javadocJar") {
        dependsOn(deleteDokkaOutputDir, dokkaHtml)
        archiveClassifier.set("javadoc")
        from(dokkaOutputDir)
    }
}

ext["moduleDescription"] = "Kotlin Multiplatform library for MIDI 1.0 and MIDI 2.0 - Native specific"
afterEvaluate {
    apply { from("../publish-pom.gradle") }
}
