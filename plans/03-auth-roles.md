# Phase 3 — Authentication and Role-Aware Navigation

**Goal:** a user can sign in, their role is resolved from the database, and the app routes them to the right experience — with session state that survives backgrounding, process death, and token refresh failure.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) §0.5 (client construction, Auth API, the `SessionStatus.Initializing` backgrounding trap) and §0.10.
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "User Roles", "Security".
3. [02-backend-schema.md](02-backend-schema.md) — the role model you are reading against.

## Preconditions

Phases 1–2 complete. Seed users exist for every role.

---

## Tasks

### 3.1 Supabase client via Hilt

Copy the Hilt module shape from contract §0.5. One `@Singleton SupabaseClient` provided from `core/network/`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
    }
}
```

No Context is passed and no initializer is registered — `auth-kt` handles Android session persistence through `androidx.startup` automatically (contract §0.5).

### 3.2 Auth data layer

- `AuthRepository` interface in `domain/repository/`, implementation in `data/repository/`.
- Sign-in: `supabase.auth.signInWith(Email) { email = ...; password = ... }` — note it returns `Unit`, not a session. Read the session afterwards from `currentSessionOrNull()`.
- Sign-out: `supabase.auth.signOut()`.
- Session observation: expose `supabase.auth.sessionStatus` as a domain-level `Flow<AuthState>`.

### 3.3 Map SessionStatus correctly — this is the bug that will bite you

```
SessionStatus.Authenticated   -> AuthState.SignedIn(userId)
SessionStatus.Initializing    -> AuthState.Unknown        // HOLD. Do NOT navigate to login.
SessionStatus.NotAuthenticated-> AuthState.SignedOut(isSignOut)
SessionStatus.RefreshFailure  -> AuthState.SessionExpired // prompt re-auth, preserve local data
```

`Initializing` is emitted **every time the app is backgrounded** (`onStop` calls `stopAutoRefreshForCurrentSession()`). A naive `is NotAuthenticated -> navigateToLogin()` mapping will throw the user out on every app switch. Show a hold/splash state for `Unknown`.

`RefreshFailure` must **never** discard unsynced local incidents. It prompts re-authentication; Phase 4's outbox survives it.

### 3.4 Profile and role resolution

- On `SignedIn`, fetch the caller's row from `profiles` joined to `roles` via Postgrest.
- Cache it in Room so role-aware navigation works offline on relaunch.
- The role is a **server fact**. The client caches it for routing convenience only; every actual authorisation decision is enforced by RLS (Phase 2). Do not add a client-side permission check that has no RLS counterpart.

DTOs must be `@Serializable` and remember `propertyConversionMethod` defaults to camelCase→snake_case (contract §0.5), so `Profile::organisationId` maps to `organisation_id` automatically.

### 3.5 Role-aware navigation

Route to a role-specific start destination:

- Volunteer → volunteer dashboard (the primary field experience; PRD prioritises SOS/critical alerts, then active assignment, then reporting).
- Medical / Police / NGO responder → their responder dashboard.
- Organiser / command → operational dashboard.
- Administrator → admin surface.

Unknown or missing role → a safe error state, not a crash and not a silent fallback to the highest-privilege screen.

### 3.6 Auth UI

Sign-in screen with loading, error, empty, and offline states (Definition of Done requires all four). All strings localised into `values/`, `values-hi/`, `values-mr/`. Minimum 48dp touch targets.

Offline sign-in attempt must produce a clear "no connection" message, not a hang or a generic failure.

---

## Verification checklist

- [ ] Sign in as each seeded role; each lands on the correct start destination.
- [ ] Background the app for 30 seconds and return — **the user is still signed in and is not bounced to login**. (This is the §3.3 trap; test it explicitly.)
- [ ] Kill and relaunch the app — session is restored from storage without a re-login.
- [ ] With the device offline, relaunch — the cached role still routes correctly.
- [ ] Sign out — session cleared, cached profile cleared, routed to login.
- [ ] Force a refresh failure (revoke the session server-side) — the app shows a re-auth prompt and does not crash.
- [ ] Unit tests: `SessionStatus` → `AuthState` mapping, covering all four states including `Initializing`.
- [ ] Compose UI test: sign-in flow, error state rendering.
- [ ] `git grep -nE "gotrue|install\(GoTrue\)|loginWith|\.logout\(\)"` returns nothing.
- [ ] Attempting a cross-role read through Postgrest as a volunteer returns empty, proving RLS is doing the work rather than the client.

## Anti-pattern guards

- Do **not** map `SessionStatus.Initializing` to signed-out.
- Do **not** use `GoTrue`, `loginWith`, `logout`, or the `io.github.jan.supabase.gotrue` package — all renamed (contract §0.10).
- Do **not** expect `signInWith` to return a session; it returns `Unit`.
- Do **not** enforce authorisation only on the client. Every client-side gate must have an RLS policy behind it.
- Do **not** store the anon key anywhere but `BuildConfig` fed from git-ignored `local.properties`.
- Do **not** clear local unsynced data on session expiry.

## Done when

All six sign-in/session scenarios above pass on a real device, and the backgrounding test in particular is demonstrated.
