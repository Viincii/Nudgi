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

    signingConfigs {
        // A debug key committed with the project, so APKs from CI and from any machine sign alike and install
        // over each other. The default one is generated per machine, so each CI run had a different signature.
        // Debug only: release builds must never use it.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    implementation(project(":core:accessibility"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:nudge"))
    implementation(project(":core:usagestats"))
    implementation(project(":feature:home"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:widget"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
}
