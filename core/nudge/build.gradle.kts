plugins {
    alias(libs.plugins.nudgi.android.library)
    alias(libs.plugins.nudgi.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vincentmignot.nudgi.core.nudge"
}

dependencies {
    implementation(project(":core:bandit"))
    implementation(project(":core:database"))
    implementation(project(":core:mascot"))
    implementation(project(":core:usagestats"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
}
