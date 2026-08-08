# V2-8 — Financial Preparation and Snapshot 사용자 검증

## 검증 목표

재무 분석이 독립 모듈과 경로로 표시되고, 현재 `TechOpsInputSnapshot`의 값을 provenance와 함께 승계하는지 확인한다. 없는 재무 세부값만 사용자에게 요청하고, CAC를 서버가 계산하며, 모든 필수 입력이 준비된 뒤에만 immutable `FinancialInputSnapshot`을 만들어 외부 handoff에 전달하는지도 확인한다.

## 준비

1. rebuild baseline을 사용하는 로컬 DB 볼륨을 새로 생성한다.
2. V2-1부터 V2-7까지 정상 완료된 프로젝트를 준비한다.
3. 현재 선택에 대응하는 `MarketAnalysisSeedSnapshot`과 `TechOpsInputSnapshot`을 확정한다.
4. Backend와 Frontend를 실행한다.
5. `/app/projects/{projectId}/finance`를 연다.

## 1. 독립 Route와 Module Status

1. 프로젝트 사이드바에 `7. 재무 분석`이 표시되는지 확인한다.
2. 마케팅이 `8. 마케팅 콘텐츠 제작`으로 표시되는지 확인한다.
3. 프로젝트 개요와 목록이 8단계 파이프라인으로 표시되는지 확인한다.
4. 기존 BM·Persona 화면에 별도 `재무분석` 결과 카드가 더 이상 표시되지 않는지 확인한다.
5. TechOps Snapshot이 없는 프로젝트에서도 Finance 화면 진입 자체는 가능하지만 준비 Action은 막히는지 확인한다.
6. TechOps Snapshot이 있는 프로젝트에서는 Finance status가 `READY` 또는 Preparation 생성 후 `NEEDS_INPUT`인지 확인한다.

## 2. Preparation과 TechOps 승계

화면 진입 시 Preparation이 없으면 다음 API가 자동 실행된다.

```http
POST /api/v3/projects/{projectId}/finance/preparation/initialize
Content-Type: application/json

{}
```

현재 값은 다음 API로 확인한다.

```http
GET /api/v3/projects/{projectId}/finance/preparation
```

응답에서 다음을 확인한다.

- `contract = financial-input-preparation-v1`
- `schemaVersion = 2.0`
- `sourceTechOpsSnapshotId`, `sourceMarketSeedSnapshotId`, `sourceSnapshotHash`
- `upstreamReferences.fixedOperatingCost.label = 기술·운영 단계에서 가져옴`
- `upstreamReferences.initialInvestment`와 `upstreamReferences.threeYearTargets`에 TechOps 원본이 보존됨
- 정확히 일치해 승계된 필드는 `readOnly = true`, `sourceSnapshotId = sourceTechOpsSnapshotId`
- 없는 필드는 `source = USER_INPUT`, `decision = OPEN`, `readOnly = false`
- AI Provider 미연결 도움말은 `source = AI_ESTIMATE`, `decision = PROPOSED`, `proposalValue = null`, `providerStatus = NOT_CONNECTED`

같은 initialize API를 다시 호출했을 때 Preparation ID가 동일한지 확인한다.

## 3. 이미 존재하는 값 재입력 금지

TechOps에 정확한 `annualFixedLaborCost`가 있어 readOnly로 승계된 경우 해당 필드를 PATCH한다.

```http
PATCH /api/v3/projects/{projectId}/finance/preparation
Content-Type: application/json

{
  "values": {
    "annualFixedLaborCost": {"amount": 1, "currency": "KRW"}
  }
}
```

다음을 확인한다.

- `FINANCIAL_INPUT_INVALID`로 거절됨
- 오류 메시지가 기술·운영 단계에서 가져온 값은 다시 입력할 수 없음을 설명함
- 기존 값과 provenance가 바뀌지 않음

TechOps에 총 고정운영비만 있고 세부항목이 없는 일반적인 경우에는 총액이 참고값으로 표시되고 세부항목 입력란은 열린 상태인지 확인한다. 총액이 세부항목으로 임의 배분되지 않아야 한다.

## 4. 필수 재무 입력과 3개년 목표

승계되지 않은 값만 다음과 같이 저장한다. 실제 readOnly 필드는 요청에서 제외한다.

```http
PATCH /api/v3/projects/{projectId}/finance/preparation
Content-Type: application/json

{
  "values": {
    "annualFixedLaborCost": {"amount": 120000000, "currency": "KRW"},
    "annualFixedRentAndManagementCost": {"amount": 24000000, "currency": "KRW"},
    "annualFixedInfrastructureCost": {"amount": 18000000, "currency": "KRW"},
    "initialDevelopmentAndRnDCost": {"amount": 80000000, "currency": "KRW"},
    "initialEquipmentAndInfrastructureCost": {"amount": 20000000, "currency": "KRW"},
    "initialPatentAndLicensingCost": {"amount": 10000000, "currency": "KRW"},
    "threeYearTargets": {
      "metric": "customerCount",
      "unit": "명",
      "years": [
        {"year": 1, "value": 300},
        {"year": 2, "value": 1200},
        {"year": 3, "value": 3000}
      ]
    },
    "totalMarketingCost": {"amount": 10000000, "currency": "KRW"},
    "totalSalesCost": {"amount": 5000000, "currency": "KRW"},
    "newCustomerCount": 300
  }
}
```

확인 사항:

1. 저장한 값은 `source = USER_INPUT`, `decision = LOCKED`가 된다.
2. 새로고침 후 값이 유지된다.
3. 목표 metric은 네 값 중 하나만 허용된다.
   - `salesVolume`
   - `customerCount`
   - `subscriberCount`
   - `transactionCount`
4. 1~3년차가 모두 있고 각 값이 0 이상이어야 한다.
5. 필수값이 명시적 0이면 빈칸과 구분되어 저장된다.

## 5. CAC 시스템 계산

위 예시를 저장한 뒤 Preparation 응답과 화면에서 다음을 확인한다.

- `calculatedCac.amount = 50000.00`
- `calculatedCac.currency = KRW`
- `calculatedCac.formula = (totalMarketingCost + totalSalesCost) / newCustomerCount`
- `calculatedCac.source = SYSTEM_CALCULATION`
- 사용자가 CAC 결과 자체를 입력하는 필드나 API가 없음

다음 오류도 확인한다.

1. `newCustomerCount = 0`이면 Snapshot 확정이 거절된다.
2. 마케팅비와 영업비 통화가 다르면 Snapshot 확정이 거절된다.

## 6. 조건부 단위원가

1. 화면의 `조건부 단위원가 입력`이 기본적으로 접혀 있는지 확인한다.
2. 펼치면 다섯 조건부 항목이 표시되는지 확인한다.
3. 해당 사업에 필요한 항목만 입력하고 나머지는 비운다.
4. 모든 조건부 항목을 비워도 공통 필수값이 완료되면 `readyToFinalize = true`인지 확인한다.

선택 입력 예시:

```http
PATCH /api/v3/projects/{projectId}/finance/preparation
Content-Type: application/json

{
  "values": {
    "paymentFee": {"amount": 350, "currency": "KRW"},
    "customerIncrementalInfraCost": {"amount": 120, "currency": "KRW"}
  }
}
```

## 7. Snapshot Gate와 불변성

필수값이 하나라도 비어 있는 상태에서 아래 API를 호출한다.

```http
POST /api/v3/projects/{projectId}/finance/input-snapshots/finalize
Content-Type: application/json

{}
```

1. `FINANCIAL_SNAPSHOT_NOT_READY`로 거절되는지 확인한다.
2. 모든 필수값과 유효한 CAC 구성값을 완료한 뒤 다시 호출한다.
3. 응답에서 다음을 확인한다.
   - `contract = financial-input-snapshot-v1`
   - `schemaVersion = 2.0`
   - `snapshotId`, `sha256:` hash, `createdAt`
   - `preparationId`, `sourceTechOpsSnapshotId`, `sourceMarketSeedSnapshotId`, `sourceSnapshotHash`
   - `values`, `valueProvenance`, `calculatedCac`, `upstreamReferences`, `assistance`
4. 같은 finalize를 다시 호출하고 ID/hash/createdAt이 동일한지 확인한다.
5. 확정 후 Preparation PATCH가 `FINANCIAL_SNAPSHOT_IMMUTABLE`로 거절되는지 확인한다.

DB 확인:

```sql
select id, project_id, preparation_id, source_tech_ops_snapshot_id,
       source_market_seed_snapshot_id, schema_version, snapshot_hash, finalized_at
from financial_input_snapshots
where project_id = {projectId};
```

## 8. 외부 Handoff 경계

```http
POST /api/v3/projects/{projectId}/module-handoffs
Content-Type: application/json

{
  "module": "FINANCIAL_ANALYSIS",
  "inputSnapshotId": "{financialInputSnapshotId}",
  "requestedOperation": "START_FINANCIAL_ANALYSIS"
}
```

다음을 확인한다.

- `contract = module-handoff-v2`
- `module = FINANCIAL_ANALYSIS`
- `inputSnapshotType = FINANCIAL_INPUT`
- `inputSchemaVersion = 2.0`
- 입력 ID/hash가 현재 `FinancialInputSnapshot`과 동일
- `input`이 Snapshot 본문과 동일
- 내부 Preparation Entity나 TechOps DB table을 직접 읽지 않음
- 외부 adapter가 없는 환경에서는 run status가 `NOT_CONNECTED`
- 같은 Snapshot/hash/operation 재호출은 같은 handoff/run을 반환
- 이전 TechOps Snapshot 기반 재무 run은 새 TechOps Snapshot이 현재가 되면 `STALE`로 표시

## 9. 빠른 자동 검증

Backend:

```powershell
cd backend
.\gradlew.bat test --no-daemon --tests "com.aivle.backend.pipeline.finance.*" --tests "com.aivle.backend.pipeline.techops.TechOpsHandoffTests" --tests "com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests" --tests "com.aivle.backend.pipeline.module.NewPipelineFoundationMigrationTests"
```

Frontend:

```powershell
cd frontEnd
npm.cmd run test:run -- src/features/finance/model/financeModel.test.js src/features/finance/pages/FinancePage.test.jsx src/features/business-persona-integration/pages/BusinessPersonaIntegrationPage.test.jsx src/app/module-status/projectModuleModel.test.js src/app/routing/AppRouter.cutover.test.js src/features/projects/ProjectPages.test.jsx
```

계약 JSON:

```powershell
Get-Content docs/rebuild/contracts/financial-input-snapshot-v1.schema.json -Raw | ConvertFrom-Json | Out-Null
```

## 수용 기준

- TechOps Snapshot 없이 Finance Preparation을 만들 수 없다.
- TechOps에 정확히 존재하는 값은 다시 입력받지 않는다.
- 총액이나 자유 형식 목표를 세부 재무값으로 임의 변환하지 않는다.
- 여섯 필수 비용 세부항목과 구조화 3개년 목표, 세 CAC 구성값이 준비되어야 Snapshot을 확정할 수 있다.
- 조건부 원가는 모든 사업에 강제되지 않는다.
- CAC는 서버가 계산하며 사용자가 결과를 입력하지 않는다.
- AI 미연결 제안은 사용자 사실로 표시되지 않는다.
- `FinancialInputSnapshot`은 재확정 시 동일하고 확정 후 수정할 수 없다.
- 외부 handoff는 `FINANCIAL_INPUT` Snapshot 경계만 사용한다.
- 외부 재무 분석 알고리즘 구현은 이번 수용 범위가 아니다.
