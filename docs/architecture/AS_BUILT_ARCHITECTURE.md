# As-Built Architecture (실제 동작 구조)

- Status: **AS_BUILT** — 코드에서 직접 읽어 정리한 것. 목표(Target) 문서가 아니다.
- Code Baseline: `9ea9975` (origin/main, 2026-08-04 확인)
- 짝 문서: [AI 모듈 통합 가이드](AI_MODULE_INTEGRATION_GUIDE.md) — 새 AI 기능을 끼워 넣는 절차

> **우선순위:** 이 문서(As-built) < 코드. 충돌하면 코드가 맞다.
> 같은 폴더의 `SYSTEM_ARCHITECTURE.md`·`AI_SERVER_BOUNDARY.md`·`SPRING_WAS_BOUNDARY.md`는
> **TARGET_CANONICAL**(목표)이며 `Implementation Status: NOT_STARTED/PARTIAL`이 붙어 있다.
> "이렇게 되어야 한다"와 "지금 이렇다"를 섞지 말 것.

---

## 1. 프로세스와 포트

```mermaid
flowchart LR
    U["브라우저"] -->|"HTTPS"| F["frontend<br/>React 19 + Vite<br/>:3000 (compose) / :5173 (dev)"]
    F -->|"/api/v1, /api/v2"| S["backend<br/>Spring Boot 4.1 (Java 17)<br/>:8080"]
    S -->|"JPA / Flyway"| D[("PostgreSQL 17<br/>:5432")]
    S -->|"S3 SDK"| O[("MinIO<br/>:9000")]
    S -->|"POST /internal/v1/ai/executions<br/>Bearer AI_INTERNAL_SERVICE_TOKEN"| A["ai-server<br/>FastAPI<br/>:8000"]
    A -->|"OpenAI 호환 /chat/completions"| M["모델 provider"]
    A -->|"법제처 Open API"| L["law.go.kr/DRF"]
```

**금지된 연결** (코드로 강제되고 있음):
- 프론트 → AI 서버 직접 호출 (AI 서버에는 브라우저용 public endpoint가 없다)
- AI 서버 → DB / Object Storage (AI 서버에 드라이버·SDK 자체가 없다)
- AI 서버가 받는 것은 **Spring이 추출한 텍스트뿐** — 파일 bytes·presigned URL·JWT·엔티티 직렬화 금지

**기동**: `compose.yaml` — postgres → minio → minio-init → ai-server → backend → frontend 순으로
healthcheck 의존. 필요한 환경변수는 `.env.example` 참조 (`AI_PROVIDER`·`AI_API_KEY`·`AI_MODEL`·
`AI_INTERNAL_SERVICE_TOKEN`·`JWT_SECRET`·`POSTGRES_PASSWORD`·`MINIO_ROOT_PASSWORD`는 **필수**,
없으면 compose가 뜨지 않는다).

---

## 2. 사용자 여정(Journey) — 이 제품이 실제로 하는 일

라우트는 `frontEnd/src/app/router/AppRouter.jsx`가 정본이다. 현재 제품은 **하나의 선형 여정**이다:

| # | 화면 | 라우트 | 백엔드 | AI TaskType |
|---|---|---|---|---|
| 1 | 아이디어 입력·해석 | `/app/projects/:id/idea` | `JourneyController` | `IDEA_INTERPRETATION` |
| 2 | 법률 사전점검 | `/app/projects/:id/legal` | `LegalPrecheckController` | `IDEA_LEGAL_PRECHECK` |
| 3 | 컨셉 생성 | `…/journey/concept` | `ConceptJourneyController` | `CONCEPT_GENERATION` + `CONCEPT_LEGAL_VALIDATION` |
| 4 | 컨셉 분석 | `…/journey/concept-analysis` | `ConceptJourneyController` | `QUICK_ASSESSMENT`, `DETAILED_ANALYSIS` |
| 5 | 컨셉 선택 | `…/journey/concept-selection` | `ConceptJourneyController` | — (사용자 결정) |
| 6 | 페르소나 | `…/journey/persona` | `PersonaJourneyController` | `PERSONA_CARD_GENERATION` |
| 7 | 인터뷰 | `…/journey/interview` | `PersonaJourneyController` | `PERSONA_INTERVIEW`, `INTERVIEW_SYNTHESIS` |
| 8 | 마케팅 | `…/journey/marketing` | `MarketingReportJourneyController` | `MARKETING_GENERATION`, `MARKETING_COMPARISON` |
| 9 | 최종 리포트 | `…/journey/final-report` | `MarketingReportJourneyController` | `FINAL_REPORT_GENERATION` |

각 단계는 **앞 단계의 확정 산출물을 입력으로 요구**한다. 예를 들어 마케팅은
`ideas.findCurrent(...).filter(IdeaVersion::isConfirmed)` → `ConceptSelection` → `PersonaStudy` →
성공한 `InterviewSynthesisRun`이 전부 있어야 하고, 하나라도 없으면
`PROJECT_STAGE_INVALID`/`ANALYSIS_INPUT_INVALID`로 막는다 (`MarketingReportJourneyService.context()`).

### 죽은 화면 — 라우트만 남고 전부 리다이렉트

`AppRouter.jsx` 82–134행이 옛 경로를 전부 journey로 넘긴다.
`plan/`·`structured-plan`·`review/legal`·`review/financial`·`validate/*`·`report` 등.
대응하는 프론트 feature 폴더(`feasibility/`, `financial/`, `structured-plan/`, `documents/`,
`legal-review/`, `personas/`, `report/`, `validation/`, `marketing/`)와 백엔드 `/api/v1` 컨트롤러
(`legal-reviews`, `feasibility-assessments`, `financial-analyses`, `marketing-contents`,
`persona-recommendations`, `panel-interviews`, `market-responses`)는 **코드는 살아 있으나 여정에서
도달 불가능**하다. 새 작업을 여기에 얹지 말 것.

---

## 3. AI 실행의 심장 — TaskRun

모든 AI 호출은 예외 없이 `TaskRun` 한 줄을 남긴다. **Spring이 유일한 상태 소유자**이고
AI 서버는 상태를 갖지 않는 동기 실행기다.

### 3-1. 도메인 3층

| 엔티티 | 파일 | 소유하는 것 |
|---|---|---|
| `TaskRun` | `taskrun/domain/TaskRun.java` | 업무 요청 1건과 **최종 상태**. 입력 스냅샷·입력 해시·멱등키·correlation |
| `TaskAttempt` | `taskrun/domain/TaskAttempt.java` | 개별 실행 1회. claim token·lease·timeout·오류 |
| `TaskResult` | `taskrun/domain/TaskResult.java` | 검증 결과. `ADOPTED` 또는 `REJECTED` |

상태: `TaskRunState` = QUEUED → READY → RUNNING → SUCCEEDED / FAILED / TIMED_OUT / CANCELLED.
`TaskAttemptState` = CREATED → CLAIMED → RUNNING → SUCCEEDED / FAILED / TIMED_OUT / CANCELLED.

**채택은 정확히 한 번**이다. `TaskRunService.adopt()`가
`state==RUNNING && finalResultId==null && attemptId==currentAttemptId`를 확인하고,
어긋나면 결과를 `REJECTED`로 저장한 뒤 `LATE_OR_DUPLICATE_RESULT`로 실패시킨다.
네트워크 모호성으로 AI 실행이 중복될 수 있음을 전제로 설계돼 있다.

### 3-2. 실행 한 사이클 (파일 단위)

```
① 도메인 서비스            journey/XxxService.java
     taskInput(key, text)        입력 텍스트를 chunk 배열 JSON으로 포장
     hasher.hash(...)            CanonicalInputHasher — 정규화 해시
     taskRuns.create(...)        TaskRunService.create — 멱등/중복 검사 후 TaskRun INSERT
② 클레임                   TaskRunService.claim()          → TaskAttempt 생성, claimToken 발급
③ 실행 시작 표시           TaskRunService.startExecution() → attempt RUNNING
④ HTTP 호출                InternalAiExecutionClient.execute()
                              POST http://ai-server:8000/internal/v1/ai/executions
                              Authorization: Bearer <AI_INTERNAL_SERVICE_TOKEN>
                              X-Correlation-Id: <run.correlationId>
⑤ 응답 검증                호출자의 validator (도메인별) + 클라이언트의 봉투 검증
⑥ 채택 or 거부             TaskRunService.adopt()  /  rejectAndFail()
⑦ 도메인 반영              XxxPersistenceService.complete(...)  — 별도 @Transactional
```

**④는 반드시 DB 트랜잭션 밖에서 돈다.** `TaskRunWorker.execute()`가
`TransactionSynchronizationManager.isActualTransactionActive()`를 확인하고 켜져 있으면
`IllegalStateException("AI call must run outside a DB transaction")`을 던진다.
그래서 ①②③⑥⑦이 각각 짧은 트랜잭션으로 쪼개져 있는 것이다.

### 3-3. 실행 패턴이 **세 가지**다 — 어느 것을 쓰는지 반드시 확인

| 패턴 | 누가 실행하나 | 사용자 체감 | 쓰는 곳 |
|---|---|---|---|
| **A. 동기 인라인** | HTTP 요청 스레드가 직접 `claim→execute→adopt` | 응답이 올 때까지 대기 | `JourneyAiService`, `PersonaJourneyService`, `MarketingReportJourneyService`, `ConceptJourneyService.execute()` |
| **B. TaskRun 워커** | `@Scheduled` 폴러가 `TaskRunWorker.executeOne(type, workerId)` | 즉시 202 → 화면이 폴링 | `LegalPrecheckService` + `LegalPrecheckWorkerScheduler` (1초 주기) |
| **C. 인메모리 배치** | `conceptEligibilityExecutor`(ThreadPoolTaskExecutor, core 1 / max 2 / queue 20) | 즉시 반환 → 배치 상태 폴링 | `ConceptJourneyService.generate()` → `runEligibility()` |

패턴 A는 응답 검증을 **호출한 서비스가 직접** 한다(`this::validateIdea` 같은 `Consumer<JsonNode>`).
패턴 B는 **`TaskRunWorker.validateResult()`가** 한다 — 그리고 이 메서드는 현재
`IDEA_INTERPRETATION`·`IDEA_LEGAL_PRECHECK`·`CONCEPT_LEGAL_VALIDATION` **3개만** 안다.
그 밖의 TaskType으로 워커를 돌리면 `RESULT_DOMAIN_INVARIANT_VIOLATION`으로 무조건 거부된다.

패턴 C는 라운드 루프다: 생성 → origin 무결성 검사 → 법률 배치 검증 →
목표 개수(`CONCEPT_TARGET_ELIGIBLE_COUNT`, 기본 3)를 채울 때까지 최대
`CONCEPT_MAX_REPLACEMENT_ROUNDS`(2) 라운드, 후보 상한 `CONCEPT_MAX_INSPECTED_CANDIDATES`(9).

### 3-4. 결과가 도메인에 반영되는 두 시점

- 패턴 A/C: 실행 직후 `persistence.complete(...)` 호출
- 패턴 B: **조회 시 지연 반영.** `LegalPrecheckService.current()` → `synchronize(run)`이
  `TaskRun` 상태를 읽어 도메인 상태를 따라가고, `SUCCEEDED`인데 아직 버전이 없으면
  `materialize()`가 그때 `LegalPrecheckVersion`·`LegalGuardrailSet`을 만든다.

같은 지연 복구가 A 패턴에도 있다: `JourneyAiService.recoverAdoptedResult()` —
`TaskResult`가 `ADOPTED`인데 도메인 run이 미완료면 재호출 없이 결과만 다시 반영한다.
**AI를 다시 부르지 않고 복구하는 경로**이므로 지우면 비용이 샌다.

---

## 4. Spring ↔ AI 계약 (v1)

정본은 `docs/contracts/INTERNAL_AI_API_V1_CONTRACT.md`(91KB)와
`docs/contracts/fixtures/internal-ai-v1/`의 픽스처다. 실제 코드가 강제하는 핵심만 옮긴다.

### 요청 (Spring → AI)

`POST /internal/v1/ai/executions`

```json
{
  "contractVersion": "1.0",
  "taskType": "IDEA_INTERPRETATION",
  "taskSchemaVersion": "1.0",
  "taskRunId": "...", "taskAttemptId": "...", "correlationId": "...",
  "deadlineAt": "2026-08-04T09:00:00Z",
  "canonicalInputHash": "sha256:...",
  "locale": "ko-KR",
  "input": { "textContents": [ { "contentKey": "idea-source", "contentType": "PLAIN_TEXT",
      "language": "ko-KR", "totalCharacters": 1234, "contentHash": "sha256:...",
      "chunks": [ { "index": 0, "text": "...", "characterCount": 1234, "chunkHash": "sha256:..." } ] } ] }
}
```

### 응답 (AI → Spring)

성공은 **정확히 이 12개 필드**여야 한다 (`InternalAiExecutionClient.SUCCESS_FIELDS`):
`contractVersion, taskType, taskSchemaVersion, taskRunId, taskAttemptId, correlationId,
canonicalInputHash, resultSchemaVersion, result, warnings, provenance, usage`
— 하나라도 빠지거나 남으면 `RESULT_UNKNOWN_FIELD`로 거부.

실패는 `{"error": {"code", "message", "correlationId", "taskRunId", "taskAttemptId", "retryable", "details":[{"reason"}]}}`.
`code`는 12개 화이트리스트(`INTERNAL_CODES`) 안에 있어야 하고 밖이면 그 자체가 계약 위반이다.

### 양쪽이 강제하는 한계 — 어기면 조용히 실패한다

| 항목 | 값 | 강제하는 곳 |
|---|---|---|
| 요청/응답 JSON | ≤ **2 MiB** | Spring `InternalAiExecutionClient.MAX_JSON_BYTES`, AI `main.py` 미들웨어 |
| `textContents` 개수 | 1–64 | `executions.py validate_text_contents` |
| content당 chunk | 1–64, **총합도 64 이하** | 같은 곳 |
| chunk 텍스트 | 1–16,384자 | 같은 곳 (Spring은 16,000 코드포인트로 자름) |
| `input` 스냅샷 | ≤ 2 MiB, 유효 JSON | `TaskRunService.validateCreation` |
| `maxAttempts` | 1–20 | 같은 곳 |
| `deadlineAt` | `…Z` 형식, **미래여야 함** | `executions.py` — 과거면 즉시 `DEADLINE_EXCEEDED` |
| `X-Correlation-Id` 헤더 | 본문 `correlationId`와 **일치** | `executions.py` — 불일치 시 `HEADER_BODY_CORRELATION_MISMATCH` |

### canonical input hash — 가장 자주 깨지는 곳

양쪽이 **독립적으로 같은 해시를 계산해 대조**한다.
Spring `CanonicalInputHasher` ↔ AI `executions.py canonical_hash`.

규칙:
1. 해시 대상은 `{contractVersion, input, locale, taskSchemaVersion, taskType}` **5개뿐**
2. 객체 키는 **NFC 정규화 후 코드포인트 순 정렬**, 정규화 후 키 충돌은 에러
3. 문자열도 NFC 정규화
4. 구분자 공백 없음 (`,` `:`)
5. **부동소수점 숫자 금지** — Spring이 `"floating-point JSON numbers are not canonical task input"`으로 던진다

→ 새 task의 input에 `0.35` 같은 값을 넣으면 **컴파일도 테스트도 통과하고 런타임에만 깨진다.**
비율이 필요하면 정수 basis point(`35` = 0.35%)나 문자열로 넣을 것.

### 응답 금칙 필드

`TaskRunWorker.rejectForbiddenFields()`가 결과 JSON 전체를 재귀 순회하며
`storageUrl, objectKey, presignedUrl, localPath, fileBytes, base64, prompt, rawProviderResponse, credential`
중 하나라도 있으면 거부한다. AI가 프롬프트나 원본 응답을 결과에 실어 보내는 것을 구조적으로 막는 장치다.

---

## 5. AI 서버 내부

```
ai/main.py                      FastAPI 앱. 미들웨어(2MiB 제한·중복 키 거부)·예외 핸들러·라우터 등록
 ├ app/api/executions.py        POST /internal/v1/ai/executions   ← 여정 AI의 전부
 ├ app/api/tasks.py             POST /internal/v1/tasks           ← 구 AiTask 경로 (아래 §7)
 ├ app/api/marketing.py         POST /api/v1/marketing/banners/generate (배너 이미지)
 └ app/api/errors.py            오류 정규화
app/services/
 ├ journey_provider.py          ★ 프롬프트 로드 → provider 호출 → Pydantic 검증
 ├ task_service.py              구 AiTask 핸들러 레지스트리
 ├ marketing_task_service.py / banner_service.py / artifact_service.py
 └ prompt_service.py            배너 프롬프트 조립
app/models/journey.py           ★ TaskType별 결과 Pydantic 모델 11개
app/legal/
 ├ pipeline.py                  IDEA_LEGAL_PRECHECK / CONCEPT_LEGAL_VALIDATION 파이프라인
 ├ concept_validation.py        GUARDRAIL / GUARDRAIL_BATCH 모드
 ├ moleg.py                     법제처 Open API 클라이언트
 └ registry.py                  법령 레지스트리
ai/prompts/<task_folder>/{system.md,user.md}   ★ 프롬프트 (11개 폴더)
```

`executions.py`의 분기 (141–152행):

```
CONCEPT_LEGAL_VALIDATION + validationMode=GUARDRAIL_BATCH → legal.concept_validation (배치)
CONCEPT_LEGAL_VALIDATION + validationMode=GUARDRAIL       → legal.concept_validation (단건)
IDEA_LEGAL_PRECHECK / CONCEPT_LEGAL_VALIDATION            → legal.pipeline (법제처 실연동)
그 외 11종                                                 → journey_provider.execute_journey_task
```

`execute_journey_task`는 `TaskType → 프롬프트 폴더` 매핑과 `TaskType → Pydantic 모델` 매핑
**두 개의 dict**를 탄다. 둘 중 하나만 추가하면 `AI_CONFIGURATION_INVALID` 또는
`RESULT_SCHEMA_INVALID`가 난다.

**provider 설정**: `AI_PROVIDER`는 `openai` 또는 `openai-compatible`만 허용.
`temperature=0.1`, `response_format={"type":"json_object"}` 고정. 응답에서 JSON 블록을
`_extract_json`이 뽑아낸다(```json 펜스 허용, 객체 1개만).

**Mock이 없다.** `.env.example`이 못박고 있다 — `AI_FIXTURE_MODE=false`,
"the redesigned Journey does not use fixture/success fallback".
키가 없으면 `DEPENDENCY_UNAVAILABLE / AI_CONFIGURATION_INVALID`로 실패하지, 가짜 결과를 만들지 않는다.

---

## 6. 데이터

- 마이그레이션: SQL `V1`–`V36` + **Java 마이그레이션 `V5`·`V10`** (`backend/src/main/java/db/migration/`).
  `ddl-auto=validate`. **다음 빈 버전은 V37.** V1–V36은 immutable.
- 여정 관련 주요 버전: V27 TaskRun 기반, V28 idea journey, V29 concept journey,
  V30 persona interview, V31 marketing report, V32 idea origin, V33 legal precheck guardrails,
  V34–V36 concept eligibility 루프·재시도.
- 파일 bytes는 MinIO(S3 호환), 메타데이터는 RDB. AI 서버는 둘 다 접근 못 한다.

---

## 7. 병존하는 옛 경로 — 헷갈리기 쉬운 3중 구조

이름이 비슷한 AI 연동 경로가 **셋** 있다. 새 작업은 전부 ①이다.

| | 경로 | Spring 쪽 | AI 쪽 | 상태 |
|---|---|---|---|---|
| ① | `POST /internal/v1/ai/executions` | `taskrun/` + `journey/` | `app/api/executions.py` | **현행. 여기에 붙일 것** |
| ② | `POST /internal/v1/tasks` | `aitask/` + `integration/ai/task/` (`AiTaskType` 3종) | `app/api/tasks.py` | 스모크·배너 아티팩트 전용 |
| ③ | provider 직접 어댑터 | `integration/ai/{document,feasibility,legal,persona,openai}` | 없음 (Spring이 OpenAI 직접 호출) | **레거시**. `AnalysisJob` 기반 |

`TaskType`(여정용, 13종)과 `AiTaskType`(②용, 3종)은 **이름만 같고 다른 enum**이다.
import를 잘못하면 컴파일은 되고 의미만 틀어진다.

③은 `analysis/{feasibility,financial,legal}`·`document`·`persona` 패키지와 `job/` 러너를 쓴다.
여정 화면에서 도달할 수 없다.

---

## 8. 오류가 사용자에게 도달하는 경로

```
provider 오류 → journey_provider.ProviderFailure(code, reason, status, retryable)
             → executions.py internal_error(...)  → HTTP 4xx/5xx + {"error": {...}}
             → InternalAiExecutionClient.parseFailure → ExecutionFailure
             → TaskRunService.mapPublic() 또는 서비스별 journeyFailureCode()
             → BusinessException(ErrorCode.*) → 프론트
```

공개 코드로의 축약 규칙(`TaskRunService.mapPublic`):

| 내부 | 공개 |
|---|---|
| `PAYLOAD_TOO_LARGE` | `PAYLOAD_TOO_LARGE` |
| `DEADLINE_EXCEEDED` | `TASK_TIMEOUT` |
| `INVALID_REQUEST`, `UNSUPPORTED_*`, `RESULT_SCHEMA_INVALID` | `AI_RESULT_INVALID` |
| 그 외 | `AI_SERVICE_UNAVAILABLE` |
| (예외) `reason == AI_CONFIGURATION_INVALID` | `AI_CONFIGURATION_INVALID` — 키 미설정을 운영자가 구분할 수 있게 |

**provider 응답 원문·비밀값은 절대 사용자에게 나가지 않는다.** 진단은 서버 로그로만
(`log.warn("AI result contract invalid taskType=… fieldPath=… topLevelFields=…")`).

---

## 9. 지뢰 (실측)

1. **부동소수점 input 금지** — §4의 canonical hash 규칙. 런타임에만 터진다.
2. **`TaskRunWorker.validateResult`는 3개 TaskType만 안다** — 워커(패턴 B)로 새 타입을 돌리려면
   여기를 반드시 고쳐야 한다. 안 고치면 AI 호출은 성공하고 결과만 버려진다.
3. **AI 호출은 트랜잭션 밖** — 도메인 서비스에 `@Transactional`을 통째로 붙이면 런타임 예외.
4. **결과 필드 집합은 정확히 일치해야 한다** — `Set.copyOf(result.propertyNames()).equals(expected)`.
   프롬프트가 필드를 하나 더 만들어도 전체가 거부된다. 프롬프트와 validator는 항상 같이 고친다.
5. **`X-Correlation-Id` 헤더와 본문 `correlationId`가 다르면 400.**
6. **`deadlineAt`은 미래여야 한다** — 테스트에서 고정 시각을 쓰면 `DEADLINE_EXCEEDED`.
7. **`AiTaskType` ≠ `TaskType`** (§7).
8. **`ai/legal/`은 이제 없다.** 정본은 `ai/app/legal/`. 디스크에 남은 `ai/legal/`은 추적되지 않는
   `__pycache__`·`출력/` 잔재다.
9. **로그 인코딩**: Java/gradle 로그는 CP949, Python 로그는 UTF-8. `Get-Content -Encoding UTF8`을
   잘못 붙이면 한글이 깨져 오진한다.
10. **PowerShell 5.1은 BOM 없는 `.ps1`을 ANSI로 읽는다** — 스크립트에 한글 경로 리터럴 금지.
