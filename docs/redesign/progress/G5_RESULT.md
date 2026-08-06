# G5 Concept Core 구현 결과

- 기준 브랜치: `feature/conversational-validation-workspace`
- 기준 SHA: `060020daa7276486ba378705a625598a3ca969d6`
- 구현 일자: 2026-08-05
- 범위: READY Regulatory Boundary와 Confirmed Brief 기반 Concept Batch/Slot/Attempt, deterministic trace, 법률 구현 가능성 평가, durable worker, 공개 Concept 계약
- 제외: G6 Workboard UI, G7 Quick Assessment/선택, G9 Legal Report, 기존 Journey 삭제, 수동 브라우저 검증

## 1. 현재 구현 감사 결과

- `journey_provider.py`는 단일 후보 fan-out과 concurrency 설정을 이미 가졌으나 AI 출력에 `sourceValue`, authoritative `originTrace`, `legalTrace`를 요구했다.
- fatal provider failure가 하나라도 있으면 sibling schema repair 전체를 건너뛰는 조건이 있었다. 이 조건을 제거해 각 slot의 정상 결과와 repair를 독립 보존했다.
- 기존 `ConceptJourneyService`는 Idea Origin/Legal Guardrail 기반 process-local aggregate 실행이며 `PASS/FAIL_LEGAL` 중심이다. G5 durable path로 승격하지 않고 legacy fallback으로 유지했다.
- 기존 Concept batch/draft/version 테이블은 독립 Slot, Attempt Phase, provider/schema failure, 5단계 legal assessment, validated hash 및 내부 draft 비노출을 구조적으로 표현하지 못했다.
- 기존 `ConceptVersion.eligible()`는 positioning을 summary/value proposition에 중복 매핑하고, target segment JSON을 문자열로 축약하며, origin trace를 problem에, feature set을 solution에 저장하고 risks를 빈 배열로 저장한다. 기존 행의 의미를 추측 보정하지 않고 legacy 조회를 유지했다. 신규 G5 저장 계약은 전체 Skeleton을 `exploration_concepts.skeleton_json`에 보존하여 이 매핑을 우회한다.

## 2. Migration 결정

additive `V5__concept_core.sql`을 추가했다. 기존 Concept 테이블/열은 삭제하거나 rename하지 않았고 legacy Concept를 신규 적격 Concept로 자동 변환하지 않았다.

신규 테이블은 `concept_exploration_batches`, `concept_slots`, `concept_attempts`, `concept_origin_validations`, `concept_legal_assessments`, `concept_rule_traces`, `exploration_concepts`다. Batch input idempotency, Slot index, Attempt sequence, assessment snapshot hash 및 공개 Concept order/hash를 DB unique constraint로 방어한다.

## 3. 상태와 실행 경계

- Batch: `QUEUED`, `GENERATING`, `VALIDATING`, `REPLACING`, `COMPLETED`, `NEEDS_INPUT`, `FAILED`, `STALE`
- Slot: `QUEUED`, `GENERATING`, `GENERATED`, `SCHEMA_INVALID`, `TRANSIENT_PROVIDER_FAILURE`, `PERMANENT_PROVIDER_FAILURE`, `VALIDATING_ORIGIN`, `VALIDATING_BOUNDARY`, `REDESIGNING`, `REPLACING`, `ELIGIBLE`, `REJECTED`, `NEEDS_INPUT`, `FAILED`, `STALE`
- Attempt phase: `INITIAL`, `REPAIR`, `REDESIGN`, `REPLACEMENT`
- AI outcome: `VALID`, `SCHEMA_INVALID`, `TRANSIENT_PROVIDER_FAILURE`, `PERMANENT_PROVIDER_FAILURE`

COMPLETED는 현재 Brief/Boundary ID와 hash가 일치하고 Origin/Boundary 검증 및 중복 검사를 통과한 공개 가능 Concept가 정확히 3개일 때만 설정된다. `UNRESOLVED_FACT`는 `NEEDS_INPUT`, hard prohibition은 `FAILED` 경로로 구분한다.

## 4. Concept Skeleton 및 Trace 계약

Strict Skeleton은 `conceptName`, `oneLineSummary`, `targetSegment`, `problemScenario`, `valueProposition`, `solutionMechanism`, `actorRoles`, `platformRole`, `transactionFlow`, `dataFlow`, `physicalActivities`, `partnerRequirements`, `featureSet`, `channelHypothesis`, `pricingHypothesis`, `revenueModelHypothesis`, `operatingModel`, `assumptions`, `risks`, `legalImplementationHypothesis`를 가진다. nested object도 exact field set을 검증한다.

AI가 `sourceValue`, `originTrace`, `legalTrace`, Evidence ID, 법률 원문, citation 또는 최종 법률 상태를 반환하면 strict schema 오류다.

Origin Trace는 서버가 Confirmed Field에서 결정론적으로 만든다. `fieldKey`, decision/source 상태, Brief version/hash, source/concept value hash, concept path, `PRESERVED|VARIED_WITHIN_PREFERENCE|OPEN_DECISION_USED|ASSUMPTION_INTRODUCED|VIOLATION`을 보존한다. 원문 sourceValue는 trace에 저장하지 않는다.

Boundary Trace도 서버가 실제 G4 Rule/Evidence FK에서 만든다. compliance는 `SATISFIED`, `SATISFIED_WITH_CONTROLS`, `REDESIGN_REQUIRED`, `INSUFFICIENT_INFORMATION`, `HARD_BLOCK`이고 AI가 Evidence ID를 만들 수 없다.

## 5. Legal Feasibility Assessment

Authoritative 상태는 `IMPLEMENTABLE`, `IMPLEMENTABLE_WITH_CONTROLS`, `REDESIGN_REQUIRED`, `INSUFFICIENT_INFORMATION`, `HARD_BLOCK`이다. 앞의 두 상태만 공개 가능하다.

Assessment는 규제 활동, satisfied/control/violated/unresolved Rule ID, 필수 통제·파트너/자격·고지, 금지 변형, 미해결 가정, 실제 Evidence ID, 전문 검토 flag와 Brief/Boundary version/hash를 저장한다. `validatedSnapshotHash`는 Concept Skeleton, Brief/Boundary version/hash, 시스템 Origin/Boundary Trace의 canonical 입력을 포함한다.

## 6. Mixed failure 및 bound

- Slot 실행은 `asyncio.gather(..., return_exceptions=True)`로 격리한다.
- 정상 Slot은 재생성하지 않는다.
- schema invalid Slot만 최대 1회 `REPAIR`한다.
- transient provider failure만 최대 1회 재시도한다.
- permanent provider failure는 repair하지 않고 replacement 대상이 된다.
- required partner를 충족하지 않는 후보는 최대 1회 `REDESIGN`한다.
- 초기 3 Slot, 최대 후보 검사 9개, replacement round 최대 2회다.
- 기본 concurrency는 1이고 `AI_CONCEPT_GENERATION_CONCURRENCY=1..3`만 허용한다. Slot index가 정렬 기준이다.

Variation Focus는 `TARGET_AND_USER_EXPERIENCE`, `OPERATING_MODEL_AND_PARTNERS`, `REVENUE_AND_CHANNELS`다. 중복 key 입력은 target segment, solution mechanism, platform role, actor roles, operating model, revenue model, partner requirements다. 동일 canonical hash는 `DUPLICATE`; 서버 검증은 7개 중 5개 이상 동일하면 `NEAR_DUPLICATE`로 기록하며 둘 다 공개하지 않는다.

## 7. Durable Worker와 Event

`CONCEPT_EXPLORATION` TaskRun을 API transaction에서 QUEUED로 만들고 commit 후 worker가 claim/lease를 획득한다. max attempts는 3이며 bounded backoff, expired RUNNING recovery, input idempotency 및 Domain commit 이후 terminal Event 계약을 사용한다.

Job Event key는 `job.concept.batch.queued`, `job.concept.slot.started`, `generated`, `schema_invalid`, `retrying`, `repairing`, `validating_origin`, `validating_boundary`, `redesigning`, `replacing`, `eligible`, `rejected`, `job.concept.batch.needs_input`, `completed`, `failed`, `recovered`다. safe params만 사용하며 전체 Concept/Brief/Boundary/Prompt/provider body/법률 원문/Authorization을 기록하지 않는다.

## 8. API와 G6 계약

- `POST /api/v2/projects/{projectId}/concept-explorations`
- `GET /api/v2/projects/{projectId}/concept-explorations/current`
- `GET /api/v2/projects/{projectId}/concept-explorations/{batchId}`
- `GET /api/v2/projects/{projectId}/concept-explorations/{batchId}/slots`
- `GET /api/v2/projects/{projectId}/concepts?contract=concept-core-v1`

Start 입력은 `confirmedBriefVersionId`, `regulatoryBoundaryVersionId`다. 차단 응답은 `code`, `userMessage`, `blockingState`, `nextAction`을 제공한다. query contract가 없는 기존 `/concepts`는 legacy Controller가 계속 처리한다.

G6 Slot View는 `slotId`, `slotIndex`, `variationFocus`, `status`, `currentPhase`, `attemptCount`, `safeMessageKey`, `legalState`, `eligible`, `updatedAt`이다. 공개 Concept View는 전체 주요 Skeleton 필드, legal state, assessment version, validated snapshot hash를 제공하며 Batch COMPLETED 전 내부 draft는 반환하지 않는다.

## 9. 검증 결과

- AI targeted: 결합 범위 51 passed 후 failure-injection 보강 `test_concept_core` 8 passed
- Backend Concept/JobEvent/Internal AI client targeted: Gradle `BUILD SUCCESSFUL`
- Backend compile/test compile: targeted 실행 중 `compileJava`, `compileTestJava` 성공
- PostgreSQL targeted: `PostgreSqlBaselineMigrationTests`, `PostgreSqlConceptCoreTests` 총 8개 test 성공; V5 clean/upgrade, idempotency, slot uniqueness, lease recovery, stale, project isolation 검증
- 최초 PostgreSQL 명령은 잘못된 기본 `test` task를 사용해 “No tests found”로 실패했고 올바른 `postgresTest` task로 재실행해 성공했다.
- AI 전체 회귀: 110 passed, 10 deprecation warnings
- Backend 전체 회귀: 315 passed, 실패/skip 0, Gradle `BUILD SUCCESSFUL`
- `git diff --check`: 단계 종료 검증에서 통과

Frontend 코드는 변경하지 않았다. G6용 View 계약은 Backend에만 추가했으므로 frontend targeted/lint/build/baseline은 실행하지 않았다. 전체 `postgresTest`도 사용자 정책에 따라 G6 통합 전까지 생략했다.

## 10. Docker/OpenAI 검증과 미해결 위험

사용자 절차: [G5_DOCKER_OPENAI_VERIFICATION.md](../verification/G5_DOCKER_OPENAI_VERIFICATION.md)

미해결 위험:

- 신규 G5 공개 Concept는 legacy Quick Assessment가 직접 소비하지 않는다. G6/G7 adapter가 `concept-core-v1` 계약을 연결해야 한다.
- 시스템 Origin validation은 결정론적 hash/상태 보존 검증이며 완전한 의미 동치 판정은 아니다. LOCKED 의미 보존 고도화는 validator 품질 모니터링이 필요하다.
- Docker/OpenAI 및 브라우저 수동 검증은 수행하지 않았다.
- 현재 Codex shell에는 `docker` CLI가 없어 Compose config 실행도 수행하지 못했다. 사용자는 검증 문서 절차로 확인해야 한다.
- 개발용 failure injection은 명시적 flag가 true일 때만 활성화되며 운영 배포에서 반드시 false여야 한다.

G6는 READY current Batch의 Slot View와 정확히 3개의 `concept-core-v1` 공개 Concept만 Workboard에 표시한다. G5에서는 Workboard, 비교, Quick Assessment, 선택 및 Legal Report를 구현하지 않았다.
