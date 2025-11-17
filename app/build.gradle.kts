plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // ADDED: For Room's compiler
}

android {
    namespace = "com.vibereader"
    compileSdk = 36 // Your 'release(36)' is fine, just showing the number

    defaultConfig {
        applicationId = "com.vibereader"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
    }
}

// ADDED: Configuration for Room's schema location
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // --- ADDED: ViewModel & Service Lifecycle ---
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose) // For screen navigation

    // --- ADDED: Room (Database) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)          // For Coroutine support
    ksp(libs.androidx.room.compiler)                // Annotation processor

    // --- ADDED: MediaSession (for Lock Screen Controls) ---
    implementation(libs.androidx.media)

    // --- ADDED: Retrofit (for Dictionary API) ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // --- ADDED: Coroutines ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.accompanist.permissions)

    // --- Standard Test Dependencies ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}