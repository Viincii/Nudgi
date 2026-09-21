package nudgi

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

private const val COMPILE_SDK = 37
private const val MIN_SDK = 31

/** Shared Android settings for every module, application or library. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.apply {
        compileSdk = COMPILE_SDK

        defaultConfig.minSdk = MIN_SDK

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17

        lint.abortOnError = true
        lint.checkDependencies = true
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false))
        }
    }

    dependencies {
        add("testImplementation", libs.library("junit"))
    }
}
