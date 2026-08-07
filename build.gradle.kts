// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // AGP 9 부터 Kotlin 지원이 내장되어 kotlin-android 플러그인은 적용하지 않는다.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
