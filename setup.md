# VARI Sahayak — Setup Guide

Everything needed to go from a clean machine to a running debug build, plus backend, Firebase, and AI configuration.

Version numbers here are pinned and must match `gradle/libs.versions.toml` and [plans/00-api-contract.md](plans/00-api-contract.md). If they drift, the contract file is correct.

---

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **17** or newer | Required by AGP 9.3. JDK 21 is fine. |
| Android Studio | Current stable | Or command-line SDK tools |
| Android SDK Platform | **API 37** | |
| SDK Build Tools | **36.0.0** | |
| Emulator system image | **API 26 or higher** | See §22 — JUnit 5 instrumented tests are disabled below API 26 |
| Node.js | 18+ | For the Supabase CLI |
| Supabase CLI | Current | `npm i -g supabase`, or use `npx supabase` |
| Git | Any recent | |

Gradle is **not** required — the wrapper provides Gradle 9.7.1.

Accept SDK licenses before the first build:

```bash
sdkmanager --licenses
```

## 2. Android Studio setup

1. Open the repository root as a project.
2. Let Studio write `local.properties` with `sdk.dir`. This file holds **only** the SDK path — no secrets live there.
3. Confirm **Settings → Build → Build Tools → Gradle → Gradle JDK** is set to JDK 17+.
4. Sync Gradle.

If you use the command line instead, create `local.properties` yourself:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## 3. Environment configuration

Client configuration comes from a git-ignored `.env` at the repository root.

```bash
cp .env.example .env
```

Fill in:

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-anon-or-publishable-key
GOOGLE_MAPS_API_KEY=your-restricted-android-maps-key
```

`app/build.gradle.kts` reads these through Gradle's provider API, so editing `.env` correctly invalidates the configuration cache. In CI, omit `.env` and set the same names as environment variables — the build falls back to them.

**These three values are compiled into the APK and are extractable from it.** That is by design: the Supabase anon key is meant to be public and is guarded by Row Level Security, and the Maps key is guarded by platform restriction. Keeping `.env` out of git prevents credential sprawl; it is not a confidentiality boundary.

**Never** put `SUPABASE_SERVICE_ROLE_KEY` or `GEMINI_API_KEY` in `.env`, `local.properties`, Gradle files, or app resources. See §15.

## 4. Kotlin DSL configuration

Three things about this build differ from most Android tutorials, all mandated by AGP 9:

1. **There is no `org.jetbrains.kotlin.android` plugin.** AGP 9 has built-in Kotlin support enabled by default. Do not add it back.
2. **Kotlin and KSP versions are raised in a root `buildscript` classpath block**, not through a plugin alias — AGP would otherwise pin KGP to its own bundled version.
3. **`android { kotlinOptions { } }` does not exist.** Compiler options live in a top-level `kotlin { compilerOptions { } }` block.

`kapt` is not used anywhere; Hilt and Room both run on KSP.

## 5. SDK requirements

```
compileSdk = 37     targetSdk = 36     minSdk = 23
```

**minSdk 23 constrains three dependencies. Do not "upgrade" past them:**

| Dependency | Pinned | Why |
|---|---|---|
| `navigation-compose` | **2.9.8** | 2.10.0 requires API 24 |
| `androidx.room` | **2.8.4** | Room 3 (`androidx.room3`) publishes no minSdk |
| Core library desugaring | **required** | supabase-kt states an Android minimum of 26 and directs lower targets to enable desugaring |

The Compose BOM's minSdk is not published — see §28.

## 6. Dependencies

Everything is declared in `gradle/libs.versions.toml`. Key pins:

AGP 9.3.0 · Gradle 9.7.1 · Kotlin 2.3.21 · KSP 2.3.11 · Compose BOM 2026.08.00 · Hilt 2.60.1 · Room 2.8.4 · WorkManager 2.11.2 · CameraX 1.6.2 · ML Kit barcode 17.3.0 · maps-compose 8.5.0 · play-services-location 21.4.0 · Firebase BOM 34.18.0 · supabase-kt BOM 3.8.0 · Ktor 3.5.1

**The Ktor engine must be `ktor-client-okhttp`.** `ktor-client-android` — which the official Supabase Android tutorial uses — has no WebSocket support, and Realtime fails at runtime with `Engine doesn't support WebSocketCapability`.

Do not declare `play-services-maps` directly; `maps-compose` pulls the correct version.

## 7. Supabase setup

```bash
supabase init
supabase login
supabase link --project-ref <PROJECT_REF>
```

Copy the project URL and anon key from **Project Settings → API** into `.env`.

## 8. Database migrations

All schema lives in `supabase/migrations/`. Never create objects through the dashboard SQL editor — the repository is the source of truth.

```bash
supabase migration new create_incidents_table   # author a change
supabase db reset                               # rebuild locally from scratch
supabase db push                                # apply to the linked project
supabase migration list                         # confirm local and remote agree
```

`supabase db reset` must reproduce the entire backend from migrations alone.

## 9. Authentication

Supabase Auth with email/password. In **Authentication → Providers**, enable Email.

The client installs the `Auth` plugin and needs no Context and no initializer — `auth-kt` registers an `androidx.startup` initializer that captures the application context itself.

**One behaviour will surprise you:** with lifecycle callbacks enabled (the default), `sessionStatus` emits `SessionStatus.Initializing` every time the app is backgrounded. Treat it as "unknown, hold" — mapping it to signed-out bounces the user to the login screen on every app switch.

## 10. Row Level Security

Enable RLS on **every** table and write explicit policies. Four rules, all mandatory:

```sql
alter table public.incidents enable row level security;

create policy "Reporters read their own incidents."
on public.incidents for select
to authenticated
using ( (select auth.uid()) = reporter_id );

create index incidents_reporter_id_idx on public.incidents using btree (reporter_id);
```

1. Wrap `auth.uid()` in a subselect — `(select auth.uid())` — so Postgres caches it per statement instead of calling it per row.
2. Always name the role with `to authenticated`, or the policy also runs for `anon`.
3. Index every column a policy filters on, or reads become sequential scans.
4. `for insert` policies need `with check`; `for update` needs **both** `using` and `with check`.

Verify per-role access with direct PostgREST calls, not through the app UI.

## 11. Storage

Create a **private** bucket for incident photographs. Access through `downloadAuthenticated` or signed URLs, not public URLs. Define the bucket policy in a migration.

Note `publicRenderUrl` is deprecated in supabase-kt 3.x — use `publicUrl`, even though the live docs page still shows the old name.

## 12. Realtime

```sql
alter publication supabase_realtime add table public.incidents;
alter publication supabase_realtime add table public.incident_assignments;
alter publication supabase_realtime add table public.responders;
alter publication supabase_realtime add table public.communication_messages;
```

Apply `replica identity full` only where DELETE-event filtering is genuinely needed — it inflates WAL volume.

**RLS is not applied to realtime DELETE events.** Delete payloads reach every subscriber, so never hard-delete rows containing pilgrim or medical data from a published table. Revoke `delete` from `authenticated` on these tables and use status transitions instead.

Client-side, always register the flow collector **before** calling `subscribe()`, or the first events are lost.

## 13. Google Maps

1. Google Cloud Console → enable **Maps SDK for Android**.
2. Create an API key.
3. **Restrict it** to Android apps, application ID `com.varisahayak` (`com.varisahayak.debug` for debug builds), and your signing certificate SHA-1:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
4. Put the key in `.env` as `GOOGLE_MAPS_API_KEY`. It is injected as a manifest placeholder.

An unrestricted key is billable by anyone who extracts it from the APK.

## 14. Firebase and FCM

1. Create a Firebase project; add an Android app with application ID `com.varisahayak`.
2. Download `google-services.json` into `app/`. It is git-ignored.
3. The build applies the google-services and Crashlytics plugins **only if that file exists**, so a fresh clone builds without Firebase configured.
4. Add your SHA-1 to the Firebase app settings.

Client notes: override `onNewToken(String)` — `onTokenRefresh` and `FirebaseInstanceId` no longer exist. Request `POST_NOTIFICATIONS` at runtime on Android 13+; FCM declares the permission but does not request it.

Payloads carry an incident identifier and a type only. Authoritative data is fetched from the backend.

## 15. Gemini and Edge Functions

Gemini is reached **only** through an authenticated Edge Function. No key ever enters the app.

```bash
supabase secrets set GEMINI_API_KEY=...
supabase functions deploy classify-incident
supabase functions serve classify-incident --env-file .env.local   # local
```

Function shape — note that both `Deno.serve` and `import { serve }` are explicitly discouraged in current guidance:

```ts
import { withSupabase } from 'npm:@supabase/server@^1'

export default {
  fetch: withSupabase({ auth: 'user' }, async (req, ctx) => {
    const key = Deno.env.get('GEMINI_API_KEY')
    // ctx.supabase is already RLS-scoped to the caller; ctx.userClaims is their identity
    return Response.json({ ok: true })
  }),
}
```

Model: **`gemini-3.5-flash-lite`**. Structured output uses `generationConfig.responseFormat`, **not** `responseMimeType`/`responseSchema`. Do not set `temperature`, `topP`, or `topK` — all deprecated.

Distinguish the two 429s: `rate_limit_exceeded` is retryable, `quota_exceeded` is not. In every failure case the incident workflow must continue on deterministic rules.

## 16. Room

Room 2.8.4 with KSP. The Room Gradle plugin requires a schema directory:

```kotlin
room { schemaDirectory("$projectDir/schemas") }
```

Commit the generated schema JSON — it is the migration record.

`fallbackToDestructiveMigration` is deliberately **not** set. Wiping the database on a schema change would discard unsynced incidents. Always write a migration.

## 17. WorkManager

The manifest removes WorkManager's default initializer so Hilt can supply the worker factory; `VariSahayakApplication` implements `Configuration.Provider`. If you see `WorkerFactory` errors, that pairing is what to check.

Sync work is enqueued as **unique** work with a `CONNECTED` constraint and exponential backoff, and is re-armed on `BOOT_COMPLETED`.

## 18. Location permissions

Declared: `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`. Request at runtime with a rationale.

Coarse-only is a **supported state**, not a failure — capture the fix and flag it approximate. Incident creation must succeed with no permission at all.

## 19. QR setup

CameraX plus **bundled** ML Kit barcode scanning (`com.google.mlkit:barcode-scanning`). Bundled costs roughly 2.4 MB of APK but needs no Play Services and no first-use model download — an unbundled model that has not downloaded yet is a dead scanner at the moment it is needed.

`CAMERA` is requested at runtime. Manual code entry must always be available as a fallback.

QR payloads contain an opaque token only. Resolution happens server-side.

## 20. Offline documentation

Route documentation is cached in full and readable with no network. Updates download in the background and replace the local copy **only once fully downloaded**, so a partial download never destroys a usable copy.

## 21. Localisation

`res/values/`, `res/values-hi/`, `res/values-mr/`. No user-facing string may be hardcoded in a composable. Domain enums map to string resources in `core/designsystem/component/Labels.kt`.

Test layouts in Marathi and Hindi — Devanagari sets taller and longer than Latin and will break tight leading.

## 22. Project structure

```
app/src/main/java/com/varisahayak/
├── app/            Application, MainActivity, navigation
├── core/           common, designsystem, network, di, permissions, location, utils
├── data/           local (Room), remote (Supabase), repository, sync
├── domain/         model, repository interfaces, usecase
└── feature/        auth, dashboard, incidents, sos, map, qr, lostfound,
                    communication, documentation, notifications, profile
supabase/
├── migrations/     versioned SQL
└── functions/      Edge Functions
plans/              phased implementation plan + pinned API contract
```

Dependency direction: `Compose UI → ViewModel → Use Case → Repository → Room / Supabase`.

## 23. Build commands

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:installDebug           # install on a connected device
./gradlew :app:assembleRelease        # minified release build
./gradlew :app:lintDebug
./gradlew :app:dependencies           # verify resolved versions
```

On Windows use `gradlew.bat`.

## 24. Testing commands

```bash
./gradlew :app:testDebugUnitTest             # JUnit 5
./gradlew :app:connectedDebugAndroidTest     # JUnit 4, needs an API 26+ emulator
```

Unit tests run on **JUnit 5** via `de.mannodermaus.android-junit` 2.0.1 (note: the plugin id no longer contains a `5`).

Instrumented tests are a **JUnit 4** world — `AndroidJUnitRunner`, `createAndroidComposeRule()`, and `HiltAndroidRule` are all JUnit 4. There is no `ui-test-junit5` artifact. JUnit 5 instrumented tests additionally require **API 26+**; on lower devices they are silently disabled.

## 25. Security checklist

- [ ] `.env`, `local.properties`, `google-services.json`, `*.jks` are all git-ignored
- [ ] No `SUPABASE_SERVICE_ROLE_KEY` or `GEMINI_API_KEY` anywhere under `app/`
- [ ] RLS enabled on every table, verified per role by direct PostgREST calls
- [ ] Every policy names `to authenticated` and wraps `auth.uid()` in a subselect
- [ ] Maps key restricted by application ID and signing SHA-1
- [ ] `usesCleartextTraffic="false"`
- [ ] `allowBackup="false"` with backup and data-extraction rules excluding everything
- [ ] QR payloads contain no personal data
- [ ] Release APK grepped for secrets:
      `unzip -p app/build/outputs/apk/release/*.apk | strings | grep -iE "service_role|AIza|GEMINI"`
- [ ] Git history checked: `git log -p | grep -iE "service_role|GEMINI_API_KEY"`

## 26. Git hygiene

- Never commit production secrets. If one is committed, rotate it — removing it from history is not sufficient.
- Commit `app/schemas/` (Room) and `supabase/migrations/`.
- Do not commit `build/`, `.gradle/`, or `.idea/`.
- `.env.example` is committed; `.env` is not.

## 27. Development sequence

The build order in [plans/](plans/), each phase self-contained:

1. Foundation — toolchain, design system, navigation
2. Backend schema, RLS, realtime
3. Authentication and role-aware navigation
4. Incident engine and offline sync
5. Location and maps
6. Prioritisation, matching, realtime, notifications
7. SOS Bridge, QR, Lost & Found
8. Operations — documentation, communication, command dashboard
9. AI Edge Function
10. Hardening, tests, security, accessibility
11. Documentation
12. Final verification

## 28. Deployment principles

- Ship only minified release builds that have been **exercised on a physical device** — a passing debug build proves nothing about R8 behaviour.
- supabase-kt ships no consumer ProGuard rules and upstream disables minification in its own sample, so release-mode serialization is the highest-risk area. Keep rules live in `app/proguard-rules.pro`. A `SerializationException` in release is a missing keep rule — fix the rule, never disable minification.
- Apply database migrations before releasing a client that depends on them.
- Rotate any credential that has ever been committed.

## 29. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Engine doesn't support WebSocketCapability` | `ktor-client-android` is on the classpath. Switch to `ktor-client-okhttp`. |
| `Serializer for class X not found` | Missing `@Serializable`, or the serialization plugin is not applied. In release, a missing keep rule. |
| User is signed out every time the app is backgrounded | `SessionStatus.Initializing` is being mapped to signed-out. It means "unknown" — hold. |
| Unresolved `io.github.jan.supabase.gotrue.*` | Renamed in 3.x. Use `io.github.jan.supabase.auth.*`, artifact `auth-kt` (not `gotrue-kt`). |
| Insert/update returns no data | supabase-kt defaults to `Returning.Minimal`. Call `select()` inside the request block. |
| Duplicate incidents after a retry | Sync is using `insert`. Use `upsert` with `onConflict = "client_id"`. |
| Realtime misses the first events | The collector was registered after `subscribe()`. Register it first. |
| `Cannot find symbol: kapt` / kapt plugin errors | kapt is incompatible with AGP 9 built-in Kotlin. Use KSP. |
| `kotlinOptions` unresolved | Removed by AGP 9. Use top-level `kotlin { compilerOptions { } }`. |
| Compose fails to compile at minSdk 23 | The Compose BOM's minSdk is unpublished and may be 24. Verify, then either drop to an older BOM or raise minSdk — this is a product decision, not a build fix. |
| Jupiter instrumented tests silently do not run | The emulator is below API 26. |
| Gemini returns 404 `model_not_found` | The model ID was retired. Update it; the workflow should already have fallen back to deterministic rules. |
| Maps shows a blank grey grid | Key missing, unrestricted-but-wrong, or the SHA-1 does not match the build variant. |
| Build succeeds but Supabase calls fail immediately | `.env` is missing or empty. Values default to empty strings rather than failing the build. |
