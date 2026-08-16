# V2-9 — Active Surface Cleanup and Acceptance 사용자 검증

## 검증 목표

활성 제품에서 구형 입력·planning proposal·FinalizedPlanning·중복 라우트가 노출되지 않고, 8단계 모듈 상태와 Job Center가 현재 저장된 사실을 표시하는지 확인한다.

## 준비

1. rebuild 기준선으로 새 로컬 DB 볼륨을 준비한다. 기존 V1 기준선 적용 DB를 재사용하지 않는다.
2. Backend와 Frontend를 실행하고 로그인한다.
3. 새 프로젝트 하나와 V2-8까지 진행된 기존 프로젝트 하나를 준비한다.
4. 브라우저 개발자 도구의 Network 패널을 연다.

## 1. 정규 경로와 8단계 내비게이션

프로젝트를 열고 사이드바에 다음 순서가 표시되는지 확인한다.

1. 아이디어 정리
2. 컨셉 생성·법률검토
3. 컨셉 비교·선택
4. 시장분석
5. BM 분석
6. 기술·운영 분석
7. 재무 분석
8. 마케팅 콘텐츠 제작

BM을 선택했을 때 주소가 다음인지 확인한다.

```text
/app/projects/{projectId}/business-model
```

`business-persona-test`, `BM·재무`, `Persona 응답 테스트`, `기존 MVP`, `Journey` 문구가 프로젝트 화면에 없어야 한다.

## 2. 구형 중복 경로 차단

로그인 상태에서 아래 URL을 직접 입력한다.

```text
/dashboard
/projects
/projects/{projectId}/structured-plan/current
/projects/{projectId}/legal-review
/projects/{projectId}/feasibility
/projects/{projectId}/market-validation
/app/projects/{projectId}/plan/current
/app/projects/{projectId}/review/legal
/app/projects/{projectId}/report/final
/app/projects/{projectId}/business-persona-test
```

구형 화면이 렌더링되거나 구형 API가 호출되면 실패다. 전역 구형 URL은 Not Found로 처리되어야 하며, 프로젝트 내부 알 수 없는 하위 경로는 현재 프로젝트 개요 정규 경로로만 복귀해야 한다.

## 3. 초기 Idea 필수 입력

새 프로젝트에서 1단계로 이동한다.

- 최초 필수 입력은 아이디어 개요, 해결할 문제, 예상 사용자 세 항목이어야 한다.
- 수익 모델, 경쟁사, 시장 규모, 실행 계획 등을 초기 필수 입력으로 강제하면 실패다.
- Network에서 planning 또는 structured-plan API가 호출되면 실패다.

## 4. planning proposal 비노출

시장분석 화면과 결과 영역을 확인한다.

- 채택, 부분 채택, 거절로 planning을 변경하는 카드가 없어야 한다.
- `planning-change-proposals`, `planning/current`, `planning/snapshots`, `finalized-planning` API가 호출되면 실패다.
- 외부 분석 결과가 원본 Market Seed를 자동 변경한다고 안내하면 실패다.

## 5. BM 입력과 상태

### Market Seed가 없는 프로젝트

BM 화면에 Market Seed 확정 필요가 표시되고 모듈 상태가 `NOT_READY`여야 한다. Handoff 버튼으로 실행할 수 없어야 한다.

### Market Seed가 있는 프로젝트

1. BM 화면의 입력 Snapshot ID가 현재 Market Seed ID와 같은지 확인한다.
2. 외부 BM 알고리즘이 연결되지 않았다는 문구가 보이는지 확인한다.
3. `BM Handoff 준비`를 누른다.
4. Network 요청이 다음 형태인지 확인한다.

```http
POST /api/v3/projects/{projectId}/module-handoffs
Content-Type: application/json

{
  "module": "BUSINESS_MODEL",
  "inputSnapshotId": "{현재 Market Seed snapshotId}",
  "requestedOperation": "START_BUSINESS_MODEL_ANALYSIS"
}
```

응답에서 다음을 확인한다.

- `contract = module-handoff-v2`
- `module = BUSINESS_MODEL`
- `inputSnapshotType = MARKET_ANALYSIS_SEED`
- `inputSchemaVersion = 2.0`
- `inputSnapshotId`와 본문의 snapshot ID가 현재 Market Seed와 같음
- 외부 adapter 미연결 시 run 상태가 `NOT_CONNECTED`
- `FINALIZED_PLANNING` 또는 FinalizedPlanning ID가 없음

## 6. 독립 모듈 상태

`GET /api/v3/projects/{projectId}/module-statuses` 응답을 확인한다.

- module이 정확히 `IDEA`, `CONCEPT_FACTORY`, `CONCEPT_SELECTION`, `MARKET_ANALYSIS`, `BUSINESS_MODEL`, `TECH_OPS`, `FINANCE`, `MARKETING` 순서다.
- `BUSINESS_PERSONA_TEST`가 없다.
- Market Seed가 없으면 MARKET_ANALYSIS와 BUSINESS_MODEL이 `NOT_READY`다.
- Market Seed가 있고 BM run이 없으면 BUSINESS_MODEL이 `NOT_CONNECTED`다.
- BM의 `sourceSnapshotId`는 현재 Market Seed ID다.
- TECH_OPS와 FINANCE는 각각 자신의 preparation/snapshot 상태를 반영한다.
- MARKETING은 `MarketingSourceSnapshot`을 기준으로 하며 FinalizedPlanning을 요구하지 않는다.

## 7. Job Center 현재 사실

1. Idea 또는 Concept 비동기 작업을 하나 실행한다.
2. 페이지를 새로고침해도 활성 작업이 Job Center에 복원되는지 확인한다.
3. 작업을 선택했을 때 Idea는 `/idea`, Concept 생성은 `/concepts`, 가설 대안은 `/concepts/compare`, Marketing은 `/marketing`으로 이동하는지 확인한다.
4. 해결된 과거 `NEEDS_INPUT` 작업이 활성 목록에 남지 않고 최근 목록에서 `RESOLVED_INPUT`으로 표시되는지 확인한다.
5. 구형 planning, feasibility, persona, report 경로로 이동하는 Job이 없어야 한다.

## 8. 공개 랜딩 문구

로그아웃 후 `/`를 연다.

- `사업 검증 파이프라인`, `8단계 사업 검증 흐름`이 표시되어야 한다.
- Journey 또는 5단계 제품 흐름이라고 표시하면 실패다.
- 핵심 기능 설명이 Idea Brief, 콘셉트, Market Seed, BM·기술·운영, 재무, 마케팅 콘텐츠를 현재 기능으로 설명해야 한다.

## 9. 빠른 자동 검증

Backend:

```powershell
cd backend
.\gradlew.bat test --no-daemon --tests "*ActiveSurfaceCleanupTests" --tests "*ProjectModuleStatusServiceTests" --tests "*ProjectJobQueryServiceTests" --tests "*IdeaBriefFieldCatalogTests" --tests "*MarketingSourceV2ContractTests" --tests "*TechOpsHandoffTests" --tests "*FinancialHandoffTests"
```

Frontend:

```powershell
cd frontEnd
npm.cmd test -- --run src/app/routing/AppRouter.cutover.test.js src/app/module-status/projectModuleModel.test.js src/features/business-model/pages/BusinessModelPage.test.jsx src/features/landing/__tests__/LandingPage.test.jsx
```

## 수용 기준

- 구형 초기 필수 입력, planning proposal workflow, FinalizedPlanning 의존, 중복 라우트가 활성 표면에서 발견되지 않는다.
- 8개 모듈 상태와 Job Center가 현재 DB·TaskRun 사실을 표시한다.
- BM은 Market Seed를 불변 입력으로 사용하고 외부 알고리즘 미연결을 사실대로 표시한다.
- 위 자동 검증이 모두 통과한다.
