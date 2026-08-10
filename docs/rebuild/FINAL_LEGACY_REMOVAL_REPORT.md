# Final Legacy Removal Report

## Manifest deletion

All 36 `DELETE_IN_R7` paths in `REPOSITORY_FILE_OPERATION_MANIFEST.csv` were compared with the
working tree and removed. They covered the old frontend Journey/features, backend legacy product
packages/controllers, legacy AI prompts, and their directly-owned tests.

## Evidence-based additional deletion

Compile errors and `rg` import evidence identified additional legacy-only execution layers:

- backend `job`, `aitask`, `simulation`, legacy marketing execution, document/legal/persona AI adapters
- legacy generic TaskRun worker and legal-source contract that dispatched removed task types
- AI legacy marketing/task routers, artifact/banner/task services, Journey task dispatcher and smoke/tests
- frontend obsolete route helper, admin persona UI/API/hook, and dead persona CSS
- admin cluster persona services and ProjectStage/ProjectArea presentation dependencies

## Preserved common assets

- all `backend/pipeline/**`
- TaskRun, TaskAttempt, TaskResult, JobEvent and worker context/repositories
- `InternalAiExecutionClient`
- `ai/app/providers/**` and `ai/app/tasks/**`
- new marketing content generator, prompts and renderer
- `ai/app/legal/**` official-source/evidence utilities
- `ai/app/services/journey_provider.py` as transport-only compatibility import
- auth/user, project, file, audit and common exceptions

## Admin replacement

Admin project list/detail remains available. Legacy Journey stage, ProjectArea, persona settings,
legacy analysis completion and marketing workspace status were removed. Detail now uses common
project metadata plus new Pipeline Module Status and Module Run identifiers/status.

## Exposure result

Legacy public frontend routes/controllers and the legacy FastAPI routers are absent. The active
FastAPI execution allow-list contains only the five new task types. No legacy feature was restored
to make compilation pass.

## Deferred proof

Full backend/AI/frontend tests, PostgreSQL/Testcontainers, Docker rebuild, browser testing, and
real provider smoke are not R7A execution evidence. They remain mandatory user verification gates.
