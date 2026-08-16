# V2-3 Concept Legal Fact Pattern 통합 — 실행 결과

## 결과

V2-3 범위를 구현했다. 차별성 검증을 통과한 `ConceptCandidateV2`는 이제 후보별 `LegalFactPattern V2`로 변환된 뒤 공식 법령 근거 조회와 법률 구현 가능성 사전검토를 받는다. 이전 후보에서 수집한 공식 근거를 다음 후보의 입력으로 재사용하던 경로를 제거했으며, 각 후보가 자체 Fact Pattern으로 법률 근거 조회를 새로 수행한다.

법률 검토 결과는 입력 Fact Pattern의 schema version과 hash에 결합된다. 적격 후보의 안전한 assessment에는 실제 검토한 Fact Pattern을 함께 저장해 화면과 후속 단계가 검토 대상을 확인할 수 있다.

## 구현한 계약

- `LegalFactPattern V2`에 다음을 포함했다.
  - 관할과 대상 지역
  - 참여자 역할과 플랫폼 역할
  - 제공자·판매자·중개자 역할
  - 거래·결제 흐름
  - 개인정보 이용과 물리적 활동
  - 파트너 모델·요건과 자격 요건
  - 광고 주장과 운영 모델
- 각 Concept 값에 `source`, `authority`, `decision`을 보존했다. 파트너 모델과 파트너 요건도 서로의 출처를 합치지 않고 독립적으로 보존한다.
- `targetRegion`, `revenueModel`, `price`를 `LEGAL_SENSITIVE`로, `channels`, `differentiators`를 `POTENTIALLY_LEGAL_SENSITIVE`로 법률 검토 전에 포함했다.
- pre-market SOM 점유율·금액 가설은 법률 Fact Pattern과 법률 Provider 입력에서 제외했다.
- `providerRole`, `sellerRole`, `intermediaryRole`을 `ConceptCandidateV2` 필수 필드로 추가해 법률 단계가 역할을 추측하지 않도록 했다.
- 사용자 외부 사실 입력은 `existingLicenses`, `mandatoryExistingPartners`, `fixedJurisdiction`, `claimedIntellectualProperty`로 제한했다.
- 현재 Seed에서 외부 사실로 전달 가능한 값은 `USER_INPUT + LOCKED` 대상 지역뿐이며 `fixedJurisdiction`으로 매핑한다. 외부 사실이 없어도 Concept 자체 Fact Pattern으로 검토를 시작할 수 있다.
- 각 Concept마다 공식 근거 조회 파이프라인을 호출한다. Context Pack에 저장된 이전 Concept 근거를 다음 Concept의 AI 입력으로 전달하지 않는다.
- Concept 설계 누락 질문은 `REDESIGNABLE + redesignRequirements`로 바꾸고 사용자에게 질문하지 않는다.
- 기존 보유 인허가, 기존 필수 계약, 실제 고정 관할, 보유 특허·라이선스처럼 AI가 설계할 수 없는 현실 사실만 `NEEDS_FACTS`로 보낸다.
- 근거가 없고 외부 사실 질문도 없는 경우 Evidence 없는 통과나 `NEEDS_FACTS` 대신 재시도 가능한 `LEGAL_SOURCE_EVIDENCE_UNAVAILABLE`로 처리한다.
- 재설계 입력에 `designGaps`와 검토한 `LegalFactPattern`을 포함했다.
- 재설계 후보는 기존 V2 순서대로 schema/LOCKED·origin/차별성을 다시 통과해야 법률 검토를 한 번 더 받을 수 있다. 다른 적격 후보와 중복이면 법률 호출 전에 거부하고 replacement로 전환한다.
- 적격 결과 저장 전에 `reviewedFactPatternSchemaVersion`과 `reviewedFactPatternHash`가 현재 후보에서 다시 계산한 값과 일치하는지 검증한다.
- 공개·비교 화면의 법률 상세에서 assessment에 저장된 제공자·판매자·중개자 역할과 결제·거래·개인정보·광고 Fact Pattern을 우선 표시한다.

## 변경 파일

### AI

- `ai/app/tasks/concept_candidate/models.py`
- `ai/app/tasks/concept_candidate/service.py`
- `ai/app/tasks/concept_legal_review/models.py`
- `ai/app/tasks/concept_legal_review/service.py`
- `ai/app/tasks/concept_redesign/models.py`
- `ai/app/tasks/concept_redesign/service.py`
- `ai/app/tools/concept_factory_provider_smoke.py`
- `ai/tests/concept_candidate_v2_fixture.py`
- `ai/tests/test_concept_factory_schema.py`
- `ai/tests/test_concept_legal_evidence.py`

### Backend

- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptLegalFactPatternMapper.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryExecutionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptCandidateV2Validator.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorker.java`
- `backend/src/main/java/com/aivle/backend/pipeline/legal/application/CanonicalLegalContextAssembler.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptCandidateV2ValidatorTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorkerTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/legal/LegalEvidenceHardeningTests.java`

### Frontend

- `frontEnd/src/features/concept-factory/components/ConceptReveal.jsx`
- `frontEnd/src/features/concept-factory/components/ConceptReveal.test.jsx`
- `frontEnd/src/features/concept-selection/components/LegalDetailDialog.jsx`

### 문서

- `docs/rebuild/progress/V2-3-CONCEPT-LEGAL-FACT-PATTERN-INTEGRATION_RESULT.md`
- `docs/rebuild/verification/V2-3-CONCEPT-LEGAL-FACT-PATTERN-INTEGRATION_USER_VERIFICATION.md`

## 실제 실행한 검사

- AI 표적 테스트: 2개 파일, 17개 테스트 통과
  - Concept별 Fact Pattern과 법률 민감 가설의 사전 전달
  - Concept별 공식 근거 조회 호출
  - SOM 법률 입력 제외
  - 설계 누락의 `REDESIGNABLE` 처리
  - 실제 외부 사실만 `NEEDS_FACTS` 처리
  - Evidence 없는 결과의 재시도 가능한 실패 처리
  - 공식 근거 중복 제거 및 finding별 근거 참조 검증
- Backend 표적 테스트: 3개 클래스, 28개 테스트 통과
  - Concept 값을 완전한 Fact Pattern으로 매핑하고 출처를 보존
  - 결제·역할 변경 시 Fact Pattern hash 변경
  - 외부 사용자 사실이 없는 경우 허용
  - 중복 후보의 법률 호출 전 차단
  - 재설계 중복 후보의 두 번째 법률 호출 전 차단
  - Worker 법률 입력에서 이전 공유 근거와 SOM 제외
- Frontend 표적 테스트: 2개 파일, 3개 테스트 통과
- 변경 Frontend 파일 ESLint: 통과
- `git diff --check`: 통과. Git의 LF→CRLF 변환 안내만 확인했다.

## 의도적으로 생략한 검사

Fast Execution Profile에 따라 다음은 실행하지 않았다.

- Backend 전체 테스트와 전체 Postgres/Testcontainers 테스트
- AI 전체 테스트와 실제 OpenAI·법령 Provider smoke
- Frontend 전체 baseline과 production build
- Docker 재빌드·E2E
- 브라우저, 모바일, 접근성 수동 검증
- 전체 CI

## 남은 위험

- 외부 현실 사실 질문과 Concept 설계 질문의 런타임 분리는 허용된 한국어 표현을 보수적으로 판별한다. 실제 Provider가 우회 표현을 사용할 때의 품질은 Provider smoke에서 확인해야 한다.
- 법령 조회 결과의 후보별 적합성과 `REDESIGNABLE` 문구 품질은 실제 Provider 연결 검증이 남아 있다.
- Context Pack 저장 구조는 기존 테이블을 유지하면서 내용만 외부 사실 전용으로 바꿨다. 기존 로컬 DB의 과거 Context Pack은 새 run/snapshot으로 재생성해 확인해야 한다.
- 전체 상태 전이와 assessment 영속화를 실제 DB로 검증하는 통합 테스트는 Fast Execution Profile에 따라 생략했다.

## 정확한 계속 지점

다음 작업은 V2-4 `Concept Selection and Hypothesis Decision`이다. 시작점은 적격·서로 다른 Concept 5개 중 하나를 선택하고, 선택 Concept의 가설을 accept/edit-and-accept/reject-request-alternative로 결정하는 영속 계약을 구현하는 것이다. `LOCKED` 값은 읽기 전용이며 legal-sensitive 변경만 Delta Legal Review를 받아야 한다. V2-5 Market Snapshot으로 자동 진행하지 않는다.
