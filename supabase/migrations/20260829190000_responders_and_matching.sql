-- Phase 6.2 and 6.3: responder availability, and the matching engine.
--
-- Two problems this fixes:
--
--   1. public.responders had five columns; the Android client queries eleven. Every
--      availability read (`ResponderDto`) and every location write failed against a
--      column that did not exist, so the roster was permanently empty and a responder
--      could not go on shift.
--   2. Nothing matched an incident to a responder. The PRD's chain is
--      prioritise -> match -> notify, and the middle link was absent: an incident was
--      created, prioritised on-device, and then sat there with assignee_id null forever.
--
-- Matching runs here rather than on the device deliberately. Scoring has to read across
-- every responder, and that is exactly the data RLS hides from any individual client. A
-- client-side matcher would need a "everyone reads all responders" policy, which
-- contradicts the access model in migration 20260829150000.
--
-- Safe to re-run: every statement is guarded.

-- ---------------------------------------------------------------------------
-- 1. Bring public.responders in line with the client
-- ---------------------------------------------------------------------------

alter table public.responders
    add column if not exists capabilities text[] not null default '{}',
    add column if not exists last_latitude double precision,
    add column if not exists last_longitude double precision,
    add column if not exists last_location_at timestamptz;

create index if not exists responders_availability_idx
    on public.responders (availability);

-- ---------------------------------------------------------------------------
-- 2. The roster the client actually reads
-- ---------------------------------------------------------------------------
-- display_name, role, area, and organisation live on profiles and roles, not on
-- responders. Rather than denormalise them (and then have to keep them in step), the
-- client reads a view shaped exactly like its ResponderDto.
--
-- security_invoker: the view evaluates under the *caller's* RLS, not the view owner's.
-- Without it a view silently becomes a hole straight through every policy underneath it.

create or replace view public.responder_directory
with (security_invoker = true) as
select
    r.user_id,
    p.display_name,
    ro.wire_name        as role,
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

-- ---------------------------------------------------------------------------
-- 3. Every responder needs a roster row
-- ---------------------------------------------------------------------------
-- Sign-up created a profile but never a responders row, so a newly registered medical
-- responder did not exist as far as matching was concerned.

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

-- Backfill responders who registered before this existed.
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

-- Hook it into sign-up. Re-declared in full because 20260829180000 owns this function;
-- the only change is the ensure_responder_row call before the return.
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

    if v_role.id is null or coalesce(v_role.self_assignable, false) = false then
        select id, wire_name, self_assignable
          into v_role
          from public.roles
         where wire_name = 'VOLUNTEER';
    end if;

    if v_role.id is null then
        raise exception 'roles table is not seeded: VOLUNTEER is missing';
    end if;

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

    -- A responder that the matcher cannot see is a responder who never gets dispatched.
    perform public.ensure_responder_row(new.id);

    return new;
end;
$fn$;

-- ---------------------------------------------------------------------------
-- 4. Align public.incident_events with the client and the matcher
-- ---------------------------------------------------------------------------
-- The base table had (event_type, payload). The client's IncidentEventDto expects
-- (type, from_value, to_value, note, occurred_at, incident_client_id). Both spellings are
-- kept and mirrored, so existing rows stay readable and new writers can use either.

alter table public.incident_events
    add column if not exists incident_client_id text,
    add column if not exists type text,
    add column if not exists from_value text,
    add column if not exists to_value text,
    add column if not exists note text,
    add column if not exists occurred_at timestamptz not null default now();

alter table public.incident_events
    alter column event_type drop not null;

create or replace function public.sync_incident_event_columns()
returns trigger
language plpgsql
as $fn$
begin
    -- Whichever name the writer used, fill in the other. event_type was NOT NULL before
    -- this migration, so a writer using only `type` would otherwise be rejected.
    new.event_type := coalesce(new.event_type, new.type);
    new.type       := coalesce(new.type, new.event_type);
    return new;
end;
$fn$;

drop trigger if exists incident_events_sync_columns on public.incident_events;

create trigger incident_events_sync_columns
    before insert or update on public.incident_events
    for each row execute function public.sync_incident_event_columns();

create index if not exists incident_events_incident_idx
    on public.incident_events (incident_id, occurred_at desc);

-- Redeclared from 20260829150000 with auth.uid() wrapped in a subselect. In a plain
-- function body the wrapping changes nothing at runtime, but this function is called from
-- inside RLS policies, and the project-wide rule is that every auth.uid() reachable from a
-- policy is written as (select auth.uid()) so the planner treats it as a cached initplan
-- rather than re-evaluating per row. Editing the applied migration in place would not
-- re-run it, so the correction lives here.
create or replace function public.my_area_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $fn$
    select area_id from public.profiles where id = (select auth.uid());
$fn$;

-- ---------------------------------------------------------------------------
-- 5. Distance helper
-- ---------------------------------------------------------------------------
-- Haversine in plain SQL rather than PostGIS. The base migration installs PostGIS into
-- the `extensions` schema, which is not on this function's pinned search_path; depending
-- on it here would couple matching to that placement for no benefit at these distances.

create or replace function public.distance_metres(
    lat1 double precision,
    lon1 double precision,
    lat2 double precision,
    lon2 double precision
)
returns double precision
language sql
immutable
parallel safe
as $fn$
    select case
        when lat1 is null or lon1 is null or lat2 is null or lon2 is null then null
        else 6371000 * 2 * asin(
            sqrt(
                power(sin(radians(lat2 - lat1) / 2), 2)
                + cos(radians(lat1)) * cos(radians(lat2))
                    * power(sin(radians(lon2 - lon1) / 2), 2)
            )
        )
    end;
$fn$;

-- ---------------------------------------------------------------------------
-- 6. The matcher
-- ---------------------------------------------------------------------------
-- Scores the PRD's six criteria. Weights are a proposed implementation decision, not a
-- source requirement -- they are documented as such in Project Summary.md and mirrored in
-- MatchingRules.kt as unit-tested documentation only. There is one live implementation
-- and it is this one.
--
--   role fit          0..40   a MEDICAL incident wants a medical responder
--   capability fit    0..15   incident category maps to a required capability
--   area fit          0..15   responder posted to the incident's area
--   organisation fit  0..10   same organisation as the reporter
--   proximity         0..15   nearer is better, and only if the fix is fresh
--   workload         -20..0   already-loaded responders slide down
--
-- Availability is a hard filter, not a score: dispatching to somebody who is off shift is
-- not a worse match, it is a non-match.

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
    if not found then
        return null;
    end if;

    select organisation_id into v_reporter_org
      from public.profiles where id = v_incident.reporter_id;

    select d.user_id
      into v_best
      from public.responder_directory d
     where d.availability = 'AVAILABLE'
     order by (
            -- role fit
            case
                when v_incident.category = 'MEDICAL'      and d.role = 'MEDICAL_RESPONDER' then 40
                when v_incident.category = 'CROWD_SURGE'  and d.role = 'POLICE_RESPONDER'  then 40
                when v_incident.category = 'LOST_PERSON'  and d.role = 'POLICE_RESPONDER'  then 35
                when v_incident.category in ('WATER', 'SANITATION') and d.role = 'NGO_RESPONDER' then 35
                when v_incident.category = 'BLOCKED_ROAD' and d.role = 'POLICE_RESPONDER'  then 30
                -- Any responder beats no responder. A medical case handled late by a
                -- police responder is better than one nobody was sent to.
                else 10
            end

            -- capability fit
            + case
                when v_incident.category = 'MEDICAL'
                     and 'FIRST_AID' = any (d.capabilities) then 15
                when v_incident.category = 'LOST_PERSON'
                     and 'CHILD_SAFEGUARDING' = any (d.capabilities) then 15
                else 0
            end

            -- area fit
            + case
                when v_incident.area_id is not null and d.area_id = v_incident.area_id then 15
                else 0
            end

            -- organisation fit
            + case
                when v_reporter_org is not null and d.organisation_id = v_reporter_org then 10
                else 0
            end

            -- proximity, and only from a fix that is still worth trusting. A position
            -- older than the staleness window is unknown, NOT current -- scoring it as
            -- current would send somebody who left the area twenty minutes ago.
            + case
                when d.last_location_at is null
                     or d.last_location_at < now() - interval '15 minutes'
                     or v_incident.latitude is null
                    then 0
                else greatest(
                    0,
                    15 - (
                        coalesce(
                            public.distance_metres(
                                v_incident.latitude, v_incident.longitude,
                                d.last_latitude, d.last_longitude
                            ),
                            100000
                        ) / 200
                    )
                )
            end

            -- workload
            - least(20, d.active_assignment_count * 5)
        ) desc,
        -- Deterministic tie-break, so the same inputs always pick the same responder and
        -- the choice is reproducible when auditing an assignment after the fact.
        d.active_assignment_count asc,
        d.user_id asc
     limit 1;

    return v_best;
end;
$fn$;

-- ---------------------------------------------------------------------------
-- 7. Assignment
-- ---------------------------------------------------------------------------

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
        -- it is emphatically not an error.
        insert into public.incident_events (incident_id, actor_id, type, note)
        values (p_incident_id, p_assigned_by, 'ASSIGNMENT_FAILED',
                'no available responder matched');
        return null;
    end if;

    insert into public.incident_assignments (incident_id, responder_id, assigned_by)
    values (p_incident_id, v_responder, p_assigned_by);

    update public.incidents
       set assignee_id = v_responder,
           status = 'ASSIGNED'
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
    values (
        p_incident_id,
        p_assigned_by,
        'ASSIGNED',
        v_previous::text,
        v_responder::text,
        'matched by public.match_responder'
    );

    return v_responder;
end;
$fn$;

-- ---------------------------------------------------------------------------
-- 8. Auto-assign on arrival
-- ---------------------------------------------------------------------------
-- Best-effort, and that word is load-bearing. This trigger runs inside the client's
-- INSERT transaction, so an exception here would fail the insert and lose the incident.
-- The PRD's rule outranks matching: an incident is never blocked by anything.

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
        -- Swallowed on purpose. An unmatched incident is a queued incident; a failed
        -- insert is a lost one.
        raise notice 'auto-assignment failed for incident %: %', new.id, sqlerrm;
    end;

    return null;
end;
$fn$;

drop trigger if exists incidents_auto_assign on public.incidents;

create trigger incidents_auto_assign
    after insert on public.incidents
    for each row execute function public.auto_assign_new_incident();

-- ---------------------------------------------------------------------------
-- 9. Keep the workload counter honest
-- ---------------------------------------------------------------------------
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

drop trigger if exists incidents_release_responder on public.incidents;

create trigger incidents_release_responder
    after update on public.incidents
    for each row execute function public.release_responder_on_close();

-- ---------------------------------------------------------------------------
-- 10. Realtime
-- ---------------------------------------------------------------------------
-- incident_events is what the audit and activity views read live.

do $do$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and tablename = 'incident_events'
    ) then
        alter publication supabase_realtime add table public.incident_events;
    end if;
end
$do$;

-- RLS does not filter realtime DELETE payloads -- they reach every subscriber. Revoking
-- delete is what keeps incident data out of them.
revoke delete on public.incident_events from authenticated;
