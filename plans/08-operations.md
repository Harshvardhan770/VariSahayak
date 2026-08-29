# Phase 8 — Operations: Documentation, Communication, Command Visibility

**Goal:** volunteers can read route documentation offline and communicate with authorised responders; organisers get the operational picture the PRD's "Monitor" step requires.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) §0.5 (Realtime, Storage), §0.6 (RLS, realtime authorisation cost).
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "Monitor", "Organiser / Command User", "User Roles / Volunteer", "Realtime".

## Preconditions

Phases 1–7 complete.

---

## Tasks

### 8.1 Offline documentation

Route and procedure documentation for volunteers — the PRD lists it as a core volunteer capability.

- `documents` table + Supabase Storage for content.
- **Cached locally in Room/files and readable with no network.** Documentation that requires connectivity is useless on the route, which is precisely where it is needed.
- Versioned: the app checks for updates when online and downloads in the background via WorkManager, keeping the last-good copy until a new one is fully downloaded.
- Localised into English, Hindi, and Marathi.
- Clear "last updated" indication so a volunteer knows how stale their copy is.

### 8.2 Communication

- `communication_channels` and `communication_messages`.
- Scoped by role, organisation, area, and incident assignment — enforced by RLS, not by the client.
- An incident-scoped thread so a responder and reporter can coordinate on a specific case.
- Realtime message delivery using the Phase 6 pattern: collector before `subscribe()`, writes into Room, reconciliation on resubscribe.
- **Offline composition:** messages written offline queue in the same outbox pattern as incidents (Phase 4) and send on reconnect. Show a clear pending state.
- Note the realtime authorisation cost from contract §0.6 — every event is authorised per subscriber. Keep channel membership tight; do not put every user in one broad channel.

### 8.3 Command / organiser dashboard

The PRD's "Monitor" list, on a phone-sized surface — resist the temptation to build a desktop-density table:

- Open incidents, filterable by category, priority, status, and area.
- Assigned incidents with responder and elapsed time.
- Responder availability roll-up.
- Map view with hotspots (incident density by area over a time window).
- Escalations, surfaced above everything else.
- Operational reporting: counts by category, status, and area; median time-to-assignment and time-to-resolution.

Every number on this dashboard must be traceable to `incidents` and `incident_events` rows. Do not compute metrics that cannot be reproduced from the audit trail.

### 8.4 Escalation

An organiser can escalate an incident (Phase 4's `ESCALATED` state), which:

- Records the actor and reason in `incident_events`.
- Raises priority through the Phase 6 engine.
- Fires an escalation notification (Phase 6).
- Optionally triggers reassignment (`REASSIGNMENT_REQUIRED`).

### 8.5 Notification centre

An in-app list of received notifications backed by the `notifications` table, so a missed or dismissed push is recoverable. Push delivery is best-effort; the in-app record is authoritative.

---

## Verification checklist

- [ ] Airplane mode → route documentation opens and is fully readable.
- [ ] Documentation update downloads in the background; the previous copy stays readable until the new one completes.
- [ ] Documentation renders in Hindi and Marathi.
- [ ] Send a message on device A → it appears on device B in seconds.
- [ ] Compose a message offline → it queues with a pending state → sends on reconnect, exactly once.
- [ ] As a volunteer, attempt to read a channel outside your area/assignment → RLS returns nothing (verified through a direct Postgrest call, not just the UI).
- [ ] Organiser dashboard counts match direct SQL counts against `incidents`.
- [ ] Hotspot view reflects seeded incident density correctly.
- [ ] Escalating an incident writes an `incident_events` row, raises priority, and fires a notification.
- [ ] Dismissing a push still leaves the item in the in-app notification centre.
- [ ] Dashboard is usable one-handed on a phone; no horizontal scrolling tables.
- [ ] All new strings localised; all touch targets at least 48dp.

## Anti-pattern guards

- Do **not** require network to read documentation.
- Do **not** overwrite cached documentation before the replacement has fully downloaded.
- Do **not** enforce channel access on the client alone.
- Do **not** build one broad realtime channel for all users — authorisation cost scales per subscriber per event.
- Do **not** show dashboard metrics that are not derivable from `incidents` / `incident_events`.
- Do **not** port a desktop dashboard layout onto the volunteer or organiser phone UI.

## Done when

Documentation is fully usable offline, messaging works live and offline, and every dashboard figure reconciles against the database.
