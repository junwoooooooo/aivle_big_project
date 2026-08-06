# Phase 0 Repository Audit

- Status: CURRENT_BASELINE
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P1.1
- Introduced In Commit: 80ce95bbf53bcc5faeae894abc37c8a4cac02222
- Scope: Code-verified repository baseline used by re-foundation
- Supersedes: Phase 0 conversation-only audit report
- Implementation Status: IMPLEMENTED

## Repository baseline

- Branch: main
- HEAD: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Worktree: audit 시작과 종료 모두 clean
- Phase 0은 읽기 전용으로 수행됐으며 파일·migration 변경이 없었다.

## Runtime and inventory

| Area | Current inventory |
|---|---|
| Runtime | Spring Boot 4.1/Java 17 backend, React 19/Vite frontend, FastAPI AI Server |
| Backend | admin, aitask, analysis, audit, auth, common, config, document, file, integration, job, marketing, persona, project, report, simulation, user, validation |
| Frontend | admin, auth, documents, structured-plan, legal-review, feasibility, financial, personas, validation, marketing, report, projects, settings, landing |
| AI | FastAPI task/marketing routes, task/prompt/artifact/banner services, pytest |
| DB | Flyway V1–V26; V5/V10 Java migration, 나머지 SQL; PostgreSQL/H2 |
| Storage | ObjectStoragePort, local/S3-compatible adapters, presigned GET/PUT, integrity, reconciliation |
| Job | AnalysisJob claim token, retry/backoff, wake listener, recovery scheduler, 유형별 executor |
| API | Spring controllers가 실제 동작 원천; legacy OpenAPI는 일부 controller와 drift |
| Test | Backend unit/H2/PostgreSQL/MinIO, frontend Vitest, FastAPI pytest, PowerShell Docker smoke |
| Documents | P0 당시 current/admin/product/ADR/ERD/reference 등 87개 |
| CI | backend, PostgreSQL, frontend, Docker E2E, OpenAPI lint, gitleaks, Trivy, dependency review |

## Classification

| Classification | Items |
|---|---|
| KEEP_STABLE_CORE | auth/JWT/refresh, admin authorization, Project owner scope, Spring JPA/Flyway, Object Storage port, 공통 오류, audit |
| REUSE_WITH_CHANGE | file/version/parser, storage reconciliation, job claim/retry/recovery, Spring–AI task envelope, AI task result/artifact integrity, Admin 운영 기반, Compose/quality gates |
| REPLACE | StructuredPlan 중심 aggregate, 12개 section, AnalysisJob 중심 신규 확장, runtime report, legacy OpenAPI |
| DELETE | FILLED/WAIVED completion, legal/feasibility/financial legacy slices, fixed Persona, interview/market response/legacy marketing, entity-only V2 모델, legacy routes/pages/tests/docs |

## Legacy dependency map

Project → Document/DocumentVersion → DOCUMENT_PARSE AnalysisJob → StructuredPlan/12 Sections/MissingField → FILLED/WAIVED → confirm → LegalReview → Feasibility → Financial/Persona → Interview/Market Response → Marketing → browser runtime report.

| Legacy area | Upstream | Downstream and removal impact |
|---|---|---|
| StructuredPlan/12 sections | DocumentVersion, parser, AI adapter, job | structured-plan API/UI/entity/test; legal·feasibility·persona snapshots; V1/V3–V7/V26 |
| Legal | confirmed plan, source document, job | legal API/route/entities/tests; feasibility gate; V7/V8 |
| Feasibility | plan + legal, job | assessment API/route/entities/tests; financial/persona; V8/V9/V21 |
| Financial | feasibility source | CRUD/run API, frontend workspace, financial_analyses, V21/V22 |
| Fixed Persona | CSV/baseline catalog, V9/V17 policy | catalog/recommendation/selection/admin/UI/tests |
| Interview | Persona validation source | panel-interview API/UI/table/tests; marketing source; V19/V20 |
| Market Response | validation source | market-response API/UI/table/tests; marketing source; V19/V20 |
| Marketing | interview/market source, job/artifact | content/version API/UI/AI tests; V18/V20/V25 |
| Runtime Report | legacy read APIs | ReportPage/export/dashboard; V2 Report entities와 실제 연결 없음 |

## Spring direct provider adapters

app.ai.enabled=true일 때 Spring RestClient가 provider URL과 Bearer key를 직접 사용하는 adapter는 4개다.

1. OpenAiDocumentStructureAdapter
2. OpenAiLegalReviewAdapter
3. OpenAiFeasibilityAnalysisAdapter
4. OpenAiPersonaRecommendationAdapter

별도 Spring→FastAPI 경계는 AiTaskClient와 AiServerMarketingClient이며 당시 SYSTEM_SMOKE_TEST, SYSTEM_ARTIFACT_SMOKE_TEST, MARKETING_BANNER_GENERATION만 지원했다.

## Object Storage and Job findings

Spring은 local/S3-compatible adapter, bucket configuration, key validation, checksum, presigned URL, orphan reconciliation을 보유했다. FastAPI는 RDB에 접근하지 않았지만 presigned URL로 Storage를 HTTP GET/PUT하고 mock banner를 로컬 outputs에 기록했다. P1에서 두 경로 모두 Target 금지로 결정됐다.

AnalysisJob은 유용한 claim/retry/recovery 기반이지만 source_document/structured_plan/legal/feasibility FK와 JobType이 legacy workflow에 결합됐다. 따라서 기반 아이디어는 재사용하되 신규 중심 model로 확장하지 않는다.

## Document classification

디자인 원본만 reference로 유지하고 사람용 legacy 기획·설계·감사 문서는 삭제한다. CI/backend가 읽는 openapi.yaml과 frontend build가 읽는 guide/example DOCX는 consumer 대체 전까지 machine-consumed legacy input으로 유지한다. 별도 archive는 만들지 않는다.

## Risks

- StructuredPlan과 AnalysisJob FK 중심 결합으로 제거 순서가 복잡하다.
- 12-section 값이 DB, Java, prompt, OpenAPI, frontend에 중복된다.
- OpenAPI와 controller drift가 있다.
- Spring direct provider adapter가 Target AI boundary와 충돌한다.
- FastAPI presigned/로컬 output이 Target storage ownership과 충돌한다.
- runtime report와 V2 Report entity가 이름만 같고 연결되지 않는다.
- FastAPI pytest 전용 CI job이 없다.

## Open decisions carried forward

초기 FILE 형식, 대용량 Spring–AI 전송, Workflow state/gate, Concept 분석 입력, Persona 상세 축, Final Report export, TaskRun transaction, AI provider/library, 법령 MCP 연동 방식은 P2 또는 플랫폼 Phase로 전달했다.

## No-change evidence

Phase 0 종료 시 git status는 main...origin/main clean이었고 git diff --stat/name-only 출력이 없었다. 테스트는 실행하지 않았으며 통과로 기록하지 않았다.
