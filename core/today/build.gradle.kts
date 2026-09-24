plugins {
    alias(libs.plugins.nudgi.android.library)
    alias(libs.plugins.nudgi.android.hilt)
}

android {
    namespace = "com.vincentmignot.nudgi.core.today"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:mascot"))
    implementation(project(":core:nudge"))
    implementation(project(":core:usagestats"))

    testImplementation(libs.kotlinx.coroutines.test)
}
