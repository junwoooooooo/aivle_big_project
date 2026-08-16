# V26 Marketing Execution Alignment Result

## Status

IMPLEMENTED. READY FOR V27 — MARKETING TEST ALIGNMENT.

Start SHA: `7697819e2836d4ce7669cc94b335487e8b97a598`  
Donor SHA (read-only): `4ee74359a1b231359dc3131fb8eecb126462d2bf`

## Contracts implemented

- Reused `CurrentConceptSourceResolver` as the only current-concept authority.
- Marketing source snapshot `2.1` binds the exact Market Seed, Selection ID/revision, and BM plan revision and carries bounded BM semantics.
- V38 adds nullable exact lineage for source snapshots, manual attempt/history linkage for content generations, and durable `STALE`. Legacy rows without exact revision lineage remain historical.
- Existing `MarketingContent` remains the durable generation/version aggregate. `START`, `RETRY`, and `REGENERATE` use canonical TaskRun input and `createWithDisposition` idempotency.
- Each TaskRun has one execution attempt. Manual retry is FAILED-only, exact-source-only, and capped at three domain attempts.
- Regenerate creates a new content row and preserves prior successful revisions/results.
- Late completion is materialized for audit, then marked `STALE` if authority drifted. Late failure after drift is also `STALE`.
- Added `GET /current` and `POST /{contentId}/retry`; existing paths remain compatible.
- AI validates lineage/generation metadata, keeps the shared provider path, excludes operational generation metadata from persuasive prompt content, and forbids performance guarantees.
- Frontend uses “마케팅 실행”, visible AI-draft review guidance, human-readable states, historical stale result access, distinct retry/regenerate CTAs, and one current GET after ambiguous mutation without POST resend.
- No automatic marketing-test navigation and no concept/Seed/Selection/BM mutation were introduced.

## Files changed

- Backend Marketing source/content domain, service, completion, controller, repositories, worker, focused tests, and V38 migration.
- AI marketing input models, prompt boundary, service projection, and focused contract test.
- Marketing Content API/hooks/model/components/page and focused tests.
- Marketing source JSON schema and these V26 result/verification documents.
- Minimal compile alignment: Marketing `STALE` mapping in `ProjectModuleStatusService`; V25 `MarketInterviewContract` uses the supported Jackson object/array predicate instead of unavailable `isContainerNode()` with equivalent behavior.

## Checks actually run

- Backend focused Gradle command: PASS, 31 tests across 7 Marketing classes. The command was rerun only after compile/fixture failures; initial sandbox-only wrapper download denials did not execute tests.
- AI focused: `14 passed` in `tests/test_marketing_content_contract.py`; rerun once after a bounded-string schema fixture failure.
- Frontend focused: 2 files, `15 passed`.
- Selective ESLint: PASS after one `no-useless-assignment` correction.
- Final `git diff --check`: recorded in final handoff.

## Intentionally omitted

- Full Backend/AI/Frontend suites, production build, Docker, browser/E2E, performance tests, and real provider calls.
- V25 deferred tests were not executed: Gradle 0, pytest 0, Vitest 0; visual verification remains pending at the final integration gate.

## Remaining risks

- V38 requires normal environment migration execution at deployment time; it was statically and compile-tested only.
- Browser/mobile visual review is pending.
- V25 integration debt remains separate and unresolved by this stage.

## Continuation point

Proceed to V27 Marketing Test Alignment without changing V26 generation semantics.
