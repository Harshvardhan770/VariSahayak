# Phase 7 — SOS Bridge, QR, and Lost & Found

**Goal:** a Varkari without a smartphone can get help. A volunteer scans their QR identifier, the app resolves it server-side, and an incident is created and routed through the same engine as any other.

This is the PRD's core inclusion feature. Treat it as such.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) §0.4 (CameraX 1.6.2, ML Kit barcode).
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "SOS Bridge", "User Roles / Volunteer", "Security".
3. [02-backend-schema.md](02-backend-schema.md) — the `qr_identifiers` table.

## Preconditions

Phases 1–6 complete.

---

## Tasks

### 7.1 Camera and scanning

- CameraX **1.6.2** (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-compose`).
- ML Kit barcode scanning. Choose deliberately:
  - **Bundled** `com.google.mlkit:barcode-scanning:17.3.0` — ~2.4 MB APK cost, works with no Play Services dependency and no first-use model download. **Recommended for this app** — volunteers are in a field environment with poor connectivity, and an unbundled model that has not downloaded yet is a dead scanner at the moment it is needed.
  - Unbundled `play-services-mlkit-barcode-scanning:18.3.1` — ~200 KB but requires Play Services and a first-use download.
- `CAMERA` runtime permission with rationale and permanent-denial handling.
- Scanner UI: large viewfinder, torch toggle, clear scanning/success/failure feedback, and a **manual code entry fallback** for when the QR is damaged, dirty, or the camera fails. The fallback is not optional — a torn wristband must not mean no help.

### 7.2 QR identifiers — the security rule

**The QR payload is an opaque, non-sensitive token and nothing else.** No name, no medical condition, no phone number, no address, no identity number. The PRD is explicit and this is the one requirement in this phase that cannot be traded away.

Resolution happens server-side: the app sends the scanned token, and an Edge Function or an RLS-protected query returns only what the scanning volunteer's role is permitted to see. A volunteer scanning a wristband does not get a medical history; a medical responder assigned to that incident might.

Log every resolution to `incident_events` — who scanned what, when, and where. Unknown or revoked tokens return a clear "not recognised" result, never a partial or guessed match.

### 7.3 SOS Bridge workflow

Implement exactly the PRD's chain:

**Varkari needs help → volunteer/help desk → QR scan → resolve identifier → create incident → capture location → prioritise → match → notify → resolve**

Key properties:

- The created incident flows through the **same** Phase 4 offline path and the **same** Phase 6 priority and matching engines. No parallel pipeline.
- It is marked as SOS-Bridge-originated, with the resolved subject reference — so the responder knows they are meeting someone without a phone.
- **It must work offline.** Scanning and incident creation succeed with no network; the identifier resolves against a locally cached token map where one exists, and otherwise the incident is created with the raw token and resolved on sync. Never block the creation of a help request on connectivity.
- An SOS-flagged incident enters the top priority band deterministically (Phase 6 rule 1).

### 7.4 SOS action for volunteers

A prominent, hard-to-miss SOS control on the volunteer dashboard that raises a critical incident in the fewest possible taps, with confirmation to prevent accidents but no multi-screen flow. This is the PRD's top volunteer UI priority.

### 7.5 Lost & Found

- `lost_found_items` — report a lost person or item, with photo, description, last-known location, and QR identifier where one exists.
- Search and match against open reports.
- A lost-person report is an incident of category `LOST_PERSON` and goes through the normal pipeline; the Lost & Found surface is a view over it, not a separate system.
- Same offline-first behaviour as every other write.

---

## Verification checklist

- [ ] Scan a valid QR → identifier resolves → incident is created with location and SOS-Bridge origin.
- [ ] Same flow **in airplane mode** → incident is created locally and syncs on reconnect.
- [ ] Scan an unknown/revoked token → clear "not recognised" message, no partial data, no crash.
- [ ] Manual code entry produces the same result as a scan.
- [ ] Deny camera permission → rationale shown; manual entry remains available.
- [ ] Inspect a generated QR payload — it contains **only** an opaque token. Decode it and confirm no personal data.
- [ ] As a volunteer, resolve a token → returned fields contain no medical or identity detail beyond role permission.
- [ ] An SOS-Bridge incident lands in the top priority band without any AI involvement.
- [ ] Every resolution writes an `incident_events` audit row.
- [ ] Lost & Found report creates a `LOST_PERSON` incident visible in the normal incident list.
- [ ] Torch toggle works; scanner recovers after the app is backgrounded and resumed.
- [ ] `git grep -nE "name|phone|medical|aadhaar|dob" -- <qr payload construction>` shows no personal fields in the token.

## Anti-pattern guards

- Do **not** encode any personal, medical, identity, or contact data in a QR payload.
- Do **not** resolve identifiers client-side from a bundled lookup table containing sensitive data.
- Do **not** block SOS Bridge incident creation on network availability.
- Do **not** build a parallel incident pipeline for SOS Bridge — reuse Phases 4 and 6.
- Do **not** ship the scanner without a manual-entry fallback.
- Do **not** leave camera resources bound after the screen is disposed.

## Done when

The full SOS Bridge chain works online and offline, and the QR payload is demonstrably free of sensitive data.
