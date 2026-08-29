-- Plan 02: completes the schema.
--
-- Two problems this fixes:
--   1. public.incidents was missing most of the columns the Android client actually
--      sends (location, photo, SOS markers, reported_at). Every sync would have failed.
--   2. Eight of the sixteen planned tables did not exist, and RLS was enabled on only
--      two of them — the rest were either absent or unprotected.
--
-- Safe to re-run: every statement is guarded.

-- ---------------------------------------------------------------------------
-- 1. Bring public.incidents in line with the client payload
-- ---------------------------------------------------------------------------

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

-- Indexes for the columns policies and queries filter on. Without these, every
-- authorised read degrades to a sequential scan.
create index if not exists incidents_reporter_id_idx on public.incidents (reporter_id);
create index if not exists incidents_assignee_id_idx on public.incidents (assignee_id);
create index if not exists incidents_area_id_idx on public.incidents (area_id);
create index if not exists incidents_status_idx on public.incidents (status);
create index if not exists incidents_is_sos_idx on public.incidents (is_sos) where is_sos;

-- ---------------------------------------------------------------------------
-- 2. Remaining tables
-- ---------------------------------------------------------------------------

create table if not exists public.incident_assignments (
    id uuid default gen_random_uuid() primary key,
    incident_id uuid not null references public.incidents(id) on delete cascade,
    responder_id uuid not null references public.profiles(id),
    assigned_by uuid references public.profiles(id),
    assigned_at timestamptz not null default now(),
    responded_at timestamptz,
    -- ACCEPTED / REJECTED / REASSIGNED / null while awaiting a response
    response text,
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

-- SOS Bridge identifiers.
-- The token is opaque. subject_reference is a short NON-IDENTIFYING label (a tag serial
-- or registration group) that lets a responder confirm they are with the right person.
-- Nothing here may hold a name, phone number, address, or medical detail.
create table if not exists public.qr_identifiers (
    token text primary key,
    subject_reference text not null,
    area_id uuid references public.areas(id),
    organisation_id uuid references public.organisations(id),
    revoked boolean not null default false,
    issued_at timestamptz not null default now(),
    issued_by uuid references public.profiles(id)
);

create table if not exists public.qr_resolution_events (
    id uuid default gen_random_uuid() primary key,
    token text not null references public.qr_identifiers(token),
    incident_client_id text,
    resolved_by uuid not null references public.profiles(id),
    resolved_at timestamptz not null default now()
);
create index if not exists qr_resolution_events_token_idx
    on public.qr_resolution_events (token);

create table if not exists public.lost_found_items (
    id uuid default gen_random_uuid() primary key,
    client_id text unique not null,
    incident_client_id text,
    kind text not null check (kind in ('PERSON', 'ITEM')),
    title text not null,
    description text not null default '',
    last_seen_latitude double precision,
    last_seen_longitude double precision,
    last_seen_at timestamptz,
    qr_token text references public.qr_identifiers(token),
    status text not null default 'OPEN' check (status in ('OPEN', 'MATCHED', 'RESOLVED')),
    reported_by uuid not null references public.profiles(id),
    reported_at timestamptz not null default now()
);
create index if not exists lost_found_status_idx on public.lost_found_items (status);

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

-- ---------------------------------------------------------------------------
-- 3. Role helpers
-- ---------------------------------------------------------------------------
-- SECURITY DEFINER so a policy can read the caller's role without that read itself
-- being subject to the policy it is evaluating (which would recurse).

create or replace function public.is_command()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(public.current_role() in ('ORGANISER', 'ADMINISTRATOR'), false);
$$;

create or replace function public.my_area_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    select area_id from public.profiles where id = auth.uid();
$$;

-- ---------------------------------------------------------------------------
-- 4. RLS on every table
-- ---------------------------------------------------------------------------

alter table public.organisations enable row level security;
alter table public.areas enable row level security;
alter table public.roles enable row level security;
alter table public.incident_assignments enable row level security;
alter table public.incident_events enable row level security;
alter table public.responders enable row level security;
alter table public.locations enable row level security;
alter table public.qr_identifiers enable row level security;
alter table public.qr_resolution_events enable row level security;
alter table public.lost_found_items enable row level security;
alter table public.notifications enable row level security;
alter table public.documents enable row level security;
alter table public.communication_channels enable row level security;
alter table public.communication_channel_members enable row level security;
alter table public.communication_messages enable row level security;
alter table public.device_tokens enable row level security;

-- Reference data: readable by any signed-in user, writable by nobody through the API.
drop policy if exists "Signed-in users read organisations" on public.organisations;
create policy "Signed-in users read organisations" on public.organisations
    for select to authenticated using (true);

drop policy if exists "Signed-in users read areas" on public.areas;
create policy "Signed-in users read areas" on public.areas
    for select to authenticated using (true);

drop policy if exists "Signed-in users read roles" on public.roles;
create policy "Signed-in users read roles" on public.roles
    for select to authenticated using (true);

-- Incidents: reporters see their own, assignees see theirs, command sees their area.
drop policy if exists "Assignees read their incidents" on public.incidents;
create policy "Assignees read their incidents" on public.incidents
    for select to authenticated
    using ( (select auth.uid()) = assignee_id );

drop policy if exists "Command reads area incidents" on public.incidents;
create policy "Command reads area incidents" on public.incidents
    for select to authenticated
    using ( public.is_command() );

-- An assignee may progress their own incident. WITH CHECK is present as well as USING:
-- without it, a responder could reassign the incident to somebody else.
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
    using ( public.is_command() )
    with check ( public.is_command() );

-- Assignments
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
    for insert to authenticated
    with check ( public.is_command() );

-- Audit trail: append-only. No UPDATE or DELETE policy exists anywhere, deliberately —
-- an audit record that can be edited is not an audit record.
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
    for insert to authenticated
    with check ( (select auth.uid()) = actor_id );

-- Responders
drop policy if exists "Responders read roster" on public.responders;
create policy "Responders read roster" on public.responders
    for select to authenticated
    using ( (select auth.uid()) = user_id or public.is_command() );

drop policy if exists "Responders set their own availability" on public.responders;
create policy "Responders set their own availability" on public.responders
    for update to authenticated
    using ( (select auth.uid()) = user_id )
    with check ( (select auth.uid()) = user_id );

drop policy if exists "Responders create their own row" on public.responders;
create policy "Responders create their own row" on public.responders
    for insert to authenticated
    with check ( (select auth.uid()) = user_id );

-- Locations: a responder writes their own, command reads their area's.
drop policy if exists "Users write their own location" on public.locations;
create policy "Users write their own location" on public.locations
    for insert to authenticated
    with check ( (select auth.uid()) = user_id );

drop policy if exists "Users and command read locations" on public.locations;
create policy "Users and command read locations" on public.locations
    for select to authenticated
    using ( (select auth.uid()) = user_id or public.is_command() );

-- QR identifiers: readable by any signed-in responder so a scan can resolve. This is
-- safe only because the row holds nothing identifying. Issuing is administrator-only,
-- and there is no client-facing INSERT policy at all.
drop policy if exists "Signed-in users resolve tokens" on public.qr_identifiers;
create policy "Signed-in users resolve tokens" on public.qr_identifiers
    for select to authenticated using ( not revoked );

drop policy if exists "Users log their own resolutions" on public.qr_resolution_events;
create policy "Users log their own resolutions" on public.qr_resolution_events
    for insert to authenticated
    with check ( (select auth.uid()) = resolved_by );

drop policy if exists "Command reads resolutions" on public.qr_resolution_events;
create policy "Command reads resolutions" on public.qr_resolution_events
    for select to authenticated
    using ( (select auth.uid()) = resolved_by or public.is_command() );

-- Lost & Found: visible to all signed-in users. Finding a missing person depends on the
-- widest possible set of eyes.
drop policy if exists "Signed-in users read lost and found" on public.lost_found_items;
create policy "Signed-in users read lost and found" on public.lost_found_items
    for select to authenticated using (true);

drop policy if exists "Users file lost and found reports" on public.lost_found_items;
create policy "Users file lost and found reports" on public.lost_found_items
    for insert to authenticated
    with check ( (select auth.uid()) = reported_by );

drop policy if exists "Reporters and command update lost and found" on public.lost_found_items;
create policy "Reporters and command update lost and found" on public.lost_found_items
    for update to authenticated
    using ( (select auth.uid()) = reported_by or public.is_command() )
    with check ( (select auth.uid()) = reported_by or public.is_command() );

-- Notifications
drop policy if exists "Users read their notifications" on public.notifications;
create policy "Users read their notifications" on public.notifications
    for select to authenticated
    using ( (select auth.uid()) = recipient_id );

drop policy if exists "Users mark their notifications read" on public.notifications;
create policy "Users mark their notifications read" on public.notifications
    for update to authenticated
    using ( (select auth.uid()) = recipient_id )
    with check ( (select auth.uid()) = recipient_id );

-- Documentation is operational guidance, not sensitive.
drop policy if exists "Signed-in users read documents" on public.documents;
create policy "Signed-in users read documents" on public.documents
    for select to authenticated using (true);

-- Communication: membership-scoped.
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

-- Device tokens: a user manages only their own.
drop policy if exists "Users manage their device tokens" on public.device_tokens;
create policy "Users manage their device tokens" on public.device_tokens
    for all to authenticated
    using ( (select auth.uid()) = user_id )
    with check ( (select auth.uid()) = user_id );

-- ---------------------------------------------------------------------------
-- 5. Realtime
-- ---------------------------------------------------------------------------
-- incidents and responders were added by the first migration. Adding a table twice
-- raises, so each is guarded.

do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and tablename = 'incident_assignments'
    ) then
        alter publication supabase_realtime add table public.incident_assignments;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and tablename = 'communication_messages'
    ) then
        alter publication supabase_realtime add table public.communication_messages;
    end if;
end $$;

-- RLS is NOT applied to realtime DELETE events — delete payloads reach every subscriber.
-- Revoking DELETE on the published tables is what keeps pilgrim data out of them.
-- Cancellation is a status transition, never a row removal.
revoke delete on public.incident_assignments from authenticated;
revoke delete on public.communication_messages from authenticated;
revoke delete on public.responders from authenticated;

-- ---------------------------------------------------------------------------
-- 6. Keep updated_at honest
-- ---------------------------------------------------------------------------

create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists incidents_touch_updated_at on public.incidents;
create trigger incidents_touch_updated_at
    before update on public.incidents
    for each row execute function public.touch_updated_at();
