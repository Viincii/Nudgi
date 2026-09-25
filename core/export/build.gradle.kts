plugins {
    alias(libs.plugins.nudgi.android.library)
    alias(libs.plugins.nudgi.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vincentmignot.nudgi.core.export"
}

dependencies {
    implementation(project(":core:database"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
}
