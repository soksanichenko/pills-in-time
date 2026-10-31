// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9's built-in Kotlin pins the compiler to its own default (2.2.10) unless a newer
// Kotlin Gradle Plugin is on the build classpath. Without this, bumping `kotlin` in the
// version catalog only moves the compose/serialization plugins and stdlib, and the
// compiler then rejects libraries built with newer Kotlin metadata.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}

// Lock the release runtime classpath: the dependencies that ship in the app. The
// daily security scan (osv-scanner) reads the resulting gradle.lockfile, and
// build, lint and test tooling is deliberately left out so its findings do not
// bury the ones that matter. Refresh the lock after changing dependencies with
// `./gradlew :app:dependencies --configuration releaseRuntimeClasspath --write-locks`.
allprojects {
    configurations.configureEach {
        if (name == "releaseRuntimeClasspath") {
            resolutionStrategy.activateDependencyLocking()
        }
    }
}
