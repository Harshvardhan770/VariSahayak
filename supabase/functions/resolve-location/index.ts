import { withSupabase } from 'npm:@supabase/server@^1'

/**
 * Public QR location resolver.
 *
 * This is what a pilgrim's phone camera reaches. It is deliberately the only unauthenticated
 * surface in the system, and it returns exactly four things: where you are, who is nearby to
 * help, a WhatsApp channel, and enough route context for the journey view.
 *
 * `auth: 'none'` with `verify_jwt = false` — a person standing at a water point has no
 * account and must not need one to call for help. The safety of that decision rests entirely
 * on `resolve_public_location`, a SECURITY DEFINER function that projects only the columns
 * explicitly marked public. There is no table access here at all, so no policy mistake
 * elsewhere can widen what this returns.
 *
 * A public scan must never expose the volunteer dashboard, internal incident lists, private
 * Lost & Found data, operational maps, or private volunteer information.
 */

const MAX_DESCRIPTION = 1000

type SosPayload = {
  token?: string
  emergency_type?: string
  description?: string
  person_name?: string
  approximate_age?: number
  people_count?: number
  phone?: string
  latitude?: number
  longitude?: number
  accuracy_m?: number
}

const bad = (message: string, status = 400) =>
  Response.json({ ok: false, message }, { status })

export default {
  fetch: withSupabase({ auth: 'none' }, async (req: Request, ctx: any) => {
    const url = new URL(req.url)
    const action = url.searchParams.get('action') ?? 'resolve'

    // ---------------------------------------------------------------------------------
    // Resolve: the landing page for a scanned sign.
    // ---------------------------------------------------------------------------------
    if (req.method === 'GET' || action === 'resolve') {
      const token = url.searchParams.get('token')?.trim()
      if (!token) return bad('A location code is required.')

      const { data, error } = await ctx.supabase
        .rpc('resolve_public_location', { p_token: token })
        .maybeSingle()

      if (error) {
        // Status only. The body can echo internals and this response is public.
        console.error('resolve_public_location failed:', error.code)
        return Response.json(
          { ok: false, message: 'Could not look up this location. Please try again.' },
          { status: 503 },
        )
      }

      if (!data) {
        // Never guess a location. An unknown, disabled, or withdrawn sign says so.
        return Response.json(
          { ok: false, message: 'This code was not recognised.' },
          { status: 404 },
        )
      }

      // Best-effort audit of the public scan. Anonymous, so no identity is recorded — only
      // that this sign was scanned, which is what the audit trail is for.
      await ctx.supabaseAdmin
        ?.from('qr_scan_events')
        .insert({ qr_token: data.qr_token, source: 'PUBLIC_QR' })
        .then(undefined, () => {})

      return Response.json({ ok: true, location: data })
    }

    // ---------------------------------------------------------------------------------
    // Nearby: assistance points for the journey view.
    // ---------------------------------------------------------------------------------
    if (action === 'nearby') {
      const latitude = Number(url.searchParams.get('lat'))
      const longitude = Number(url.searchParams.get('lng'))

      if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        return bad('A valid position is required.')
      }

      const { data, error } = await ctx.supabase.rpc('public_nearby_points', {
        p_latitude: latitude,
        p_longitude: longitude,
        p_radius_metres: 5000,
      })

      if (error) {
        console.error('public_nearby_points failed:', error.code)
        return Response.json({ ok: true, points: [] })
      }

      return Response.json({ ok: true, points: data ?? [] })
    }

    // ---------------------------------------------------------------------------------
    // SOS: an emergency raised from the public page.
    // ---------------------------------------------------------------------------------
    if (req.method === 'POST' && action === 'sos') {
      let payload: SosPayload
      try {
        payload = await req.json()
      } catch {
        return bad('Could not read the request.')
      }

      const token = payload.token?.trim()
      if (!token) return bad('A location code is required.')

      const description = (payload.description ?? '').trim().slice(0, MAX_DESCRIPTION)
      if (!description && !payload.emergency_type) {
        return bad('Describe what help is needed.')
      }

      // The sign must exist and be active before anything is written. Without this check an
      // arbitrary string would create incidents anywhere.
      const { data: location } = await ctx.supabase
        .rpc('resolve_public_location', { p_token: token })
        .maybeSingle()

      if (!location) {
        return Response.json({ ok: false, message: 'This code was not recognised.' }, { status: 404 })
      }

      // Written with the admin client: a public reporter has no profile, and the incident
      // pipeline requires a reporter_id. The sign's own organisation owns the report.
      //
      // The QR's coordinate and the browser's coordinate are stored separately and
      // deliberately: the person is NEAR the sign, not standing on it, and conflating the
      // two would send a responder to the wrong side of a crowd.
      const clientId = `public-${token}-${crypto.randomUUID()}`

      const { error } = await ctx.supabaseAdmin.from('incidents').insert({
        client_id: clientId,
        category: mapCategory(payload.emergency_type),
        description: buildDescription(payload, location.location_name),
        // Browser GPS when granted, the sign's fixed coordinate otherwise.
        latitude: payload.latitude ?? location.latitude,
        longitude: payload.longitude ?? location.longitude,
        location_accuracy_m: payload.accuracy_m ?? null,
        location_is_approximate: payload.latitude == null,
        reporter_id: await publicReporterId(ctx),
        sos_bridge_token: token,
        // Enters the top priority band deterministically, through the same Phase 6 rules as
        // any other SOS. There is no parallel pipeline for public reports.
        is_sos: true,
        priority: 'CRITICAL',
        status: 'REPORTED',
      })

      if (error) {
        console.error('public SOS insert failed:', error.code)
        return Response.json(
          { ok: false, message: 'Could not send the alert. Please find a volunteer nearby.' },
          { status: 503 },
        )
      }

      return Response.json({
        ok: true,
        message: 'Help has been alerted. Stay where you are if it is safe to do so.',
        location_name: location.location_name,
      })
    }

    return bad('Unsupported request.', 405)
  }),
}

/**
 * Maps the public form's coarse choice onto the PRD's seven categories.
 *
 * Unknown values become OTHER rather than being rejected: a pilgrim in trouble must not be
 * blocked by a category mismatch, and the deterministic engine pins any SOS to critical
 * regardless of category.
 */
function mapCategory(value?: string): string {
  switch ((value ?? '').toUpperCase()) {
    case 'MEDICAL':
      return 'MEDICAL'
    case 'LOST_PERSON':
      return 'LOST_PERSON'
    case 'WATER':
      return 'WATER'
    case 'CROWD':
    case 'CROWD_SURGE':
      return 'CROWD_SURGE'
    case 'SANITATION':
      return 'SANITATION'
    case 'BLOCKED_ROAD':
      return 'BLOCKED_ROAD'
    default:
      return 'OTHER'
  }
}

function buildDescription(payload: SosPayload, locationName: string): string {
  const parts = [payload.description?.trim()].filter(Boolean)

  if (payload.people_count && payload.people_count > 1) {
    parts.push(`${payload.people_count} people need help`)
  }
  if (payload.person_name) parts.push(`Name given: ${payload.person_name}`)
  if (payload.approximate_age) parts.push(`Approx age ${payload.approximate_age}`)
  if (payload.phone) parts.push(`Contact: ${payload.phone}`)

  parts.push(`Reported from ${locationName} via public QR`)
  return parts.join('. ')
}

/**
 * The system account that owns public reports.
 *
 * A dedicated profile rather than a null reporter, because `incidents.reporter_id` is NOT
 * NULL and because attributing public reports to one visible account is what lets an
 * organiser filter them.
 */
async function publicReporterId(ctx: any): Promise<string | null> {
  const { data } = await ctx.supabaseAdmin
    .from('profiles')
    .select('id')
    .eq('display_name', 'Public QR Reporter')
    .maybeSingle()

  return data?.id ?? null
}
