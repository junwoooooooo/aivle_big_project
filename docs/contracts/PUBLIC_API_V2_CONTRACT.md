# Public API v2 As-Is Contract

- Status: CURRENT_AS_BUILT
- Baseline date: 2026-08-04
- Scope: 실제 Spring `/api/v2` Controller와 `frontEnd` Journey client
- Implementation Status: IMPLEMENTED_WITH_RETAINED_MVP

## 1. 권위와 범위

현재 Public API 실행 권위는 실제 Spring Controller, `frontEnd/src/features/journey/journeyApi.js`, `frontEnd/src/shared/api/apiClient.js`, 현재 response/error 구현이다. 이 문서는 그 As-Is를 기록하며 Controller를 과거 Target 계약에 맞추도록 요구하지 않는다.

현재 공식 Journey는 다음에서 종료한다.

`Idea 입력 → AI 해석 → Idea Origin 보완·확정 → Legal Precheck → Legal Guardrail → Concept 생성 → Origin Integrity → Concept Legal Validation → 적격 Concept 3개 표시`

Quick Assessment, Detailed Analysis, Concept Selection, Persona, Interview, Marketing, Final Report API는 구현·Route·UI를 보존한 **기존 MVP 실험 기능**이다. 공식 Journey와 자동 연결된 단계가 아니다.

`docs/api/openapi.yaml`은 Backend semantic test가 읽는 기존 `/api/v1` 중심 machine-consumed 계약이다. 현재 Journey `/api/v2` 전체의 단일 권위가 아니며 전면 통합은 별도 기능 작업이다.

## 2. 실제 response와 client 처리

Journey Controller success envelope:

```json
{
  "success": true,
  "data": {},
  "meta": { "requestId": "opaque", "timestamp": "RFC3339" }
}
```

일반 오류는 `GlobalExceptionHandler`가 같은 `ApiResponse` 계열로 반환한다.

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "safe message",
    "fieldErrors": [],
    "retryable": false
  },
  "meta": { "requestId": "opaque", "timestamp": "RFC3339" }
}
```

TaskRun v2 success/error는 별도 envelope다.

```json
{ "data": {}, "meta": { "correlationId": "opaque" } }
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

`apiClient.request()`는 JSON payload 전체를 반환하고 `journeyApi`가 success payload의 `.data`를 한 번만 추출한다. TaskRun retry도 이 방식으로 `data`를 반환한다. 현재 Journey client에는 TaskRun GET/cancel wrapper가 없다.

## 3. Public API As-Is Matrix

`ApiResponse`는 `{success,data,error,meta.requestId/meta.timestamp}`, `TaskEnvelope`는 `{data,meta.correlationId}`를 뜻한다. 별도 status 선언이 없는 Spring handler는 200이다.

| 기능 | Method | 실제 Path | Request | HTTP Status | Response Envelope | 실행 방식 | 현재 UI 사용 여부 |
|---|---|---|---|---:|---|---|---|
| Idea TEXT 저장 | POST | `/api/v2/projects/{projectId}/ideas` | JSON `{title?, text}` | 200 | ApiResponse | 동기 저장 | 예, 현재 Journey |
| Idea FILE 저장 | POST | `/api/v2/projects/{projectId}/ideas` | multipart `title?`, `file` | 200 | ApiResponse | 동기 저장/Storage | 예, 현재 Journey |
| 현재 Idea | GET | `/api/v2/projects/{projectId}/ideas/current` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 현재 Journey |
| Idea Interpretation 시작 | POST | `/api/v2/projects/{projectId}/idea-interpretations` | 없음 | 200 | ApiResponse | Service 내부 동기 claim/execute + TaskRun | 예, 현재 Journey |
| 현재 Interpretation | GET | `/api/v2/projects/{projectId}/idea-interpretations/current` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 현재 Journey |
| Idea Origin workspace | GET | `/api/v2/projects/{projectId}/idea-origin` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 현재 Journey |
| Origin 질문 답변 | PUT | `/api/v2/projects/{projectId}/idea-origin/questions/{questionId}` | `{answer, answerSource}` | 200 | ApiResponse | 동기 저장 | 예, 현재 Journey |
| Origin 보완 반영 | POST | `/api/v2/projects/{projectId}/idea-origin/apply` | `{draftVersionId}` | 200 | ApiResponse | 동기 version 생성 | 예, 현재 Journey |
| 이전 IdeaVersion 확정 | POST | `/api/v2/projects/{projectId}/idea-versions/{ideaVersionId}/confirm` | 없음 | 200 | ApiResponse | 동기 compatibility 경로 | 아니오 |
| 이전 Legal Review 실행 | POST | `/api/v2/projects/{projectId}/legal-reviews` | 없음 | 200 | ApiResponse | 이전 Journey 동기 경로 | 아니오 |
| 이전 Legal Review 조회 | GET | `/api/v2/projects/{projectId}/legal-reviews/current` | 없음 | 200 | ApiResponse | 이전 Journey 조회 | 아니오 |
| Legal Precheck 시작 | POST | `/api/v2/projects/{projectId}/legal-prechecks` | 없음 | 202 | ApiResponse | Persistent Worker TaskRun | 예, 현재 Journey |
| Legal 공식 Source 재확인 | POST | `/api/v2/projects/{projectId}/legal-prechecks/refresh` | 없음 | 202 | ApiResponse | 현재 확정 Origin을 유지하고 새 Persistent Worker TaskRun 시작 | 예, SOURCE_PARTIAL/REGISTRY_GAP 후속 확인 |
| 현재 Legal Precheck | GET | `/api/v2/projects/{projectId}/legal-prechecks/current` | 없음 | 200 | ApiResponse | Run/result/guardrail 조회 | 예, 현재 Journey |
| Legal 질문 반영 | POST | `/api/v2/projects/{projectId}/legal-prechecks/answers/apply` | `{ideaOriginVersionId}` | 200 | ApiResponse | 새 Origin 생성 | 예, 현재 Journey |
| Legal 질문 일괄 반영·재검토 | POST | `/api/v2/projects/{projectId}/legal-prechecks/answers/apply-and-restart` | `{ideaOriginVersionId}` | 200 | ApiResponse | 새 Origin 1개 생성 후 Precheck 자동 시작 | 예, 현재 Journey |
| Revision 제안 단건 수락 | POST | `/api/v2/projects/{projectId}/legal-prechecks/versions/{versionId}/revision-suggestions/{index}/accept` | 없음 | 200 | ApiResponse | 호환용 단건 Origin 생성 | 아니오, Compatibility |
| Revision 계획 일괄 반영·재검토 | POST | `/api/v2/projects/{projectId}/legal-prechecks/versions/{versionId}/revision-suggestions/accept` | `{indexes:[...]}` | 200 | ApiResponse | 선택 Category를 Origin 1개에 반영 후 Precheck 자동 시작 | 예, 현재 Journey |
| Concept Generation 시작 | POST | `/api/v2/projects/{projectId}/concept-generations` | 없음 | 202 | ApiResponse | In-memory Executor + TaskRun | 예, 현재 Journey |
| 현재 Concept batch | GET | `/api/v2/projects/{projectId}/concept-generations/current` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 현재 Journey |
| 적격 Concept 조회 | GET | `/api/v2/projects/{projectId}/concepts` | 없음 | 200 | ApiResponse | ELIGIBLE 결과 조회 | 예, 현재 Journey |
| Quick Assessment 시작 | POST | `/api/v2/projects/{projectId}/quick-assessments` | 없음 | 200 | ApiResponse | Service 내부 동기 claim/execute | 예, 보존 MVP |
| 현재 Quick 결과 | GET | `/api/v2/projects/{projectId}/quick-assessments/current` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Shortlist 저장 | PUT | `/api/v2/projects/{projectId}/shortlist` | `{conceptVersionIds, reason}` | 200 | ApiResponse | 동기 저장 | 예, 보존 MVP |
| 현재 Shortlist | GET | `/api/v2/projects/{projectId}/shortlist` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Detailed Analysis 시작 | POST | `/api/v2/projects/{projectId}/detailed-analyses` | `{financials:[FinancialInput]}` | 200 | ApiResponse | Service 내부 동기 claim/execute | 예, 보존 MVP |
| 현재 Detailed 결과 | GET | `/api/v2/projects/{projectId}/detailed-analyses/current` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Concept Selection 저장 | PUT | `/api/v2/projects/{projectId}/concept-selection` | `{conceptVersionId, reason}` | 200 | ApiResponse | 동기 저장 | 예, 보존 MVP |
| 현재 Concept Selection | GET | `/api/v2/projects/{projectId}/concept-selection` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Persona Study 생성 | POST | `/api/v2/projects/{projectId}/persona-studies` | 없음 | 200 | ApiResponse | 동기 root 생성 | 예, 보존 MVP |
| 현재 Persona Study | GET | `/api/v2/projects/{projectId}/persona-studies/current` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Persona Card 생성 | POST | `/api/v2/projects/{projectId}/persona-cards/generate` | 없음 | 200 | ApiResponse | Service 내부 동기 claim/execute | 예, 보존 MVP |
| Persona Card 조회 | GET | `/api/v2/projects/{projectId}/persona-cards` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Persona Interview 실행 | POST | `/api/v2/projects/{projectId}/persona-interviews` | `{personaCardVersionIds}` | 200 | ApiResponse | Persona별 동기 claim/execute | 예, 보존 MVP |
| Persona Interview 조회 | GET | `/api/v2/projects/{projectId}/persona-interviews` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Interview Synthesis | POST | `/api/v2/projects/{projectId}/interview-syntheses` | 없음 | 200 | ApiResponse | Service 내부 동기 claim/execute | 예, 보존 MVP |
| 현재 Synthesis | GET | `/api/v2/projects/{projectId}/interview-syntheses/current` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Marketing 생성 | POST | `/api/v2/projects/{projectId}/marketing-generations` | 없음 | 200 | ApiResponse | Service 내부 동기 claim/execute | 예, 보존 MVP |
| Marketing Workspace | GET | `/api/v2/projects/{projectId}/marketing-workspace` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Marketing Asset 선택 | PUT | `/api/v2/projects/{projectId}/marketing-assets/{assetId}/select` | 없음 | 200 | ApiResponse | 동기 저장 | 예, 보존 MVP |
| Marketing Comparison | POST | `/api/v2/projects/{projectId}/marketing-comparisons` | 없음 | 200 | ApiResponse | Service 내부 동기 claim/execute | 예, 보존 MVP |
| 현재 Comparison | GET | `/api/v2/projects/{projectId}/marketing-comparisons/current` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| Final Report 생성 | POST | `/api/v2/projects/{projectId}/final-reports` | 없음 | 200 | ApiResponse | Service 내부 동기 claim/execute | 예, 보존 MVP |
| 현재 Final Report | GET | `/api/v2/projects/{projectId}/final-reports/current` | 없음 | 200 | ApiResponse | 동기 조회 | 예, 보존 MVP |
| 사용자 Report 결정 | PUT | `/api/v2/projects/{projectId}/final-reports/{reportId}/decision` | `{decision, reasons}` | 200 | ApiResponse | 동기 저장 | 예, 보존 MVP |
| TaskRun 조회 | GET | `/api/v2/projects/{projectId}/task-runs/{taskRunId}` | 없음 | 200 | TaskEnvelope | 동기 조회 | 현재 Journey wrapper 없음 |
| TaskRun retry | POST | `/api/v2/projects/{projectId}/task-runs/{taskRunId}/retry` | `Idempotency-Key` header 필수 | 202 | TaskEnvelope | 같은 TaskRun의 새 Attempt | 예, 실패 복구 |
| TaskRun cancel | POST | `/api/v2/projects/{projectId}/task-runs/{taskRunId}/cancel` | 없음 | 200 | TaskEnvelope | 동기 cancel request | 현재 Journey wrapper 없음 |

`FinancialInput`은 `conceptVersionId`, `unitPrice`, `monthlyCustomers`, `variableCostPerCustomer`, `monthlyFixedCost`, `initialInvestment`를 포함한다.

## 4. 구현 상태 구분

| 구분 | 상태 |
|---|---|
| Idea/Origin/Legal Precheck/Concept Eligibility | 현재 구현된 공식 Journey |
| Quick/Detailed/Selection/Persona/Interview/Marketing/Final Report | 구현·UI·Route가 보존된 기존 MVP 실험 기능 |
| 이전 `/idea-versions/{id}/confirm`, `/legal-reviews*` | 구현은 남았으나 현재 `journeyApi`가 사용하지 않는 compatibility 성격의 경로 |
| 과거 Target 문서의 workflow/history/cursor/version/export endpoint | 현재 코드에 없으므로 현재 API가 아님 |
| Public API compatibility redirect | Spring `/api/v2` Controller에서 확인되지 않음 |

## 5. 인증·오류·비동기 주의사항

- 기존 Spring 인증과 Project owner scope를 사용한다.
- 일반 Journey 오류 code/status는 `ErrorCode`와 `GlobalExceptionHandler`가 결정한다.
- TaskRun API는 `TaskRunV2ExceptionHandler`의 별도 stable envelope를 사용한다.
- 모든 AI 기능을 202/polling으로 통일하지 않았다. 202는 현재 Legal Precheck, Concept Generation, TaskRun retry에 사용된다.
- `ApiResponse`의 correlation field는 `meta.requestId`; TaskRun envelope는 `meta.correlationId` 및 `X-Correlation-Id` header를 사용한다.
- Public API와 `/internal/v1/ai/executions` Internal API는 별도 계약이다. Internal v1은 13개 TaskType과 별도 identity/hash validation을 사용한다.
