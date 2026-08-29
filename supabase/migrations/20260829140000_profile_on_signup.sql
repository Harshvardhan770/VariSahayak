-- Fixes: signing up created an auth.users row but never a public.profiles row, with no
-- error, because nothing ever wrote to profiles. The client only called signUpWith(),
-- which populates auth.users.raw_user_meta_data and nothing else.
--
-- The profile is created by a database trigger rather than by a second client call, for
-- three reasons:
--
--   1. With email confirmation enabled there is no session immediately after sign-up, so
--      a client-side INSERT has no auth.uid() and RLS rejects it.
--   2. If the app is killed between the two calls, the account is left with no profile
--      and the user can never get past the splash screen.
--   3. The role must not be client-supplied. See below.

-- ---------------------------------------------------------------------------
-- 1. Profile creation trigger
-- ---------------------------------------------------------------------------

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
-- Pinned search_path: a SECURITY DEFINER function without one can be hijacked by a
-- caller-controlled search_path.
set search_path = public
as $$
declare
    v_role_id uuid;
    v_display_name text;
begin
    -- SECURITY: the role is forced to VOLUNTEER and is deliberately NOT read from
    -- new.raw_user_meta_data. That metadata is entirely client-supplied, so trusting it
    -- would let anyone self-register as ADMINISTRATOR simply by editing the sign-up
    -- payload. Elevated roles are granted only through an administrator flow.
    select id into v_role_id from public.roles where wire_name = 'VOLUNTEER';

    if v_role_id is null then
        raise exception 'roles table is not seeded: VOLUNTEER is missing';
    end if;

    v_display_name := coalesce(
        nullif(trim(new.raw_user_meta_data ->> 'display_name'), ''),
        split_part(new.email, '@', 1)
    );

    insert into public.profiles (id, display_name, role_id)
    values (new.id, v_display_name, v_role_id)
    on conflict (id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- 2. Backfill accounts created before this trigger existed
-- ---------------------------------------------------------------------------
-- Anyone who signed up while the bug was live has an auth user and no profile, which
-- leaves them stuck on the splash screen forever. Give them one.

insert into public.profiles (id, display_name, role_id)
select
    u.id,
    coalesce(
        nullif(trim(u.raw_user_meta_data ->> 'display_name'), ''),
        split_part(u.email, '@', 1)
    ),
    (select id from public.roles where wire_name = 'VOLUNTEER')
from auth.users u
left join public.profiles p on p.id = u.id
where p.id is null
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------
-- 3. Profile write policies
-- ---------------------------------------------------------------------------
-- Only a SELECT policy existed, so a profile could be read but never written. The trigger
-- runs as SECURITY DEFINER and bypasses RLS, but users still need to maintain their own
-- display name and phone number.

drop policy if exists "Users can update their own profile" on public.profiles;

create policy "Users can update their own profile" on public.profiles
    for update to authenticated
    using ( (select auth.uid()) = id )
    with check ( (select auth.uid()) = id );

-- A plain UPDATE policy would let a user set their own role_id and escalate to
-- administrator. Columns cannot be restricted in a policy, so the change is reverted here.
create or replace function public.prevent_profile_privilege_escalation()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
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
$$;

drop trigger if exists profiles_prevent_escalation on public.profiles;

create trigger profiles_prevent_escalation
    before update on public.profiles
    for each row execute function public.prevent_profile_privilege_escalation();

-- ---------------------------------------------------------------------------
-- 4. Index the RLS join column
-- ---------------------------------------------------------------------------
-- public.current_role() joins profiles to roles on every policy evaluation. Without this
-- index that join is a sequential scan on every authorised read.

create index if not exists profiles_role_id_idx on public.profiles using btree (role_id);
