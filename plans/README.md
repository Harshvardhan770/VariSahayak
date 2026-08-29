# VARI Sahayak — Phased Implementation Plan

Source of truth for product scope: [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md)
Source of truth for **APIs, versions, and syntax**: [00-api-contract.md](00-api-contract.md)

## How to execute this plan

Each phase file is **self-contained** and designed to be run in a fresh chat context.

For every phase, in order:

1. Read [00-api-contract.md](00-api-contract.md) **in full** before writing any code. It is short and it is binding.
2. Read the phase file.
3. Read the "Read first" sources listed in that phase.
4. Implement only what the phase's Tasks section lists.
5. Run the phase's Verification Checklist. Do not proceed to the next phase until every item passes.
6. If a verification item fails because a documented API does not behave as the contract says, **stop and report** — do not invent an alternative API.

## Phase index

| # | File | Outcome |
|---|---|---|
| 0 | [00-api-contract.md](00-api-contract.md) | Pinned versions, allowed APIs, banned anti-patterns. No code. |
| 1 | [01-foundation.md](01-foundation.md) | Toolchain installed, project builds, design system, navigation skeleton |
| 2 | [02-backend-schema.md](02-backend-schema.md) | Supabase project, migrations, tables, RLS, realtime publication |
| 3 | [03-auth-roles.md](03-auth-roles.md) | Supabase Auth, profiles, role-aware navigation |
| 4 | [04-incident-engine-offline.md](04-incident-engine-offline.md) | Incident domain, Room, WorkManager sync, state machine |
| 5 | [05-location-maps.md](05-location-maps.md) | Permissions, Fused Location, Maps, incident markers |
| 6 | [06-matching-realtime-notifications.md](06-matching-realtime-notifications.md) | Availability, assignment engine, Realtime, FCM |
| 7 | [07-sos-bridge-qr.md](07-sos-bridge-qr.md) | CameraX, ML Kit barcode, QR identifiers, SOS Bridge, Lost & Found |
| 8 | [08-operations.md](08-operations.md) | Offline documentation, communication, command dashboard |
| 9 | [09-ai-edge-function.md](09-ai-edge-function.md) | Gemini via Edge Function + deterministic rule layer + fallback |
| 10 | [10-hardening-tests.md](10-hardening-tests.md) | Offline edge cases, security, tests, Crashlytics, a11y, release build |
| 11 | [11-documentation.md](11-documentation.md) | The three required Markdown deliverables |
| 12 | [12-final-verification.md](12-final-verification.md) | Cross-cutting verification and anti-pattern sweep |

## Scope boundary (from PRD)

Voice reporting, SMS/IVR, ambulance dispatch, predictive analytics, gamification, and other-gathering expansion are **future scope**. Do not implement them. Do not add libraries for them.
