# V2-5 — Market Seed Snapshot and Handoff 사용자 검증

## 검증 목표

Concept 선택과 최종 가설 결정이 완료되기 전에는 Market Seed를 확정할 수 없고, 완료 후에는 하나의 immutable Snapshot만 생성되며, Market Handoff와 Result가 이 Snapshot 경계를 넘지 않는지 확인한다.

## 준비

1. rebuild baseline을 새 DB에 적용한다.
2. V2-1부터 V2-4까지 정상 완료된 프로젝트를 준비한다.
3. 적격 Concept 5개 중 하나를 선택한다.
4. 브라우저에서 `/app/projects/{projectId}/concepts/compare`를 연다.

기존 baseline이 적용된 DB를 계속 사용하는 경우 신규 table이 없으므로 검증하지 말고 로컬 DB 볼륨을 재생성한다.

## 1. Finalize Gate

1. 가설 하나 이상을 미확정 상태로 둔다.
2. `시장분석 Seed 확정` 버튼이 비활성화되는지 확인한다.
3. legal-sensitive 가설을 수정해 Delta Legal Review를 실패시키는 fixture가 있다면 실행한다.
4. 실패한 값이 최종 확정되지 않고 Snapshot 생성도 거부되는지 확인한다.
5. 가설 6개를 모두 채택하거나 수정 후 채택한다.
6. 필요한 Delta Legal Review가 모두 `PASSED`, 변경이 없는 항목은 `NOT_REQUIRED`인지 확인한다.
7. 버튼이 활성화되는지 확인한다.

API로도 확인할 수 있다.

```http
POST /api/v3/projects/{projectId}/market-analysis-seed-snapshots/finalize
```

미완료 상태에서는 `409`와 `HYPOTHESIS_DECISIONS_INCOMPLETE`가 기대 결과다.

## 2. Snapshot 본문과 불변성

1. `시장분석 Seed 확정`을 한 번 누른다.
2. 화면에 Snapshot ID, schemaVersion `2.0`, `sha256:` hash, 생성시각이 표시되는지 확인한다.
3. 아래 API를 호출한다.

```http
GET /api/v3/projects/{projectId}/market-analysis-seed-snapshots/current
```

4. 응답의 `snapshot` 본문에서 다음을 확인한다.
   - `contract = market-analysis-seed-snapshot-v1`
   - `snapshotId`, `schemaVersion`, `hash`, `createdAt`
   - `originalSeed.ideaOverview`
   - `originalSeed.fields.problem`, `targetUsers`, optional LOCKED 값과 각 source/decisionState
   - `aiInterpretation.industryCategory`, `researchScope`, `usageContext`
   - `selectedConcept.identity`, `solution`, `operation`
   - `finalHypotheses.targetRegion`, `revenueModel`, `price`, `channels`, `differentiators`, `preMarketSomShare`, `preMarketSom`
   - `legalResult.legalStatus`, controls, partner/qualification, prohibited variants, disclosures, official Evidence references
5. 같은 POST를 다시 호출한다.
6. Snapshot ID, hash, 생성시각이 모두 첫 응답과 동일한지 확인한다.
7. DB에서 선택당 행이 하나인지 확인한다.

```sql
select id, project_id, selection_id, concept_id, schema_version, snapshot_hash, finalized_at
from market_analysis_seed_snapshots
where project_id = {projectId};
```

## 3. Snapshot-only Market Handoff

1. 확정 화면에서 `시장분석으로 이동`을 누른다.
2. `시장분석 Handoff 준비`를 누른다.
3. Handoff 응답을 확인한다.
   - `contract = module-handoff-v2`
   - `inputSnapshotType = MARKET_ANALYSIS_SEED`
   - `inputSchemaVersion = 2.0`
   - `inputSnapshotId`가 현재 Seed Snapshot ID와 동일
   - `inputSnapshotHash`가 현재 Seed Snapshot hash와 동일
   - `input`이 `market-analysis-seed-snapshot-v1` 본문 그대로임
4. `input`에 예전 `selected-concept-market-input-v1` wrapper나 `planningChangeProposals`가 없는지 확인한다.

Snapshot 확정 전 Handoff POST는 성공하면 안 된다.

```http
POST /api/v3/projects/{projectId}/module-handoffs
Content-Type: application/json

{
  "module": "MARKET_ANALYSIS",
  "requestedOperation": "START_MARKET_ANALYSIS"
}
```

## 4. Market Result 비변경 경계

1. local/test profile에서 Market Result fixture 또는 외부 callback으로 `market-analysis-result-v1` 결과를 수신한다.
2. Result request에 `planningChangeProposals`가 필요하지 않은지 확인한다.
3. 결과 화면에는 요약, 고객/가격·채널 시사점, 경쟁제품, 기준 Snapshot만 표시되는지 확인한다.
4. Planning 변경 제안·채택·부분채택·거절 UI가 없는지 확인한다.
5. 결과 수신 전후 아래 값을 비교한다.
   - `concepts.candidate_json`
   - `concept_hypothesis_decisions.final_value_json`
   - `market_analysis_seed_snapshots.snapshot_json`, `snapshot_hash`
6. 어떤 값도 Market Result 때문에 변경되지 않았는지 확인한다.
7. `/api/v3/projects/{projectId}/planning/...` 및 `/api/v3/projects/{projectId}/planning-change-proposals/...` 활성 사용자 API가 노출되지 않는지 확인한다.

## 5. 빠른 자동 검사

Backend:

```powershell
cd backend
.\gradlew.bat test --tests "com.aivle.backend.pipeline.marketseed.*" --tests "com.aivle.backend.pipeline.selection.SelectionAndHandoffContractTests" --tests "com.aivle.backend.pipeline.integration.MarketResultSchemaTests"
```

Frontend:

```powershell
cd frontend
npm.cmd test -- --run src/features/concept-selection/components/MarketSeedFinalization.test.jsx src/features/concept-selection/components/HypothesisDecisionPanel.test.jsx
```

## 통과 기준

- 모든 Finalize Gate가 지켜진다.
- 같은 선택은 하나의 Snapshot만 반환한다.
- Snapshot에 필수 본문과 불변 식별정보가 있다.
- Market Handoff는 이 Snapshot만 공식 입력으로 사용한다.
- Market Result가 Concept, 가설, Snapshot, Planning을 수정하지 않는다.
- Planning 변경 결정 UI와 활성 API가 노출되지 않는다.
