import { withSupabase } from 'npm:@supabase/server@^1'

/**
 * Bridges the Lost & Found records to the Python OpenCV/DeepFace service.
 *
 * The Python service is a *computation* service, never the system of record. It keeps face
 * embeddings in its own MongoDB and has no connection to this database at all — so the
 * status it computes is written back HERE, by this function, under the caller's RLS:
 *
 *   Android app -> existing Lost & Found repository -> existing PostgreSQL schema
 *                                                   \-> this function -> Python CV service
 *                                                                     -> embedding stored in
 *                                                                        MongoDB, status
 *                                                                        returned
 *                                                   <-/ face_match_status written back here
 *
 * Three rules govern everything here:
 *
 * 1. **The client uploads photographs, never embeddings.** A client-supplied vector would
 *    be trivially forged, and this function never accepts one.
 * 2. **Nothing about face processing may block a report.** Every failure path returns 200
 *    with a status the caller records and moves on from. The report has already been saved
 *    on its non-photo fields and stays fully matchable without a face signal.
 * 3. **The status write is best-effort too.** If face processing succeeded but recording
 *    the status did not, the volunteer is still told what happened to their photograph.
 *    A failed bookkeeping write is not worth turning a good result into an error.
 */

const FACE_SERVICE_URL = Deno.env.get('FACE_SERVICE_URL')
const FACE_SERVICE_TOKEN = Deno.env.get('FACE_SERVICE_TOKEN')

/** DeepFace on a cold container is slow; a short timeout would mark good photos bad. */
const TIMEOUT_MS = 25_000

type Status =
  | 'READY'
  | 'NO_FACE'
  | 'MULTIPLE_FACES'
  | 'INVALID_IMAGE'
  | 'SERVICE_UNAVAILABLE'

const unavailable = (message: string) =>
  Response.json({
    ok: false,
    status: 'SERVICE_UNAVAILABLE' satisfies Status,
    message,
  })

export default {
  // auth: 'user' — only a signed-in volunteer may submit a photograph, and ctx.supabase is
  // already scoped to them, so RLS decides which reports they may touch.
  fetch: withSupabase({ auth: 'user' }, async (req: Request, ctx: any) => {
    if (req.method !== 'POST') {
      return Response.json({ ok: false, message: 'Unsupported request.' }, { status: 405 })
    }

    if (!FACE_SERVICE_URL || !FACE_SERVICE_TOKEN) {
      // Unconfigured is not an error the volunteer can act on. The product runs without
      // face matching; it simply matches on the other nine signals.
      console.warn('Face service is not configured; skipping enrolment')
      return unavailable(
        'Face matching is temporarily unavailable. The report was saved and will continue ' +
          'using other matching information.',
      )
    }

    let payload: { report_client_id?: string; image?: string; action?: string }
    try {
      payload = await req.json()
    } catch {
      return Response.json({ ok: false, message: 'Could not read the request.' }, { status: 400 })
    }

    const reportId = payload.report_client_id?.trim()
    if (!reportId) {
      return Response.json(
        { ok: false, message: 'report_client_id is required.' },
        { status: 400 },
      )
    }

    // Authorisation, and the reason this function exists rather than the app calling Python
    // directly: the read runs under the caller's RLS, so a volunteer can only trigger
    // processing for a report they are actually allowed to see.
    const { data: report, error: readError } = await ctx.supabase
      .from('lost_found_items')
      .select('client_id, kind, subject_type, status')
      .eq('client_id', reportId)
      .maybeSingle()

    if (readError || !report) {
      return Response.json({ ok: false, message: 'Report not found.' }, { status: 404 })
    }

    const action = payload.action ?? 'enrol'

    if (action === 'enrol' || action === 'enroll') {
      if (!payload.image) {
        return Response.json({ ok: false, message: 'An image is required.' }, { status: 400 })
      }

      // kind and subject_type travel with the photograph because the face service stores
      // them alongside the embedding, and they are what lets /compare search the opposite
      // side of the board. Without them every profile would look like a LOST person.
      const response = await callFaceService('/enroll', {
        person_id: reportId,
        report_client_id: reportId,
        kind: report.kind,
        subject_type: report.subject_type,
        image: payload.image,
      })

      await recordStatus(ctx, reportId, response)
      return response
    }

    if (action === 'compare') {
      const response = await callFaceService('/compare', {
        person_id: reportId,
        report_client_id: reportId,
      })

      // Distances are a ranking signal against named counterparts, not identity. The
      // caller folds them into the multi-attribute score, where face is one weight of ten.
      return response
    }

    return Response.json({ ok: false, message: 'Unsupported action.' }, { status: 400 })
  }),
}

/**
 * Calls the Python service and normalises every failure into a status.
 *
 * Nothing from the far side is echoed verbatim: a Flask error page, a DeepFace exception,
 * or an internal path must never reach a volunteer's screen.
 */
async function callFaceService(path: string, body: unknown): Promise<Response> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS)

  try {
    const response = await fetch(`${FACE_SERVICE_URL}${path}`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-service-token': FACE_SERVICE_TOKEN!,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    })

    if (!response.ok && response.status >= 500) {
      console.warn(`Face service returned ${response.status}`)
      return unavailable(
        'Face matching is temporarily unavailable. The report was saved and will continue ' +
          'using other matching information.',
      )
    }

    const result = await response.json()

    // Pass through only the fields a client may see. Explicitly not `...result`: the
    // service must never be able to widen this response, and an embedding must never
    // reach a device or a public page.
    return Response.json({
      ok: result.ok ?? false,
      status: result.status ?? 'SERVICE_UNAVAILABLE',
      message: result.message ?? null,
      face_available: result.face_available ?? false,
      distances: result.distances ?? {},
      eligible: result.eligible ?? [],
      sample_count: result.sample_count ?? 0,
    })
  } catch (error) {
    // AbortError and network failures both land here.
    console.warn('Face service call did not complete:', (error as Error).name)
    return unavailable(
      'Face matching is temporarily unavailable. The report was saved and will continue ' +
        'using other matching information.',
    )
  } finally {
    clearTimeout(timeout)
  }
}

/**
 * Writes the face outcome onto the Lost & Found report.
 *
 * This exists because the Python service moved its storage to MongoDB and no longer holds
 * a connection to this database — which is the safer arrangement, since a service that
 * accepts uploads from the internet now has no way to reach pilgrim records at all. The
 * cost is that somebody has to carry the status across, and this is the only place that
 * already has both the report id and an RLS-scoped client.
 *
 * Deliberately never throws. A photograph that processed correctly must be reported as
 * such even if this bookkeeping write fails; the next enrolment attempt corrects it.
 */
async function recordStatus(ctx: any, reportId: string, response: Response): Promise<void> {
  try {
    const body = await response.clone().json()
    const status = body?.status

    const known = ['READY', 'NO_FACE', 'MULTIPLE_FACES', 'INVALID_IMAGE', 'SERVICE_UNAVAILABLE']
    if (typeof status !== 'string' || !known.includes(status)) return

    // SERVICE_UNAVAILABLE is transient and is not recorded. Writing it would turn a
    // retryable outage into a permanent state on the report, and the next attempt would
    // have no way to tell it apart from a genuinely unusable photograph.
    if (status === 'SERVICE_UNAVAILABLE') return

    const { error } = await ctx.supabase
      .from('lost_found_items')
      .update({ face_match_status: status, updated_at: new Date().toISOString() })
      .eq('client_id', reportId)

    if (error) console.warn('Could not record face status:', error.code)
  } catch (error) {
    console.warn('Could not record face status:', (error as Error).name)
  }
}
