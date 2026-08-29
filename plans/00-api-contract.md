# Phase 0 — API Contract (Documentation Discovery Output)

**Research date: 2026-08-29.** Every version and signature below was read off a live official page or off library source at a published tag. This file is BINDING. If you want to use an API that is not listed here, go read its official documentation first and add it here with a URL — do not guess.

---

## 0.1 Local environment: what is actually installed

Verified on this machine on 2026-08-29:

| Tool | State |
|---|---|
| JDK | **21.0.7** installed. AGP 9.3 requires JDK 17+ — 21 is fine. |
| Node | 22.14.0 |
| Android SDK | **NOT INSTALLED** (`%LOCALAPPDATA%\Android\Sdk` absent, `ANDROID_HOME` unset) |
| Android Studio | **NOT INSTALLED** (`C:\Program Files\Android` absent) |
| Gradle | Not on PATH (fine — the wrapper provides it) |
| Supabase CLI | **NOT INSTALLED** |

Phase 1 must install the SDK and accept licenses before any Gradle command can succeed. Do not assume `gradlew assembleDebug` works on a clean checkout.

---

## 0.2 Pinned toolchain

| Item | Pinned value | Why |
|---|---|---|
| Android Gradle Plugin | **9.3.0** | latest stable — developer.android.com/build/releases/gradle-plugin |
| Gradle | **9.7.1** (AGP 9.3 min is 9.5.0) | docs.gradle.org/current/release-notes.html |
| JDK | **17** target (21 toolchain OK) | AGP 9.3 requirement |
| Kotlin (KGP) | **2.3.21** — NOT 2.4.x | KSP's latest (2.3.11) targets Kotlin 2.3; Dagger 2.60 is built on 2.3.21 |
| KSP | **2.3.11** | github.com/google/ksp/releases |
| compileSdk | **37** (Android 17) | AGP 9.3 max API |
| targetSdk | **36** | Play mandate for new apps from 2026-08-31 |
| minSdk | **23** | PRD requirement — see §0.3, this has real costs |

### The single most important toolchain fact

**AGP 9.0+ has built-in Kotlin support, enabled by default.**

> "you no longer have to apply the `org.jetbrains.kotlin.android` (or `kotlin-android`) plugin in your build files to compile Kotlin source files" — developer.android.com/build/migrate-to-built-in-kotlin

Consequences, all mandatory:

- **Do NOT** put `org.jetbrains.kotlin.android` anywhere. Not in `libs.versions.toml`, not in any `plugins { }` block.
- Kotlin version is raised via a root `buildscript { dependencies { classpath(...) } }` block, not a plugin alias.
- `android { kotlinOptions { ... } }` is gone. Use top-level `kotlin { compilerOptions { ... } }`.
- `org.jetbrains.kotlin.kapt` is **incompatible** with built-in Kotlin. Use KSP everywhere.

---

## 0.3 minSdk 23 — the PRD requirement and its documented costs

The PRD mandates API 23. AndroidX as a whole moved its default floor to 24 during 2026. Honouring 23 is possible but constrains several things, and one of them is unverified:

1. **Navigation Compose must be pinned to `2.9.8`.** Navigation `2.10.0` raised its minimum to **API 24** (release notes, 2.10.0-rc01). Do not "upgrade" it.
2. **Room must be `2.8.4` (`androidx.room`), not Room 3.** Room `3.0.2` (`androidx.room3`) publishes **no minSdk statement** — unverified against 23. Room 2.8.4 is confirmed minSdk 23. Room 2.x is in maintenance mode; that is an accepted trade.
3. **UNVERIFIED: Compose BOM 2026.08.00 (ui/foundation 1.12.0, material3 1.4.0) does not publish a minSdk.** Given the AndroidX-wide move to 24 in the same release window, it may require 24. **This is Phase 1's first gate.** If Compose 1.12.0 turns out to require minSdk 24, STOP and report to the user; the choice between "drop to an older Compose BOM" and "raise minSdk to 24" is theirs, not yours.
4. **`supabase-kt` states its Android minimum is 26**, and: *"For lower versions, you need to enable core library desugaring."* So at minSdk 23 the app module **must** enable `isCoreLibraryDesugaringEnabled = true` and add a `coreLibraryDesugaring(...)` dependency. Verify the current `com.android.tools:desugar_jdk_libs` version at implementation time.
5. **JUnit 5 instrumented tests require API 26+.** On API 23–25 devices the plugin logs *"JUnit 5 is not supported on this device. All Jupiter tests will be disabled."* The `androidTest` emulator must be **API 26 or higher**, or on-device Jupiter tests silently vanish. Unit tests (`test/`) are unaffected.

ML Kit (23), Maps SDK (23), Firebase (23), and Hilt (23) all sit *exactly* at 23. There is zero headroom below it.

---

## 0.4 Pinned library versions

All verified on official release-notes pages on 2026-08-29.

| Library | Version | minSdk | Note |
|---|---|---|---|
| Compose BOM | 2026.08.00 | UNVERIFIED | gate in Phase 1 |
| activity-compose | 1.13.0 | 23 OK | |
| lifecycle-* | 2.11.0 | 23 OK | |
| core-ktx | 1.19.0 | 21 OK | |
| navigation-compose | **2.9.8** | 21 OK | NOT 2.10.0 |
| Hilt (Dagger) | 2.60.1 | 23 OK | requires AGP 9.0.0+ |
| androidx.hilt (nav-compose, work) | 1.4.0 | — | requires AGP >= 9.2.0 |
| Room | **2.8.4** (`androidx.room`) | 23 OK | NOT `androidx.room3` |
| WorkManager | 2.11.2 | 23 OK | |
| CameraX | 1.6.2 | 23 OK | |
| ML Kit barcode (bundled) | `com.google.mlkit:barcode-scanning:17.3.0` | 23 OK | ~2.4 MB |
| ML Kit barcode (unbundled) | `com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1` | 23 OK | ~200 KB, needs Play Services |
| maps-compose | 8.5.0 | 21 OK | pulls play-services-maps transitively |
| play-services-location | 21.4.0 | 23 OK | |
| Firebase BOM | 34.18.0 | 23 OK | messaging 25.1.2, crashlytics 20.1.0 |
| google-services plugin | 4.5.0 | — | |
| Crashlytics plugin | 3.0.8 | — | |
| kotlinx-coroutines | 1.11.0 | — | |
| kotlinx-serialization | 1.11.0 | — | |
| **supabase-kt BOM** | **3.8.0** | see §0.5 | |
| **Ktor client** | **3.5.1** | — | see §0.5 — engine choice is not free |
| androidx.test core/runner/rules | 1.7.0 | — | |
| androidx.test.ext:junit | 1.3.0 | — | |
| espresso-core | 3.7.0 | — | |
| JUnit BOM | 5.14.1 | — | |
| mannodermaus plugin | `de.mannodermaus.android-junit` **2.0.1** | — | id renamed, no `5` |
| MockK | 1.14.11 | — | `mockk-jvm` / `mockk-android` + `mockk-agent` |

### Plugin ids and placement

| Plugin id | Where |
|---|---|
| `com.android.application` | root `apply false` + app module |
| ~~`org.jetbrains.kotlin.android`~~ | **NOWHERE** |
| `org.jetbrains.kotlin.plugin.compose` | root `apply false` + every Compose module. Version **must equal** KGP version. |
| `org.jetbrains.kotlin.plugin.serialization` | root `apply false` + modules with `@Serializable` |
| `com.google.devtools.ksp` | root `apply false` + modules with processors |
| `com.google.dagger.hilt.android` | root `apply false` + app module |
| `androidx.room` | root `apply false` + data module. `schemaDirectory(...)` is **required** once applied. |
| `com.google.gms.google-services` | root `apply false` + app module only |
| `com.google.firebase.crashlytics` | root `apply false` + app module only |
| `de.mannodermaus.android-junit` | app module |

`settings.gradle.kts` `pluginManagement` repositories: `google()`, `mavenCentral()`, `gradlePluginPortal()`. Nothing else. **No `jcenter()`.**

---

## 0.5 supabase-kt — ALLOWED APIs (v3.8.0)

Group is `io.github.jan-tennert.supabase` (with hyphen). **Packages are `io.github.jan.supabase`** (no hyphen, no `tennert`). This mismatch causes most import errors.

### Dependencies

```kotlin
implementation(platform("io.github.jan-tennert.supabase:bom:3.8.0"))
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:auth-kt")
implementation("io.github.jan-tennert.supabase:realtime-kt")
implementation("io.github.jan-tennert.supabase:storage-kt")
implementation("io.github.jan-tennert.supabase:functions-kt")

// Realtime needs a WebSocket-capable engine.
implementation("io.ktor:ktor-client-okhttp:3.5.1")
```

**Do NOT use `ktor-client-android`.** The official Supabase Android tutorial uses it, but it does not support WebSockets — installing `Realtime` on top of it throws `IllegalArgumentException: Engine doesn't support WebSocketCapability`. Use `ktor-client-okhttp` (or `ktor-client-cio`).

`kotlin("plugin.serialization")` is mandatory and every DTO must be `@Serializable`, or you get *"Serializer for Class 'X' not found"* at runtime.

`<uses-permission android:name="android.permission.INTERNET" />` is required.

### Client construction (source: supabase.com/docs/reference/kotlin/initializing)

```kotlin
val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY
) {
    install(Auth)
    install(Postgrest)
    install(Realtime)
    install(Storage)
    install(Functions)
}
```

Session persistence on Android needs **no Context and no manual initializer** — `auth-kt` registers an `androidx.startup` `Initializer` that captures `applicationContext` automatically. There is no `Supabase.initialize(context)`; do not look for one.

### Auth

```kotlin
val sessionStatus: StateFlow<SessionStatus>   // io.github.jan.supabase.auth.status
fun currentSessionOrNull(): UserSession?
fun currentUserOrNull(): UserInfo?
suspend fun signUpWith(provider, redirectUrl = ..., config: (C.() -> Unit)? = null): R?
suspend fun signInWith(provider, redirectUrl = ..., config: (C.() -> Unit)? = null)  // returns Unit
suspend fun signOut(scope: SignOutScope = SignOutScope.LOCAL)
```

`SessionStatus` sealed states: `Authenticated(session, source)`, `Initializing`, `NotAuthenticated(isSignOut)`, `RefreshFailure(cause)`.

**Android backgrounding trap:** with the default `enableLifecycleCallbacks = true`, `onStop` sets the status back to **`SessionStatus.Initializing`**. A naive collector reads that as "logged out" and bounces the user to the login screen every time the app backgrounds. Treat `Initializing` as "unknown, hold", never as "signed out".

### Postgrest

```kotlin
suspend inline fun select(columns: Columns = Columns.ALL, request: SelectRequestBuilder.() -> Unit = {}): PostgrestResult
suspend inline fun <reified T : Any> insert(value: T, request: InsertRequestBuilder.() -> Unit = {}): PostgrestResult
suspend inline fun update(update: PostgrestUpdate.() -> Unit, request: PostgrestRequestBuilder.() -> Unit = {}): PostgrestResult
suspend inline fun <reified T : Any> upsert(value: T, request: UpsertRequestBuilder.() -> Unit = {}): PostgrestResult
suspend inline fun delete(request: PostgrestRequestBuilder.() -> Unit = {}): PostgrestResult
```

Request-builder members: `select()`, `single()`, `maybeSingle()`, `count(Count)`, `order(...)`, `limit(...)`, `range(...)`, `filter { }`.
Result decoders: `decodeAs<T>()`, `decodeList<T>()`, `decodeSingle<T>()`, `decodeSingleOrNull<T>()`, `countOrNull()`.

**Filters go inside a `filter { }` block.** Two critical defaults:

- `returning` defaults to `Returning.Minimal` — insert/update/upsert return **no data** unless you call `select()` inside the request block.
- `propertyConversionMethod` defaults to `CAMEL_CASE_TO_SNAKE_CASE`.

```kotlin
val incident = supabase.from("incidents").insert(dto) { select() }.decodeSingle<IncidentDto>()

supabase.from("incidents").update({ IncidentDto::status setTo "ASSIGNED" }) {
    select()
    filter { eq("id", incidentId) }
}.decodeSingle<IncidentDto>()
```

### Realtime

```kotlin
inline fun <reified T : PostgresAction> RealtimeChannel.postgresChangeFlow(
    schema: String,
    noinline filter: PostgresChangeFilter.() -> Unit = {}
): Flow<T>

suspend fun subscribe(blockUntilSubscribed: Boolean = false)
suspend fun unsubscribe()
val status: StateFlow<RealtimeChannel.Status>  // UNSUBSCRIBED / SUBSCRIBING / SUBSCRIBED / UNSUBSCRIBING
```

**Ordering rule: register the flow collector BEFORE calling `subscribe()`.**

```kotlin
val channel = supabase.channel("incident-assignments")
channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "incident_assignments" }
    .onEach { action -> /* ... */ }
    .launchIn(scope)
channel.subscribe()
```

Reconnection is automatic, configured by `Realtime.Config` defaults: `heartbeatInterval = 15s`, `reconnectDelay = 7s`, `rejoinDelay = 2s`, `maxAttempts = 5`, `connectOnSubscribe = true`, `disconnectOnSessionLoss = true`. Do not hand-roll a reconnect loop; do implement reconciliation-on-resubscribe (see Phase 6).

### Storage

```kotlin
suspend fun upload(path: String, data: ByteArray, options: UploadOptionBuilder.() -> Unit = {}): FileUploadResponse
suspend fun downloadAuthenticated(path: String, options: DownloadOptionBuilder.() -> Unit = {}): ByteArray
fun publicUrl(path: String, builder: PublicUrlBuilder.() -> Unit = {}): String
suspend fun createSignedUrl(path: String, expiresIn: Duration, ...): String
```

On Android, read the photo URI to a `ByteArray` via `ContentResolver` and use the `ByteArray` overload. `publicRenderUrl` is **`@Deprecated`** — use `publicUrl`, even though the live docs page still shows the old name.

### Functions

```kotlin
supabase.functions.invoke(
    function = "classify-incident",
    body = buildJsonObject { put("description", description) },
    headers = Headers.build { append(HttpHeaders.ContentType, "application/json") }
)
```

### Verified import paths

```kotlin
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
```

---

## 0.6 Supabase Postgres — allowed SQL patterns

### RLS (source: supabase.com/docs/guides/database/postgres/row-level-security)

```sql
alter table public.incidents enable row level security;

create policy "Reporters read their own incidents."
on public.incidents for select
to authenticated
using ( (select auth.uid()) = reporter_id );

create policy "Volunteers create their own incidents."
on public.incidents for insert
to authenticated
with check ( (select auth.uid()) = reporter_id );

create policy "Assignees update their incident."
on public.incidents for update
to authenticated
using ( (select auth.uid()) = assignee_id )
with check ( (select auth.uid()) = assignee_id );

create index incidents_reporter_id_idx on public.incidents using btree (reporter_id);
```

Four rules, all from that page, all mandatory:

1. **Always wrap `auth.uid()` in a subselect** — `(select auth.uid())`. This makes Postgres cache it via `initPlan` instead of calling it per row.
2. **Always name the role with `to authenticated`.** Omitting it makes the policy run for `anon` too.
3. **Always index every column a policy filters on.** An unindexed policy column turns every read into a sequential scan.
4. INSERT policies need `with check`. UPDATE policies need **both** `using` and `with check` — without `with check`, a user can reassign a row to somebody else.

### Migrations CLI

```bash
supabase init
supabase login
supabase link --project-ref <PROJECT_REF>
supabase migration new create_incidents_table
supabase db push
supabase migration list
supabase db reset          # local only
```

Files live at `supabase/migrations/<timestamp>_<description>.sql`.

### Realtime enablement

```sql
alter publication supabase_realtime add table public.incidents;

-- ONLY if you need to FILTER delete events. Inflates WAL volume; do not apply blanket.
alter table public.incidents replica identity full;
```

**RLS is NOT applied to realtime DELETE events.** Per the docs: *"RLS policies are not applied to DELETE statements, because there is no way for Postgres to verify that a user has access to a deleted record."* Delete payloads reach every subscriber. Therefore: **never hard-delete a row containing sensitive Varkari or medical data from a realtime-published table.** Use soft-delete / status transitions instead — which the PRD's state model already provides (`CANCELLED`).

Realtime authorisation is evaluated per subscriber per event (100 subscribers on one change = 100 authorization checks), and client access policies are cached for the connection's duration.

---

## 0.7 Supabase Edge Functions — allowed shape

**The entry shape has changed twice. Both older forms are now non-recommended.**

Current official guidance (supabase.com/docs/guides/ai-tools/ai-prompts/edge-functions) is explicitly: **"Avoid `import { serve }` or `Deno.serve`"**. The recommended shape:

```ts
import { withSupabase } from 'npm:@supabase/server@^1'

export default {
  fetch: withSupabase({ auth: 'user' }, async (req, ctx) => {
    const { userClaims, supabase, supabaseAdmin } = ctx
    // supabase      -> already RLS-scoped to the calling user
    // supabaseAdmin -> bypasses RLS (service role)
    // userClaims    -> caller identity from the verified JWT
    return Response.json({ ok: true })
  }),
}
```

Auth modes: `'user'` (valid user JWT on `Authorization`), `'secret'` (secret key on `apikey`), `'publishable'`, `'none'`.
Pairing for this app: **`auth: 'user'` with `verify_jwt = true` (the default)**. `withSupabase` also handles CORS automatically — do not hand-roll CORS headers when using it.

**Do NOT hand-parse the Authorization header** with `createClient(url, key, { global: { headers: { Authorization: ... } } })` + `supabase.auth.getUser()`. That pattern is absent from the current auth docs. Use `ctx.supabase` / `ctx.userClaims`.

Secrets:

```bash
supabase secrets set GEMINI_API_KEY=...
supabase secrets list
supabase functions serve classify-incident --env-file .env.local
supabase functions deploy classify-incident
```

Read with `Deno.env.get('GEMINI_API_KEY')`. Note `SUPABASE_SECRET_KEYS` / `SUPABASE_PUBLISHABLE_KEYS` are **JSON documents** needing `JSON.parse(...)['default']`; `SUPABASE_SERVICE_ROLE_KEY` / `SUPABASE_ANON_KEY` are labelled legacy.

`--legacy-bundle` does not exist. The Docker-avoidance flag is `--use-api`.

---

## 0.8 Gemini — allowed model IDs and request shape

**Model ID: `gemini-3.5-flash-lite`** — "Our fastest, most cost-effective 3.5 model for high-throughput execution." Stable, no shutdown marker.

**Retired / do not use:** `gemini-2.0-flash` and `gemini-2.0-flash-lite` were **shut down 2026-06-01**. `gemini-1.5-*` no longer appears in the docs at all. `gemini-3.1-flash-lite` is deprecated (shutdown 2027-05-07). `gemini-2.5-flash` / `2.5-flash-lite` are listed stable but there are unofficial reports of retirement — prefer `gemini-3.5-flash-lite`.

**Structured output uses `responseFormat`, not `responseMimeType`.** The Interactions API removed `response_mime_type`, and the current generateContent guide shows `generationConfig.responseFormat.text.{mimeType,schema}`:

```
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent
Header: x-goog-api-key: $GEMINI_API_KEY

{
  "system_instruction": { "parts": [ { "text": "..." } ] },
  "contents": [ { "parts": [ { "text": "..." } ] } ],
  "generationConfig": {
    "responseFormat": {
      "text": {
        "mimeType": "application/json",
        "schema": {
          "type": "object",
          "properties": {
            "category": { "type": "string", "enum": ["MEDICAL","WATER","LOST_PERSON","BLOCKED_ROAD","SANITATION","CROWD_SURGE","OTHER"] },
            "severity": { "type": "integer", "minimum": 1, "maximum": 5 },
            "rationale": { "type": "string" }
          },
          "required": ["category","severity"]
        }
      }
    }
  }
}
```

Allowed schema types: `string`, `number`, `integer`, `boolean`, `null`, `object`, `array`; descriptors `title`, `description`, `enum`, `format`, `minimum`, `maximum`, `minItems`, `maxItems`. Not all JSON Schema features are supported. The docs state output is syntactically valid JSON but *"applications must validate semantic accuracy independently."*

**Do NOT set `temperature`, `top_p`, or `top_k`** — deprecated as of the 2026-07-21 changelog.

**Error handling for the fallback path** (source: ai.google.dev/gemini-api/docs/api-errors):

| Code | HTTP | Action |
|---|---|---|
| `rate_limit_exceeded` | 429 | retry with exponential backoff |
| `quota_exceeded` | 429 | **do NOT retry** — wait for daily reset |
| `service_unavailable` | 503 | retry with exponential backoff |
| `deadline_exceeded` | 504 | adjust timeout |
| `model_not_found` | 404 | the hardcoded model ID was retired — alert, fall back |

Both quota states are 429; distinguishing them matters. In every failure case the deterministic rule engine takes over and **incident creation must still succeed** (PRD requirement).

Note: `generateContent` is now officially "legacy" — the Interactions API went GA in June 2026 and is "recommended for all new projects". `generateContent` remains **fully supported and not deprecated**. This plan uses `generateContent`; migrating to Interactions is future scope, not MVP.

---

## 0.9 Testing contract

- **Unit tests (`src/test/`) run on JUnit 5.** Apply `de.mannodermaus.android-junit` **2.0.1** (the id lost its `5`; the repo moved to `mannodermaus/android-junit-framework`). Do not write `tasks.withType<Test> { useJUnitPlatform() }` — the plugin wires it.
- **Instrumented tests (`src/androidTest/`) are a JUnit 4 world.** `AndroidJUnitRunner` is a JUnit 4 runner; `createAndroidComposeRule()` and `HiltAndroidRule` are JUnit 4 `@Rule`s; there is no `ui-test-junit5` artifact. Do not fight this.
- Instrumented emulator must be **API 26+** (see §0.3).
- MockK coordinates: `io.mockk:mockk-jvm` for unit tests; `io.mockk:mockk-android` **plus** `io.mockk:mockk-agent` for instrumented.

---

## 0.10 BANNED PATTERNS — grep list

Phase 12 greps for every one of these. Do not introduce them.

| Pattern | Why banned |
|---|---|
| `org.jetbrains.kotlin.android` / `kotlin-android` | AGP 9 built-in Kotlin; incompatible |
| `kapt(` / `kotlin-kapt` / `org.jetbrains.kotlin.kapt` | maintenance mode; incompatible with built-in Kotlin. Use `ksp(` |
| `kotlinCompilerExtensionVersion` | dead since Kotlin 2.0; use the `plugin.compose` plugin |
| `kotlinOptions {` | replaced by top-level `kotlin { compilerOptions { } }` |
| `jcenter()` | read-only since 2021 |
| `android.enableJetifier` | default false; removal planned in AGP 10 |
| `android.builtInKotlin=false` / `android.newDsl=false` | removed in AGP 10; never opt out on a new project |
| `applicationVariants.all` / `variantFilter` / `dexOptions` | removed in AGP 9 |
| `de.mannodermaus.android-junit5` | id renamed to `...android-junit` |
| `io.mockk:mockk:` (bare) | use `mockk-jvm` / `mockk-android` |
| `io.github.jan.supabase.gotrue` | package renamed to `...auth` |
| `gotrue-kt` | artifact does not exist at 3.x |
| `install(GoTrue)` / `supabase.gotrue` | renamed to `Auth` / `supabase.auth` |
| `loginWith(` / `.logout()` | renamed `signInWith(` / `signOut()` |
| `createChannel(` / `.join()` / `.leave()` | renamed `channel(` / `subscribe()` / `unsubscribe()` |
| `postgrestChangeFlow` / `PostgrestAction` | typo present in upstream KDoc — real names have no `t` |
| `publicRenderUrl` | `@Deprecated` — use `publicUrl` |
| `ktor-client-android` | no WebSocket support; breaks Realtime |
| `SessionStatus.LoadingFromStorage` / `SessionStatus.NetworkError` | renamed `Initializing` / `RefreshFailure` |
| `Deno.serve(` / `import { serve }` | explicitly "avoid" in current Edge Function guidance |
| `--legacy-bundle` | flag does not exist |
| `gemini-2.0-flash` / `gemini-1.5-` | shut down / removed |
| `responseMimeType` / `responseSchema` | replaced by `responseFormat` |
| `"temperature"` / `"topP"` / `"topK"` in Gemini bodies | deprecated 2026-07-21 |
| `auth.uid() =` without an enclosing `(select ` | per-row re-evaluation; forces seq scans |
| `for insert` policy without `with check` | policy does nothing useful |
| `onTokenRefresh` / `FirebaseInstanceId` | removed; use `onNewToken` |
| `GEMINI_API_KEY` or `SERVICE_ROLE` anywhere under `app/` | secret leak into the APK |

---

## 0.11 Known gaps — verify before relying on these

The research agents flagged these as UNVERIFIED. Each is an action item, not a settled fact:

1. **Compose BOM 2026.08.00 minSdk** — may be 24. Gate in Phase 1.
2. **Room 3.0.2 minSdk** — unpublished. We use Room 2.8.4 to sidestep it entirely.
3. **`org.jetbrains.kotlin.plugin.compose` + AGP built-in Kotlin together** — no official page shows them applied side by side. Smoke-test an empty `@Composable` in Phase 1 before writing any UI.
4. **`desugar_jdk_libs` version** for core library desugaring at minSdk 23 — look it up at implementation time.
5. **supabase-kt R8/ProGuard rules** — the AAR ships none and upstream's sample disables minification. Release-build behaviour with `kotlinx-serialization` DTOs is untested upstream. Real Phase 10 risk.
6. **`responseMimeType` vs `responseFormat` in `generateContent`** — the REST reference still lists the old fields while the guide shows the new. Use `responseFormat`; keep the fallback path working so a schema rejection degrades to the rule engine rather than blocking incident creation.
7. **Gemini safety-setting enum values** — not fetched. Do not assume `HARM_CATEGORY_*` names without checking.
8. **maps-compose v8 removed composable APIs** — the changelog records no API-level removals. Do not act on tutorial claims about removed `GoogleMap` parameters without checking the v8 reference.
