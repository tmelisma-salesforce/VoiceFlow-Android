// /Users/tmelisma/Development/VoiceFlow-Android/SpeechSDK/build.gradle.kts

// Import statements remain the same
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR

plugins {
    // Apply plugins by ID only - Versions are now managed in settings.gradle.kts
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
    `maven-publish`
}

android {
    namespace = "com.salesforce.speechsdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Ensure that we do not use newer language features that would make the SDK incompatible with
        // apps that do not target the latest version of Kotlin.
        apiVersion = "1.9"
        languageVersion = "1.9"
    }
}

dependencies {
    // Dependencies using the version catalog remain the same
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.material)
    implementation(libs.androidx.media3.exoplayer)
    testImplementation(libs.bundles.testLibs)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.espresso.core)
}

// Version, Jacoco, and Publishing blocks remain the same
version = rootProject.version // Make sure rootProject has a version defined or remove/change this

// ****************
// Jacoco Reporting
// ****************
val testTargetTask = "testDebugUnitTest"

tasks.register("jacocoTestReport", JacocoReport::class) {
    group = "Reporting"
    description = "Generates Jacoco coverage reports"
    val coverageSourceDirs = "src"
    val excludes = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/*\$ViewInjector*.*",
        "**/*\$ViewBinder*.*",
        "**/BuildConfig.*",
        "**/Manifest*.*",
    )
    sourceDirectories.from(files(coverageSourceDirs))
    classDirectories.from(fileTree("${project.layout.buildDirectory.get()}/intermediates/javac/debug/compileDebugJavaWithJavac/classes/") {
        exclude(excludes)
    } + fileTree("${project.layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(excludes)
    })

    val ecFiles = fileTree("${project.layout.buildDirectory.get()}") {
        include("outputs/unit_test_code_coverage/**/${testTargetTask}.exec")
    }
    ecFiles.forEach {
        logger.lifecycle("Reading Unit Test coverage from $it")
    }
    executionData.setFrom(ecFiles)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacoco_androidtests.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacoco_androidtests"))
    }
}

tasks.register<Exec>("viewUnitTestCoverage") {
    dependsOn("jacocoTestReport")
    group = "Reporting"
    commandLine(
        "open",
        "${project.layout.buildDirectory.get()}/reports/jacoco/jacoco_androidtests/index.html"
    )
}

tasks.withType<Test> {
    this.testLogging {
        this.showStandardStreams = true
        events(PASSED, FAILED, STANDARD_ERROR, SKIPPED)
        exceptionFormat = FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    extensions.configure(JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf(
            "jdk.internal.*"
        )
    }
}

afterEvaluate {
    tasks.named("check").configure {
        dependsOn(tasks.named("jacocoTestReport"))
    }
    tasks.named("jacocoTestReport").configure {
        dependsOn(tasks.named(testTargetTask))
    }

    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "SpeechSDK"
            }
        }

        repositories {
            maven {
                name = "Nexus"
                val releasesRepoUrl =
                    uri("https://nexus-proxy.repo.local.sfdc.net/nexus/content/repositories/releases")
                val snapshotsRepoUrl =
                    uri("https://nexus-proxy.repo.local.sfdc.net/nexus/content/repositories/snapshots")
                url = if (version.toString()
                        .endsWith("SNAPSHOT")
                ) snapshotsRepoUrl else releasesRepoUrl

                // nexusUsername, nexusPassword are set in ~/.gradle/gradle.properties by the "GradleInit" method in SFCI
                // Ensure these properties are available in your project or remove/comment out this credentials block if not needed
                // val nexusUsername: String by project
                // val nexusPassword: String by project
                // credentials {
                //     username = nexusUsername
                //     password = nexusPassword
                // }
            }
        }
    }
}