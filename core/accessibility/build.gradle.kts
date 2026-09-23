plugins {
    alias(libs.plugins.nudgi.android.library)
    alias(libs.plugins.nudgi.android.hilt)
}

android {
    namespace = "com.vincentmignot.nudgi.core.accessibility"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
