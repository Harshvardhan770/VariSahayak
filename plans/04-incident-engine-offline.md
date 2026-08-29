# Phase 4 — Incident Engine and Offline-First Sync

**Goal:** a volunteer can create an incident with no network, see it immediately, and have it reach Supabase exactly once when connectivity returns — surviving app restart, device reboot, and retries.

This is the core of the product. The PRD's hard rule: **"No locally captured incident should silently disappear."**

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) §0.5 (Postgrest — note `Returning.Minimal` and the `filter { }` block), §0.4 (Room 2.8.4, WorkManager 2.11.2).
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "Core Workflow / Report", "Offline-first Requirements", "Incident State Model", "Definition of Done".
3. [02-backend-schema.md](02-backend-schema.md) — in particular the `incidents.client_id` unique constraint.

## Preconditions

Phases 1–3 complete.

---

## Tasks

### 4.1 Domain layer

In `domain/model/`, pure Kotlin, no Android or Room annotations:

- `Incident` — id, clientId, category, description, location, reporterId, timestamp, photoRef, affectedPersonInfo, status, syncState, priority.
- `IncidentCategory` enum — the PRD's seven values.
- `IncidentStatus` sealed class or enum — the PRD's ten states.
- `SyncState` enum — `PENDING_SYNC`, `SYNCING`, `SYNCED`, `FAILED`.
- A **state machine** that defines the legal transitions of `REPORTED → TRIAGED → ASSIGNED → ACCEPTED → IN_PROGRESS → RESOLVED`, plus entry into `CANCELLED`, `REASSIGNMENT_REQUIRED`, and `ESCALATED`. Illegal transitions must be rejected in the domain, not silently accepted. This is the highest-value unit-test target in the project.

Use cases in `domain/usecase/`: `CreateIncidentUseCase`, `UpdateIncidentStatusUseCase`, `ObserveIncidentsUseCase`, `SyncPendingIncidentsUseCase`.

### 4.2 Room local store

`data/local/` with Room **2.8.4** (`androidx.room`), KSP, `room { schemaDirectory("$projectDir/schemas") }` in the module build file — the plugin requires it once applied.

- `IncidentEntity` with `clientId` as a unique index, `syncState`, and `lastSyncAttemptAt`.
- `IncidentDao` returning `Flow<List<IncidentEntity>>` for reactive UI.
- Type converters for enums and timestamps.
- Commit the generated schema JSON — it is the migration record.

### 4.3 The offline write path

Implement exactly the PRD's ten-step flow:

1. Validate input locally.
2. Generate a local ID — a UUID, stored as `clientId`.
3. Insert into Room.
4. Display immediately (the UI reads from Room, always — never from the network directly).
5. Mark `PENDING_SYNC`.
6. WorkManager observes connectivity.
7. Upload to Supabase.
8. Reconcile the server ID against `clientId`.
9. Mark `SYNCED`.
10. Retry safely on failure.

**Room is the single source of truth for the UI.** The repository never returns network results straight to a ViewModel; it writes them to Room and lets the Flow emit. This is what makes offline and online behave identically.

### 4.4 Idempotent sync

- `SyncIncidentsWorker` — a `CoroutineWorker`, `@HiltWorker`, with `androidx.hilt:hilt-work`.
- Constraint: `NetworkType.CONNECTED`. Backoff: exponential.
- Enqueue as **unique work** with `ExistingWorkPolicy.KEEP` so app restarts do not stack duplicate workers.
- Upload with **upsert on `client_id`**, not plain insert:

```kotlin
supabase.from("incidents").upsert(dto) {
    onConflict = "client_id"
    select()
}.decodeSingle<IncidentDto>()
```

The `select()` is required — `returning` defaults to `Returning.Minimal` and you need the server row back to reconcile the ID (contract §0.5). The `onConflict = "client_id"` is what makes a retried upload a no-op instead of a duplicate.

- Reschedule pending sync on device boot (`BOOT_COMPLETED` receiver or a periodic worker) so a reboot mid-queue does not strand records.

### 4.5 Handle every failure mode the PRD lists

Each of these needs an explicit, tested path:

| Condition | Required behaviour |
|---|---|
| No internet | Incident saved locally, marked `PENDING_SYNC`, visible in the list with a clear sync badge |
| Intermittent connectivity | Worker retries with backoff; no duplicate rows |
| Timeout | Retry; record stays `PENDING_SYNC` |
| App restart | Pending queue survives; unique work is not duplicated |
| Device reboot | Sync is rescheduled |
| Duplicate submission | `client_id` upsert makes it a no-op |
| Authentication expiry | Sync pauses, record is **retained**, user is prompted to re-auth, sync resumes after |
| Server failure (5xx) | Retry with backoff; after N attempts mark `FAILED` and surface it in the UI — never drop it |
| Location failure | Incident is still creatable without coordinates; location is captured later or flagged missing |
| Sync conflict | Server row wins for server-owned fields (status, assignment); local wins for reporter-authored fields. Record the resolution in `incident_events`. |

### 4.6 Photo attachment

Optional photograph: read the URI via `ContentResolver` to a `ByteArray`, upload with `supabase.storage.from(bucket).upload(path, bytes)`. Queue the upload as part of the same sync work — an incident must never be blocked on its photo. If the photo fails, the incident still syncs and the photo retries independently.

### 4.7 Report and list UI

- Fast reporting form: category picker (large targets), description, auto-captured location, optional photo. Minimal taps — this is used one-handed, outdoors, in a crowd.
- Incident list showing status **and** sync state, with colour paired to icon and text label (never colour alone).
- Loading / error / empty / offline states for both screens.
- All strings localised.

---

## Verification checklist

- [ ] Airplane mode → create an incident → it appears in the list instantly with a `PENDING_SYNC` badge.
- [ ] Re-enable network → within the worker's window the record syncs and the badge clears.
- [ ] Airplane mode → create 3 incidents → force-stop the app → relaunch → restore network → **exactly 3 rows** appear server-side.
- [ ] Airplane mode → create an incident → **reboot the device** → restore network → the record syncs.
- [ ] Run the sync worker twice against the same pending record → server row count does not increase.
- [ ] Expire the session mid-sync → local records are retained, user is prompted, sync completes after re-auth.
- [ ] Deny location permission → incident creation still succeeds.
- [ ] Simulate a 500 from Supabase → record is marked `FAILED` and is visible in the UI, not lost.
- [ ] Unit tests: state machine rejects every illegal transition; sync conflict resolution; `client_id` generation uniqueness.
- [ ] Room instrumented test: DAO insert/query/update, migration from schema v1.
- [ ] `gradlew :app:testDebugUnitTest` passes.
- [ ] `git grep -n "androidx.room3"` returns nothing.
- [ ] `git grep -n "insert(" app/src/main --include=*.kt` — every incident upload path uses `upsert` with `onConflict`, not bare `insert`.

## Anti-pattern guards

- Do **not** return network results directly to the UI. Room is the source of truth.
- Do **not** use plain `insert` for incident sync — retries will duplicate.
- Do **not** forget `select()` inside the request block; `Returning.Minimal` means you get nothing back.
- Do **not** put filters outside the `filter { }` block.
- Do **not** delete a local record because a sync attempt failed.
- Do **not** block incident creation on location or photo availability.
- Do **not** use `androidx.room3` or Room 3 APIs.
- Do **not** enqueue non-unique work — restarts will stack workers.

## Done when

All eleven scenarios in the verification checklist pass on a real device, including the reboot and force-stop cases.
