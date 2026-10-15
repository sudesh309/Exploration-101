plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kidlearn.app"

    defaultConfig {
        applicationId = "com.kidlearn.app"
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // R8 shrinking keeps the APK small and the app's memory footprint low on budget phones.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Debug-signed so CI can produce an installable APK; use a real keystore for store releases.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // Course packs are already-compressed zips; storing them avoids double compression.
        noCompress += "zip"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:profiles"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:lesson"))
    implementation(project(":feature:media"))
    implementation(project(":feature:quiz"))
    implementation(project(":feature:results"))
    implementation(project(":feature:importer"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
}
