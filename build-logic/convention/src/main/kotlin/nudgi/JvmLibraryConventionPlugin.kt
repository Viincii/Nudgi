package nudgi

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * A plain Kotlin module with no Android types, which Android modules can depend on and JVM tools
 * can run. Same Java level and warning policy as the Android modules.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                    allWarningsAsErrors.set(
                        providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false),
                    )
                }
            }

            dependencies {
                add("testImplementation", libs.library("junit"))
            }

            // CI and CLAUDE.md run every unit test with `testDebugUnitTest`, the Android task name.
            // Without this alias a JVM module's tests would silently drop out of that command.
            tasks.register("testDebugUnitTest") {
                group = "verification"
                description = "Runs the unit tests, under the name the Android modules use."
                dependsOn("test")
            }
        }
    }
}
