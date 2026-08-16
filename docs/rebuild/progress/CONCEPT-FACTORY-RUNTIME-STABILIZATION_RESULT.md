# Concept Factory 런타임 안정화 결과

## 1. 실행 요약

구현 완료, 정적·컴파일 검증 완료, 실제 런타임 인수 검증은 사용자 테스트 대기 상태다.

이번 작업은 Concept 후보의 사업 내용만 LLM이 생성하고, 메타데이터·거버넌스·LOCKED 보존은 코드가 결정하도록 경계를 재구성했다. 동일 입력을 다시 보내던 가짜 `REPAIR`, 이전 실패 후보를 모르는 생성 입력, 예외 시 dangling Attempt, 오류 원인 덮어쓰기, 502 중심의 불충분한 진단, 제한적인 JobEvent/프런트 추적을 함께 보완했다.

## 2. 코드에서 확인된 원인

1. `ConceptCandidateResult` 전체가 Provider schema여서 LLM이 사업 내용과 31개 `valueSemantics`, `candidateIndex`, `generationStrategy`, `originalCandidate`를 모두 생성해야 했다.
2. LOCKED `targetRegion`은 Provider 결과가 틀리면 사후 오류로 거부할 뿐, 최종 후보에서 Seed 값을 코드로 복원하지 않았다.
3. 백엔드의 schema `REPAIR`는 같은 TaskType과 같은 입력을 그대로 다시 호출했다.
4. 다음 후보 생성에는 적격 후보 fingerprint만 전달했고, 폐기·실패·현재 Slot의 이전 후보는 전달하지 않았다.
5. Worker의 예상 밖 예외 경계는 활성 `conceptAttemptId`를 잃은 채 Slot만 실패시켜 `result_json`, `error_classification`, `safe_error_code`가 모두 `NULL`인 Attempt가 남을 수 있었다.
6. replacement 소진 시 현재 Attempt의 실제 오류를 `INTERNAL_EXECUTION_ERROR`로 다시 기록할 수 있었다.
7. AI API는 ProviderFailure를 구조화해 기록하지 않았고 Pydantic 실패 필드가 백엔드까지 전달되지 않았다.
8. Java enum의 `LEGAL_EXTERNAL_FACT_UNRESOLVED` 및 새 exhaustion 분류와 DB `ck_concept_attempt_error`가 일치하지 않았다.
9. Concept Factory 부모 TaskRun은 슬롯 사이 heartbeat가 없고 8분 고정 deadline을 사용했다.
10. 프런트 Timeline은 Slot 번호와 이벤트 이름만 표시해 실패 단계·작업 유형·안전 오류·실패 필드·소요 시간을 확인할 수 없었다.

실제 REDESIGN `IllegalArgumentException`의 정확한 발생 문장은 기존 로그에 없으므로 확정하지 않았다. 다만 해당 예외가 dangling Attempt로 이어지는 코드 경로는 확인했다.

## 3. 아키텍처 변경

LLM 책임:

- Concept의 이름·정의·가치·가설 내용·해결 방식·운영 구조·법률 Fact Pattern 입력용 사업 내용 생성
- variation focus별 실질적인 사업 축 차이 생성
- 실제 content 필드가 불완전할 때 이전 초안과 실패 필드를 받은 1회 한정 repair

결정적 시스템 책임:

- `schemaVersion`, `generationStrategy`, `candidateIndex`, `originalCandidate`
- 31개 `valueSemantics`
- Seed 기반 `source`, `authority`, `decision`
- LOCKED direct value의 강제 복원
- AS_IS Candidate 1의 원안 필드 보존
- LOCKED 제약의 `constraintCompliance` 반영
- KR 지원 관할 검증
- Attempt terminalization, 오류 보존, bounded retry/replacement

## 4. 변경 파일

- `ai/app/tasks/concept_candidate/models.py`: Provider draft와 canonical CandidateV2 분리, 실패·현재 Slot fingerprint 입력 추가
- `ai/app/tasks/concept_candidate/service.py`: 결정적 정규화, LOCKED 강제 보존, 실제 content repair, variation 규칙, avoid 후보 조립
- `ai/app/tasks/concept_redesign/service.py`: REDESIGN도 사업 초안만 생성하고 기존 LOCKED/메타데이터를 코드로 복원
- `ai/app/providers/structured.py`: Provider JSON 실패 분류와 안전 validation detail 운반
- `ai/app/api/executions.py`: ProviderFailure 구조화 로그와 안전 필드 응답
- `ai/app/tasks/concept_legal_review/service.py`, `ai/app/tasks/concept_distinctness_judge/service.py`: Pydantic 결과 실패 세분화
- `backend/.../ConceptFactoryExecutionService.java`: 실패·현재 후보 fingerprint, 결과 보존형 Attempt 종료, dangling 방지, 실패 원인 조회
- `backend/.../ConceptFactoryWorker.java`: blind repair 제거, 원인 보존, heartbeat, 상세 예외 로그, 안전 JobEvent trace
- `backend/.../ConceptCandidateV2Validator.java`: AS_IS의 `USER_INPUT`/`USER_CONFIRMED` 정책 정렬
- `backend/.../ConceptAttemptError.java`: exhaustion 분류 추가
- `backend/.../InternalAiExecutionClient.java`: 새 안전 diagnostic reason 허용
- `backend/.../TaskRunService.java`: schema/domain 실패를 `AI_SERVICE_UNAVAILABLE`로 오분류하지 않도록 public code 보정
- `backend/src/main/resources/db/migration/V6__concept_factory_runtime_stabilization.sql`: Attempt enum/CHECK 정합성과 terminal shape 제약
- `frontEnd/.../ConceptTimeline.jsx`, `jobEventMessages.js`, `concept-factory.css`: 실행 상세 보기와 안전 trace 표시
- 관련 AI/백엔드 회귀 테스트: 새 결정적 계약과 blind repair 제거 정책 반영

## 5. 후보 계약 변경

이전:

- Provider가 완전한 `ConceptCandidateV2`와 31개 거버넌스를 한 번에 생성
- 잘못된 LOCKED semantics는 502 schema failure

변경 후:

- Provider는 `ConceptCandidateDraft` 사업 내용만 생성
- 코드가 canonical `ConceptCandidateV2`를 조립
- Provider schema에는 `valueSemantics`, `candidateIndex`, `generationStrategy`, `originalCandidate`가 없음
- canonical 결과는 기존 Java·Selection·Market 소비 계약을 유지

## 6. 거버넌스와 LOCKED 처리

- `USER_INPUT + LOCKED` → `USER_INPUT + LOCKED + ACCEPTED`
- `USER_CONFIRMED + LOCKED` → `USER_CONFIRMED + LOCKED + ACCEPTED`
- 비어 있던 7개 가설형 값 → `AI_HYPOTHESIS + OPEN + PROPOSED`
- 일반 Concept 설계값 → `CONCEPT_GENERATED + OPEN + PROPOSED`
- `targetRegion`, `revenueModel`, `price`, `channels`, `differentiators`의 LOCKED 값은 LLM 초안과 달라도 Seed 값으로 강제 복원
- AS_IS Candidate 1의 `conceptDefinition`, `problemScenario`, `targetUsers`는 확인된 원본 필드로 복원
- pre-market SOM 두 필드는 항상 AI 가설로 유지

## 7. Repair·Replacement·다양성

- 백엔드의 동일 입력 schema 재호출을 제거했다.
- Provider가 반환한 사업 초안의 content 검증이 실패한 경우에만 `previousCandidate`, `failureCode`, `failedFields`, `requiredCorrection`, strategy/index/focus, LOCKED 제약을 포함해 1회 repair한다.
- 다음 생성은 적격 후보, 폐기·실패 후보, 현재 Slot 이전 후보의 canonical business fingerprint를 함께 받는다.
- focus별 최소 변화 축을 명시했다. 이름 변경만으로 차이를 만들지 않으며 고객 경험, 운영·파트너, 수익·가격, 채널·확장, 저위험 실행 구조를 각각 다르게 설계한다.

## 8. REDESIGN

- REDESIGN Provider도 사업 초안만 반환하고 원 후보의 시스템 메타데이터·LOCKED 값·거버넌스를 코드가 복원한다.
- REDESIGN 후에도 schema → origin/LOCKED → deterministic/semantic distinctness → legal 순서를 유지한다.
- legal review 결과를 오류 분류와 함께 `result_json`에 보존한다.
- 예외 시 최신 active Attempt를 `INTERNAL_STATE_FAILURE`로 terminalize하고, 개발 로그에는 exception type, 안전화된 message, stack trace를 남긴다.
- 정확한 기존 `IllegalArgumentException` 발생 원인은 실제 새 stack trace 확인이 필요하다. **런타임 확인 필요**.

## 9. 오류 분류

- Provider JSON 해석 실패: `PROVIDER_JSON_INVALID`
- Pydantic canonical 결과 실패: `PYDANTIC_RESULT_VALIDATION_FAILED`
- content 필드 누락: `CONTENT_FIELD_MISSING`
- 거버넌스 불일치: `GOVERNANCE_SEMANTICS_MISMATCH`
- 후보 메타데이터 오류: `CANDIDATE_METADATA_INVALID`
- bounded 소진: `SCHEMA_REPAIR_EXHAUSTED`, `DISTINCTNESS_EXHAUSTED`, `REPLACEMENT_EXHAUSTED`, `LEGAL_REDESIGN_EXHAUSTED`
- replacement 소진은 현재 Attempt의 실제 root classification과 safe reason을 덮어쓰지 않는다.

## 10. 관측성

AI 서버 로그는 task type, TaskRun/Attempt ID, correlation ID, code, reason, retryable, schema, upstream status, 허용된 Provider error type/param, validation field를 기록한다. Prompt, Provider body, Authorization, key/token은 기록하지 않는다.

백엔드 로그는 Slot 예외의 run/slot/status/phase/safe code와 개발용 stack trace를 기록한다. JobEvent의 안전 payload에는 slot/focus/phase/task type/attempt/event/safe code/reason/failed field/retryable/duration/correlation ID가 포함된다.

프런트는 `VITE_ENABLE_PIPELINE_DEBUG=true`에서 위 안전 정보를 “실행 상세 보기”에 표시한다. Query API가 정본이고 SSE는 갱신 신호와 실행 추적 역할만 유지한다.

## 11. 데이터베이스 변경

신규 V6 migration은 기존 migration을 수정하지 않는다.

- `ck_concept_attempt_error`에 Java enum과 실제 사용 오류를 정렬
- `LEGAL_EXTERNAL_FACT_UNRESOLVED` 추가
- exhaustion 분류 추가
- `error_classification`이 있으면 `safe_error_code`도 반드시 존재하고, 오류가 없으면 retryable이 false인 terminal shape CHECK 추가

실제 DB migration 적용은 실행하지 않았다.

## 12. Selection·Market 호환성

정적 검토 결과:

- 7개 `HypothesisType`과 V2 migration의 `TARGET_REGION`이 일치한다.
- Selection 초기화는 canonical Candidate의 `valueSemantics`를 사용하며 LOCKED를 mutation 불가로 유지한다.
- Market Seed는 `originalSeed`, `aiInterpretation`, `selectedConcept`, `finalHypotheses`, `legalResult`를 유지한다.
- `selectedConcept`은 identity/solution/operation/valueSemantics/canonicalHash를 유지한다.
- 최종 `targetRegion`은 Candidate 원문이 아니라 `TARGET_REGION` decision에서 공급된다.
- Market handoff는 `MARKET_ANALYSIS`, snapshot ID/hash/본문, `START_MARKET_ANALYSIS`를 유지한다.

## 13. Codex가 수행한 검증

통과:

- `python -m compileall app`
- `backend\\gradlew.bat compileJava`
- `ConceptFactoryWorkerTests` 20건
- `ConceptCandidateV2ValidatorTests` 8건
- 변경 프런트 파일 ESLint
- `git diff --check`

실행되지 않음:

- Python pytest: 로컬 Python에 `pytest`와 `pydantic`이 설치되어 있지 않아 실행 불가. 추가 설치는 하지 않음.

## 14. 수행하지 않은 검증

- 실제 AI Provider 호출
- `concept_factory_provider_smoke`
- MOLEG 실제 호출
- Docker Compose build/up/down
- 실제 5 Slot 생성
- 브라우저 E2E
- Selection·Market Seed finalize·module handoff E2E
- 전체 백엔드/AI/프런트 테스트 suite
- DB migration 실제 적용

## 15. 사용자 런타임 테스트 계획

정확한 명령과 순서는 `docs/rebuild/verification/CONCEPT-FACTORY-RUNTIME-STABILIZATION_USER_VERIFICATION.md`에 기록했다.

## 16. 성공 시 예상 결과

- 최소 Seed는 `EXPLORE`로 실행된다.
- Provider smoke의 locked `targetRegion`은 LLM 추측과 무관하게 `대한민국 / USER_* / LOCKED / ACCEPTED`로 정규화된다.
- 5개 Slot이 모두 `ELIGIBLE`일 때만 Run이 `COMPLETED`가 된다.
- terminal Run 이후 dangling Attempt 조회 결과가 0건이다.
- schema 실패의 safe reason이 `INTERNAL_EXECUTION_ERROR` 또는 `AI_SERVICE_UNAVAILABLE`로 덮이지 않는다.
- REDESIGN 실패 시 로그에서 예외 message와 stack trace, 프런트 trace에서 안전 단계 정보가 보인다.
- 선택 후 7개 가설, Market Seed Snapshot, module handoff 본문이 생성된다.

## 17. 실패 자료 수집

실패 시 다음을 함께 수집한다.

- `backend`, `ai-server`의 Concept/REDESIGN/schema/validation 관련 로그
- 프런트 “실행 상세 보기”의 해당 Slot trace
- Run/Slot/Attempt/TaskRun SQL 조회 결과
- Market Seed Snapshot 및 module handoff SQL 조회 결과

secret, Authorization, raw prompt, Provider raw body는 전달하지 않는다.

## 18. 남은 런타임 위험

- 실제 Provider가 축소된 Draft response schema를 안정적으로 수용하는지 미확인
- 실제 MOLEG 근거 조회와 REDESIGN 후 재검토 경로 미확인
- V6 migration의 실제 운영 DB 적용 미확인
- 장시간 5 Slot 실행에서 heartbeat/deadline이 충분한지 미확인
- SSE replay와 프런트 debug trace의 실제 브라우저 표시 미확인
- 기존 장애의 `IllegalArgumentException` 정확한 발생 지점은 새 stack trace로 확인 필요

## 19. `git diff --stat` 및 정확한 계속 지점

추적 중인 18개 파일의 통계는 `694 insertions(+), 152 deletions(-)`이다. 아래 신규 파일 3개는 아직 추적되지 않아 해당 통계에 포함되지 않는다.

- `backend/src/main/resources/db/migration/V6__concept_factory_runtime_stabilization.sql`
- `docs/rebuild/progress/CONCEPT-FACTORY-RUNTIME-STABILIZATION_RESULT.md`
- `docs/rebuild/verification/CONCEPT-FACTORY-RUNTIME-STABILIZATION_USER_VERIFICATION.md`

사용자가 검증 문서의 Test 1부터 실제 Runtime Acceptance를 수행한다. 첫 실패가 발생하면 반복 실행을 중단하고 해당 Run ID, 안전 로그, Debug Trace, SQL 결과를 제공한다. 다음 재구축 단계로 자동 진행하지 않는다.
