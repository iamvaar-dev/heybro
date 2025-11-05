plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.vibeagent.dude"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { jvmTarget = "1.8" }

    defaultConfig {
        applicationId = "com.vibeagent.dude"
        minSdk = 24
        targetSdk = 34
        versionCode = flutter.versionCode
        versionName = flutter.versionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    buildFeatures { buildConfig = true }

    // sourceSets {
    //     getByName("main") {
    
    //     }
    // }
}

flutter { source = "../.." }

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // HTTP client for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // JSON handling
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Add coroutines Task await extensions
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Porcupine Wake Word Detection
    implementation("ai.picovoice:porcupine-android:3.0.1")

    // ML Kit Text Recognition for on-device OCR
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Work Manager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// chaquopy {
//     defaultConfig {
//         version = "3.11"
//
//         pip {
//             install("requests")
//             install("pillow")
//         }
//     }
//
//     sourceSets {
//         getByName("main") {

//         }
//     }
// }
