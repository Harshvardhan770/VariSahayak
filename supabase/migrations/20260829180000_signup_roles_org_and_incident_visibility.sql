-- Fixes three defects:
--
--   1. Sign-up ignored the role the user picked. handle_new_user() hard-coded VOLUNTEER,
--      so every account -- medical, police, NGO, organiser -- landed on the volunteer
--      role_id and got the volunteer dashboard.
--   2. Responders had no organisation. There was nowhere to record which hospital, unit,
--      or NGO a responder answers for, so profiles.organisation_id was always null.
--   3. An incident filed by a volunteer was invisible to everyone who could act on it.
--      The only SELECT policies on public.incidents were "I reported it", "I am the
--      assignee", and "I am command". A responder is none of those until somebody has
--      already assigned them, which nothing does -- so the queue looked permanently empty.
--
-- Safe to re-run: every statement is guarded.

-- ---------------------------------------------------------------------------
-- 1. Roles: which roles a user may claim at sign-up
-- ---------------------------------------------------------------------------
-- SECURITY NOTE. The previous trigger refused to read the role from
-- raw_user_meta_data at all, because that payload is client-supplied and trusting it
-- lets anyone self-register as ADMINISTRATOR. That protection is real, but it was
-- implemented by discarding the user's choice entirely, which is the bug being fixed.
--
-- The replacement keeps the decision in the database: a role is claimable at sign-up
-- only if roles.self_assignable is true. Every role ships self_assignable so that each
-- option in the app's role picker works, as specified. To lock the privileged roles down
-- for a production deployment, run:
--
--   update public.roles set self_assignable = false
--    where wire_name in ('ORGANISER', 'ADMINISTRATOR');
--
-- No application change is needed -- a request for a non-claimable role falls back to
-- VOLUNTEER, exactly as before.

alter table public.roles
    add column if not exists self_assignable boolean not null default true;

insert into public.roles (wire_name) values
    ('VOLUNTEER'), ('MEDICAL_RESPONDER'), ('POLICE_RESPONDER'),
    ('NGO_RESPONDER'), ('ORGANISER'), ('ADMINISTRATOR')
on conflict (wire_name) do nothing;

-- ---------------------------------------------------------------------------
-- 2. Organisations: resolve by name, create on first use
-- ---------------------------------------------------------------------------
-- A responder types their organisation as free text. Matching is case-insensitive so
-- "City Hospital" and "city hospital" are one organisation, not two.

do $do$
begin
    create unique index if not exists organisations_name_lower_key
        on public.organisations (lower(name));
exception when unique_violation then
    raise notice 'public.organisations already holds duplicate names; unique index skipped';
end
$do$;

create or replace function public.organisation_id_for_name(p_name text)
returns uuid
language plpgsql
security definer
-- Pinned search_path: a SECURITY DEFINER function without one can be hijacked by a
-- caller-controlled search_path.
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

-- ---------------------------------------------------------------------------
-- 3. Profile creation honours the selected role and organisation
-- ---------------------------------------------------------------------------

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

    -- Unknown, absent, or non-claimable role: fall back to the least privileged one.
    -- Never fall back upwards.
    if v_role.id is null or coalesce(v_role.self_assignable, false) = false then
        select id, wire_name, self_assignable
          into v_role
          from public.roles
         where wire_name = 'VOLUNTEER';
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

    return new;
end;
$fn$;

drop trigger if exists on_auth_user_created on auth.users;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- 4. Repair accounts created while the role was being discarded
-- ---------------------------------------------------------------------------
-- Everyone who signed up before this migration is a VOLUNTEER regardless of what they
-- chose. Their choice survives in auth.users.raw_user_meta_data only if the client was
-- already sending it, so this repairs what it can and leaves the rest alone.
--
-- profiles_prevent_escalation reverts any role_id or organisation_id change on UPDATE,
-- which is correct for user-initiated edits and wrong for this one-off repair.

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

-- ---------------------------------------------------------------------------
-- 5. Role helpers
-- ---------------------------------------------------------------------------
-- current_role() is called from inside policies. As a SECURITY INVOKER function it read
-- public.profiles under the caller's own RLS, which works only by coincidence of the
-- "read your own profile" policy; and without a pinned search_path it was resolvable
-- against a caller-controlled schema.

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
     where p.id = auth.uid();
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

create or replace function public.my_organisation_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $fn$
    select organisation_id from public.profiles where id = auth.uid();
$fn$;

-- ---------------------------------------------------------------------------
-- 6. Incidents: stamp the reporter's organisation and area
-- ---------------------------------------------------------------------------
-- The Android client has no organisation or area to send -- it knows the reporter, and
-- nothing else. Deriving both from the reporter's profile server-side is what gives an
-- incident the scope that the visibility policies below filter on.

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
        select p.organisation_id, p.area_id
          into v_org_id, v_area_id
          from public.profiles p
         where p.id = new.reporter_id;

        -- Assigned through coalesce rather than straight into NEW: a reporter with no
        -- profile row would otherwise blank an organisation the client did supply.
        new.organisation_id := coalesce(new.organisation_id, v_org_id);
        new.area_id := coalesce(new.area_id, v_area_id);
    end if;

    return new;
end;
$fn$;

drop trigger if exists incidents_stamp_reporter_context on public.incidents;

create trigger incidents_stamp_reporter_context
    before insert on public.incidents
    for each row execute function public.stamp_incident_reporter_context();

-- The Android client decodes every incident row into a type whose priority is
-- non-nullable, and one failed row aborts the whole refresh -- a single incident with a
-- null priority would empty the incident list for every user at once. The column has a
-- default but was nullable, so an explicit null could still get in.

update public.incidents set priority = 'MEDIUM' where priority is null;

alter table public.incidents
    alter column priority set default 'MEDIUM',
    alter column priority set not null;

-- The device computes priority offline and deterministically -- an SOS is CRITICAL before
-- any network is involved -- so the client now sends it. A queued offline write that
-- replays later must still not undo a triage decision made while the device was
-- disconnected, so once an incident has moved past REPORTED only command may change it.

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

drop trigger if exists incidents_preserve_triage on public.incidents;

create trigger incidents_preserve_triage
    before update on public.incidents
    for each row execute function public.preserve_incident_triage();

-- ---------------------------------------------------------------------------
-- 7. Incident visibility
-- ---------------------------------------------------------------------------
-- Re-declared rather than assumed: the migration that first created these lives in the
-- repository as an empty file, so a database provisioned from the migrations alone has
-- no reporter policy at all -- the volunteer's own incident would not read back.

drop policy if exists "Volunteers can read incidents they reported" on public.incidents;
create policy "Volunteers can read incidents they reported" on public.incidents
    for select to authenticated
    using ( (select auth.uid()) = reporter_id );

drop policy if exists "Volunteers can create incidents" on public.incidents;
create policy "Volunteers can create incidents" on public.incidents
    for insert to authenticated
    with check ( (select auth.uid()) = reporter_id );

-- The missing piece. A responder sees an incident when it is theirs to act on:
-- assigned to them, scoped to their organisation or area, or sitting unclaimed in the
-- open pool. Resolved and cancelled work drops out of the pool.
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

-- Supporting index for the organisation predicate above; area, status, assignee, and
-- reporter are already indexed by migration 20260829150000.
create index if not exists incidents_organisation_id_idx
    on public.incidents (organisation_id);
