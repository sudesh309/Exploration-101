plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kidlearn.feature.importer"
}

dependencies {
    implementation(project(":core:ui"))
}

dependencies {
    implementation(libs.androidx.activity.compose)
}
