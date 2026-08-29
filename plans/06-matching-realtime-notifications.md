# Phase 6 — Prioritisation, Matching, Realtime, and Notifications

**Goal:** a reported incident is deterministically prioritised, matched to a suitable responder, delivered as a push notification, and reflected live on every relevant screen — with recovery when the realtime channel drops.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) §0.5 (Realtime — the collector-before-subscribe rule, reconnection defaults), §0.6 (realtime publication, the DELETE/RLS caveat), §0.10.
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "Prioritise", "Match & Notify", "Monitor", "Realtime", "Notifications".

## Preconditions

Phases 1–5 complete.

---

## Tasks

### 6.1 Deterministic priority engine — build this FIRST, and without AI

`domain/usecase/` — a pure Kotlin `PriorityEngine` with no network dependency:

1. An explicit SOS/emergency indicator forces the top priority band. Nothing overrides it.
2. Deterministic safety rules: `MEDICAL` and any SOS bypass ordinary queues (PRD: "Critical medical and SOS incidents must bypass ordinary queues").
3. Category and severity contribute a base score.
4. An AI-suggested priority (Phase 9) may **raise** a score but may never lower a deterministically-critical one.
5. An authorised human override (organiser/command) is recorded in `incident_events` with the actor and reason.

Phase 9 plugs AI in behind this. The engine must be complete and fully tested here, before Gemini exists in the codebase. If the AI layer is never built, this phase's output is still a working product.

### 6.2 Responder availability

- `responders` state: available / busy / off-shift, with area assignment and current capability set.
- Volunteer-facing availability toggle, written through to Supabase and cached in Room.
- Recent location (from Phase 5) feeds the matcher; stale positions past a threshold are treated as unknown, not as current.

### 6.3 Matching engine

A pure, testable `MatchResponderUseCase` scoring candidates on the PRD's six criteria: role, capability, availability, assigned area, current/recent location, and workload/active assignments.

Where the matcher runs is a decision with consequences:

- **Recommended: a Supabase Edge Function (`auth: 'user'` or `'secret'`) or a Postgres function.** Matching needs to read across all responders — data that RLS deliberately hides from any individual client. A client-side matcher would need over-broad read policies, which contradicts Phase 2's access model.
- Write the scoring logic once in SQL/TypeScript server-side, and mirror the *scoring rules* in Kotlin only as unit-tested domain documentation if useful — do not maintain two live implementations.

Assignment writes a row to `incident_assignments` and an audit row to `incident_events`. Reassignment and escalation follow the same path; `REASSIGNMENT_REQUIRED` and `ESCALATED` are real states with real transitions (Phase 4's state machine).

### 6.4 Realtime subscriptions

Subscribe to `incidents`, `incident_assignments`, `responders`, and `communication_messages` per the Phase 2 publication.

```kotlin
val channel = supabase.channel("incident-assignments")
channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "incident_assignments" }
    .onEach { action -> repository.applyRealtimeChange(action) }
    .launchIn(scope)
channel.subscribe()
```

Non-negotiables:

- **Register the collector before `subscribe()`.** Reversing this loses the first events.
- **Realtime writes into Room, never straight into the UI.** Same rule as Phase 4 — one source of truth.
- **Realtime is not the source of truth** (PRD). On every `SUBSCRIBED` transition of `channel.status`, run a **reconciliation fetch** of the relevant rows from Postgrest. This closes the gap for anything that changed while the socket was down.
- Do not hand-roll reconnection — the SDK reconnects (7s delay, 5 attempts). Do handle exhausted reconnection by falling back to periodic polling and surfacing a degraded-connectivity indicator.
- Tie channel lifecycle to the screen/session scope and `unsubscribe()` on teardown.
- **Never trust a realtime DELETE event as an authorisation-filtered signal** — RLS does not apply to deletes (contract §0.6). Phase 2 revoked delete on these tables; if one ever arrives, log it and reconcile from the server rather than acting on the payload.

### 6.5 FCM

- `FirebaseMessagingService` overriding **`onNewToken(String)`** and `onMessageReceived`. `onTokenRefresh` and `FirebaseInstanceId` no longer exist (contract §0.10).
- Register the token in `device_tokens`, keyed to the profile. Refresh on every `onNewToken` and on sign-in; delete on sign-out.
- Request `POST_NOTIFICATIONS` at runtime on Android 13+ — FCM declares the permission but does not request it for you.
- Notification channels by importance: **SOS/critical** (max importance, bypasses DND where policy allows), assignment, escalation, status change, announcements.
- **Payloads carry minimal safe data** — an incident ID and a type, nothing more (PRD). The app fetches authoritative data from the backend on open. No medical or identity details in a push payload.
- Deep-link a notification tap to the incident detail screen, including from a cold start.
- Notification delivery failure must not block the workflow — the incident and assignment remain visible in-app regardless.

Sending is server-side: an Edge Function or database trigger fires FCM on assignment/escalation. The Android app never sends pushes.

---

## Verification checklist

- [ ] Unit tests: priority engine — SOS always top band; MEDICAL bypasses queues; an AI suggestion cannot lower a deterministic critical; override is recorded.
- [ ] Unit tests: matching scores across all six criteria, including workload tie-breaks and stale-location handling.
- [ ] Assigning an incident on device A appears on device B within seconds, without a manual refresh.
- [ ] Kill the network for 60s during an assignment, restore it — the assignment appears after reconnection **via reconciliation**, not lost.
- [ ] `channel.status` reaching `SUBSCRIBED` demonstrably triggers a reconciliation fetch (log or test assertion).
- [ ] A push notification arrives for a new assignment; tapping it from a cold start opens the right incident.
- [ ] Notification payload inspected — contains only an ID and a type, no personal data.
- [ ] Sign out → token removed from `device_tokens`; sign in on another account → new token registered.
- [ ] Android 13+ device: `POST_NOTIFICATIONS` is requested at runtime.
- [ ] `git grep -nE "onTokenRefresh|FirebaseInstanceId|createChannel\(|\.join\(\)|\.leave\(\)|postgrestChangeFlow|PostgrestAction"` returns nothing.
- [ ] Every `postgresChangeFlow` call site registers its collector before `subscribe()`.

## Anti-pattern guards

- Do **not** build the AI classifier in this phase. Deterministic rules first, and they must stand alone.
- Do **not** let an AI or remote signal downgrade a deterministically-critical incident.
- Do **not** call `subscribe()` before registering the collector.
- Do **not** treat realtime as the source of truth; always reconcile on resubscribe.
- Do **not** run the matcher client-side against broadly-readable responder data.
- Do **not** put personal or medical data in an FCM payload.
- Do **not** use `onTokenRefresh`, `FirebaseInstanceId`, `createChannel`, `join()`, or `leave()`.

## Done when

Cross-device assignment works live, the disconnect/reconcile test passes, and the priority and matching engines are fully unit-tested with no AI dependency.
