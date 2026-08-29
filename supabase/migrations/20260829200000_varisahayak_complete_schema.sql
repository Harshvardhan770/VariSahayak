-- =============================================================================
-- VARI Sahayak -- complete schema, consolidated.
-- =============================================================================
--
-- This file is the authoritative definition of the database. Running it against an empty
-- database produces the full schema; running it against a database at any earlier
-- migration brings it up to date; running it twice changes nothing the second time.
--
-- It supersedes and contains everything from:
--   20260829072644_create_incidents_table.sql          (base tables, kept only in git history)
--   20260829140000_profile_on_signup.sql               (profile creation trigger)
--   20260829150000_complete_schema.sql                 (operational tables, RLS)
--   20260829180000_signup_roles_org_and_incident_visibility.sql
--   20260829190000_responders_and_matching.sql
--   ...plus Plan 07: location QR, SOS bridge, and intelligent Lost & Found.
--
-- Those files remain in the repository so an already-migrated database keeps a truthful
-- history. New environments need only this one.
--
-- IDEMPOTENCY. Every statement is guarded: `if not exists` on creates, `add column if not
-- exists` on alters, `create or replace` on functions and views, and `drop policy if
-- exists` before every policy. Data-touching statements are written so a second run is a
-- no-op. The two places that could destroy data are called out inline.
--
-- ORDERING. Types, then tables, then the functions policies depend on, then policies,
-- then triggers, then realtime, then seed data. Nothing references anything defined later.
--
-- =============================================================================


-- =============================================================================
-- 1. EXTENSIONS AND TYPES
-- =============================================================================

create extension if not exists postgis schema extensions;

-- Enum creation is not `if not exists`-able before PG 15 in all forms, so each is guarded.
do $do$
begin
    if not exists (select 1 from pg_type where typname = 'incident_status') then
        create type public.incident_status as enum (
            'REPORTED', 'TRIAGED', 'ASSIGNED', 'ACCEPTED',
            'IN_PROGRESS', 'RESOLVED', 'PENDING_SYNC',
            'CANCELLED', 'REASSIGNMENT_REQUIRED', 'ESCALATED'
        );
    end if;

    if not exists (select 1 from pg_type where typname = 'incident_category') then
        create type public.incident_category as enum (
            'MEDICAL', 'WATER', 'LOST_PERSON', 'BLOCKED_ROAD',
            'SANITATION', 'CROWD_SURGE', 'OTHER'
        );
    end if;
end
$do$;


-- =============================================================================
-- 2. CORE REFERENCE TABLES
-- =============================================================================

create table if not exists public.organisations (
    id uuid default gen_random_uuid() primary key,
    name text not null,
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

create table if not exists public.areas (
    id uuid default gen_random_uuid() primary key,
    name text not null,
    organisation_id uuid references public.organisations(id),
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

create table if not exists public.roles (
    id uuid default gen_random_uuid() primary key,
    wire_name text unique not null
);

-- Which roles a user may claim at sign-up.
--
-- SECURITY NOTE. The role arrives in client-supplied `raw_user_meta_data`, so it is a
-- request, not an instruction. `handle_new_user` grants it only when this flag is true and
-- falls back to VOLUNTEER otherwise -- a tampered payload cannot self-grant ADMINISTRATOR.
-- Every role ships claimable so each option in the app's picker works. To lock the
-- privileged two down for production, run:
--
--   update public.roles set self_assignable = false
--    where wire_name in ('ORGANISER', 'ADMINISTRATOR');
--
-- No application change is needed.
alter table public.roles
    add column if not exists self_assignable boolean not null default true;

create table if not exists public.profiles (
    id uuid references auth.users on delete cascade primary key,
    display_name text not null,
    role_id uuid references public.roles(id),
    organisation_id uuid references public.organisations(id),
    area_id uuid references public.areas(id),
    phone text,
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- public.current_role() joins profiles to roles on every policy evaluation. Without this
-- index that join is a sequential scan on every authorised read.
create index if not exists profiles_role_id_idx on public.profiles using btree (role_id);

-- Case-insensitive organisation lookup, so "City Hospital" and "city hospital" are one
-- organisation rather than two. Guarded: an existing database may already hold duplicates.
do $do$
begin
    create unique index if not exists organisations_name_lower_key
        on public.organisations (lower(name));
exception when unique_violation then
    raise notice 'public.organisations holds duplicate names; unique index skipped';
end
$do$;


-- =============================================================================
-- 3. OPERATIONAL TABLES
-- =============================================================================

create table if not exists public.incidents (
    id uuid default gen_random_uuid() primary key,
    client_id text unique not null,   -- device-generated; makes offline sync idempotent
    status public.incident_status default 'REPORTED' not null,
    category public.incident_category not null,
    description text not null,
    reporter_id uuid references public.profiles(id) not null,
    assignee_id uuid references public.profiles(id),
    area_id uuid references public.areas(id),
    priority text default 'MEDIUM',
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- Columns the Android client sends. Added separately because the original table predates
-- them and an existing database already holds rows.
alter table public.incidents
    add column if not exists latitude double precision,
    add column if not exists longitude double precision,
    add column if not exists location_accuracy_m real,
    add column if not exists location_is_approximate boolean not null default false,
    add column if not exists reported_at timestamptz not null default now(),
    add column if not exists photo_path text,
    add column if not exists affected_person_note text,
    add column if not exists is_sos boolean not null default false,
    add column if not exists sos_bridge_token text,
    add column if not exists organisation_id uuid references public.organisations(id);

-- The client decodes every incident into a type whose priority is non-nullable, and one
-- failed row aborts a whole refresh -- a single null would empty the list for every user.
update public.incidents set priority = 'MEDIUM' where priority is null;

alter table public.incidents
    alter column priority set default 'MEDIUM',
    alter column priority set not null;

create index if not exists incidents_reporter_id_idx on public.incidents (reporter_id);
create index if not exists incidents_assignee_id_idx on public.incidents (assignee_id);
create index if not exists incidents_area_id_idx on public.incidents (area_id);
create index if not exists incidents_status_idx on public.incidents (status);
create index if not exists incidents_organisation_id_idx on public.incidents (organisation_id);
create index if not exists incidents_is_sos_idx on public.incidents (is_sos) where is_sos;

create table if not exists public.incident_events (
    id uuid default gen_random_uuid() primary key,
    incident_id uuid references public.incidents(id) on delete cascade,
    actor_id uuid references public.profiles(id),
    event_type text,
    payload jsonb,
    created_at timestamptz default now()
);

-- The client's IncidentEventDto uses different names from the original table. Both
-- spellings are kept and mirrored by a trigger, so old rows stay readable and either
-- writer works.
alter table public.incident_events
    add column if not exists incident_client_id text,
    add column if not exists type text,
    add column if not exists from_value text,
    add column if not exists to_value text,
    add column if not exists note text,
    add column if not exists occurred_at timestamptz not null default now();

alter table public.incident_events alter column event_type drop not null;

create index if not exists incident_events_incident_idx
    on public.incident_events (incident_id, occurred_at desc);

create table if not exists public.responders (
    user_id uuid references public.profiles(id) primary key,
    availability text default 'OFF_SHIFT' not null,
    last_known_location geography(POINT, 4326),
    active_assignment_count int default 0,
    updated_at timestamptz default now()
);

-- The client queries eleven columns; the original table had five.
alter table public.responders
    add column if not exists capabilities text[] not null default '{}',
    add column if not exists last_latitude double precision,
    add column if not exists last_longitude double precision,
    add column if not exists last_location_at timestamptz;

create index if not exists responders_availability_idx on public.responders (availability);

create table if not exists public.incident_assignments (
    id uuid default gen_random_uuid() primary key,
    incident_id uuid not null references public.incidents(id) on delete cascade,
    responder_id uuid not null references public.profiles(id),
    assigned_by uuid references public.profiles(id),
    assigned_at timestamptz not null default now(),
    responded_at timestamptz,
    response text,      -- ACCEPTED / REJECTED / REASSIGNED / null while awaiting
    match_score numeric,
    unique (incident_id, responder_id, assigned_at)
);
create index if not exists incident_assignments_incident_idx
    on public.incident_assignments (incident_id);
create index if not exists incident_assignments_responder_idx
    on public.incident_assignments (responder_id);

create table if not exists public.locations (
    id uuid default gen_random_uuid() primary key,
    user_id uuid not null references public.profiles(id) on delete cascade,
    latitude double precision not null,
    longitude double precision not null,
    accuracy_m real,
    is_approximate boolean not null default false,
    recorded_at timestamptz not null default now()
);
create index if not exists locations_user_recorded_idx
    on public.locations (user_id, recorded_at desc);

create table if not exists public.notifications (
    id uuid default gen_random_uuid() primary key,
    recipient_id uuid not null references public.profiles(id) on delete cascade,
    type text not null,
    title text not null,
    body text not null,
    incident_id uuid references public.incidents(id) on delete cascade,
    received_at timestamptz not null default now(),
    read_at timestamptz
);
create index if not exists notifications_recipient_idx
    on public.notifications (recipient_id, received_at desc);

create table if not exists public.documents (
    id uuid default gen_random_uuid() primary key,
    document_key text not null,
    language_tag text not null,
    title text not null,
    body_markdown text not null,
    version int not null default 1,
    area_id uuid references public.areas(id),
    updated_at timestamptz not null default now(),
    unique (document_key, language_tag)
);

create table if not exists public.communication_channels (
    id uuid default gen_random_uuid() primary key,
    name text not null,
    incident_id uuid references public.incidents(id) on delete cascade,
    area_id uuid references public.areas(id),
    organisation_id uuid references public.organisations(id),
    created_at timestamptz not null default now()
);

create table if not exists public.communication_channel_members (
    channel_id uuid not null references public.communication_channels(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    primary key (channel_id, user_id)
);

create table if not exists public.communication_messages (
    id uuid default gen_random_uuid() primary key,
    client_id text unique not null,
    channel_id uuid not null references public.communication_channels(id) on delete cascade,
    sender_id uuid not null references public.profiles(id),
    body text not null,
    sent_at timestamptz not null default now()
);
create index if not exists communication_messages_channel_idx
    on public.communication_messages (channel_id, sent_at);

create table if not exists public.device_tokens (
    token text primary key,
    user_id uuid not null references public.profiles(id) on delete cascade,
    platform text not null default 'android',
    updated_at timestamptz not null default now()
);
create index if not exists device_tokens_user_idx on public.device_tokens (user_id);


-- =============================================================================
-- 4. PLAN 07 -- LOCATION QR NETWORK
-- =============================================================================
--
-- A QR code is a PLACE, never a person. Codes are installed on checkpoints, water points,
-- medical tents and junctions. They are never placed on wristbands, clothing, identity
-- cards or belongings, and a token resolves to a location and nothing else.
--
-- No configuration lives in the payload, so a help point can move, change its WhatsApp
-- channel or change its staffing without anybody reprinting a sign.

create table if not exists public.qr_locations (
    id uuid default gen_random_uuid() primary key,

    -- The opaque printed token, e.g. VARI-LOC-8F72A91C. Carries no meaning by itself.
    qr_token text unique not null,

    location_name text not null,
    description text,

    latitude double precision not null,
    longitude double precision not null,

    -- The Wari is a sequence, not open ground. route_sequence is what lets the matching
    -- engine reason about "two points further along" rather than straight-line distance.
    route_segment text,
    route_sequence int,

    location_type text not null default 'ROUTE_POINT'
        check (location_type in (
            'CHECKPOINT', 'MEDICAL_POINT', 'VOLUNTEER_POINT', 'REST_AREA',
            'WATER_POINT', 'ROUTE_POINT', 'EMERGENCY_POINT', 'OTHER'
        )),

    status text not null default 'ACTIVE'
        check (status in ('ACTIVE', 'DISABLED', 'REVOKED')),

    -- Whether the public website answers for this sign at all.
    public_page_enabled boolean not null default true,

    -- Public-facing configuration, resolved server-side at scan time. Every field here is
    -- explicitly public; nothing private about a volunteer belongs in this table.
    public_help_point_name text,
    public_contact_label text,
    public_contact_value text,
    whatsapp_channel_url text,

    area_id uuid references public.areas(id),
    organisation_id uuid references public.organisations(id),

    installed_at timestamptz not null default now(),
    last_verified_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists qr_locations_status_idx on public.qr_locations (status);
create index if not exists qr_locations_route_sequence_idx on public.qr_locations (route_sequence);
create index if not exists qr_locations_area_idx on public.qr_locations (area_id);

-- Every resolution is auditable (section 7.12). Deliberately narrow: who scanned, which sign,
-- when, from where if permission already existed, and what it led to. No more than that.
create table if not exists public.qr_scan_events (
    id uuid default gen_random_uuid() primary key,
    qr_token text not null references public.qr_locations(qr_token) on delete cascade,

    -- Null for a public scan: the website resolves a location for anonymous passers-by,
    -- and demanding identity would defeat the point of a public help sign.
    scanned_by uuid references public.profiles(id),
    source text not null default 'PUBLIC_QR'
        check (source in ('PUBLIC_QR', 'VOLUNTEER_APP')),

    scanned_at timestamptz not null default now(),
    device_latitude double precision,
    device_longitude double precision,

    incident_client_id text,
    report_client_id text
);
create index if not exists qr_scan_events_token_idx on public.qr_scan_events (qr_token, scanned_at desc);
create index if not exists qr_scan_events_scanner_idx on public.qr_scan_events (scanned_by);


-- =============================================================================
-- 5. PLAN 07 -- LOST & FOUND
-- =============================================================================
--
-- The table name is unchanged from the pre-Plan-07 schema, deliberately: section 7.21E forbids
-- renaming existing tables or building a parallel person store. What changes is its shape.
--
-- The governing product rule is that A PHOTOGRAPH IS NEVER MANDATORY. Every descriptive
-- column below is nullable so a parent who reaches a volunteer at dusk with no picture and
-- half a description can still file a report the matching engine can work with.

create table if not exists public.lost_found_items (
    id uuid default gen_random_uuid() primary key,
    client_id text unique not null,
    incident_client_id text,
    kind text not null default 'LOST',
    title text not null,
    description text not null default '',
    status text not null default 'OPEN',
    reported_by uuid not null references public.profiles(id),
    reported_at timestamptz not null default now()
);

alter table public.lost_found_items
    -- LOST or FOUND: which side of the separation. Distinct from subject_type, which is
    -- PERSON or ITEM. The original table conflated them in one `kind` column.
    add column if not exists subject_type text not null default 'PERSON',

    add column if not exists person_name text,
    add column if not exists approximate_age int,
    add column if not exists gender text,
    add column if not exists approximate_height_cm int,
    add column if not exists clothing_description text,
    add column if not exists physical_description text,
    add column if not exists language text,
    add column if not exists condition text,
    add column if not exists additional_notes text,

    add column if not exists guardian_name text,
    add column if not exists guardian_phone text,

    -- Three distinct locations, never conflated: the fixed sign, the reporting device's
    -- fix, and the best current belief a volunteer may correct by hand.
    add column if not exists qr_location_token text,
    add column if not exists device_latitude double precision,
    add column if not exists device_longitude double precision,
    add column if not exists last_known_latitude double precision,
    add column if not exists last_known_longitude double precision,
    add column if not exists route_segment text,
    add column if not exists route_sequence int,

    add column if not exists occurred_at timestamptz,

    add column if not exists photo_path text,
    -- Result of server-side face processing. Never blocks the report: every value except
    -- READY simply means "this report contributes no face signal".
    add column if not exists face_match_status text not null default 'NOT_APPLICABLE',

    -- Who is holding a found person right now. Denormalised from the custody chain
    -- because list and map views ask this constantly and must not join per row.
    add column if not exists custodian_user_id uuid references public.profiles(id),
    add column if not exists custodian_name text,
    add column if not exists custodian_contact text,

    add column if not exists updated_at timestamptz not null default now();

-- Migrate pre-Plan-07 rows.
--
-- The old `kind` column held PERSON/ITEM -- the subject, not the side. Every report filed
-- before Plan 07 described something that had been lost, so those become kind = LOST with
-- their old value preserved as subject_type. Guarded by the value check so a second run
-- finds nothing to do.
update public.lost_found_items
   set subject_type = kind,
       kind = 'LOST'
 where kind in ('PERSON', 'ITEM');

-- The old status vocabulary used RESOLVED for what is now REUNITED.
update public.lost_found_items set status = 'REUNITED' where status = 'RESOLVED';

-- Constraints are added only once the data above conforms to them.
do $do$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'lost_found_items_kind_check'
    ) then
        alter table public.lost_found_items
            add constraint lost_found_items_kind_check check (kind in ('LOST', 'FOUND'));
    end if;

    if not exists (
        select 1 from pg_constraint where conname = 'lost_found_items_subject_type_check'
    ) then
        alter table public.lost_found_items
            add constraint lost_found_items_subject_type_check
            check (subject_type in ('PERSON', 'ITEM'));
    end if;

    if not exists (
        select 1 from pg_constraint where conname = 'lost_found_items_status_check'
    ) then
        alter table public.lost_found_items
            add constraint lost_found_items_status_check
            check (status in ('OPEN', 'MATCHED', 'REUNITED', 'CLOSED'));
    end if;

    if not exists (
        select 1 from pg_constraint where conname = 'lost_found_items_face_status_check'
    ) then
        alter table public.lost_found_items
            add constraint lost_found_items_face_status_check
            check (face_match_status in (
                'NOT_APPLICABLE', 'PENDING', 'READY',
                'NO_FACE', 'MULTIPLE_FACES', 'INVALID_IMAGE', 'SERVICE_UNAVAILABLE'
            ));
    end if;

    if not exists (
        select 1 from pg_constraint where conname = 'lost_found_items_qr_location_fkey'
    ) then
        -- Not enforced as a hard FK on the token column, because a report filed offline
        -- against an unrecognised sign must still be storable and reconciled later.
        -- Left as a documented soft reference on purpose.
        null;
    end if;
end
$do$;

create index if not exists lost_found_status_idx on public.lost_found_items (status);
create index if not exists lost_found_kind_idx on public.lost_found_items (kind, status);
create index if not exists lost_found_route_idx on public.lost_found_items (route_sequence);
create index if not exists lost_found_custodian_idx on public.lost_found_items (custodian_user_id);
create index if not exists lost_found_face_status_idx on public.lost_found_items (face_match_status);

-- Custody chain for found people.
--
-- A chain rather than a single mutable field, because "who has this child right now" is
-- the question a frantic parent is actually asking, and the answer has to survive a
-- handover at a shift change.
create table if not exists public.lost_found_custody (
    id uuid default gen_random_uuid() primary key,
    client_id text unique not null,
    report_client_id text not null,
    custodian_user_id uuid not null references public.profiles(id),
    custodian_name text,
    help_point_name text,
    qr_location_token text,
    latitude double precision,
    longitude double precision,
    from_at timestamptz not null default now(),
    until_at timestamptz,          -- null while this is the current custodian
    handover_note text,
    created_at timestamptz not null default now()
);
create index if not exists lost_found_custody_report_idx
    on public.lost_found_custody (report_client_id, from_at desc);
create index if not exists lost_found_custody_custodian_idx
    on public.lost_found_custody (custodian_user_id);

-- Only one open custody span per report. Without this a half-applied handover would leave
-- a found child with two custodians.
create unique index if not exists lost_found_custody_one_current_idx
    on public.lost_found_custody (report_client_id) where until_at is null;

-- Candidate matches awaiting human review.
create table if not exists public.lost_found_matches (
    id uuid default gen_random_uuid() primary key,
    client_id text unique not null,
    lost_report_client_id text not null,
    found_report_client_id text not null,
    overall_score double precision not null,
    confidence text not null check (confidence in ('HIGH', 'MEDIUM', 'LOW')),
    -- The explanation as generated, so what a volunteer reviews is exactly what triggered
    -- the notification rather than a recomputation that may have drifted.
    signals jsonb not null default '[]'::jsonb,
    status text not null default 'CANDIDATE'
        check (status in ('CANDIDATE', 'CONFIRMED', 'REJECTED')),
    created_at timestamptz not null default now(),
    reviewed_by uuid references public.profiles(id),
    reviewed_at timestamptz,
    review_note text,

    -- One verdict per pair. This is what stops the engine re-raising, and re-notifying,
    -- the same candidate on every run.
    unique (lost_report_client_id, found_report_client_id)
);
create index if not exists lost_found_matches_status_idx on public.lost_found_matches (status);
create index if not exists lost_found_matches_lost_idx
    on public.lost_found_matches (lost_report_client_id);
create index if not exists lost_found_matches_found_idx
    on public.lost_found_matches (found_report_client_id);

-- ---------------------------------------------------------------------------
-- Face embeddings -- protected, server-only
-- ---------------------------------------------------------------------------
--
-- A separate table rather than a column on lost_found_items, for one reason: RLS is
-- row-level and cannot hide a single column. A client running `select *` on the report
-- would receive the vector. Splitting the embedding into its own table lets it be granted
-- to nobody at all.
--
-- This is NOT a parallel person store -- it holds no name, no description and no report
-- content, only the derived vector keyed by the report that owns it. section 7.21E's prohibition
-- is on duplicating Lost/Found records into a second database, which this does not do.
create table if not exists public.lost_found_face_data (
    report_client_id text primary key,
    -- Facenet embedding. A plain float array, chosen over a vector extension type so the
    -- schema has no dependency the rest of the project does not already carry.
    embedding double precision[] not null,
    model text not null default 'Facenet',
    detector text,
    -- How many augmentation variants were averaged into the stored vector.
    sample_count int not null default 1,
    processed_at timestamptz not null default now()
);

-- Biometric data about children. No client role may read, write, or even see that a row
-- exists; only the service role, which bypasses RLS as table owner, may touch it.
revoke all on public.lost_found_face_data from authenticated;
revoke all on public.lost_found_face_data from anon;


-- =============================================================================
-- 6. FUNCTIONS
-- =============================================================================
-- Every SECURITY DEFINER function pins search_path: without it, one can be hijacked by a
-- caller-controlled schema.

create or replace function public.current_role()
returns text
language sql
stable
security definer
set search_path = public
as $fn$
    select r.wire_name
      from public.profiles p
      join public.roles r on r.id = p.role_id
     where p.id = (select auth.uid());
$fn$;

create or replace function public.is_command()
returns boolean
language sql
stable
security definer
set search_path = public
as $fn$
    select coalesce(public.current_role() in ('ORGANISER', 'ADMINISTRATOR'), false);
$fn$;

create or replace function public.is_responder()
returns boolean
language sql
stable
security definer
set search_path = public
as $fn$
    select coalesce(
        public.current_role() in ('MEDICAL_RESPONDER', 'POLICE_RESPONDER', 'NGO_RESPONDER'),
        false
    );
$fn$;

create or replace function public.my_area_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $fn$
    select area_id from public.profiles where id = (select auth.uid());
$fn$;

create or replace function public.my_organisation_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $fn$
    select organisation_id from public.profiles where id = (select auth.uid());
$fn$;

-- Find-or-create an organisation by name, case-insensitively.
create or replace function public.organisation_id_for_name(p_name text)
returns uuid
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_name text := nullif(trim(p_name), '');
    v_id uuid;
begin
    if v_name is null then
        return null;
    end if;

    select id into v_id from public.organisations where lower(name) = lower(v_name) limit 1;
    if v_id is not null then
        return v_id;
    end if;

    insert into public.organisations (name) values (v_name) returning id into v_id;
    return v_id;
exception when unique_violation then
    -- Two sign-ups naming the same new organisation can race. The loser re-reads rather
    -- than failing the sign-up.
    select id into v_id from public.organisations where lower(name) = lower(v_name) limit 1;
    return v_id;
end;
$fn$;

-- Every responder needs a roster row, or the matcher cannot see them.
create or replace function public.ensure_responder_row(p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $fn$
begin
    insert into public.responders (user_id, availability)
    select p_user_id, 'OFF_SHIFT'
    from public.profiles p
    join public.roles r on r.id = p.role_id
    where p.id = p_user_id
      and r.wire_name in ('MEDICAL_RESPONDER', 'POLICE_RESPONDER', 'NGO_RESPONDER')
    on conflict (user_id) do nothing;
end;
$fn$;

-- Profile creation on sign-up.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_requested_role text;
    v_role record;
    v_display_name text;
    v_org_name text;
    v_org_id uuid;
begin
    v_requested_role := nullif(trim(new.raw_user_meta_data ->> 'role'), '');
    v_org_name       := nullif(trim(new.raw_user_meta_data ->> 'organisation_name'), '');

    select id, wire_name, self_assignable
      into v_role
      from public.roles
     where wire_name = v_requested_role;

    -- Unknown, absent, or non-claimable: fall back to the least privileged role. Never
    -- fall back upwards.
    if v_role.id is null or coalesce(v_role.self_assignable, false) = false then
        select id, wire_name, self_assignable
          into v_role from public.roles where wire_name = 'VOLUNTEER';
    end if;

    if v_role.id is null then
        raise exception 'roles table is not seeded: VOLUNTEER is missing';
    end if;

    -- Enforced here as well as in the app: a responder with no organisation cannot be
    -- routed to, and client-side validation is not a control a tampered client respects.
    if v_role.wire_name in ('MEDICAL_RESPONDER', 'POLICE_RESPONDER', 'NGO_RESPONDER')
       and v_org_name is null then
        raise exception 'organisation_name is required for responder sign-up'
            using errcode = 'check_violation';
    end if;

    v_org_id := public.organisation_id_for_name(v_org_name);

    v_display_name := coalesce(
        nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
        split_part(new.email, '@', 1)
    );

    insert into public.profiles (id, display_name, role_id, organisation_id)
    values (new.id, v_display_name, v_role.id, v_org_id)
    on conflict (id) do nothing;

    perform public.ensure_responder_row(new.id);

    return new;
end;
$fn$;

-- A plain UPDATE policy on profiles would let a user set their own role_id. Columns cannot
-- be restricted in a policy, so the change is reverted here instead.
create or replace function public.prevent_profile_privilege_escalation()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
begin
    if new.role_id is distinct from old.role_id
        or new.organisation_id is distinct from old.organisation_id
        or new.area_id is distinct from old.area_id
    then
        -- Silently preserve rather than raise: this fires on ordinary profile edits, and
        -- an error would block a legitimate display-name change.
        new.role_id := old.role_id;
        new.organisation_id := old.organisation_id;
        new.area_id := old.area_id;
    end if;

    new.updated_at := now();
    return new;
end;
$fn$;

create or replace function public.touch_updated_at()
returns trigger language plpgsql as $fn$
begin
    new.updated_at := now();
    return new;
end;
$fn$;

create or replace function public.sync_incident_event_columns()
returns trigger language plpgsql as $fn$
begin
    -- Whichever name the writer used, fill in the other.
    new.event_type := coalesce(new.event_type, new.type);
    new.type       := coalesce(new.type, new.event_type);
    return new;
end;
$fn$;

-- Derive an incident's scope from its reporter. The Android client knows the reporter and
-- nothing else; this is what gives an incident the area and organisation that the
-- visibility policies filter on.
create or replace function public.stamp_incident_reporter_context()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_org_id uuid;
    v_area_id uuid;
begin
    if new.organisation_id is null or new.area_id is null then
        select p.organisation_id, p.area_id into v_org_id, v_area_id
          from public.profiles p where p.id = new.reporter_id;

        -- Through coalesce, not straight into NEW: a reporter with no profile row would
        -- otherwise blank an organisation the client did supply.
        new.organisation_id := coalesce(new.organisation_id, v_org_id);
        new.area_id := coalesce(new.area_id, v_area_id);
    end if;

    return new;
end;
$fn$;

-- A queued offline write that replays later must not undo a triage decision made while
-- the device was disconnected.
create or replace function public.preserve_incident_triage()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
begin
    if old.status <> 'REPORTED'
       and new.priority is distinct from old.priority
       and not public.is_command()
    then
        new.priority := old.priority;
    end if;

    return new;
end;
$fn$;

-- Haversine in plain SQL rather than PostGIS: matching should not depend on where the
-- extension was installed, and at these distances the difference is irrelevant.
create or replace function public.distance_metres(
    lat1 double precision, lon1 double precision,
    lat2 double precision, lon2 double precision
)
returns double precision
language sql
immutable
parallel safe
as $fn$
    select case
        when lat1 is null or lon1 is null or lat2 is null or lon2 is null then null
        else 6371000 * 2 * asin(sqrt(
            power(sin(radians(lat2 - lat1) / 2), 2)
            + cos(radians(lat1)) * cos(radians(lat2))
                * power(sin(radians(lon2 - lon1) / 2), 2)
        ))
    end;
$fn$;

-- The responder roster the client reads.
--
-- security_invoker: the view evaluates under the CALLER's RLS, not the owner's. Without
-- it a view is a hole straight through every policy underneath it.
create or replace view public.responder_directory
with (security_invoker = true) as
select
    r.user_id,
    p.display_name,
    ro.wire_name as role,
    r.availability,
    p.area_id,
    p.organisation_id,
    r.capabilities,
    r.last_latitude,
    r.last_longitude,
    r.last_location_at,
    r.active_assignment_count
from public.responders r
join public.profiles p on p.id = r.user_id
join public.roles ro on ro.id = p.role_id;

-- Matching runs here rather than on the device because scoring must read across every
-- responder -- exactly the data RLS hides from any individual client.
--
-- Weights are a proposed implementation decision, mirrored as unit-tested documentation in
-- MatchingRules.kt. There is one live implementation and it is this one.
create or replace function public.match_responder(p_incident_id uuid)
returns uuid
language plpgsql
stable
security definer
set search_path = public
as $fn$
declare
    v_incident public.incidents;
    v_reporter_org uuid;
    v_best uuid;
begin
    select * into v_incident from public.incidents where id = p_incident_id;
    if not found then return null; end if;

    select organisation_id into v_reporter_org
      from public.profiles where id = v_incident.reporter_id;

    select d.user_id into v_best
      from public.responder_directory d
     -- Availability is a hard filter, not a score: dispatching to somebody off shift is
     -- not a worse match, it is a non-match.
     where d.availability = 'AVAILABLE'
     order by (
        case
            when v_incident.category = 'MEDICAL'     and d.role = 'MEDICAL_RESPONDER' then 40
            when v_incident.category = 'CROWD_SURGE' and d.role = 'POLICE_RESPONDER'  then 40
            when v_incident.category = 'LOST_PERSON' and d.role = 'POLICE_RESPONDER'  then 35
            when v_incident.category in ('WATER', 'SANITATION')
                 and d.role = 'NGO_RESPONDER' then 35
            when v_incident.category = 'BLOCKED_ROAD' and d.role = 'POLICE_RESPONDER' then 30
            -- Any responder beats no responder: a medical case attended late by a police
            -- responder is better than one nobody was sent to.
            else 10
        end
        + case
            when v_incident.category = 'MEDICAL' and 'FIRST_AID' = any (d.capabilities) then 15
            when v_incident.category = 'LOST_PERSON'
                 and 'CHILD_SAFEGUARDING' = any (d.capabilities) then 15
            else 0
        end
        + case
            when v_incident.area_id is not null and d.area_id = v_incident.area_id then 15
            else 0
        end
        + case
            when v_reporter_org is not null and d.organisation_id = v_reporter_org then 10
            else 0
        end
        -- Proximity, and only from a fix still worth trusting. A position older than the
        -- staleness window is UNKNOWN, not current.
        + case
            when d.last_location_at is null
                 or d.last_location_at < now() - interval '15 minutes'
                 or v_incident.latitude is null
                then 0
            else greatest(0, 15 - (coalesce(
                    public.distance_metres(
                        v_incident.latitude, v_incident.longitude,
                        d.last_latitude, d.last_longitude), 100000) / 200))
        end
        - least(20, d.active_assignment_count * 5)
     ) desc,
     -- Deterministic tie-break, so an assignment can be reproduced when audited.
     d.active_assignment_count asc,
     d.user_id asc
     limit 1;

    return v_best;
end;
$fn$;

create or replace function public.assign_incident(
    p_incident_id uuid,
    p_assigned_by uuid default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_responder uuid;
    v_previous uuid;
begin
    select assignee_id into v_previous from public.incidents where id = p_incident_id;

    v_responder := public.match_responder(p_incident_id);

    if v_responder is null then
        -- No candidate. The incident stays REPORTED and visible in the unclaimed pool;
        -- this is emphatically not an error.
        insert into public.incident_events (incident_id, actor_id, type, note)
        values (p_incident_id, p_assigned_by, 'ASSIGNMENT_FAILED',
                'no available responder matched');
        return null;
    end if;

    insert into public.incident_assignments (incident_id, responder_id, assigned_by)
    values (p_incident_id, v_responder, p_assigned_by);

    update public.incidents
       set assignee_id = v_responder, status = 'ASSIGNED'
     where id = p_incident_id;

    update public.responders
       set active_assignment_count = active_assignment_count + 1
     where user_id = v_responder;

    if v_previous is not null and v_previous <> v_responder then
        update public.responders
           set active_assignment_count = greatest(0, active_assignment_count - 1)
         where user_id = v_previous;
    end if;

    insert into public.incident_events (incident_id, actor_id, type, from_value, to_value, note)
    values (p_incident_id, p_assigned_by, 'ASSIGNED',
            v_previous::text, v_responder::text, 'matched by public.match_responder');

    return v_responder;
end;
$fn$;

-- Best-effort, and that word is load-bearing: this runs inside the client's INSERT
-- transaction, so an exception here would fail the insert and lose the incident. The
-- product rule that an incident is never blocked outranks matching.
create or replace function public.auto_assign_new_incident()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
begin
    begin
        perform public.assign_incident(new.id, null);
    exception when others then
        raise notice 'auto-assignment failed for incident %: %', new.id, sqlerrm;
    end;
    return null;
end;
$fn$;

-- active_assignment_count feeds the matcher's workload term. If it only ever went up,
-- every responder would drift to the bottom of the ranking and stay there.
create or replace function public.release_responder_on_close()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
begin
    if new.status in ('RESOLVED', 'CANCELLED')
       and old.status not in ('RESOLVED', 'CANCELLED')
       and new.assignee_id is not null
    then
        update public.responders
           set active_assignment_count = greatest(0, active_assignment_count - 1)
         where user_id = new.assignee_id;
    end if;
    return new;
end;
$fn$;

-- ---------------------------------------------------------------------------
-- Plan 07 functions
-- ---------------------------------------------------------------------------

-- Public location resolution.
--
-- SECURITY DEFINER and callable by anon on purpose: this is what a pilgrim's phone camera
-- reaches when it opens the sign's link. It returns ONLY the fields marked public, so an
-- anonymous caller can never see internal identifiers, staffing, or operational data.
create or replace function public.resolve_public_location(p_token text)
returns table (
    qr_token text,
    location_name text,
    description text,
    latitude double precision,
    longitude double precision,
    route_segment text,
    route_sequence int,
    location_type text,
    help_point_name text,
    contact_label text,
    contact_value text,
    whatsapp_channel_url text
)
language sql
stable
security definer
set search_path = public
as $fn$
    select
        l.qr_token,
        l.location_name,
        l.description,
        l.latitude,
        l.longitude,
        l.route_segment,
        l.route_sequence,
        l.location_type,
        l.public_help_point_name,
        l.public_contact_label,
        l.public_contact_value,
        l.whatsapp_channel_url
    from public.qr_locations l
    where l.qr_token = p_token
      and l.status = 'ACTIVE'
      and l.public_page_enabled;
$fn$;

grant execute on function public.resolve_public_location(text) to anon, authenticated;

-- Nearby assistance points for the public journey view (section 7.8).
create or replace function public.public_nearby_points(
    p_latitude double precision,
    p_longitude double precision,
    p_radius_metres double precision default 5000
)
returns table (
    location_name text,
    location_type text,
    latitude double precision,
    longitude double precision,
    distance_metres double precision
)
language sql
stable
security definer
set search_path = public
as $fn$
    select
        l.location_name,
        l.location_type,
        l.latitude,
        l.longitude,
        public.distance_metres(p_latitude, p_longitude, l.latitude, l.longitude)
    from public.qr_locations l
    where l.status = 'ACTIVE'
      and l.public_page_enabled
      and public.distance_metres(p_latitude, p_longitude, l.latitude, l.longitude)
          <= p_radius_metres
    order by 5 asc
    limit 25;
$fn$;

grant execute on function public.public_nearby_points(
    double precision, double precision, double precision) to anon, authenticated;

-- Stamp a Lost & Found report with its QR location's coordinates and route position.
--
-- The sign is an exact known point; a phone fix in a crowd is not. Copying route_sequence
-- across is what lets the matching engine reason about progression along the route.
create or replace function public.stamp_lost_found_location()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_loc public.qr_locations;
begin
    if new.qr_location_token is null then
        return new;
    end if;

    select * into v_loc from public.qr_locations where qr_token = new.qr_location_token;
    if not found then
        -- An offline report filed against a sign this server does not know. Keep it: the
        -- token is retained and the location attaches if the sign is added later.
        return new;
    end if;

    new.route_segment  := coalesce(new.route_segment, v_loc.route_segment);
    new.route_sequence := coalesce(new.route_sequence, v_loc.route_sequence);
    new.last_known_latitude  := coalesce(new.last_known_latitude, v_loc.latitude);
    new.last_known_longitude := coalesce(new.last_known_longitude, v_loc.longitude);

    return new;
end;
$fn$;

-- Notify both sides when a candidate match is raised (section 7.21F).
--
-- The notification body carries no name, description or photograph: it lands on a lock
-- screen in public, and the authoritative detail is behind the protected review screen.
create or replace function public.notify_lost_found_match()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_lost_reporter uuid;
    v_found_reporter uuid;
    v_custodian uuid;
begin
    if new.status <> 'CANDIDATE' then
        return new;
    end if;

    select reported_by into v_lost_reporter
      from public.lost_found_items where client_id = new.lost_report_client_id;

    select reported_by, custodian_user_id into v_found_reporter, v_custodian
      from public.lost_found_items where client_id = new.found_report_client_id;

    -- distinct: the same volunteer may be reporter and custodian, and must not receive
    -- the same alert twice.
    insert into public.notifications (recipient_id, type, title, body)
    select distinct r, 'LOST_FOUND_MATCH',
           'Possible Lost & Found match found',
           'An active report may match another. Review the candidate match.'
      from unnest(array[v_lost_reporter, v_found_reporter, v_custodian]) as r
     where r is not null;

    return new;
end;
$fn$;

-- Mark both reports reunited when a human confirms, and only then.
--
-- No amount of facial similarity reaches this function on its own: it fires on a status
-- change to CONFIRMED, which only the review screen can produce.
create or replace function public.apply_lost_found_match_verdict()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
begin
    if new.status = 'CONFIRMED' and old.status is distinct from 'CONFIRMED' then
        update public.lost_found_items
           set status = 'REUNITED', updated_at = now()
         where client_id in (new.lost_report_client_id, new.found_report_client_id);

    elsif new.status = 'REJECTED' and old.status is distinct from 'REJECTED' then
        -- A rejection returns both reports to the pool. Neither underlying report is
        -- altered by having been wrongly paired.
        update public.lost_found_items
           set status = 'OPEN', updated_at = now()
         where client_id in (new.lost_report_client_id, new.found_report_client_id)
           and status = 'MATCHED';
    end if;

    return new;
end;
$fn$;

-- Keep the denormalised custodian on the report in step with the custody chain.
create or replace function public.sync_current_custodian()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
begin
    if new.until_at is null then
        update public.lost_found_items
           set custodian_user_id = new.custodian_user_id,
               custodian_name = coalesce(new.custodian_name, custodian_name),
               updated_at = now()
         where client_id = new.report_client_id;
    end if;
    return new;
end;
$fn$;


-- =============================================================================
-- 7. TRIGGERS
-- =============================================================================

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

drop trigger if exists profiles_prevent_escalation on public.profiles;
create trigger profiles_prevent_escalation
    before update on public.profiles
    for each row execute function public.prevent_profile_privilege_escalation();

drop trigger if exists incidents_touch_updated_at on public.incidents;
create trigger incidents_touch_updated_at
    before update on public.incidents
    for each row execute function public.touch_updated_at();

drop trigger if exists incidents_stamp_reporter_context on public.incidents;
create trigger incidents_stamp_reporter_context
    before insert on public.incidents
    for each row execute function public.stamp_incident_reporter_context();

drop trigger if exists incidents_preserve_triage on public.incidents;
create trigger incidents_preserve_triage
    before update on public.incidents
    for each row execute function public.preserve_incident_triage();

drop trigger if exists incidents_auto_assign on public.incidents;
create trigger incidents_auto_assign
    after insert on public.incidents
    for each row execute function public.auto_assign_new_incident();

drop trigger if exists incidents_release_responder on public.incidents;
create trigger incidents_release_responder
    after update on public.incidents
    for each row execute function public.release_responder_on_close();

drop trigger if exists incident_events_sync_columns on public.incident_events;
create trigger incident_events_sync_columns
    before insert or update on public.incident_events
    for each row execute function public.sync_incident_event_columns();

drop trigger if exists qr_locations_touch_updated_at on public.qr_locations;
create trigger qr_locations_touch_updated_at
    before update on public.qr_locations
    for each row execute function public.touch_updated_at();

drop trigger if exists lost_found_stamp_location on public.lost_found_items;
create trigger lost_found_stamp_location
    before insert or update on public.lost_found_items
    for each row execute function public.stamp_lost_found_location();

drop trigger if exists lost_found_touch_updated_at on public.lost_found_items;
create trigger lost_found_touch_updated_at
    before update on public.lost_found_items
    for each row execute function public.touch_updated_at();

drop trigger if exists lost_found_matches_notify on public.lost_found_matches;
create trigger lost_found_matches_notify
    after insert on public.lost_found_matches
    for each row execute function public.notify_lost_found_match();

drop trigger if exists lost_found_matches_verdict on public.lost_found_matches;
create trigger lost_found_matches_verdict
    after update on public.lost_found_matches
    for each row execute function public.apply_lost_found_match_verdict();

drop trigger if exists lost_found_custody_sync on public.lost_found_custody;
create trigger lost_found_custody_sync
    after insert or update on public.lost_found_custody
    for each row execute function public.sync_current_custodian();


-- =============================================================================
-- 8. ROW LEVEL SECURITY
-- =============================================================================

alter table public.profiles                     enable row level security;
alter table public.incidents                    enable row level security;
alter table public.organisations                enable row level security;
alter table public.areas                        enable row level security;
alter table public.roles                        enable row level security;
alter table public.incident_assignments         enable row level security;
alter table public.incident_events              enable row level security;
alter table public.responders                   enable row level security;
alter table public.locations                    enable row level security;
alter table public.notifications                enable row level security;
alter table public.documents                    enable row level security;
alter table public.communication_channels       enable row level security;
alter table public.communication_channel_members enable row level security;
alter table public.communication_messages       enable row level security;
alter table public.device_tokens                enable row level security;
alter table public.qr_locations                 enable row level security;
alter table public.qr_scan_events               enable row level security;
alter table public.lost_found_items             enable row level security;
alter table public.lost_found_custody           enable row level security;
alter table public.lost_found_matches           enable row level security;
alter table public.lost_found_face_data         enable row level security;

-- --- reference data: readable by any signed-in user, writable through the API by nobody
drop policy if exists "Signed-in users read organisations" on public.organisations;
create policy "Signed-in users read organisations" on public.organisations
    for select to authenticated using (true);

drop policy if exists "Signed-in users read areas" on public.areas;
create policy "Signed-in users read areas" on public.areas
    for select to authenticated using (true);

drop policy if exists "Signed-in users read roles" on public.roles;
create policy "Signed-in users read roles" on public.roles
    for select to authenticated using (true);

-- --- profiles
drop policy if exists "Users can read their own profile" on public.profiles;
create policy "Users can read their own profile" on public.profiles
    for select to authenticated using ( (select auth.uid()) = id );

drop policy if exists "Command reads area profiles" on public.profiles;
create policy "Command reads area profiles" on public.profiles
    for select to authenticated using ( public.is_command() );

drop policy if exists "Users can update their own profile" on public.profiles;
create policy "Users can update their own profile" on public.profiles
    for update to authenticated
    using ( (select auth.uid()) = id )
    with check ( (select auth.uid()) = id );

-- --- incidents
drop policy if exists "Volunteers can create incidents" on public.incidents;
create policy "Volunteers can create incidents" on public.incidents
    for insert to authenticated with check ( (select auth.uid()) = reporter_id );

drop policy if exists "Volunteers can read incidents they reported" on public.incidents;
create policy "Volunteers can read incidents they reported" on public.incidents
    for select to authenticated using ( (select auth.uid()) = reporter_id );

drop policy if exists "Assignees read their incidents" on public.incidents;
create policy "Assignees read their incidents" on public.incidents
    for select to authenticated using ( (select auth.uid()) = assignee_id );

drop policy if exists "Command reads area incidents" on public.incidents;
create policy "Command reads area incidents" on public.incidents
    for select to authenticated using ( public.is_command() );

-- A responder sees an incident when it is theirs to act on: assigned to them, scoped to
-- their organisation or area, or sitting unclaimed in the open pool.
drop policy if exists "Responders read actionable incidents" on public.incidents;
create policy "Responders read actionable incidents" on public.incidents
    for select to authenticated
    using (
        public.is_responder()
        and (
            assignee_id = (select auth.uid())
            or organisation_id = public.my_organisation_id()
            or area_id = public.my_area_id()
            or (assignee_id is null and status not in ('RESOLVED', 'CANCELLED'))
        )
    );

-- WITH CHECK as well as USING: without it a responder could reassign an incident away.
drop policy if exists "Assignees update their incidents" on public.incidents;
create policy "Assignees update their incidents" on public.incidents
    for update to authenticated
    using ( (select auth.uid()) = assignee_id )
    with check ( (select auth.uid()) = assignee_id );

drop policy if exists "Reporters update their unassigned incidents" on public.incidents;
create policy "Reporters update their unassigned incidents" on public.incidents
    for update to authenticated
    using ( (select auth.uid()) = reporter_id and assignee_id is null )
    with check ( (select auth.uid()) = reporter_id );

drop policy if exists "Command updates area incidents" on public.incidents;
create policy "Command updates area incidents" on public.incidents
    for update to authenticated
    using ( public.is_command() ) with check ( public.is_command() );

-- --- assignments
drop policy if exists "Responders read their assignments" on public.incident_assignments;
create policy "Responders read their assignments" on public.incident_assignments
    for select to authenticated
    using ( (select auth.uid()) = responder_id or public.is_command() );

drop policy if exists "Responders respond to their assignments" on public.incident_assignments;
create policy "Responders respond to their assignments" on public.incident_assignments
    for update to authenticated
    using ( (select auth.uid()) = responder_id )
    with check ( (select auth.uid()) = responder_id );

drop policy if exists "Command creates assignments" on public.incident_assignments;
create policy "Command creates assignments" on public.incident_assignments
    for insert to authenticated with check ( public.is_command() );

-- --- audit trail: append-only. No UPDATE or DELETE policy exists anywhere, deliberately.
drop policy if exists "Participants read incident events" on public.incident_events;
create policy "Participants read incident events" on public.incident_events
    for select to authenticated
    using (
        public.is_command()
        or exists (
            select 1 from public.incidents i
            where i.id = incident_id
              and ( i.reporter_id = (select auth.uid()) or i.assignee_id = (select auth.uid()) )
        )
    );

drop policy if exists "Signed-in users append incident events" on public.incident_events;
create policy "Signed-in users append incident events" on public.incident_events
    for insert to authenticated with check ( (select auth.uid()) = actor_id );

-- --- responders
drop policy if exists "Responders read roster" on public.responders;
create policy "Responders read roster" on public.responders
    for select to authenticated
    using ( (select auth.uid()) = user_id or public.is_command() );

drop policy if exists "Responders set their own availability" on public.responders;
create policy "Responders set their own availability" on public.responders
    for update to authenticated
    using ( (select auth.uid()) = user_id ) with check ( (select auth.uid()) = user_id );

drop policy if exists "Responders create their own row" on public.responders;
create policy "Responders create their own row" on public.responders
    for insert to authenticated with check ( (select auth.uid()) = user_id );

-- --- locations
drop policy if exists "Users write their own location" on public.locations;
create policy "Users write their own location" on public.locations
    for insert to authenticated with check ( (select auth.uid()) = user_id );

drop policy if exists "Users and command read locations" on public.locations;
create policy "Users and command read locations" on public.locations
    for select to authenticated
    using ( (select auth.uid()) = user_id or public.is_command() );

-- --- notifications
drop policy if exists "Users read their notifications" on public.notifications;
create policy "Users read their notifications" on public.notifications
    for select to authenticated using ( (select auth.uid()) = recipient_id );

drop policy if exists "Users mark their notifications read" on public.notifications;
create policy "Users mark their notifications read" on public.notifications
    for update to authenticated
    using ( (select auth.uid()) = recipient_id )
    with check ( (select auth.uid()) = recipient_id );

-- --- documents
drop policy if exists "Signed-in users read documents" on public.documents;
create policy "Signed-in users read documents" on public.documents
    for select to authenticated using (true);

-- --- communication: membership-scoped
drop policy if exists "Members read their channels" on public.communication_channels;
create policy "Members read their channels" on public.communication_channels
    for select to authenticated
    using (
        public.is_command()
        or exists (
            select 1 from public.communication_channel_members m
            where m.channel_id = id and m.user_id = (select auth.uid())
        )
    );

drop policy if exists "Members read membership" on public.communication_channel_members;
create policy "Members read membership" on public.communication_channel_members
    for select to authenticated
    using ( (select auth.uid()) = user_id or public.is_command() );

drop policy if exists "Members read channel messages" on public.communication_messages;
create policy "Members read channel messages" on public.communication_messages
    for select to authenticated
    using (
        exists (
            select 1 from public.communication_channel_members m
            where m.channel_id = channel_id and m.user_id = (select auth.uid())
        )
    );

drop policy if exists "Members send channel messages" on public.communication_messages;
create policy "Members send channel messages" on public.communication_messages
    for insert to authenticated
    with check (
        (select auth.uid()) = sender_id
        and exists (
            select 1 from public.communication_channel_members m
            where m.channel_id = channel_id and m.user_id = (select auth.uid())
        )
    );

-- --- device tokens
drop policy if exists "Users manage their device tokens" on public.device_tokens;
create policy "Users manage their device tokens" on public.device_tokens
    for all to authenticated
    using ( (select auth.uid()) = user_id ) with check ( (select auth.uid()) = user_id );

-- --- QR locations
--
-- Readable by any signed-in user: a location is a public physical place and holds nothing
-- sensitive. Anonymous access goes through resolve_public_location() instead of a policy,
-- so the public website sees only the explicitly public columns.
drop policy if exists "Signed-in users read locations" on public.qr_locations;
create policy "Signed-in users read locations" on public.qr_locations
    for select to authenticated using ( status = 'ACTIVE' );

drop policy if exists "Command manages locations" on public.qr_locations;
create policy "Command manages locations" on public.qr_locations
    for all to authenticated
    using ( public.is_command() ) with check ( public.is_command() );

drop policy if exists "Users log their own scans" on public.qr_scan_events;
create policy "Users log their own scans" on public.qr_scan_events
    for insert to authenticated with check ( (select auth.uid()) = scanned_by );

drop policy if exists "Command reads scan audit" on public.qr_scan_events;
create policy "Command reads scan audit" on public.qr_scan_events
    for select to authenticated
    using ( (select auth.uid()) = scanned_by or public.is_command() );

-- --- Lost & Found
--
-- Visible to every signed-in user. Finding a missing child depends on the widest possible
-- set of eyes, and the alternative -- scoping reports to an area -- is how a child found one
-- route point outside their reporter's area stays unmatched.
drop policy if exists "Signed-in users read lost and found" on public.lost_found_items;
create policy "Signed-in users read lost and found" on public.lost_found_items
    for select to authenticated using (true);

drop policy if exists "Users file lost and found reports" on public.lost_found_items;
create policy "Users file lost and found reports" on public.lost_found_items
    for insert to authenticated with check ( (select auth.uid()) = reported_by );

-- The custodian is included: whoever is holding a found child must be able to update
-- their condition and location without asking the original reporter.
drop policy if exists "Reporters and custodians update lost and found" on public.lost_found_items;
create policy "Reporters and custodians update lost and found" on public.lost_found_items
    for update to authenticated
    using (
        (select auth.uid()) = reported_by
        or (select auth.uid()) = custodian_user_id
        or public.is_command()
    )
    with check (
        (select auth.uid()) = reported_by
        or (select auth.uid()) = custodian_user_id
        or public.is_command()
    );

drop policy if exists "Signed-in users read custody" on public.lost_found_custody;
create policy "Signed-in users read custody" on public.lost_found_custody
    for select to authenticated using (true);

drop policy if exists "Users record custody" on public.lost_found_custody;
create policy "Users record custody" on public.lost_found_custody
    for insert to authenticated with check ( (select auth.uid()) = custodian_user_id );

-- Closing your own span is how a handover works: the outgoing custodian ends their span
-- and the incoming one opens theirs.
drop policy if exists "Custodians close their own span" on public.lost_found_custody;
create policy "Custodians close their own span" on public.lost_found_custody
    for update to authenticated
    using ( (select auth.uid()) = custodian_user_id or public.is_command() )
    with check ( (select auth.uid()) = custodian_user_id or public.is_command() );

drop policy if exists "Signed-in users read matches" on public.lost_found_matches;
create policy "Signed-in users read matches" on public.lost_found_matches
    for select to authenticated using (true);

drop policy if exists "Signed-in users raise matches" on public.lost_found_matches;
create policy "Signed-in users raise matches" on public.lost_found_matches
    for insert to authenticated with check (true);

drop policy if exists "Signed-in users review matches" on public.lost_found_matches;
create policy "Signed-in users review matches" on public.lost_found_matches
    for update to authenticated using (true) with check (true);

-- Face embeddings: no policy at all, on purpose. RLS is enabled and no policy admits
-- anybody, so `authenticated` and `anon` are denied every operation. Section 8A revokes the
-- table privilege too, which is what keeps that true once the blanket grant there runs.
-- Only the service role may read or write. Embeddings never reach a client.


-- =============================================================================
-- 8A. TABLE PRIVILEGES
-- =============================================================================
--
-- RLS decides WHICH ROWS a caller may touch. It cannot grant access on its own: PostgREST
-- checks ordinary table privileges first, and a role holding no GRANT is refused before a
-- single policy is evaluated.
--
-- A project that does not auto-expose new tables to the Data API roles -- `[api]
-- auto_expose_new_tables = false` in config.toml, and the effective behaviour of a cloud
-- project whose tables arrive by migration rather than through the dashboard -- leaves
-- every table above unreachable no matter how correct section 8 is. The symptom is
-- SQLSTATE 42501, `permission denied for table profiles`, on every request from a
-- perfectly valid session: sign-in succeeds, the profile fetch behind it does not, and
-- the app is left with no role to route on.
--
-- Granting broadly here is safe precisely because section 8 enables RLS on every one of
-- these tables. The grant opens the door; the policies decide who walks through.

grant usage on schema public to anon, authenticated, service_role;

-- No DELETE in the general grant. Section 9 explains why removal is not something clients
-- do -- realtime delete payloads are not filtered by RLS -- so cancellation is a status
-- transition and nothing is ever removed.
grant select, insert, update on all tables in schema public to authenticated;

-- The one real deletion a client performs. A device token must not outlive the session
-- that registered it, or push for this user keeps arriving on a phone they signed out of.
grant delete on public.device_tokens to authenticated;

-- Edge functions run as service_role. It bypasses RLS, but it is not the owner of these
-- tables and needs privileges like any other role -- including on lost_found_face_data,
-- which it is the only role permitted to touch at all.
grant all on all tables in schema public to service_role;

-- Nothing is granted to `anon`, deliberately. The public website reaches
-- resolve_public_location() and public_nearby_points() -- SECURITY DEFINER, section 6 --
-- and has no table access of any kind.

-- Re-asserted after the blanket grant above, which would otherwise have just handed the
-- face embeddings to every signed-in client. Biometric data about children stays
-- server-only; this revoke is what keeps the "no policy at all" note above true.
revoke all on public.lost_found_face_data from anon, authenticated;

-- Future tables. Without this, the next migration that adds one reintroduces exactly the
-- failure this section exists to fix, and it fails the same silent way.
alter default privileges in schema public
    grant select, insert, update on tables to authenticated;
alter default privileges in schema public
    grant all on tables to service_role;


-- =============================================================================
-- 9. REALTIME
-- =============================================================================

do $do$
declare
    t text;
begin
    foreach t in array array[
        'incidents', 'responders', 'incident_assignments',
        'communication_messages', 'incident_events',
        'lost_found_items', 'lost_found_matches'
    ]
    loop
        if not exists (
            select 1 from pg_publication_tables
            where pubname = 'supabase_realtime' and tablename = t
        ) then
            execute format('alter publication supabase_realtime add table public.%I', t);
        end if;
    end loop;
end
$do$;

-- RLS is NOT applied to realtime DELETE events -- delete payloads reach every subscriber.
-- Revoking delete is what keeps pilgrim data out of them. Cancellation is a status
-- transition, never a row removal.
revoke delete on public.incidents from authenticated;
revoke delete on public.incident_assignments from authenticated;
revoke delete on public.communication_messages from authenticated;
revoke delete on public.responders from authenticated;
revoke delete on public.incident_events from authenticated;
revoke delete on public.lost_found_items from authenticated;
revoke delete on public.lost_found_matches from authenticated;


-- =============================================================================
-- 10. SEED DATA
-- =============================================================================

insert into public.roles (wire_name) values
    ('VOLUNTEER'), ('MEDICAL_RESPONDER'), ('POLICE_RESPONDER'),
    ('NGO_RESPONDER'), ('ORGANISER'), ('ADMINISTRATOR')
on conflict (wire_name) do nothing;


-- =============================================================================
-- 11. BACKFILL FOR EXISTING DATA
-- =============================================================================
-- Only relevant to a database that already held rows. On a fresh database every statement
-- below matches nothing.

-- Accounts created while sign-up discarded the selected role. Their choice survives in
-- auth metadata only if the client was already sending it, so this repairs what it can.
--
-- profiles_prevent_escalation reverts role_id changes on UPDATE, which is correct for
-- user-initiated edits and wrong for this one-off repair -- hence the disable/enable.
do $do$
begin
    if exists (
        select 1 from pg_trigger
        where tgrelid = 'public.profiles'::regclass
          and tgname = 'profiles_prevent_escalation'
    ) then
        alter table public.profiles disable trigger profiles_prevent_escalation;
    end if;
end
$do$;

update public.profiles p
   set role_id = r.id,
       organisation_id = coalesce(
           p.organisation_id,
           public.organisation_id_for_name(u.raw_user_meta_data ->> 'organisation_name')
       ),
       updated_at = now()
  from auth.users u
  join public.roles r
    on r.wire_name = nullif(trim(u.raw_user_meta_data ->> 'role'), '')
   and r.self_assignable
 where p.id = u.id
   and p.role_id is distinct from r.id;

-- Accounts that predate the profile trigger entirely and have no profile at all: without
-- one they are stuck on the splash screen forever.
insert into public.profiles (id, display_name, role_id)
select
    u.id,
    coalesce(nullif(trim(u.raw_user_meta_data ->> 'display_name'), ''),
             split_part(u.email, '@', 1)),
    (select id from public.roles where wire_name = 'VOLUNTEER')
from auth.users u
left join public.profiles p on p.id = u.id
where p.id is null
on conflict (id) do nothing;

do $do$
begin
    if exists (
        select 1 from pg_trigger
        where tgrelid = 'public.profiles'::regclass
          and tgname = 'profiles_prevent_escalation'
    ) then
        alter table public.profiles enable trigger profiles_prevent_escalation;
    end if;
end
$do$;

-- Responders who registered before the roster row existed.
do $do$
declare
    v_id uuid;
begin
    for v_id in
        select p.id from public.profiles p
        join public.roles r on r.id = p.role_id
        where r.wire_name in ('MEDICAL_RESPONDER', 'POLICE_RESPONDER', 'NGO_RESPONDER')
    loop
        perform public.ensure_responder_row(v_id);
    end loop;
end
$do$;

-- Reports that carry a photo but have never been through face processing.
update public.lost_found_items
   set face_match_status = 'PENDING'
 where photo_path is not null
   and face_match_status = 'NOT_APPLICABLE';
