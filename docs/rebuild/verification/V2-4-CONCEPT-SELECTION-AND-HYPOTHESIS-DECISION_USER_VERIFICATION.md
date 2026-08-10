# V2-4 사용자 검증 안내

## 목적

법률 적격 Concept 5개를 먼저 비교한 뒤 하나만 선택하고, 선택 Concept의 AI 가설만 결정하며, LOCKED 값과 Delta Legal Review 경계가 지켜지는지 확인한다.

## 1. Backend 표적 검증

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\backend`

```powershell
.\gradlew.bat test --tests "com.aivle.backend.pipeline.selection.ConceptHypothesisDecisionTests" --tests "com.aivle.backend.pipeline.selection.ConceptSelectionServiceV2Tests" --tests "com.aivle.backend.pipeline.module.ProjectModuleStatusServiceTests" --tests "com.aivle.backend.pipeline.selection.SelectionAndHandoffContractTests"
```

예상 소요: 10초~2분.

성공 기준:

- `BUILD SUCCESSFUL`
- 4개 클래스, 15개 테스트 통과
- 선택 시 6개 최신 가설 결정이 반환됨
- Seed LOCKED 가격은 final value가 있는 읽기 전용 결정으로 생성됨
- 거절 후 proposal version 2의 `ALTERNATIVE_PROPOSED` 생성
- 수익 수정은 법률 검토 호출, 실패 시 final value 없음
- SOM 수정은 법률 검토 없이 `USER_EDITED_ACCEPTED`

## 2. AI 대안 제안 계약 검증

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\ai`

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_concept_hypothesis_alternative.py tests/test_concept_factory_schema.py tests/test_internal_task_type_alignment.py -q
```

예상 소요: 5초~1분.

성공 기준:

- `9 passed`
- 대안 결과가 동일 hypothesis type과 요청한 proposal version을 보존
- `source=AI_HYPOTHESIS`, `decisionStatus=ALTERNATIVE_PROPOSED`
- 잘못된 version 결과 거부
- Java와 FastAPI TaskType 일치

## 3. Frontend 표적 테스트

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\frontEnd`

```powershell
npm.cmd test -- --run src/features/concept-selection/components/HypothesisDecisionPanel.test.jsx src/features/concept-selection/components/SelectionConfirmation.test.jsx src/features/concept-selection/components/ConceptComparisonView.test.jsx
```

예상 소요: 10초~2분.

성공 기준:

- 3개 파일, 4개 테스트 통과
- LOCKED 값에는 Action 버튼이 없음
- 열린 AI 가설에 채택·수정 후 채택·다른 제안 Action이 있음
- SOM 구조 수정값이 객체로 전달됨
- 비교 화면에 점수나 자동 순위가 없음

## 4. Frontend lint

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\frontEnd`

```powershell
npx.cmd eslint src/features/concept-selection/api/conceptSelectionApi.js src/features/concept-selection/hooks/useConceptSelection.js src/features/concept-selection/components/ConceptCard.jsx src/features/concept-selection/components/SelectionConfirmation.jsx src/features/concept-selection/components/HypothesisDecisionPanel.jsx src/features/concept-selection/components/HypothesisDecisionPanel.test.jsx src/features/concept-selection/pages/ConceptComparisonPage.jsx
```

예상 소요: 5초~1분. 출력 없이 종료 코드 0이면 성공이다.

## 5. Diff 검사

저장소 루트에서 실행한다.

```powershell
git diff --check
```

성공 기준: 종료 코드 0. Windows의 LF→CRLF 안내는 whitespace 오류가 아니다.

## 6. 브라우저 수동 확인

로컬 앱과 실제 Provider를 연결한 뒤 새 Concept Factory 결과로 확인한다.

1. 선택 전에 적격 Concept 5개를 비교할 수 있고 카드에 시장 값이 AI 사전 가설이라고 표시되는지 확인한다.
2. 5개 후보의 가설을 각각 확정하라고 요구하지 않는지 확인한다.
3. Concept 하나를 선택하면 선택한 Concept의 6개 가설만 나타나는지 확인한다.
4. Seed에서 입력한 LOCKED 가격·수익·채널·차별점은 `사용자가 입력 · 확정됨`으로 표시되고 Action 버튼이 없는지 확인한다.
5. 열린 AI 가설에는 `채택`, `수정 후 채택`, `다른 제안`이 모두 표시되는지 확인한다.
6. `다른 제안`을 누르면 단독 거절 상태로 멈추지 않고 `새 AI 제안 · 확인 필요`와 증가한 대안이 나타나는지 확인한다.
7. 수익 모델을 무료에서 정기결제처럼 수정하면 `법률 영향 확인 중`을 거쳐 적격 결과일 때만 확정되는지 확인한다.
8. Delta Legal Review를 부적격으로 유도하면 수정값이 확정되지 않고 `법률 검토 미통과 · 대안 필요`와 다른 제안 Action이 남는지 확인한다.
9. pre-market SOM 점유율 또는 금액을 수정하면 법률 검토 spinner 없이 즉시 확정되는지 확인한다.
10. 모든 결정 전에는 시장분석 Snapshot 저장 또는 시장분석 시작 링크가 나타나지 않는지 확인한다.

예상 소요: 10~25분. 실제 AI·법령 Provider 응답 시간에 따라 늘어날 수 있다.

## 실패 시 수집할 로그

- Backend: 실패 테스트명, assertion, `backend/build/reports/tests/test/index.html`
- AI: pytest traceback과 `ProviderFailure.code`, `ProviderFailure.reason`
- Frontend: Vitest assertion, 브라우저 콘솔 오류, 실패 Action의 network response
- 결정 오류: `selectionId`, `hypothesisType`, `proposalVersion`, `decisionStatus`, `legalReviewStatus`
- Delta 결합 오류: 변경 전후 Fact Pattern hash와 안전한 법률 status

Prompt 원문, Provider raw body, 인증정보, 사용자 전체 원문, 공식 조문 전체는 로그에 포함하지 않는다.

## 다음 단계 진행 가능 조건

- 위 표적 자동 검증이 모두 통과한다.
- 선택한 Concept에만 6개 결정이 생성된다.
- LOCKED 값은 endpoint와 UI 모두에서 변경할 수 없다.
- reject가 versioned alternative로 이어져 dead end가 없다.
- legal-sensitive 변경만 필요한 법률 재검토를 받고 실패한 값은 확정되지 않는다.
- SOM 변경은 법률 재검토를 호출하지 않는다.
- 선택만으로 시장분석 Snapshot이 조기 생성되지 않는다.
- 이 조건이 충족되면 V2-5를 시작할 수 있다.
