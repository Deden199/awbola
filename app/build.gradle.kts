plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.mod.aksesmudah"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mod.aksesmudah.awbola"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Dipakai REST Firestore
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"a8r-livestream\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyB1QDMW7QWyhnR98VdSxQcUXj-05lzZ24g\"")

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

    // wajib di AGP baru supaya BuildConfig.* kebentuk
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // AndroidX & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)

    // Glide
    implementation("com.github.bumptech.glide:glide:5.0.5")

    // ====================== FIREBASE & GMS ======================

    // BOM biar versi semua lib Firebase sinkron
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))

    // Firebase Analytics & Firestore (pakai -ktx biar idiomatic Kotlin)
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Google Play Services base (buat GoogleApiAvailability, dll)
    implementation("com.google.android.gms:play-services-base:18.5.0")

    // ============================================================

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
