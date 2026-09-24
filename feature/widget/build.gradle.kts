plugins {
    alias(libs.plugins.nudgi.android.feature)
}

android {
    namespace = "com.vincentmignot.nudgi.feature.widget"
}

dependencies {
    implementation(project(":core:today"))
    implementation(libs.androidx.glance.appwidget)
}
