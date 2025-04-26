// /Users/tmelisma/Development/VoiceFlow-Android/settings.gradle.kts
import java.io.File // Required for File class usage

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // It's good practice to also define plugin versions via the catalog
    // See the refactoring section below. For now, keeping explicit versions.
    plugins {
        id("com.android.application") version "8.9.1" apply false
        id("com.android.library") version "8.9.1" apply false
        id("org.jetbrains.kotlin.android") version "2.0.21" apply false
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add any other project-wide repositories here (e.g., Jitpack, custom Nexus)
    }

    // NO versionCatalogs block needed here!
    // Gradle will automatically find 'gradle/libs.versions.toml'
    // and make it available as 'libs' by convention.
    // The previous explicit 'create("libs").from(...)' conflicted with this.
}

rootProject.name = "VoiceFlow"

// Include your app and library modules
include(":app")
include(":SpeechSDK")

// Keep existing logic for including SalesforceMobileSDK build if present
// (This block is currently inactive since the directory doesn't exist, which is fine)
val salesforceMobileSdkRoot = File("mobile_sdk/SalesforceMobileSDK-Android")
if (salesforceMobileSdkRoot.exists()) {
    includeBuild(salesforceMobileSdkRoot)
}