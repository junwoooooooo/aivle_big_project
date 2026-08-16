# Repository Baseline Audit

- 최초 조사일: 2026-08-04
- 보정일: 2026-08-04 (`정리 작업 1.1`)
- 조사 기준: `C:\Users\seewo\Desktop\big_proj_01\new_2` 작업 디렉터리의 실제 파일
- 성격: 읽기 전용 기준선 감사. 이 문서의 작성 외에는 코드·설정·문서·Migration을 변경하지 않았다.
- 분류 정의: `CURRENT_CANONICAL`(현재 제품 결정의 권위), `CURRENT_AS_BUILT`(실제 실행 상태), `UPDATE_REQUIRED`(현재 권위 또는 실제 상태에 맞춘 갱신 필요), `MACHINE_CONSUMED`(build/test/script/runtime 입력), `REFERENCE_ONLY`(디자인·원본 참고), `HISTORICAL_EVIDENCE`(과거 감사·검증 이력), `LEGACY_REFERENCED`(현재 코드·소비자가 실제 참조), `SUPERSEDED`(현재 권위로 완전히 대체됨), `REMOVAL_CANDIDATE`(미참조 입증), `UNKNOWN`(근거 부족).
- 주의: 목표 문서의 선언은 구현 증거로 사용하지 않았고, 기존 Flyway Migration은 수정·삭제 후보로 분류하지 않았다.

## 1. 조사 범위와 제한

요청된 핵심 문서, `compose.yaml`, 모든 env example, Spring 설정·Migration·Journey/TaskRun/common response, FastAPI execution/model/legal/service/test, React router/layout/journey/shared API/UI, build 정의, scripts를 읽었다. 최초 조사에서 숨김 경로 부재를 일반 `rg --files` 결과에 과도하게 의존한 부분은 아래 방법으로 보정했다. 참조 조사는 정적 import/call/path/file-read 검색이며, 동적 reflection, 외부 소비자, 배포 환경의 실제 값은 확인하지 않았다.

Git 명령, Docker, build, test, lint, formatter, network 호출을 실행하지 않았다. Secret 값은 수집하거나 기록하지 않았다. 정적 조사이므로 “실행 가능”은 코드 경로와 설정의 연결을 의미하며 실제 구동 성공을 보증하지 않는다.

## 조사 방법 보정

- 숨김 항목: 저장소 root를 `Get-ChildItem -Force`로 확인하고 `.github`는 `Get-ChildItem .github -Recurse -Force` 대상 여부를 별도로 판정했다. 참조 검색은 `rg --hidden --glob '!.git/**'`를 사용했다.
- Markdown: vendor dependency와 cache(`frontEnd/node_modules`, `.pytest_cache`)를 제외한 저장소 Markdown 86개를 모두 `Get-Content -Raw -Encoding utf8`로 읽었다. 이 중 `README.md`, `docs/README.md`, `docs/CURRENT_BASELINE.md`와 `docs/product`, `architecture`, `contracts`, `governance`, `quality`의 핵심 Markdown은 34개다.
- Migration: SQL 위치뿐 아니라 `backend/src/main/java/db/migration`을 함께 조사했다.
- Frontend API: `apiClient.request()`의 반환문과 `journeyApi`의 각 호출을 같은 코드 흐름으로 대조했다.
- 파일 상태: Git 명령은 금지되어 사용하지 않았으며, 보정 작업에서는 이 보고서만 수정했다.

## 기존 감사의 오류 정정

| 기존 판정 | 보정된 판정 | 근거 |
|---|---|---|
| `.github/workflows`가 없으므로 CI를 확인할 수 없음 | 현재 작업 트리에는 `.github` 자체가 없고 repository-local GitHub Actions는 **NOT_PRESENT/INACTIVE** | root `Get-ChildItem -Force`; `.github` 경로 부재. workflow·trigger branch·job은 각각 0개 |
| 주요 Markdown 로그의 한글 깨짐 | 파일은 정상 UTF-8이며 이전 깨짐은 shell 출력 디코딩 문제 | 저장소 Markdown 86개에서 U+FFFD와 대표 mojibake marker 0, 한글 문장 정상 해석 |
| V5/V10 Java Migration을 현재 경로에서 찾지 못함 | V5와 V10 Java Migration이 실제 존재 | `db.migration.V5__harden_document_integrity`, `V10__add_username_and_optional_profile`, 둘 다 `BaseJavaMigration` |
| `apiClient` double unwrap 위험 | 위험 취소. unwrap은 정확히 한 번 | `apiClient.request()`는 `return payload`; `journeyApi`가 반환 envelope의 `.data`를 한 번 추출 |
| 후속 MVP의 연결 여부가 사용자 결정 대기 | 현재 Journey에는 공식 미연결인 “보존된 기존 MVP 실험 화면”으로 확정 | 사용자 확정 결정; route/code는 유지 |
| Public v2 target 계약과 code 중 권위 미정 | 실제 Controller와 frontend client가 현재 As-Is 실행 권위 | 사용자 확정 결정; 대규모 API code 변경은 이번 정리 범위 밖 |

## 확정된 정리 결정

1. 현재 재설계 Journey 종료점은 **Idea → Legal → 적격 Concept 3개 표시**다.
2. Concept 분석·선택·Persona·Interview·Marketing·Report 코드는 보존하되 현재 Journey의 공식 단계가 아닌 **보존된 기존 MVP 실험 화면**으로 분류한다.
3. 현재 API의 As-Is 권위는 실제 Controller와 frontend client다. 오래된 Target API 계약에 맞추는 대규모 코드 변경은 하지 않는다.
4. 모든 AI 기능을 202/Polling으로 일괄 전환하지 않는다. Legal worker, Concept in-memory executor, service 동기 claim/execute를 서로 다른 현재 실행 방식으로 문서화한다.
5. `/api/v1`은 외부 소비 여부가 확인되지 않았으므로 유지한다.
6. V1~V36 Migration은 SQL/Java 구분 없이 모두 유지한다.
7. `docs/reference/design/**`는 `REFERENCE_ONLY`로 유지한다.
8. 삭제는 `HIGH` 증거의 미참조 파일만 후속 별도 작업에서 수행한다.

## 2. 현재 실제 시스템 구조

```text
Browser / React 19 + Vite
  ├─ /api/v1  ───────────────┐
  └─ /api/v2 Journey ────────┤
                              v
                        Spring Boot
                  (auth/owner scope/API/RDB)
                   ├─ PostgreSQL + Flyway V1..V36
                   ├─ local 또는 S3-compatible ObjectStoragePort ── MinIO(compose)
                   ├─ legacy AnalysisJob + Spring direct OpenAI adapters
                   └─ TaskRun/TaskAttempt/TaskResult
                              │ service Bearer + bounded JSON
                              v
                         FastAPI AI Server
                   ├─ provider HTTP 호출
                   ├─ 법제처 API + versioned legal assets
                   └─ legacy task/banner/output endpoints
```

핵심 근거는 `AppRouter`, `journeyApi`, `/api/v2` Controller들, `TaskRunService`/`TaskRunWorker`, `InternalAiExecutionClient`, FastAPI `/internal/v1/ai/executions`, `compose.yaml`, V27~V36이다. Browser가 AI Server를 직접 호출하는 현재 Router 경로는 없다. FastAPI는 Spring RDB에 직접 접근하지 않는다. 다만 legacy AI artifact/banner 기능은 local output 또는 presigned URL 계열을 보유한다.

## 3. 현재 실제 사용자 Journey

### 3.1 재설계 핵심 구간

| 단계 | 실제 구현 | 근거 Symbol/파일 | 판정 |
|---|---|---|---|
| Idea 입력(TEXT/FILE) | `/api/v2/projects/{id}/ideas`; Spring이 TEXT 저장 또는 DOCX parser 추출 | `JourneyController.saveText/saveFile`, `JourneyAiService.saveText/saveFile`, `JourneyPages` | 구현됨 |
| Idea 해석 | `IDEA_INTERPRETATION` TaskRun을 만들고 같은 HTTP 요청 안에서 claim/execute/adopt | `JourneyAiService.interpret/execute`, `InternalAiExecutionClient`, FastAPI `execute_journey_task` | 구현됨 |
| Idea Origin Draft/질문 | 해석 결과로 draft와 누락 질문 생성, 질문별 답변 저장 | `IdeaOriginService.createDraft/answer`, V32 | 구현됨 |
| Idea Origin 확정 | draft+확인 답변을 새 confirmed version으로 적용 | `IdeaOriginService.apply`, `JourneyController.applyIdeaOrigin` | 구현됨 |
| Legal Precheck | persistent TaskRun을 202로 생성하고 scheduler worker가 실행 | `LegalPrecheckService.start`, `LegalPrecheckWorkerScheduler`, `TaskRunWorker` | 구현됨 |
| Legal Guardrail | 성공 TaskResult를 동기화해 version/evidence/reasoning/guardrail 저장 | `LegalPrecheckService.synchronize/materialize/buildGuardrails`, V33 | 구현됨 |
| Concept 생성 | 별도 in-memory executor가 batch loop를 시작하고 `CONCEPT_GENERATION` TaskRun을 동기 claim/execute | `ConceptJourneyService.generate/runEligibility`, `conceptEligibilityExecutor` | 구현됨 |
| Origin Integrity | Spring의 deterministic 비교로 draft별 PASS/FAIL | `ConceptJourneyService.originIntegrity` | 구현됨 |
| Concept Legal Validation | Origin 통과 draft를 batch로 FastAPI `GUARDRAIL_BATCH` 검증 | `CONCEPT_LEGAL_VALIDATION`, `execute_concept_legal_validation_batch` | 구현됨 |
| 적격 Concept 3개 | 기본 3, 최대 대체 2 round/총 9개; 실패 draft 비노출 | `BatchView.concepts`, V34~V36, `ConceptGenerationPage` | 구현됨 |

### 3.2 이후 흐름

코드는 적격 3개 이후에도 Quick Assessment → Shortlist → Detailed Analysis(+Spring 재무 계산) → Concept Selection → Persona 3개 → 독립 Interview → Synthesis → Marketing 생성/비교 → persisted Final Report/사용자 Decision까지 구현한다. `AppRouter`에 모두 route가 있고 각 page는 다음 page로 링크한다. V29~V31과 `/api/v2` services가 저장한다.

그러나 `ProjectLayout.CURRENT_JOURNEY_STEPS`는 Idea/Legal/Concept만 current로, 나머지를 `LEGACY_MVP_STEPS`와 “현재 재설계와 미연결”로 표시한다. `ConceptGenerationPage`도 “후속 분석으로 자동 이동하지 않는다”고 명시한다. 즉 자동 전환은 없지만 직접 route 접근과 후속 page 내부 연결은 실제로 존재한다. “코드 미구현”이 아니라 제품 범위 표시·gate 정책이 모순된 상태다.

별도의 `/api/v1` DOCX/StructuredPlan/legal/feasibility/financial/persona/validation/marketing/report 계열도 backend에 남아 있으나 현재 `AppRouter`는 해당 옛 page를 mount하지 않고 대부분 새 Journey로 redirect한다.

## 4. 현재 Runtime 및 서비스 경계

| 경로/영역 | 현재 역할 | 실제 참조 근거 | 분류 | 권장 조치 | 위험도 |
|---|---|---|---|---|---|
| `frontEnd/src/main.jsx` → `app/App.jsx` → `AppRouter.jsx` | 현재 Browser 진입점 | 직접 import chain | CURRENT_AS_BUILT | 유지 | 낮음 |
| Spring `/api/v1` | stable core 및 legacy workflow API | frontend stable core, legacy tests/clients, 외부 소비 가능 | LEGACY_REFERENCED | v2 대체 완료 전 보존 | 높음 |
| Spring `/api/v2` Journey | 현재 Idea~Final Report UI의 API | `journeyApi.js`와 Controller exact 호출 | CURRENT_AS_BUILT | As-Is endpoint matrix 작성 | 높음 |
| `taskrun` | durable run/attempt/result source of truth | V27, Journey entity FK, worker/client | CURRENT_AS_BUILT | 유지·validator 강화 | 높음 |
| FastAPI internal execution | 한 TaskAttempt 동기 실행 | `InternalAiExecutionClient.execute` → `/internal/v1/ai/executions` | CURRENT_AS_BUILT | schema drift 수정 | 높음 |
| PostgreSQL | Spring-owned 업무/Runs/legacy 저장 | JPA/Flyway/compose | CURRENT_AS_BUILT | 기존 migration 불변 | 높음 |
| MinIO/Object Storage | compose의 S3-compatible storage; legacy artifact/document 경계 | `ObjectStoragePort`, S3 adapter, compose | CURRENT_AS_BUILT | 실제 Journey artifact 사용 범위 문서화 | 중간 |
| Spring direct provider adapters | legacy document/legal/feasibility/persona 실행 | interface Bean 주입, legacy JobExecutor | LEGACY_REFERENCED | 소비자 전환 후에만 제거 | 높음 |
| Browser direct AI | 확인되지 않음 | `journeyApi`는 Spring만 호출 | CURRENT_AS_BUILT | 금지 경계 유지 | 낮음 |

실제 호출 방향은 Browser → Spring → PostgreSQL/Object Storage 및 Spring → FastAPI → AI provider/법제처다. legacy 구간에서는 Spring → OpenAI provider 직접 호출과 Spring → FastAPI legacy endpoints도 공존한다.

## 5. 보정된 문서 분류표

분류는 한 파일의 **현재 주 역할**을 기준으로 하나를 부여했다. 정확한 내용 보정이 필요한 canonical 문서는 `UPDATE_REQUIRED`로 두었고, build/test가 실제 읽는 파일은 오래되어도 `MACHINE_CONSUMED`를 우선했다.

> 이 표는 정리 작업 1.1 당시의 분류를 보존한 조사 기록이다. 이후 변경된 현재 상태는 문서 하단의 `Baseline Cutover 결과`와 `정리 작업 C 결과`가 우선하며, 현재 redesign 권위는 `docs/redesign/AI_JOURNEY_REDESIGN_SPEC_v0.4.md` 하나다.

| 경로/영역 | 현재 역할 | 실제 참조 근거 | 분류 | 권장 조치 | 위험도 |
|---|---|---|---|---|---|
| `docs/maintenance/REPOSITORY_BASELINE_AUDIT_2026-08-04.md` | 보정된 현재 저장소 감사 | 정리 작업 1/1.1 결과 | CURRENT_AS_BUILT | 정리 작업 2 기준선으로 사용 | 중간 |
| `README.md`, `docs/README.md`, `docs/CURRENT_BASELINE.md` | 진입점/index/as-built 설명 | 현재 구현을 V26·legacy 중심으로 설명 | UPDATE_REQUIRED | V5/V10 포함 V36, 종료점, CI 부재 반영 | 매우 높음 |
| `docs/redesign/AI_JOURNEY_REDESIGN_SPEC_v0.3.md` | Idea→Legal→적격 3개 제품 결정 | V32~V36 및 확정 종료점과 일치 | CURRENT_CANONICAL | 현재 범위 권위로 유지 | 중간 |
| `docs/redesign/CODEX_EXECUTION_PLAN_v0.1.md` | 완료된 구현 작업 지시 | 이 감사 외 production/build/canonical 참조 0; 구현 결과 존재 | SUPERSEDED | 결정·결과를 최신 baseline/decision 문서로 옮긴 뒤 별도 제거 제안 | 낮음 |
| `docs/product/PRODUCT_VISION.md`, `NON_FUNCTIONAL_REQUIREMENTS.md`, `TERMINOLOGY.md` | 제품 원칙/비기능/용어 권위 | canonical index와 architecture/contracts 참조 | CURRENT_CANONICAL | 유지 | 중간 |
| `docs/product/PRODUCT_SCOPE.md`, `USER_JOURNEY.md`, `PROJECT_WORKFLOW.md`, `FUNCTIONAL_REQUIREMENTS.md`, `OPEN_DECISIONS.md` | target 범위/workflow | 확정 종료점·보존 MVP와 일부 충돌 | UPDATE_REQUIRED | current/target/실험 구분 | 높음 |
| `docs/architecture/SPRING_WAS_BOUNDARY.md`, `DATA_AND_STORAGE_ARCHITECTURE.md`, `SECURITY_ARCHITECTURE.md` | ownership/security 경계 권위 | 실제 Spring/RDB/storage/service-token 방향과 일치 | CURRENT_CANONICAL | 유지 | 높음 |
| `docs/architecture/SYSTEM_ARCHITECTURE.md`, `AI_SERVER_BOUNDARY.md`, `DEPLOYMENT_ARCHITECTURE.md` | runtime/deployment 설명 | V28~V36, 13 task types, CI 부재 미반영 | UPDATE_REQUIRED | current coexistence 반영 | 높음 |
| `docs/contracts/INTERNAL_AI_API_PRINCIPLES.md`, `PROVENANCE_CONTRACT.md` | internal/provenance 원칙 | Spring/FastAPI 경계의 결정 근거 | CURRENT_CANONICAL | exact 계약 안정화의 상위 원칙 | 높음 |
| `docs/contracts/CONTRACT_OVERVIEW.md`, `PUBLIC_API_PRINCIPLES.md`, `PUBLIC_API_V2_CONTRACT.md`, `INTERNAL_AI_API_V1_CONTRACT.md`, `STATUS_AND_ERROR_CONTRACT.md` | 상세 계약 | actual endpoint 또는 task/contentType/retry와 불일치 | UPDATE_REQUIRED | As-Is matrix와 internal 안정화 결과 반영 | 매우 높음 |
| `docs/contracts/fixtures/internal-ai-v1/**` | executable fixture/validator | Java hash test, Python validator, manifest | MACHINE_CONSUMED | 13 task와 exact schema로 갱신; 삭제 금지 | 매우 높음 |
| `docs/api/openapi.yaml` | legacy API machine contract | backend test가 runtime read | MACHINE_CONSUMED | consumer 유지 중 삭제 금지 | 매우 높음 |
| `docs/guide/*.docx`, `docs/example/*.docx` | frontend 배포 원본 | npm hooks, Node copy/hash, Dockerfile COPY | MACHINE_CONSUMED | consumer 교체 전 삭제 금지 | 높음 |
| `docs/reference/design/**` | 디자인 원본 | build/runtime read 없음 | REFERENCE_ONLY | 그대로 유지 | 낮음 |
| `docs/uiux/TARGET_ROUTE_MAP.md`, `WORKFLOW_UX.md` | target route/UX | 실제 Router와 보존 MVP 표현이 다름 | UPDATE_REQUIRED | current Journey/실험 route 분리 | 높음 |
| `docs/migration/CURRENT_TO_TARGET_MAPPING.md`, `LEGACY_REMOVAL_PLAN.md` | 전환/제거 원칙 | V28~V36와 보존 정책 미반영 | UPDATE_REQUIRED | V1~V36 유지/HIGH-only 원칙 반영 | 높음 |
| `docs/governance/DECISION_LOG.md` | accepted/deferred 결정 권위 | canonical 문서가 참조 | CURRENT_CANONICAL | 이번 확정 결정 후속 반영 | 중간 |
| `docs/governance/CHANGE_IMPACT_LEDGER.md`, `PHASE_STATUS.md`, `PROGRAM_STATUS.md` | 변경 영향/현재 현황 의도 | 현재 구현보다 뒤처짐 | UPDATE_REQUIRED | 현재 종료점과 CI NOT_PRESENT 반영 | 높음 |
| `docs/governance/PHASE0_REPOSITORY_AUDIT.md` | 과거 Phase 0 감사 | index/removal 문서가 이력으로 참조 | HISTORICAL_EVIDENCE | 현재 권위로 사용하지 않고 보존 | 낮음 |
| `docs/governance/VERIFICATION_EVIDENCE.md` | 과거 실행 기록 | command/result/한계를 기록 | HISTORICAL_EVIDENCE | 기존 행 보존, 새 evidence만 추가 | 중간 |
| `docs/quality/ACCEPTANCE_CRITERIA.md`, `QUALITY_GATES.md`, `STABLE_CORE_REGRESSION.md`, `TEST_STRATEGY.md` | 품질 정책/명령 | 현 Journey·CI 부재·internal drift gate 미반영 | UPDATE_REQUIRED | 새 기준선 최소 gate 재정의 | 높음 |

주요 문서 중 `REMOVAL_CANDIDATE`는 없다. 실행계획만 `SUPERSEDED`이며 즉시 삭제하지 않고 결정·결과 이관 후 별도 제거 대상으로 제안한다.

## 6. 보정된 코드 분류표

| 경로/영역 | 현재 역할 | 실제 참조 근거 | 분류 | 권장 조치 | 위험도 |
|---|---|---|---|---|---|
| `backend/.../journey/{JourneyAiService,IdeaOrigin*,LegalPrecheck*}` | 현재 Idea/Legal 재설계 | Router→journeyApi→Controller→Service→V28/V32/V33 | CURRENT_AS_BUILT | 유지·internal 계약 정렬 | 높음 |
| `backend/.../journey/Concept*` + V34~V36 | 현재 eligibility 종료점 | Concept page와 in-memory executor | CURRENT_AS_BUILT | 실제 실행 방식을 문서화 | 높음 |
| `ai/app/legal/**` 및 legal assets | official source/guardrail/concept validation | FastAPI task dispatch, tests | CURRENT_AS_BUILT | registry/version/error schema 유지 | 높음 |
| `frontEnd/src/features/journey/JourneyPages.jsx`, `ConceptJourneyPages.jsx`, `journeyApi.js` | 현재 공식 Journey UI/API client | `AppRouter` 직접 import, Idea→Legal→Concept | CURRENT_AS_BUILT | 종료점 표현 유지 | 높음 |
| Concept 분석·선택·Persona·Interview·Marketing·Report v2 code/pages | 보존된 기존 MVP 실험 화면 | route 접근과 상호 링크는 존재하나 공식 Journey 단계 아님 | LEGACY_REFERENCED | 코드/route 유지, 공식 Journey로 표현하지 않음 | 높음 |
| legacy `/api/v1` backend domains | 기존 workflow/API | Controller route, tests, admin/jobs, 외부 소비 가능 | LEGACY_REFERENCED | replacement+consumer 증거 후 단계 제거 | 높음 |
| legacy frontend `features/documents`, `feasibility`, `financial`, `legal-review`, `personas`, `validation`, `marketing`, `report` | Router에는 mount 안 됨; tests와 내부 hooks가 참조 | runtime AppRouter import 없음, test imports 존재 | LEGACY_REFERENCED | replacement test 전환 후 파일별 재조사 | 중간 |
| `frontEnd/src/page/**`, `Head.jsx/Head.css`, root `App.css` | 과거 top-level UI | current entry/router import 없음; 일부 상호 import와 public docx link만 존재 | REMOVAL_CANDIDATE | 후보별 import graph 확인 후 묶음 제거 | 중간 |
| `frontEnd/src/assets/react.svg`, `vite.svg` | starter assets | 저장소 전체 참조 0 | REMOVAL_CANDIDATE | 정리 작업 2에서 제거 가능 | 낮음 |
| Spring direct OpenAI adapters | 과거 workflow AI | interface bean으로 legacy JobExecutor가 호출 | LEGACY_REFERENCED | 현재 호출자 제거 전 보존 | 높음 |
| FastAPI `/api/v1/tasks`, marketing/banner/output | smoke/legacy AI 경로 | Spring legacy clients, scripts, readiness check | LEGACY_REFERENCED | scripts/clients 교체 전 보존 | 중간 |
| `LegacyAwarePasswordEncoder` | 과거 password hash 호환 | `JwtConfiguration` Bean에서 현재 사용 | LEGACY_REFERENCED | 이름만으로 제거 금지 | 높음 |
| `frontEnd/src/shared/ui/**` | current shared components | current auth/project/journey imports | CURRENT_AS_BUILT | 유지 | 낮음 |

## 7. 실제 API와 문서 계약 불일치

현재 실행 권위는 실제 Controller와 `journeyApi`다. 아래 불일치는 오래된 Target 계약/OpenAPI를 현재 As-Is에 맞춰 정리하기 위한 입력이며, Target 문서에 맞춘 대규모 API 코드 변경 제안이 아니다.

| 항목 | 문서의 정의 | 실제 코드 | 영향 | 정리 제안 |
|---|---|---|---|---|
| Idea paths | `/idea-sources/text`, `/idea-sources/files` | JSON/multipart 모두 `POST /ideas` | 계약 client 생성 불가 | As-Is endpoint matrix에 `POST /ideas` 기록 후 계약 갱신 |
| run resource 명명 | `*-runs`, history/id 조회 | `/idea-interpretations`, `/concept-generations/current` 등 current 중심 | history/version API 부재 | 필요한 history semantics 결정 |
| async command | AI command는 202 + `TaskRunPublicView` + `resultResource` | Legal은 worker, Concept은 202+in-memory executor, 다수 후속 MVP는 HTTP 요청 안 동기 실행 | timeout/retry/UI semantics 불일치 | 일괄 전환하지 말고 기능별 실제 실행 방식을 계약에 기록 |
| Concept 생성 | `/concept-generation-runs` | `/concept-generations`; 이것만 202이나 batch view 반환 | target client 불호환 | eligibility batch를 계약 resource로 명시 |
| idempotency | 모든 비멱등 POST에 `Idempotency-Key` 필수 | Journey client는 TaskRun retry만 header 설정 | 오래된 Target 계약과 As-Is 차이 | 먼저 As-Is matrix에 기록; 정책 변경은 별도 제품 결정 |
| decision method/resource | POST resource 생성(201) | shortlist/selection/asset/report decision은 PUT 200 | audit/replay semantics 차이 | immutable decision 정책 결정 |
| Persona paths | study 하위 cards와 generation runs | flat `/persona-cards`, `/persona-cards/generate`, `/persona-interviews` | contract와 route graph 상이 | 현 flat API를 문서화하거나 target로 이행 |
| Final report | logical report/version/HTML/PDF export | `/final-reports`, `/current`, browser `window.print()` | persisted HTML/PDF 목표 미구현 | current browser print를 명시, export는 target 유지 |
| success envelope | public v2 examples는 `data/meta` 중심 | common `ApiResponse`는 `success,data,error,meta`; TaskRun은 별도 `Envelope(data,meta)` | v2 내부에서도 두 envelope | As-Is를 먼저 문서화하고 이후 정합화 범위 결정 |
| error envelope | target fieldErrors/details 규약 | `GlobalExceptionHandler` common shape와 `TaskRunV2ExceptionHandler.ErrorEnvelope` 별도 | client error parser drift | common handler 또는 adapter 통합 |
| HTTP creation status | root/version/decision 201 | 다수 Controller annotation 없이 200 | caching/client behavior 오차 | endpoint별 status 테스트 추가 |
| OpenAPI coverage | 구현 계약이어야 함 | v2는 TaskRun GET/retry/cancel만 포함, Journey 없음 | generated client/validation이 현실 누락 | legacy section 동결 + current v2 별도 spec 후 합치기 |

## 취소된 위험 항목

- **API client double unwrap 위험은 취소한다.** `frontEnd/src/shared/api/apiClient.js`의 `request()`는 `readResponseBody()`로 얻은 JSON payload 전체를 `return payload`한다. `frontEnd/src/features/journey/journeyApi.js`가 각 `client.get/post/put/upload` 결과의 `.data`를 한 번만 추출한다. 현재 구조는 `ApiResponse<T>` envelope에 대해 단일 unwrap이다.

가장 위험한 부분은 “202 accepted durable work”라는 문서와 같은 HTTP 요청 안에서 AI provider 완료까지 기다리는 실제 구현의 차이다. Legal Precheck만 persistent polling worker 방식에 가깝고 Concept은 in-memory executor, 나머지는 service 동기 실행이다.

## 8. Spring–AI 내부 계약 불일치

| 항목 | 계약 | 실제 코드 | 영향/제안 |
|---|---|---|---|
| TaskType set | 문서 registry는 11개 중심 | Java/FastAPI는 13개: `IDEA_LEGAL_PRECHECK`, `CONCEPT_LEGAL_VALIDATION` 추가 | 문서/fixtures/validator에 두 type과 schemas 추가 |
| `contentType` | `TextContent.contentType = TEXT` | 모든 Spring Journey producer는 `PLAIN_TEXT`; FastAPI validator는 key 존재만 검사 | HIGH: executable fixture와 production payload가 다름. Spring을 `TEXT`로 고치고 FastAPI enum 검증 추가 |
| locale | v1은 `ko-KR`만 허용 | Spring은 `ko-KR`; Pydantic/FastAPI는 임의 string 허용 | AI Server에서 literal 검증 추가 |
| schemaVersion | `1.0` exact task schema | 생성은 대부분 `1.0`; Pydantic은 string, route에서 `1.0` 검사 | 대체로 일치, nested task schema는 generic dict라 불충분 |
| input schema | task별 strict named schema/unknown reject | Pydantic `input: dict[str, Any]`; 공통 text keys만 검사, legal handler가 별도 수동 검증 | contract-level strictness 미충족; task discriminator validator 필요 |
| max payload | raw request/response 2 MiB | FastAPI middleware와 Java client 모두 2 MiB | 일치. 단 Spring domain input 생성 전 bound 검증 확인 필요 |
| canonical hash | version/type/schema/locale/input canonical JSON, NFC | Java `CanonicalInputHasher`, Python `canonical_hash` 방향 일치 | fixture test는 IDEA만 직접 소비; 신규 task coverage 추가 |
| response identity | 모든 identity echo/exact 검증 | `TaskRunWorker`는 강하게 검증; service별 sync execute는 response identity 일부를 명시적으로 재검증하지 않고 client parse 후 adopt | 공통 validator를 sync paths에도 강제 |
| result schema | task별 exact result + domain validator | Java service별 수동 validator; FastAPI response는 generic dict | 신규 legal/concept result registry와 fixture 부족 |
| error code/reason | 12 provider-neutral code, reason별 retryable 고정 | Java allowed set은 12개이나 `AI_CONFIGURATION_INVALID`, `LEGAL_*`, `CONCEPT_*`, `AI_RESULT_INVALID` reason이 계약 registry 밖에서 사용됨 | reason registry 확장 또는 표준 reason으로 normalize |
| deadline retryable | 계약의 `REQUEST_DEADLINE_EXCEEDED`는 retryable=true | FastAPI는 504, retryable=false; Java `TaskAttempt.timeOut`은 true | 동일 실패가 경로별 다르게 재시도됨; 하나로 통일 |
| unauthorized reason | missing/invalid 구분 | FastAPI는 token 없음도 `SERVICE_TOKEN_INVALID`; Java local missing은 `SERVICE_TOKEN_MISSING` | reason/observability drift 수정 |
| legal content model | contract `TEXT`/공통 schema | `LegalSourcePipelineInput`은 별도 builder, FastAPI legal pipeline 수동 validation | 정식 v1 schema로 등록 |

## 9. Frontend Route와 현재/Legacy 화면 관계

| Route/영역 | 실제 element | 관계 | 분류/조치 |
|---|---|---|---|
| `/app/projects/:id`, `/idea` | `IdeaJourneyPage` | current 공식 재설계 | CURRENT_AS_BUILT |
| `/legal` | `LegalJourneyPage` | current 공식 재설계 | CURRENT_AS_BUILT |
| `/journey/concept` | `ConceptGenerationPage` | current 공식 eligibility 종료점 | CURRENT_AS_BUILT |
| `/journey/concept-analysis` ~ `/journey/final-report` | v2 Journey pages | route 접근/상호 링크는 존재하나 공식 Journey가 아닌 보존 MVP 실험 화면 | LEGACY_REFERENCED |
| old `plan/review/validate/report` | `LegacyProjectRedirect` | 새 route로 redirect하는 compatibility | LEGACY_REFERENCED |
| old `/projects/:id/...` | `LegacyProjectRedirect` | 새 route로 redirect하는 compatibility | LEGACY_REFERENCED |
| old feature/page components | Router mount 없음 | tests/내부 hooks만 남음 | 제거 후보 또는 legacy referenced를 파일별 분리 |

후속 MVP는 route와 내부 링크가 존재하지만 제품 정책상 현재 Journey에 공식 연결된 단계로 간주하지 않는다. 코드는 보존하고 UI/문서에서 “보존된 기존 MVP 실험 화면”으로 명확히 표시하는 것이 확정 방향이다.

## 10. 데이터베이스 및 Migration 상태

## 확인된 Migration 구성

- SQL Migration은 `backend/src/main/resources/db/migration`에 있고 Java Migration은 Flyway 기본 classpath package인 `backend/src/main/java/db/migration`에 있다.
- `V5__harden_document_integrity.java`는 `BaseJavaMigration`을 상속하고 문서 무결성 보강을 수행한다.
- `V10__add_username_and_optional_profile.java`는 `BaseJavaMigration`을 상속하고 username/optional profile 전환을 수행한다.
- SQL V1~V4, V6~V9, V11~V36과 Java V5/V10을 합치면 1부터 36까지 누락 없이 36개 version이다.
- 가장 높은 번호는 `V36__normalize_current_journey_retryability.sql`이다.
- TaskRun 기반: V27.
- Idea source/version/interpretation/legal review: V28.
- 기존 concept~selection: V29, Persona/interview: V30, Marketing/report: V31.
- Idea Origin/clarification: V32.
- Legal Precheck/Guardrail: V33.
- Concept eligibility/origin/legal loop: V34, retryability: V35~V36.
- V34는 기존 `concept_versions`에 eligibility fields를 추가하고 기존 row를 `LEGACY`, 새 eligible row를 `ELIGIBLE`로 공존시킨다. V29는 `financial_analyses`에 Journey FK를 추가해 legacy 재무 테이블을 후속 Journey에서도 재사용한다.
- legacy V1~V26 테이블과 AnalysisJob은 삭제되지 않았고 `/api/v1` domains가 계속 참조한다.

모든 V1~V36은 현재 schema를 재현하는 `CURRENT_AS_BUILT`이자 **무조건 유지 대상**이다. 기존 Migration 수정·삭제, 데이터 삭제, Drop을 제안하지 않는다. 추후 DB 정리가 필요해도 별도 승인된 새 Migration으로만 수행한다.

## 11. 환경설정 상태

| 경로/영역 | 현재 역할 | 실제 참조 근거 | 분류 | 권장 조치 | 위험도 |
|---|---|---|---|---|---|
| DB `POSTGRES_*` → compose `DB_URL/DB_USERNAME/DB_PASSWORD` | PostgreSQL 연결 | compose/application profile | CURRENT_AS_BUILT | 이름 mapping 문서화 | 낮음 |
| `AI_INTERNAL_SERVICE_TOKEN` → backend `AI_SERVER_INTERNAL_API_KEY` | Spring–FastAPI service auth | compose/client/FastAPI | CURRENT_AS_BUILT | 두 이름의 의도적 bridge 명시 | 높음 |
| `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL` | FastAPI provider | `journey_provider.py` | CURRENT_AS_BUILT | secret 제외 예시 유지 | 높음 |
| `AI_MODEL_CONCEPT_VALIDATION` | concept legal 전용 model override | `concept_validation.py` | CURRENT_AS_BUILT | `.env.demo.example`에도 필요 여부 명시 | 중간 |
| `MOLEG_API_KEY`, `MOLEG_API_BASE_URL`, legal timeout/cache/registry | 법제처/법률 source | `moleg.py`, `registry.py`, compose | CURRENT_AS_BUILT | no-key degraded behavior 문서화 | 높음 |
| `CONCEPT_TARGET_ELIGIBLE_COUNT`, replacement/max inspected | eligibility 정책 | application + compose + service | CURRENT_AS_BUILT | 3/2/9 invariant와 허용 범위 검증 | 중간 |
| `OBJECT_STORAGE_*`, `MINIO_ROOT_*` | Spring S3 adapter/compose MinIO | compose/ObjectStorageProperties | CURRENT_AS_BUILT | `MINIO_BUCKET` vs `OBJECT_STORAGE_BUCKET` 통일 검토 | 중간 |
| `VITE_API_BASE_URL` | local frontend API base | `config.js`, demo script | CURRENT_AS_BUILT | 값이 `/api/v1`이어도 absolute `/api/v2`가 통과하는 동작 문서화 | 중간 |
| `AI_FIXTURE_MODE` | env examples/compose에 존재 | AI application code 직접 소비 검색 결과 없음 | UNKNOWN | 실제 사용 의도 확인 후 구현 또는 제거 | 중간 |
| `AI_APP_ENVIRONMENT` | compose가 FastAPI `APP_ENVIRONMENT`로 전달 | e2e fault/환경 경계 가능 | CURRENT_AS_BUILT | 이름 bridge 설명 | 낮음 |
| `AI_ARTIFACT_MAX_BYTES` | compose/example | `artifact_service.py` 직접 소비는 timeout만 확인 | UNKNOWN | code 사용 여부 추가 조사 | 낮음 |
| `.env.infrastructure.example`의 `MINIO_BUCKET` | infrastructure compose 전용 | `compose.infrastructure.yaml` | LEGACY_REFERENCED | main compose naming과 혼합 금지 | 중간 |

`.env.e2e.example`의 `AI_SERVER_INTERNAL_API_KEY`는 main compose가 요구하는 `AI_INTERNAL_SERVICE_TOKEN`과 이름이 달라 동일 compose에 바로 쓰는 계약으로는 불일치한다. 어느 compose/script 전용인지 명시해야 한다. Secret의 실제 값은 확인하지 않았다.

## 확인된 CI 상태

| 확인 항목 | 결과 | 판정 |
|---|---|---|
| `.github` | root `Get-ChildItem -Force`에 없음 | NOT_PRESENT |
| `.github/workflows` | 상위 경로가 없어 없음 | NOT_PRESENT |
| workflow 파일 | 0개 | 없음 |
| trigger branch | workflow 정의가 없어 0개 | 없음 |
| jobs | workflow 정의가 없어 0개 | 없음 |
| 현재 repository-local GitHub Actions | 실행 정의가 없으므로 활성 상태가 아님 | **REMOVED/NOT_PRESENT** (현재 트리 기준); DEFERRED나 ACTIVE 아님 |

따라서 CI는 “보류된 workflow가 파일로 남아 있음”이나 “활성”이 아니다. **현재 작업 트리 기준 workflow가 제거되었거나 구성되지 않은 상태**다. Git history를 조회하지 않았으므로 제거 시점과 의도는 단정하지 않는다. README, baseline, governance, quality 문서의 GitHub Actions/Remote CI 설명은 현재 상태 설명으로는 `UPDATE_REQUIRED`다.

## 12. Machine-consumed 문서와 리소스

| 자원 | 소비 방식 | 판정 |
|---|---|---|
| `docs/api/openapi.yaml` | backend `Phase2SemanticContractTests`가 `Files.readString`; 문서/quality tooling도 current legacy contract로 지정 | machine-consumed, 삭제 금지 |
| `docs/contracts/fixtures/internal-ai-v1/**` | `validate_fixtures.py`; Java `CanonicalInputHasherTests`가 request fixture read | machine-consumed, 갱신 필요 |
| `docs/guide/*.docx`, `docs/example/*.docx` | npm predev/prebuild/pretest → Node copy+hash verify; frontend Dockerfile COPY | build/test input, 삭제 금지 |
| `frontEnd/public/resources/business-plan/*.docx` | 위 script의 생성/배포 대상; runtime download link | 배포 resource. 원본과 중복이지만 임의 삭제 금지 |
| `frontEnd/public/business_plan_guideline.docx` | 현재 Router에 없는 `src/page/ProjectCreate.jsx`만 링크 | legacy referenced; page 제거와 함께 재검토 |
| `docs/reference/design/**` | build/runtime read 확인 안 됨 | 사람용 reference-only |
| `.github/workflows/*` | `.github` 자체가 없음 | current tree에 workflow consumer/definition 없음; CI INACTIVE |
| `scripts/*.ps1` | manual smoke/demo, legacy AI endpoints와 compose 사용 | machine/manual operational input |

## 13. 유지 대상

1. V1~V36 전체 Migration과 현재 JPA entity/repository 연결.
2. auth/user/project/admin/audit/service policy/common security 및 current shared UI/API client.
3. Idea Origin, Legal Precheck/Guardrail, Concept Eligibility와 legal assets.
4. TaskRun/TaskAttempt/TaskResult, canonical hasher, internal client/worker.
5. machine-consumed OpenAPI/fixtures/guide/example는 소비자 대체 전 유지.
6. `/api/v1` 및 direct provider adapters는 실제 호출자·외부 소비 종료 증거 전 유지.

## 14. 최신화 대상

우선순위는 다음과 같다.

1. `INTERNAL_AI_API_V1_CONTRACT.md` + fixtures + Spring producers + FastAPI validator의 `contentType`, task set, error/retry semantics.
2. `CURRENT_BASELINE.md`, root/docs README, architecture/runtime 설명을 V36 기준으로 갱신.
3. 실제 Controller와 `journeyApi` 기반 Public API As-Is Endpoint Matrix 작성.
4. `PUBLIC_API_V2_CONTRACT.md`, status/error, machine-consumed OpenAPI를 As-Is 기준으로 정합화.
5. `ProjectLayout`, route map, workflow UX에서 현재 Journey와 보존 MVP 실험 화면을 분리.
6. env example별 대상 compose/script와 변수 alias를 명시.
7. HIGH 제거 후보만 별도 작업으로 정리.
8. 사용자 최소 검증 결과를 기록하고 새 기준선을 선언.

## 15. 제거 후보와 증거 수준

| 경로/영역 | 현재 역할 | 실제 참조 근거 | 분류 | 권장 조치 | 위험도 |
|---|---|---|---|---|---|
| `frontEnd/src/assets/react.svg` | Vite starter asset | 전체 저장소 exact filename 참조 0; current asset은 `hero.png` 등 | REMOVAL_CANDIDATE / HIGH | 정리 작업 2에서 제거 가능 | 낮음 |
| `frontEnd/src/assets/vite.svg` | Vite starter asset | 전체 저장소 exact filename 참조 0 | REMOVAL_CANDIDATE / HIGH | 정리 작업 2에서 제거 가능 | 낮음 |
| `frontEnd/src/Head.jsx`, `Head.css` | 과거 header | current entry/router import 0; 둘만 상호 참조; `AppShell` 대체 확인 | REMOVAL_CANDIDATE / HIGH | 묶음 제거 전 visual 의도 확인 | 낮음 |
| `frontEnd/src/page/ContactPage.jsx`, `ServicePage.jsx`, `Login.jsx`, `Admin.jsx` 등 | 과거 top-level screens | current entry/router import 0; 일부 root CSS/서로만 참조 | REMOVAL_CANDIDATE / MEDIUM | 외부 deep import/교육 demo 여부 확인 후 파일별 제거 | 중간 |
| `frontEnd/src/page/ProjectCreate.jsx` + `public/business_plan_guideline.docx` | 과거 project create/download | page는 current Router import 0, docx는 이 page만 참조; 새 `ProjectCreatePage`와 guide/example 배포 대체 존재 | REMOVAL_CANDIDATE / MEDIUM | 둘의 소비자 동시 확인 후 제거 | 중간 |
| old feature page components | Router 미연결 | runtime import 0인 파일이 많지만 test가 직접 import하고 hooks/API는 상호 참조 | REMOVAL_CANDIDATE / LOW | 제거 권고하지 않음; replacement test/외부 소비 확인 | 중간 |

`HIGH`는 정적 전체 저장소 무참조와 대체 진입점이 함께 확인된 것, `MEDIUM`은 직접 runtime 참조는 없으나 외부/dynamic 소비 가능성이 남은 것, `LOW`는 미참조를 충분히 입증하지 못한 것이다. LOW는 제거 권고가 아니다. Flyway, 이름에 Legacy가 포함된 auth code, `/api/v1`, direct adapters, old OpenAPI/guide/example는 실제 참조가 있으므로 제거 후보에서 제외했다.

## 16. 남은 확인 항목

제품·정리 방향은 더 이상 확인 대기가 아니다. 남은 항목은 외부 또는 runtime evidence가 필요한 다음 세 가지뿐이다.

1. 외부 `/api/v1` 소비자와 old deep-link 사용자의 존재 여부. 확인 전까지 유지한다.
2. `.github`가 제거된 시점과 외부 CI 존재 여부. current tree에는 workflow가 없다는 사실은 확정이다.
3. `AI_FIXTURE_MODE`, `AI_ARTIFACT_MAX_BYTES`의 의도된 runtime 소비 여부.

## 17. 개선된 후속 작업 순서

| 순서 | Atomic 작업 | 주요 수정 대상 | 금지 범위 |
|---|---|---|---|
| 1 | Internal AI 계약 안정화 | 아래 18절의 exact target | Public API/Route/Migration 변경 금지 |
| 2 | 현재 기준선 문서 최신화 | `README.md`, `docs/README.md`, `CURRENT_BASELINE.md`, architecture/governance 일부 | code 동작 변경 금지 |
| 3 | Public API 실제 Endpoint Matrix 작성 | Controller, `journeyApi`, `ApiResponse`를 읽어 새/기존 계약 문서에 As-Is matrix 반영 | endpoint code 변경 금지 |
| 4 | Public API·Status·OpenAPI 정합화 | `PUBLIC_API_V2_CONTRACT.md`, `STATUS_AND_ERROR_CONTRACT.md`, machine-consumed `openapi.yaml`과 consumer test | 대규모 API 재설계, `/api/v1` 제거 금지 |
| 5 | 현재 Journey/보존 MVP Route 표현 정리 | `ProjectLayout.jsx`, route/UX 문서; 필요 최소 Router 표기 | 보존 MVP code/route 삭제 금지 |
| 6 | env/compose 설정 정리 | env examples, compose aliases, `application.yaml` 문서 연결 | secret 값, provider 동작 변경 금지 |
| 7 | HIGH 제거 후보 정리 | `react.svg`, `vite.svg`, `Head.jsx`, `Head.css`를 별도 change로 재검증 | MEDIUM/LOW, Migration, machine inputs 삭제 금지 |
| 8 | 사용자 최소 검증과 새 기준선 선언 | 허용된 backend/frontend/AI 최소 gate 결과 및 baseline/governance | 미실행 결과를 PASS로 기록 금지 |

각 단계는 앞 단계 결과만 입력으로 삼는 별도 변경 단위다. DB 정리는 이 순서에 포함하지 않으며, 향후 별도 승인 시에도 기존 V1~V36을 수정하지 않는다.

## 18. 다음 작업: Internal AI 계약 안정화의 정확한 대상

### 18.1 Production 수정 대상

- Spring text producer: `backend/src/main/java/com/aivle/backend/taskrun/contract/LegalSourcePipelineInput.java`
- Spring Journey text producer: `JourneyAiService.java`, `ConceptJourneyService.java`, `PersonaJourneyService.java`, `MarketingReportJourneyService.java`
- Spring internal response/error 검증: `backend/src/main/java/com/aivle/backend/taskrun/integration/InternalAiExecutionClient.java`, `service/TaskRunWorker.java`, 필요 시 `TaskRunService.java`와 `domain/TaskAttempt.java`
- Java task registry 확인: `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskType.java` — 현재 13개 set은 유지하고 임의 제거하지 않는다.
- FastAPI request/schema validation: `ai/app/models/executions.py`, `ai/app/api/executions.py`
- FastAPI task/error provider normalization: `ai/app/services/journey_provider.py`, 필요한 legal validator인 `ai/app/legal/pipeline.py`, `concept_validation.py`

### 18.2 Contract·fixture·test 수정 대상

- `docs/contracts/INTERNAL_AI_API_V1_CONTRACT.md`
- `docs/contracts/STATUS_AND_ERROR_CONTRACT.md`의 internal mapping 부분만
- `docs/contracts/fixtures/internal-ai-v1/validate_fixtures.py`, `manifest.json`, `tasks/**`, 필요한 `common/**`·`negative/**`
- AI: `ai/tests/test_internal_executions.py`, `test_legal_source_contract.py`, `test_concept_eligibility_contract.py`
- Backend: `backend/src/test/java/com/aivle/backend/taskrun/**`와 `backend/src/test/java/com/aivle/backend/journey/**` 중 contract producer/validator tests

### 18.3 Atomic 하위 순서

1. 모든 Spring producer의 `contentType`을 canonical `TEXT`로 정렬하고 FastAPI가 literal `TEXT`와 locale `ko-KR`를 강제한다.
2. Java/FastAPI의 13개 TaskType에 맞춰 contract registry와 fixtures에 `IDEA_LEGAL_PRECHECK`, `CONCEPT_LEGAL_VALIDATION` request/response schema를 추가한다.
3. deadline, unauthorized, provider/legal reason과 `retryable`을 stable code/reason registry로 정렬한다.
4. sync service와 worker 모두 response identity/hash/schema/domain invariant를 같은 공통 기준으로 검증한다.

이 작업에서는 Public API endpoint/status/envelope, frontend Route, `/api/v1`, env/compose, Flyway Migration을 변경하지 않는다.

### 이후 기준선·UX 대상

- `README.md`, `docs/README.md`, `docs/CURRENT_BASELINE.md`
- `docs/architecture/{SYSTEM_ARCHITECTURE,AI_SERVER_BOUNDARY,SPRING_WAS_BOUNDARY}.md`
- `docs/uiux/{TARGET_ROUTE_MAP,WORKFLOW_UX}.md`
- `frontEnd/src/app/layouts/ProjectLayout.jsx`, 필요 시 `AppRouter.jsx`
- `docs/migration/{CURRENT_TO_TARGET_MAPPING,LEGACY_REMOVAL_PLAN}.md`
- `docs/governance/*`, `docs/quality/*`

### 이후 환경 및 선택적 제거

- `.env.example`, `.env.demo.example`, `.env.e2e.example`, `.env.infrastructure.example`
- `compose.yaml`, `compose.infrastructure.yaml`, `application.yaml`은 결정된 alias/timeout만 최소 변경
- HIGH 후보: `frontEnd/src/assets/react.svg`, `vite.svg`, `frontEnd/src/Head.jsx`, `Head.css`
- MEDIUM 후보는 사용자 확인 후 별도 목록으로 처리

`docs/api/openapi.yaml`은 현재 test consumer 때문에 단순 삭제 대상이 아니다. public v2 정리 시 legacy section을 보존하면서 실제 v2를 추가하거나, test consumer를 먼저 대체해야 한다.

## 19. 이번 조사에서 변경하지 않은 항목

- 이 보고서 외 기존 코드, 설정, 문서, Migration: 수정/생성/삭제 0.
- 파일 이동/이름 변경, 새 Migration, DB 데이터 변경: 0.
- Git 명령 및 Git 상태 조회: 0.
- Docker/build/test/lint/format/CI/network/provider/법제처 호출: 0.
- Secret 및 실제 환경변수 값 출력: 0.
- 정리 작업 1.1에서는 새 파일을 만들지 않고 기존 감사 보고서를 현재 위치에서 직접 보정했다.

### 알려진 가설 판정 요약

| 가설 | 판정 | 근거 |
|---|---|---|
| root README가 Idea·Legal·Concept를 반영하지 못함 | 맞음 | 구현 미시작/legacy 중심 설명, 실제 V32~V36 존재 |
| CURRENT_BASELINE Migration/Workflow가 오래됨 | 맞음 | V26 설명 vs 실제 V36; v2 전 Journey 존재 |
| PUBLIC_API_V2와 Controller/ApiResponse가 다름 | 맞음 | paths/status/idempotency/envelope 다수 불일치 |
| INTERNAL_AI contentType가 다름 | 맞음 | 계약 `TEXT`, Spring `PLAIN_TEXT` |
| Legal/Concept/Persona 실행 방식이 다름 | 맞음 | worker polling / in-memory executor / service sync claim |
| CODEX 실행계획은 완료된 계획일 수 있음 | 맞음; SUPERSEDED | 작업 묶음의 entity/service/UI/migration이 존재하고 production/build/canonical 소비 없음 |
| openapi는 오래됐지만 build/test 참조 가능 | 맞음 | backend test가 runtime read; Journey v2 coverage 거의 없음 |
| 후속 MVP는 미연결이나 route 접근 가능 | 기술적으로 접근 가능, 제품상 공식 미연결로 확정 | 코드는 보존된 기존 MVP 실험 화면으로 유지 |

## Baseline Cutover 결과

이 절은 최초 조사 사실을 보존하면서, 이후 사용자가 승인한 2026-08-04 Migration Baseline 전환 결과를 기록한다. 위 본문에서 V1~V36과 Java V5/V10을 유지 대상으로 판단한 내용은 **조사 당시 상태와 당시 제약**에 대한 기록이다. 이후 사용자는 기존 PostgreSQL 데이터를 승계하지 않고 빈 DB로 재시작하며 V1~V36 upgrade 경로를 지원하지 않기로 확정했다.

| 경로/영역 | Cutover 결과 | 근거/효과 |
|---|---|---|
| `backend/src/main/resources/db/migration/V1__baseline_schema.sql` | 현재 유일한 Runtime Migration | 과거 V1~V36의 최종 Table/Column/PK/FK/Unique/Check/Index/Default/Nullability를 빈 PostgreSQL에 직접 생성 |
| 과거 SQL V1~V36 | Runtime에서 제거 | upgrade/backfill/중간 호환 경로는 지원하지 않으며 Git history로 보존 |
| Java V5 | Baseline에 흡수 후 제거 | section code/status check와 active business-plan partial unique index 포함 |
| Java V10 | Baseline에 흡수 후 제거 | `username`/optional profile, nullable email, username unique index 포함 |
| Reference data | 별도 V2 없음 | 필수 고정 row가 없고 service setting은 코드 기본값, persona catalog는 startup importer가 담당 |
| Migration 검증 | PostgreSQL/Testcontainers 기준 | 빈 schema fresh/validate, 주요 table/constraint/index와 JPA `ddl-auto=validate`를 검증 |
| H2 | Service 로직 격리 테스트에 한정 | Migration 정확성의 증거로 사용하지 않음 |

기존 DB를 재사용하거나 `baselineOnMigrate=true`/validation 우회를 적용하지 않는다. 적용 전 PostgreSQL 또는 Docker volume을 삭제하고 재생성해야 한다. 상세 절차와 rollback은 `docs/maintenance/MIGRATION_BASELINE_CUTOVER_2026-08-04.md`를 따른다.

## 정리 작업 C 결과

최초 감사의 분류와 참조 조사 사실은 위에 보존한다. 이후 2026-08-04 최종 정리에서 다음 변경을 승인·반영했다.

- 실제 `/api/v2` Controller 45개 mapping과 Frontend `journeyApi` 사용 관계를 `PUBLIC_API_V2_CONTRACT.md`의 As-Is Matrix로 확정했다.
- Journey `ApiResponse`와 TaskRun 전용 envelope/status 차이를 현재 구현 그대로 문서화했다.
- 공식 Journey는 Idea → Legal → 적격 Concept 3개에서 종료하고 이후 Route/API/UI는 보존 MVP 실험 기능으로 분리했다.
- `docs/api/openapi.yaml`은 Backend test가 읽는 기존 `/api/v1` 중심 machine-consumed 계약으로 유지했으며 수정하지 않았다.
- HIGH 근거의 미참조 파일 `frontEnd/src/assets/react.svg`, `frontEnd/src/assets/vite.svg`, `frontEnd/src/Head.jsx`, `frontEnd/src/Head.css`를 제거했다. runtime/build import는 0이었고 `AppShell` 대체가 확인됐다.
- 이전 redesign draft와 완료된 Codex 실행계획의 필수 결정·결과를 v0.4 및 현재 기준선에 흡수한 뒤 두 파일을 제거했다. 과거 내용은 Git history로 보존한다.
- Baseline V1, Migration test, Public Controller, Frontend Route, Internal AI 계약은 변경하지 않았다.

### 정리 작업 C 최소 검증 보완

최초 C 정리 직후 전체 최소 검증에서 다음 누락이 확인되어 보완했다.

- Public As-Is 문서로 교체된 뒤에도 Internal fixture validator가 과거 67개 Target endpoint catalog를 읽고 있던 결합을 제거했다. Validator는 현재 45개 As-Is Matrix와 `STATUS_AND_ERROR_CONTRACT.md`의 명시적 Internal normalization registry를 읽는다.
- PostgreSQL 전용 V1을 H2가 실행하면서 partial index 문법으로 실패하던 test profile을 Flyway disabled + Hibernate `create-drop` Service 격리로 전환했다. PostgreSQL `postgresTest`의 Flyway/validation 책임은 변경하지 않았다.
- H2에서 과거 Flyway history 개수와 V22 upgrade row를 검사하던 테스트는 현재 upgrade 미지원 결정에 맞춰 서비스 스키마 검사로 교체하거나 제거했다. 따라서 위의 “Migration test 미변경”은 **PostgreSQL Baseline Migration test 미변경**을 뜻하며, 역사적 H2 migration 테스트는 이번 보완에서 정리됐다.
- AI pytest는 사용자 공용 Temp 권한이나 다른 실행 주체가 만든 고정 경로에 의존하지 않도록 실행별로 고유한 저장소 내부 임시 경로를 사용한다.
- Frontend lint를 해소하고 현재 Journey Route/UI와 달라진 테스트 기대값을 갱신했다. test-debt allowlist는 40개에서 18개로 축소했으며 새 실패를 추가하지 않았다.
- C에서 제거한 `react.svg`, `vite.svg`, `Head.jsx`, `Head.css`는 현재/백업 전체 소스 대조에서도 유일한 차이였고 runtime/build/test 실패 원인이 아니므로 복구하지 않았다.

## 기준선 마감 정리 결과

- 최초 조사 당시에는 `.github/workflows`가 없었다.
- 정리 작업 D에서 repository-local `.github/workflows/ci.yml`을 추가했다.
- 현재 CI 상태는 최신 기준선 문서와 실제 Workflow가 우선한다.
- 의도적으로 남긴 호환·MVP·machine-consumed 항목은 [RETAINED_LEGACY_REGISTRY](RETAINED_LEGACY_REGISTRY.md)에서 제거 조건과 함께 관리한다.
