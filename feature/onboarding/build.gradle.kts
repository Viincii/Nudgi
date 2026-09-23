plugins {
    alias(libs.plugins.nudgi.android.feature)
}

android {
    namespace = "com.vincentmignot.nudgi.feature.onboarding"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:accessibility"))
    implementation(project(":core:nudge"))
    implementation(project(":core:usagestats"))

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
