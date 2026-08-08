# V2-7 — TechOps Preparation and Snapshot 사용자 검증

## 검증 목표

기술·운영 분석이 독립 모듈과 경로로 표시되고, 상위 확정값을 다시 입력받지 않으며, 사용자 사실·필수 결정·실제 Evidence가 분리되는지 확인한다. 모든 필수값이 준비된 뒤에만 immutable `TechOpsInputSnapshot`을 만들고 외부 handoff가 이 Snapshot만 전달하는지도 확인한다.

## 준비

1. rebuild baseline을 사용하는 로컬 DB 볼륨을 새로 생성한다.
2. V2-1부터 V2-5까지 정상 완료된 프로젝트를 준비한다.
3. Concept 선택, 필수 가설 결정, Delta Legal Review, `MarketAnalysisSeedSnapshot` 확정을 완료한다.
4. Backend와 Frontend를 실행한다.
5. `/app/projects/{projectId}/tech-ops`를 연다.

## 1. 독립 Route와 Module Status

1. 프로젝트 사이드바에 `6. 기술·운영 분석`이 표시되는지 확인한다.
2. 프로젝트 개요와 목록이 7단계 파이프라인으로 표시되는지 확인한다.
3. Market Seed가 없는 프로젝트에서도 TechOps 화면 진입 자체는 가능하지만 입력 확정 Action은 막히는지 확인한다.
4. Market Seed가 있는 프로젝트에서는 Module status가 `READY` 또는 Preparation 생성 후 `NEEDS_INPUT`인지 확인한다.

## 2. Preparation과 상위 값 승계

화면 진입 시 Preparation이 없으면 자동으로 다음 API가 실행된다.

```http
POST /api/v3/projects/{projectId}/tech-ops/preparation/initialize
Content-Type: application/json

{}
```

현재 값은 다음 API로 확인한다.

```http
GET /api/v3/projects/{projectId}/tech-ops/preparation
```

응답에서 다음을 확인한다.

- `contract = tech-ops-input-preparation-v1`
- `schemaVersion = 2.0`
- `sourceMarketSeedSnapshotId`, `sourceSnapshotHash`
- 제품·서비스 사양의 `source = CONCEPT_GENERATED`
- 제품·서비스 사양의 `decision = ACCEPTED`
- 제품·서비스 사양의 `readOnly = true`
- 미입력 사실은 `decision = OPEN`, `readOnly = false`
- Evidence가 제안 결정 JSON 안에 섞이지 않음

같은 initialize API를 다시 호출했을 때 Preparation ID가 동일한지 확인한다.

## 3. 사용자 필수 사실

1. 목표 출시일을 입력한다.
2. 보유 인력을 `역할|인원|비고` 형식으로 입력한다. 실제 인력이 없으면 `현재 전담 인력 없음|0`으로 명시한다.
3. 보유 자산·설비를 한 줄에 하나씩 입력한다. 없으면 `현재 보유 자산 없음`으로 명시한다.
4. 월 고정운영비와 초기투자금을 입력한다. 값이 0이면 빈칸 대신 명시적으로 `0`을 입력한다.
5. 1~3년차 목표를 모두 입력한다.
6. 사용자 사실 저장을 누른다.
7. 새로고침 후 값이 유지되고 source가 `USER_INPUT`, decision이 `LOCKED`인지 확인한다.

API 예시:

```http
PATCH /api/v3/projects/{projectId}/tech-ops/preparation
Content-Type: application/json

{
  "values": {
    "targetLaunchDate": "2027-03-01",
    "ownedPersonnel": [{"role": "개발", "count": 2, "notes": "내부 인력"}],
    "ownedAssetsAndFacilities": ["클라우드 계정"],
    "fixedOperatingCost": {"amount": 1200000, "currency": "KRW", "period": "MONTHLY"},
    "initialInvestment": {"amount": 30000000, "currency": "KRW"},
    "threeYearTargets": [
      {"year": 1, "target": "유료 고객 100명"},
      {"year": 2, "target": "유료 고객 500명"},
      {"year": 3, "target": "유료 고객 1500명"}
    ]
  }
}
```

## 4. 필수 운영 결정

각 필드에서 다음 동작을 확인한다.

- 상위 제안이 있으면 `제안 채택`
- 값을 직접 바꾸려면 `수정 후 확정`
- 제안을 거절하면 `다른 제안 요청`

월 처리량 예시:

```http
POST /api/v3/projects/{projectId}/tech-ops/preparation/proposals/expectedMonthlyThroughputOrSales/decision
Content-Type: application/json

{
  "action": "EDIT_ACCEPT",
  "value": {"amount": 2500, "unit": "건"}
}
```

확인 사항:

1. 채택은 `ACCEPTED`, 수정 후 확정은 `USER_EDITED_ACCEPTED`가 된다.
2. 거절 후에는 `REJECTED`, `alternativeRequested = true`가 된다.
3. 거절 상태에서는 Snapshot을 확정할 수 없다.
4. 다른 제안 생성기가 연결되지 않은 현재 환경에서는 직접 수정 후 확정하여 막다른 상태를 해소할 수 있다.

## 5. 실제 Evidence 분리

1. 견적서, BOM, 공급사 정보, 사양서, 파일럿 자료 중 하나를 등록한다.
2. 실제 업로드 시스템에서 받은 파일 ID 또는 안전한 artifact reference를 사용한다.
3. 등록 항목의 source가 `USER_PROVIDED_EVIDENCE`인지 확인한다.
4. AI 제안이나 임의 생성 문서를 Evidence로 등록하지 않는다.
5. Snapshot 확정 전 Evidence 삭제가 가능하고 확정 후에는 추가·삭제가 모두 거절되는지 확인한다.

```http
POST /api/v3/projects/{projectId}/tech-ops/preparation/evidence
Content-Type: application/json

{
  "evidenceType": "QUOTE",
  "displayName": "클라우드 운영 견적서",
  "artifactRef": "stored-file:123",
  "description": "2026년 8월 공급사 제공 자료"
}
```

## 6. Snapshot Gate와 불변성

필수값이 하나라도 비어 있는 상태에서 아래 API를 호출한다.

```http
POST /api/v3/projects/{projectId}/tech-ops/input-snapshots/finalize
Content-Type: application/json

{}
```

1. `TECH_OPS_SNAPSHOT_NOT_READY`로 거절되는지 확인한다.
2. 모든 사용자 사실과 운영 결정을 완료한 뒤 다시 호출한다.
3. 응답에서 다음을 확인한다.
   - `contract = tech-ops-input-snapshot-v1`
   - `schemaVersion = 2.0`
   - `snapshotId`, `sha256:` hash, `createdAt`
   - `preparationId`, `sourceMarketSeedSnapshotId`, `sourceSnapshotHash`
   - `requiredFacts`, `requiredFactProvenance`, `requiredDecisions`, `evidenceReferences`
4. 같은 finalize를 다시 호출하고 ID/hash/createdAt이 동일한지 확인한다.
5. 확정 후 Preparation PATCH, 제안 결정, Evidence 추가·삭제가 `TECH_OPS_SNAPSHOT_IMMUTABLE`로 거절되는지 확인한다.

DB 확인:

```sql
select id, project_id, preparation_id, source_market_seed_snapshot_id,
       schema_version, snapshot_hash, finalized_at
from tech_ops_input_snapshots
where project_id = {projectId};
```

## 7. 외부 Handoff 경계

```http
POST /api/v3/projects/{projectId}/module-handoffs
Content-Type: application/json

{
  "module": "TECH_OPS",
  "inputSnapshotId": "{techOpsInputSnapshotId}",
  "requestedOperation": "START_TECH_OPS_ANALYSIS"
}
```

다음을 확인한다.

- `contract = module-handoff-v2`
- `module = TECH_OPS`
- `inputSnapshotType = TECH_OPS_INPUT`
- `inputSchemaVersion = 2.0`
- 입력 ID/hash가 현재 `TechOpsInputSnapshot`과 동일
- `input`이 Snapshot 본문과 동일
- 내부 Preparation Entity나 DB table 구조가 노출되지 않음
- 외부 adapter가 없는 환경에서는 run status가 `NOT_CONNECTED`
- 같은 Snapshot/hash/operation 재호출은 같은 handoff/run을 반환

## 8. 빠른 자동 검증

Backend:

```powershell
cd backend
.\gradlew.bat test --tests "com.aivle.backend.pipeline.techops.*" --tests "com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests" --tests "com.aivle.backend.pipeline.selection.SelectionAndHandoffContractTests"
```

Frontend:

```powershell
cd frontEnd
npm.cmd test -- --run src/features/tech-ops/model/techOpsModel.test.js src/features/tech-ops/hooks/useTechOps.test.jsx src/app/module-status/projectModuleModel.test.js src/app/routing/AppRouter.cutover.test.js src/features/projects/ProjectPages.test.jsx
```

## 통과 기준

- TechOps가 독립 route와 module status를 가진다.
- 상위 확정값은 provenance를 보존하며 다시 입력받지 않는다.
- 사용자 필수 사실과 세 가지 운영 결정이 모두 준비되어야 Snapshot이 생성된다.
- AI/상위 제안과 사용자 제공 Evidence가 분리된다.
- Snapshot은 ID, schemaVersion, hash, createdAt을 가진 immutable 경계다.
- Handoff는 내부 Entity가 아닌 `TechOpsInputSnapshot`만 전달한다.
- 외부 미연결 상태를 분석 완료로 표시하지 않는다.
