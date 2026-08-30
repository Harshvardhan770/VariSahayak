import { withSupabase } from 'npm:@supabase/server@^1'

/**
 * Bridges the Lost & Found records to the deployed Python OpenCV/DeepFace service.
 *
 * The Python service is a *computation* service, never the system of record. It keeps face
 * embeddings in its own MongoDB and has no connection to this database at all — so the
 * status it computes is written back HERE, by this function, under the caller's RLS:
 *
 *   Android app -> existing Lost & Found repository -> existing PostgreSQL schema
 *                                                   \-> this function -> Python CV service
 *                                                                     -> embedding stored in
 *                                                                        MongoDB, status and
 *                                                                        distances returned
 *                                                   <-/ face_match_status written back here
 *
 * ---------------------------------------------------------------------------------------
 * THE DEPLOYED CONTRACT (github.com/atharvrahate296/FaceMatch_VariSahayak, src/app.py)
 * ---------------------------------------------------------------------------------------
 * This function previously called `/enroll` and `/compare` with an `X-Service-Token` header.
 * Neither exists on the deployed service. Both were verified against the live host:
 *
 *   POST /enroll            -> 500 {"error":"Internal server error"}
 *   POST /compare           -> 500 {"error":"Internal server error"}
 *   X-Service-Token: <any>  -> 401 {"error":"Unauthorized"}
 *
 * The 500 rather than a 404 is why this never surfaced as a routing bug: Flask's catch-all
 * `@app.errorhandler(Exception)` converts the routing miss into a 500, this function's
 * `status >= 500` branch turned that into SERVICE_UNAVAILABLE, and every photograph came
 * back "temporarily unavailable" forever. What is actually deployed:
 *
 *   GET  /health              no auth
 *   POST /v1/face/register    X-API-Key   {record_id, images[], metadata?, photo_type?}
 *   POST /v1/face/match       X-API-Key   {image_base64, record_ids?, photo_type?}
 *   POST /v1/face/detect      X-API-Key   {image_base64}
 *
 * `record_id` on the far side IS the Lost & Found `client_id` on this side. That is the
 * whole join, and it is why nothing about the PostgreSQL schema had to change.
 *
 * `photo_type` carries LOST or FOUND. The service filters candidates by it
 * (`MongoFaceRepository.get_candidates`), which is how a search reaches only the opposite
 * side of the board — the job the removed `/compare` route used to do.
 *
 * ---------------------------------------------------------------------------------------
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

/**
 * Base address of the deployed service, no path and no trailing slash.
 *
 * `FACE_API_URL`/`FACE_API_KEY` are the names the deployed service uses for itself, so one
 * name means one thing across the whole system. The older `FACE_SERVICE_URL`/
 * `FACE_SERVICE_TOKEN` are still read so an existing deployment does not break the moment
 * this ships; set the new names and drop the old ones.
 */
const FACE_API_URL = (Deno.env.get('FACE_API_URL') ?? Deno.env.get('FACE_SERVICE_URL') ?? '')
  .trim()
  // A configured value of "http://host:8080/" must not produce "http://host:8080//v1/...".
  .replace(/\/+$/, '')

const FACE_API_KEY = (Deno.env.get('FACE_API_KEY') ?? Deno.env.get('FACE_SERVICE_TOKEN') ?? '')
  .trim()

/** Routes, built once from the configured base. Nothing below concatenates a URL by hand. */
const ROUTES = {
  register: '/v1/face/register',
  match: '/v1/face/match',
  detect: '/v1/face/detect',
} as const

/**
 * Per-call budgets.
 *
 * Cloud Run runs this service at `min-instances 0` with `concurrency 1`, so a cold start
 * pays for a container boot plus RetinaFace on CPU. The old 25s covered neither and marked
 * good photographs bad. Registration and matching each get a real budget; detection is only
 * ever a follow-up on an already-warm container and gets a short one.
 */
const REGISTER_TIMEOUT_MS = 45_000
const MATCH_TIMEOUT_MS = 45_000
const DETECT_TIMEOUT_MS = 15_000

/** Mirrors `FaceMatchStatus` in the Android domain model. The wire names are the contract. */
type Status =
  | 'NOT_APPLICABLE'
  | 'READY'
  | 'NO_FACE'
  | 'MULTIPLE_FACES'
  | 'INVALID_IMAGE'
  | 'SERVICE_UNAVAILABLE'

/** The single message a volunteer sees for anything that is the system's fault, not theirs. */
const UNAVAILABLE_MESSAGE =
  'Face matching is temporarily unavailable. The report was saved and will continue ' +
  'using other matching information.'

const INVALID_IMAGE_MESSAGE =
  'That photograph could not be read. The report was saved without it.'

interface FaceResult {
  status: Status
  message: string | null
  /** Cosine distance per Lost & Found client_id on the opposite side. Ranking only. */
  distances: Record<string, number>
  facesDetected: number
  candidateCount: number
}

/**
 * The one response shape this function ever emits.
 *
 * Always HTTP 200 for anything the volunteer could see, per rule 2. Only genuinely
 * malformed requests — which are a client bug, never a volunteer action — get a 4xx.
 */
const respond = (result: FaceResult) =>
  Response.json({
    ok: result.status === 'READY',
    status: result.status,
    message: result.message,
    face_available: result.status === 'READY',
    distances: result.distances,
    faces_detected: result.facesDetected,
    candidate_count: result.candidateCount,
  })

const failure = (status: Status, message: string | null = null): FaceResult => ({
  status,
  message,
  distances: {},
  facesDetected: 0,
  candidateCount: 0,
})

const unavailable = () => failure('SERVICE_UNAVAILABLE', UNAVAILABLE_MESSAGE)

export default {
  // auth: 'user' — only a signed-in volunteer may submit a photograph, and ctx.supabase is
  // already scoped to them, so RLS decides which reports they may touch.
  fetch: withSupabase({ auth: 'user' }, async (req: Request, ctx: any) => {
    if (req.method !== 'POST') {
      return Response.json({ ok: false, message: 'Unsupported request.' }, { status: 405 })
    }

    if (!FACE_API_URL || !FACE_API_KEY) {
      // Unconfigured is not an error the volunteer can act on. The product runs without
      // face matching; it simply matches on the other nine signals.
      console.warn('Face service is not configured; skipping enrolment')
      return respond(unavailable())
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
    // processing for a report they are actually allowed to see. It is also what keeps the
    // service's API key on the server instead of inside an APK.
    const { data: report, error: readError } = await ctx.supabase
      .from('lost_found_items')
      .select('client_id, kind, subject_type, status')
      .eq('client_id', reportId)
      .maybeSingle()

    if (readError || !report) {
      return Response.json({ ok: false, message: 'Report not found.' }, { status: 404 })
    }

    // An umbrella has no face. Answering locally saves a pointless upload and a cold start,
    // and NOT_APPLICABLE is exactly what the board should show for it.
    if (report.subject_type !== 'PERSON') {
      return respond({
        status: 'NOT_APPLICABLE',
        message: null,
        distances: {},
        facesDetected: 0,
        candidateCount: 0,
      })
    }

    const kind: string = report.kind === 'FOUND' ? 'FOUND' : 'LOST'
    const opposite = kind === 'LOST' ? 'FOUND' : 'LOST'

    // 'enrol'/'enroll' and 'compare' are the legacy action names; both still resolve so an
    // older client build keeps working. Everything new takes the default and gets both
    // halves in one upload — the deployed /v1/face/match requires an image, so a separate
    // compare call would mean sending the same photograph twice.
    const action = payload.action ?? 'enrol_and_match'
    const wantsEnrol = action !== 'compare'
    const wantsMatch = action !== 'enrol' && action !== 'enroll'

    if (!payload.image) {
      return Response.json({ ok: false, message: 'An image is required.' }, { status: 400 })
    }

    const image = payload.image

    let result: FaceResult = {
      status: 'READY',
      message: null,
      distances: {},
      facesDetected: 0,
      candidateCount: 0,
    }

    if (wantsEnrol) {
      result = await enrol(reportId, image, kind, report.subject_type)
      await recordStatus(ctx, reportId, result.status)

      // A photograph the service would not enrol cannot be searched with either — the same
      // detector rejected it. Stop here rather than pay for a second cold start to be told
      // the same thing.
      if (result.status !== 'READY') return respond(result)
    }

    if (wantsMatch) {
      const search = await match(image, opposite)

      if (wantsEnrol) {
        // Enrolment already decided the status and has already been recorded. A search
        // failure after a successful enrolment must not downgrade READY: the embedding is
        // stored, the report can contribute a face signal, and only this one ranking pass
        // came back empty.
        result = {
          ...result,
          distances: search.distances,
          facesDetected: search.facesDetected,
          candidateCount: search.candidateCount,
        }
      } else {
        result = search
        await recordStatus(ctx, reportId, result.status)
      }
    }

    return respond(result)
  }),
}

// --- upstream calls -------------------------------------------------------------------------

/**
 * Registers one photograph and turns the deployed service's answer into a status.
 *
 * `metadata` deliberately carries ids and enums only. It is stored in MongoDB and echoed
 * back inside match results, so a name or a description put here would leave the PostgreSQL
 * database that governs it and land somewhere RLS does not reach.
 */
async function enrol(
  reportId: string,
  image: string,
  kind: string,
  subjectType: string,
): Promise<FaceResult> {
  const response = await callFaceService(
    ROUTES.register,
    {
      record_id: reportId,
      images: [image],
      photo_type: kind,
      metadata: { report_client_id: reportId, kind, subject_type: subjectType },
    },
    REGISTER_TIMEOUT_MS,
  )

  if (!response) return unavailable()

  const { httpStatus, body } = response

  if (httpStatus === 200) {
    // `images_used` is how many photographs produced an embedding. Zero cannot reach here —
    // the service 400s instead — but a defensive read costs nothing and READY is a claim
    // the matching engine acts on.
    const used = typeof body?.images_used === 'number' ? body.images_used : 0
    if (used > 0) {
      return { status: 'READY', message: null, distances: {}, facesDetected: 1, candidateCount: 0 }
    }
    return failure('NO_FACE')
  }

  if (httpStatus === 413) {
    return failure('INVALID_IMAGE', INVALID_IMAGE_MESSAGE)
  }

  if (httpStatus === 400) {
    // The service collapses "no face" and "more than one face" into a single 400 —
    // `FaceValidationError("Registration image must contain exactly one detectable face")`.
    // The volunteer-facing advice for those two is opposite ("take a photo" vs "take a
    // photo of just this person"), so the difference is worth one cheap follow-up call to
    // the deployed diagnostic route rather than a guess.
    const rejected = typeof body?.images_rejected === 'number' ? body.images_rejected : 0
    if (rejected > 0) return await disambiguate(image)

    // Anything else at 400 is a request this function built wrong — a missing record_id, a
    // bad images array. That is a bug here, not a bad photograph, and it is logged as one.
    console.warn('Face service rejected the registration request')
    return unavailable()
  }

  if (httpStatus === 401) {
    // A wrong or missing FACE_API_KEY. Nothing the volunteer can do, and it must not be
    // written onto the report as though the photograph were unusable.
    console.error('Face service refused the API key; check FACE_API_KEY')
    return unavailable()
  }

  // 500 and 503 both land here, as does the 500 the service returns for an undecodable
  // image (decode_base64_image raises outside the route's try block).
  console.warn(`Face service returned ${httpStatus} from ${ROUTES.register}`)
  return unavailable()
}

/**
 * Searches the opposite side of the board for this photograph.
 *
 * Distances are a ranking signal against named counterparts, not identity. The caller folds
 * them into the multi-attribute score, where face is one weight among ten, and §7.32 still
 * requires a human to confirm every reunification.
 */
async function match(image: string, oppositeKind: string): Promise<FaceResult> {
  const response = await callFaceService(
    ROUTES.match,
    { image_base64: image, photo_type: oppositeKind },
    MATCH_TIMEOUT_MS,
  )

  if (!response) return unavailable()

  const { httpStatus, body } = response

  if (httpStatus !== 200) {
    if (httpStatus === 400) return await disambiguate(image)
    if (httpStatus === 413) return failure('INVALID_IMAGE', INVALID_IMAGE_MESSAGE)
    if (httpStatus === 401) {
      console.error('Face service refused the API key; check FACE_API_KEY')
      return unavailable()
    }
    console.warn(`Face service returned ${httpStatus} from ${ROUTES.match}`)
    return unavailable()
  }

  const facesDetected = typeof body?.total_faces_detected === 'number'
    ? body.total_faces_detected
    : 0

  if (facesDetected === 0) {
    return { ...failure('NO_FACE'), candidateCount: countOf(body) }
  }

  // Only `matched[]` carries a record_id. `unmatched[]` holds a best distance with no
  // identity attached, so there is nothing a caller could key it by — it is dropped rather
  // than invented into a shape it does not have.
  const distances: Record<string, number> = {}
  const matched = Array.isArray(body?.matched) ? body.matched : []

  for (const entry of matched) {
    const recordId = entry?.record_id
    const distance = entry?.distance
    if (typeof recordId === 'string' && recordId && typeof distance === 'number') {
      // Several faces in one frame can resolve to the same record. Keep the closest.
      const existing = distances[recordId]
      if (existing === undefined || distance < existing) distances[recordId] = distance
    }
  }

  return {
    status: 'READY',
    message: null,
    distances,
    facesDetected,
    candidateCount: countOf(body),
  }
}

const countOf = (body: any): number =>
  typeof body?.candidate_count === 'number' ? body.candidate_count : 0

/**
 * Tells NO_FACE and MULTIPLE_FACES apart after the service has collapsed them into one 400.
 *
 * Uses the deployed `/v1/face/detect` route, which returns bounding boxes and never an
 * embedding. Best-effort: if this call also fails the caller still gets a usable answer,
 * because "no face" is the more common case and the safer thing to tell a volunteer.
 */
async function disambiguate(image: string): Promise<FaceResult> {
  const response = await callFaceService(
    ROUTES.detect,
    { image_base64: image },
    DETECT_TIMEOUT_MS,
  )

  const detected = response?.httpStatus === 200 &&
      typeof response.body?.total_faces_detected === 'number'
    ? response.body.total_faces_detected
    : 0

  if (detected > 1) {
    return { ...failure('MULTIPLE_FACES'), facesDetected: detected }
  }

  return { ...failure('NO_FACE'), facesDetected: detected }
}

/**
 * The only place an HTTP request leaves this function.
 *
 * Returns the upstream status and parsed body, or null when the call did not complete at
 * all. Nothing from the far side is echoed verbatim by any caller: a Flask error page, a
 * DeepFace exception, a Mongo connection string, or an internal path must never reach a
 * volunteer's screen. `{"error": "..."}` bodies are read for *classification* only.
 */
async function callFaceService(
  path: string,
  body: unknown,
  timeoutMs: number,
): Promise<{ httpStatus: number; body: any } | null> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), timeoutMs)

  try {
    const response = await fetch(`${FACE_API_URL}${path}`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        // The deployed service reads this header and only this one. `X-Service-Token`,
        // which this function used to send, returns 401 against the live host.
        'X-API-Key': FACE_API_KEY,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    })

    // A non-JSON body is what an infrastructure error in front of the service looks like —
    // a proxy's HTML 502, an empty 204. Treated as "no body", never parsed into a status.
    let parsed: any = null
    try {
      parsed = await response.json()
    } catch {
      parsed = null
    }

    return { httpStatus: response.status, body: parsed }
  } catch (error) {
    // AbortError and network failures both land here.
    console.warn(`Face service call to ${path} did not complete:`, (error as Error).name)
    return null
  } finally {
    clearTimeout(timeout)
  }
}

/**
 * Writes the face outcome onto the Lost & Found report.
 *
 * This exists because the Python service keeps its storage in MongoDB and holds no
 * connection to this database — which is the safer arrangement, since a service that
 * accepts uploads from the internet has no way to reach pilgrim records at all. The cost is
 * that somebody has to carry the status across, and this is the only place that already has
 * both the report id and an RLS-scoped client.
 *
 * Deliberately never throws. A photograph that processed correctly must be reported as such
 * even if this bookkeeping write fails; the next enrolment attempt corrects it.
 */
async function recordStatus(ctx: any, reportId: string, status: Status): Promise<void> {
  // SERVICE_UNAVAILABLE is transient and is not recorded. Writing it would turn a retryable
  // outage into a permanent state on the report, and the next attempt would have no way to
  // tell it apart from a genuinely unusable photograph.
  if (status === 'SERVICE_UNAVAILABLE' || status === 'NOT_APPLICABLE') return

  try {
    const { error } = await ctx.supabase
      .from('lost_found_items')
      .update({ face_match_status: status, updated_at: new Date().toISOString() })
      .eq('client_id', reportId)

    if (error) console.warn('Could not record face status:', error.code)
  } catch (error) {
    console.warn('Could not record face status:', (error as Error).name)
  }
}
