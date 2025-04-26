plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") // Use standard ID
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" // Keep explicit version for this one as needed previously
}

dependencies {
    implementation("com.salesforce.mobilesdk:MobileSync:13.0.0")
    implementation(project(":SpeechSDK"))

    val composeBomVersion = "2024.04.01"
    val activityComposeVersion = "1.9.0"
    val lifecycleVersion = "2.7.0"
    val coreKtxVersion = "1.13.1"
    val junitVersion = "4.13.2"
    val androidxJunitVersion = "1.1.5"
    val espressoCoreVersion = "3.5.1"

    implementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBomVersion"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.activity:activity-compose:$activityComposeVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    // implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion") // If needed

    testImplementation("junit:junit:$junitVersion")
    androidTestImplementation("androidx.test.ext:junit:$androidxJunitVersion")
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoCoreVersion")

}

android {
    namespace = "com.salesforce.voiceflow"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.salesforce.voiceflow"
        targetSdk = 35
        minSdk = 28
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE",
                "/META-INF/{AL2.0,LGPL2.1}"
            )
        }
    }

    buildFeatures {
        renderScript = true
        aidl = true
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}