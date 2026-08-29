# Phase 12 — Final Verification

**Goal:** prove, with commands rather than assertions, that the delivered project matches the API contract, the PRD, and the Definition of Done.

Run this as a fresh session. Verify; do not fix-and-verify in the same pass — if something fails, record it, fix it deliberately, then re-run the whole phase.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) — the entire file, especially §0.10.
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "Definition of Done", "Important Scope Boundary".

---

## 12.1 Banned-pattern sweep

Every one of these must return **no results**. Run from the repo root.

```bash
# Build system
git grep -nE "org\.jetbrains\.kotlin\.android|kotlin-android|kotlinCompilerExtensionVersion|kotlinOptions \{|jcenter\(\)|enableJetifier|builtInKotlin=false|newDsl=false"
git grep -nE "kapt\(|kotlin-kapt|org\.jetbrains\.kotlin\.kapt"
git grep -nE "applicationVariants\.all|variantFilter|dexOptions|registerTransform"

# Pinned-version violations
git grep -nE "androidx\.room3|navigation-compose:2\.1[0-9]|ktor-client-android"

# supabase-kt renames
git grep -nE "io\.github\.jan\.supabase\.gotrue|gotrue-kt|install\(GoTrue\)|supabase\.gotrue|loginWith\(|\.logout\(\)"
git grep -nE "createChannel\(|\.join\(\)|\.leave\(\)|postgrestChangeFlow|PostgrestAction|publicRenderUrl"
git grep -nE "SessionStatus\.LoadingFromStorage|SessionStatus\.NetworkError"

# Testing
git grep -nE "de\.mannodermaus\.android-junit5|io\.mockk:mockk:"

# Firebase
git grep -nE "onTokenRefresh|FirebaseInstanceId"

# Edge Functions / Gemini
git grep -nE "Deno\.serve\(|import \{ serve \}|--legacy-bundle"
git grep -nE "gemini-2\.0|gemini-1\.5|responseMimeType|responseSchema"
git grep -nE '"temperature"|"topP"|"topK"' -- supabase/

# SQL
grep -rn "auth.uid()" supabase/migrations/ | grep -v "(select auth.uid())"

# Secrets
git grep -nE "GEMINI_API_KEY|SERVICE_ROLE|service_role" -- app/
git grep -nE "AIza|eyJhbGciOi"
git log -p | grep -iE "service_role|GEMINI_API_KEY"
```

## 12.2 Build and test

```bash
gradlew :app:assembleDebug
gradlew :app:testDebugUnitTest
gradlew :app:connectedDebugAndroidTest    # API 26+ emulator
gradlew :app:assembleRelease              # minification ON
gradlew :app:lintDebug
```

- [ ] All five succeed.
- [ ] The **minified release APK** is installed on a real device and completes: sign-in, incident create + offline sync, realtime update, QR scan, map, notification.
- [ ] `unzip -p app/build/outputs/apk/release/*.apk | strings | grep -iE "service_role|AIza|GEMINI"` finds nothing.

## 12.3 Contract conformance

- [ ] `gradlew :app:dependencies` shows: AGP 9.3.0, Kotlin 2.3.21, KSP 2.3.11, navigation-compose **2.9.8**, room-runtime **2.8.4**, ktor-client-**okhttp** 3.5.1, supabase BOM 3.8.0, Hilt 2.60.1, CameraX 1.6.2, maps-compose 8.5.0, Firebase BOM 34.18.0.
- [ ] `org.jetbrains.kotlin.android` appears nowhere in any build file.
- [ ] Exactly one `play-services-maps` version resolves.
- [ ] Every `postgresChangeFlow` call site registers its collector before `subscribe()`.
- [ ] Every incident sync path uses `upsert` with `onConflict = "client_id"`, and every insert/update that needs a return value calls `select()`.
- [ ] The three Phase-1 gates (Compose BOM minSdk, compose plugin + built-in Kotlin, desugaring) are recorded as resolved.

## 12.4 Backend conformance

- [ ] `supabase db reset` reproduces the entire schema from migrations alone.
- [ ] Every `public` table has `rowsecurity = true`.
- [ ] Every row in `pg_policies` names `authenticated` in `roles`; every INSERT/UPDATE policy has a non-null `with_check`.
- [ ] Every policy filter column has an index.
- [ ] `pg_publication_tables` lists exactly the intended realtime tables.
- [ ] `delete` is revoked from `authenticated` on all realtime-published tables.
- [ ] Per-role Postgrest probes (direct HTTP, bypassing the app) confirm every access rule from Phase 2.

## 12.5 PRD Definition of Done

For each shipped feature:

- [ ] Functional requirements implemented.
- [ ] Loading, error, empty, **and offline** states handled.
- [ ] Authorisation enforced — server-side, not only in the client.
- [ ] Relevant local persistence implemented.
- [ ] Network failure does not silently lose data.
- [ ] Core business logic tested.
- [ ] Critical UI flows tested.
- [ ] No secrets committed.
- [ ] Code follows Clean Architecture + MVVM: no business logic in composables; ViewModels depend on use cases; use cases depend on repository interfaces; repositories own the Room/Supabase split.
- [ ] Accessibility satisfied: 48dp targets, content descriptions, 200% text scale, no colour-only priority.
- [ ] All user-facing strings localised in English, Hindi, and Marathi.
- [ ] Crash and error paths handled.

## 12.6 Critical safety properties

These are the ones that matter most in the field. Verify each by execution, not by reading code.

- [ ] **No locally captured incident silently disappears** — airplane mode, create 3, force-stop, reboot, reconnect → exactly 3 server-side.
- [ ] **Duplicate sync is a no-op** — run the sync worker repeatedly against the same pending record; row count is stable.
- [ ] **SOS always goes critical** — with Gemini disabled, with Gemini returning "low severity", and offline. All three.
- [ ] **The workflow survives Gemini being unavailable** — incident creation, prioritisation, matching, and notification all still work.
- [ ] **Backgrounding does not sign the user out** (the `SessionStatus.Initializing` trap).
- [ ] **Realtime disconnect recovers by reconciliation**, not by losing updates.
- [ ] **QR payloads contain no personal data** — decode a live token and confirm.
- [ ] **Route documentation is readable with no network.**
- [ ] **Session expiry retains unsynced local data.**

## 12.7 Scope boundary

- [ ] No voice-based incident reporting.
- [ ] No SMS/IVR.
- [ ] No direct ambulance dispatch.
- [ ] No predictive crowd analytics.
- [ ] No volunteer gamification.
- [ ] No other-gathering expansion features.
- [ ] No libraries present that exist only to serve the above.

## 12.8 Documentation

- [ ] Exactly three root Markdown files: `Readme.md`, `Project Summary.md`, `setup.md`.
- [ ] `setup.md` versions match `libs.versions.toml` and the API contract.
- [ ] A clean-machine walkthrough of `setup.md` reaches a running debug build.

---

## Reporting

Produce a single verification report listing, for every item above: **PASS**, **FAIL** (with the command output), or **NOT RUN** (with the reason). Do not report an item as passing because the code looks correct — every checkbox in §12.2 through §12.6 requires an executed command or an observed device behaviour.

If any item fails, fix it in a separate pass and re-run this phase in full.
