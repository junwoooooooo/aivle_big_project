# Provider Output Contract Stabilization 구현 결과

- 시작 HEAD: `7cb57e1fb3699254c496f866ac48079870e7ccce`
- 브랜치: `feature/conversational-validation-workspace`
- 구현 일자: 2026-08-06
- 범위: OpenAI strict response schema의 `valueJson: Any` 결함 제거, Provider DTO→Domain DTO 결정론 매핑, 안전한 schema-rejection 분류, 실제 Provider smoke
- 제외: G7, Boundary/Concept 변경, SSE/Worker/Retry 의미 변경, Frontend, Migration, 인증, Prompt 목적 변경

## 1. 확정 원인과 수정

R3는 Domain 정본인 `OpportunityBriefDraftResult.model_json_schema()`를 OpenAI strict `response_format`에 직접 전달했다. `OpportunityBriefFieldProposal.valueJson: Any`는 JSON Schema에서 `{"title":"Valuejson"}`처럼 `type/anyOf/$ref`가 없는 property가 되었고 OpenAI가 모델 실행 전에 `error.type=invalid_request_error`, `error.param=response_format`, HTTP 400으로 거부했다.

Domain 모델과 Backend 응답 계약은 변경하지 않았다. 대신 `ai/app/models/idea_conversation_provider.py`에 다음 Provider 전용 closed DTO를 추가했다.

- `ProviderOpportunityFieldProposal`
- `ProviderOpportunityBriefDraftResult`
- `valueKind=TEXT|TEXT_LIST|MISSING`
- `textValue: string|null`
- `listValue: string[]`
- `decisionStatus=PREFERRED|OPEN|ASSUMPTION`
- `sourceType=SOURCE_EXTRACTED|AI_PROPOSED|MISSING`
- `confidence: strict number 0.0..1.0|null`

TEXT/TEXT_LIST/MISSING의 value/source/confidence 상관관계를 model validator로 검증한다. Provider schema에는 `valueJson`과 `Any`가 없고 모든 object는 `additionalProperties=false`이며 모든 property가 required이고 `type`, `anyOf`, `$ref` 중 하나를 가진다.

## 2. 결정론 Mapper와 호출 계약

Provider 결과 검증 후 다음과 같이 기존 Domain DTO로 변환하고 `OpportunityBriefDraftResult`로 다시 검증한다.

- TEXT → `valueJson=textValue`
- TEXT_LIST → `valueJson=listValue`
- MISSING → `valueJson=null`

Initial과 단일 Repair는 모두 `ProviderOpportunityBriefDraftResult.model_json_schema()`를 사용한다. Prompt의 RESULT CONTRACT와 모델로 사전 검증한 valid example도 Provider DTO 형식이다. Repair 성공 후 Internal Execution/Backend 응답은 기존 `fieldKey/valueJson/decisionStatus/sourceType/confidence` 계약을 유지한다.

Schema lint는 전체 schema를 순회해 빈 `{}`, untyped property, 누락된 `additionalProperties=false`, required/property 불일치와 직렬화 가능성을 검증한다. 기존 Domain schema를 lint하면 `valueJson:untyped`가 재현되고 Provider schema는 issue 0건이다.

## 3. Provider 오류 Mapping

structured response schema가 있는 OpenAI 요청에서만 다음 exact upstream 오류를 별도 분류한다.

- upstream status: 400
- `error.type=invalid_request_error`
- `error.param=response_format`
- internal code: `RESULT_SCHEMA_INVALID`
- reason: `PROVIDER_RESPONSE_SCHEMA_REJECTED`
- HTTP: 502
- retryable: false

Backend `InternalAiExecutionClient`는 이 reason을 additive하게 허용한다. 로그는 task type, model, upstream status, 안전한 provider error type/param, allowlisted schema name만 남긴다. Provider body/message, API key, Authorization, Prompt, 사용자 입력은 기록하지 않는다. 다른 Provider 4xx의 기존 분류는 변경하지 않았다.

## 4. 실제 OpenAI Smoke

추가 도구: `python -m app.tools.idea_conversation_provider_smoke`

저장소 `.env`의 기존 설정을 현재 프로세스에만 주입하고 실제 OpenAI를 한 번 호출했다. DB 저장은 수행하지 않았다.

```text
provider=openai
model=gpt-4o-mini
responseFormat=json_schema
providerStatus=2xx
providerSchemaValidation=PASSED
domainMappingValidation=PASSED
```

Prompt, Provider 원문, synthetic input 전문, API key는 출력하지 않았다. 현재 실행 환경에는 Docker executable이 없어 `docker compose build/up`과 실제 Backend/브라우저 Conversation 1 Turn은 수행하지 않았다. 사용자 검증 절차는 별도 문서에 기록했다.

## 5. 변경 파일

- AI: `ai/app/models/idea_conversation_provider.py`, `ai/app/services/journey_provider.py`, `ai/app/tools/__init__.py`, `ai/app/tools/idea_conversation_provider_smoke.py`
- Prompt: `ai/prompts/idea_conversation_turn/system.md`
- Backend: `backend/src/main/java/com/aivle/backend/taskrun/integration/InternalAiExecutionClient.java`
- Test: `ai/tests/test_idea_conversation_result_repair.py`, `ai/tests/test_idea_conversation_internal_contract.py`, `ai/tests/test_journey_provider.py`, `backend/src/test/java/com/aivle/backend/taskrun/InternalAiExecutionClientTests.java`, `backend/src/test/java/com/aivle/backend/journey/conversation/PostgreSqlIdeaIntakeWorkerTests.java`
- 문서: 이 결과 문서, current-to-target map, Runtime verification 문서

Migration과 Frontend 변경은 없다. 시작 시 기존 미추적 `.pytest-tmp/`는 보존했다.

## 6. 자동 검증

- AI Provider/Conversation targeted: 44 passed, failure/error 0, FastAPI deprecation warning 10.
- AI 전체 suite: 135 passed, failure/error 0, FastAPI deprecation warning 10. 공통 provider error mapping을 변경했기 때문에 한 번 실행했다.
- Backend Internal AI/Idea Intake targeted 및 compile: 10 passed, failure/error/skip 0.
- PostgreSQL `PostgreSqlIdeaIntakeWorkerTests`: 최종 8 passed, failure/error/skip 0.
- `git diff --check`: 통과. line-ending 안내 외 whitespace error 없음.

PostgreSQL 최초 class 실행은 새 테스트가 terminal event의 기존 safe code 대신 reason을 기대해 8건 중 1건 실패했다. Worker의 기존 Event 계약은 `technicalCode=RESULT_SCHEMA_INVALID`가 정본이므로 제품 코드는 변경하지 않고 테스트 기대값만 바로잡았다. 실패 method 단독 통과 후 class 전체 8건을 한 번 재확인했다.

Sandbox 내부 최초 Gradle 실행은 pinned distribution 네트워크 접근이 차단되어 테스트 시작 전 실패했다. 승인된 Gradle/Docker 범위에서 동일 targeted 명령을 재실행해 성공했다.

Backend 전체 suite와 전체 `postgresTest`는 TaskRun/Worker/DB schema를 변경하지 않았으므로 실행하지 않았다. Frontend는 변경하지 않아 Frontend test/lint/build를 실행하지 않았다.

## 7. 남은 검증

실제 OpenAI schema smoke는 통과했다. 남은 항목은 Docker executable이 있는 사용자 환경에서 Backend와 AI 이미지를 rebuild한 뒤 실제 새 Project/Conversation 1 Turn을 수행하는 것이다. 성공 조건은 Internal Execution 2xx, USER/ASSISTANT Message, non-null Brief, `NEEDS_INPUT|READY_FOR_CONFIRMATION`, `activeJobId=null`, TaskRun SUCCEEDED, terminal `job.completed`이다.

G7은 구현하지 않았고 commit/push도 수행하지 않았다.
