# R2B Result — Idea Brief Domain, Persistence, and Command API

## Outcome

R2B is implemented on branch `rebuild/new-pipeline-v1` from starting HEAD `defa37cee8b90494313d24e6e25dc49bbba15b5a`.

The new pipeline now owns a canonical Idea Brief domain under `backend/.../pipeline/idea`. It persists versioned briefs, fields, question cards, answers, and stored-file links; exposes the five `/api/v3/projects/{projectId}/idea-brief` endpoints; queues an `IDEA_BRIEF_DERIVATION` TaskRun without executing AI; and treats a confirmed brief row as an immutable snapshot. Editing after confirmation forks a new Draft linked to the prior confirmed snapshot.

No new code imports or calls the legacy Journey, Conversation API, Conversational Workspace, or Legacy Document Workspace. R2A dirty frontend files were preserved without modification.

## Files changed

Updated:

- `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskType.java`

Created:

- `backend/src/main/java/com/aivle/backend/pipeline/idea/api/IdeaBriefApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/api/IdeaBriefController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefIdempotencyPolicy.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBrief.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefField.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaQuestion.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaAnswer.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaBriefStatus.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaDecisionState.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaFieldProvenance.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/domain/IdeaQuestionType.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/repository/IdeaBriefRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/repository/IdeaBriefFieldRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/repository/IdeaQuestionRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/idea/repository/IdeaAnswerRepository.java`
- `backend/src/main/resources/db/migration/V7__idea_brief_domain.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefFieldInvariantTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefSnapshotTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/idea/IdeaBriefControllerTests.java`
- `docs/rebuild/progress/R2B_RESULT.md`
- `docs/rebuild/verification/R2B_USER_VERIFICATION.md`

## Contracts implemented

- Persistence tables: `idea_briefs`, `idea_brief_fields`, `idea_questions`, `idea_answers`, plus the `idea_brief_attachments` link table to existing `stored_files`.
- Idea Brief statuses: `DRAFT`, `DERIVING`, `NEEDS_INPUT`, `READY_FOR_REVIEW`, `CONFIRMED`, `FAILED`, `STALE`.
- Decision states: `LOCKED`, `PREFERRED`, `OPEN`, `ASSUMPTION`.
- Provenance: `USER_CONFIRMED`, `SOURCE_EXTRACTED`, `AI_PROPOSED`, `MISSING`.
- Domain factories reject AI-authored `LOCKED` or `USER_CONFIRMED` fields. User edits may create locked/user-confirmed fields.
- Canonical endpoints:
  - `GET /api/v3/projects/{projectId}/idea-brief`
  - `POST /api/v3/projects/{projectId}/idea-brief/derive`
  - `PATCH /api/v3/projects/{projectId}/idea-brief/fields`
  - `POST /api/v3/projects/{projectId}/idea-brief/answers`
  - `POST /api/v3/projects/{projectId}/idea-brief/confirm`
- Every query and command resolves the project through the authenticated owner ID. Unknown or foreign projects do not expose a brief.
- Mutating endpoints require a normalized `Idempotency-Key`. Same-key/same-request replays return the current result; same-key/different-command or payload produces `IDEMPOTENCY_CONFLICT`.
- Derive creates a canonical-hash `IDEA_BRIEF_DERIVATION` TaskRun in `QUEUED` state and stores its ID as the brief's active task/job ID. No Worker, claim, provider call, or result adoption was added.
- Confirm requires `READY_FOR_REVIEW`, zero unanswered questions, and optional optimistic `expectedVersion` agreement.
- Confirm stores a deterministic SHA-256 snapshot hash, clears the active task, marks the row `CONFIRMED`, and points `confirmedSnapshotId` to the immutable row itself.
- A field patch or new derive after confirmation creates the next sequence Draft with `parentBriefId` and `confirmedSnapshotId` pointing to the confirmed version, then copies fields and attachment links. The confirmed row and fields are not updated.
- API responses contain `briefId`, `status`, fields, questions with answers, readiness, `activeJobId`, `confirmedSnapshotId`, and `updatedAt`.
- Attachments refer directly to existing `stored_files` IDs through a new-pipeline link table. No legacy document/conversation entity is referenced.

## Checks actually run

- Root `AGENTS.md`, branch, HEAD, dirty state, required R2B documents, Fast Profile, and relevant manifest paths were checked.
- Initial focused Gradle test invocation did not start because the wrapper distribution was absent and sandbox network access was denied.
- The single allowed retry downloaded the pinned Gradle distribution and ran:
  - `gradlew.bat test --tests "com.aivle.backend.pipeline.idea.IdeaBriefFieldInvariantTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefSnapshotTests" --tests "com.aivle.backend.pipeline.idea.IdeaBriefControllerTests"`
  - Result: `BUILD SUCCESSFUL`; the three selected tests passed.
- A post-change `gradlew.bat compileJava` check initially encountered the sandbox wrapper-cache/network boundary; its allowed escalated retry completed with `BUILD SUCCESSFUL`.
- Forbidden new-package import/reference search for Journey and Conversation: no findings.
- `git diff --check`: passed after the final documentation update.

## Checks intentionally omitted

- Full backend test suite and full `postgresTest`.
- Testcontainers and runtime PostgreSQL migration execution.
- Backend/AI integration and R2C Worker execution.
- Frontend tests, lint, baseline, and production build.
- Docker Compose rebuild/start.
- Provider smoke, browser/manual API test, commit, and push.

## Remaining risks

- V7 Flyway DDL and Spring Data repository queries compiled but were not exercised against PostgreSQL or a booted application context.
- The current stored-file model has no direct owner/project column. R2B persists only supplied stored-file IDs; an upload-token/project binding policy must be enforced when the frontend attachment API is connected rather than importing the Legacy Document Workspace.
- R2C must reconcile TaskRun terminal results into brief status, generated fields, and 2–4 questions while preserving the AI provenance/decision invariants.
- `updatedAt`, pessimistic locking, replay behavior, and self-referencing confirmed snapshot constraints still require runtime database/API verification.
- R2A remains local-only and was not wired to these endpoints in R2B by scope.

## Exact continuation point

Stop after R2B. Run `docs/rebuild/verification/R2B_USER_VERIFICATION.md` and resolve any persistence, ownership, idempotency, or snapshot failure. Only after acceptance may a separately authorized R2C start at the `IDEA_BRIEF_DERIVATION` TaskRun Worker/result-adoption boundary. Do not add R2C AI execution automatically.
