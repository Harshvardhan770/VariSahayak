# VARI Sahayak — Product Requirements Specification

**Document status:** baseline specification for the MVP.
**Applies to:** native Android application, Supabase backend.

Requirements marked **[Proposed]** are implementation decisions made during design. They are not derived from the source material and are open to revision. Everything unmarked traces to the supplied project sources.

---

## 1. Product definition

VARI Sahayak is a native Android coordination platform for the Pandharpur Wari. It connects volunteers, NGOs, police personnel, medical responders, and organisers so that a problem observed on the route reaches a suitable responder quickly and reliably — including when the reporter has no connectivity and the affected pilgrim has no phone.

It is an operational tool, not a public-facing consumer app. Its users are people already serving pilgrims; its job is to make that service faster and better coordinated.

## 2. Problem statement

The Wari moves hundreds of thousands of pilgrims along a fixed route over several weeks. Coordination today depends on physical proximity, personal networks, and mobile phone calls. This produces four failures:

1. **Reports do not reach the right responder.** A volunteer who sees a medical emergency may not know which medical team is nearest or free.
2. **Connectivity is unreliable.** Network coverage along the route is intermittent, and demand spikes where crowds are densest — exactly where incidents occur.
3. **Many pilgrims carry no smartphone.** A person who most needs help is often least able to request it.
4. **Organisers lack an operational picture.** Without aggregated incident and responder data, deployment decisions are made blind.

## 3. Product vision

Every pilgrim in distress reaches a responder, regardless of whether they own a phone or whether the network is working.

The system optimises for the real field environment:

**Fast reporting → reliable local persistence → safe prioritisation → suitable responder → immediate notification → realtime tracking → successful resolution.**

## 4. Product principles

1. **Safety over sophistication.** A simple rule that always works beats a clever one that sometimes does not.
2. **Offline is a normal operating mode**, not an error state.
3. **No captured incident is ever silently lost.** This constraint outranks every other technical consideration.
4. **Deterministic rules govern emergencies.** AI assists; it never decides alone.
5. **Authorisation is enforced by the server.** Client-side checks are convenience, never security.
6. **The field user's time is the scarcest resource.** Minimise taps, maximise legibility.
7. **Inclusion is a feature, not an add-on.** The SOS Bridge is core scope.

## 5. User roles

| Role | Primary need | Key capabilities |
|---|---|---|
| **Volunteer** | Act fast on what is in front of them | Report incidents, accept/reject assignments, update status, navigate, scan QR, create SOS Bridge incidents, read documentation, communicate, work offline |
| **Medical responder** | Reach medical emergencies first | Authorised medical incidents, emergency notifications, assigned work |
| **Police responder** | Maintain safety and flow | Safety, crowd, blockage, missing-person, and assigned incidents |
| **NGO responder** | Coordinate organisational effort | Organisation- and area-scoped operational information |
| **Organiser / Command** | See and direct the whole picture | Live dashboard, incidents, hotspots, responder availability, assignments, escalations, reporting |
| **Administrator** | Keep the system correctly configured | Users, roles, organisations, areas, access, bulk registration |

The volunteer is the primary field user. Where a design trade-off exists between volunteer usability and any other role's convenience, the volunteer wins.

## 6. Functional requirements

### 6.1 Report

A volunteer or authorised operational user can create an incident capturing, where available: category, description, current location, reporter, timestamp, optional photograph, relevant affected-person information, and synchronisation status.

Categories: medical emergency, water shortage, lost person, blocked road, sanitation, crowd surge, other.

**Incident creation must never depend on network availability, a location fix, or a photograph.** Each of those enriches a report; none gates one.

### 6.2 Prioritise

Prioritisation uses, in precedence order:

1. Explicit SOS / emergency indicators
2. Deterministic safety rules
3. Incident category and severity
4. AI-assisted classification and priority recommendation
5. Authorised human override

Critical medical and SOS incidents bypass ordinary queues.

### 6.3 Match & Notify

Incidents are matched to responders on role, capability, availability, assigned area, current or recent location, and workload / active assignments. The selected responder is notified through Firebase Cloud Messaging.

### 6.4 Monitor

Authorised operational users see open incidents, assigned incidents, responder status, incident status, map information, hotspots, escalations, and general operational visibility.

## 7. Incident model

| Field | Notes |
|---|---|
| `clientId` | Device-generated, immutable, unique. Exists before the network is involved. |
| `serverId` | Null until the server accepts the record. |
| `category` | One of the seven categories. |
| `description` | Free text. |
| `location` | Optional. Carries accuracy and an approximate flag. |
| `reporterId` | The authenticated creator. |
| `reportedAt` | Capture time on the device. |
| `photo` | Optional; local path until uploaded, then a storage path. |
| `affectedPersonNote` | Optional, minimal. |
| `status` | See §8. |
| `priority` | Critical / High / Medium / Low. |
| `syncState` | Pending / Syncing / Synced / Failed. |
| `isSos`, `sosBridgeToken` | SOS and SOS Bridge origin markers. |
| `assigneeId`, `areaId`, `organisationId` | Routing and scoping. |

**[Proposed]** The `clientId` / `serverId` split, the separation of `syncState` from `status`, and the accuracy/approximate location fields are design decisions. The source specifies that synchronisation status be captured but does not prescribe this structure.

## 8. Incident state machine

```
REPORTED → TRIAGED → ASSIGNED → ACCEPTED → IN_PROGRESS → RESOLVED
```

Additional states: `PENDING_SYNC`, `CANCELLED`, `REASSIGNMENT_REQUIRED`, `ESCALATED`.

**[Proposed]** The specific legal transitions below are a design decision; the source specifies the states and the main line but not the full transition table.

| From | Permitted to |
|---|---|
| `PENDING_SYNC` | `REPORTED`, `CANCELLED` |
| `REPORTED` | `TRIAGED`, `ASSIGNED`, `ESCALATED`, `CANCELLED` |
| `TRIAGED` | `ASSIGNED`, `ESCALATED`, `CANCELLED` |
| `ASSIGNED` | `ACCEPTED`, `REASSIGNMENT_REQUIRED`, `ESCALATED`, `CANCELLED` |
| `ACCEPTED` | `IN_PROGRESS`, `REASSIGNMENT_REQUIRED`, `ESCALATED`, `CANCELLED` |
| `IN_PROGRESS` | `RESOLVED`, `REASSIGNMENT_REQUIRED`, `ESCALATED`, `CANCELLED` |
| `REASSIGNMENT_REQUIRED` | `ASSIGNED`, `ESCALATED`, `CANCELLED` |
| `ESCALATED` | `ASSIGNED`, `IN_PROGRESS`, `RESOLVED`, `CANCELLED` |
| `RESOLVED`, `CANCELLED` | terminal |

Illegal transitions are rejected, not silently applied. Every accepted transition writes an audit record.

## 9. Responder matching

Candidates are scored on the six source-specified criteria: role, capability, availability, assigned area, current/recent location, and workload.

**[Proposed]** Matching executes server-side, in an Edge Function or database function. Rationale: matching must read across all responders, which is data that Row Level Security deliberately hides from any individual client. A client-side matcher would require over-broad read policies, defeating the access model.

**[Proposed]** A responder position older than a defined staleness threshold is treated as unknown rather than current.

## 10. AI requirements

AI is server-mediated:

```
Android → authenticated Edge Function → Gemini → validated structured response
        → safety/rule engine → database
```

AI may assist with incident classification, priority recommendation, structured information extraction, and alert summarisation.

AI must not:

- Store secrets in the APK
- Become the only emergency decision mechanism
- Override deterministic SOS rules
- Prevent incident creation when unavailable

**[Proposed]** An AI suggestion may raise a priority score but is capped below the critical band. Reaching CRITICAL requires an explicit SOS or a deterministic safety rule. Rationale: a misclassification that fabricates an emergency is as operationally damaging as one that misses it.

**[Proposed]** Model responses are validated against a schema server-side before use. Category values outside the seven permitted and severities outside 1–5 are discarded and treated as no suggestion.

If Gemini is unavailable, rate-limited, or returns invalid output, the incident workflow continues on deterministic rules alone.

## 11. SOS Bridge

The inclusion feature that lets a Varkari without a smartphone obtain assistance.

```
Varkari needs help → volunteer / help desk → QR scan → resolve QR identifier
→ create incident → capture location → prioritise → match → notify → resolve
```

Requirements:

- QR codes use **non-sensitive, opaque identifiers**. Private medical, identity, and contact information is never encoded in a QR payload.
- Identifier resolution happens server-side, returning only what the scanning user's role permits.
- The workflow reuses the standard incident pipeline — no parallel path.
- **[Proposed]** The scanner must offer manual code entry as a fallback for damaged or unreadable tags, and creation must succeed offline with the raw token resolved on sync.

## 12. Offline architecture

Incident creation must not depend on immediate network availability.

1. Validate input locally
2. Generate a local ID
3. Save to Room
4. Display the incident immediately
5. Mark `PENDING_SYNC`
6. WorkManager detects suitable connectivity
7. Sync to Supabase
8. Reconcile server and local IDs
9. Mark the record synchronised
10. Retry safely if required

**[Proposed]** Room is the single source of truth for all UI. Network and realtime results are written to the database and observed from there, never returned directly to a ViewModel. This is what makes online and offline behave identically.

**[Proposed]** Uploads use upsert on the unique `client_id` rather than insert, making a retried send idempotent. Sync work is enqueued as unique work so restarts do not stack duplicate workers.

## 13. Data architecture

Supabase PostgreSQL is the primary backend database. Conceptual entities:

`profiles` · `roles` · `organisations` · `areas` · `responders` · `incidents` · `incident_assignments` · `incident_events` · `locations` · `qr_identifiers` · `lost_found_items` · `notifications` · `documents` · `communication_channels` · `communication_messages` · `device_tokens`

Row Level Security governs role-, organisation-, area-, and assignment-based access. Android-side role checks are never relied upon.

**[Proposed]** All schema is defined as versioned migrations in the repository; nothing is created through the dashboard. `incident_events` is append-only and is the audit trail from which every operational decision can be reconstructed.

## 14. Realtime architecture

Supabase Realtime carries incident assignments, incident status, responder availability, communication messages, and operational updates.

Realtime is **not** treated as the only source of truth. Important state is persisted in the database with recovery and reconciliation supported.

**[Proposed]** On every transition to a subscribed channel state, the client performs a reconciliation fetch to close the gap for anything that changed while the socket was down. Realtime DELETE events are never trusted as an authorisation-filtered signal, because RLS does not apply to deletes; sensitive rows are therefore never hard-deleted from published tables.

## 15. Notification architecture

Firebase Cloud Messaging supports new assignment, SOS, escalation, reassignment, status changes, and operational announcements.

Notification payloads contain minimal safe information; the application retrieves authoritative incident data from the backend.

**[Proposed]** Separate notification channels by importance, with SOS at maximum importance. An in-app notification record is the authoritative list, so a dismissed or undelivered push remains recoverable. Notification failure never blocks the workflow.

## 16. Location requirements

Fused Location Provider supplies incident coordinates and responder positions. Google Maps SDK renders incidents, responders, and hotspots.

**[Proposed]** Coarse-only permission is a supported state, not a failure — the fix is captured and flagged as approximate. Continuous location updates run only while a responder is on shift and the app is foregrounded. Map tiles require connectivity; when offline the map shows an offline state while the incident list remains fully usable.

## 17. UI information architecture

Volunteer priority order, top to bottom:

1. SOS / critical alerts
2. Active assignment
3. Incident reporting
4. Map / location
5. Availability
6. Communication
7. Documentation

Responder surfaces lead with assigned work. Command surfaces lead with escalations, then open incidents, then responder availability.

## 18. UI style specification

The interface must be native Android, professional, field-first, fast, clean, high contrast, accessible, easy to operate outdoors, minimal in unnecessary interaction, and clear about both emergency status and offline/sync state.

- Dense desktop-style layouts are avoided, including on command surfaces.
- Large, clear controls. **Minimum touch target 48 dp.**
- **Incident priority is never communicated by colour alone** — colour is always paired with an icon, label, or text.
- Semantic design tokens rather than scattered hardcoded colours: Primary, Primary Container, Critical, Warning, Success, Info, Surface, Error, On-Surface, On-Surface Variant. Values are centralised in the Compose Material theme.
- **[Proposed]** Dynamic colour is not used: operational status must look identical across devices.

## 19. Volunteer-specific requirements

Volunteers must be able to report incidents; accept and reject assignments; update incident status; view assigned incident information; navigate to incident locations; scan QR identifiers; create SOS Bridge incidents; access route documentation; communicate with authorised responders; and continue basic incident capture during connectivity loss.

**[Proposed]** The SOS control is the largest element on the volunteer dashboard and reachable in a single tap plus confirmation.

## 20. Non-functional requirements

| Area | Requirement |
|---|---|
| Availability | Core capture and viewing work fully offline |
| Durability | No locally captured incident is lost to restart, reboot, or failure |
| Latency | Incident appears in the UI immediately on capture; assignment reaches a responder within seconds when online |
| Security | See §13, plus HTTPS/TLS, secure secret management, server-side validation |
| Accessibility | 48 dp targets, content descriptions, 200% text scale, WCAG AA contrast, no colour-only meaning |
| Localisation | English, Hindi, Marathi; no hardcoded user-facing strings |
| Compatibility | Minimum SDK 23 (Android 6.0) |
| Observability | Crash reporting plus non-fatal reporting for sync, AI, and realtime failures, with personal data scrubbed |

## 21. Edge cases

All of the following must have an implemented and tested path: no internet · intermittent connectivity · timeout · app restart · device reboot · duplicate submissions · authentication expiry · server failure · location failure · notification failure · sync conflicts.

**[Proposed]** Conflict resolution: the server owns status, priority, and assignment; the device owns reporter-authored content. A record still awaiting sync is not overwritten by a server copy. Resolutions are recorded in the audit trail.

**[Proposed]** Session expiry pauses sync and prompts re-authentication while retaining all local data.

## 22. Future scope

Deliberately **not** in the MVP:

- Voice-based incident reporting
- SMS / IVR
- Direct ambulance dispatch
- Predictive crowd analytics
- Volunteer gamification
- Expansion to other mass gatherings

These remain future scope unless explicitly promoted to MVP requirements.

## 23. Success metrics

| Metric | Intent |
|---|---|
| Time from report to assignment | Core responsiveness, measured by priority band |
| Time from assignment to resolution | Operational throughput |
| Offline capture success rate | Proportion of offline-created incidents that reach the server |
| Duplicate rate | Should be zero; any duplicate indicates an idempotency defect |
| SOS handling time | Measured separately; the number that matters most |
| SOS Bridge usage | Whether the inclusion feature is actually reaching phoneless pilgrims |
| Data loss incidents | Target zero, without qualification |

## 24. Acceptance criteria

The MVP is acceptable when:

1. An incident created in airplane mode appears immediately, survives force-stop and device reboot, and reaches the server **exactly once** on reconnection.
2. Running the sync path repeatedly against the same pending record does not increase the server row count.
3. An SOS incident reaches the critical band with Gemini disabled, with Gemini returning a low severity, and while offline.
4. Incident creation, prioritisation, matching, and notification all function with Gemini unavailable.
5. Backgrounding the app does not sign the user out.
6. A realtime disconnection recovers by reconciliation with no lost updates.
7. A live QR payload decodes to an opaque token containing no personal data.
8. Route documentation is fully readable with no network.
9. Session expiry retains all unsynced local data.
10. Per-role access is verified by direct backend calls, not only through the app UI.
11. The minified release build completes every critical flow on a physical device.
12. The release APK contains no server-only secret.
13. All user-facing strings render correctly in English, Hindi, and Marathi at 200% text scale.
14. No incident priority is conveyed by colour alone.
