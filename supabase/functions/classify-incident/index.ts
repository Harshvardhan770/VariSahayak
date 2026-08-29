import { withSupabase } from 'npm:@supabase/server@^1'

/**
 * Incident classification assistance.
 *
 * This function is an *enrichment* path, never a gate. Its contract with the rest of the
 * product is that every failure mode returns 200 with `{ available: false }`, so the
 * caller has exactly one thing to handle: a suggestion arrived, or it did not. The Android
 * client has already written the incident to Room and enqueued it for sync before this is
 * ever called — the PRD requires the workflow to run identically when Gemini is down, and
 * the way that requirement is met is by making "down" indistinguishable from "no opinion".
 *
 * The Gemini key lives only here. It is read from the environment, never returned, never
 * logged, and never reaches the device.
 */

const CATEGORIES = [
  'MEDICAL',
  'WATER',
  'LOST_PERSON',
  'BLOCKED_ROAD',
  'SANITATION',
  'CROWD_SURGE',
  'OTHER',
] as const

type Category = (typeof CATEGORIES)[number]

/**
 * Model ID from an environment variable with the contract's value as the default.
 *
 * Google retires model IDs on a schedule. Reading it from config means a retirement is a
 * `supabase secrets set` away, not a source change and redeploy.
 */
const MODEL = Deno.env.get('GEMINI_MODEL') ?? 'gemini-3.5-flash-lite'

const GEMINI_ENDPOINT =
  `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`

/** Short, because this sits in a field app's request path on a bad connection. */
const TIMEOUT_MS = 6000

const SYSTEM_INSTRUCTION = [
  'You classify short incident reports from volunteers assisting pilgrims on a walking',
  'pilgrimage route. Return only a category and a severity.',
  '',
  'You do not make dispatch decisions. You do not decide who responds, how urgent the',
  'response is operationally, or what anyone should do. A separate deterministic rule',
  'engine owns those decisions and will override you.',
  '',
  'severity is 1 (minor, can wait) to 5 (life-threatening, immediate).',
  'Judge only what the text states. Do not infer identity, medical history, or intent.',
].join('\n')

/**
 * Structured output per contract §0.8: `responseFormat.text.{mimeType, schema}`.
 *
 * NOT `responseMimeType`/`responseSchema` — those were removed. Sampling parameters
 * (temperature, topP, topK) are deprecated as of 2026-07-21 and are deliberately absent.
 */
const RESPONSE_SCHEMA = {
  type: 'object',
  properties: {
    category: { type: 'string', enum: CATEGORIES },
    severity: { type: 'integer', minimum: 1, maximum: 5 },
    rationale: { type: 'string' },
  },
  required: ['category', 'severity'],
}

type Classification = {
  category: Category
  severity: number
  rationale?: string
}

/** The single "no opinion" answer. Every failure path returns this. */
const noSuggestion = (reason: string) =>
  Response.json({ available: false, reason })

/**
 * Validates the model's output.
 *
 * The Gemini docs are explicit that output is syntactically valid JSON but that
 * *"applications must validate semantic accuracy independently"*. A category outside the
 * PRD's seven or a severity outside 1-5 is discarded rather than coerced: a wrong value
 * that looks plausible is more dangerous downstream than no value at all.
 */
function validate(raw: unknown): Classification | null {
  if (typeof raw !== 'object' || raw === null) return null

  const candidate = raw as Record<string, unknown>

  const category = candidate.category
  if (typeof category !== 'string') return null
  if (!CATEGORIES.includes(category as Category)) return null

  const severity = candidate.severity
  if (typeof severity !== 'number' || !Number.isInteger(severity)) return null
  if (severity < 1 || severity > 5) return null

  const rationale = typeof candidate.rationale === 'string'
    // Truncated: this is persisted to incident_events and read on a phone.
    ? candidate.rationale.slice(0, 500)
    : undefined

  return { category: category as Category, severity, rationale }
}

/**
 * Maps the error taxonomy from contract §0.8.
 *
 * The two 429s mean different things and must be treated differently: a rate limit clears
 * in seconds and is worth one retry, while an exhausted quota does not clear until the
 * daily reset and retrying it just burns the request budget faster.
 */
function shouldRetry(status: number, body: string): boolean {
  if (status === 503) return true
  if (status === 429) return !body.toLowerCase().includes('quota')
  return false
}

async function callGemini(
  apiKey: string,
  description: string,
  category: string | null,
): Promise<Classification | null> {
  const prompt = [
    `Report: ${description}`,
    category ? `Volunteer selected category: ${category}` : null,
  ].filter(Boolean).join('\n')

  const body = {
    system_instruction: { parts: [{ text: SYSTEM_INSTRUCTION }] },
    contents: [{ parts: [{ text: prompt }] }],
    generationConfig: {
      responseFormat: {
        text: {
          mimeType: 'application/json',
          schema: RESPONSE_SCHEMA,
        },
      },
    },
  }

  // One retry, then give up quietly. This is enrichment; a slow retry loop would hold a
  // volunteer's request open for no operational benefit.
  for (let attempt = 0; attempt < 2; attempt++) {
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS)

    try {
      const response = await fetch(GEMINI_ENDPOINT, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'x-goog-api-key': apiKey,
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      })

      if (!response.ok) {
        const text = await response.text()

        if (response.status === 404) {
          // The pinned model ID was retired. Loud, because every call will now fail and
          // the fix is a config change somebody has to make.
          console.error(
            `Gemini model "${MODEL}" not found (404). It has probably been retired. ` +
            `Set the GEMINI_MODEL secret to a current model ID.`,
          )
          return null
        }

        if (shouldRetry(response.status, text) && attempt === 0) {
          await new Promise((resolve) => setTimeout(resolve, 400))
          continue
        }

        // Status only. The body can echo request content, and this is a log.
        console.warn(`Gemini call failed with status ${response.status}`)
        return null
      }

      const payload = await response.json()
      const text = payload?.candidates?.[0]?.content?.parts?.[0]?.text
      if (typeof text !== 'string') return null

      return validate(JSON.parse(text))
    } catch (error) {
      // AbortError (deadline exceeded) and network failures both land here. Give up
      // quietly on the second attempt; the caller treats it as no suggestion.
      if (attempt === 1) {
        console.warn('Gemini call did not complete:', (error as Error).name)
        return null
      }
    } finally {
      clearTimeout(timeout)
    }
  }

  return null
}

export default {
  // auth: 'user' with the default verify_jwt = true. Only signed-in users may call this,
  // and ctx.supabase is already RLS-scoped to them — the Authorization header is never
  // parsed by hand. withSupabase also handles CORS; no manual headers here.
  fetch: withSupabase({ auth: 'user' }, async (req: Request, ctx: any) => {
    if (req.method !== 'POST') {
      return noSuggestion('method_not_allowed')
    }

    const apiKey = Deno.env.get('GEMINI_API_KEY')
    if (!apiKey) {
      // Not an error the caller can act on, and not a reason to fail an incident.
      console.warn('GEMINI_API_KEY is not set; AI enrichment disabled')
      return noSuggestion('not_configured')
    }

    let payload: { description?: string; category?: string; incident_client_id?: string }
    try {
      payload = await req.json()
    } catch {
      return noSuggestion('invalid_body')
    }

    const description = payload.description?.trim()
    if (!description) {
      return noSuggestion('empty_description')
    }

    const suggestion = await callGemini(
      apiKey,
      // Bounded before it leaves the function: an unbounded field from a client should
      // never become an unbounded prompt.
      description.slice(0, 2000),
      payload.category ?? null,
    )

    if (!suggestion) {
      return noSuggestion('no_valid_suggestion')
    }

    /**
     * Recorded as its own audit row, separate from the deterministic result and the final
     * priority. Plan 09 §9.4 requires the three to be distinguishable afterwards: an
     * assignment that looks wrong in hindsight must be traceable to whether a rule or a
     * model produced it.
     *
     * Written with the caller's RLS-scoped client, and best-effort — a failed audit write
     * must not turn a successful classification into an error.
     */
    if (payload.incident_client_id) {
      const { error } = await ctx.supabase
        .from('incident_events')
        .insert({
          incident_client_id: payload.incident_client_id,
          actor_id: ctx.userClaims?.sub ?? null,
          type: 'AI_SUGGESTION_RECORDED',
          to_value: `${suggestion.category}:${suggestion.severity}`,
          note: suggestion.rationale ?? null,
        })

      if (error) console.warn('Could not record AI suggestion:', error.message)
    }

    return Response.json({
      available: true,
      category: suggestion.category,
      severity: suggestion.severity,
      rationale: suggestion.rationale ?? null,
    })
  }),
}
