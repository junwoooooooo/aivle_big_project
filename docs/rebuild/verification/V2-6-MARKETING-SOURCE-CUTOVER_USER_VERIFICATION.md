# V2-6 — Marketing Source Cutover 사용자 검증

## 검증 목표

Marketing 콘텐츠가 `선택 Concept + 최종 수락 가설 + Legal Result`만으로 생성되며 Market Result나 Finalized Planning을 기다리지 않는지 확인한다. 또한 생성, 사용자 편집, 최종 저장 세 경로에서 Legal Guard가 유지되고 기존 TaskRun/SSE 복구가 동작하는지 확인한다.

## 준비

1. rebuild baseline을 사용하는 로컬 DB 볼륨을 새로 생성한다. 기존 baseline 적용 DB를 그대로 사용하면 `marketing_source_snapshots` table과 새 FK가 없다.
2. V2-1부터 V2-5까지 정상 완료된 프로젝트를 준비한다.
3. 공개 가능한 Concept 하나를 선택한다.
4. 선택 Concept의 필수 가설 6개를 모두 채택하거나 수정 후 채택한다.
5. 필요한 Delta Legal Review가 모두 통과한 뒤 `MarketAnalysisSeedSnapshot`을 확정한다.
6. Market Analysis 결과와 Finalized Planning은 만들지 않는다.
7. Backend, Frontend, AI 서비스를 실행하고 `/app/projects/{projectId}/marketing`을 연다.

## 1. Marketing Source 자동 확정

1. Marketing 화면을 처음 연다.
2. 화면이 현재 Source를 조회하고, 없다면 Market Seed로 Source를 자동 확정하는지 확인한다.
3. 아래 API로도 확인한다.

```http
GET /api/v3/projects/{projectId}/marketing-source-snapshots/current
```

필요하면 직접 확정할 수 있다.

```http
POST /api/v3/projects/{projectId}/marketing-source-snapshots/finalize
Content-Type: application/json

{}
```

4. 응답에서 다음을 확인한다.
   - `contract = marketing-source-snapshot-v1`
   - `schemaVersion = 2.0`
   - `snapshotId` 존재
   - `snapshotHash`가 `sha256:`와 64자리 16진수로 구성됨
   - `createdAt` 존재
   - `marketAnalysisSeedSnapshotId`, `selectionId`, `conceptId` 존재
5. `snapshot` 본문에서 다음을 확인한다.
   - 선택 Concept의 `conceptName`, `targetSegment`, `problem`, `valueProposition`, `positioning`, `keyFeatures`
   - 최종 가설의 `targetRegion`, `revenueModel`, `price`, `channels`, `competitorDifferentiators`, `preMarketSomShare`, `preMarketSom`
   - `allowedClaims`, `prohibitedClaims`, `requiredDisclosures`, `requiredControls`, `communicationRequiredControls`
   - `officialEvidenceReferences`
6. 같은 POST를 다시 호출하고 `snapshotId`, `snapshotHash`, `createdAt`이 처음과 동일한지 확인한다.

DB에서도 하나의 저장 Snapshot인지 확인한다.

```sql
select id, project_id, source_market_seed_snapshot_id, selection_id, concept_id,
       schema_version, snapshot_hash, finalized_at
from marketing_source_snapshots
where project_id = {projectId};
```

## 2. Market Result·Finalized Planning 비의존 검증

1. 준비 단계에서 Market Result와 Finalized Planning을 만들지 않은 상태를 유지한다.
2. Marketing 화면에서 Source 요약과 콘텐츠 생성 폼이 활성화되는지 확인한다.
3. 네트워크 요청에서 Planning Snapshot 조회 API가 호출되지 않는지 확인한다.
4. 생성 요청에 `marketingSourceSnapshotId`가 있고 `planningSnapshotId`가 없는지 확인한다.

## 3. 콘텐츠 생성과 비동기 복구

1. 채널, 목적, 톤, 길이를 입력하고 콘텐츠 생성을 시작한다.
2. TaskRun/JobEvent 진행 상태가 순서대로 표시되는지 확인한다.
   - 대기
   - Source 준비
   - 문구 생성
   - 법률 표현 확인
   - 완료
3. 생성 중 페이지를 새로고침한다.
4. 기존 `activeJobId`로 SSE 또는 polling이 복구되고 새 TaskRun을 만들지 않는지 확인한다.
5. 완료된 콘텐츠 상세의 Source ID/hash가 현재 Marketing Source와 같은지 확인한다.

## 4. Legal Guard 검증

테스트 프로젝트의 Legal Result에 구분하기 쉬운 금지 주장과 필수 고지가 있어야 한다.

1. 생성 결과에 `prohibitedClaims` 중 하나를 그대로 넣어 사용자 편집 저장을 시도한다.
2. `422`와 `MARKETING_PROHIBITED_CLAIM`으로 저장이 거절되는지 확인한다.
3. 금지 주장을 제거한다.
4. `requiredDisclosures` 중 하나를 본문과 `legalReview.requiredDisclosuresApplied` 양쪽에서 제거하고 저장을 시도한다.
5. 필수 고지 누락으로 저장이 거절되는지 확인한다.
6. 화면의 법률 고지 반영 동작으로 필수 고지를 추가한다.
7. 편집 저장이 성공하는지 확인한다.
8. 최종 저장 직전에 다시 금지 주장 또는 필수 고지 누락을 만들면 최종 저장도 거절되는지 확인한다.
9. 모든 조건을 충족하면 최종 저장이 성공하는지 확인한다.

## 5. Stale Source 검증

새 Concept 선택으로 새 Market Seed와 Marketing Source를 만드는 fixture가 있을 때만 수행한다.

1. 기존 Marketing 콘텐츠의 Source ID를 기록한다.
2. 새 선택과 가설 결정을 완료하고 새 Market Seed/Marketing Source를 확정한다.
3. 기존 콘텐츠가 `STALE`로 표시되는지 확인한다.
4. 기존 콘텐츠에서 재생성을 실행한다.
5. 재생성 후 콘텐츠의 `marketingSourceSnapshotId`와 hash가 현재 Source로 갱신되는지 확인한다.
6. 과거 TaskRun ID가 재사용되지 않고 새 TaskRun이 생성되는지 확인한다.

## 6. 빠른 자동 검증

Backend:

```powershell
cd backend
.\gradlew.bat test --tests "com.aivle.backend.pipeline.marketing.*" --tests "com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests"
```

AI:

```powershell
cd ai
.\.venv\Scripts\python.exe -m pytest tests/test_marketing_content_contract.py -q
```

Frontend:

```powershell
cd frontEnd
npm.cmd test -- --run src/features/marketing-content/model/marketingContentModel.test.js src/features/marketing-content/hooks/useMarketingContent.test.jsx src/features/marketing-content/hooks/useMarketingGeneration.test.jsx src/features/marketing-content/components/MarketingCopyEditor.test.jsx
```

## 통과 기준

- Market Result와 Finalized Planning 없이 Marketing Source와 콘텐츠를 만들 수 있다.
- Snapshot에 ID, schemaVersion, hash, createdAt과 원본 추적 정보가 있다.
- Source가 선택 Concept, 최종 수락 가설, Legal Result만을 반영한다.
- 요청·DB FK·콘텐츠 상세이 `marketingSourceSnapshotId`를 사용한다.
- 금지 주장과 필수 고지 위반이 AI 생성, 사용자 편집, 최종 저장에서 차단된다.
- TaskRun/SSE/polling/retry 복구 동작이 유지된다.
- 새 Source가 생기면 과거 콘텐츠가 stale로 식별된다.
