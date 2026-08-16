# V2-8 — Financial Preparation and Snapshot 결과

## 결과 요약

V2-8 구현을 완료했다. 프로젝트 파이프라인에 독립적인 `재무 분석` 모듈과 `/finance` 경로를 추가하고, 현재 `TechOpsInputSnapshot`을 기준으로 하는 `FinancialInputPreparation`, 서버 계산 CAC, immutable `FinancialInputSnapshot`을 별도 도메인으로 구현했다.

TechOps에 정확히 존재하는 재무 세부값은 `source`, `decision`, `sourceSnapshotId`, `provenance`와 함께 읽기 전용으로 승계한다. TechOps가 총액이나 자유 형식 목표만 제공해 세부항목으로 안전하게 분해할 수 없는 경우에는 원본을 “기술·운영 단계에서 가져옴” 참고값으로 표시하고, 분석에 필요한 세부값만 사용자에게 요청한다. 시스템은 상위 값을 임의 배분하거나 목표 수치를 추정하지 않는다.

이번 Unit은 구현 완료 상태이며 실제 DB·브라우저·외부 재무 분석 모듈 수용 검증은 사용자 실행이 남아 있다.

## 구현한 계약

### FinancialInputPreparation

- 현재 `TechOpsInputSnapshot`마다 Preparation 하나를 만든다.
- 원본 경계를 다음 값으로 고정한다.
  - `sourceTechOpsSnapshotId`
  - `sourceMarketSeedSnapshotId`
  - `sourceSnapshotHash`
- 모든 재무 필드는 `value`, `source`, `decision`, `readOnly`, `sourceSnapshotId`, `provenance`를 보존한다.
- 정확히 승계할 수 있는 값은 읽기 전용이며 PATCH에서 재입력을 거절한다.
- 없는 값은 `USER_INPUT + OPEN + readOnly=false`로 시작하고 사용자 저장 후 `LOCKED`가 된다.
- 같은 TechOps Snapshot에 initialize를 반복하면 기존 Preparation을 반환한다.

### 고정운영비와 초기투자

필수 고정운영비 세부항목:

- `annualFixedLaborCost`
- `annualFixedRentAndManagementCost`
- `annualFixedInfrastructureCost`

필수 초기투자 세부항목:

- `initialDevelopmentAndRnDCost`
- `initialEquipmentAndInfrastructureCost`
- `initialPatentAndLicensingCost`

TechOps의 `fixedOperatingCost`, `initialInvestment` 원본과 provenance를 `upstreamReferences`에 그대로 보존한다. 월 고정운영비는 원본을 바꾸지 않고 `SYSTEM_CALCULATION`인 연간 환산 참고값만 함께 표시한다. 총액을 세부항목에 임의 배분하지 않는다.

### 3개년 목표

- 지원 metric은 `salesVolume`, `customerCount`, `subscriberCount`, `transactionCount`다.
- metric, 표시 단위, 1~3년차의 0 이상 목표값을 구조화한다.
- TechOps 목표가 이미 같은 구조이면 그대로 승계한다.
- TechOps 목표가 자유 형식 문자열이면 참고값으로만 보여 주고 사용자가 metric과 수치를 확정한다.

### CAC 구성값과 시스템 계산

사용자 필수 입력:

- `totalMarketingCost`
- `totalSalesCost`
- `newCustomerCount`

서버가 다음 공식으로 `calculatedCac`를 계산한다.

```text
(totalMarketingCost + totalSalesCost) / newCustomerCount
```

- 마케팅비와 영업비 통화가 같아야 한다.
- 신규 고객 수는 1 이상의 정수여야 한다.
- 결과에는 `source = SYSTEM_CALCULATION`과 고정된 formula를 기록한다.
- 사용자가 CAC 결과 자체를 입력하거나 덮어쓰는 API는 없다.

### 조건부 단위원가

다음 값은 선택 입력이며 Snapshot Gate의 공통 필수값이 아니다.

- `unitVariableCost`
- `paymentFee`
- `partnerPayout`
- `shippingCost`
- `customerIncrementalInfraCost`

화면에서는 접힌 “조건부 단위원가 입력” 영역에 배치해 사업 구조나 외부 계약에 필요한 경우에만 사용하게 했다.

### AI 설명과 제안 경계

- 고정비, 초기투자, 3개년 목표, CAC, 조건부 원가 그룹마다 한국어 설명과 예시를 제공한다.
- AI Provider가 연결되지 않은 현재 상태의 제안은 `source = AI_ESTIMATE`, `decision = PROPOSED`, `proposalValue = null`, `providerStatus = NOT_CONNECTED`로 정직하게 표시한다.
- null 제안을 사용자 사실로 전환하거나 Snapshot의 재무값으로 사용하지 않는다.
- 실제 AI 추정값의 생성·채택 API는 외부 Provider 연결 범위로 남겼다.

### Immutable FinancialInputSnapshot

- 계약 이름: `financial-input-snapshot-v1`
- schemaVersion: `2.0`
- 경계 식별자: `snapshotId`, `hash`, `createdAt`
- 원본 추적: `preparationId`, `sourceTechOpsSnapshotId`, `sourceMarketSeedSnapshotId`, `sourceSnapshotHash`
- `values`, `valueProvenance`, `calculatedCac`, `upstreamReferences`, `assistance`를 포함한다.
- Snapshot hash는 hash 필드 삽입 전 canonical JSON의 SHA-256으로 계산한다.
- 같은 Preparation 재확정은 같은 Snapshot을 반환한다.
- Snapshot 확정 이후 Preparation PATCH를 거절한다.

### 외부 Handoff와 Module Status

- `ModuleType.FINANCIAL_ANALYSIS`와 `PipelineModuleType.FINANCE`를 추가했다.
- `module-handoff-v2`에서 `inputSnapshotType = FINANCIAL_INPUT`, `inputSchemaVersion = 2.0`을 사용한다.
- 외부 재무 모듈에는 현재 `FinancialInputSnapshot` 본문·ID·hash만 전달한다.
- `module + inputSnapshotHash + requestedOperation` idempotency 규칙을 재사용한다.
- Module status는 TechOps Snapshot 전 `NOT_READY`, Preparation 시작 전 `READY`, 필수 입력 미완료 `NEEDS_INPUT`, Snapshot 확정 후 외부 adapter 미연결 `NOT_CONNECTED`다.
- 새 Financial Snapshot이 현재 TechOps Snapshot과 다르면 기존 run을 `STALE`로 판단한다.

### 사용자 화면과 활성 Shell 분리

- `/app/projects/{projectId}/finance` 경로를 추가했다.
- 프로젝트 내비게이션과 개요를 8단계 파이프라인으로 갱신했다.
- 기존 BM·재무·Persona 통합 화면에서 재무 결과 영역과 재무 문구를 제거했다. 기존 `BUSINESS_FINANCIAL` 내부 타입은 V2-9 정리 전 BM 호환 경계로 유지하지만 사용자에게 재무 화면으로 노출하지 않는다.
- `/finance`는 승계 참고값, 필수 세부입력, 목표 metric, CAC 구성값/계산 결과, 접힌 조건부 원가, AI 도움말, Snapshot/Handoff 상태를 독립 표시한다.

## 실제 수정 파일

### Backend 신규 Finance 도메인

- `backend/src/main/java/com/aivle/backend/pipeline/finance/api/FinancialApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/api/FinancialController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialPreparationFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialReadiness.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialCalculator.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialInputSnapshotFactory.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/application/FinancialService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/domain/FinancialInputPreparation.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/domain/FinancialInputSnapshot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/repository/FinancialInputPreparationRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/finance/repository/FinancialInputSnapshotRepository.java`

### Backend 공통 경계

- `backend/src/main/java/com/aivle/backend/common/exception/ErrorCode.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/application/ModuleIntegrationService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/domain/ModuleType.java`
- `backend/src/main/java/com/aivle/backend/pipeline/module/PipelineModuleType.java`
- `backend/src/main/java/com/aivle/backend/pipeline/module/ProjectModuleStatusService.java`
- `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`

### Frontend

- `frontEnd/src/features/finance/api/financeApi.js`
- `frontEnd/src/features/finance/hooks/useFinance.js`
- `frontEnd/src/features/finance/model/financeModel.js`
- `frontEnd/src/features/finance/pages/FinancePage.jsx`
- `frontEnd/src/features/finance/styles/finance.css`
- `frontEnd/src/features/finance/index.js`
- `frontEnd/src/app/routing/AppRouter.jsx`
- `frontEnd/src/app/routing/projectRoutes.js`
- `frontEnd/src/app/module-status/projectModuleModel.js`
- `frontEnd/src/app/project-shell/ProjectModulePages.jsx`
- `frontEnd/src/features/business-persona-integration/pages/BusinessPersonaIntegrationPage.jsx`
- `frontEnd/src/features/projects/WorkspaceHomePage.jsx`
- `frontEnd/src/features/projects/ProjectPages.jsx`
- `frontEnd/src/features/projects/components/ProjectRow.jsx`
- `frontEnd/src/features/projects/model/projectViewModel.js`

### 계약과 테스트

- `docs/rebuild/contracts/financial-input-snapshot-v1.schema.json`
- `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialPreparationContractsTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialV2ContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/finance/FinancialHandoffTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/techops/TechOpsHandoffTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/module/ProjectModuleStatusServiceTests.java`
- `frontEnd/src/features/finance/model/financeModel.test.js`
- `frontEnd/src/features/finance/pages/FinancePage.test.jsx`
- `frontEnd/src/features/business-persona-integration/pages/BusinessPersonaIntegrationPage.test.jsx`
- `frontEnd/src/app/module-status/projectModuleModel.test.js`
- `frontEnd/src/app/routing/AppRouter.cutover.test.js`
- `frontEnd/src/features/projects/ProjectPages.test.jsx`

## 실행한 검증

- Backend `compileJava`: 성공
- Backend V2-8 직접 관련 테스트: 6개 클래스, 13개 테스트 성공
  - Financial Preparation/승계/CAC/Snapshot 계약 3개
  - Financial schema/SQL/module/불변성 계약 2개
  - Financial handoff 계약 1개
  - 기존 TechOps handoff 계약 1개
  - Project module status 계약 5개
  - 기초 migration 안전성 계약 1개
- Frontend 대상 Vitest: 6개 파일, 22개 테스트 성공
- 변경 Frontend 파일 대상 ESLint: 성공
- `financial-input-snapshot-v1.schema.json` JSON parse: 성공
- 활성 Frontend의 과거 7단계·BM/재무 통합 노출 문구 검색: 잔존 없음
- Finance 활성 경로의 `FINANCIAL_ANALYSIS`, `FINANCIAL_INPUT`, `/finance` 연결 검색: 성공
- `git diff --check`: 성공
- Gradle 배포본 최초 조회는 sandbox 네트워크 제한으로 실패했으며 승인된 동일 명령 재실행 후 컴파일·테스트가 성공했다.

## 의도적으로 생략한 검증

- 전체 Backend 회귀 테스트
- 전체 `postgresTest`와 Testcontainers
- Docker 재빌드 및 브라우저 E2E
- 외부 재무 분석 adapter/provider smoke
- Frontend production build
- 외부 재무 계산 알고리즘 실행

위 항목은 `LOCAL_FAST_EXECUTION_PROFILE.md`와 V2 실행 지시에 따라 이번 Unit에서 실행하지 않았다.

## 미구현 범위

- 외부 재무 분석 알고리즘
- 실제 AI 재무 추정 Provider와 제안 채택 workflow
- 다통화 환산과 환율 Provider
- 사업 유형을 자동 판정해 조건부 원가 영역을 자동 전개하는 규칙
- V2-9 legacy cleanup

## 알려진 위험

- baseline migration을 이미 적용한 로컬 DB에는 신규 Finance table과 module check 변경이 자동 반영되지 않는다. rebuild DB 볼륨을 재생성해야 한다.
- V2-7 기본 TechOps 목표는 자유 형식 3개년 문자열이므로 Finance가 이를 숫자 metric으로 임의 변환하지 않는다. 원본은 참고값으로 승계되고 사용자가 구조화 목표를 확정해야 한다.
- TechOps에 고정비·초기투자 총액만 있으면 구성항목으로 임의 배분하지 않는다. 총액은 참고 표시되고 여섯 개 세부항목은 추가 입력 대상이다.
- Frontend 입력 통화는 현재 KRW로 고정되어 있다. Backend 계약은 통화 코드를 보존하지만 다통화 UX와 환산은 없다.
- AI Provider가 없으므로 도움말은 표시되지만 실제 추정 제안은 생성되지 않는다.
- 외부 Financial adapter가 없으므로 handoff 후 상태는 `NOT_CONNECTED`가 정상이다.
- 실제 브라우저 반응형 레이아웃, 키보드 이동, DB 재생성 후 전체 API 흐름은 수용 검증 전이다.

## 정확한 다음 시작점

다음 Unit은 `V2-9 — LEGACY-CLEANUP-AND-CUTOVER-VERIFICATION`이다. 활성 route·controller·navigation에서 남아 있는 legacy Planning, Journey, BM·재무 통합 명칭과 죽은 내부 코드를 먼저 inventory하고, authoritative V2 계약이 참조하지 않는 legacy 노출을 제거하는 작업부터 시작한다. V2-9 구현은 이번 작업에 포함하지 않았다.
