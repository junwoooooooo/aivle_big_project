# IDEA-BRIEF-ACTIONABLE-NEEDS-INPUT-FIX Result

Date: 2026-08-07

## Outcome

Idea Brief `NEEDS_INPUT` is now actionable. The UI distinguishes unanswered questions, manually completable missing required fields, and invalid recovery. A zero-question state never calls the Answers API. Manual completion patches canonical fields and queues `FINAL_SYNTHESIS`; recovery creates a new idempotent derivation TaskRun.

## Files changed

- Backend Idea controller/service and Idea integration tests.
- AI Idea input metadata, prompts, deterministic clarification filtering, provider smoke, and schema tests.
- Frontend Idea API, state model, hook, page, `MissingRequiredFieldsForm`, and targeted tests.
- Master Plan, Product Spec, this result, and the matching user-verification document.

## Contracts implemented

- Backend `IdeaBriefFieldCatalog` is serialized into AI input as typed required/regulatory-sensitive metadata.
- Clarification removes optional questions while required fields remain missing and orders regulatory-sensitive required questions first.
- Final synthesis remains question-free, permits only `AI_PROPOSED` inference, and leaves uncertain required facts in `missingFieldKeys`.
- `NEEDS_INPUT` maps to `NEEDS_QUESTIONS`, `NEEDS_FIELDS`, or `RECOVERY` from the Backend response.
- Missing fields use PATCH `/fields`; changed values queue final synthesis and return a new active job.
- Empty questions are guarded before the Answers API. Backend `@NotEmpty` validation remains unchanged.
- POST `/reanalyze` creates an idempotent `FINAL_SYNTHESIS` TaskRun for mutable failed or invalid states.
- Failure UI distinguishes derivation reanalysis from interaction-state refresh.

## Checks actually run

- Backend `compileJava` and Idea targeted tests — passed (`BUILD SUCCESSFUL`).
- AI `test_idea_brief_schema.py` — 5 passed.
- Frontend Idea Intake Vitest — 5 files, 11 tests passed.
- Targeted Frontend ESLint — passed.
- `git diff --check` — passed (line-ending warnings only).

## Checks intentionally omitted

- Full backend tests and full frontend build.
- Docker rebuild, browser E2E, and live provider smoke.

## Remaining risks

- Real provider behavior must still prove that inferable required fields are proposed conservatively and uncertain facts remain manual inputs.
- The Browser verification below remains the acceptance gate for preventing an empty Answers request.

## Exact continuation point

Run `docs/rebuild/verification/IDEA-BRIEF-ACTIONABLE-NEEDS-INPUT-FIX_USER_VERIFICATION.md`. Do not continue to another feature unit until both the direct-Review and manual-missing-field browser paths pass without an empty Answers request.
