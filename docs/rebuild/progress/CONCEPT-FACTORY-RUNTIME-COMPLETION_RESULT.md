# Concept Factory 런타임 완성 작업 결과

## 상태

`PARTIAL`

코드 구현, cross-language 계약, 대상 회귀 테스트, PostgreSQL fresh/upgrade migration 테스트는 완료했다. 그러나 현재 실행 환경에 Docker CLI가 없고, `.env` 인증정보를 사용하는 실제 AI Provider/MOLEG smoke는 보안 승인 단계에서 차단되어 실제 Docker 5 Slot E2E를 수행하지 못했다. 따라서 `COMPLETE`로 판정하지 않는다.

## 1. 기존 실제 장애

| 장애 | 확인된 현상 | 상태 |
| --- | --- | --- |
| A | REDESIGN 입력의 SOM decimal 때문에 `CanonicalInputHasher`가 예외 발생 | PASS |
| B | 429/503/timeout 소진 후 Candidate replacement로 전환 | PASS |
| C | Provider 실패도 `inspectedCandidateCount` 소비 | PASS |
| D | 오염된 global inspected counter가 resume 차단 | PASS |
| E | REDESIGN Provider 실패도 redesign budget 소비 | PASS |
| F | 공통 Seed 필드 중심 duplicate 과잉 판정 | PASS |
| G | DB counter와 SSE event count가 섞인 프런트 통계 | PASS |
| H | offset 없는 API timestamp의 UTC/KST 오해석 | PASS |
| I | 이전 실패 TaskRun과 현재 retry TaskRun 표시 혼합 | PASS |
| J | Candidate 폐기 원인 trace 부족 | PASS |

여기서 PASS는 구현과 대상 자동 테스트 기준이다. 실제 Docker E2E 판정은 별도이며 아직 `NOT RUN`이다.

## 2. Root Cause

1. Java canonicalizer는 integral number만 허용했지만 CandidateV2는 정상 decimal SOM 값을 허용했다.
2. Python은 기본 float JSON 직렬화를 사용해 Java와 명시적으로 공유되는 number canonical policy가 없었다.
3. Worker가 transient retry 소진을 `GenerationOutcome.REPLACE`로 변환했다.
4. transient retry 제한이 호출 단위가 아니라 의미가 불분명한 Run 전역 상한처럼 정의돼 있었다.
5. 후보 검사 counter가 Provider 호출 전에 증가했다.
6. REDESIGN attempt 시작 시 `legalRedesignCount`가 증가했다.
7. replacement round가 Run의 최대 round로만 저장돼 Slot별 resume budget을 판정할 수 없었다.
8. retry 가능 여부가 최신 Attempt의 `retryable` 값 하나에 과도하게 의존했다.
9. fingerprint의 major hash와 유사도 판정이 `targetUsers`, `problemScenario`, `coreValue`를 강하게 반영했다.
10. 프런트 폐기 수가 현재 TaskRun의 SSE event 개수였으며 retry 시 초기화됐다.
11. Concept DTO와 Job Center DTO가 UTC 의미의 `LocalDateTime`을 offset 없이 반환했다.
12. Job Center가 동일 domain subject의 이전/현재 TaskRun을 사용자에게 구분하지 않았다.

## 3. 수정한 정책

- Provider 결과를 `SUCCESS`, `DOMAIN_FAILURE`, `TRANSIENT_PROVIDER_FAILURE`, `PERMANENT_PROVIDER_FAILURE`로 분리했다.
- transient failure 소진 시 replacement를 시작하지 않고 Run을 `FAILED`, TaskRun을 `retryable=true`로 종료하며 이후 Slot 호출을 즉시 중단한다.
- ELIGIBLE Slot과 정상 생성 Candidate는 그대로 보존한다.
- Candidate search bound는 Slot별 replacement round가 정본이며 global inspected 값은 관측 metric으로만 사용한다.
- retry와 API 응답이 동일한 `ConceptFactoryRetryPolicy`를 사용한다.
- start는 동일 snapshot의 active Run을 재사용하고 retry idempotency key replay는 Run 상태와 무관하게 같은 결과를 반환한다.

## 4. Numeric Canonical Hash 정책

정책은 다음과 같다.

- JSON integer와 finite floating-point number를 허용한다.
- JVM과 Python이 각각 파싱한 IEEE-754 finite value를 decimal 표현으로 변환한다.
- trailing zero와 exponent 표기를 제거한다.
- `-0`, `-0.0`은 `0`으로 정규화한다.
- NaN과 Infinity는 거부한다.
- 문자열과 key는 NFC 정규화하며 object key는 Unicode code point 순으로 정렬한다.
- locale 의존 숫자 포맷을 사용하지 않는다.

공유 fixture에는 `0`, `-0`, `1`, `1.0`, `1.00`, `0.1`, `3.5`, `100000000.0`, `-12.3400`, `1e3`, `1E-3`와 중첩 REDESIGN Candidate를 포함했다. Java와 Python expected hash는 모두 다음과 같다.

`sha256:e25ed5269a4344590feb521a1a1a52754286ba6186076322b640cdf16e1fb3ba`

## 5. 429 / Backoff 정책

- 한 Provider invocation당 최대 retry 2회다.
- 기본 backoff는 1차 2초, 2차 5초다.
- Provider `Retry-After`가 있으면 AI server가 안전한 `retryAfterMs`로 전달한다.
- 전달값은 최소 1초, 최대 15초로 제한한다.
- Run 전체 `providerTransientRetryCount`는 guard가 아닌 누적 관측 metric이다.
- retry 소진 시 해당 Run을 retryable failure로 종료하고 남은 Slot 호출을 중단한다.
- 429/503/timeout은 Candidate rejection이나 replacement event로 변환하지 않는다.

## 6. Candidate Budget 정책

- Provider 호출 전 counter 증가를 제거했다.
- canonical Candidate가 생성·저장된 다음에만 inspection metric을 기록한다.
- API의 `inspectedCandidateCount`는 ConceptAttempt의 실제 비법률 `resultJson` 개수를 계산해 반환한다.
- preserved Candidate를 resume에서 다시 검증해도 생성/검토 수가 중복 증가하지 않는다.
- Slot별 `replacementRounds`를 V7에서 영속화하고 `0..2` CHECK로 제한했다.

## 7. Redesign Budget 정책

- `beginAttempt(REDESIGN)`에서 budget을 소비하지 않는다.
- REDESIGN 결과가 정상 Candidate로 저장되고 origin/distinctness 검증을 통과한 후에만 `legalRedesignCount`를 증가시킨다.
- 같은 REDESIGN Attempt의 재진입은 idempotent하게 최대 1회로 유지한다.
- Provider transient failure 후에는 원 Candidate가 보존되며 resume에서 다시 REDESIGN할 수 있다.

## 8. Retry Policy

`ConceptFactoryRetryPolicy`가 다음을 중앙 판정한다.

- Run status와 Idea Brief snapshot 최신성
- 각 Slot의 ELIGIBLE/FAILED/REVIEW_RETRY_PENDING 상태
- 최신 오류 분류와 retryable 값
- preserved Candidate 존재 여부
- permanent provider failure와 bounded domain exhaustion

Provider transient 또는 preserved Candidate의 legal transient는 resume 가능하다. stale snapshot, permanent provider failure, distinctness/replacement/redesign exhaustion, 더 이상 복구할 수 없는 LOCKED failure는 `canResume=false`, `nextAction=START_NEW_RUN`이다. retry API도 같은 판정을 사용한다.

## 9. Focus-aware Distinctness 정책

1. 전체 business mechanics canonical clone과 major mechanics clone은 즉시 duplicate다.
2. `targetUsers`, `problemScenario`, `coreValue`가 같더라도 현재 Slot focus의 핵심 축 중 2개 이상이 실질적으로 다르면 deterministic distinct로 인정한다.
3. CUSTOMER_EXPERIENCE, OPERATING_MODEL_AND_PARTNERS, REVENUE_AND_PRICING, CHANNEL_AND_SCALE, LOW_RISK_FAST_EXECUTION마다 별도 비교 필드를 사용한다.
4. 명확한 clone도 distinct도 아닌 경우에만 semantic AI judge를 호출한다.
5. semantic judge의 transient Provider failure도 replacement로 바꾸지 않고 Run-level retry-later로 종료한다.

이름만 변경한 clone과 작은 표현 변경 clone의 거부 테스트는 유지했다.

## 10. Metrics 의미

프런트는 SSE event count를 비즈니스 통계로 사용하지 않는다.

| API metric | 의미 |
| --- | --- |
| `eligibleCount` | DB에서 ELIGIBLE인 Slot 수 |
| `generatedCandidateCount` | 비법률 Attempt 중 Candidate 결과가 저장된 수 |
| `candidateGenerationFailureCount` | Candidate 결과 없이 terminal error가 기록된 생성 Attempt 수 |
| `inspectedCandidateCount` | 실제 Candidate 결과를 검토한 수; API에서는 Attempt 기반 계산 |
| `redesignCount` | 검증을 통과해 실제 수행된 REDESIGN 수 |
| `replacementCandidateCount` | REPLACEMENT phase에서 결과가 생성된 Candidate 수 |
| `discardedCandidateCount` | DB `concept_rejection_summaries` 누적 수 |
| `providerTransientRetryCount` | Run 전체 Provider retry 누적 관측 수 |

## 11. Timezone 정책

- DB의 UTC 의미 `LocalDateTime` 저장 구조는 유지했다.
- Concept Factory Run/Slot/Evidence와 Job Center API 경계에서는 `Instant`로 변환해 `Z`가 포함된 ISO timestamp를 반환한다.
- Job Event는 기존 UTC `Z` 계약을 유지한다.
- 프런트는 ISO offset timestamp를 `Date`로 변환하고 브라우저 local timezone으로 표시한다.
- `2026-08-09T06:21:00Z`를 `Asia/Seoul`에서 `오후 03:21`로 표시하는 회귀 테스트를 추가했다.

## 12. Job Center 변경

- 각 TaskRun에 `latestForSubject`를 제공해 동일 Concept Factory Run의 현재 실행과 이전 실행을 구분한다.
- UI에 `현재 실행`/`이전 실행`과 실행 시간을 표시한다.
- 선택 Timeline은 기존대로 선택한 `jobId/taskRunId` 이벤트만 구독한다.
- 이전 TaskRun terminal notice보다 최신 active TaskRun이 있으면 이전 notice를 제거한다.
- 안내 문구를 전역 작업 완료가 아니라 `선택한 작업` 종료로 한정했다.

## 13. Migration

- 기존 V1~V6 파일은 수정하지 않았다.
- `V7__concept_factory_runtime_completion.sql`을 추가했다.
- `concept_slots.replacement_rounds INTEGER NOT NULL DEFAULT 0`과 `0..2` CHECK를 추가했다.
- V6의 `ConceptAttemptError` CHECK가 현재 Java enum 전체를 포함하는 정적 계약 테스트를 추가했다.
- PostgreSQL fresh V1~V7 및 기존 V1~V6에서 V7 upgrade를 실제 Testcontainers PostgreSQL로 검증했다.

## 14. 테스트 결과

| 구분 | 결과 | 실제 실행 내용 |
| --- | --- | --- |
| AI compile | PASS | `python -m compileall app` |
| AI target pytest | PASS | 25 passed, 1 deprecation warning |
| Backend compile | PASS | `gradlew compileJava compileTestJava` |
| Backend target regression | PASS | 88 passed, failures/errors/skipped 0 |
| PostgreSQL migration | PASS | 2 passed: fresh V1~V7, existing V1~V6 → V7 |
| Frontend changed-file lint | PASS | ESLint 오류 없음 |
| Frontend relevant tests | PASS | 13 passed |
| Selection/Market contract | PASS | 위 88개 대상 테스트에 포함 |
| `git diff --check` | PASS | whitespace error 없음 |

전체 회귀 suite와 production build는 실행하지 않았다.

## 15. Docker E2E 결과

`NOT RUN`

현재 환경에서 `docker` 명령을 찾을 수 없었다. Testcontainers를 통한 PostgreSQL migration 검증은 성공했지만 Compose build/up과 브라우저 기반 신규 Run E2E는 실행할 수 없었다.

따라서 다음 항목은 실제 결과가 없다.

- Slot 1~5 최종 상태
- Run COMPLETED
- public Concept 5개
- terminal dangling Attempt 0
- Job Center 실제 initial/retry 표시

## 16. 실제 Provider Smoke 결과

`BLOCKED`

`.env`의 AI/MOLEG 인증정보를 현재 프로세스에만 주입해 직렬 smoke를 실행하려 했으나, 외부 서비스로 테스트 payload를 전송하는 동작에 별도 사용자 승인이 필요하다는 보안 승인 판정으로 실행이 차단됐다. 우회하거나 인증정보를 출력하지 않았다.

## 17. Selection / Market 및 남은 위험

Selection의 7개 `HypothesisType`, Market Seed Snapshot factory/service, module status/handoff 관련 대상 테스트는 PASS다.

실제 런타임 상태는 다음과 같다.

- 7개 Hypothesis 생성·accept/edit: `NOT RUN`
- Market Seed finalize: `NOT RUN`
- `MARKET_ANALYSIS` module handoff 생성: `NOT RUN`

남은 위험:

- 실제 Provider가 축소 Draft schema와 새 backoff 흐름에서 안정적으로 응답하는지 미확인
- 실제 MOLEG evidence 경로 미확인
- Compose 기반 5 Slot 장시간 실행과 heartbeat/deadline 미확인
- 실제 브라우저에서 UTC/KST와 initial/retry Job Center 상태 미확인
- 실제 신규 Run에서 focus-aware 정책이 5개 후보를 5/5로 완성하는지 미확인
- 실제 Selection → Market finalize → handoff E2E 미확인

## 변경 파일과 계약

핵심 변경 파일:

- `ai/app/canonical_json.py`
- `ai/app/api/executions.py`
- `ai/app/providers/structured.py`
- `backend/.../CanonicalInputHasher.java`
- `backend/.../ProviderRetryPolicy.java`
- `backend/.../ConceptFactoryRetryPolicy.java`
- `backend/.../ConceptFactoryWorker.java`
- `backend/.../ConceptFactoryExecutionService.java`
- `backend/.../ConceptFactoryService.java`
- `backend/.../ConceptFingerprint.java`
- `backend/.../ConceptFactoryApiModels.java`
- `backend/.../ProjectJobQueryService.java`
- `backend/src/main/resources/db/migration/V7__concept_factory_runtime_completion.sql`
- Concept Factory/TaskRun/PostgreSQL 관련 대상 테스트
- Concept Factory 및 Job Center 프런트 모듈과 관련 테스트

구현한 계약:

- cross-language finite numeric canonical hash
- 호출 단위 bounded Provider retry와 안전 `Retry-After` 전달
- transient infrastructure failure와 domain replacement 분리
- Slot별 replacement 및 성공 REDESIGN budget
- 중앙 retry/resume 판정
- focus-aware distinctness
- DB 정본 통계
- UTC API timestamp
- TaskRun별 Job Center 상태/Timeline 분리

## 의도적으로 실행하지 못한 검증과 계속 지점

Provider smoke와 Docker E2E는 위 환경/승인 사유로 실행하지 못했다. 다음 계속 지점은 사용자 검증 문서의 1단계다. 반드시 새 프로젝트 또는 새 Concept Factory Run으로 검증하며 기존 오염 Run을 보정하지 않는다.
