plugins {
    alias(libs.plugins.nudgi.android.feature)
}

android {
    namespace = "com.vincentmignot.nudgi.feature.friction"
}

dependencies {
    implementation(project(":core:accessibility"))
    implementation(project(":core:nudge"))
}
