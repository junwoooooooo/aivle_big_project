# AI 모듈 통합 가이드 — 새 AI 기능을 어디에 어떻게 끼워 넣나

- Status: **AS_BUILT 기반 절차서**
- Code Baseline: `9ea9975`
- 선행 문서: [As-Built Architecture](AS_BUILT_ARCHITECTURE.md) — 용어와 계약은 거기 정의돼 있다

이 문서는 "AI가 뭘 해주는 새 기능"을 추가할 때 **손대야 하는 파일 전부**와 **그 순서**를 적는다.
기존 11개 여정 task가 전부 이 절차로 만들어져 있으므로, 따라가면 기존 것과 같은 모양이 나온다.

---

## 0. 시작 전 결정 3가지

### (1) 정말 새 TaskType이 필요한가

필요 없는 경우가 더 많다. 다음이면 **기존 task 확장**이 맞다:
- 같은 입력에서 필드 몇 개만 더 원한다 → 해당 프롬프트 + Pydantic 모델 + validator만 넓힌다
- 같은 결과를 다르게 보여주고 싶다 → 프론트만 고친다

새 TaskType은 **입력 스냅샷이 다르거나, 실패했을 때 따로 재시도해야 하거나, 사용자가 별도 단계로
인식하는 경우**에만 만든다.

### (2) 실행 패턴 — A / B / C 중 하나

| | 언제 | 대가 |
|---|---|---|
| **A. 동기 인라인** | 실행이 1분 안쪽이고, 사용자가 그 화면에서 결과를 기다려도 되는 경우 | 요청 스레드를 붙잡는다. `AI_SERVER_READ_TIMEOUT`(기본 75s)에 걸린다 |
| **B. TaskRun 워커** | 오래 걸리거나, 화면을 떠나도 진행돼야 하는 경우 | 폴링 화면·`synchronize()` 지연 반영 코드가 추가로 필요하다 |
| **C. 인메모리 배치** | 여러 AI 호출을 루프로 엮어 조건을 만족할 때까지 반복해야 하는 경우 | 프로세스가 죽으면 배치가 사라진다. 재시작 복구가 없다 |

**대부분 A로 시작하고, 느려지면 B로 옮기는 게 맞다.** 실제로 법률 사전점검만 B다.

> ⚠️ **B를 고르면 `TaskRunWorker.validateResult()`에 분기를 반드시 추가**해야 한다.
> 현재 이 메서드는 `IDEA_INTERPRETATION`·`IDEA_LEGAL_PRECHECK`·`CONCEPT_LEGAL_VALIDATION`만 알고,
> 나머지는 `RESULT_DOMAIN_INVARIANT_VIOLATION`으로 **무조건 거부**한다.
> 잊으면 AI 비용은 다 쓰고 결과만 버려진다 — 컴파일 에러도, 테스트 실패도 나지 않는다.

### (3) 결정론이어야 하는 부분을 먼저 못 박는다

이 코드베이스의 일관된 원칙이다: **계산·판정은 Java, 서술만 AI.**
예) 법률 사전점검에서 PASS/REVISION_REQUIRED/PROHIBITED 판정은
`LegalPrecheckService.decide()`라는 **순수 Java 함수**가 내리고, AI는 findings/evidence만 만든다.

새 모듈에서도 점수·등급·통과 여부 같은 것은 AI 응답 필드로 받지 말고 Java에서 계산할 것.
받아야 한다면 **왜 결정론으로 못 하는지** 근거를 남긴다.

---

## 1. 손대야 하는 파일 — 전체 목록

`XXX` = 새 task 이름 (예: `MARKET_SIZING`)

### AI 서버 (`ai/`)

| # | 파일 | 하는 일 |
|---|---|---|
| 1 | `ai/app/api/executions.py` — `TASK_TYPES` 집합 | 허용 목록. 없으면 `UNSUPPORTED_TASK_TYPE` 422 |
| 2 | `ai/app/api/executions.py` — 124–130행의 두 번째 집합 | **같은 목록이 한 번 더 있다.** 여기 빠지면 `MODEL_DEPENDENCY_UNAVAILABLE` 503 |
| 3 | `ai/prompts/xxx/system.md` | 시스템 프롬프트. 출력 JSON 스키마를 여기 명시 |
| 4 | `ai/prompts/xxx/user.md` | 사용자 프롬프트. `{{input}}` 자리표시자가 텍스트로 치환된다 |
| 5 | `ai/app/services/journey_provider.py` — `_load_prompts.folders` | TaskType → 폴더명 |
| 6 | `ai/app/models/journey.py` — `XxxResult` | 결과 Pydantic 모델. **unknown field 거부** |
| 7 | `ai/app/services/journey_provider.py` — `model_types` | TaskType → 모델 |
| 8 | `ai/tests/test_internal_executions.py` | 계약 테스트 |

> 5와 7은 **별개의 dict**다. 하나만 넣으면 `AI_CONFIGURATION_INVALID`(5 누락) 또는
> `RESULT_SCHEMA_INVALID`(7 누락)로 갈린다.

### 백엔드 (`backend/`)

| # | 파일 | 하는 일 |
|---|---|---|
| 9 | `taskrun/domain/TaskType.java` | enum 추가 |
| 10 | `backend/src/main/resources/db/migration/V37__add_xxx.sql` | 도메인 run·결과 테이블 (**다음 빈 버전은 V37**) |
| 11 | `journey/XxxRun.java` + `XxxRunRepository.java` | 도메인 run 엔티티. `TaskRun` FK + state + resultJson + error |
| 12 | `journey/Xxx.java` + `XxxRepository.java` | 결과 엔티티 (필요하면) |
| 13 | `journey/XxxPersistenceService.java` | `markRunning` / `complete` / `fail` — 전부 `@Transactional`, row lock |
| 14 | `journey/XxxJourneyService.java` | 입력 조립 · `createTask` · `execute` · validator · View record |
| 15 | `journey/XxxJourneyController.java` | `/api/v2/projects/{projectId}/…` |
| 16 | (패턴 B만) `journey/XxxWorkerScheduler.java` | `@Scheduled` 폴러 |
| 17 | (패턴 B만) `taskrun/service/TaskRunWorker.validateResult()` | **필수.** §0-(2) 참고 |
| 18 | `docs/api/openapi.yaml` | 공개 계약 |
| 19 | 테스트 | 아래 §4 |

### 프론트 (`frontEnd/`)

| # | 파일 | 하는 일 |
|---|---|---|
| 20 | `src/features/journey/journeyApi.js` | API 호출 추가 |
| 21 | `src/features/journey/XxxPages.jsx` | 화면 |
| 22 | `src/app/router/AppRouter.jsx` | `…/journey/xxx` 라우트 |
| 23 | `src/features/journey/journeyFailure.js` | 새 오류 코드의 사용자 문구 |

### 계약 픽스처

| # | 파일 |
|---|---|
| 24 | `docs/contracts/fixtures/internal-ai-v1/tasks/xxx.request.valid.json` / `.response.valid.json` |
| 25 | `docs/contracts/fixtures/internal-ai-v1/negative/schema-xxx-*.json` (unknown field 등) |
| 26 | `docs/contracts/INTERNAL_AI_API_V1_CONTRACT.md` |

---

## 2. 작성 순서 (권장) — 계약부터, 화면은 마지막

```
1. 결과 JSON 스키마를 종이에 확정          → verify: 필드 하나하나 "이걸 누가 쓰나" 답할 수 있다
2. AI 서버 (#1~#7)                        → verify: ai/tests 로 executions 호출이 200 + 스키마 통과
3. Flyway + 엔티티 + Persistence (#10~#13) → verify: 통합 테스트로 저장/조회
4. Journey 서비스 + validator (#14)        → verify: validator 단위 테스트 (정상 1 + 위반 N)
5. 컨트롤러 + OpenAPI (#15, #18)           → verify: MockMvc 슬라이스
6. (패턴 B면) 워커 분기 + 스케줄러 (#16,#17)→ verify: 워커가 결과를 채택하는 테스트
7. 프론트 (#20~#23)                        → verify: 컴포넌트 테스트
8. 실스택 스모크                            → verify: 실제 3프로세스로 1회 관통
```

**AI 서버를 먼저 하는 이유**: 결과 스키마가 Java validator·Pydantic 모델·프롬프트 세 곳에 중복되어
있어서, 나중에 바꾸면 세 곳을 다시 맞춰야 한다.

---

## 3. 실제 코드 골격

### 3-1. AI 서버

`ai/app/models/journey.py` — 기존 모델과 같은 스타일로:

```python
class XxxResult(BaseModel):
    model_config = ConfigDict(extra="forbid")   # unknown field 거부 (계약 요건)
    summary: str
    items: list[XxxItem]
    assumptions: list[str]
    warnings: list[str]
```

`journey_provider.py` 두 곳:

```python
folders = { ..., "XXX": "xxx" }          # _load_prompts
model_types = { ..., "XXX": XxxResult }  # execute_journey_task
```

`executions.py` **두 집합 모두**:

```python
TASK_TYPES = { ..., "XXX" }
# 그리고 124행의 if body.taskType not in { ... "XXX" ... } 에도
```

프롬프트(`system.md`)에는 반드시 넣을 것:
- 출력은 JSON 객체 하나뿐, 마크다운 설명 금지
- **명시되지 않은 수치를 지어내지 말 것** (기존 프롬프트가 쓰는 방어선. 예: TAM/SAM/SOM 발명 금지)
- 근거는 입력 텍스트 안에서만

### 3-2. 백엔드 — 도메인 서비스 (패턴 A)

기존 4개 서비스가 **완전히 같은 6줄 골격**을 공유한다. 그대로 복사할 것:

```java
private TaskRun createTask(Long ownerId, Project project, TaskType type,
                           String subjectType, String subjectId, String input) {
    String nonce = UUID.randomUUID().toString();
    return taskRuns.create(ownerId, project.getId(), type, subjectType, subjectId, input,
        hasher.hash(type, "1.0", "ko-KR", input), nonce, nonce, 1);   // maxAttempts
}

private JsonNode execute(TaskRun run, Consumer<JsonNode> validator) {
    TaskRunService.Claim claim = taskRuns.claim(run.getId(), "journey-sync",
        Duration.ofMinutes(2), Duration.ofMinutes(2));
    taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
    try {
        var response = ai.execute(taskRuns.getOwnedForWorker(run.getId()),
            claim.taskAttemptId(), LocalDateTime.now().plusMinutes(2));
        try { validator.accept(response.result()); }
        catch (BusinessException invalid) {
            taskRuns.rejectAndFail(run.getId(), claim.taskAttemptId(), claim.claimToken(),
                response.result().toString(), response.resultSchemaVersion(), "AI_RESULT_INVALID");
            throw invalid;
        }
        taskRuns.adopt(run.getId(), claim.taskAttemptId(), claim.claimToken(),
            response.result().toString(), response.canonicalInputHash(), response.resultSchemaVersion());
        return response.result();
    } catch (ExecutionFailure failure) {
        taskRuns.fail(run.getId(), claim.taskAttemptId(), claim.claimToken(),
            failure.code(), failure.reason(), failure.retryable());
        throw failure;
    }
}
```

입력 포장도 동일하다 (`taskInput`) — 16,000 코드포인트씩 chunk를 자르고 각 chunk와 전체에
sha256을 붙인다. `PersonaJourneyService.taskInput()`을 그대로 복사할 것.
**직접 쓰지 말 것** — chunk 해시/문자수 계산이 어긋나면 AI가 `HASH_MISMATCH` 400을 낸다.

호출부는 항상 이 3중 catch:

```java
try { JsonNode result = execute(task, this::validateXxx); persistence.complete(runId, result); }
catch (ExecutionFailure f) { persistence.fail(runId, f.reason()); throw publicFailure(f); }
catch (RuntimeException f) { persistence.fail(runId, "AI_RESULT_INVALID"); throw normalized(f); }
```

### 3-3. validator 작성 규칙

```java
private void validateXxx(JsonNode r) {
    // 1) 최상위 필드 집합이 정확히 일치 — 더도 덜도 안 된다
    if (r == null || !r.isObject() || !Set.copyOf(r.propertyNames()).equals(EXPECTED_FIELDS))
        throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
    // 2) 타입·공백 검사
    // 3) enum 값은 화이트리스트 대조
    // 4) 백엔드가 보낸 ID가 그대로 돌아왔는지 대조 (환각 방지)
}
```

**4번이 중요하다.** `MarketingReportJourneyService.validateComparison()`이 모범이다 —
보낸 assetId/personaId 튜플과 **개수·순서·중복까지** 대조해서, AI가 없는 항목을 만들어내거나
빠뜨리면 거부한다. ID를 돌려받는 task라면 반드시 이 검사를 넣는다.

실패 시 로그에 진단을 남긴다 (사용자에게는 안 나간다):

```java
log.warn("AI result contract invalid taskType={} fieldPath={} topLevelFields={} resultSchemaVersion={}",
    TaskType.XXX, path, fields, schemaVersion);
```

### 3-4. Persistence 서비스

AI 호출 밖에서 도는 **짧은 트랜잭션**이어야 한다. 골격:

```java
@Transactional
public void complete(Long runId, JsonNode result) {
    XxxRun run = runs.findLockedById(runId).orElseThrow(...);
    if (run.getState() == State.SUCCEEDED) return;        // 멱등
    // ... 결과 엔티티 저장
    run.succeed(result.toString());
}
```

`findLockedById`(비관적 락)를 쓴다. 재조회 없이 `save`만 하면 동시 요청에서 상태가 뒤집힌다.

---

## 4. 테스트에서 반드시 덮을 것

| 층 | 무엇 | 왜 |
|---|---|---|
| AI 단위 | 정상 결과 200, unknown field 422, hash mismatch 400 | 계약 양쪽 대조 |
| Java 단위 | validator: 정상 1건 + **위반 케이스 N건** | 필드 집합 일치 규칙이 잘 깨진다 |
| Java 통합 | TaskRun 생성 → 채택 → 도메인 반영 | 상태 전이 |
| Java 통합 | 같은 입력 재요청 시 **멱등 재생** | `create()`의 중복 방지 경로 |
| 프론트 | 로딩·성공·실패 3상태 | |
| **실스택 스모크** | 3프로세스 띄우고 1회 관통 | **아래 참고** |

> **실스택 스모크를 빼지 말 것.** 과거에 `JobQueryService.findLatest`의 jobType 화이트리스트
> 누락이 여기서만 잡혔다. 백엔드 통합 테스트는 그 쿼리를 타지 않고, 프론트 테스트는 API를
> mock하기 때문에 **구조적으로 둘 다 못 보는 자리**였다. 새 수직 슬라이스에는 같은 것을 만든다.
> 스크립트에서 응답 코드를 조용히 삼키지 말 것 — `expect=200`을 안 걸어 400을 놓치면
> "job이 안 끝난다"로 한참 오진하게 된다.

**스크립트를 짜기 전에 관련 record/DTO를 먼저 읽을 것.** 요청/응답 모양을 짐작해서
전체 체인을 6번 헛돌린 적이 있다 (비밀번호 최소 15자, signup이 토큰을 주지 않음,
응답 필드명이 `id`이지 `projectId`가 아님 등 — 전부 DTO 4개만 읽었으면 한 번에 끝났다).

---

## 5. 체크리스트 (복사해서 쓸 것)

```
AI 서버
[ ] executions.py TASK_TYPES 에 추가
[ ] executions.py 124행 두 번째 집합에도 추가   ← 자주 빠뜨림
[ ] prompts/xxx/system.md, user.md  ({{input}} 포함)
[ ] journey_provider.py _load_prompts.folders
[ ] models/journey.py  XxxResult (extra="forbid")
[ ] journey_provider.py model_types
[ ] ai/tests 계약 테스트

백엔드
[ ] TaskType enum
[ ] V37 마이그레이션 (다음 빈 버전 확인!)
[ ] XxxRun 엔티티 + repository
[ ] XxxPersistenceService (@Transactional + 락 + 멱등)
[ ] XxxJourneyService (taskInput 복사 / createTask / execute / validator)
[ ] validator: 필드 집합 일치 + enum 화이트리스트 + 보낸 ID 대조
[ ] 컨트롤러 (/api/v2)
[ ] 패턴 B면 → TaskRunWorker.validateResult 분기 + @Scheduled 폴러
[ ] input에 부동소수점 없음 확인                ← 런타임에만 터짐
[ ] docs/api/openapi.yaml

프론트
[ ] journeyApi.js
[ ] XxxPages.jsx (로딩/성공/실패 3상태)
[ ] AppRouter.jsx 라우트
[ ] journeyFailure.js 오류 문구

계약·문서
[ ] fixtures tasks/ + negative/
[ ] INTERNAL_AI_API_V1_CONTRACT.md
[ ] AS_BUILT_ARCHITECTURE.md §2 여정 표에 한 줄 추가

경계 표시 (제거 금지)
[ ] AI 산출물임을 화면에 표시
[ ] 법률 = "법률 자문 아님"
[ ] 페르소나 = "가설이며 실제 고객 응답 아님"
[ ] 재무 = "재무 자문 아님 · 외부 시장 데이터 미반영"
```

---

## 6. 자주 나는 실패와 원인

| 증상 | 원인 |
|---|---|
| `UNSUPPORTED_TASK_TYPE` 422 | `TASK_TYPES` 집합 누락 |
| `MODEL_DEPENDENCY_UNAVAILABLE` 503 (키는 정상) | `executions.py` **두 번째** 집합 누락 |
| `AI_CONFIGURATION_INVALID` | `_load_prompts.folders` 누락, 또는 `AI_PROVIDER/API_KEY/MODEL` 미설정 |
| `RESULT_SCHEMA_INVALID` | `model_types` 누락, 또는 Pydantic 모델과 프롬프트 출력 불일치 |
| `INVALID_REQUEST / HASH_MISMATCH` | `taskInput` 직접 구현해서 chunk 해시·문자수가 어긋남 |
| Java에서 "floating-point JSON numbers…" | input에 소수 넣음 → 정수 basis point나 문자열로 |
| `HEADER_BODY_CORRELATION_MISMATCH` | 클라이언트를 직접 만들며 헤더를 다르게 넣음 |
| `DEADLINE_EXCEEDED` (즉시) | `deadlineAt`이 과거. 테스트 고정 시각 주의 |
| AI 호출은 되는데 결과가 사라짐 | 패턴 B인데 `TaskRunWorker.validateResult` 분기 누락 |
| `AI_RESULT_INVALID` (응답은 멀쩡해 보임) | 최상위 필드 집합 불일치. 서버 로그의 `topLevelFields=` 확인 |
| `IllegalStateException: AI call must run outside a DB transaction` | 도메인 서비스 메서드에 `@Transactional`을 통째로 붙임 |
| 기동 실패 (bean 2개) | 같은 포트에 `@Component`를 하나 더 붙임 |

---

## 7. 하지 말 것

- **AI 서버에 DB/스토리지 접근을 주는 것** — 계약이 금지한다. 필요한 데이터는 Spring이 텍스트로 넣는다.
- **결과에 `prompt`·`rawProviderResponse`·`storageUrl` 등을 싣는 것** — `rejectForbiddenFields`가 거부한다.
- **Mock/fixture fallback을 다시 만드는 것** — `AI_FIXTURE_MODE=false`가 명시적 결정이다.
  키가 없으면 실패해야지 가짜 결과를 만들면 안 된다.
- **`/api/v1` 레거시 경로에 새 기능을 얹는 것** — 여정 화면에서 도달할 수 없다.
- **Flyway V1–V36 수정** — immutable. 새 버전 파일로만.
- **경계 표시(disclaimer) 제거** — 체크리스트의 마지막 블록.
