plugins {
    alias(libs.plugins.nudgi.android.library)
    alias(libs.plugins.nudgi.android.room)
    alias(libs.plugins.nudgi.android.hilt)
}

android {
    namespace = "com.vincentmignot.nudgi.core.database"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
