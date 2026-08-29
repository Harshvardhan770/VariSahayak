# VARI Sahayak

A native Android coordination platform for the Pandharpur Wari — built for the volunteers, NGOs, police, medical teams, and organisers who are already serving pilgrims on the route.

The route has poor connectivity, enormous crowds, and pilgrims who often carry no phone at all. VARI Sahayak is designed for that reality first: an incident is captured on the device and shown immediately whether or not there is a network, then prioritised, matched to a suitable responder, and tracked to resolution.

---

## Core workflow

```
Report  →  Prioritise  →  Match & Notify  →  Monitor
```

**Report** — a volunteer captures category, description, location, and an optional photograph. This never waits on the network.
**Prioritise** — explicit SOS and deterministic safety rules first; AI classification only ever assists.
**Match & Notify** — the incident is scored against responders by role, capability, availability, area, recent location, and current workload, then delivered by push.
**Monitor** — organisers see open and assigned incidents, responder status, hotspots, and escalations live.

## Main features

- **Offline-first incident capture.** Reports are written locally, displayed instantly, and synced when connectivity returns. No locally captured incident is ever silently lost.
- **SOS Bridge.** A pilgrim without a smartphone gets help through a volunteer scanning their QR identifier. QR payloads carry an opaque token and nothing else — no identity, medical, or contact data.
- **Deterministic prioritisation.** SOS and critical medical incidents bypass ordinary queues. AI may raise a priority; it can never lower one, and it can never gate incident creation.
- **Responder matching and assignment**, with reassignment and escalation as first-class states.
- **Live operational picture** over Supabase Realtime, reconciled against the database rather than trusted as the source of truth.
- **Lost & Found**, route documentation readable fully offline, and incident-scoped messaging.
- **English, Hindi, and Marathi** throughout.

## Roles

| Role | Focus |
|---|---|
| Volunteer | Primary field user — reporting, assignments, SOS Bridge, QR scanning |
| Medical responder | Authorised medical incidents and emergency alerts |
| Police responder | Safety, crowd, blockage, and missing-person incidents |
| NGO responder | Organisation- and area-scoped operational work |
| Organiser / Command | Live dashboard, hotspots, assignments, escalations, reporting |
| Administrator | Users, roles, organisations, areas, and access |

## Technology

Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Hilt · Coroutines and Flow
Room · WorkManager · ConnectivityManager for offline-first operation
Supabase — PostgreSQL, Auth, Realtime, Storage, Edge Functions, Row Level Security
Fused Location Provider · Google Maps SDK · CameraX · ML Kit Barcode Scanning · Firebase Cloud Messaging
Gemini, reached only through an authenticated Edge Function
JUnit 5 · AndroidX Test · Compose UI Testing · MockK · Crashlytics

Minimum SDK 23 (Android 6.0). Exact pinned versions live in [plans/00-api-contract.md](plans/00-api-contract.md) and `gradle/libs.versions.toml`.

## Architecture

Clean Architecture with MVVM:

```
Compose UI → ViewModel → Use Case → Repository → Room / Supabase
```

Room is the single source of truth for the UI. Network and realtime results are written to the database and observed from there, which is what makes online and offline behave identically. Business logic stays out of composables and is unit-tested without Android.

## Security principles

- Authorisation is enforced by PostgreSQL Row Level Security, never by client-side role checks alone.
- Privileged operations run in Edge Functions.
- No service-role key and no Gemini key ever reaches the APK.
- QR identifiers are opaque and non-sensitive.
- Notification payloads carry an identifier and a type; authoritative data is fetched from the backend.
- The Google Maps key is restricted by package name and signing certificate.

## Quality standards

A feature is complete only when it handles loading, error, empty, **and** offline states; enforces authorisation server-side; persists locally where relevant; loses no data on network failure; is covered by tests for its business logic and critical UI flows; meets accessibility requirements including 48 dp touch targets and no priority conveyed by colour alone; and has all user-facing strings localised.

## Scope

In scope for the MVP: everything described above.

Explicitly **future scope** and deliberately not built: voice-based reporting, SMS/IVR, direct ambulance dispatch, predictive crowd analytics, volunteer gamification, and expansion to other mass gatherings.

## Success criteria

- An incident can be captured, viewed, and queued with no connectivity, and reaches the server exactly once when connectivity returns — surviving app restart and device reboot.
- SOS and critical medical incidents reach a responder ahead of ordinary traffic.
- A pilgrim without a phone can obtain help through the SOS Bridge, online or offline.
- The full workflow continues to function when Gemini is unavailable.
- The interface is operable one-handed, outdoors, in all three supported languages.

## Project status

The build system, design system, domain model, offline data layer, and deterministic priority engine are implemented. Feature surfaces, sync worker, backend migrations, and the AI Edge Function are in progress against the phased plan in [plans/](plans/).

Nothing has been compile-verified yet — the Android SDK is not installed on the development machine. See [setup.md](setup.md) for the toolchain the project expects.

## Documentation

- **[Project Summary.md](Project%20Summary.md)** — the full product requirements specification
- **[setup.md](setup.md)** — environment, Supabase, Firebase, and build setup
- **[plans/](plans/)** — the phased implementation plan and the pinned API contract
