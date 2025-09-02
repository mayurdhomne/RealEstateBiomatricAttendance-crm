plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace = "com.crm.realestate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.crm.realestate"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    configurations.all {
        exclude(group = "com.android.support", module = "support-compat")
        exclude(group = "com.android.support", module = "support-annotations")
        exclude(group = "com.android.support", module = "support-v4")
        exclude(group = "com.android.support", module = "versionedparcelable")
        exclude(group = "com.android.support", module = "animated-vector-drawable")
        exclude(group = "com.android.support", module = "support-vector-drawable")
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/androidx.*.version"
            excludes += "META-INF/com.android.support.*.version"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
    // Test dependencies for unit testing
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.kotlinx.coroutines.test)
    
    // Additional test dependencies
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    
    // Android Test dependencies
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    
    // Room testing support
    testImplementation("androidx.room:room-testing:2.5.0")
    androidTestImplementation("androidx.room:room-testing:2.5.0")

    // ML Kit Face Detection Library
    implementation(libs.face.detection)

    // Google Play Services ML Kit Face Detection
    implementation(libs.play.services.mlkit.face.detection)

    // CameraX for live face scanning (Optional but recommended)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Glide for loading images (Optional)
    implementation(libs.glide)
    kapt(libs.compiler)

    // TensorFlow Lite (Optional if you want advanced ML features)
    implementation(libs.tensorflow.lite)

    // Fingerprint
    implementation(libs.androidx.biometric)


    implementation(libs.androidx.multidex)
    implementation(libs.mpandroidchart)
    implementation(libs.anychart.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth) // Add if using Firebase Auth
    implementation(libs.firebase.firestore)

    // Retrofit for API communication
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // Room for local database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Location services
    implementation(libs.play.services.location)
    
    // Security for encrypted preferences
    implementation(libs.androidx.security.crypto)
    
    // WorkManager for background sync
    implementation(libs.androidx.work.runtime.ktx)
    
    // ViewModel and LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    
    // Lottie Animation
    implementation(libs.lottie)
    
    // MockK for testing
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.android)
    
    // Robolectric for Android unit tests
    testImplementation(libs.robolectric)
    
    // Espresso intents testing
    androidTestImplementation(libs.androidx.espresso.intents)
    
    // Espresso matchers
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.hamcrest)
    
    // Mockito for Android testing
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.mockito.core)
    
    // Kotlinx Coroutines Test for androidTest
    androidTestImplementation(libs.kotlinx.coroutines.test)
}