// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Use a valid Android Gradle Plugin version so the build can resolve
    // dependencies correctly. The previous version "8.10.0" does not exist
    // and causes the build to fail.
    id("com.android.application") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
