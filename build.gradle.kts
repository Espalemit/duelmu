// Root build file. AGP 9 memakai Kotlin bawaan (built-in Kotlin); plugin kotlin-android
// dideklarasikan `apply false` hanya untuk mematok versi Kotlin Gradle Plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
