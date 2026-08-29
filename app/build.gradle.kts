plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.android.junit)
    // google-services and crashlytics are applied conditionally below: the project must
    // build without a google-services.json so a fresh clone is not blocked on Firebase.
}

// Client configuration is read from a git-ignored .env at the repository root, with an
// environment-variable fallback for CI. Server-only keys (SUPABASE_SERVICE_ROLE_KEY,
// GEMINI_API_KEY) are deliberately absent — those live in Supabase secrets and never
// reach the APK. See .env.example.
//
// Both sources go through Gradle's provider API rather than File.readLines() and
// System.getenv(): those are untracked reads, so with the configuration cache enabled an
// edit to .env would not invalidate the cache and you would silently rebuild with stale
// values.

fun parseDotEnv(text: String): Map<String, String> = text.lineSequence()
    .map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .mapNotNull { line ->
        val entry = line.removePrefix("export ").trim()
        val separator = entry.indexOf('=')
        if (separator <= 0) return@mapNotNull null

        val key = entry.substring(0, separator).trim()
        var value = entry.substring(separator + 1).trim()

        // A '#' inside a quoted value is data, not a comment.
        val quoted = value.startsWith("\"") || value.startsWith("'")
        if (!quoted) {
            val comment = value.indexOf(" #")
            if (comment >= 0) value = value.substring(0, comment).trim()
        }

        key to value.removeSurrounding("\"").removeSurrounding("'")
    }
    .toMap()

val dotEnv: Map<String, String> = providers
    .fileContents(rootProject.layout.projectDirectory.file(".env"))
    .asText
    .map(::parseDotEnv)
    .getOrElse(emptyMap())

fun secret(key: String): String =
    dotEnv[key] ?: providers.environmentVariable(key).orNull ?: ""

val hasFirebaseConfig = project.file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.crashlytics.get().pluginId)
}

android {
    namespace = "com.varisahayak"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.varisahayak"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.varisahayak.HiltTestRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
        buildConfigField("boolean", "HAS_FIREBASE", "$hasFirebaseConfig")

        manifestPlaceholders["googleMapsApiKey"] = secret("GOOGLE_MAPS_API_KEY")

        resourceConfigurations += listOf("en", "hi", "mr")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            // Minification stays ON. supabase-kt ships no consumer ProGuard rules
            // (contract §0.11 item 5), so proguard-rules.pro carries them instead.
            // If release crashes with SerializationException, add a keep rule — do not
            // disable minification.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true // no longer enabled by default in AGP 9
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required at minSdk 23: supabase-kt states an Android minimum of 26 and
        // directs lower targets to enable core library desugaring (contract §0.3).
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE.md",
            "/META-INF/LICENSE-notice.md",
        )
    }
}

// Replaces `android { kotlinOptions { … } }`, which AGP 9's built-in Kotlin removed.
// jvmTarget is deliberately unset — it defaults to compileOptions.targetCompatibility.
kotlin {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}

// schemaDirectory is mandatory once the Room Gradle plugin is applied.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // --- compose ---
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // --- androidx core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // --- hilt (KSP, never kapt) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // --- room ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- workmanager ---
    implementation(libs.androidx.work.runtime.ktx)

    // --- camerax + ml kit ---
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.mlkit.barcode.scanning)

    // --- maps + location ---
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)
    implementation(libs.maps.compose.widgets)
    implementation(libs.play.services.location)

    // --- firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // --- supabase ---
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    // OkHttp engine, not ktor-client-android: Realtime needs WebSocket support.
    implementation(libs.ktor.client.okhttp)

    // --- excel ---
    implementation(libs.poi.ooxml)

    // --- kotlinx ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // --- unit tests: JUnit 5 ---
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // --- instrumented tests: JUnit 4 world (contract §0.9) ---
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
