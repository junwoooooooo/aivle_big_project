# Concept Factory 검색 정규화 결과

## 상태

- IMPLEMENTATION COMPLETE
- STATIC/TARGETED VERIFICATION COMPLETE
- RUNTIME ACCEPTANCE PENDING USER TEST

## 확정한 원인

검색 공간 포화의 직접 원인은 백엔드와 AI 계층의 이중 음성 이력 확대였다. 백엔드는 모든 슬롯의 과거 결과를 hard distinctness 비교 대상으로 사용했고, AI 계층은 적격 Concept, 실제 거절, 현재 슬롯 이력을 하나의 `avoidCandidates`로 합쳤다. 이 때문에 동일한 Idea Brief에서 파생된 정상적인 focus 변형까지 `DUPLICATE_CONCEPT`으로 축소되었다.

추가로 다음 런타임 불일치를 확인했다.

- 5개 슬롯의 초기·교체·법률 재설계를 합친 합법적 최대 검사 수는 20인데 도메인/DB 기준이 15였다.
- Provider transient retry는 호출별 최대 2회인데 DB 누적 카운터는 1까지만 허용했다.
- 법률 `NEEDS_FACTS`가 사용자 입력 대기 대신 후보 폐기·교체로 번역됐다.
- 완료 직전 5개 Concept 전체에 대한 high-confidence pairwise invariant가 없었다.

## 구현한 계약

### Search History 역할 분리

- `eligibleConceptFingerprints`: 현재 run의 최종 `ELIGIBLE` Concept만 포함하며 final hard conflict로 사용한다.
- `currentSlotSearchHistory`: 현재 슬롯의 후보 이력만 포함하며 exact/high-confidence 반복과 ambiguous semantic 비교에 사용한다.
- `softRejectedExamples`: `ConceptRejectionSummary.attemptId`가 가리키는 실제 거절 결과만 포함한다. 현재 슬롯은 제외하며 타 슬롯 soft 예제로만 Provider에 전달한다.
- 성공 결과, 적격 결과, 법률 검토 결과, Provider 실패, dangling 요약은 soft rejected history에서 제외한다.
- 타 슬롯 거절 이력은 백엔드 deterministic/semantic hard blocker에서 제거했다.

### Distinctness

- canonical hash 또는 major mechanics hash 일치는 계속 hard duplicate다.
- focus similarity와 mechanics similarity가 모두 매우 높고 focus의 material difference가 2개 미만일 때만 high-confidence duplicate로 판정한다.
- focus 축 2개 이상이 실질적으로 다르면 `DISTINCT`다.
- 경계 사례는 `AMBIGUOUS`로 semantic judge에 전달한다.
- semantic prompt는 동일 문제·사용자·Idea Brief·LOCKED 원본이 중복 증거가 아니며 실제 메커니즘·역할·흐름·수익·채널을 비교하도록 명시한다.
- pairwise `ConceptFingerprint.DistinctnessEvaluation`과 검색 문맥을 포함한 `ConceptFactoryExecutionService.DistinctnessEvaluation`에 분류, 충돌 scope/candidate/slot, focus/mechanics 유사도, 중첩 축, 실질 차이 축, 필수 변경 축을 구조화했다.
- `COMPLETED` 전 5개 Concept을 양쪽 슬롯 focus로 pairwise 재검사하며 clone이면 `FINAL_CONCEPT_SET_DUPLICATE`로 완료를 차단한다. 이 검사는 새 AI loop를 만들지 않는다.

### Targeted Replacement

- 교체 후보 입력에 `replacementContext`를 추가했다.
- 포함 정보: round, previousCandidate, rejectionReason, conflictSource, closestConflict, overlappingDimensions, materiallyDifferentDimensions, mustChangeDimensions, safeCorrectionInstruction.
- `mustChangeDimensions`는 현재 focus에서 최소 2개 축을 항상 제공한다.
- AI Provider 입력은 `finalConceptsToDifferentiateFrom`, `currentSlotHistory`, `softNegativeExamples`, `replacementFeedback`으로 분리했다. 기존 `avoidCandidates` 병합은 제거했다.

### Legal NEEDS_FACTS

- 법률 질문을 `DESIGN_GAP`, `CONTROL_CONVERTIBLE_EXTERNAL_FACT`, `UNAVOIDABLE_EXTERNAL_FACT`로 분류한다.
- design gap은 `REDESIGNABLE`로, 통제로 전환 가능한 외부 조건은 증거와 함께 Provider 판단으로 전달하거나 재설계로 처리한다.
- 회피 불가능한 실제 외부 사실만 `NEEDS_FACTS`가 된다.
- 백엔드는 `NEEDS_FACTS`를 `NEEDS_INPUT`으로 번역하며 후보를 폐기하거나 교체하지 않는다.
- `RunResponse.requiredInputs`에 `code`, `question`, `source`, `candidateSlot`을 제공하고 다음 동작을 `PROVIDE_REQUIRED_INPUTS`로 표시한다.
- 답변 저장/재개 API는 기존에 없으므로 이번 범위에서는 안전한 terminal `NEEDS_INPUT`까지만 구현했다.

### Runtime budget / DB

- `MAX_INSPECTED_CANDIDATES = SLOT_COUNT * (1 + MAX_REPLACEMENT_ROUNDS + MAX_LEGAL_REDESIGNS_PER_SLOT) = 20`으로 파생 계산한다.
- 도메인은 20을 초과하면 `INSPECTION_BUDGET_EXHAUSTED`를 발생시킨다.
- V9는 V1~V8을 수정하지 않고 `inspected_candidate_count >= 0`, `provider_transient_retry_count >= 0`만 DB에 강제한다.
- 호출별 Provider retry 제한은 Java 정책에 유지하고 DB는 run 누적값 2 이상을 허용한다.
- 지표 관계는 각 candidate result가 validation에 진입할 때 `initial + replacement + redesign = inspected`이며 Provider retry는 별도 카운터다.

### Job Center

- 백엔드 active/recent는 TaskRun terminal truth를 기준으로 유지한다.
- 실패한 Concept Factory TaskRun이 active에서 제외되고 recent failed에 포함되는 테스트를 추가했다.
- rejection event의 `taskType`은 attempt phase에 따라 candidate/legal review/redesign으로 기록한다.
- 프런트엔드는 terminal SSE 수신 시 active/recent와 선택 상태를 즉시 재조회하는 기존 동작을 직접 테스트로 재검증했다.

## 변경 파일

- `ai/app/tasks/concept_candidate/models.py`
- `ai/app/tasks/concept_candidate/service.py`
- `ai/app/tasks/concept_distinctness_judge/service.py`
- `ai/app/tasks/concept_legal_review/service.py`
- `ai/tests/test_concept_factory_schema.py`
- `ai/tests/test_concept_legal_evidence.py`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/api/ConceptFactoryApiModels.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryExecutionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFactoryCompletionPolicy.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFactoryLimits.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFactoryRun.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptFingerprint.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/repository/ConceptRejectionSummaryRepository.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorker.java`
- `backend/src/main/resources/db/migration/V9__concept_factory_runtime_budget_constraints.sql`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryFingerprintHistoryTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryFiveSlotTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryLegalNeedsFactsTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryLimitTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/ConceptFactoryMigrationContractTests.java`
- `backend/src/test/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorkerTests.java`
- `backend/src/test/java/com/aivle/backend/postgres/PostgreSqlBaselineMigrationTests.java`
- `backend/src/test/java/com/aivle/backend/taskrun/service/ProjectJobQueryServiceTests.java`
- `docs/rebuild/progress/CONCEPT-FACTORY-SEARCH-NORMALIZATION_RESULT.md`
- `docs/rebuild/verification/CONCEPT-FACTORY-SEARCH-NORMALIZATION_USER_VERIFICATION.md`

## 실제 실행한 검사

- AI 직접 관련 pytest: 30 passed.
- Backend `compileJava`: PASS.
- Backend 직접 관련 테스트 8개 클래스: 51 passed.
- Frontend Job Center 테스트: 8 passed.
- Frontend 변경 관련 ESLint: PASS.
- `git diff --check`: PASS.

## 의도적으로 생략한 검사

- 실제 AI Provider 호출.
- 실제 MOLEG/법령 Provider smoke.
- Docker 및 Testcontainers 기반 PostgreSQL migration runtime test.
- 실제 5-slot runtime 실행.
- 브라우저 E2E.
- 전체 backend/frontend/AI 회귀 suite.
- frontend production build.

## 남은 위험

- V9의 fresh V1→V9 및 V8→V9 실행 테스트 코드는 추가했지만 Docker 금지 경계 때문에 실제 PostgreSQL에서 실행하지 않았다.
- `requiredInputs` 답변 저장 및 resume endpoint는 현재 제품에 존재하지 않는다. 따라서 법률 외부 사실 질문은 안전하게 `NEEDS_INPUT`에서 멈추며 자동 재개되지 않는다.
- 실제 Provider가 분리된 문맥과 targeted replacement 지시를 얼마나 안정적으로 따르는지는 사용자 runtime acceptance에서 확인해야 한다.

## 정확한 계속 지점

`docs/rebuild/verification/CONCEPT-FACTORY-SEARCH-NORMALIZATION_USER_VERIFICATION.md`의 plain Docker Compose 절차로 V9 적용, 5-slot 실행, 교체 피드백, `NEEDS_INPUT`, Job Center terminal 이동을 확인한다. 확인 전에는 실제 5/5 runtime 성공을 주장하지 않는다.
