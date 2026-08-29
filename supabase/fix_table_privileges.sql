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
