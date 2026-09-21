plugins {
    alias(libs.plugins.nudgi.android.application)
    alias(libs.plugins.nudgi.android.compose)
    alias(libs.plugins.nudgi.android.hilt)
}

android {
    namespace = "com.vincentmignot.nudgi"

    defaultConfig {
        applicationId = "com.vincentmignot.nudgi"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":feature:home"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
