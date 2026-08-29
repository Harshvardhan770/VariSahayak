# Phase 10 — Hardening: Edge Cases, Security, Tests, Accessibility, Release

**Goal:** the app survives the field. Every edge case in the PRD has a tested path, no secret ships in the APK, and a minified release build actually works.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) §0.9 (testing contract), §0.10 (banned patterns), §0.11 item 5 (the R8 risk).
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "Offline-first Requirements" (the eleven conditions), "Security", "Testing", "Definition of Done", "UI/UX Requirements".

## Preconditions

Phases 1–9 complete.

---

## Tasks

### 10.1 Offline edge-case sweep

Walk the PRD's eleven conditions and confirm each has an implemented, tested path — not an assumed one:

no internet · intermittent connectivity · timeout · app restart · device reboot · duplicate submissions · authentication expiry · server failure · location failure · notification failure · sync conflicts

For each, write an automated test where possible and a documented manual test where not. The governing rule stays: **no locally captured incident silently disappears.**

Add a visible "unsynced items" surface so a volunteer can always see what has not reached the server, and a manual retry action.

### 10.2 R8 / release build — expect trouble here

Contract §0.11 item 5: supabase-kt ships **no consumer ProGuard rules** and upstream's own sample disables minification. Release-build behaviour with `kotlinx-serialization` DTOs is genuinely untested upstream.

- Enable `isMinifyEnabled = true` and `isShrinkResources = true` for release.
- Add keep rules for `@Serializable` DTOs (kotlinx-serialization's rules ship with the runtime, but verify), Room entities, Hilt-generated code, and any reflective ML Kit / Maps surface.
- **Install and exercise the minified release build on a real device against a real Supabase project.** A debug build passing tells you nothing here. Test: sign-in, incident create/sync, realtime, QR scan, map, notifications.
- Any `SerializationException` or `ClassNotFoundException` in release is this issue. Fix with keep rules, not by disabling minification.

### 10.3 Security review

Against the PRD's "Security" list:

- [ ] Supabase Auth enforced everywhere.
- [ ] RLS on every table, verified per role by direct Postgrest calls (not through the UI).
- [ ] Privileged operations only in Edge Functions.
- [ ] HTTPS/TLS only; no cleartext traffic (`android:usesCleartextTraffic` absent or false).
- [ ] Maps API key restricted by package name + signing SHA-1.
- [ ] Server-side validation for everything the client can send.
- [ ] QR identifiers carry no sensitive data.
- [ ] Minimal sensitive data stored on-device; consider encrypting the Room database if any personal data is cached.
- [ ] No service-role key, no Gemini key anywhere in the app module or the APK.
- [ ] No production secrets in git history — check the whole history, not just the working tree.

Unpack the release APK and grep it. That is the only check that actually proves the last two.

### 10.4 Test suite

Per the PRD's testing list, with the contract §0.9 split (JUnit 5 for `test/`, JUnit 4 for `androidTest/`, emulator API 26+):

**Unit (JUnit 5 + MockK + coroutines-test):** authentication state mapping · role access logic · incident creation · offline creation · sync-after-reconnect · duplicate prevention · priority rules · responder matching · assignment · reassignment · SOS rules · QR resolution · state machine transitions · sync conflict resolution · AI safety layer.

**Instrumented (JUnit 4 + AndroidX Test + Compose UI Test + Hilt):** Room DAO and migrations · WorkManager sync (`work-testing`) · FCM message handling · QR scanning · SOS Bridge flow · Lost & Found · location failure paths · permission denial paths · session expiry · realtime recovery · the critical UI flows (sign-in, report incident, accept assignment, scan QR, SOS).

Set a coverage floor for `domain/` — that is where the business logic lives and it should be near-fully covered.

### 10.5 Crashlytics

- Firebase Crashlytics wired and verified with a forced test crash.
- Non-fatal reporting for sync failures, AI failures, and realtime disconnections — the operational signals that matter in the field.
- **Scrub personal data from logs and crash reports.** No Varkari identifiers, no medical detail, no precise coordinates in a crash payload.

### 10.6 Accessibility

- Every interactive element at least **48dp**.
- Content descriptions on all non-decorative elements; TalkBack pass over the critical flows.
- Text scales to at least 200% without clipping or overlap.
- Contrast ratios meeting WCAG AA against the outdoor-readable palette.
- **No priority or status communicated by colour alone** — audit every badge, marker, and list row for a paired icon or text label.
- Test in all three languages; Devanagari text runs longer than English and will break tight layouts.

### 10.7 Performance

- Cold start time on a low-end device measured and acceptable.
- Incident list scrolls smoothly with a realistic row count.
- Battery: continuous location and realtime sockets stop when off-shift or backgrounded.
- APK size reviewed — the bundled ML Kit model is ~2.4 MB of it, which is a deliberate trade (Phase 7).

---

## Verification checklist

- [ ] All eleven PRD offline conditions have a passing test or a documented manual result.
- [ ] `gradlew :app:testDebugUnitTest` passes.
- [ ] `gradlew :app:connectedDebugAndroidTest` passes on an API 26+ emulator.
- [ ] `gradlew :app:assembleRelease` succeeds with minification on.
- [ ] The **minified release build** completes sign-in, incident create + sync, realtime update, QR scan, map, and notification on a real device.
- [ ] `unzip -p app/build/outputs/apk/release/*.apk | strings | grep -iE "service_role|gemini|AIza"` finds no server-only secret.
- [ ] `git log -p | grep -iE "service_role|GEMINI_API_KEY"` finds nothing in history.
- [ ] Per-role Postgrest probes confirm RLS blocks every unauthorised read and write.
- [ ] Forced test crash appears in the Crashlytics console; the payload contains no personal data.
- [ ] TalkBack navigates the five critical flows.
- [ ] 200% font scale renders without clipping in English, Hindi, and Marathi.
- [ ] No badge, marker, or row conveys priority by colour alone.
- [ ] Full banned-pattern grep from contract §0.10 returns nothing.

## Anti-pattern guards

- Do **not** disable minification to make a release crash go away — write the keep rule.
- Do **not** ship with RLS verified only through the app UI.
- Do **not** log personal, medical, or precise-location data.
- Do **not** treat a passing debug build as evidence the release build works.
- Do **not** skip the Hindi/Marathi layout pass.
- Do **not** leave continuous location or realtime sockets running in the background.

## Done when

The minified release build passes every critical flow on a real device, the APK contains no server-only secret, and the full test suite is green.
