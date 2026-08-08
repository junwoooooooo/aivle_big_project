# V2-4 Concept 선택 및 가설 결정 — 실행 결과

## 결과

V2-4 범위를 구현했다. 사용자는 법률 적격·서로 다른 Concept 5개를 provisional hypothesis 상태로 비교한 뒤 하나를 선택하고, 선택한 Concept의 6개 시장 가설만 최종 결정한다.

기존 선택 API가 선택 즉시 `SelectedConceptSnapshot`을 만들던 동작은 제거했다. 선택 시에는 `ConceptHypothesisDecision` 6개를 영속화하며, 모든 결정과 필요한 Delta Legal Review가 끝나기 전에는 시장분석 Snapshot이나 handoff를 만들지 않는다. Snapshot 정본 생성은 V2-5 범위다.

## 구현한 계약

- 별도 `concept_hypothesis_decisions` 테이블과 Domain을 추가했다.
- 각 결정은 다음을 보존한다.
  - Concept/Selection 식별자
  - 가설 유형과 제안값 JSON
  - source와 decision status
  - final value
  - proposal version
  - 결정 사용자와 시각
  - legal impact와 legal review status/result
- 선택 시 다음 6개 가설을 초기화한다.
  - `revenueModel`
  - `price`
  - `channels`
  - `differentiators`
  - `preMarketSomShareHypothesis`
  - `preMarketSomHypothesis`
- 후보의 `valueSemantics`가 `USER_INPUT + LOCKED`인 값은 `ACCEPTED`와 final value로 즉시 저장하고 읽기 전용으로 반환한다.
- 열린 AI 가설은 `PROPOSED`로 저장하고 선택한 Concept에 대해서만 Action을 제공한다.
- Action은 `ACCEPT`, `EDIT_AND_ACCEPT`, `REQUEST_ALTERNATIVE`다.
- `REQUEST_ALTERNATIVE`는 현재 제안을 `REJECTED`로 기록한 뒤 `CONCEPT_HYPOTHESIS_ALTERNATIVE` AI task로 실질적으로 다른 값을 생성한다.
- 대안은 `AI_HYPOTHESIS + ALTERNATIVE_PROPOSED`와 증가한 proposal version을 가진다. 기존 값과 동일한 대안은 Backend가 거부한다.
- `LOCKED` 값은 hypothesis Action endpoint에서 수정·거절·대안 요청할 수 없다.
- 원 제안을 그대로 채택하면 기존 법률 적격 결과를 유지하고 불필요한 Delta Review를 호출하지 않는다.
- 수익·가격·채널·차별점의 final value가 원 후보와 달라지면 변경한 Candidate Fact Pattern을 다시 만들고 공식 근거 기반 법률 검토를 호출한다.
- pre-market SOM 점유율·금액 변경은 `NON_LEGAL`로 즉시 확정하며 Delta Review를 호출하지 않는다.
- Delta Review가 `IMPLEMENTABLE` 또는 `IMPLEMENTABLE_WITH_CONTROLS`인 경우에만 수정값을 `USER_EDITED_ACCEPTED`로 확정한다.
- Delta Review 실패 시 `finalValue`와 `decidedAt`을 만들지 않고 `legalReviewStatus=FAILED`로 남긴다. 사용자는 같은 화면에서 다른 제안을 요청할 수 있다.
- 선택 응답은 최신 proposal version만 모아 `decisionComplete`와 함께 반환한다.
- 선택 후 가설이 끝나지 않은 상태에서 기존 Market handoff를 직접 호출하면 안전한 `HYPOTHESIS_DECISIONS_INCOMPLETE`로 차단한다.
- Concept Selection 모듈은 선택 후 Snapshot 전 상태를 실패가 아니라 가설 결정 준비 상태로 표시한다.
- 비교 카드에는 선택 전 시장 값이 AI 사전 가설임을 표시한다.
- 선택 UI는 내부 enum 대신 `사용자가 입력 · 확정됨`, `AI 제안 · 확인 필요`, `새 AI 제안 · 확인 필요`, `법률 검토 미통과 · 대안 필요`를 표시한다.
- 구조화 SOM은 JSON 값을 유지하면서 수정 후 채택할 수 있고, NON_LEGAL 변경에는 법률 검토 진행 표시를 만들지 않는다.

## 변경 파일

### AI

- `ai/app/api/executions.py`
- `ai/app/tasks/concept_hypothesis_alternative/__init__.py`
- `ai/app/tasks/concept_hypothesis_alternative/models.py`
- `ai/app/tasks/concept_hypothesis_alternative/service.py`
- `ai/tests/test_concept_hypothesis_alternative.py`
- `ai/tests/test_concept_factory_schema.py`
- `ai/tests/test_internal_task_type_alignment.py`

### Backend

- `backend/src/main/java/com/aivle/backend/common/exception/ErrorCode.java`
- `backend/src/main/java/com/aivle/backend/pipeline/integration/application/ModuleIntegrationService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/module/ProjectModuleStatusService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/api/ConceptSelectionController.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/api/SelectionApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/application/ConceptSelectionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/domain/ConceptHypothesisDecision.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/domain/HypothesisDecisionStatus.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/domain/HypothesisLegalImpact.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/domain/HypothesisLegalReviewStatus.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/domain/HypothesisType.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/repository/ConceptHypothesisDecisionRepository.java`
- `backend/src/main/java/com/aivle/backend/taskrun/domain/TaskType.java`
- `backend/src/main/java/com/aivle/backend/taskrun/service/ProjectJobQueryService.java`
- `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/selection/ConceptHypothesisDecisionTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/selection/ConceptSelectionServiceV2Tests.java`

### Frontend

- `frontEnd/src/features/concept-selection/api/conceptSelectionApi.js`
- `frontEnd/src/features/concept-selection/hooks/useConceptSelection.js`
- `frontEnd/src/features/concept-selection/components/ConceptCard.jsx`
- `frontEnd/src/features/concept-selection/components/SelectionConfirmation.jsx`
- `frontEnd/src/features/concept-selection/components/SelectionConfirmation.test.jsx`
- `frontEnd/src/features/concept-selection/components/HypothesisDecisionPanel.jsx`
- `frontEnd/src/features/concept-selection/components/HypothesisDecisionPanel.test.jsx`
- `frontEnd/src/features/concept-selection/pages/ConceptComparisonPage.jsx`
- `frontEnd/src/features/concept-selection/styles/concept-selection.css`

### 문서

- `docs/rebuild/progress/V2-4-CONCEPT-SELECTION-AND-HYPOTHESIS-DECISION_RESULT.md`
- `docs/rebuild/verification/V2-4-CONCEPT-SELECTION-AND-HYPOTHESIS-DECISION_USER_VERIFICATION.md`

## 실제 실행한 검사

- Backend 표적 테스트: 4개 클래스, 15개 테스트 통과
  - 선택 시 6개 결정 생성과 LOCKED 자동 확정
  - LOCKED endpoint mutation 차단
  - reject → versioned alternative proposal
  - 수익 변경의 Delta Legal 호출
  - Delta 실패 시 acceptance 차단
  - SOM 변경 시 Delta Legal 미호출
  - 선택 후 Snapshot 부재 상태와 기존 계약 보조 검사
- AI 표적 테스트: 3개 파일, 9개 테스트 통과
  - 대안 proposal schema와 version/type 결합
  - strict provider schema
  - Java/FastAPI TaskType 정렬
- Frontend 표적 테스트: 3개 파일, 4개 테스트 통과
  - LOCKED 읽기 전용
  - 대안 Action
  - 구조화 SOM 수정 후 채택
  - 선택 확정과 비교 화면
- 변경 Frontend 파일 ESLint: 통과
- AI 변경 파일 `py_compile`: 통과
- `git diff --check`: 통과. Git의 LF→CRLF 변환 안내만 확인했다.

## 의도적으로 생략한 검사

Fast Execution Profile에 따라 다음은 실행하지 않았다.

- Backend 전체 테스트 및 전체 Postgres/Testcontainers 테스트
- 실제 DB reset/migration 적용
- AI 전체 테스트 및 실제 대안·법률 Provider smoke
- Frontend 전체 baseline 및 production build
- Docker 재빌드·E2E
- 브라우저, 모바일, 접근성 수동 검증
- 전체 CI

## 남은 위험

- 대안 생성과 Delta Legal Review는 현재 hypothesis Action 요청 안에서 동기 호출된다. Provider 응답 지연·timeout의 실제 UX는 Provider smoke와 브라우저 검증이 필요하다.
- 채널·차별점은 잠재적으로 법률 민감한 값이므로 변경 시 보수적으로 Delta Review를 수행한다. 향후 claim 분류기가 세분화되면 불필요한 재검토를 줄일 수 있다.
- Delta Legal Result는 결정 레코드에 안전한 결과 JSON으로 저장하지만 V2-5 Market Snapshot의 Evidence 병합 규칙은 아직 구현하지 않았다.
- 기존 `SelectedConceptSnapshot` 및 과거 Market handoff 타입은 후속 전환을 위해 내부에 남아 있다. 선택 API에서는 더 이상 생성하지 않으며 V2-5에서 `MarketAnalysisSeedSnapshot`으로 교체해야 한다.
- baseline SQL을 변경했으므로 이미 생성된 로컬 DB는 reset 또는 별도 migration이 필요하다.

## 정확한 계속 지점

다음 작업은 V2-5 `Market Seed Snapshot and Handoff`다. 시작점은 현재 선택의 최신 6개 결정이 모두 accepted인지, 필요한 Delta Legal Review가 passed인지, Concept 법률 상태가 적격인지 검증한 뒤 immutable `MarketAnalysisSeedSnapshot`을 생성하는 것이다. Market 외부 module은 이 Snapshot만 입력받도록 전환한다. V2-6 Marketing Source Cutover로 자동 진행하지 않는다.
