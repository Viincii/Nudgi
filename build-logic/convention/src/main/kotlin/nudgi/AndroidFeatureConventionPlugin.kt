package nudgi

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature module: an Android library with Compose, Hilt and the shared core modules. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("nudgi.android.library")
            pluginManager.apply("nudgi.android.compose")
            pluginManager.apply("nudgi.android.hilt")

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:mascot"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("androidx-navigation-compose"))
            }
        }
    }
}
