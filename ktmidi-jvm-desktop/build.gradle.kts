plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.maven.publish)
}

kotlin {
    jvmToolchain(22)

    jvm {
        java {
            sourceCompatibility = JavaVersion.VERSION_22
            targetCompatibility = JavaVersion.VERSION_22
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
                implementation(libs.libremidi.panama)

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

val gitProjectName = "ktmidi"
val packageName = project.name
val packageDescription = "Kotlin Multiplatform library for MIDI 1.0 and MIDI 2.0 - JVM desktop-specific components"
// my common settings
val packageUrl = "https://github.com/atsushieno/$gitProjectName"
val licenseName = "MIT"
val licenseUrl = "https://github.com/atsushieno/$gitProjectName/blob/main/LICENSE"
val devId = "atsushieno"
val devName = "Atsushi Eno"
val devEmail = "atsushieno@gmail.com"

// Common copy-pasted
mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    if (project.hasProperty("mavenCentralUsername") || System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername") != null)
        signAllPublications()
    coordinates(group.toString(), project.name, version.toString())
    pom {
        name.set(packageName)
        description.set(packageDescription)
        url.set(packageUrl)
        scm { url.set(packageUrl) }
        licenses { license { name.set(licenseName); url.set(licenseUrl) } }
        developers { developer { id.set(devId); name.set(devName); email.set(devEmail) } }
    }
}
