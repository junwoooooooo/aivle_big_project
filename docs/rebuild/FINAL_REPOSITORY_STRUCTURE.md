# Final Repository Structure

## Runtime roots

| Path | Responsibility |
|---|---|
| `frontEnd/src/app` | New application shell, router, layouts, module status |
| `frontEnd/src/features` | New pipeline feature modules and shared project/admin UI |
| `backend/src/main/java/com/aivle/backend/pipeline` | New pipeline domain, API, application, worker code |
| `backend/src/main/java/com/aivle/backend/taskrun` | Common TaskRun/attempt/result and AI execution transport |
| `backend/src/main/java/com/aivle/backend/project` | Common project ownership and metadata |
| `backend/src/main/java/com/aivle/backend/file` | Common file metadata/storage boundary |
| `backend/src/main/java/com/aivle/backend/audit` | Common audit trail |
| `ai/app/tasks` | Five new-pipeline AI tasks |
| `ai/app/providers` | Common OpenAI-compatible structured-output transport |
| `ai/app/legal` | Preserved official-source/evidence utilities; not a legacy public router |
| `docs/rebuild` | Governing contracts, stage results, verification |

## Active frontend modules

The public project shell routes to overview, Idea Brief, Concept Factory, concept comparison,
market integration, business/persona external-module placeholder, marketing content, and settings.
Legacy Journey route aliases and feature imports were removed.

## Active backend modules

Foundation packages retain auth/user, project, file, audit, admin, common exceptions,
TaskRun and JobEvent. Product code is under `pipeline/{idea,concept,selection,integration,planning,marketing,module}`.

## Active AI modules

`POST /internal/v1/ai/executions` accepts only `IDEA_BRIEF_DERIVATION`, `CONCEPT_CANDIDATE`,
`CONCEPT_LEGAL_REVIEW`, `CONCEPT_REDESIGN`, and `MARKETING_CONTENT_GENERATION`.
The old marketing/task public routers and Journey dispatcher were removed. The retained
`services/journey_provider.py` is a compatibility re-export only; it contains no Journey dispatch.

## Persistence

The only runtime migration is `V1__new_pipeline_baseline.sql`. See
`FINAL_ENTITY_TABLE_INVENTORY.md` and `FINAL_DATABASE_BASELINE.md`.
