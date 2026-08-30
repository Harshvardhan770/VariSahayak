-- =============================================================================
-- Incident lifecycle logging
-- =============================================================================
--
-- Extends the existing `public.incident_events` audit trail so the timeline reflects what
-- the system actually did, rather than what a client remembered to report. No new table:
-- section 3 of the consolidated schema already defines the trail, section 8 already has an
-- append-only policy on it, and section 9 already publishes it to realtime.
--
-- WHY TRIGGERS AND NOT CLIENT WRITES. A client can only log what it did itself. Priority
-- decisions, responder matching and assignment all happen inside the database — a device
-- that files an incident and goes offline never observes the responder that was chosen for
-- it. Logging where the decision is taken is the only way the trail can be true.
--
-- Idempotent: every trigger is dropped and recreated, and every function is `create or
-- replace`. Safe to run twice.


-- =============================================================================
-- 1. INDEXES FOR THE ADMIN ACTIVITY LOG
-- =============================================================================
-- `incident_events_incident_idx (incident_id, occurred_at desc)` already exists and serves
-- the per-incident timeline. These serve the cross-incident filters instead.

create index if not exists incident_events_type_idx
    on public.incident_events (type, occurred_at desc);

create index if not exists incident_events_actor_idx
    on public.incident_events (actor_id, occurred_at desc);

create index if not exists incident_events_occurred_idx
    on public.incident_events (occurred_at desc);


-- =============================================================================
-- 2. LOGGING HELPER
-- =============================================================================

-- One place that writes the trail, so every event has the same shape.
--
-- SECURITY DEFINER because it is called from triggers running as whoever happened to make
-- the change, and the insert policy on incident_events requires actor_id = auth.uid() —
-- which is exactly wrong for an event the *system* generated with no actor at all.
create or replace function public.log_incident_event(
    p_incident_id uuid,
    p_type text,
    p_actor_id uuid default null,
    p_from text default null,
    p_to text default null,
    p_note text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $fn$
begin
    insert into public.incident_events
        (incident_id, actor_id, event_type, type, from_value, to_value, note, occurred_at)
    values
        (p_incident_id, p_actor_id, p_type, p_type, p_from, p_to, p_note, now());
exception when others then
    -- Never fails the operation being logged. An incident that cannot be written is a
    -- lost emergency; an incident whose audit row is missing is a lesser problem, and
    -- trading the first for the second would be the wrong way round.
    raise notice 'lifecycle logging failed for % (%): %', p_incident_id, p_type, sqlerrm;
end;
$fn$;


-- =============================================================================
-- 3. CREATION
-- =============================================================================

-- INCIDENT_REPORTED, plus the offline distinction.
--
-- `reported_at` is the device's own timestamp and `created_at` is when the row reached the
-- server. When they differ materially the incident was filed offline and synced later, and
-- BOTH facts are recorded with their real timestamps — the report keeps the time it
-- happened, which is what every response metric is measured from.
create or replace function public.log_incident_created()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_offline boolean;
begin
    v_offline := new.reported_at is not null
                 and new.reported_at < now() - interval '90 seconds';

    if v_offline then
        insert into public.incident_events
            (incident_id, actor_id, event_type, type, to_value, note, occurred_at)
        values
            (new.id, new.reporter_id, 'INCIDENT_CREATED_OFFLINE', 'INCIDENT_CREATED_OFFLINE',
             new.category::text, 'Filed on-device without connectivity', new.reported_at);

        perform public.log_incident_event(
            new.id, 'INCIDENT_SYNCED', new.reporter_id, null, null,
            'Reached the server ' ||
            round(extract(epoch from (now() - new.reported_at)))::text || 's after filing'
        );
    else
        insert into public.incident_events
            (incident_id, actor_id, event_type, type, to_value, occurred_at)
        values
            (new.id, new.reporter_id, 'INCIDENT_REPORTED', 'INCIDENT_REPORTED',
             new.category::text, coalesce(new.reported_at, now()));
    end if;

    -- An SOS is the single most important thing about an incident and must be visible on
    -- the timeline without reading the description.
    if new.is_sos then
        perform public.log_incident_event(
            new.id, 'SOS_TRIGGERED', new.reporter_id, null, null,
            'Raised through the SOS bridge'
        );
    end if;

    -- The priority the row arrived with. PRIORITY_ASSIGNED rather than a status change,
    -- because triage is what it represents and it is what the triage metric measures from.
    perform public.log_incident_event(
        new.id, 'PRIORITY_ASSIGNED', null, null, new.priority,
        case
            when new.is_sos and new.category = 'MEDICAL'
                then 'Medical emergency with SOS flag'
            when new.is_sos then 'SOS flag raised'
            else 'Category ' || new.category::text
        end
    );

    return null;
end;
$fn$;

drop trigger if exists incidents_log_created on public.incidents;
create trigger incidents_log_created
    after insert on public.incidents
    for each row execute function public.log_incident_created();


-- =============================================================================
-- 4. STATUS AND PRIORITY TRANSITIONS
-- =============================================================================

-- Every status change, named as the stage it represents.
--
-- The client's own STATUS_CHANGED rows are kept for its offline trail; these are the
-- server's record of what the database actually committed, which is the authoritative one.
create or replace function public.log_incident_transition()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_type text;
begin
    if new.status is distinct from old.status then
        v_type := case new.status::text
            when 'TRIAGED' then 'PRIORITY_ASSIGNED'
            when 'ASSIGNED' then 'ASSIGNMENT_SENT'
            when 'ACCEPTED' then 'ASSIGNMENT_ACCEPTED'
            when 'IN_PROGRESS' then 'RESPONDER_ARRIVED'
            when 'RESOLVED' then 'INCIDENT_RESOLVED'
            when 'CANCELLED' then 'INCIDENT_CANCELLED'
            when 'ESCALATED' then 'INCIDENT_ESCALATED'
            when 'REASSIGNMENT_REQUIRED' then 'REASSIGNMENT_REQUIRED'
            else 'STATUS_CHANGED'
        end;

        perform public.log_incident_event(
            new.id, v_type, (select auth.uid()),
            old.status::text, new.status::text, null
        );
    end if;

    -- Separate from the status change: a triage decision can be revised without the
    -- incident moving stage, and losing that would hide a real operational action.
    if new.priority is distinct from old.priority then
        perform public.log_incident_event(
            new.id,
            case when public.is_command() then 'MANUAL_PRIORITY_OVERRIDE'
                 else 'PRIORITY_UPDATED' end,
            (select auth.uid()), old.priority, new.priority, null
        );
    end if;

    return null;
end;
$fn$;

drop trigger if exists incidents_log_transition on public.incidents;
create trigger incidents_log_transition
    after update on public.incidents
    for each row execute function public.log_incident_transition();


-- =============================================================================
-- 5. MATCHING AND ASSIGNMENT
-- =============================================================================

-- RESPONDER_MATCHED, with the reasoning that produced it.
--
-- The note explains *why* this responder — the question a command user reviewing a
-- dispatch actually asks, and one that cannot be reconstructed after the fact because the
-- inputs (who was available, how far away, how loaded) have all moved on by then.
--
-- No responder name and no coordinates: the note is a rationale, and the trail is read on
-- screens that show far less than the incident record itself.
create or replace function public.log_responder_matched()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
declare
    v_incident public.incidents;
    v_distance double precision;
    v_reasons text[] := '{}';
    v_role text;
    v_caps text[];
    v_count int;
begin
    select * into v_incident from public.incidents where id = new.incident_id;
    if not found then return null; end if;

    select ro.wire_name, r.capabilities, r.active_assignment_count,
           public.distance_metres(
               v_incident.latitude, v_incident.longitude, r.last_latitude, r.last_longitude)
      into v_role, v_caps, v_count, v_distance
      from public.responders r
      join public.profiles p on p.id = r.user_id
      join public.roles ro on ro.id = p.role_id
     where r.user_id = new.responder_id;

    if v_distance is not null then
        v_reasons := v_reasons || (round(v_distance)::text || 'm away');
    end if;
    if v_role is not null then
        v_reasons := v_reasons || replace(v_role, '_', ' ');
    end if;
    if v_caps is not null and array_length(v_caps, 1) > 0 then
        v_reasons := v_reasons || (array_to_string(v_caps, ', ') || ' capability');
    end if;
    v_reasons := v_reasons || (coalesce(v_count, 0)::text || ' active assignments');

    perform public.log_incident_event(
        new.incident_id, 'RESPONDER_MATCHED', new.assigned_by, null,
        new.responder_id::text,
        array_to_string(v_reasons, ' · ')
    );

    perform public.log_incident_event(
        new.incident_id, 'ASSIGNMENT_CREATED', new.assigned_by, null,
        new.responder_id::text,
        case when new.match_score is not null
             then 'Match score ' || round(new.match_score)::text
             else null end
    );

    return null;
end;
$fn$;

drop trigger if exists assignments_log_matched on public.incident_assignments;
create trigger assignments_log_matched
    after insert on public.incident_assignments
    for each row execute function public.log_responder_matched();


-- The responder's answer.
create or replace function public.log_assignment_response()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
begin
    if new.response is distinct from old.response and new.response is not null then
        perform public.log_incident_event(
            new.incident_id,
            case new.response
                when 'ACCEPTED' then 'ASSIGNMENT_ACCEPTED'
                when 'REJECTED' then 'ASSIGNMENT_REJECTED'
                when 'REASSIGNED' then 'REASSIGNMENT_REQUIRED'
                else 'STATUS_CHANGED'
            end,
            new.responder_id, old.response, new.response, null
        );
    end if;
    return null;
end;
$fn$;

drop trigger if exists assignments_log_response on public.incident_assignments;
create trigger assignments_log_response
    after update on public.incident_assignments
    for each row execute function public.log_assignment_response();


-- MATCHING_STARTED, logged before the search runs.
--
-- Wrapped around the existing auto_assign_new_incident rather than replacing it: the
-- "best effort, never blocks the insert" contract in the original is the reason an
-- incident is never lost to a matching failure, and it is preserved exactly.
create or replace function public.auto_assign_new_incident()
returns trigger
language plpgsql
security definer
set search_path = public
as $fn$
begin
    begin
        perform public.log_incident_event(
            new.id, 'MATCHING_STARTED', null, null, null,
            'Searching available responders'
        );

        perform public.assign_incident(new.id, null);
    exception when others then
        raise notice 'auto-assignment failed for incident %: %', new.id, sqlerrm;
    end;
    return null;
end;
$fn$;


-- =============================================================================
-- 6. AUDIT INTEGRITY
-- =============================================================================
--
-- The trail is append-only and stays that way. Section 8 grants INSERT and SELECT and no
-- UPDATE or DELETE policy exists, so RLS already refuses both — but section 8A's blanket
-- `grant select, insert, update on all tables` handed the privilege back, and a table with
-- a privilege and no policy is one policy away from being mutable. Revoked explicitly so
-- the guarantee does not depend on nobody ever adding one.
revoke update on public.incident_events from authenticated;
revoke delete on public.incident_events from authenticated;
