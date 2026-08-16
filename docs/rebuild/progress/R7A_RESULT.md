# R7A Result — Legacy Internal Cleanup and Final Baseline

## Outcome

R7A removed the dead Legacy Pipeline implementation, consolidated the active contracts around the
new pipeline, and created a clean database baseline. Backend `compileJava` and Python syntax/import
checks are Green. No commit or push was performed.

## Continuation worktree record

- Branch: `rebuild/new-pipeline-v1`
- Starting HEAD: `aab3fe48a1abfe4b670e8c0294644e2758c72962`
- The continuation began with the 36 manifest paths already deleted and an uncommitted worktree.
- `git status --short`, `git diff --name-status`, and `git diff --stat` were captured before
  continuing; no reset, clean, revert, stash, branch switch, commit, or push was used.

## Manifest에 원래 있던 삭제

The 36 `DELETE_IN_R7` manifest paths were removed after checking new-pipeline imports. These include
legacy frontend features/routes, backend document/analysis/journey/persona/validation/report surfaces,
legacy marketing workspace code, AI legacy prompt folders, and directly-owned tests/fixtures.

## Manifest 밖에서 추가 확인된 Legacy 삭제

- backend legacy `job`, `aitask`, `simulation`, marketing execution, AI adapter packages
- unused AI server health/test/marketing clients and legacy generic TaskRun worker/contract
- legacy FastAPI marketing/task routers and their services/models/tests
- unused frontend route helper, ProjectStage model, admin persona service/UI/CSS
- old Flyway V1–V13 migration chain after the replacement baseline was complete

## 신규 Pipeline 공통 자산이라 보존한 항목

- `backend/pipeline/**`, TaskRun/TaskAttempt/TaskResult, JobEvent
- `InternalAiExecutionClient` and TaskRun worker context/repositories
- `ai/app/providers/**`, `ai/app/tasks/**`, new marketing content code/prompts
- `ai/app/legal/**`; active new concept legal review remains under `app/tasks/concept_legal_review`
- `journey_provider.py` file retained as a compatibility re-export with legacy dispatch removed
- auth/user, project, file, audit and common exception foundations

## 관리자 화면에서 교체한 Legacy 의존

Admin project list/detail no longer reads ProjectStage, Journey progress, persona configuration,
legacy analysis completion, or legacy marketing state. It displays common Project data and the new
Pipeline Module Status/Module Run state. Admin persona setting endpoints and UI were removed.

## Database

`FINAL_ENTITY_TABLE_INVENTORY.md` documents maintained entity/table mappings before baseline work.
The single clean migration is `V1__new_pipeline_baseline.sql`; legacy tables are excluded and the old
migration chain is removed. Existing databases must be reset.

## Checks actually run

- `backend\\gradlew.bat compileJava --no-daemon`: BUILD SUCCESSFUL (final run 21s)
- `ai\\.venv\\Scripts\\python.exe -m compileall -q ai/app ai/main.py ai/tests`: passed
- FastAPI import and task allow-list import: passed; exactly five new task types
- `pytest ai/tests/test_internal_task_type_alignment.py -q`: 1 passed (one FastAPI deprecation warning)
- static legacy route/import searches and baseline table comparison: performed
- frontend removed-path import search: zero matches
- active Legacy surface/task-type search: zero matches
- JSON: 18 files parsed; YAML: Compose/application/OpenAPI parsed
- baseline: 37 `CREATE TABLE` statements, zero duplicate table names
- `git diff --check`: passed (line-ending conversion warnings only)

## Intentionally omitted

Full backend tests, full AI tests, postgresTest/Testcontainers, frontend baseline/build, Docker
Compose rebuild, browser manual test, and real provider smoke were not run.

## Remaining risks

The consolidated PostgreSQL DDL has not yet been applied to a clean database. Frontend production
bundling and end-to-end navigation are user gates. Preserved inactive legal utilities remain for
future approved reuse but are not exposed by the active execution router.

## Exact continuation point

Run every command in `verification/R7A_USER_VERIFICATION.md` against a clean database. Begin R7B
only when Flyway, backend/AI/frontend tests, Docker health, browser route checks, and legacy exposure
searches meet the documented success criteria.
