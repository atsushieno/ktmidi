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
                implementation(npm("jzz", libs.versions.jzz.get()))
                implementation(libs.ktor.io)
            }
        }
        val jsTest by getting
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(npm("jzz", libs.versions.jzz.get()))
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
        val macosMain by creating { dependsOn(appleMain) }
        val macosArm64Main by getting { dependsOn(macosMain) }
        val macosX64Main by getting { dependsOn(macosMain) }
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
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    buildTypes {
        val debug by getting
        val release by getting
    }
}

val repositoryId: String? = System.getenv("OSSRH_STAGING_REPOSITORY_ID")
val moduleDescription = "Kotlin Multiplatform library for MIDI 1.0 and MIDI 2.0"
// copypasting
afterEvaluate {
    publishing {
        publications {
            publications.withType<MavenPublication>{
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
                    name.set("$name")
                    description.set(moduleDescription)
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
        }

        repositories {
            maven {
                name = "OSSRH"
                //url = uri("https://s01.oss.sonatype.org/service/local/staging/deployByRepositoryId/${repositoryId}/")
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
