# Phase 5 — Location and Maps

**Goal:** the app captures accurate incident coordinates, shows incidents on a map, and degrades gracefully when permissions or GPS are unavailable.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) §0.4 (maps-compose 8.5.0, play-services-location 21.4.0) and §0.11 item 8.
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "Core Workflow / Monitor", "Volunteer UI", "Security" (restricted Maps key).

## Preconditions

Phases 1–4 complete. `GOOGLE_MAPS_API_KEY` in `.env`.

---

## Tasks

### 5.1 Permissions

`core/permissions/` — a reusable permission handler covering:

- `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` (runtime, Android 6+).
- Rationale UI before the second request.
- Permanent-denial path that sends the user to app settings.
- **Coarse-only granted** is a supported state, not a failure — Android 12+ lets users grant approximate location. Handle it: capture the coarse fix and flag reduced accuracy on the incident.

### 5.2 Fused Location Provider

`core/location/`:

- `LocationProvider` wrapping `FusedLocationProviderClient`, exposing `Flow<LocationResult>`.
- A single high-accuracy fix for incident reporting, with a timeout and a last-known-location fallback.
- Continuous updates only while the volunteer is on-shift/available, at a battery-sane interval. Stop them when the app backgrounds.
- Explicit results: `Available(lat, lng, accuracy)`, `PermissionDenied`, `LocationDisabled`, `Timeout`.

**Incident creation must succeed with no location** (Phase 4 rule). Location enriches an incident; it never gates one.

### 5.3 Maps

- `maps-compose:8.5.0` only. Do **not** also declare `play-services-maps` — maps-compose pulls the right version transitively and declaring both invites a clash (contract §0.4).
- Maps key injected as a manifest placeholder from `.env`, never committed.
- **Restrict the key** in Google Cloud Console to the app's package name and signing certificate SHA-1 (PRD, "Security").
- Incident markers clustered by density, styled by priority — colour **plus** icon shape, never colour alone.
- Marker tap → incident detail.
- Responder's own position and, for command users, responder positions in their area.

Do not act on tutorial claims about removed `GoogleMap` composable parameters in maps-compose v8 — the changelog records no API removals (contract §0.11). Check the v8 reference if something does not resolve.

### 5.4 Navigate-to-incident

An "navigate" action that hands off to an external maps app via an implicit intent. Handle the no-maps-app-installed case. This is a handoff, not an embedded navigation SDK — embedded turn-by-turn is out of MVP scope.

### 5.5 Offline map behaviour

Google Maps tiles require network. When offline, the map surface must show a clear offline state and the incident list must remain fully usable — the volunteer's core workflow cannot depend on tiles loading.

---

## Verification checklist

- [ ] Fresh install → permission rationale → grant → a fix is obtained within the timeout.
- [ ] Deny permission → incident reporting still works, with location marked unavailable.
- [ ] Grant coarse only → incident is created with reduced-accuracy flagging.
- [ ] Turn off device location services → `LocationDisabled` state is shown, no crash, no hang.
- [ ] Airplane mode → map shows an offline state; incident list still renders from Room.
- [ ] Markers reflect priority through icon and label, not colour alone.
- [ ] Navigate action opens an external maps app; graceful message if none is installed.
- [ ] `git grep -n "AIza"` returns nothing (no committed Maps key).
- [ ] `gradlew :app:dependencies | grep play-services-maps` shows exactly one version.
- [ ] Backgrounding the app stops continuous location updates.

## Anti-pattern guards

- Do **not** declare `play-services-maps` alongside `maps-compose`.
- Do **not** commit the Maps API key or leave it unrestricted.
- Do **not** block incident creation on a location fix.
- Do **not** run continuous location updates when the app is backgrounded or the volunteer is off-shift.
- Do **not** treat coarse-only permission as denial.

## Done when

All ten checklist items pass, including the two degraded-permission paths.
