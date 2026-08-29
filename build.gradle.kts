// AGP 9 carries a runtime dependency on KGP 2.2.10 and will silently upgrade a lower
// version. These classpath entries raise KGP/KSP to the pinned pair from
// plans/00-api-contract.md §0.2. This is the documented override mechanism — there is
// no `org.jetbrains.kotlin.android` plugin alias to set a version on any more.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.crashlytics) apply false
}
