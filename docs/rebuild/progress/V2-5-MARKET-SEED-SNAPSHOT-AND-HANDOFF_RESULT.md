# V2-5 — Market Seed Snapshot and Handoff 결과

## 결과 요약

V2-5 범위의 정본 입력 경계를 구현했다. Concept 선택, 필수 가설 6개 확정, 필요한 Delta Legal Review 통과, Concept 및 기본 Legal Assessment 적격을 모두 확인한 뒤에만 불변 `MarketAnalysisSeedSnapshot`을 생성한다.

Market Analysis handoff는 더 이상 선택 응답이나 `SelectedConceptSnapshot`을 재조립하지 않는다. 저장된 `market-analysis-seed-snapshot-v1` 본문과 해시만 공식 입력으로 전달한다. Market Result는 분석 사실만 저장하며 Concept, 가설 결정, Planning을 수정하지 않는다.

## 구현한 계약

### Snapshot 생성 Gate

- 현재 Concept 선택이 없으면 생성을 거부한다.
- 선택 Concept가 공개 가능한 Legal 상태가 아니면 생성을 거부한다.
- `REVENUE_MODEL`, `PRICE`, `CHANNELS`, `DIFFERENTIATORS`, `PRE_MARKET_SOM_SHARE`, `PRE_MARKET_SOM`의 최신 결정이 모두 최종 수락 상태이고 `finalValue`가 있어야 한다.
- legal-sensitive 결정의 Delta Legal 상태가 `PENDING` 또는 `FAILED`이면 생성을 거부한다.
- 기본 Concept Legal Assessment도 공개 가능한 상태여야 한다.
- 같은 선택에 대한 재요청은 새 Snapshot을 만들지 않고 기존 Snapshot을 그대로 반환한다.

### 불변 Snapshot

- 별도 Entity/table `market_analysis_seed_snapshots`를 추가했다.
- 선택당 Snapshot을 하나만 허용하는 unique constraint를 추가했다.
- 본문 필수 식별자는 `snapshotId`, `schemaVersion: 2.0`, `hash`, `createdAt`이다.
- `hash`는 `hash` 필드 삽입 전 canonical JSON을 SHA-256으로 계산한다.
- 본문에는 다음을 포함한다.
  - 사용자 원본 Seed와 LOCKED 값 및 source/decisionState
  - 확정 AI Interpretation
  - 선택 Concept identity/solution/operation 및 value semantics
  - targetRegion과 최종 가설 6개, source/decision/legal review 상태
  - Legal status, controls, partner/qualification, prohibited variants, disclosures
  - 기본 Legal Review 공식 Evidence reference와 Delta Legal Review reference
- 공개 mutation method를 제공하지 않고 생성 후 수정 경로를 두지 않았다.

### Market Handoff와 Result 경계

- Handoff contract를 `module-handoff-v2`로 정렬했다.
- Market 입력 contract는 `market-analysis-seed-snapshot-v1`, type은 `MARKET_ANALYSIS_SEED`, schemaVersion은 `2.0`이다.
- Handoff의 입력 JSON은 저장된 Snapshot JSON 그대로이며 별도 선택 DTO로 재조립하지 않는다.
- Market Result request/response와 JSON Schema에서 `planningChangeProposals`를 제거했다.
- Market Result 수신 서비스에서 Planning proposal 저장 및 결정 동작을 제거했다.
- 사용자용 Planning 결정 Controller와 Market 화면의 Planning revision UI 연결을 제거했다.
- Market 결과 화면은 “분석 결과가 Concept/가설을 자동 변경하지 않는다”는 경계를 명시한다.

### 사용자 화면

- 가설 결정 화면에 `시장분석 Seed 확정` Gate를 추가했다.
- 모든 결정이 완료되기 전에는 확정 버튼을 비활성화한다.
- 확정 후 Snapshot ID, schemaVersion, hash, 생성시각을 표시하고 Market 화면으로 이동할 수 있다.
- Market 화면은 현재 `MarketAnalysisSeedSnapshot`을 직접 조회해 Handoff를 준비한다.

## 변경 파일

### Backend

- `backend/src/main/java/com/aivle/backend/pipeline/marketseed/domain/MarketAnalysisSeedSnapshot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketseed/repository/MarketAnalysisSeedSnapshotRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketseed/application/MarketAnalysisSeedSnapshotFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketseed/application/MarketAnalysisSeedSnapshotService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketseed/api/MarketAnalysisSeedApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketseed/api/MarketAnalysisSeedSnapshotController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/application/ModuleIntegrationService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/application/MarketResultIntakeService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/api/IntegrationApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/api/MarketResultApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/api/MarketResultController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/module/ProjectModuleStatusService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/application/ConceptSelectionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/application/SnapshotHasher.java`
- 삭제: `backend/src/main/java/com/aivle/backend/pipeline/planning/api/PlanningController.java`
- `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`

### Frontend

- `frontend/src/features/concept-selection/api/conceptSelectionApi.js`
- `frontend/src/features/concept-selection/hooks/useConceptSelection.js`
- `frontend/src/features/concept-selection/components/MarketSeedFinalization.jsx`
- `frontend/src/features/concept-selection/components/MarketSeedFinalization.test.jsx`
- `frontend/src/features/concept-selection/pages/ConceptComparisonPage.jsx`
- `frontend/src/features/concept-selection/styles/concept-selection.css`
- `frontend/src/features/market-integration/api/marketIntegrationApi.js`
- `frontend/src/features/market-integration/hooks/useMarketIntegration.js`
- `frontend/src/features/market-integration/pages/MarketIntegrationPage.jsx`

### 계약과 테스트

- `docs/rebuild/contracts/market-analysis-seed-snapshot-v1.schema.json`
- `docs/rebuild/contracts/market-analysis-result-v1.schema.json`
- 삭제: `docs/rebuild/contracts/selected-concept-market-input-v1.schema.json`
- `backend/src/test/java/com/aivle/backend/pipeline/marketseed/MarketAnalysisSeedSnapshotFactoryTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketseed/MarketAnalysisSeedSnapshotServiceTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketseed/MarketAnalysisSeedV2ContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/marketseed/MarketAnalysisSeedSqlContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/selection/SelectionAndHandoffContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/integration/MarketResultSchemaTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/module/ProjectModuleStatusServiceTests.java`

## 실제 실행한 검사

- Backend `compileJava`: 성공
- Backend V2-5 관련 targeted tests: 6 files, 11 tests 성공
  - `com.aivle.backend.pipeline.marketseed.*`
  - `com.aivle.backend.pipeline.selection.SelectionAndHandoffContractTests`
  - `com.aivle.backend.pipeline.integration.MarketResultSchemaTests`
- Frontend targeted tests: 2 files, 4 tests 성공
  - `MarketSeedFinalization.test.jsx`
  - `HypothesisDecisionPanel.test.jsx`
- 변경한 Frontend 파일 대상 ESLint: 성공
- `git diff --check`: 성공. 기존 V2 작업 파일의 LF→CRLF 안내만 출력됐고 whitespace 오류는 없었다.

## 의도적으로 생략한 검사

- 전체 Backend regression suite
- `postgresTest`와 Testcontainers
- Docker, 브라우저, 외부 Provider smoke
- Frontend production build
- 실제 외부 Market Analysis 알고리즘 실행

위 항목은 `LOCAL_FAST_EXECUTION_PROFILE.md`에 따라 V2-5에서 실행하지 않았다.

## 남은 위험

- 기존 baseline migration을 이미 적용한 로컬 DB는 신규 table/FK 변경을 자동 반영하지 않는다. 현재 rebuild 환경은 baseline 재생성을 전제로 하므로 기존 볼륨은 사용자 검증 전에 재생성해야 한다.
- 과거 Planning Entity/service와 사용자 미노출 frontend 모듈 파일은 R7 dead-code cleanup 전까지 내부에 남아 있다. 활성 Controller, Market Result 저장 경로, Market 화면 연결은 제거했다.
- 실제 외부 Market 모듈과 callback 서명 검증은 이번 Unit의 범위가 아니다.

## 정확한 다음 시작점

다음 작업은 `V2-6 — MARKETING-SOURCE-CUTOVER`이다. `Selected Concept + final accepted hypotheses + Legal Result`를 기반으로 별도 immutable `MarketingSourceSnapshot`을 만들고 `FinalizedPlanningSnapshot` 필수 의존성을 제거하는 지점부터 시작한다. V2-6 구현은 이번 작업에 포함하지 않았다.
