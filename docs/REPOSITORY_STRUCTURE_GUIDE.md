# Repository Structure Guide

## 1. 문서 기준 Commit

- Reviewed baseline: `3aeff219d72e1be502ba4ad1cade7f7aca83d10e`
- 현재 내용: 위 기준선에 정리 작업 D 결과를 반영한 상태. 이후 변경에서는 commit 값보다 실제 코드와 `CURRENT_BASELINE.md`를 우선 확인한다.

## 2. Root 파일 설명

| 경로 | 역할 |
|---|---|
| `README.md` | 제품·Runtime·Journey 진입점 |
| `LOCAL_RUN.md` | 공식 Docker Journey와 보존 MVP 확인 절차 |
| `compose.yaml` | React, Spring, FastAPI, PostgreSQL, MinIO 전체 로컬 stack |
| `compose.infrastructure.yaml` | PostgreSQL과 MinIO만 실행 |
| `compose.e2e.yaml` | disposable Docker E2E port/profile override |
| `.env.example` | 공식 전체 Compose 외부 입력 |
| `.env.infrastructure.example` | DB/Object Storage 전용 입력 |
| `.env.e2e.example` | 비접속 Provider placeholder를 쓰는 disposable E2E 입력 |
| `.env.demo.example` | `/api/v1` 중심 Legacy Backend/Frontend 직접 실행 입력 |
| `.gitignore` | 로컬 Secret, build/cache/runtime 산출물 제외 |
| `scripts/` | smoke, failure E2E, legacy demo와 보조 실행 스크립트 |
| `backend/` | Spring 업무/API/persistence/TaskRun 경계 |
| `ai/` | FastAPI 내부 AI execution과 legacy endpoint |
| `frontEnd/` | React UI, Router와 API client |
| `docs/` | 현재 권위, target, reference, historical, machine-consumed 문서 |

## 3. Backend 구조

| Package | 역할과 상태 | 먼저 볼 파일/Pattern |
|---|---|---|
| `admin` | 관리자 사용자·Project·TaskRun·설정·감사 조회 | `AdminController`, `Admin*Service` |
| `analysis` | 기존 타당성·재무 분석; 보존/호환 영역 | `FeasibilityAssessmentController`, `FinancialAnalysisController`, 관련 Service/Repository |
| `aitask` | `/api/v1` AI task와 artifact smoke/무결성 | `AiTaskController`, `*Task*Service`, `AiTaskResultRepository` |
| `audit` | 도메인 감사 기록 | `DomainAuditService`, `AuditEventRepository` |
| `auth` | 가입·로그인·JWT·refresh | `AuthController`, `AuthService`, `JwtTokenService` |
| `common` | 공통 response, exception, base model | `common/response`, `common/exception`, `BaseEntity` |
| `config` | Security, Web, persistence와 client 설정 | 변경 목적에 맞는 `*Config` |
| `document`, `file` | 문서 upload/구조화와 저장 파일 수명주기 | `DocumentController`, `StructuredPlanController`, command/query Service |
| `job` | 기존 `AnalysisJob` worker/query/recovery | `JobController`, `JobClaimService`, `JobRecoveryService` |
| `journey` | 공식 Idea–Legal–Concept와 보존 MVP Journey | `JourneyController`, `JourneyAiService`, `IdeaOriginService`, `LegalPrecheckController/Service`, `ConceptJourneyController/Service`; Persona/MarketingReport Service는 보존 MVP |
| `marketing` | `/api/v1` marketing content/job 호환 | `MarketingContentController`, generation Service/Repository |
| `objectstorage` | S3-compatible object adapter와 정책 | `ObjectStorage*` interface/service/adapter |
| `persona` | 기존 persona catalog/recommendation | `BaselinePersonaController`, `ProjectPersonaCatalogController`, 관련 Service |
| `project` | Project 소유권과 제품 정보 | `ProjectController`, `ProjectService`, `ProjectRepository` |
| `report` | 기존 report persistence | `ReportRepository`와 소비 Service |
| `taskrun` | TaskRun/TaskAttempt/TaskResult lifecycle와 v2 API | `TaskRunV2Controller`, `TaskRunService`, `TaskRunWorker`, `InternalAiExecutionClient` |
| `user` | 사용자 persistence와 삭제 정책 | `UserRepository`, `UserDeletionService` |
| `validation` | 기존 interview/market/persona validation | 각 Controller/Service/Repository; 보존 MVP 소비 확인 후 변경 |

Entity/DTO/Repository를 개별 나열하기보다 Controller → command/query Service → Entity/Repository → migration 순으로 추적한다. 공식 Journey 변경은 `journey`와 `taskrun`을 함께 확인한다.

## 4. AI 구조

- `main.py`: FastAPI app, middleware, health와 router 조립.
- `app/api`: `/internal/v1/ai/executions`, legacy `/internal/v1/tasks`, `/api/v1/marketing` endpoint.
- `app/models`: Internal request/response, Journey, legal source와 legacy schema.
- `app/services`: dispatcher, `journey_provider`, task/marketing service. `journey_provider`가 공식 13 TaskType 결과를 만든다.
- `app/legal`: legal registry/source/screening pipeline과 Concept legal validation.
- `app/testing`: E2E fault/test-double 지원. Production 성공 fallback으로 사용하지 않는다.
- `prompts`: Task별 prompt. 계약 변경과 prompt 변경을 분리한다.
- `tests`: Internal execution, Journey/legal/concept contract와 dispatcher 회귀.
- `requirements.txt`, `Dockerfile`: Python dependency와 deployable image.

`/internal/v1/ai/executions`가 Spring–FastAPI 공식 내부 경계다. Legal pipeline과 Concept validation은 그 dispatcher 안의 서로 다른 TaskType이며, legacy task/marketing endpoint는 별도 호환 경계다.

## 5. Frontend 구조

- `src/main.jsx`: React bootstrap.
- `src/app`: application shell; `router/AppRouter.jsx`, `layouts`, `providers`가 route/layout/context를 소유한다.
- `features/auth`, `features/projects`: 인증과 Project 흐름.
- `features/journey`: 공식 Idea·Legal·Concept UI와 보존된 분석·선택·Persona·Interview·Marketing·Report UI.
- `features/admin`, `features/settings`: 관리자와 설정.
- `shared/api`: 공통 HTTP client/envelope/error 처리.
- `shared/ui`: 공유 UI primitives.
- `pages`: 현재 app-level page.
- `page`: 과거 page tree; 직접 import가 확인되지 않아 Registry에서 보존 판단을 관리한다.
- `test`: Vitest와 test-debt baseline.
- `package.json`, `vite.config.js`: script/build/test 설정.
- `nginx.conf`, `Dockerfile`: static serving과 Spring reverse proxy image.

공식 Journey는 적격 Concept 3개에서 끝난다. 이후 `journey/*` route는 코드를 보존한 MVP 실험 화면이며 자동 다음 단계가 아니다.

## 6. docs 구조

| Folder | 성격 |
|---|---|
| `architecture` | `SYSTEM_ARCHITECTURE`는 As-Built; 나머지 boundary/deployment 문서는 구현/target 구분 |
| `api` | `/api/v1` 중심 machine-consumed `openapi.yaml` |
| `contracts` | Public As-Is, Internal v1, status/error와 machine-consumed fixtures |
| `example`, `guide` | Frontend predev/prebuild/pretest가 복사하는 DOCX 원본 |
| `governance` | 결정·상태·검증 이력; 현재 기준선과 함께 해석 |
| `maintenance` | 감사, migration cutover, retained legacy registry |
| `migration` | 전환 계획/이력; 실제 Baseline V1이 우선 |
| `operations` | 운영 정책 target/canonical 문서 |
| `product` | 현재 제품 범위와 Journey 결정 |
| `quality` | test strategy, gate와 회귀 정책 |
| `redesign` | AI Journey v0.4 canonical과 로컬 설정 |
| `reference/design` | build/runtime 권위가 아닌 디자인 원본 |
| `uiux` | 현재 route/UX와 reference 적용 규칙 |

## 7. 실행과 테스트

```powershell
Push-Location frontEnd
npm.cmd ci
npm.cmd run lint
npm.cmd run test:baseline
npm.cmd run build
Pop-Location

python docs/contracts/fixtures/internal-ai-v1/validate_fixtures.py
python -m pytest ai/tests

Push-Location backend
.\gradlew.bat test
.\gradlew.bat postgresTest
.\gradlew.bat minioTest
Pop-Location

docker compose up --build
powershell -ExecutionPolicy Bypass -File scripts/docker-e2e-smoke.ps1 -EnvFile .env.e2e.example
```

GitHub Actions는 Frontend, AI, Backend test/postgresTest를 실행한다. `minioTest`, 실제 Provider·법제처와 Docker E2E는 기본 CI에서 실행하지 않는다.

## 8. 빠른 파일 찾기

| 변경 | 먼저 볼 위치 |
|---|---|
| 인증 | `backend/.../auth`, `frontEnd/src/features/auth` |
| Project | `backend/.../project`, `frontEnd/src/features/projects` |
| Idea | `JourneyController`, `JourneyAiService`, `IdeaOriginService`, `features/journey` |
| Legal | `LegalPrecheckController/Service`, `ai/app/legal`, legal models/tests |
| Concept | `ConceptJourneyController/Service`, `journey_provider`, `ConceptJourneyPages` |
| AI Schema | `taskrun/contract`, `ai/app/models`, `docs/contracts/fixtures/internal-ai-v1` |
| Migration | `V1__baseline_schema.sql`; 새 변경은 V2 이상 |
| Frontend Route | `frontEnd/src/app/router/AppRouter.jsx` |
| Docker/env | 네 env example, 세 compose, `LOCAL_RUN.md` |
| 오류 계약 | Spring exception handlers, AI error registry, `STATUS_AND_ERROR_CONTRACT.md` |
