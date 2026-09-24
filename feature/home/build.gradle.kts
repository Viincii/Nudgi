plugins {
    alias(libs.plugins.nudgi.android.feature)
}

android {
    namespace = "com.vincentmignot.nudgi.feature.home"
}

dependencies {
    implementation(project(":core:nudge"))
    implementation(project(":core:today"))

    testImplementation(libs.kotlinx.coroutines.test)
}
