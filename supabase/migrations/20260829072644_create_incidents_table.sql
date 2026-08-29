-- Enable the PostGIS extension for geography and geometry types
CREATE EXTENSION IF NOT EXISTS postgis SCHEMA extensions;
-- 1. Create Enums for Status and Category
CREATE TYPE public.incident_status AS ENUM (
    'REPORTED', 'TRIAGED', 'ASSIGNED', 'ACCEPTED',
    'IN_PROGRESS', 'RESOLVED', 'PENDING_SYNC',
    'CANCELLED', 'REASSIGNMENT_REQUIRED', 'ESCALATED'
);

CREATE TYPE public.incident_category AS ENUM (
    'MEDICAL', 'WATER', 'LOST_PERSON', 'BLOCKED_ROAD',
    'SANITATION', 'CROWD_SURGE', 'OTHER'
);

-- 2. Core Infrastructure Tables
CREATE TABLE public.organisations (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    name text NOT NULL,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now()
);

CREATE TABLE public.areas (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    name text NOT NULL,
    organisation_id uuid REFERENCES public.organisations(id),
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now()
);

CREATE TABLE public.roles (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    wire_name text UNIQUE NOT NULL -- VOLUNTEER, ADMINISTRATOR, etc.
);

-- 3. Profile Table (Linked to Auth)
CREATE TABLE public.profiles (
    id uuid REFERENCES auth.users ON DELETE CASCADE PRIMARY KEY,
    display_name text NOT NULL,
    role_id uuid REFERENCES public.roles(id),
    organisation_id uuid REFERENCES public.organisations(id),
    area_id uuid REFERENCES public.areas(id),
    phone text,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now()
);

-- 4. Operational Tables
CREATE TABLE public.incidents (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    client_id text UNIQUE NOT NULL, -- For idempotent offline sync
    status public.incident_status DEFAULT 'REPORTED' NOT NULL,
    category public.incident_category NOT NULL,
    description text NOT NULL,
    reporter_id uuid REFERENCES public.profiles(id) NOT NULL,
    assignee_id uuid REFERENCES public.profiles(id),
    area_id uuid REFERENCES public.areas(id),
    priority text DEFAULT 'MEDIUM',
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now()
);

CREATE TABLE public.incident_events (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    incident_id uuid REFERENCES public.incidents(id) ON DELETE CASCADE,
    actor_id uuid REFERENCES public.profiles(id),
    event_type text NOT NULL, -- STATUS_CHANGE, ASSIGNMENT, etc.
    payload jsonb,
    created_at timestamptz DEFAULT now()
);

CREATE TABLE public.responders (
    user_id uuid REFERENCES public.profiles(id) PRIMARY KEY,
    availability text DEFAULT 'OFF_SHIFT' NOT NULL,
    last_known_location geography(POINT, 4326),
    active_assignment_count int DEFAULT 0,
    updated_at timestamptz DEFAULT now()
);

-- 5. Support Tables
CREATE TABLE public.qr_identifiers (
    token text PRIMARY KEY, -- Opaque token from physical tag
    mapped_to_type text NOT NULL, -- e.g., 'profile' or 'incident'
    mapped_to_id uuid NOT NULL,
    created_at timestamptz DEFAULT now()
);

CREATE TABLE public.communication_messages (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    channel_id uuid NOT NULL,
    sender_id uuid REFERENCES public.profiles(id),
    content text NOT NULL,
    created_at timestamptz DEFAULT now()
);

-- 6. Helper Functions for RLS
CREATE OR REPLACE FUNCTION public.current_role()
RETURNS text AS $$
    SELECT r.wire_name FROM public.profiles p
    JOIN public.roles r ON p.role_id = r.id
    WHERE p.id = auth.uid();
$$ LANGUAGE sql STABLE;

-- 7. Row Level Security (RLS)
-- Enable RLS on all tables
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.incidents ENABLE ROW LEVEL SECURITY;

-- Example Policies (Applying the §0.6 subselect pattern)
CREATE POLICY "Users can read their own profile" ON public.profiles
    FOR SELECT TO authenticated USING ( (SELECT auth.uid()) = id );

CREATE POLICY "Volunteers can read incidents they reported" ON public.incidents
    FOR SELECT TO authenticated USING ( (SELECT auth.uid()) = reporter_id );

CREATE POLICY "Volunteers can create incidents" ON public.incidents
    FOR INSERT TO authenticated WITH CHECK ( (SELECT auth.uid()) = reporter_id );

-- 8. Realtime Enablement
ALTER PUBLICATION supabase_realtime ADD TABLE public.incidents;
ALTER PUBLICATION supabase_realtime ADD TABLE public.responders;

-- Revoke Delete to prevent data loss on Realtime tables
REVOKE DELETE ON public.incidents FROM authenticated;

-- 9. Seed Roles
INSERT INTO public.roles (wire_name) VALUES
('VOLUNTEER'), ('MEDICAL_RESPONDER'), ('POLICE_RESPONDER'),
('NGO_RESPONDER'), ('ORGANISER'), ('ADMINISTRATOR');