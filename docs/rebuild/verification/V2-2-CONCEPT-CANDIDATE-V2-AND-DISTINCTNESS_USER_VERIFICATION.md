# V2-2 사용자 검증 안내

## 목적

ConceptCandidateV2가 최소 Seed에서 생성 가능하고, 사용자 `LOCKED` 값과 AS_IS 원안을 보존하며, 중복 후보를 법률 검토 전에 교체하고, pre-market SOM을 가설로만 표시하는지 확인한다.

## 1. Backend 표적 검증

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\backend`

```powershell
.\gradlew.bat test --tests "com.aivle.backend.pipeline.concept.ConceptCandidateV2ValidatorTests" --tests "com.aivle.backend.pipeline.concept.ConceptFingerprintTests" --tests "com.aivle.backend.pipeline.concept.ConceptFactoryStateMachineTests" --tests "com.aivle.backend.pipeline.concept.ConceptFactorySqlContractTests" --tests "com.aivle.backend.pipeline.concept.ConceptFactoryFiveSlotTests" --tests "com.aivle.backend.pipeline.concept.ConceptFactoryLimitTests" --tests "com.aivle.backend.pipeline.concept.worker.ConceptFactoryWorkerTests"
```

예상 소요: 10초~2분. 최초 Gradle 다운로드가 필요하면 더 오래 걸릴 수 있다.

성공 기준:

- `BUILD SUCCESSFUL`
- 실패·오류·skip 0
- 최소 Seed, LOCKED 수익 모델 보존/위반, AS_IS Candidate 1, SOM 가설 표기 테스트 통과
- 이름 변경 중복과 작은 문구 변경 중복 테스트 통과
- 중복 후보의 법률 호출 전 교체 및 `INSUFFICIENT_DISTINCT_CONCEPTS` 종료 테스트 통과

## 2. AI schema 및 법률 연결 검증

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\ai`

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_concept_factory_schema.py tests/test_concept_legal_evidence.py
```

예상 소요: 5초~1분.

성공 기준:

- `13 passed`
- ConceptCandidateV2/Redesign/Legal provider schema가 closed strict schema
- 누락 수익 모델과 SOM이 `AI_HYPOTHESIS + OPEN + PROPOSED`
- AS_IS 원안 후보가 Candidate 1에서만 허용

## 3. Frontend 표적 테스트

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\frontEnd`

```powershell
npm.cmd test -- --run src/features/concept-factory/components/ConceptReveal.test.jsx src/features/concept-factory/model/conceptFactoryModel.test.js src/features/concept-selection/model/conceptComparisonModel.test.js src/shared/async-events/jobEventMessages.test.js
```

예상 소요: 10초~2분.

성공 기준:

- 테스트 파일 4개 통과
- 테스트 7개 통과

## 4. Frontend lint

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\frontEnd`

```powershell
npx.cmd eslint src/features/concept-factory/components/ConceptReveal.jsx src/features/concept-factory/components/ConceptReveal.test.jsx src/features/concept-factory/components/ConceptTimeline.jsx src/features/concept-factory/model/conceptFactoryModel.js src/features/concept-selection/components/LegalDetailDialog.jsx src/features/concept-selection/model/conceptComparisonModel.js src/features/concept-selection/model/conceptComparisonModel.test.js src/shared/async-events/jobEventMessages.js src/shared/async-events/jobEventMessages.test.js
```

예상 소요: 5초~1분. 출력 없이 종료 코드 0이면 성공이다.

## 5. Diff 검사

저장소 루트에서 실행한다.

```powershell
git diff --check
```

성공 기준: 종료 코드 0. Windows의 LF→CRLF 안내 경고는 whitespace 오류가 아니다.

## 6. 브라우저 수동 확인

로컬 앱을 실행한 뒤 다음을 확인한다.

1. 필수 3개 Seed만 입력하고 Concept Factory를 시작할 수 있다.
2. 진행 Timeline에 `아이디어 조건 확인` 다음 `후보 차별성 확인`, 그 다음 `법률 근거 확인`이 나타난다.
3. 완료 화면은 서로 다른 후보 5개를 한 번에 공개한다.
4. 카드에 컨셉 정의, 핵심 가치, 대상 사용자, 업종, 조사 범위, 수익 모델, 가격, 채널, 차별점이 표시된다.
5. 수익·가격·채널·차별점 옆에 source/authority/decision 의미가 표시된다.
6. SOM에는 `실제 시장분석 결과가 아닙니다` 경고와 `AI_HYPOTHESIS · OPEN · PROPOSED`가 표시된다.
7. AS_IS Candidate 1에는 `사용자 원안 구조화`가 표시된다.
8. 공식 법령 링크와 사전검토 한계 문구가 유지된다.

브라우저 확인 예상 소요: 5~15분. 실제 AI Provider를 연결하면 생성 시간에 따라 늘어날 수 있다.

## 실패 시 수집할 로그

- Backend: 실패한 테스트명, assertion, `backend/build/reports/tests/test/index.html`
- AI: pytest traceback과 `ProviderFailure.code`, `ProviderFailure.reason`
- Frontend: Vitest assertion, 브라우저 콘솔 오류, 실패 화면의 network response
- Worker: `runId`, `slotNumber`, Slot status, `safeErrorCode`, Job Event 순서
- 중복 문제: 후보의 conceptName을 제외한 9개 Fingerprint 필드 값

Prompt 원문, Provider raw body, 인증정보, 사용자 전체 원문은 로그에 포함하지 않는다.

## 다음 단계 진행 가능 조건

- 위 표적 자동 검증이 모두 통과한다.
- 브라우저에서 서로 다른 후보 5개와 V2 가설 의미가 확인된다.
- 중복 후보가 법률 검토 이벤트 전에 교체되는 것이 확인된다.
- `LOCKED` 값이 후보에서 변경되지 않는다.
- 이 조건이 충족되면 V2-3을 시작할 수 있다.
