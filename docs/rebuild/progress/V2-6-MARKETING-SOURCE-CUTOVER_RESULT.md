# V2-6 — Marketing Source Cutover 결과

## 결과 요약

V2-6 구현을 완료했다. Marketing 콘텐츠의 정식 입력은 더 이상 `FinalizedPlanningSnapshot`이나 Market Result가 아니다. 현재 선택에 대응하는 `MarketAnalysisSeedSnapshot`에서 선택 Concept, 사용자가 최종 수락한 가설, 최종 Legal Result를 추출해 별도의 immutable `MarketingSourceSnapshot`을 확정하고, 콘텐츠 생성·재생성·조회·최종 저장이 이 Snapshot ID와 hash를 기준으로 동작한다.

기존 `MARKETING_CONTENT_GENERATION` TaskRun, TaskAttempt, JobEvent, SSE, polling, retry/recovery 경로는 유지했다. 이번 Unit은 구현 완료 상태이며 실제 브라우저·AI Provider·DB 통합 수용 검증은 사용자 실행이 남아 있다.

## 구현한 계약

### MarketingSourceSnapshot

- 계약 이름: `marketing-source-snapshot-v1`
- schemaVersion: `2.0`
- 경계 식별자: `snapshotId`, `hash`, `createdAt`
- 원본 추적: `marketAnalysisSeedSnapshotId`, `marketAnalysisSeedSnapshotHash`, `selectionId`, `conceptId`
- 선택 Concept: 이름, 대상 사용자, 문제, 가치 제안, 포지셔닝, 핵심 기능
- 최종 가설: 지역, 수익 모델, 가격, 채널, 차별점, 사전 SOM 점유율·금액 가설
- Legal Guard: `allowedClaims`, `prohibitedClaims`, `requiredDisclosures`, `requiredControls`, `communicationRequiredControls`, 공식 근거 참조
- 같은 Market Seed에 대한 재확정은 같은 저장 Snapshot을 반환한다.
- Entity는 생성 이후 공개 mutation 경로를 제공하지 않는다.

### Marketing 콘텐츠 입력 전환

- 생성 요청의 `planningSnapshotId`를 `marketingSourceSnapshotId`로 교체했다.
- 콘텐츠가 보관하는 FK와 source JSON/hash도 `MarketingSourceSnapshot`을 가리킨다.
- 현재 Marketing Source와 ID/hash가 다른 과거 콘텐츠는 `STALE`로 표시한다.
- 재생성은 현재 Marketing Source로 입력을 갱신한다.
- Market Result와 Finalized Planning 조회·Repository·Factory 의존성을 Marketing 활성 경로에서 제거했다.

### Legal Guard

- AI 프롬프트와 strict input schema에 허용 주장, 금지 주장, 필수 고지, 커뮤니케이션 관련 통제를 전달한다.
- AI 생성 완료 직전에 금지 문구와 필수 고지를 서버에서 다시 검사한다.
- 사용자 편집 저장과 최종 저장에도 같은 서버 검사를 적용한다.
- 금지 주장이 포함되거나 필수 고지가 누락되면 저장을 거절한다.
- 비동기 생성에서 차단되면 내부 예외를 노출하지 않고 `MARKETING_PROHIBITED_CLAIM` 안전 코드로 종료 이벤트를 발행한다.

### 사용자 화면

- Marketing 화면은 현재 Source가 없을 때 Market Seed 기반 Marketing Source 확정을 시도한다.
- Source 요약에 선택 Concept, 최종 가설과 Legal Guard 항목을 표시한다.
- Market Result와 Finalized Planning이 필수 조건이 아님을 명시한다.
- 콘텐츠 생성·진행 이벤트·편집·법률 고지 반영·최종 저장 흐름은 유지한다.

## 실제 수정 파일

### Backend

- `backend/src/main/java/com/aivle/backend/common/exception/ErrorCode.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/api/MarketingApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/api/MarketingSourceApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/api/MarketingSourceSnapshotController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingContentCompletionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingContentService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingLegalGuard.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingSourceSnapshotFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingSourceSnapshotService.java`
- 삭제: `backend/src/main/java/com/aivle/backend/pipeline/marketing/application/MarketingSourceSnapshot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/domain/MarketingContent.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/domain/MarketingSourceSnapshot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/repository/MarketingSourceSnapshotRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketing/worker/MarketingContentWorker.java`
- `backend/src/main/java/com/aivle/backend/pipeline/module/ProjectModuleStatusService.java`
- `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`

### AI

- `ai/app/api/executions.py`
- `ai/app/tasks/marketing_content/models.py`
- `ai/app/tasks/marketing_content/prompts/generation.py`
- `ai/app/tools/marketing_content_provider_smoke.py`
- `ai/tests/test_marketing_content_contract.py`

### Frontend

- `frontEnd/src/features/marketing-content/api/marketingSourceApi.js`
- 삭제: `frontEnd/src/features/marketing-content/api/finalizedPlanningApi.js`
- `frontEnd/src/features/marketing-content/components/MarketingContentList.jsx`
- `frontEnd/src/features/marketing-content/components/MarketingSourceSummary.jsx`
- `frontEnd/src/features/marketing-content/hooks/useMarketingContent.js`
- `frontEnd/src/features/marketing-content/hooks/useMarketingContent.test.jsx`
- `frontEnd/src/features/marketing-content/model/marketingContentModel.js`
- `frontEnd/src/features/marketing-content/model/marketingContentModel.test.js`
- `frontEnd/src/features/marketing-content/pages/MarketingContentPage.jsx`
- `frontEnd/src/shared/api/apiError.js`

### 계약과 테스트

- `docs/rebuild/contracts/marketing-content-request-v1.schema.json`
- `docs/rebuild/contracts/marketing-source-snapshot-v1.schema.json`
- `backend/src/test/java/com/aivle/backend/pipeline/marketing/MarketingContentContractsTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketing/MarketingSourceSnapshotServiceTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketing/MarketingSourceV2ContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/module/ProjectModuleStatusServiceTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`

## 실행한 검증

- Backend Marketing·Module Status 대상 테스트: 5개 테스트 클래스, 성공
  - `com.aivle.backend.pipeline.marketing.*`
  - `com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests`
- 최종 변경 후 Backend 계약 재검증: 2개 테스트 클래스, 성공
  - `MarketingContentContractsTests`
  - `MarketingSourceV2ContractTests`
- AI Marketing 계약 테스트: 5개 성공
- 변경 AI 모듈 `py_compile`: 성공
- Frontend Marketing 대상 Vitest: 4개 파일, 5개 테스트 성공
- 변경 Frontend Marketing 파일 대상 ESLint: 성공
- 활성 Marketing backend/frontend/AI 경로에서 `FinalizedPlanningSnapshot`, `planningSnapshotId`, Market Result 의존성 검색: 잔존 없음
- `git diff --check`: 성공

## 의도적으로 생략한 검증

- 전체 Backend 회귀 테스트
- 전체 `postgresTest`와 Testcontainers
- Docker 재빌드 및 브라우저 E2E
- 실제 AI Provider smoke
- Frontend production build
- 외부 Market Analysis 실행

위 항목은 `LOCAL_FAST_EXECUTION_PROFILE.md`와 V2 실행 지시에 따라 이번 Unit에서 실행하지 않았다.

## 미구현 범위

- 외부 Market Analysis 알고리즘과 그 결과의 Marketing 활용
- Marketing A/B 검증 Workspace
- launch strategy validator
- V2-7 TechOps 준비·Snapshot
- 기존 Planning dead code 전체 삭제: Marketing 활성 경로에서만 의존성을 제거했으며 전체 dead-code 정리는 V2-9 범위다.

## 알려진 위험

- baseline migration을 이미 적용한 기존 로컬 DB에는 신규 table/FK가 자동 추가되지 않는다. rebuild 정책대로 DB 볼륨을 재생성해야 한다.
- 실제 AI Provider가 strict `MarketingSourceSnapshot` 입력과 Legal Guard 지시를 준수하는지는 live provider smoke가 필요하다.
- 금지 주장 검사는 정규화된 의미 판정이 아니라 명시 문구 포함 검사다. 우회 표현의 의미적 충돌은 AI의 legal review와 사용자 확인을 함께 거쳐야 한다.
- 실제 브라우저에서 Source 자동 확정, SSE 재연결, stale 재생성 UX는 아직 수용 검증 전이다.

## 정확한 다음 시작점

다음 Unit은 `V2-7 — TECH-OPS-PREPARATION-AND-SNAPSHOT`이다. 기술·운영 준비 폼, upstream 확정값 재사용, 사용자 추가 입력, AI 제안 필드, `TechOpsInputSnapshot`, module status, route/frontend shell, 외부 handoff 경계를 조사하는 지점부터 시작한다. V2-7 구현은 이번 작업에 포함하지 않았다.
