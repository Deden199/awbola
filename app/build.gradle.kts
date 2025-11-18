plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    alias(libs.plugins.google.gms.google.services)         // FIX kapt
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Slider + UI
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)      // ⬅️ pakai ini
    implementation(libs.androidx.cardview)

    // Glide
    implementation("com.github.bumptech.glide:glide:5.0.5")
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
