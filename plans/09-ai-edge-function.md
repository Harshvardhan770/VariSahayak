# Phase 9 — AI Classification via Edge Function

**Goal:** Gemini assists with incident classification and priority recommendation, server-side only, behind the deterministic rule engine — and the product works identically when Gemini is unavailable.

## Read first (mandatory)

1. [00-api-contract.md](00-api-contract.md) §0.7 (Edge Function shape, `withSupabase`, secrets), §0.8 (model ID, `responseFormat`, error taxonomy), §0.10, §0.11 items 6–7.
2. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "AI Architecture", "Prioritise", "Security".
3. [06-matching-realtime-notifications.md](06-matching-realtime-notifications.md) §6.1 — the rule engine this sits behind.

## Preconditions

Phases 1–8 complete. **The deterministic priority engine from Phase 6 is finished and fully tested.** If it is not, stop — AI must not be the first prioritisation mechanism to exist.

---

## Tasks

### 9.1 Edge Function

`supabase/functions/classify-incident/index.ts`, using the current recommended shape from contract §0.7:

```ts
import { withSupabase } from 'npm:@supabase/server@^1'

export default {
  fetch: withSupabase({ auth: 'user' }, async (req, ctx) => {
    const { userClaims, supabase } = ctx
    // ...
  }),
}
```

- `auth: 'user'` with `verify_jwt = true` (the default). Only authenticated users may call it.
- **Do not** use `Deno.serve` or `import { serve }` — both are explicitly "avoid" in current guidance.
- **Do not** hand-parse the `Authorization` header; use `ctx.supabase` and `ctx.userClaims`.
- `withSupabase` handles CORS — do not add manual CORS headers.

### 9.2 Gemini call

- Model: **`gemini-3.5-flash-lite`**. Read it from an environment variable with that value as the default, so a future retirement is a config change and not a redeploy of source.
- `GEMINI_API_KEY` via `supabase secrets set GEMINI_API_KEY=...`, read with `Deno.env.get`. **It never leaves the server.**
- Structured output via `generationConfig.responseFormat.text.{mimeType, schema}` — copy the request shape from contract §0.8 exactly. **Not** `responseMimeType`/`responseSchema`.
- **Do not** set `temperature`, `top_p`, or `top_k` — deprecated 2026-07-21.
- System instruction constrains the model to classification only: return a category from the PRD's seven, a severity 1–5, and a short rationale. It does not make dispatch decisions.
- Short server-side timeout. This call sits in a field-app request path.

### 9.3 Validate the response — the docs require it

The Gemini docs state output is syntactically valid JSON but *"applications must validate semantic accuracy independently."* Therefore:

- Parse and validate against the schema server-side.
- Reject any category outside the PRD's seven values and any severity outside 1–5.
- On any validation failure, return a "no suggestion" result. **Never** pass an unvalidated model output into the priority pipeline.

### 9.4 Safety / rule layer

Server-side, after validation and before anything is written:

- An AI suggestion may **raise** a priority score.
- An AI suggestion may **never lower** a deterministically-critical incident, and may never reclassify an explicit SOS out of the critical band.
- The deterministic rules from Phase 6 run regardless of whether the AI call succeeded.
- Persist the AI suggestion, the deterministic result, and the final priority separately in `incident_events`, so any decision can be audited afterwards.

### 9.5 Failure handling

Map the contract §0.8 error taxonomy:

| Condition | Behaviour |
|---|---|
| `rate_limit_exceeded` (429) | retry with exponential backoff, then give up quietly |
| `quota_exceeded` (429) | **do not retry**; disable AI enrichment until reset; alert |
| `service_unavailable` (503) | retry with backoff, then give up quietly |
| `deadline_exceeded` (504) | give up quietly |
| `model_not_found` (404) | the model ID was retired — alert loudly, fall back |
| schema validation failure | treat as no suggestion |

In **every** one of these cases: the incident is created, prioritised deterministically, matched, and notified. The PRD is unambiguous — *"If Gemini is unavailable, the incident workflow must continue using deterministic rules."*

AI enrichment is therefore **asynchronous and non-blocking**: the Android client creates the incident (Phase 4 offline path) and enrichment happens after, updating the record if it has anything to add. The client never waits on Gemini to save an incident.

### 9.6 Android side

- Call through `supabase.functions.invoke("classify-incident", ...)` (contract §0.5).
- Optional suggestion in the reporting UI, clearly labelled as a suggestion, always overridable by the volunteer.
- A failed or slow call is invisible to the user — it never surfaces as a reporting error.
- **No Gemini key, no model name secrets, no direct `generativelanguage.googleapis.com` call from the app.**

---

## Verification checklist

- [ ] `supabase functions deploy classify-incident` succeeds.
- [ ] An authenticated call returns a valid, schema-conforming classification.
- [ ] An **unauthenticated** call is rejected (verify_jwt is doing its job).
- [ ] Set an invalid `GEMINI_API_KEY` → incident creation **still succeeds** end to end with deterministic priority.
- [ ] Block outbound access to Gemini → same result; workflow unaffected.
- [ ] Force a malformed model response → validated away, treated as no suggestion, no bad data written.
- [ ] An explicit SOS incident classified by the model as low severity **still lands in the top priority band**.
- [ ] `incident_events` shows the AI suggestion, the deterministic result, and the final priority as distinct records.
- [ ] Airplane mode → report an incident → it saves locally and syncs later; AI never blocked it.
- [ ] `git grep -rn "GEMINI_API_KEY" -- app/` returns nothing.
- [ ] `git grep -rn "generativelanguage.googleapis.com" -- app/` returns nothing.
- [ ] `git grep -rnE "Deno\.serve|import \{ serve \}|responseMimeType|responseSchema|gemini-2\.0|gemini-1\.5|\"temperature\"|\"topP\"|\"topK\"" -- supabase/` returns nothing.
- [ ] Unit tests: the safety layer cannot lower a deterministic critical; validation rejects out-of-enum categories and out-of-range severities.

## Anti-pattern guards

- Do **not** put a Gemini key, or any server-only secret, in the Android app.
- Do **not** call Gemini directly from Android.
- Do **not** let AI availability gate incident creation, prioritisation, matching, or notification.
- Do **not** let an AI suggestion override a deterministic SOS or critical-medical rule.
- Do **not** write unvalidated model output into the database.
- Do **not** use `Deno.serve`, `import { serve }`, `responseMimeType`, `responseSchema`, or sampling parameters.
- Do **not** hardcode a retired model ID.

## Done when

Every AI failure mode leaves the product fully functional, and the "SOS classified as low severity still goes critical" test passes.
