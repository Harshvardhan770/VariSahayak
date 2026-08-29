# Phase 2 — Backend Schema, RLS, and Realtime

**Goal:** a Supabase project whose schema, access policies, and realtime publication are defined entirely as versioned migrations in this repo — before any Android code talks to it.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) §0.6 (RLS patterns, migrations CLI, realtime enablement, the DELETE/RLS caveat).
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "Backend and Database Requirements", "User Roles", "Incident State Model", "SOS Bridge", "Security".

## Preconditions

Phase 1 complete. Supabase CLI available.

---

## Tasks

### 2.1 Project bootstrap

```bash
supabase init
supabase login
supabase link --project-ref <PROJECT_REF>
```

Migrations live at `supabase/migrations/<timestamp>_<description>.sql`. Create every schema object through `supabase migration new`, never through the dashboard SQL editor — the repo is the source of truth.

### 2.2 Schema

Create the entities the PRD lists, one migration per logical group:

`profiles`, `roles`, `organisations`, `areas`, `responders`, `incidents`, `incident_assignments`, `incident_events`, `locations`, `qr_identifiers`, `lost_found_items`, `notifications`, `documents`, `communication_channels`, `communication_messages`, `device_tokens`.

Requirements that are not negotiable:

- **`incidents.status`** is a Postgres enum or a check-constrained text column with exactly the PRD's states: `REPORTED`, `TRIAGED`, `ASSIGNED`, `ACCEPTED`, `IN_PROGRESS`, `RESOLVED`, `PENDING_SYNC`, `CANCELLED`, `REASSIGNMENT_REQUIRED`, `ESCALATED`.
- **`incidents.category`** constrained to: `MEDICAL`, `WATER`, `LOST_PERSON`, `BLOCKED_ROAD`, `SANITATION`, `CROWD_SURGE`, `OTHER`.
- **`incidents.client_id`** — a UNIQUE, non-null column holding the client-generated local ID. This is what makes offline sync idempotent in Phase 4. Without it, a retried upload creates duplicates. Add `unique (client_id)`.
- **`incident_events`** is append-only — it is the audit trail for every state transition, assignment, and escalation.
- **`qr_identifiers`** stores an opaque, non-sensitive token and maps it server-side to whatever record it refers to. **No medical, identity, or contact data in the token itself** (PRD, "SOS Bridge").
- Every table gets `created_at timestamptz default now()` and `updated_at timestamptz`.
- Role membership lives in `profiles`/`roles` — a user's role is a database fact, never a client claim.

### 2.3 RLS on every table

Copy the policy shape from contract §0.6 exactly. For each of the sixteen tables:

```sql
alter table public.<t> enable row level security;
```

Then write explicit policies. Mandatory form, every time:

- `to authenticated` on every policy.
- `(select auth.uid())` — wrapped, never bare.
- `for insert` policies carry `with check`.
- `for update` policies carry **both** `using` and `with check`.
- A btree index on every column any policy filters on.

Access model to implement:

| Role | Read | Write |
|---|---|---|
| Volunteer | own reported incidents; incidents assigned to them; area documentation | create incidents; update status on their assigned incidents |
| Medical responder | incidents of category MEDICAL in their area, plus assigned | status updates on assigned |
| Police responder | CROWD_SURGE / BLOCKED_ROAD / LOST_PERSON in their area, plus assigned | status updates on assigned |
| NGO responder | incidents scoped to their organisation and area | as assigned |
| Organiser / command | all incidents in their area | triage, assign, escalate, override priority |
| Administrator | user/role/org/area management | full within their scope |

Write these as helper SQL functions (e.g. `public.current_role()`, `public.has_area_access(area_id)`) marked `security definer` and `stable`, then reference them from policies. That keeps policies short and indexable.

**Never rely on Android-side role checks** (PRD, "Backend and Database Requirements"). Every rule above must hold if a caller talks to PostgREST directly with a valid JWT.

### 2.4 Realtime publication

```sql
alter publication supabase_realtime add table public.incidents;
alter publication supabase_realtime add table public.incident_assignments;
alter publication supabase_realtime add table public.responders;
alter publication supabase_realtime add table public.communication_messages;
```

Do **not** apply `replica identity full` unless a specific feature needs to filter DELETE events. It inflates WAL volume.

**Because RLS is not applied to realtime DELETE events** (contract §0.6), the schema must never hard-delete rows from these four tables. Cancellations are `status = 'CANCELLED'`; removals are soft. Enforce this by revoking `delete` from `authenticated` on the published tables.

### 2.5 Storage

Create a private bucket for incident photographs. Access via signed URLs or `downloadAuthenticated` — not public URLs. Write the bucket policy in the same migration series.

### 2.6 Seed data

`supabase/seed.sql` with: the role rows, a couple of organisations and areas, and a handful of test profiles for each role. This is what Phase 3 onward develops against.

---

## Verification checklist

- [ ] `supabase db reset` applies every migration cleanly from scratch on a local instance.
- [ ] `supabase db push` applies to the linked project; `supabase migration list` shows local and remote in sync.
- [ ] `select tablename from pg_tables where schemaname='public'` lists all sixteen tables.
- [ ] Every one of those tables reports `rowsecurity = true` in `pg_tables`.
- [ ] `select * from pg_policies where schemaname='public'` — **every row** has a non-null `roles` containing `authenticated`, and every `INSERT`/`UPDATE` policy has a non-null `with_check`.
- [ ] `grep -n "auth.uid()" supabase/migrations/*.sql | grep -v "(select auth.uid())"` returns nothing.
- [ ] Every column named in a policy `using`/`with check` clause has a matching index (check `pg_indexes`).
- [ ] `insert into incidents (client_id, ...) values ('same-id', ...)` twice fails on the unique constraint the second time.
- [ ] Signed in as a volunteer test user, `select * from incidents` returns only their own and assigned rows.
- [ ] Signed in as a volunteer, `update incidents set assignee_id = <other user>` is rejected.
- [ ] `select * from pg_publication_tables where pubname='supabase_realtime'` lists exactly the four intended tables.
- [ ] `delete from incidents` as an `authenticated` user is rejected.

## Anti-pattern guards

- Do **not** write bare `auth.uid()` in a policy.
- Do **not** omit `to authenticated`.
- Do **not** write an INSERT policy without `with check`, or an UPDATE policy without both clauses.
- Do **not** create schema objects through the dashboard — migrations only.
- Do **not** put identity, medical, or contact data in a QR token payload.
- Do **not** enable `replica identity full` on tables that do not need DELETE filtering.
- Do **not** grant `delete` on realtime-published tables.

## Done when

A fresh `supabase db reset` reproduces the entire backend, and the RLS assertions above pass against real test users for every role.
