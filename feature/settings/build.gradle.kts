plugins {
    alias(libs.plugins.nudgi.android.feature)
}

android {
    namespace = "com.vincentmignot.nudgi.feature.settings"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:export"))
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
