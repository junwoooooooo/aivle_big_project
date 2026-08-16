# R3A Result — Five-Slot Concept Factory Domain, Persistence and Bounded State Machine

## Outcome

R3A is implemented as a new pipeline-only Concept Factory and Legal domain. A run is created only from an owner-scoped, confirmed Idea Brief snapshot and atomically receives the five canonical variation slots. AI generation, legal execution, TaskRun workers, and frontend workboard behavior remain intentionally deferred to R3B.

## Files changed

- `backend/src/main/java/com/aivle/backend/pipeline/concept/**`
  - run/slot/attempt/concept/rejection entities and enums
  - bounded run and slot state machines
  - iteration limit and completion policies
  - canonical/major-field duplicate hashing policy
  - repositories, application service, `/api/v3` controller, and public response models
- `backend/src/main/java/com/aivle/backend/pipeline/legal/**`
  - legal context, evidence, assessment, evidence-link entities and repositories
  - public and internal legal status contract
- `backend/src/main/resources/db/migration/V8__concept_factory_domain.sql`
  - the nine R3A tables, indexes, checks, snapshot constraints, and composite project-isolation foreign keys
- `backend/src/test/java/com/aivle/backend/pipeline/concept/**`
  - state transition, five-slot, iteration-cap, and SQL contract tests
- `docs/rebuild/progress/R3A_RESULT.md`
- `docs/rebuild/verification/R3A_USER_VERIFICATION.md`

## Contracts implemented

- Run states: `QUEUED`, `GENERATING`, `VALIDATING`, `REPLACING`, `NEEDS_INPUT`, `COMPLETED`, `FAILED`, `STALE`.
- Slot states and attempt phases exactly match the R3A instruction; no `PROVIDER_FAILURE` state was introduced. This boundary is ratified by [D-009](../decisions/DECISION_LOG.md#d-009-concept-provider-failure-상태-경계): provider failures belong to Concept Attempt error classification.
- Exactly five ordered slots are created with the five canonical `VariationFocus` values.
- Bounds are enforced in domain logic and database checks: one legal redesign per slot, two replacement rounds, fifteen inspected candidates, and one provider transient retry.
- Completion requires five eligible slots and five non-duplicate concepts from the same immutable Idea Brief snapshot. Only `IMPLEMENTABLE` and `IMPLEMENTABLE_WITH_CONTROLS` concepts can be published.
- Canonical hash and major-field hash both participate in duplicate rejection and receive per-run unique constraints.
- Public APIs were added for create/current/run/slots/retry/concepts. Before `COMPLETED`, `GET /concepts` returns no draft concept detail.
- Snapshot identity/hash and project isolation are protected with composite foreign keys and hash checks in V8.

## Checks actually run

- `backend\\gradlew.bat compileJava` — passed (`BUILD SUCCESSFUL`, 3s).
- `backend\\gradlew.bat test --tests "com.aivle.backend.pipeline.concept.*"` — passed (`BUILD SUCCESSFUL`, 5s).
- The first sandbox attempts for each Gradle command could not fetch Gradle 9.5.1 because network access was denied; the approved executions passed. These were environment bootstrap failures, not test failures.
- `git diff --check` — recorded after final edits in this stage.

## Checks intentionally omitted

- Full backend tests and full `postgresTest`.
- Testcontainers, Docker rebuild/startup, Flyway application against PostgreSQL, provider smoke, frontend baseline/build, and browser/manual API verification.
- AI task packages, legal provider calls, durable worker, TaskRun/JobEvent linkage, and R3 frontend are R3B scope.

## Remaining risks

- V8 has not yet been applied to a real PostgreSQL instance; composite foreign keys and Flyway startup require user verification.
- Exact five-row cardinality is enforced at run creation and completion in application/domain logic; SQL enforces slot-number/focus uniqueness and ranges but cannot alone require five child rows.
- R3A creates a durable queued run but no worker consumes it until R3B.
- Completion and publication policy is unit-level code in R3A and is not invoked by an execution worker until R3B.

## Exact continuation point

Run every gate in `docs/rebuild/verification/R3A_USER_VERIFICATION.md`. After they pass, begin R3B at the queued `ConceptFactoryRun`: add the new AI task packages and durable worker that creates attempts, legal context/evidence/assessments, applies the bounded policies, emits safe Job Events, and invokes the existing completion policy. Do not start R3B before R3A acceptance.
