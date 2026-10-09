plugins {
    alias(libs.plugins.nudgi.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.vincentmignot.nudgi.tools.simulator.MainKt")
}

// Paths given on the command line are relative to the repository, not to this module.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":core:bandit"))
    implementation(libs.kotlinx.serialization.json)
}
