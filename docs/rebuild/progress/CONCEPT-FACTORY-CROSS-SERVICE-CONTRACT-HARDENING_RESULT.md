# Concept Factory Cross-Service Contract Hardening 결과

## 상태

- `IMPLEMENTATION COMPLETE`
- `STATIC/TARGETED VERIFICATION COMPLETE`
- `RUNTIME ACCEPTANCE PENDING USER TEST`

실제 Provider, MOLEG, Docker 전체 E2E, 브라우저 E2E, 실제 5 Slot Run은 이번 작업에서 실행하지 않았다.

## 1. Confirmed Root Cause

실제 코드에서 Backend `ConceptFingerprint.businessSummary()`는 focus-aware distinctness를 위해 21개 필드를 생성하고 있었지만, AI의 `ConceptCandidateInput`과 `ConceptDistinctnessJudgeInput`은 서로 복제된 13필드 Pydantic 모델을 사용하고 있었다. 두 모델 모두 `extra="forbid"`이므로 Backend가 추가한 `featureSet`, `actorRoles`, `price`, `paymentFlow`, `personalDataUsage`, `physicalActivities`, `partnerRequirements`, `qualificationRequirements`가 `extra_forbidden`으로 거부됐다.

Worker는 이 AI 내부 입력 오류를 `retryable=false`만 보고 `PERMANENT_PROVIDER_FAILURE`로 분류했고, run-global deterministic 오류임에도 다음 Slot을 계속 호출했다. 법률 재설계 소진 경로는 rejection Timeline을 발행하면서 `ConceptRejectionSummary`를 저장하지 않아 폐기 metric이 0으로 남을 수 있었다.

## 2. Backend 21-field contract

Backend canonical producer는 다음 21개 필드를 사용한다.

1. `targetUsers`
2. `problemScenario`
3. `coreValue`
4. `solutionMechanism`
5. `revenueModel`
6. `channels`
7. `platformRole`
8. `operatingModel`
9. `partnerModel`
10. `transactionFlow`
11. `providerRole`
12. `sellerRole`
13. `intermediaryRole`
14. `featureSet`
15. `actorRoles`
16. `price`
17. `paymentFlow`
18. `personalDataUsage`
19. `physicalActivities`
20. `partnerRequirements`
21. `qualificationRequirements`

문자열 필드는 항상 `String`, 흐름·기능·역할·요건 필드는 항상 `List<String>`으로 직렬화한다. 누락 배열은 빈 목록, 잘못 들어온 단일 문자열 배열 축은 1개 원소 목록으로 정규화한다. key ordering에는 의미를 두지 않는다.

## 3. 기존 Python 13-field contract

기존 `AcceptedConceptFingerprint`와 Distinctness Judge의 `BusinessFingerprint`는 첫 13개 축만 각각 선언했다. 이 중복 DTO가 Backend 확장과 독립적으로 남아 producer/consumer drift를 만들었다.

## 4. Canonical BusinessFingerprint

`ai/app/contracts/concept_fingerprint.py`에 strict `BusinessFingerprint v1`, 21개 `FingerprintDimension`, `BUSINESS_FINGERPRINT_FIELDS`를 정의했다. Candidate와 Distinctness가 모두 이 모델을 import한다. `extra="forbid"`는 유지했고 unknown field 회귀 테스트를 추가했다.

문자열/목록 길이와 필수 목록 정책은 CandidateV2 business content 계약에 맞췄다. `transactionFlow`, `featureSet`, `actorRoles`, `paymentFlow`는 최소 1개이며 개인정보·물리활동·파트너·자격 요건 목록은 빈 목록을 허용한다.

## 5. Candidate Input 변경

`acceptedConceptFingerprints`, `rejectedConceptFingerprints`, `currentSlotPreviousFingerprints`가 모두 동일한 canonical `BusinessFingerprint` 목록을 사용한다. 세 목록이 모두 non-empty인 실제 입력 fixture를 Python이 직접 validation한다. Provider에는 전체 Candidate history 대신 deduplicated 21필드 fingerprint만 전달하는 기존 경계를 유지했다.

## 6. Distinctness Judge 변경

`candidateA`, `candidateB`가 Candidate와 동일한 canonical 모델을 사용한다. `overlappingDimensions`와 `materiallyDifferentDimensions`는 임의 문자열이 아니라 21개 `FingerprintDimension` Literal만 허용하고 최대 길이를 21로 변경했다. Prompt에도 기능·행위자·가격·결제·개인정보·물리활동·파트너·자격 축을 간결하게 추가했다.

기존 focus rule은 변경하지 않았다.

- `CUSTOMER_EXPERIENCE`: problem/solution/feature/core value
- `OPERATING_MODEL_AND_PARTNERS`: actors/operation/partners/roles/transaction
- `REVENUE_AND_PRICING`: revenue/price/payment
- `CHANNEL_AND_SCALE`: channels/platform/transaction/operation
- `LOW_RISK_FAST_EXECUTION`: data/physical/partners/qualification/operation

## 7. Shared contract fixture

- `contracts/concept/business-fingerprint-v1.json`: 21개 필드와 실제 타입을 가진 단일 fingerprint fixture
- `contracts/concept/concept-candidate-input-v1.json`: accepted/rejected/current-slot이 모두 non-empty인 Candidate 입력 fixture
- `contracts/concept/README.md`: producer, consumers, versioning, 보안 경계

Backend는 같은 fixture의 key set/type과 `businessSummary()` 결과를 비교한다. Python은 같은 fixture를 `BusinessFingerprint`, `ConceptCandidateInput`, `ConceptDistinctnessJudgeInput`으로 검증한다.

## 8. Request failure taxonomy

Before:

`INVALID_REQUEST / FIELD_CONSTRAINT_VIOLATION → PERMANENT_PROVIDER_FAILURE`

After:

`INVALID_REQUEST` 및 내부 contract/task schema version 위반 → `REQUEST_CONTRACT_INVALID`

`RESULT_SCHEMA_INVALID`는 결과 schema 오류로, transient dependency는 `TRANSIENT_PROVIDER_FAILURE`로, nonretryable Provider/config failure는 `PERMANENT_PROVIDER_FAILURE`로 유지한다. Parent TaskRun에는 `EXECUTION_FAILED / REQUEST_CONTRACT_INVALID`를 기록하며 `AI_SERVICE_UNAVAILABLE`로 공개하지 않는다.

## 9. Run-global fatal behavior

`REQUEST_CONTRACT_INVALID`는 첫 발생 Slot의 Attempt에 `retryable=false`, safe code `REQUEST_CONTRACT_INVALID`로 저장하고 Run을 즉시 `FAILED`로 만든다. Worker는 `FATAL_FAILURE`를 반환해 나머지 Slot의 `beginAttempt`와 AI 호출을 만들지 않는다.

`AI_CONFIGURATION_INVALID`, `LEGAL_CONFIGURATION_INVALID`, 내부 service token/auth configuration 오류도 run-global fatal로 중단한다. 일반 Candidate domain duplicate/origin/locked/legal rejection은 기존 replacement 경로를 유지한다.

Retry policy는 요청 계약 오류에 대해 `canResume=false`, `nextAction=FIX_SYSTEM_AND_START_NEW_RUN`을 반환하며 retry endpoint가 기존 authoritative guard로 거부한다.

## 10. Discard metric

`ConceptRejectionSummary`에 nullable `attempt_id`를 추가하고 non-null 값에 unique index를 적용했다. 실제 후보 폐기는 Attempt와 1:1로 저장하며 같은 Attempt 재처리는 summary를 중복 생성하지 않는다.

`LEGAL_REDESIGN_EXHAUSTED`에서 후보 Attempt를 terminal rejection으로 기록하고 summary 저장 후 rejection Timeline을 발행한다. Duplicate, origin/locked invalid, legal rejected, unresolved external facts의 기존 저장 경로도 같은 attempt identity를 사용한다. Provider 및 request contract failure는 discard 경로를 호출하지 않는다.

V8 이전 row는 `attempt_id=NULL`로 유지하므로 기존 증거 Run을 수정하지 않는다.

## 11. Summary metrics

| API/UI metric | 권위 source와 의미 |
|---|---|
| `eligibleCount` | DB Slot status가 `ELIGIBLE`인 수 |
| `initialCandidateSuccessCount` | `INITIAL` phase에서 result가 저장된 후보 수 |
| `generatedCandidateCount` | 호환용 전체 비법률 Candidate result 수 |
| `replacementCandidateCount` | `REPLACEMENT` phase 성공 후보 수 |
| `redesignCount` | 검증을 통과해 Slot에 기록된 성공한 redesign 수 |
| `inspectedCandidateCount` | `ConceptFactoryRun.inspected_candidate_count` persisted counter |
| `discardedCandidateCount` | DB `concept_rejection_summaries` 누적 수 |
| `candidateGenerationFailureCount` | result 없이 error classification으로 끝난 비법률 Attempt 수 |
| `providerTransientRetryCount` | DB persisted Provider transient retry 수 |

API가 `inspectedCandidateCount` 자리에 우연히 같은 값이던 `generated` 계산값을 전달하던 오류를 수정했다. UI는 신규/대체/재설계 성공을 분리해 중복 해석을 줄였고 `생성/시스템 실패` 의미를 명시했다.

## 12. Job Center UX

선택 Timeline header에 task type, 표시 status, 로컬 시간을 표시한다. 종료 notice에도 task type을 포함한다. 자동 선택 상태에서는 새 active job을 선택하고 해당 job이 terminal이 되면 같은 Timeline을 유지한다. 사용자가 과거 job을 직접 선택하면 이후 refresh가 그 선택을 덮어쓰지 않는다.

Concept debug Timeline에는 `errorClassification`, `safeErrorCode`, `safeReason`, `failedField`, `retryable`을 표시한다. 요청 계약 failure event는 실제 validation field를 포함한다. raw prompt/provider body/stack trace/secret은 추가하지 않았다. Docker frontend build가 `VITE_ENABLE_PIPELINE_DEBUG` build arg를 받을 수 있게 했다.

## 13. Migration

`V8__concept_factory_cross_service_contract_hardening.sql`을 추가했다.

- `ck_concept_attempt_error`에 `REQUEST_CONTRACT_INVALID` 추가
- `concept_rejection_summaries.attempt_id` 추가
- `concept_attempts(id)` FK 추가
- non-null `attempt_id` unique partial index 추가
- V6/V7은 수정하지 않음

빈 PostgreSQL schema의 V1→V8 및 기존 V1→V7 schema의 V8 upgrade를 Testcontainers로 검증했다.

## 14. Tests executed

- Python compile: `ai/app` PASS
- Python targeted pytest: 17 passed
- Backend `compileJava`: PASS
- Backend targeted tests: 6 classes, 45 passed
- PostgreSQL migration tests: 2 passed
- Frontend changed-file ESLint: PASS
- Frontend targeted Vitest: 3 files, 10 passed
- `git diff --check`: PASS

초기 Frontend 명령은 Windows 실행 정책이 `npm.ps1`을 차단해 시작되지 않았고, 같은 범위를 `npm.cmd`로 실행해 PASS했다. 초기 sandbox Backend compile/test는 Gradle distribution network 접근이 차단됐고 승인된 실행으로 동일 명령을 수행해 PASS했다.

## 15. Tests not executed

- 실제 AI Provider 호출
- 실제 MOLEG 호출
- `concept_factory_provider_smoke`
- Docker Compose 전체 E2E 및 실제 5 Slot Run
- 브라우저 E2E/수동 UI 검증
- Backend/AI/Frontend 전체 regression suite
- Frontend production build
- Docker volume 삭제/초기화

## 16. Runtime acceptance pending

구현과 정적/targeted 검증은 완료됐지만 실제 Runtime Acceptance는 사용자 테스트 전까지 pending이다. `extra_forbidden` 0건, Slot 2+ 실제 Candidate 호출, 조건부 Distinctness Judge 21필드 호출, Timeline/summary 폐기 수 일치는 새 Run에서 확인해야 한다.

## 17. Remaining risks

- 실제 Provider 결과가 AMBIGUOUS 또는 `LEGAL_REDESIGN_EXHAUSTED` 경로를 만들지 않으면 해당 Runtime 분기는 한 Run에서 관찰되지 않을 수 있다.
- cross-language source는 shared fixture와 양쪽 테스트로 고정했지만 Java/Python 코드를 하나의 schema generator에서 자동 생성하지는 않는다. breaking change 시 v2 fixture와 양쪽 테스트를 함께 갱신해야 한다.
- V8 이전 rejection summary는 원본 Attempt identity가 없으므로 `attempt_id`를 소급 채우지 않았다.
- 실제 Provider/MOLEG 품질, rate limit, 인증 설정은 이번 저비용 검증 범위 밖이다.

## 변경 파일

### Shared contract

- `contracts/concept/README.md`
- `contracts/concept/business-fingerprint-v1.json`
- `contracts/concept/concept-candidate-input-v1.json`

### AI

- `ai/app/contracts/__init__.py`
- `ai/app/contracts/concept_fingerprint.py`
- `ai/app/tasks/concept_candidate/models.py`
- `ai/app/tasks/concept_candidate/service.py`
- `ai/app/tasks/concept_distinctness_judge/models.py`
- `ai/app/tasks/concept_distinctness_judge/service.py`
- `ai/tests/test_business_fingerprint_contract.py`
- `ai/tests/test_concept_distinctness_judge.py`

### Backend

- `backend/src/main/java/com/aivle/backend/pipeline/concept/api/ConceptFactoryApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryExecutionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryRetryPolicy.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptAttemptError.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFingerprint.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptRejectionSummary.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/repository/ConceptRejectionSummaryRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorker.java`
- `backend/src/main/java/com/aivle/backend/taskrun/service/TaskRunService.java`
- `backend/src/main/resources/db/migration/V8__concept_factory_cross_service_contract_hardening.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryFingerprintHistoryTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryMigrationContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryRetryPolicyTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFingerprintTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorkerTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`

### Frontend/Compose

- `frontEnd/Dockerfile`
- `frontEnd/src/features/concept-factory/model/conceptFactoryModel.js`
- `frontEnd/src/features/concept-factory/model/conceptFactoryModel.test.js`
- `frontEnd/src/features/concept-factory/pages/ConceptFactoryPage.jsx`
- `frontEnd/src/features/job-center/JobCenter.jsx`
- `frontEnd/src/features/job-center/JobCenter.test.jsx`
- `frontEnd/src/features/job-center/useProjectJobs.js`
- `frontEnd/src/features/job-center/useProjectJobs.test.jsx`
- `compose.yaml`

### Governing/result documents

- `docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md`
- `docs/rebuild/decisions/DECISION_LOG.md`
- `docs/rebuild/progress/CONCEPT-FACTORY-CROSS-SERVICE-CONTRACT-HARDENING_RESULT.md`
- `docs/rebuild/verification/CONCEPT-FACTORY-CROSS-SERVICE-CONTRACT-HARDENING_USER_VERIFICATION.md`

## Exact continuation point

`docs/rebuild/verification/CONCEPT-FACTORY-CROSS-SERVICE-CONTRACT-HARDENING_USER_VERIFICATION.md` 순서대로 V8 포함 이미지를 build하고, 기존 confirmed Idea Brief에서 새 Concept Factory Run을 한 번 시작해 Runtime Acceptance를 수행한다. 기존 실패 Run 두 개는 수정하거나 resume하지 않는다.
