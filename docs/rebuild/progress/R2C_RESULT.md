# R2C Result — Idea Brief AI Derivation, Durable Worker, and Frontend Async Integration

## Outcome

R2C is implemented on branch `rebuild/new-pipeline-v1` from starting HEAD `a611d8a7294ba44e95264fa67577e4c6596328c3`.

`IDEA_BRIEF_DERIVATION` is now an end-to-end internal task contract. The AI server validates a closed provider schema and deterministically maps it to a domain result; the backend claims queued TaskRuns using scalar WorkerContext, emits safe Job Events, commits generated fields/questions in a transaction, and converts failures to terminal states; the R2A frontend now uses the R2B APIs and common SSE-first/Polling-fallback event hook, including refresh recovery and terminal re-query.

No legacy conversation task, Conversation entity/API, Journey component, or raw provider JSON is used as the new pipeline source of truth.

## Files changed

Created:

- `ai/app/tasks/idea_brief/__init__.py`
- `ai/app/tasks/idea_brief/models.py`
- `ai/app/tasks/idea_brief/mapper.py`
- `ai/app/tasks/idea_brief/service.py`
- `ai/app/tools/idea_brief_provider_smoke.py`
- `ai/tests/test_idea_brief_schema.py`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefDerivationCommitService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/worker/IdeaBriefDerivationWorker.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefDerivationWorkerTests.java`
- `frontEnd/src/features/idea-intake/hooks/useIdeaIntake.test.jsx`
- `docs/rebuild/progress/R2C_RESULT.md`
- `docs/rebuild/verification/R2C_USER_VERIFICATION.md`
- `docs/rebuild/progress/R2_RESULT.md`
- `docs/rebuild/verification/R2_USER_VERIFICATION.md`

Updated:

- `ai/app/api/executions.py`
- `ai/tests/test_internal_task_type_alignment.py`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefField.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/repository/IdeaBriefRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/repository/IdeaQuestionRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/repository/IdeaAnswerRepository.java`
- `frontEnd/src/features/idea-intake/api/ideaBriefApi.js`
- `frontEnd/src/features/idea-intake/hooks/useIdeaIntake.js`
- `frontEnd/src/features/idea-intake/model/ideaIntakeModel.js`
- `frontEnd/src/features/idea-intake/pages/IdeaIntakePage.jsx`
- `frontEnd/src/shared/async-events/jobEventMessages.js`

## Contracts implemented

- Added `IDEA_BRIEF_DERIVATION` to the Internal AI execution registry without reusing `IDEA_CONVERSATION_TURN`.
- Added strict Pydantic input/provider/domain models with `extra="forbid"`, typed nested properties, bounded arrays/text, strict integer readiness score, and closed enums.
- Provider result includes `extractedFields`, `fieldSuggestions`, `clarificationQuestions`, `contradictions`, `readiness`, and `userFacingSummary`.
- Provider-to-domain mapping deterministically assigns only `SOURCE_EXTRACTED` or `AI_PROPOSED`; it cannot emit `USER_CONFIRMED` or `LOCKED`.
- Internal AI endpoint validates the Idea Brief input contract before provider execution and returns the mapped domain result, not provider raw JSON.
- Durable Worker flow: claim → start → information extraction → question/brief build → domain commit → TaskRun adoption → terminal event.
- Worker passes only immutable scalar `TaskRunWorkerContext`, claim IDs, and validated response data across transaction boundaries.
- Domain commit revalidates the closed result shape, respects user-confirmed/locked fields, replaces stale generated questions and answers safely, stores 0–4 questions, updates readiness, and adopts the TaskRun result atomically.
- Worker error boundary calls TaskRun failure, marks the active Brief failed, and emits `job.idea.failed`, preventing a stuck RUNNING Brief. Terminal event publication occurs after successful domain adoption and cannot roll back the confirmed success state.
- Safe events: `job.idea.queued`, `started`, `extracting`, `questions.preparing`, `brief.preparing`, `completed`, and `failed`; params are empty or bounded `questionCount` only.
- Recovery requeues expired Idea Brief TaskRuns through the existing lease recovery service and emits a new queued event.
- Frontend GET restores current Brief, questions, answers, active job, and screen state.
- Frontend derive/fields/answers/confirm commands use unique Idempotency-Key headers and the R2B API.
- Existing `useJobEvents` provides Last-Event-ID replay, sequence dedupe, SSE reconnect, bounded exponential retry, and 2-second Polling fallback.
- Terminal event triggers an Idea Brief GET; the query response determines Needs Input, Review, Confirmed, or Failed.
- Running UI displays the common Job Timeline; refresh with `activeJobId` reconnects automatically.
- Added user-run provider smoke entry point: `python -m app.tools.idea_brief_provider_smoke`.

## Checks actually run

- AI schema test initially did not start because system Python lacked pytest. The allowed retry used `ai/.venv` and passed: 2 tests.
- Frontend async hook Targeted Test passed: 1 file, 1 test.
- Backend Worker Targeted Test passed with `BUILD SUCCESSFUL`.
- Final backend `compileJava` passed with `BUILD SUCCESSFUL`; one existing-style deprecated API note was reported for Jackson property iteration.
- AI `compileall` for the new task, smoke tool, and execution endpoint passed.
- Targeted frontend ESLint initially found three effect/parser issues; after correction, its single allowed retry passed.
- `git diff --check` passed after final documentation.

## Checks intentionally omitted

- Actual provider smoke, Docker rebuild/start, browser test, PostgreSQL/Testcontainers, and cross-service E2E.
- Full backend, AI, and frontend suites.
- Frontend production build and baseline.
- Commit and push.

## Remaining risks

- Provider structured-output compatibility, model quality, timeout/rate-limit behavior, and live service authentication require the user-run smoke and Docker gate.
- V7 persistence plus Worker commit/recovery have not run against PostgreSQL.
- Frontend selected local files are not uploaded in R2C; only existing stored-file IDs are supported by the backend contract.
- The final small safety edits were compile/lint checked but the already-passed Targeted Tests were not rerun again under the Fast Mode rerun limit.
- Browser refresh, offline fallback, Timeline Korean rendering, and failure recovery remain manual gates.

## Exact continuation point

Stop after R2C. Run the integrated `docs/rebuild/verification/R2_USER_VERIFICATION.md`. R3 may begin only after every R2 gate passes. Do not implement concept generation or any R3 surface automatically.
