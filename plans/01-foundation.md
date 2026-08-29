# Phase 1 — Foundation

**Goal:** a Kotlin/Compose Android project that assembles a debug APK, with Hilt, Navigation, the design system, and localisation scaffolding in place — and with the three unverified toolchain risks from the API contract resolved empirically before any feature code exists.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) — all of it, especially §0.1, §0.2, §0.3, §0.4, §0.10.
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "Project Structure", "UI/UX Requirements", "UI Colour System", "Multilingual Requirements", "Architecture".

## Preconditions

Nothing is installed. Assume a bare machine with JDK 21 and Node 22.

---

## Tasks

### 1.1 Install the toolchain

- Install Android Studio (current stable) **or** the command-line tools + platform SDK.
- Install SDK Platform **API 37** and Build Tools **36.0.0**.
- Install an emulator system image at **API 26 or higher** (contract §0.3 — JUnit 5 instrumented tests are disabled below 26).
- Set `ANDROID_HOME`, accept licenses (`sdkmanager --licenses`).
- Install the Supabase CLI (needed from Phase 2 onward). Node 22 is present, so `npx supabase` is acceptable if a global install is awkward on Windows.

### 1.2 Create the Gradle project

Copy the exact `libs.versions.toml`, `settings.gradle.kts`, root `build.gradle.kts`, and `app/build.gradle.kts` shapes from contract §0.2 and §0.4. Specifically:

- Version catalog at `gradle/libs.versions.toml` with every pinned version from §0.4.
- `settings.gradle.kts` with `pluginManagement` repos `google()`, `mavenCentral()`, `gradlePluginPortal()` and `dependencyResolutionManagement` with `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.
- Root `build.gradle.kts` with a `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21"); classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11") } }` block **above** the `plugins { }` block, then every plugin `apply false`.
- `app/build.gradle.kts` with `namespace = "com.varisahayak"`, `applicationId = "com.varisahayak"`, `compileSdk = 37`, `minSdk = 23`, `targetSdk = 36`.
- Top-level `kotlin { compilerOptions { languageVersion = KotlinVersion.KOTLIN_2_3 } }` — **not** `android { kotlinOptions { } }`.
- `buildFeatures { compose = true; buildConfig = true }` — `buildConfig` is no longer on by default.
- `compileOptions { isCoreLibraryDesugaringEnabled = true }` plus a `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:<current>")` dependency. Required because supabase-kt's stated Android minimum is 26 and we are on 23 (contract §0.3 item 4). Look up the current `desugar_jdk_libs` version — do not guess.
- `gradle.properties` per contract: `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, configuration cache on. **No** `enableJetifier`, **no** `builtInKotlin=false`.

### 1.3 GATE — resolve the three unverified toolchain risks

Do these before writing any UI. Each has a defined stop condition.

**Gate A — Compose BOM minSdk.** Add Compose BOM 2026.08.00 and run a build at `minSdk = 23`. If the manifest merger or dependency resolution rejects it because a Compose artifact requires API 24:
- **STOP. Report to the user.** Present the two options: (a) drop to the newest Compose BOM that supports 23, or (b) raise `minSdk` to 24 and drop the Navigation 2.9.8 pin. Do not choose on their behalf — the PRD explicitly fixes minSdk at 23.

**Gate B — Compose compiler plugin + AGP built-in Kotlin.** Write one trivial `@Composable` and compile it. No official page shows `org.jetbrains.kotlin.plugin.compose` applied alongside AGP 9 built-in Kotlin; this is an inference in the contract. If it fails, report the exact error before improvising.

**Gate C — desugaring at minSdk 23.** Add the supabase-kt BOM + `auth-kt` + `ktor-client-okhttp:3.5.1` and confirm the project still assembles at minSdk 23 with desugaring enabled.

Record the outcome of all three gates in a short note at the top of `plans/01-foundation.md` or in the commit message, so later phases know they passed.

### 1.4 Secrets plumbing

- Create `local.properties` entries (git-ignored) for `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_MAPS_API_KEY`.
- Read them in `app/build.gradle.kts` and expose via `buildConfigField` / `manifestPlaceholders`.
- Commit a `local.properties.example` with empty values.
- Confirm `.gitignore` covers `local.properties`, `google-services.json`, `*.jks`, `supabase/.env*`.
- **`GEMINI_API_KEY` and any service-role key must never appear anywhere under `app/`** (contract §0.10).

### 1.5 Package skeleton

Create the package tree from the PRD's "Project Structure" section under `app/src/main/java/com/varisahayak/`: `app/`, `core/{common,designsystem,network,permissions,location,utils}/`, `data/{local,remote,repository}/`, `domain/{model,repository,usecase}/`, `feature/{auth,dashboard,incidents,sos,map,qr,lostfound,communication,documentation,notifications,profile}/`.

Empty packages are fine at this stage. Do not create speculative classes.

### 1.6 Hilt

- `@HiltAndroidApp class VariSahayakApplication : Application()`, registered in the manifest.
- `@AndroidEntryPoint class MainActivity : ComponentActivity()`.
- One `@Module @InstallIn(SingletonComponent::class) object CoreModule` — empty for now.
- Wire Hilt with **`ksp(libs.hilt.compiler)`**, never `kapt`.

### 1.7 Design system

Build `core/designsystem/` with the PRD's semantic tokens: `Primary`, `PrimaryContainer`, `Critical`, `Warning`, `Success`, `Info`, `Surface`, `Error`, `OnSurface`, `OnSurfaceVariant`.

- Material 3 `ColorScheme` for light and dark, plus an extension holder for the tokens M3 does not natively carry (`Critical`, `Warning`, `Success`, `Info`) exposed through a `CompositionLocal`.
- High-contrast, outdoor-readable values. Type scale and spacing constants.
- A `PriorityBadge` composable that pairs colour with **an icon and a text label** — the PRD forbids communicating priority by colour alone.
- A minimum touch target constant of **48dp**, applied by the shared button/list-item components.

### 1.8 Navigation skeleton

- Navigation Compose **2.9.8**.
- A `NavHost` with placeholder destinations for: auth, dashboard, incident list, incident report, map, QR, profile.
- Type-safe routes via a sealed hierarchy. No string literals scattered through composables.

### 1.9 Localisation scaffolding

- `res/values/strings.xml`, `res/values-hi/strings.xml`, `res/values-mr/strings.xml`.
- Every user-facing string in this phase must already be a resource. **No hardcoded strings inside composables** — this is a PRD requirement and it is far cheaper to hold from day one than to retrofit.
- Hindi and Marathi files may start as copies of English with a `TODO` marker, but the keys must exist.

---

## Verification checklist

- [ ] `gradlew :app:assembleDebug` succeeds.
- [ ] `gradlew :app:installDebug` onto an API 26+ emulator launches to the navigation skeleton.
- [ ] Gate A, B, and C outcomes are recorded; none is unresolved.
- [ ] `gradlew :app:dependencies` shows `navigation-compose:2.9.8`, `room-runtime:2.8.4`, and `ktor-client-okhttp:3.5.1` — and does **not** show `ktor-client-android` or `androidx.room3`.
- [ ] `git grep -nE "kotlinOptions|kapt\(|kotlinCompilerExtensionVersion|jcenter|org.jetbrains.kotlin.android|enableJetifier"` returns nothing.
- [ ] `git grep -nE "GEMINI_API_KEY|SERVICE_ROLE" -- app/` returns nothing.
- [ ] `git check-ignore local.properties` confirms it is ignored.
- [ ] `git grep -nE 'text\s*=\s*"' -- app/src/main` returns nothing (no hardcoded UI strings).
- [ ] Switching device language to Hindi and Marathi loads the corresponding `strings.xml` without crashing.
- [ ] Every interactive component in the skeleton measures at least 48dp.

## Anti-pattern guards

- Do **not** add `org.jetbrains.kotlin.android`. AGP 9 provides Kotlin.
- Do **not** use `kapt` for Hilt or Room. KSP only.
- Do **not** set `composeOptions { kotlinCompilerExtensionVersion }`.
- Do **not** "upgrade" Navigation to 2.10.0 or Room to `androidx.room3` — both break minSdk 23.
- Do **not** add `ktor-client-android`; Realtime in Phase 6 will fail at runtime, not compile time.
- Do **not** add libraries for future-scope features (voice, SMS/IVR, analytics, gamification).
- Do **not** hardcode colours in composables. Tokens only.

## Done when

The app builds and runs, all three gates are resolved, and no banned pattern appears in the tree.
