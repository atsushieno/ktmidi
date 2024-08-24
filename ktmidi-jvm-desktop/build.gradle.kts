plugins {
    kotlin("multiplatform")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka")
}

kotlin {
    jvmToolchain(11)

    jvm {
        java {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        //test { useJUnit() }
        //compileKotlin { kotlinOptions.jvmTarget = "11" }
        //compileTestKotlin.kotlinOptions.jvmTarget = "11
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":ktmidi"))
                implementation(libs.alsa.javacpp)
                api(libs.rtmidi.javacpp)
                api(libs.libremidi.javacpp)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
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

var repositoryId = System.getenv("OSSRH_STAGING_REPOSITORY_ID")
var moduleDescription = "Kotlin Multiplatform library for MIDI 1.0 and MIDI 2.0 - ALSA and RtMidi"
afterEvaluate {
    publishing {
        publications {
            publications.withType<MavenPublication>{
                // https://github.com/gradle/gradle/issues/26091#issuecomment-1681343496
                var dokkaJar = project.tasks.register("${name}DokkaJar", Jar::class) {
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

