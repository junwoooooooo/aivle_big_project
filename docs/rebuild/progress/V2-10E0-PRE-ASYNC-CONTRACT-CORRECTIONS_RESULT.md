# V2-10E0 — PRE-ASYNC CONTRACT CORRECTIONS 결과

## 상태

IMPLEMENTATION COMPLETE / RUNTIME ACCEPTANCE PENDING

## 구현한 계약

1. Idea commitment canonical 변경은 assessment를 stale로 만들고 새 `FINAL_SYNTHESIS` TaskRun을 생성한다. 실제 변경이 없는 action과 동일 idempotency replay는 중복 TaskRun을 만들지 않는다.
2. Frontend는 commitment review 응답이 `DERIVING`이면 Interpretation patch와 Confirm을 중단하고, 새 Task의 terminal 신호 뒤 Query로 Review에 복귀한다.
3. INITIAL, REPLACEMENT, REDESIGN 후보가 동일한 schema/origin/deterministic distinctness/ambiguous semantic judge/Legal pipeline을 사용한다. judge 실패 후보는 Legal로 전달하지 않는다.
4. `TARGET_REGION`을 7번째 `HypothesisType`으로 추가했다. `USER_INPUT` 또는 `USER_CONFIRMED + LOCKED` region은 선택 시 자동 확정·읽기 전용이며, 열린 region은 `AI_HYPOTHESIS + OPEN + PROPOSED`다.
5. `MarketAnalysisSeedSnapshot`의 region은 Candidate 직접 복사가 아니라 최종 `TARGET_REGION` decision에서 생성한다.
6. deterministic jurisdiction resolver를 추가하고 현재 공식 Legal 지원 범위를 KR only로 고정했다. locked foreign region은 Concept run 생성 전에, AI foreign candidate는 candidate validation에서, selection foreign edit/alternative는 Delta Legal Provider 전에 `LEGAL_JURISDICTION_UNSUPPORTED`로 차단한다.
7. active Concept Factory의 `NEEDS_FACTS`를 사용자 `NEEDS_INPUT` dead-end가 아니라 `LEGAL_EXTERNAL_FACT_UNRESOLVED` business rejection과 bounded replacement로 처리한다. Provider technical failure와 구분한다.
8. DB constraint migration으로 `TARGET_REGION` decision type을 허용했다.

## 변경 파일

### Backend

- `backend/src/main/java/com/aivle/backend/pipeline/idea/application/IdeaBriefService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryExecutionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptFactoryService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/application/ConceptLegalFactPatternMapper.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptAttemptError.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/domain/ConceptCandidateV2Validator.java`
- `backend/src/main/java/com/aivle/backend/pipeline/concept/worker/ConceptFactoryWorker.java`
- `backend/src/main/java/com/aivle/backend/pipeline/legal/application/LegalJurisdictionResolver.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/domain/HypothesisType.java`
- `backend/src/main/java/com/aivle/backend/pipeline/selection/application/ConceptSelectionService.java`
- `backend/src/main/java/com/aivle/backend/pipeline/marketseed/application/MarketAnalysisSeedSnapshotFactory.java`
- `backend/src/main/java/com/aivle/backend/common/exception/ErrorCode.java`
- `backend/src/main/resources/db/migration/V2__v2_10e0_contract_corrections.sql`
- 관련 Idea/Concept/Selection/Market Seed/Legal targeted test 파일

### AI

- `ai/app/tasks/concept_candidate/models.py`
- `ai/app/tasks/concept_candidate/service.py`
- `ai/app/tasks/concept_hypothesis_alternative/models.py`
- `ai/app/tasks/concept_hypothesis_alternative/service.py`
- `ai/app/tasks/concept_legal_review/service.py`
- 관련 Concept Candidate fixture/schema test

### Frontend

- `frontEnd/src/features/idea-intake/hooks/useIdeaIntake.js`
- `frontEnd/src/features/idea-intake/hooks/useIdeaIntake.test.jsx`
- `frontEnd/src/features/concept-selection/components/HypothesisDecisionPanel.jsx`
- `frontEnd/src/features/concept-selection/components/HypothesisDecisionPanel.test.jsx`

### 정본 문서

- `docs/rebuild/NEW_PIPELINE_MASTER_PLAN_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_PRODUCT_SPEC_v1.0.md`
- `docs/rebuild/NEW_PIPELINE_DATA_MODEL_AND_API_CONTRACT_v1.0.md`
- `docs/rebuild/ASYNC_EXECUTION_AND_JOB_EVENT_STANDARD_v1.0.md`

## 실제 실행한 검사

- Backend `compileJava`: 성공.
- Backend `testClasses`: 성공.
- Backend E0 targeted test 10개 클래스: 최초 56개 중 55개 통과, 현행 3필수필드 catalog와 불일치하던 기존 Idea assertion 1개를 수정했다. 수정 후 `IdeaBriefCanonicalizationIntegrationTests` 클래스 재실행 성공. 그 외 E0 targeted 클래스는 최초 실행에서 모두 통과했다.
- AI targeted pytest: 24 passed.
- Frontend targeted Vitest: 4 files, 17 passed.
- Frontend targeted eslint: 성공.
- `git diff --check`: 성공. Git의 LF→CRLF 안내만 있었고 whitespace error는 없었다.

## 의도적으로 생략한 검사

- 전체 Backend/AI/Frontend suite
- postgresTest/Testcontainers
- Docker rebuild/E2E
- 실제 browser E2E
- 실제 외부 Provider smoke
- Frontend production build

## 남은 위험

- KR resolver는 안전 우선 allowlist다. 지원 지역 표기 확장이 필요하면 명시적 vocabulary와 테스트를 함께 추가해야 한다.
- Flyway V2 migration은 compile/H2 targeted context에서 확인했지만 실제 운영 PostgreSQL fresh/upgrade DB 적용은 사용자 runtime gate가 필요하다.
- 실제 provider가 열린 region에 KR-compatible 값을 안정적으로 반환하는지는 provider smoke 전까지 pending이다.

## 정확한 계속 지점

다음 Unit은 `V2-10E1 — CONCEPT SELECTION ASYNC HARDENING`이다. `CONCEPT_HYPOTHESIS_ALTERNATIVE`와 `CONCEPT_DELTA_LEGAL_REVIEW`만 대상으로, 현재 동기 `ai.execute(...)`를 TaskRun worker로 이동한다. E2/E3/F/G 코드는 E1 완료 전 수정하지 않는다.
