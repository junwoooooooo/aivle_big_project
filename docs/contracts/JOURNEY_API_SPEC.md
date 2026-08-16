# Journey API 명세서 (프론트 ↔ 백엔드 통신 규약)

- Status: **AS_BUILT** — Spring Controller와 `frontEnd/src/features/journey/journeyApi.js`에서 직접 읽어 작성
- Baseline: 2026-08-05 / `581234a`
- 목적: **통신 규약**. 값의 스키마는 [`JOURNEY_DATA_CONTRACT.md`](JOURNEY_DATA_CONTRACT.md),
  단계 이동 조건은 [`JOURNEY_STATE_MACHINE_SPEC.md`](JOURNEY_STATE_MACHINE_SPEC.md)를 볼 것.

> **우선순위:** 코드 > 이 문서. 충돌하면 Controller가 맞다.
> 이 문서는 `PUBLIC_API_V2_CONTRACT.md`의 As-Is 매트릭스를 요청/응답 관점으로 다시 정리한 것이다.

---

## 1. 공통 규약

### 1-1. 베이스 경로

모든 여정 API는 `/api/v2/projects/{projectId}` 아래에 있다.
프론트의 유일한 진입점은 `createJourneyApi(client, projectId)`
(`frontEnd/src/features/journey/journeyApi.js`)이다. 화면이 `fetch`를 직접 부르지 않는다.

### 1-2. 인증·인가

| 항목 | 값 |
|---|---|
| 인증 | 기존 Spring JWT 인증. `CurrentUserProvider.currentUserId()` |
| 인가 | **프로젝트 소유자 스코프**. 모든 서비스가 `projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)`로 시작 |
| 실패 시 | `PROJECT_ACCESS_DENIED` (403) |

### 1-3. 요청 헤더

| 헤더 | 필수 | 용도 |
|---|---|---|
| `Authorization: Bearer <JWT>` | 예 | 인증 |
| `X-Request-Id` | 아니오 | 응답 `meta.requestId`로 그대로 반향된다. 모든 Controller가 `request.getHeader("X-Request-Id")`를 읽는다 |
| `Idempotency-Key` | **TaskRun retry만 필수** | 같은 키로는 새 Attempt를 만들지 않는다 |
| `Content-Type: multipart/form-data` | 파일 업로드만 | `POST /ideas` 파일 변형 |

### 1-4. 응답 봉투가 **두 종류**다

**① `ApiResponse` — 여정 API 전부**

성공:
```json
{
  "success": true,
  "data": { },
  "meta": { "requestId": "opaque|null", "timestamp": "RFC3339" }
}
```

실패 (`GlobalExceptionHandler`):
```json
{
  "success": false,
  "error": {
    "code": "ANALYSIS_INPUT_INVALID",
    "message": "분석 입력이 올바르지 않습니다.",
    "fieldErrors": [],
    "retryable": false
  },
  "meta": { "requestId": "opaque|null", "timestamp": "RFC3339" }
}
```

**② `TaskEnvelope` — `/task-runs/**` 전용** (`TaskRunV2ExceptionHandler`)

```json
{ "data": { }, "meta": { "correlationId": "opaque" } }
```
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed.",
    "correlationId": "opaque",
    "taskRunId": null,
    "details": []
  }
}
```

> 상관관계 필드 이름이 다르다. `ApiResponse`는 `meta.requestId`, TaskRun은 `meta.correlationId` +
> `X-Correlation-Id` 헤더다. 섞어 쓰면 로그 추적이 끊긴다.

### 1-5. 클라이언트의 `.data` 추출

`apiClient.request()`는 JSON 페이로드 **전체**를 반환하고, `journeyApi`의 각 메서드가
`.data`를 **한 번만** 벗긴다. TaskRun retry도 같은 방식으로 `data`를 반환한다.
→ 화면 코드가 보는 것은 항상 `data` 안쪽이다.

### 1-6. HTTP 상태

별도 선언이 없는 Spring 핸들러는 **200**이다. 202를 쓰는 곳은 **셋뿐**이다.

| 202를 반환하는 엔드포인트 | 이유 |
|---|---|
| `POST /legal-prechecks`, `POST /legal-prechecks/refresh` | 워커(패턴 B)가 나중에 실행 |
| `POST /concept-generations` | 인메모리 배치(패턴 C)가 나중에 실행 |
| `POST /task-runs/{id}/retry` | 새 Attempt 큐잉 |

**나머지 AI 호출은 전부 동기다.** 요청 스레드가 AI 응답까지 기다린다.
그래서 프론트가 엔드포인트별로 다른 타임아웃을 건다(§4).

---

## 2. 엔드포인트 명세

`ApiResponse`/`TaskEnvelope`는 §1-4의 봉투를 뜻한다. **Response**는 봉투 안 `data`의 타입이며
타입 정의는 데이터 계약 문서 §2에 있다.

### 2-1. 아이디어 입력·해석 — `JourneyController`

| # | Method | Path | Request | Status | Response(`data`) | 실행 |
|---|---|---|---|---:|---|---|
| 1 | POST | `/ideas` | JSON `{title?: ≤200, text: 필수 ≤200000}` | 200 | `IdeaSourceView` | 동기 저장 |
| 2 | POST | `/ideas` | multipart `title?`, `file` | 200 | `IdeaSourceView` | 동기 저장 + 텍스트 추출 |
| 3 | GET | `/ideas/current` | — | 200 | `IdeaSourceView` | 조회 |
| 4 | POST | `/idea-interpretations` | 없음 | 200 | `InterpretationView` | **동기 AI** (패턴 A) |
| 5 | GET | `/idea-interpretations/current` | — | 200 | `InterpretationView` | 조회 |
| 6 | GET | `/idea-origin` | — | 200 | `WorkspaceView` | 조회 |
| 7 | PUT | `/idea-origin/questions/{questionId}` | `{answer: 필수 ≤20000, answerSource: 필수 ≤300}` | 200 | `QuestionView` | 동기 저장 |
| 8 | POST | `/idea-origin/apply` | `{draftVersionId: 필수}` | 200 | `WorkspaceView` | 동기 CONFIRMED 버전 생성 |

호환용 경로 (현재 `journeyApi`가 호출하지 않음):

| Method | Path | Request | Status | Response |
|---|---|---|---:|---|
| POST | `/idea-versions/{ideaVersionId}/confirm` | 없음 | 200 | `IdeaVersionView` |
| POST | `/legal-reviews` | 없음 | 200 | `LegalView` |
| GET | `/legal-reviews/current` | — | 200 | `LegalView` |

**파일 업로드 오류** (`JourneyAiService.saveFile`):

| 조건 | 코드 | HTTP |
|---|---|---|
| `file`이 없거나 비어 있음 | `FILE_REQUIRED` | 400 |
| 파서가 지원하지 않는 형식 | `FILE_TYPE_UNSUPPORTED` | 415 |
| 파싱 실패 | `DOCUMENT_PARSE_FAILED` | 422 (retryable) |

### 2-2. 법률 사전점검 — `LegalPrecheckController`

베이스: `/api/v2/projects/{projectId}/legal-prechecks`

| # | Method | Path | Request | Status | Response(`data`) | 실행 |
|---|---|---|---|---:|---|---|
| 9 | POST | `` (베이스) | 없음 | **202** | `StartView` | **워커 큐잉** (패턴 B) |
| 10 | POST | `/refresh` | 없음 | **202** | `StartView` | 확정 Origin 유지하고 새 Run 강제 |
| 11 | GET | `/current` | — | 200 | `CurrentView` | 조회 + **지연 반영** |
| 12 | POST | `/answers/apply` | `{ideaOriginVersionId: 필수}` | 200 | `WorkspaceView` | 새 Origin 생성 |
| 13 | POST | `/answers/apply-and-restart` | `{ideaOriginVersionId: 필수}` | 200 | `RevisionApplyView` | 새 Origin + Precheck 자동 시작 |
| 14 | POST | `/versions/{versionId}/revision-suggestions/{index}/accept` | 없음, `index ≥ 0` | 200 | `WorkspaceView` | 호환용 단건 반영 |
| 15 | POST | `/versions/{versionId}/revision-suggestions/accept` | `{indexes: [int ≥0], 1~50개}` | 200 | `RevisionApplyView` | 선택 반영 + 자동 재시작 |

**#11은 단순 조회가 아니다.** `LegalPrecheckService.current()`가 `synchronize(run)`으로 TaskRun
상태를 도메인에 따라붙이고, `SUCCEEDED`인데 버전이 없으면 그 시점에 `materialize()`가
`LegalPrecheckVersion`·`LegalGuardrailSet`을 만든다. **폴링 대상이 이 엔드포인트다.**

**#9의 멱등성:** 같은 `(projectId, ideaOriginVersionId, inputSnapshotHash)` Run이 이미
`QUEUED`/`RUNNING`이면 새 TaskRun을 만들지 않고 기존 것을 그대로 돌려준다.
종료 상태여도 `AI_CONFIGURATION_INVALID` 실패가 아닌 한 재사용한다. `/refresh`만 이를 무시한다.

### 2-3. 컨셉 — `ConceptJourneyController`

| # | Method | Path | Request | Status | Response(`data`) | 실행 |
|---|---|---|---|---:|---|---|
| 16 | POST | `/concept-generations` | 없음 | **202** | `BatchView` | **인메모리 배치** (패턴 C) |
| 17 | GET | `/concept-generations/current` | — | 200 | `BatchView`\|null | 조회 |
| 18 | GET | `/concepts` | — | 200 | `ConceptView[]` | ELIGIBLE만. 조건 불충족 시 **빈 배열** |
| 19 | POST | `/quick-assessments` | 없음 | 200 | `QuickView` | **동기 AI** |
| 20 | GET | `/quick-assessments/current` | — | 200 | `QuickView` | 조회 |
| 21 | PUT | `/shortlist` | `{conceptVersionIds: Long[], reason: string}` | 200 | `ShortlistView` | 동기 저장 |
| 22 | GET | `/shortlist` | — | 200 | `ShortlistView` | 조회 |
| 23 | POST | `/detailed-analyses` | `{financials: FinancialInput[]}` | 200 | `DetailedView` | **동기 AI** |
| 24 | GET | `/detailed-analyses/current` | — | 200 | `DetailedView` | 조회 |
| 25 | PUT | `/concept-selection` | `{conceptVersionId: Long, reason: string}` | 200 | `SelectionView` | 동기 저장 |
| 26 | GET | `/concept-selection` | — | 200 | `SelectionView` | 조회 |

**#18은 404를 내지 않는다.** 배치가 `COMPLETED`가 아니거나 origin/guardrail이 없거나
`inputSnapshotHash`가 현재 입력과 다르면 **빈 배열**을 반환한다. 프론트는 이 경우를
"아직 없음"으로 다뤄야 하고, 오류로 처리하면 안 된다.

**#23 `FinancialInput` 검증** (`validateFinancialInputs`, 전부 `ANALYSIS_INPUT_INVALID`):
- `financials`의 개수와 `conceptVersionId` 집합이 shortlist와 **정확히 일치**해야 한다
- `unitPrice > 0`, `monthlyCustomers ≥ 0`, `variableCostPerCustomer ≥ 0`,
  `monthlyFixedCost ≥ 0`, `initialInvestment ≥ 0`
- **`unitPrice > variableCostPerCustomer`** (같거나 작으면 거부)

### 2-4. 페르소나·인터뷰 — `PersonaJourneyController`

| # | Method | Path | Request | Status | Response(`data`) | 실행 |
|---|---|---|---|---:|---|---|
| 27 | POST | `/persona-studies` | 없음 | 200 | `StudyView` | 동기 생성 |
| 28 | GET | `/persona-studies/current` | — | 200 | `StudyView` | 조회 |
| 29 | POST | `/persona-cards/generate` | 없음 | 200 | `PersonaView[]` | **동기 AI** |
| 30 | GET | `/persona-cards` | — | 200 | `PersonaView[]` | 조회 |
| 31 | POST | `/persona-interviews` | `{personaCardVersionIds: Long[]}` | 200 | `InterviewView[]` | **페르소나별 동기 AI 반복** |
| 32 | GET | `/persona-interviews` | — | 200 | `InterviewView[]` | 조회 |
| 33 | POST | `/interview-syntheses` | 없음 | 200 | `SynthesisView` | **동기 AI** |
| 34 | GET | `/interview-syntheses/current` | — | 200 | `SynthesisView` | 조회 |

**#31은 선택과 실행을 겸한다.** 보낸 `personaCardVersionIds`가 곧 "선택된 페르소나"로 저장되고
(`persistence.selectPersonas`), 그 각각에 대해 인터뷰 AI가 순차 호출된다. 그래서 프론트 타임아웃이
180초로 가장 길다.

### 2-5. 마케팅·최종 리포트 — `MarketingReportJourneyController`

| # | Method | Path | Request | Status | Response(`data`) | 실행 |
|---|---|---|---|---:|---|---|
| 35 | POST | `/marketing-generations` | 없음 | 200 | `WorkspaceView` | **동기 AI** |
| 36 | GET | `/marketing-workspace` | — | 200 | `WorkspaceView` | 조회 |
| 37 | PUT | `/marketing-assets/{assetId}/select` | 없음 | 200 | `WorkspaceView` | 동기 토글 |
| 38 | POST | `/marketing-comparisons` | 없음 | 200 | `ComparisonView` | **동기 AI** |
| 39 | GET | `/marketing-comparisons/current` | — | 200 | `ComparisonView` | 조회 |
| 40 | POST | `/final-reports` | 없음 | 200 | `ReportView` | **동기 AI** |
| 41 | GET | `/final-reports/current` | — | 200 | `ReportView` | 조회 |
| 42 | PUT | `/final-reports/{reportId}/decision` | `{decision: string, reasons: string[]}` | 200 | `ReportView` | 동기 저장 |

**#42 검증:** `decision`이 허용 집합(`DECISIONS`) 안이어야 하고, `reasons`는 공백 제거 후
**최소 1개**가 남아야 한다. 아니면 `ANALYSIS_INPUT_INVALID`.

**#40의 재실행 억제:** 같은 `sourceReferenceJson`(idea/concept/synthesis/comparison/선택 자산 ID
묶음)으로 이미 `SUCCEEDED`한 리포트가 있으면 **AI를 다시 부르지 않고** 기존 것을 반환한다.
`PENDING`/`RUNNING`이면 `ANALYSIS_ALREADY_RUNNING`(409).

### 2-6. TaskRun — `TaskRunV2Controller`

봉투가 `TaskEnvelope`다.

| # | Method | Path | Request | Status | 비고 |
|---|---|---|---|---:|---|
| 43 | GET | `/task-runs/{taskRunId}` | — | 200 | `journeyApi`에 래퍼 없음 |
| 44 | POST | `/task-runs/{taskRunId}/retry` | `Idempotency-Key` 헤더 **필수** | **202** | 같은 TaskRun의 새 Attempt |
| 45 | POST | `/task-runs/{taskRunId}/cancel` | — | 200 | `journeyApi`에 래퍼 없음 |

`journeyApi.retryTaskRun`은 `Idempotency-Key: journey-retry-<taskRunId>`를 고정으로 보낸다.
→ **같은 TaskRun에 대한 재시도는 몇 번 눌러도 Attempt가 하나만 늘어난다.**

---

## 3. 오류 계약

### 3-1. 여정 오류 코드 (`ErrorCode`)

| 코드 | HTTP | retryable | 언제 |
|---|---:|---|---|
| `PROJECT_ACCESS_DENIED` | 403 | false | 프로젝트 소유자가 아님 |
| `PROJECT_STAGE_INVALID` | 422 | false | 선행 단계 산출물이 없음 (컨셉 선택 없이 페르소나 등) |
| `ANALYSIS_INPUT_INVALID` | 422 | false | 입력이 선행 산출물과 불일치 |
| `ANALYSIS_ALREADY_RUNNING` | 409 | false | 같은 입력의 실행이 진행 중 |
| `IDEA_NOT_FOUND` | 404 | false | 저장된 IdeaSource 없음 |
| `IDEA_NOT_CONFIRMED` | 409 | false | 확정되지 않은 아이디어로 후속 단계 시도 |
| `LEGAL_REVIEW_NOT_FOUND` | 404 | false | 최종 리포트 입력에 법률 결과가 없음 |
| `RESOURCE_NOT_FOUND` | 404 | false | 질문·버전 ID가 프로젝트 소유가 아님 |
| `RESOURCE_VERSION_CONFLICT` | 409 | **true** | 이미 반영된 draft의 질문을 다시 답변 |
| `FILE_REQUIRED` | 400 | false | 업로드 파일 없음 |
| `FILE_TYPE_UNSUPPORTED` | 415 | false | 파서 미지원 형식 |
| `FILE_TOO_LARGE` | 413 | false | 크기 초과 |
| `DOCUMENT_PARSE_FAILED` | 422 | **true** | 문서 구조화 실패 |
| `AI_RESULT_INVALID` | 502 | false | AI 응답이 계약 위반 |
| `AI_CONFIGURATION_INVALID` | 503 | false | provider/API Key 미설정 |
| `EXTERNAL_AI_SERVICE_UNAVAILABLE` | 503 | **true** | AI 서버 연결 불가 |

### 3-2. AI 내부 오류가 공개 코드로 축약되는 규칙

`TaskRunService.mapPublic()`:

| 내부(AI) | 공개 |
|---|---|
| `PAYLOAD_TOO_LARGE` | `PAYLOAD_TOO_LARGE` |
| `DEADLINE_EXCEEDED` | `TASK_TIMEOUT` |
| `INVALID_REQUEST`, `UNSUPPORTED_*`, `RESULT_SCHEMA_INVALID` | `AI_RESULT_INVALID` |
| 그 외 | `AI_SERVICE_UNAVAILABLE` |
| (예외) `reason == AI_CONFIGURATION_INVALID` | `AI_CONFIGURATION_INVALID` |

**provider 원문·프롬프트·비밀값은 응답에 절대 실리지 않는다.** 진단은 서버 로그로만 한다.

---

## 4. 프론트 타임아웃 (`journeyApi.js` 실측)

동기 AI 엔드포인트는 기본 타임아웃으로는 못 버틴다. 클라이언트가 개별 지정한다.

| 엔드포인트 | `timeoutMs` |
|---|---:|
| `POST /idea-interpretations` | 90,000 |
| `POST /concept-generations` | 120,000 |
| `POST /quick-assessments` | 120,000 |
| `POST /detailed-analyses` | 120,000 |
| `POST /persona-cards/generate` | 120,000 |
| `POST /interview-syntheses` | 120,000 |
| **`POST /persona-interviews`** | **180,000** (페르소나 수만큼 순차 호출) |
| `POST /marketing-generations` | 150,000 |
| `POST /marketing-comparisons` | 150,000 |
| `POST /final-reports` | 150,000 |
| 그 외 (GET·PUT·202 계열) | 기본값 |

> 새 동기 AI 엔드포인트를 추가하면 `journeyApi.js`에 `timeoutMs`를 **반드시 같이** 넣어야 한다.
> 안 넣으면 백엔드는 성공하는데 브라우저만 끊긴다.

---

## 5. 이 명세에 없는 것

- `/api/v1` 레거시 컨트롤러 (`legal-reviews`, `feasibility-assessments`, `financial-analyses`,
  `marketing-contents`, `persona-recommendations`, `panel-interviews`, `market-responses`) —
  코드는 살아 있으나 여정에서 도달 불가. `docs/api/openapi.yaml`이 담당한다.
- `POST /internal/v1/ai/executions` — Spring↔AI 내부 계약. 별도 문서
  `INTERNAL_AI_API_V1_CONTRACT.md`. **프론트는 AI 서버를 직접 부르지 않는다.**
- 과거 Target 문서의 workflow/history/cursor/version/export 엔드포인트 — 코드에 없다.
