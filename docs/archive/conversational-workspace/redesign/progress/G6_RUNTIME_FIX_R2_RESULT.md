# Conversational Intake Runtime Hotfix R2 구현 결과

- 기준 HEAD: `d50385e8e9efbe4ec39858f62ebcbd883de43c83`
- 브랜치: `feature/conversational-validation-workspace`
- 구현 일자: 2026-08-05
- 범위: Backend Internal AI request와 AI `/internal/v1/ai/executions`의 `IDEA_CONVERSATION_TURN` 계약 정렬
- 제외: G7, G4/G5 변경, Prompt 목적 변경, Provider 변경, Frontend 변경, Migration, 인증 변경, 수동 브라우저/OpenAI 검증

## 1. 실제 400 원인

Backend는 `IDEA_CONVERSATION_TURN`의 `input`에 `conversationContract`, 순서화된 Message, 현재 Brief, attachment, source 규칙을 저장했다. AI endpoint는 Task Type과 무관하게 legacy 공통 `textContents` 배열을 먼저 요구했다. 따라서 실제 request는 `validate_text_contents()`에서 `FIELD_CONSTRAINT_VIOLATION`으로 400을 반환했다.

이전 Mock 기반 Backend 테스트는 HTTP response만 stub해 실제 AI Pydantic/endpoint validation을 통과하지 않았으므로 이 불일치를 잡지 못했다. 또한 단순히 `textContents`를 추가하더라도 endpoint는 chunk text만 `journey_provider`에 전달해 구조화 Conversation/Brief/provenance 입력을 잃는 두 번째 계약 결함이 있었다.

승인된 G3 계약은 전용 canonical Task Type `IDEA_CONVERSATION_TURN`과 순서화된 대화, versioned Assistant Envelope, attachment 추출문, 현재 Brief/provenance, supported fields/source rules를 요구한다. 따라서 Backend의 목적을 보존하면서 AI endpoint에 이 Task 전용 strict input model을 추가했다. 기존 Task의 `textContents` 계약은 변경하지 않았다.

## 2. 필드별 계약 비교

| 의미 | Backend JSON key/type | AI 정렬 후 key/type | 필수 | 기존 불일치와 수정 위치 |
|---|---|---|---|---|
| 실행 ID | `taskRunId: string` | `taskRunId: string` | 예 | 별도 `executionId`는 사용하지 않고 기존 TaskRun ID가 canonical execution ID다. |
| Project | `input.projectId: integer` | `IdeaConversationTurnInputV1.projectId: strict positive int` | 예 | Backend `IdeaIntakeAiService.buildInput`에 추가 |
| Owner | `input.ownerId: integer` | `ownerId: strict positive int` | 예 | Backend input에 추가; log/event에는 노출하지 않음 |
| Task Type | `taskType: IDEA_CONVERSATION_TURN` | endpoint allowlist/dispatch의 동일 literal | 예 | 기존 문자열은 일치했고 canonical type으로 문서화 |
| Task Version | `taskSchemaVersion: "1.0"` | `taskSchemaVersion: "1.0"` | 예 | 기존 일치 유지 |
| Contract/Input Version | `contractVersion: "1.0"`, `input.schemaVersion: "1.0"` | 동일 literal | 예 | input schemaVersion을 명시적으로 추가 |
| Locale | outer/input `locale: "ko-KR"` | 동일 literal | 예 | input에도 명시해 null/implicit default를 제거 |
| Input Hash | `canonicalInputHash: sha256` | 동일 canonical wrapper hash | 예 | shared fixture로 양 서비스 계산/echo 검증 |
| Input Snapshot | `input: object` | Task Type별 `IdeaConversationTurnInputV1` | 예 | AI의 무조건 `textContents` 검증을 Conversation 전용 validation으로 분기 |
| Conversation | `input.conversationId: integer` | strict positive int | 예 | Backend에 추가 |
| Source Message | `input.sourceMessageId: integer` | 마지막 USER Message의 `messageId`와 일치 | 예 | Backend에 추가, AI가 order/source 관계 검증 |
| Brief Version | `input.briefVersionId: integer|null` | required nullable positive int | 예 | Draft 없음은 명시적 null, 존재 시 ID |
| Current Brief | `input.currentBrief: object|null` | typed field map 또는 null | 예 | 기존 빈 `{}`를 null로 구분하고 `valueJson`을 JSON 문자열이 아닌 실제 JSON value로 정렬 |
| Messages | `input.messages: array` | strict ordered Message array | 예 | `messageId`, sequence, role/type, content, nullable versioned envelope를 구조화 |
| Attachments | `input.attachments: array` | strict `{attachmentId, contentHash, text}` | 예 | 빈 배열 의미 유지; EXTRACTED attachment만 포함 |
| Supported Fields | `input.supportedFields: 12 literals` | 동일 exact set | 예 | 중복/누락 거부 |
| Source Rules | `input.sourceRules` | exact AI source allowlist와 자동확정 금지 literal | 예 | AI가 USER_CONFIRMED/자동 default를 생성하지 못하는 상위 규칙 유지 |
| Legacy Source | `input.legacyIdeaSource: string|null` | required nullable string | 예 | 없음과 누락을 구분 |
| Correlation | `correlationId: string` 및 header | 동일 string | 예 | header/body 일치 검증 유지 |

## 3. Request·Response와 shared fixture

공유 정본 fixture:

- `contracts/internal-ai/idea-conversation-turn-v1.request.json`
- `contracts/internal-ai/idea-conversation-turn-v1.response.json`

Request fixture는 USER → versioned Assistant `QUESTION_SET` → USER follow-up 순서, `currentBrief=null`, `attachments=[]`, canonical hash를 포함한다. Backend 테스트가 실제 request DTO 직렬화 결과를 같은 fixture와 JSON tree로 비교한다. AI 테스트는 같은 파일을 outer Pydantic model과 `IdeaConversationTurnInputV1`에 통과시키고 FastAPI endpoint/dispatch를 검증한다.

Response fixture는 strict `OpportunityBriefDraftResult`와 Internal Execution success envelope다. AI와 Backend가 동일 fixture를 각각 strict model과 response parser에 통과시킨다. 성공 response는 기존 필드 `contractVersion`, `taskType`, `taskSchemaVersion`, run/attempt/correlation/hash echo, `resultSchemaVersion`, result/warnings/provenance/usage를 유지한다.

## 4. 수정한 서비스

- Backend `IdeaIntakeAiService`: 실제 Task input에 version/IDs/locale, ordered Message와 Assistant Envelope, nullable Brief, actual JSON field value, attachment hash를 구성한다.
- Backend Worker 성공 terminal은 generic `eventType=job.completed`와 사용자 단계별 `messageKey=job.idea.questions.completed|job.idea.brief.draft.completed`를 함께 보존한다.
- Backend `InternalAiExecutionClient`: 명시적 request envelope 직렬화, RFC3339 UTC deadline, shared fixture serializer test, bounded validation diagnostics parsing을 추가했다.
- AI `executions.py`: `IDEA_CONVERSATION_TURN` 전용 strict nested Pydantic input과 message/provenance/source rule 검증을 추가했다.
- AI endpoint: Conversation Task만 전용 model로 검증한 뒤 canonical JSON 전체를 `journey_provider`에 전달한다. 다른 Task는 기존 `textContents` 검증을 유지한다.
- AI/Main 오류 경계: 400에서 값 없이 `path`, `expectedType`, `category`만 최대 12개 반환·기록한다.

운영 Job Event에는 validation field path를 넣지 않는다. Backend warning 예시는 `taskType`, `REQUEST_SCHEMA_INVALID`, bounded field metadata만 포함하며 request body, inputSnapshot, 사용자 원문, Prompt, provider body, Authorization/API Key는 기록하지 않는다.

## 5. 오류 분류

- Request/Pydantic/hash 400 `INVALID_REQUEST`: permanent, `retryable=false`, 즉시 FAILED, attempt 1에서 종료.
- unsupported Task/schema/contract 및 인증/configuration: permanent.
- Provider timeout/일시 연결/허용된 dependency 5xx: 기존 bounded retry 유지.
- AI result strict schema 오류: permanent result failure.

## 6. 검증 결과

- Backend compile: targeted test의 `compileJava` 통과.
- Backend targeted: `InternalAiExecutionClientTests` 6 + `IdeaConversationInternalAiContractTests` 3 + `IdeaIntakeAiContractTests` 3 = 12 passed, failure/error/skip 0.
- AI Internal Execution targeted: 26 passed, failure 0. shared fixture endpoint, missing/extra/unknown Task, hash/error envelope, 기존 task regression을 포함한다.
- AI Conversation prompt targeted: 2 passed, 17 deselected. canonical Task dispatch와 AI source 자동확정 거부를 검증했다.
- 추가 shared-fixture strict test 최종 재검증: 8 passed, failure 0.
- PostgreSQL `PostgreSqlIdeaIntakeWorkerTests`: 6 passed, failure/error/skip 0. 최초 Turn과 versioned Assistant Envelope를 포함한 후속 Turn, Brief/provenance, NEEDS_INPUT/READY, TaskRun 성공, `job.completed`, Contract 400 비재시도, bounded retry와 project isolation을 검증했다.
- Backend 전체 suite: R2에서는 실행하지 않았다. TaskRun 공통 기반은 변경하지 않았고 Internal AI 경계와 Idea Intake targeted/PostgreSQL tests로 범위를 검증했다.
- AI 전체 suite: 실행하지 않았다. 공통 Provider와 Prompt 목적은 변경하지 않았고 endpoint/model/journey targeted tests를 실행했다.
- 전체 `postgresTest`: 실행하지 않았다. DB schema/Migration/Repository/Transaction 구조 변경이 없으며 관련 PostgreSQL class를 실행했다.
- Frontend tests/lint/build: R2에서 Frontend 파일을 변경하지 않아 실행하지 않았다.
- Migration: 없음.
- `git diff --check`: 통과. line-ending warning 외 whitespace error는 없다.

주요 실행 명령:

```powershell
.\gradlew.bat test --tests "*InternalAiExecutionClientTests" --tests "*IdeaConversationInternalAiContractTests" --tests "*IdeaIntakeAiContractTests" --no-daemon --console=plain
.\.venv\Scripts\python.exe -m pytest tests\test_internal_executions.py tests\test_idea_conversation_internal_contract.py tests\test_internal_task_type_alignment.py -q
.\.venv\Scripts\python.exe -m pytest tests\test_journey_provider.py -k "conversation_intake" -q
$env:DOCKER_API_VERSION='1.40'; $env:JAVA_TOOL_OPTIONS='-Dapi.version=1.40'; .\gradlew.bat postgresTest --tests "*PostgreSqlIdeaIntakeWorkerTests" --no-daemon --console=plain
git diff --check
```

초기 실패 처리: 신규 canonical journey test 1건이 Prompt wrapper 전체를 JSON으로 직접 파싱한 테스트 가정 때문에 실패했다. 실패 method를 단독 재현해 wrapper 뒤 JSON 부분만 검사하도록 수정했고 1 passed 후 전체 targeted를 다시 통과시켰다. 제품 코드 실패가 아니었다.

## 7. R2 변경 파일

- AI production: `ai/app/models/executions.py`, `ai/app/api/executions.py`, `ai/main.py`.
- Backend production: `IdeaIntakeAiService.java`, `IdeaIntakeDurableWorker.java`, `InternalAiExecutionClient.java`.
- Shared contract: `contracts/internal-ai/idea-conversation-turn-v1.request.json`, `idea-conversation-turn-v1.response.json`.
- Test: `ai/tests/test_idea_conversation_internal_contract.py`, `IdeaConversationInternalAiContractTests.java`, `PostgreSqlIdeaIntakeWorkerTests.java`.
- 문서: 이 R2 결과, current-to-target map, 기존 Docker 검증 절차.
- R1의 기존 미커밋 파일과 작업 시작 전 미추적 `.pytest-tmp/`는 삭제·복원하지 않았다.

## 8. 사용자 검증과 남은 위험

갱신한 절차: [CONVERSATIONAL_INTAKE_RUNTIME_FIX.md](../verification/CONVERSATIONAL_INTAKE_RUNTIME_FIX.md)

Codex는 Docker/OpenAI/브라우저 수동 검증 완료를 주장하지 않는다. 남은 위험은 실제 Provider가 strict result를 반복적으로 위반하는 경우의 운영 관찰, 큰 extracted attachment가 2 MiB request 상한에 근접하는 경우, 기존 과거 QUEUED Task가 이전 input snapshot 계약을 가진 경우다. 과거 실패 Task를 임의 변환하지 않으며 새 Message/새 TaskRun으로 재실행해야 한다.

G7은 구현하지 않았다. commit과 push도 수행하지 않았다.
