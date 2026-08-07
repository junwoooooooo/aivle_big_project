# IDEA-SYNTHESIS-AND-CONCEPT-FACTORY-RUNTIME-CORRECTION Result

Date: 2026-08-07

## Outcome

Idea Brief now queues `FINAL_SYNTHESIS` after the last clarification answer and after changed Review fields. Confirmation requires a current assessment hash. Legal provider output contracts no longer require duplicated citation coverage or parallel finding arrays. Candidate generation and Legal Review use separate attempt phases; technical legal failures preserve the Candidate, do not publish `rejected`, and expose safe retry diagnostics. Failed Concept Factory resume creates a new idempotent TaskRun and active job id.

## Files changed

- Idea backend: `IdeaBrief`, derivation mode, assessment hasher, readiness/service/commit/API models, baseline schema, worker error allow-list, and Idea integration tests.
- Legal/AI: legal source models/pipeline, Idea provider contract, Concept legal models/service, provider smoke input, capability endpoint, and targeted pytest fixtures.
- Concept backend: attempt/error/status domains, execution worker/service/controller/API, baseline schema, worker tests, and real-repository replacement/retry integration test.
- Frontend: Idea intake hook/page and Concept Factory API/hook/model/page/card plus targeted tests.
- Product contracts: `NEW_PIPELINE_MASTER_PLAN_v1.0.md`, `NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`.
- This result and the matching user-verification document.

## Contracts implemented

- `INITIAL`, `CLARIFICATION`, `FINAL_SYNTHESIS` Idea modes; final synthesis cannot return questions and does not increment clarification round.
- Max clarification round no longer implies AI readiness; frontend no longer jumps to Review at max round.
- Canonical assessment SHA-256 covers overview, sorted field key/value, decision state, provenance, and attachment ids. API exposes `assessmentCurrent`; stale assessment cannot confirm.
- Review field changes queue final synthesis and show `변경 내용을 다시 정리하고 있습니다.` until the new job completes.
- Screening provider returns only `screenings`; backend rejects unknown/duplicate ids and deterministically derives omitted ids as excluded. Routing, screening, and repair use strict structured schemas.
- Each legal material finding owns `{text, evidenceReferenceIndexes}`. Backend derives user-facing string arrays, finding coverage, and the top-level evidence union.
- `LEGAL_REVIEW` attempts are separate from Candidate attempts. Legal schema/source/provider failures transition to `REVIEW_RETRY_PENDING`, preserve Candidate JSON, and do not consume replacement rounds or emit rejected events.
- Actual legal `REJECTED` remains the replacement boundary. Candidate schema repair/replacement behavior remains bounded.
- Slot API separates `candidateCount`, `legalReviewAttemptCount`, `legalRedesignCount`, and `replacementCount`; safe root phase/code/retryability/candidate preservation are returned.
- Retry accepts an idempotency key, keeps the failed parent TaskRun, creates a new TaskRun, replaces `activeJobId`, preserves eligible slots, and resumes a preserved Candidate at legal review. `NEEDS_INPUT` and stale-source retry are rejected with the appropriate next action.
- Concept Factory capability is separate from liveness/readiness and checks provider config, internal token, legal registry/version, and MOLEG configuration without live provider calls.
- Persisted official evidence is reused from the shared legal context pack by later slots, preventing the same stored evidence set from being retrieved and screened for every slot.

## Checks actually run

- `backend\\gradlew compileJava` — passed.
- Combined Idea, Concept worker, and real-repository replacement/retry targeted backend tests — passed (`BUILD SUCCESSFUL`).
- `ConceptFactoryReplacementIntegrationTests` — 2 passed using real H2 repositories and the real transactional execution/retry services.
- AI targeted pytest: Idea schema, legal source contract, concept legal evidence, concept factory schema — 20 passed.
- Frontend Idea Intake and Concept Factory Vitest — 7 files, 11 tests passed.
- Targeted frontend ESLint — passed.

## Checks intentionally omitted

- Full backend regression and full `postgresTest`.
- Docker rebuild, browser E2E, and real provider/MOLEG smoke.
- Frontend production build and full repository lint.

These heavy gates are explicitly assigned to the user verification document and are mandatory before the next feature unit.

## Remaining risks

- Real OpenAI structured-schema acceptance and real MOLEG output were not exercised here. Provider smoke is a mandatory acceptance gate.
- Shared evidence reuse is implemented for evidence already persisted in the snapshot context pack. Candidate-specific delta retrieval is not yet persisted as a separately classified delta pack; a novel activity after shared evidence exists therefore needs special attention in runtime verification.
- Baseline V1 was updated because this rebuild repository uses a reset baseline. Existing non-reset databases require recreation according to the rebuild database reset contract.
- The lower-priority Data/API and Async standard still contain the older generic “schema failure → replacement” wording; the Master Plan and Product Spec now carry the corrected Candidate-vs-Legal distinction and govern this unit.

## Exact continuation point

Run every step in `docs/rebuild/verification/IDEA-SYNTHESIS-AND-CONCEPT-FACTORY-RUNTIME-CORRECTION_USER_VERIFICATION.md`. Do not start another feature unit until final synthesis, stale-confirm blocking, provider smoke, five-slot execution, Candidate preservation, new retry job id, and five simultaneous eligible reveals all pass.
