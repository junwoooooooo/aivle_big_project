# V2-3 사용자 검증 안내

## 목적

각 Concept가 자체 `LegalFactPattern V2`를 만든 뒤에만 공식 법령 근거 검토를 받고, 이전 후보의 근거가 재사용되지 않으며, Concept 설계 누락은 사용자 질문이 아니라 재설계로 처리되는지 확인한다.

## 1. Backend 표적 검증

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\backend`

```powershell
.\gradlew.bat test --tests "com.aivle.backend.pipeline.concept.ConceptCandidateV2ValidatorTests" --tests "com.aivle.backend.pipeline.concept.worker.ConceptFactoryWorkerTests" --tests "com.aivle.backend.pipeline.legal.LegalEvidenceHardeningTests"
```

예상 소요: 10초~2분. 최초 Gradle 배포판 다운로드가 필요하면 더 오래 걸릴 수 있다.

성공 기준:

- `BUILD SUCCESSFUL`
- 3개 클래스, 28개 테스트 통과
- Fact Pattern에 역할·거래·결제·개인정보·파트너·자격·광고·운영 값과 출처가 포함됨
- 수익·가격·지역 가설이 법률 검토 전에 포함되고 SOM은 제외됨
- 중복 후보와 재설계 중복 후보가 법률 호출 전에 차단됨
- 외부 사용자 사실이 없어도 Context 생성 가능

## 2. AI 계약 및 분기 검증

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\ai`

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_concept_legal_evidence.py tests/test_concept_factory_schema.py -q
```

예상 소요: 5초~1분.

성공 기준:

- `17 passed`
- 동일 실행을 두 번 호출하면 공식 근거 조회도 두 번 호출됨
- 결제 주체·판매자 역할 같은 설계 누락은 `REDESIGNABLE`
- 현재 보유 인허가 같은 외부 현실 사실만 `NEEDS_FACTS`
- 공식 근거가 없고 확인 질문도 없으면 재시도 가능한 `LEGAL_SOURCE_EVIDENCE_UNAVAILABLE`
- material finding의 공식 근거 참조가 누락되거나 범위를 벗어나면 거부

## 3. Frontend 표적 테스트

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\frontEnd`

```powershell
npm.cmd test -- --run src/features/concept-factory/components/ConceptReveal.test.jsx src/features/concept-selection/model/conceptComparisonModel.test.js
```

예상 소요: 10초~2분.

성공 기준:

- 2개 파일, 3개 테스트 통과
- 공개 화면이 assessment의 판매자 역할과 검토 결제 흐름을 우선 표시
- 공식 법령 링크와 사전검토 한계 문구 유지

## 4. Frontend lint

작업 위치: `C:\Users\seewo\Desktop\big_proj_01\new_3\frontEnd`

```powershell
npx.cmd eslint src/features/concept-factory/components/ConceptReveal.jsx src/features/concept-factory/components/ConceptReveal.test.jsx src/features/concept-selection/components/LegalDetailDialog.jsx
```

예상 소요: 5초~1분. 출력 없이 종료 코드 0이면 성공이다.

## 5. Diff 검사

저장소 루트에서 실행한다.

```powershell
git diff --check
```

성공 기준: 종료 코드 0. Windows의 LF→CRLF 안내는 whitespace 오류가 아니다.

## 6. 브라우저 수동 확인

로컬 앱과 실제 Provider를 연결한 뒤 새 Concept Factory run으로 확인한다.

1. 진행 순서가 `후보 생성 → 아이디어 조건 확인 → 후보 차별성 확인 → 법률 근거 확인`인지 확인한다.
2. 이름만 바꾸거나 다른 후보와 구조가 같은 후보는 법률 근거 확인 전에 교체되는지 확인한다.
3. 각 적격 Concept의 법률 상세에서 플랫폼·제공자·판매자·중개자 역할과 거래·결제·개인정보·광고 구조가 해당 Concept 내용과 일치하는지 확인한다.
4. Concept A와 B의 결제·판매자 구조가 다르면 공식 Evidence와 검토 요약도 각 구조에 맞게 조회되는지 확인한다.
5. 화면의 법률 구조에 pre-market SOM 점유율·금액이 나타나지 않는지 확인한다.
6. 결제 주체나 파트너 역할이 모호한 후보를 유도했을 때 사용자 질문 화면으로 멈추지 않고 1회 재설계하는지 확인한다.
7. 재설계 후보를 기존 다른 후보와 같게 만들었을 때 두 번째 법률 검토 없이 replacement로 이동하는지 확인한다.
8. 실제 보유 인허가나 필수 기존 계약 여부처럼 AI가 결정할 수 없는 경우에만 `NEEDS_INPUT`으로 전환되는지 확인한다.
9. 법률 상세의 공식 법령 링크, 기준일, 전문가 검토 권고, 사전검토 한계 문구를 확인한다.

예상 소요: 10~25분. 실제 AI·법령 Provider 응답 시간에 따라 늘어날 수 있다.

## 실패 시 수집할 로그

- Backend: 실패 테스트명, assertion, `backend/build/reports/tests/test/index.html`
- AI: pytest traceback과 `ProviderFailure.code`, `ProviderFailure.reason`
- Frontend: Vitest assertion, 브라우저 콘솔 오류, 실패 화면의 network response
- Worker: `runId`, `slotNumber`, candidate/redesign phase, Slot status, `safeErrorCode`, Job Event 순서
- 법률 결합 오류: `reviewedFactPatternSchemaVersion`, `reviewedFactPatternHash`와 현재 후보에서 계산한 hash
- 분기 오류: 공식 근거 조회의 `requiredUserInputs.question` 중 민감정보를 제거한 안전한 분류 예시

Prompt 원문, Provider raw body, 공식 조문 전체, 인증정보, 사용자 전체 원문은 로그에 포함하지 않는다.

## 다음 단계 진행 가능 조건

- 위 표적 자동 검증이 모두 통과한다.
- 후보별 Fact Pattern과 공식 Evidence가 서로 섞이지 않는다.
- 설계 누락이 `NEEDS_FACTS`로 사용자에게 전가되지 않는다.
- 재설계 후보가 schema/LOCKED·origin/차별성/법률 순서로 다시 검증된다.
- 검토 hash와 저장된 Fact Pattern이 일치한다.
- 이 조건이 충족되면 V2-4를 시작할 수 있다.
