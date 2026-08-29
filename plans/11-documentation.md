# Phase 11 — Documentation Deliverables

**Goal:** produce the three Markdown files the PRD requires — and only those three.

## Read first (mandatory)

1. [../VARI_Sahayak_PRD.md](../VARI_Sahayak_PRD.md) — "Documentation Requirement" (the exact required contents of each file), "Environment and Secret Requirements".
2. [00-api-contract.md](00-api-contract.md) — every version number in `setup.md` must match it exactly.

## Preconditions

Phases 1–10 complete. Write the docs against what was **actually built**, not against what was planned.

---

## Tasks

### 11.1 The three files

The PRD states the project documentation must contain **exactly three** Markdown files at the project root:

```
Readme.md
Project Summary.md
setup.md
```

Two notes before you start:

- The repo currently has `README.md` (uppercase). Windows and macOS filesystems are case-insensitive, so `Readme.md` and `README.md` are the same file — rename it with `git mv -f README.md Readme.md` so git records the intended casing, rather than creating a second file.
- The `plans/` directory is process material, not project documentation. It sits outside the three-file rule. If the user prefers a clean root, `plans/` can be removed after execution — but do not delete it as part of this phase without asking.

### 11.2 Readme.md

Per the PRD, containing: product overview · confirmed technology stack · main features · core workflow · architecture overview · roles · security principles · quality standards · scope boundaries · success criteria.

Keep it the front door: what VARI Sahayak is, who it serves, and what it does — readable by someone who has never seen the project.

### 11.3 Project Summary.md

The comprehensive PRS. Per the PRD, containing: product definition · problem statement · product vision · product principles · user roles · functional requirements · incident model · incident state machine · responder matching · AI requirements · SOS Bridge · offline architecture · data architecture · realtime architecture · notification architecture · location requirements · UI information architecture · UI style specification · volunteer-specific requirements · non-functional requirements · edge cases · future scope · success metrics · acceptance criteria.

Two rules from the PRD's "Source Handling Rules" apply directly here:

- **Preserve** the problem statement, product vision, workflows, feature requirements, volunteer focus, SOS Bridge concept, and future scope from the source material.
- **Mark any requirement not supported by the source as a proposed implementation decision**, explicitly labelled as such rather than presented as source-derived. The state machine details, the matching score weights, and the priority bands are examples — flag them.

Future scope stays future scope: voice reporting, SMS/IVR, ambulance dispatch, predictive analytics, gamification, other-gathering expansion. Document them as out of MVP, and do not imply they were built.

### 11.4 setup.md

Per the PRD, containing: prerequisites · Android Studio setup · Kotlin DSL configuration · SDK requirements · dependencies · Supabase setup · database migrations · authentication · RLS · storage · realtime · Google Maps · Firebase/FCM · Gemini/Edge Functions · Room · WorkManager · location permissions · QR setup · offline documentation · localisation · project structure · build commands · testing commands · security checklist · git hygiene · development sequence · deployment principles · troubleshooting.

Requirements:

- **Every version number must match [00-api-contract.md](00-api-contract.md).** A setup guide that drifts from the build files is worse than none.
- Document required configuration **without exposing credentials**:
  ```properties
  SUPABASE_URL=...
  SUPABASE_ANON_KEY=...
  GOOGLE_MAPS_API_KEY=...
  ```
  Server-side only, never in the app: `SUPABASE_SERVICE_ROLE_KEY`, `GEMINI_API_KEY`.
- Call out the minSdk-23 constraints explicitly (Navigation pinned to 2.9.8, Room 2.x, core library desugaring required, instrumented tests need an API 26+ emulator) — the next developer will otherwise "helpfully" upgrade and break the build.
- Troubleshooting should cover the failures actually hit during Phases 1–10: the Realtime WebSocket engine error, `Serializer not found`, the `Initializing`-on-background auth bounce, release-build serialization failures, and Gradle/AGP built-in Kotlin surprises.

---

## Verification checklist

- [ ] Exactly three Markdown files at the project root: `Readme.md`, `Project Summary.md`, `setup.md`.
- [ ] `git ls-files "*.md" | grep -v "^plans/"` lists exactly those three.
- [ ] `git log --follow Readme.md` shows the rename from `README.md`, not a new file plus a deletion.
- [ ] Every section the PRD lists for each file is present.
- [ ] Every version in `setup.md` matches `plans/00-api-contract.md` and the actual `libs.versions.toml`.
- [ ] `grep -rE "eyJ|AIza|service_role|sk-" *.md` finds no real credential.
- [ ] A clean-machine walkthrough of `setup.md` gets to a running debug build without needing outside knowledge.
- [ ] Every build and test command in `setup.md` is copy-pasteable and actually runs.
- [ ] Proposed (non-source-derived) requirements in `Project Summary.md` are explicitly labelled.
- [ ] Future-scope features are documented as future scope, not as delivered.

## Anti-pattern guards

- Do **not** create a fourth root Markdown file.
- Do **not** put real credentials in any document.
- Do **not** let documented versions drift from `libs.versions.toml`.
- Do **not** present proposed decisions as source-derived requirements.
- Do **not** document features that were not built.
- Do **not** delete `plans/` without asking.

## Done when

The three files exist, are complete against the PRD's section lists, contain no credentials, and a clean-machine walkthrough of `setup.md` succeeds.
