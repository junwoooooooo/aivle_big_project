# Current to Target Mapping

- Status: TARGET_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1
- Introduced In Commit: 1549a8efa0aeb2ca400f4795c1c44b34868e4722
- Scope: Package, frontend, API, persistence, test and document transition
- Supersedes: Phase 0 audit output and legacy code audits
- Implementation Status: PARTIAL

## Stable and reusable platform

| Current unit | Target treatment | Direction |
|---|---|---|
| backend auth/user/common security | KEEP_STABLE_CORE | JWT/refresh/admin/owner/cross-owner 404 regression |
| backend project | KEEP_STABLE_CORE | Project remains owner-scoped root |
| backend file/object/reconciliation | REUSE_WITH_CHANGE | Spring-only Storage; Idea/report artifacts |
| backend job claim/retry/recovery | REUSE_WITH_CHANGE | policies inform TaskRun; AnalysisJob not expanded |
| backend aitask result/artifact | REUSE_WITH_CHANGE | TaskResult/TaskArtifact direction |
| frontend auth/projects/settings/admin base | REUSE_WITH_CHANGE | retain stable flows; remove legacy areas |
| audit/service policy | REUSE_WITH_CHANGE | generic policy and TaskRun operations |

## Workflow mapping

| Backend/package | Frontend/route | API | Entity/table direction | Test direction | Document direction | Target |
|---|---|---|---|---|---|---|
| document upload/parser | documents, plan/documents | projects documents | DocumentVersion/StoredFile reuse review | keep parser/storage + add Idea | replace DOCX-centered | IdeaSource FILE |
| document structure | structured-plan | structured-plans | Plan/Section/MissingField DELETE | replace after Idea/Concept tests | deleted legacy | normalization/Concept |
| analysis.legal | legal-review | legal-reviews | legal tables DELETE | LegalReviewRun tests | new legal docs | Korean Legal |
| analysis.feasibility | feasibility | feasibility-assessments | legacy/V8 tables DELETE | Quick/Detailed tests | new analysis docs | assessments |
| analysis.financial | financial | financial-analyses | financial_analyses DELETE | detailed finance aspect | new analysis docs | Detailed Analysis |
| persona catalog/recommendation | personas | persona APIs | V2/V9/V17 tables DELETE | PersonaCard tests | new Persona docs | Three-Layer cards |
| validation.panel | interview | panel-interviews | panel table DELETE | independent interview tests | new Persona docs | PersonaInterview |
| validation.market | market-response | market-responses | prediction table DELETE | remove | out of scope | none |
| marketing | validate/marketing | marketing-contents | V18/V20/V25/V2 DELETE later | workspace/binary/comparison | new Marketing docs | MarketingWorkspace |
| frontend report + V2 reports | report | no persisted report API | replace report entities | version/export tests | Final Report docs | FinalReportVersion |
| integration.ai adapters | none | Spring direct provider | none | replace adapter tests | AI boundary | AI Server only |
| AnalysisJob | polling/admin jobs | jobs/latest | analysis_jobs replace | carry claim policy + TaskRun | TaskRun direction | TaskRun family |
| `job` / `analysis_jobs` legacy runtime | no new route | legacy `/api/v1` remains | retained unchanged through P3 | full regression retained | removal remains P12 | coexist with new `taskrun` package |
| `taskrun` Target package | no UI in P3 | `/api/v2/projects/{projectId}/task-runs/{taskRunId}` GET/retry/cancel | V27 `task_runs`, `task_attempts`, `task_results` | domain/client/H2/PostgreSQL/concurrency direction | P2 contracts partially implemented | reusable P4-P10 execution foundation |

Stable /api/v1은 유지 가능하고 신규 Workflow는 /api/v2다. compatibility endpoint/redirect를 추가하지 않는다. 2026-08-04 승인 결정에 따라 과거 V1~V36 upgrade chain은 통합 `V1__baseline_schema.sql`로 대체되었다. 기존 DB upgrade는 지원하지 않으며 이후 스키마 변경은 새 V2부터 추가한다. 데이터는 이관하지 않는다.
