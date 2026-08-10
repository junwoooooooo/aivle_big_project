# V2-7 — TechOps Preparation and Snapshot 결과

## 결과 요약

V2-7 구현을 완료했다. 프로젝트 파이프라인에 독립적인 `기술·운영 분석` 모듈과 `/tech-ops` 경로를 추가하고, 분석 직전 입력을 준비하는 `TechOpsInputPreparation`, 사용자 제공 근거 자료, immutable `TechOpsInputSnapshot`을 별도 도메인으로 구현했다.

현재 `MarketAnalysisSeedSnapshot`의 선택 Concept·운영 구조·Legal 통제 중 신뢰할 수 있는 확정값만 provenance와 함께 승계한다. 추가 사용자 사실과 필수 운영 결정이 모두 준비되어야 Snapshot을 확정할 수 있다. 외부 분석에는 내부 Entity가 아니라 Snapshot 본문·ID·hash·schemaVersion만 공통 handoff envelope로 전달한다.

이번 Unit은 구현 완료 상태이며 실제 DB·브라우저·외부 TechOps 모듈 수용 검증은 사용자 실행이 남아 있다.

## 구현한 계약

### TechOpsInputPreparation

- 현재 Concept 선택에 대응하는 `MarketAnalysisSeedSnapshot`마다 Preparation 하나를 만든다.
- 상위 Snapshot의 제품·서비스 사양을 `CONCEPT_GENERATED + ACCEPTED`로 승계하고 읽기 전용으로 표시한다.
- 상위 입력에 동일 구조로 확정된 TechOps 값이 있는 경우에만 자동 승계한다. 의미가 다른 일반 제약값을 억지로 변환하지 않는다.
- 다음 사용자 사실을 준비한다.
  - `productServiceSpecification`
  - `targetLaunchDate`
  - `ownedPersonnel`
  - `ownedAssetsAndFacilities`
  - `fixedOperatingCost`
  - `initialInvestment`
  - `threeYearTargets`
- 비용은 금액·통화 구조로, 인력은 역할·인원 수 구조로, 3개년 목표는 1~3년차 값으로 검증한다.

### 필수 운영 결정

- 다음 필드를 별도 제안/결정 상태로 보존한다.
  - `deliveryOrProductionMethod`
  - `expectedMonthlyThroughputOrSales`
  - `technicalSupplyOperationalConstraints`
- 상위 Concept 운영 구조와 Legal 통제에서 재사용 가능한 값은 원래 source를 보존한 `PROPOSED` 값으로 제공한다.
- 사용자는 `ACCEPTED`, `USER_EDITED_ACCEPTED`, `REJECTED + alternativeRequested`를 결정할 수 있다.
- AI 대안 생성기를 이번 Unit에서 새로 구현하지 않았다. 대안 요청 상태를 보존하며 직접 수정 후 확정하는 경로를 제공한다.

### Evidence 분리

- 견적서, BOM, 공급사 정보, 사양서, 파일럿 자료만 optional Evidence reference로 등록한다.
- Evidence는 `tech_ops_evidence_references`에 Preparation/제안 JSON과 분리 저장한다.
- 모든 항목은 `USER_PROVIDED_EVIDENCE` source와 제공 사용자 ID를 가진다.
- AI 제안이나 상위 단계 가설을 Evidence로 저장하지 않는다.

### Immutable TechOpsInputSnapshot

- 계약 이름: `tech-ops-input-snapshot-v1`
- schemaVersion: `2.0`
- 경계 식별자: `snapshotId`, `hash`, `createdAt`
- 원본 추적: `preparationId`, `sourceMarketSeedSnapshotId`, `sourceSnapshotHash`
- 최종 사용자 사실, 각 사실의 provenance, 채택된 필수 결정, 사용자 Evidence reference를 포함한다.
- Snapshot hash는 hash 필드 삽입 전 canonical JSON의 SHA-256으로 계산한다.
- 같은 Preparation 재확정은 같은 Snapshot을 반환한다.
- Snapshot 확정 이후 Preparation·결정·Evidence 수정은 거절한다.

### 외부 Handoff와 Module Status

- `ModuleType.TECH_OPS`와 `PipelineModuleType.TECH_OPS`를 추가했다.
- `module-handoff-v2`에서 `inputSnapshotType = TECH_OPS_INPUT`, `inputSchemaVersion = 2.0`을 사용한다.
- `module + inputSnapshotHash + requestedOperation` idempotency 규칙을 재사용한다.
- 외부 adapter가 없으면 기존 공통 규칙대로 `NOT_CONNECTED`를 반환한다.
- Module status는 Market Seed 전에는 `NOT_READY`, Preparation 시작 전에는 `READY`, 입력 미완료는 `NEEDS_INPUT`, Snapshot 확정 후 미연결은 `NOT_CONNECTED`, 실행 중·완료·실패·stale은 공통 외부 상태로 표시한다.

### 사용자 화면

- `/app/projects/{projectId}/tech-ops` 경로를 추가했다.
- 프로젝트 내비게이션과 개요를 7단계 파이프라인으로 갱신했다.
- 상위 승계값, 사용자 사실, 제안 결정, 사용자 제공 Evidence, Snapshot/Handoff 상태를 별도 영역으로 표시한다.
- Snapshot 확정 후 모든 입력과 Evidence Action을 잠근다.

## 실제 수정 파일

### Backend 신규 TechOps 도메인

- `backend/src/main/java/com/aivle/backend/pipeline/techops/api/TechOpsApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/api/TechOpsController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/application/TechOpsPreparationFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/application/TechOpsReadiness.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/application/TechOpsInputSnapshotFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/application/TechOpsService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/domain/TechOpsInputPreparation.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/domain/TechOpsEvidenceReference.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/domain/TechOpsInputSnapshot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/repository/TechOpsInputPreparationRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/repository/TechOpsEvidenceReferenceRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/techops/repository/TechOpsInputSnapshotRepository.java`

### Backend 공통 경계

- `backend/src/main/java/com/aivle/backend/common/exception/ErrorCode.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/application/ModuleIntegrationService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/domain/ModuleType.java`
- `backend/src/main/java/com/aivle/backend/pipeline/module/PipelineModuleType.java`
- `backend/src/main/java/com/aivle/backend/pipeline/module/ProjectModuleStatusService.java`
- `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`

### Frontend

- `frontEnd/src/features/tech-ops/api/techOpsApi.js`
- `frontEnd/src/features/tech-ops/hooks/useTechOps.js`
- `frontEnd/src/features/tech-ops/model/techOpsModel.js`
- `frontEnd/src/features/tech-ops/pages/TechOpsPage.jsx`
- `frontEnd/src/features/tech-ops/styles/tech-ops.css`
- `frontEnd/src/features/tech-ops/index.js`
- `frontEnd/src/app/routing/AppRouter.jsx`
- `frontEnd/src/app/routing/projectRoutes.js`
- `frontEnd/src/app/module-status/projectModuleModel.js`
- `frontEnd/src/app/project-shell/ProjectModulePages.jsx`
- `frontEnd/src/features/projects/WorkspaceHomePage.jsx`
- `frontEnd/src/features/projects/ProjectPages.jsx`
- `frontEnd/src/features/projects/components/ProjectRow.jsx`
- `frontEnd/src/features/projects/model/projectViewModel.js`
- `frontEnd/src/shared/api/apiError.js`

### 계약과 테스트

- `docs/rebuild/contracts/tech-ops-input-snapshot-v1.schema.json`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsPreparationContractsTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsV2ContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsHandoffTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/module/ProjectModuleStatusServiceTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`
- `frontEnd/src/features/tech-ops/model/techOpsModel.test.js`
- `frontEnd/src/features/tech-ops/hooks/useTechOps.test.jsx`
- `frontEnd/src/app/module-status/projectModuleModel.test.js`
- `frontEnd/src/app/routing/AppRouter.cutover.test.js`
- `frontEnd/src/features/projects/ProjectPages.test.jsx`

## 실행한 검증

- Backend `compileJava`, `compileTestJava`: 성공
- Backend V2-7 대상 테스트: 5개 클래스, 13개 테스트 성공
  - TechOps Preparation/Snapshot 계약 3개
  - TechOps schema/SQL/module 계약 2개
  - TechOps immutable handoff 계약 1개
  - Project module status 4개
  - 공통 Snapshot/hash/handoff 계약 3개
- Frontend 대상 Vitest: 5개 파일, 20개 테스트 성공
- 변경 Frontend 파일 대상 ESLint: 성공
- `tech-ops-input-snapshot-v1.schema.json` JSON parse: 성공
- TechOps 활성 경로의 Planning/Finalized Planning 의존성 검색: 잔존 없음
- 활성 Frontend의 과거 6단계 문구 검색: 잔존 없음
- `git diff --check`: 성공

## 의도적으로 생략한 검증

- 전체 Backend 회귀 테스트
- 전체 `postgresTest`와 Testcontainers
- Docker 재빌드 및 브라우저 E2E
- 외부 TechOps adapter/provider smoke
- Frontend production build
- 외부 기술·운영 분석 알고리즘 실행

위 항목은 `LOCAL_FAST_EXECUTION_PROFILE.md`와 V2 실행 지시에 따라 이번 Unit에서 실행하지 않았다.

## 미구현 범위

- 외부 TechOps 분석 알고리즘
- AI 대안 제안 생성 Provider
- 실제 파일 업로드 UI와 파일 내용 분석
- V2-8 Financial Preparation/Snapshot
- 기존 BM·재무·Persona 통합 Shell의 분리와 오래된 Planning 코드 정리

## 알려진 위험

- baseline migration을 이미 적용한 로컬 DB에는 신규 TechOps table과 module check 변경이 자동 반영되지 않는다. rebuild DB 볼륨을 재생성해야 한다.
- Evidence 입력은 실제 업로드가 완료된 파일 ID 또는 안전한 자료 참조를 연결하는 metadata 경계다. 이번 Unit에서는 artifact reference의 저장소 소유권과 파일 내용 분석을 추가하지 않았다.
- 월 처리량/판매량에 자동 생성된 AI 제안이 없으면 사용자가 직접 값을 입력해 확정해야 한다. 대안 요청 상태는 보존하지만 외부 제안 생성기는 아직 연결되지 않았다.
- 외부 TechOps adapter가 없으므로 handoff 후 상태는 `NOT_CONNECTED`가 정상이다.
- 실제 브라우저의 반응형 레이아웃, 키보드 이동, DB 재생성 후 전체 API 흐름은 수용 검증 전이다.

## 정확한 다음 시작점

다음 Unit은 `V2-8 — FINANCIAL-PREPARATION-AND-SNAPSHOT`이다. `TechOpsInputSnapshot`에서 고정운영비·초기투자·3개년 목표를 provenance와 함께 승계하고, 없는 재무 상세값·CAC 구성값·조건부 원가만 요청하는 `FinancialInputPreparation` 설계부터 시작한다. V2-8 구현은 이번 작업에 포함하지 않았다.
