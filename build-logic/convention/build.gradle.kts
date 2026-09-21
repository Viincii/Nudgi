plugins {
    `kotlin-dsl`
}

group = "com.vincentmignot.nudgi.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "nudgi.android.application"
            implementationClass = "nudgi.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "nudgi.android.library"
            implementationClass = "nudgi.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "nudgi.android.compose"
            implementationClass = "nudgi.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "nudgi.android.feature"
            implementationClass = "nudgi.AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "nudgi.android.hilt"
            implementationClass = "nudgi.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "nudgi.android.room"
            implementationClass = "nudgi.AndroidRoomConventionPlugin"
        }
    }
}
