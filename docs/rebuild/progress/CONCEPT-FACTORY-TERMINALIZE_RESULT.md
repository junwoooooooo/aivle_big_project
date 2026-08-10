# CONCEPT-FACTORY-TERMINALIZE Result

Date: 2026-08-07

## Outcome

Concept Factory execution now has explicit worker outcomes (`COMPLETED`, `NEEDS_INPUT`, `FAILED`) and explicit slot outcomes. Every claimed parent TaskRun is terminalized through adoption, `NEEDS_INPUT`, or failure; bounded replacement exhaustion no longer returns silently.

## Files changed

- `.env.example`
- `compose.yaml`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryExecutionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptAttempt.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFactoryRun.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptSlot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorker.java`
- `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskAttempt.java`
- `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskAttemptState.java`
- `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskRun.java`
- `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskRunState.java`
- `backend/src/main/java/com/aivle/backend/taskrun/service/TaskRunService.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryStateMachineTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorkerTests.java`
- `backend/src/test/java/com/aivle/backend/taskrun/TaskRunDomainTests.java`
- `frontEnd/src/features/concept-factory/components/ConceptTimeline.jsx`
- `frontEnd/src/shared/async-events/jobEventMessages.js`
- `docs/rebuild/progress/CONCEPT-FACTORY-TERMINALIZE_RESULT.md`
- `docs/rebuild/verification/CONCEPT-FACTORY-TERMINALIZE_USER_VERIFICATION.md`

No migration was added or modified. TaskRun and TaskAttempt state columns are unconstrained `VARCHAR(20)` columns in baseline V1, so `NEEDS_INPUT` requires no database schema change.

## Contracts implemented

- Replaced the boolean slot aggregate with explicit worker and slot outcome models.
- Parent completion adopts the TaskResult and ends the TaskRun as `SUCCEEDED`.
- Added terminal, non-claimable `NEEDS_INPUT` states to TaskRun and TaskAttempt and a transactional `TaskRunService.needsInput` transition.
- Failure paths end both ConceptFactoryRun and parent TaskRun as `FAILED`; already eligible concepts and slots are not deleted or downgraded.
- A claimed attempt can fail at the start boundary before it reaches `RUNNING`, preventing a claimed parent from remaining active after a startup exception.
- Enforced one transient retry for the same slot call, one schema repair, one legal redesign per slot, two replacement rounds, and at most 15 inspected candidates.
- Replacement rounds are shared run rounds rather than incrementing once per slot; five slots can each participate in rounds 1 and 2 while the run counter remains bounded at 2.
- Replacement exhaustion records an attempt error, fails the slot, and propagates a failed worker outcome.
- Permanent provider failures fail immediately. Transient exhaustion and schema repair exhaustion move to bounded replacement. Redesign provider failures use the same classification boundary.
- Added safe, aligned Job Events for run start, legal-context start/completion, slot lifecycle, and all terminal outcomes. Event parameters contain only slot numbers/counts and technical codes.
- Removed unused `CONCEPT_*` environment settings from Compose and `.env.example`; `ConceptFactoryLimits` is the single executable source for 5 slots, 2 replacement rounds, and 15 inspected candidates.
- Frontend event messages and timeline labels now use the run/slot event keys and report five completed concepts.

## Checks actually run

- `backend\\gradlew.bat compileJava` — passed (`BUILD SUCCESSFUL`).
- Targeted backend tests — passed, 23 tests total:
  - `ConceptFactoryWorkerTests` — 12 passed.
  - `ConceptFactoryStateMachineTests` — 2 passed.
  - `ConceptFactoryLimitTests` — 1 passed.
  - `ConceptFactoryFiveSlotTests` — 1 passed.
  - `TaskRunDomainTests` — 7 passed.
- `ai\\.venv\\Scripts\\python.exe -m pytest tests/test_concept_factory_schema.py -q` — 2 passed.
- Targeted frontend ESLint for `ConceptTimeline.jsx` and `jobEventMessages.js` — passed.
- Static search confirmed no remaining `CONCEPT_TARGET_ELIGIBLE_COUNT`, `CONCEPT_MAX_REPLACEMENT_ROUNDS`, `CONCEPT_MAX_INSPECTED_CANDIDATES`, legacy `job.concept.batch.*`, or `validating_boundary` key in the active Concept UI/config paths.
- `git diff --check` — passed.

The first sandboxed Gradle invocation could not download the configured Gradle distribution because network access was denied. The approved targeted rerun downloaded the wrapper distribution and passed; this was an environment restriction, not a test failure.

## Checks intentionally omitted

- Full backend test suite and full `postgresTest`.
- Docker image build and Compose runtime startup.
- Real provider and official legal-source smoke tests.
- Full frontend lint/test baseline, production build, and browser E2E.

These are intentionally left to the user-run verification profile below.

## Remaining risks

- Real provider latency/error payloads and PostgreSQL transaction behavior were not exercised in this execution unit.
- `providerTransientRetryCount` remains in the compatibility response, but retry enforcement now correctly lives at the individual slot call boundary and attempt history is the authoritative diagnostic record.
- Runtime acceptance still needs to prove that a real five-slot run publishes details only after all five slots become eligible, and that injected terminal failures leave no TaskRun in `RUNNING`.

## Exact continuation point

Run `docs/rebuild/verification/CONCEPT-FACTORY-TERMINALIZE_USER_VERIFICATION.md` against the Compose stack. Confirm the normal five-slot terminal path and inspect TaskRun, slot, attempt, and Job Event rows. If runtime acceptance passes, stop this unit and continue only with the next explicitly assigned execution unit.
