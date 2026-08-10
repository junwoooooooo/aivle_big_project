# V2-2 ConceptCandidateV2 및 후보 차별성 — 실행 결과

## 결과

V2-2 범위를 구현했다. Concept Factory는 이제 Market Seed에서 `EXPLORE`, `REFINE`, `AS_IS` 전략을 결정하고, 5개 슬롯의 후보를 `ConceptCandidateV2`로 생성한다. 생성 후보는 법률 검토에 전달되기 전에 다음 순서를 반드시 거친다.

1. strict V2 schema 검증
2. 사용자 `LOCKED` 값 및 AS_IS 원안 보존 검증
3. Concept Fingerprint 기반 차별성 검증
4. 기존 법률 검토 연결

중복 후보나 `LOCKED` 위반 후보는 법률 모듈을 호출하지 않고 bounded replacement로 넘긴다. 중복 교체 한도 내에 서로 다른 후보를 확보하지 못하면 `INSUFFICIENT_DISTINCT_CONCEPTS`로 종료한다.

## 구현한 계약

- `ConceptCandidateV2` 사용자 표시 필드와 운영·법률 Fact Pattern 입력 필드를 strict schema로 고정했다.
- 확인된 Seed와 `AI_DERIVED + REVIEWABLE` Interpretation을 함께 후보 생성 입력으로 전달한다.
- 모든 후보 핵심 값 28개에 `source`, `authority`, `decision` 의미를 포함했다.
- Seed 선택값은 `USER_INPUT + LOCKED + ACCEPTED`로 후보에 그대로 보존한다.
- Seed에 없던 `revenueModel`, `price`, `channels`, `differentiators`는 `AI_HYPOTHESIS + OPEN + PROPOSED`로 생성한다.
- pre-market SOM 점유율 및 금액 가설은 구조화하고 항상 `AI_HYPOTHESIS + OPEN + PROPOSED`로 표시한다.
- `AS_IS`의 Candidate 1은 `ideaOverview`, `problem`, `targetUsers` 원안을 보존하며 해당 필드 의미도 `USER_INPUT + LOCKED + ACCEPTED`로 유지한다.
- Fingerprint는 `targetUsers`, `problemScenario`, `coreValue`, `solutionMechanism`, `revenueModel`, `channels`, `platformRole`, `operatingModel`, `partnerModel`을 사용한다.
- 이름을 Fingerprint에서 제외해 이름만 바꾼 후보를 중복으로 거부한다.
- 정규화 hash와 문자 3-gram 유사도를 함께 사용해 작은 표현 변경을 포함한 의미 중복을 판정한다.
- Slot 상태에 `VALIDATING_DISTINCTNESS`를 추가하고 이벤트·화면 문구를 연결했다.
- 공개 후보 화면과 비교 모델을 V2 필드명 및 가설 의미에 맞췄다.
- 현재 법률 검토 연결은 V2 후보 필드를 읽도록 최소 정렬했으며, Legal Fact Pattern 자체의 V2-3 확장은 수행하지 않았다.

## 변경 파일

### AI

- `ai/app/tasks/concept_candidate/models.py`
- `ai/app/tasks/concept_candidate/service.py`
- `ai/app/tasks/concept_redesign/service.py`
- `ai/app/tasks/concept_legal_review/service.py`
- `ai/tests/concept_candidate_v2_fixture.py`
- `ai/tests/test_concept_factory_schema.py`
- `ai/tests/test_concept_legal_evidence.py`

### Backend

- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryExecutionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorker.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptGenerationStrategy.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptCandidateV2Validator.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFingerprint.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptAttempt.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptAttemptError.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptSlot.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptSlotStatus.java`
- `backend/src/main/resources/db/migration/V1__new_pipeline_baseline.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptCandidateV2ValidatorTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFingerprintTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryStateMachineTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactorySqlContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryReplacementIntegrationTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorkerTests.java`

### Frontend

- `frontEnd/src/features/concept-factory/components/ConceptReveal.jsx`
- `frontEnd/src/features/concept-factory/components/ConceptReveal.test.jsx`
- `frontEnd/src/features/concept-factory/components/ConceptTimeline.jsx`
- `frontEnd/src/features/concept-factory/model/conceptFactoryModel.js`
- `frontEnd/src/features/concept-selection/model/conceptComparisonModel.js`
- `frontEnd/src/features/concept-selection/model/conceptComparisonModel.test.js`
- `frontEnd/src/features/concept-selection/components/LegalDetailDialog.jsx`
- `frontEnd/src/shared/async-events/jobEventMessages.js`
- `frontEnd/src/shared/async-events/jobEventMessages.test.js`

### 문서

- `docs/rebuild/progress/V2-2-CONCEPT-CANDIDATE-V2-AND-DISTINCTNESS_RESULT.md`
- `docs/rebuild/verification/V2-2-CONCEPT-CANDIDATE-V2-AND-DISTINCTNESS_USER_VERIFICATION.md`

## 실제 실행한 검사

- Backend 표적 테스트: 7개 테스트 클래스, 27개 테스트 통과
  - V2 후보 검증
  - Fingerprint 이름 변경·의미 중복
  - Slot 상태 전이
  - SQL 상태·오류 계약
  - 5개 슬롯 및 bounded limit
  - Worker의 중복 사전 차단·교체·한도 종료
- AI 표적 테스트: 13개 통과
  - strict provider schema
  - 최소 Seed 입력
  - 누락 수익 가설 의미
  - AS_IS Candidate 1
  - pre-market SOM 가설 표기
  - 기존 법률 검토 입력 연결
- Frontend 표적 테스트: 4개 파일, 7개 통과
- 변경 Frontend 파일 ESLint: 통과
- `git diff --check`: 통과. Git의 LF→CRLF 변환 경고만 확인했다.

## 의도적으로 생략한 검사

Fast Execution Profile에 따라 다음은 실행하지 않았다.

- Backend 전체 테스트 및 전체 Postgres/Testcontainers 테스트
- 실제 DB migration 재생성·적용
- AI 전체 테스트 및 실제 Provider smoke
- Frontend 전체 baseline 및 production build
- Docker 재빌드·E2E
- 브라우저 수동 검증, 모바일·접근성 수동 검증
- 전체 CI

## 남은 위험

- 의미 중복 판정은 deterministic hash와 문자 3-gram 유사도까지 구현했다. 경계 사례에 대한 structured AI distinctness judge는 권장 보강점으로 남아 있다.
- `EXPLORE`/`REFINE`/`AS_IS` 자동 선택은 선택 `LOCKED` 필드의 구체성에 기반한 결정 규칙이다. 실제 Provider 결과 품질은 Provider smoke에서 확인해야 한다.
- 기존 baseline SQL을 변경했으므로 이미 생성된 로컬 DB에는 reset 또는 별도 migration 적용이 필요하다.
- 법률 입력의 완전한 Concept-specific Fact Pattern, redesign 재검증 세부 계약, `NEEDS_FACTS` 예외 제한은 V2-3 범위다.

## 정확한 계속 지점

다음 작업은 V2-3 `Concept Legal Fact Pattern Integration`이다. 시작점은 완성된 `ConceptCandidateV2`를 Concept-specific Legal Fact Pattern으로 변환하고, distinctness 통과 후보만 공식 근거 기반 법률 검토에 전달하도록 기존 연결을 V2-3 계약으로 교체하는 것이다. V2-4의 선택·가설 결정으로 자동 진행하지 않는다.
