# Conversational Intake Runtime Hotfix R3 구현 결과

- 시작 HEAD: `d50385e8e9efbe4ec39858f62ebcbd883de43c83`
- 브랜치: `feature/conversational-validation-workspace`
- 구현 일자: 2026-08-05
- 범위: `IDEA_CONVERSATION_TURN` Provider 결과 strict schema 적용과 schema-invalid 단일 Repair
- 제외: G7, Regulatory Boundary, Concept Core, UI 재설계, 인증, Migration, 수동 Docker/OpenAI/브라우저 검증

## 1. 실제 실패 원인

`OpportunityBriefDraftResult`로 Provider 응답을 최종 검증하고 있었지만 실제 Chat Completions 요청은 `response_format={"type":"json_object"}`만 사용했다. Prompt에도 machine enum과 JSON type이 충분히 명시되지 않아 Provider가 비표준 enum, 숫자 문자열, 정수 question ID, 비표준 question type, null options를 반환할 수 있었다.

| Field path | 허용 계약 | 확인된 잘못된 결과 |
|---|---|---|
| `extractedFields[*].decisionStatus` | `PREFERRED`, `OPEN`, `ASSUMPTION` | 한국어 또는 비표준 상태 문자열 |
| `extractedFields[*].confidence` | strict JSON number `0.0..1.0` 또는 허용된 `null` | 숫자 문자열/퍼센트/자연어 |
| `clarificationQuestions[*].id` | 길이 1~80의 string | integer |
| `clarificationQuestions[*].type` | `FREE_TEXT`, `SINGLE_SELECT`, `MULTI_SELECT`, `UNDECIDED` | 비표준 alias |
| `clarificationQuestions[*].options` | 항상 string array | `null` 또는 단일 string |

추가 감사에서 AI Pydantic 모델은 `LOCKED`를 허용하고 `MISSING`을 거부했지만 Backend 채택 계약과 승인된 provenance 불변식은 AI의 `LOCKED` 자동 생성을 거부하고 `MISSING`을 허용하고 있었다. Pydantic 결과 모델을 Backend 계약에 맞춰 `decisionStatus=PREFERRED|OPEN|ASSUMPTION`, `sourceType=SOURCE_EXTRACTED|AI_PROPOSED|MISSING`으로 정렬했다. `MISSING`은 `valueJson=null`, `confidence=null`일 때만 유효하다.

## 2. Provider strict schema 적용

`OpportunityBriefDraftResult.model_json_schema()`가 단일 결과 정본이다. Conversation initial과 repair Provider 요청 모두 strict `json_schema` response format으로 같은 schema를 전달한다. 다른 Journey Task는 기존 `json_object` 요청을 유지한다.

Conversation Prompt에는 모델에서 검증된 유효 예제 한 개, exact enum, number/string/array 규칙, Markdown/code fence 및 extra field 금지를 추가했다. Provider 결과는 response format 적용 후에도 동일 Pydantic 모델로 다시 검증한다. 단일 JSON code fence는 기존 bounded parser가 내부 JSON 객체 하나만 추출하지만 Prompt와 structured-output 계약은 fence를 금지한다.

## 3. 단일 Schema Repair

1. Initial Provider 결과를 strict Pydantic 모델로 검증한다.
2. 실패 시 input value 없이 최대 20개의 `path`와 validation `type`만 추출한다.
3. `attemptPhase=REPAIR`, 동일 result schema, bounded issue metadata, initial candidate를 Provider에 한 번 전달한다. 이 payload는 저장하거나 로그에 남기지 않는다.
4. Repair Prompt는 기존 유효 의미 보존, type/literal/structure만 정규화, 새 사업 정보 생성 금지, JSON-only를 요구한다.
5. Repair 결과를 동일 Pydantic 모델로 검증한다.
6. 성공하면 기존 Assistant Message/Brief/provenance completion 경로를 사용한다.
7. 재실패하면 세 번째 Provider 호출 없이 `RESULT_SCHEMA_INVALID`, `retryable=false`로 종료한다.

Repair callback은 Internal Execution success envelope에 `code=RESULT_SCHEMA_REPAIRED`, `attemptPhase=REPAIR`, `issueCount`만 기록한다. Backend는 이 exact warning만 bounded하게 수용하여 `job.idea.result.repairing`을 영속 발행한다. Event params는 `attemptPhase`와 `issueCount(1..20)`만 포함한다. invalid value, Provider body, Prompt, 사용자 입력은 포함하지 않는다. 성공 terminal event는 기존 domain completion commit 이후 `job.completed`로 발행된다.

## 4. 변경 파일

- AI production: `ai/app/models/journey.py`, `ai/app/services/journey_provider.py`, `ai/app/api/executions.py`
- Prompt: `ai/prompts/idea_conversation_turn/system.md`
- Backend production: `backend/src/main/java/com/aivle/backend/journey/conversation/IdeaIntakeAiService.java`
- AI test: `ai/tests/test_idea_conversation_result_repair.py`, `ai/tests/test_idea_conversation_internal_contract.py`, `ai/tests/test_journey_provider.py`
- PostgreSQL test: `backend/src/test/java/com/aivle/backend/journey/conversation/PostgreSqlIdeaIntakeWorkerTests.java`
- Frontend: `frontEnd/src/shared/async-events/jobEventMessages.js`, `frontEnd/src/shared/async-events/JobTimeline.test.jsx`
- 문서: 이 결과 문서, current-to-target map, `CONVERSATIONAL_INTAKE_RUNTIME_FIX.md`

기존 R1/R2 미커밋 변경과 `.pytest-tmp/`는 삭제하거나 복원하지 않았다.

## 5. 검증 결과

- AI R3 + Conversation targeted: 38 passed, failure/error 0. FastAPI deprecation warning 10건.
- Backend Idea Intake/Internal AI targeted: 12 passed, failure/error/skip 0. `compileJava` 포함 성공.
- PostgreSQL `PostgreSqlIdeaIntakeWorkerTests`: 8 passed, failure/error/skip 0. Repair success에서 Assistant Message 1개, Brief 1개, safe repair event, terminal event를 확인했고 `RESULT_SCHEMA_INVALID`가 attempt 1에서 permanent FAILED가 되는 것을 확인했다.
- AI 전체 suite: 129 passed, failure/error 0. 공통 `execute_structured_prompt`에 optional strict-schema 인자를 추가했기 때문에 단계 종료 시 한 번 실행했다. FastAPI deprecation warning 10건.
- Frontend Timeline targeted: 4 passed, failure 0. Repair event를 `응답 형식을 정리하고 있습니다.`로 표시하고 technicalCode/issue metadata를 노출하지 않음을 확인했다.
- Frontend lint: 성공.
- Frontend production build: 성공. 기존 500 kB 초과 chunk warning만 존재한다.
- Frontend baseline: 299 passed, 18 explicitly allowed failures, 0 unexpected failures. 공통 Timeline message mapper를 변경했기 때문에 한 번 실행했다.
- `git diff --check`: 통과. 기존 line-ending 변환 warning만 있고 whitespace error는 없다.

주요 실행 명령:

```powershell
cd ai
.\.venv\Scripts\python.exe -m pytest tests\test_idea_conversation_result_repair.py tests\test_idea_conversation_internal_contract.py tests\test_journey_provider.py -q
.\.venv\Scripts\python.exe -m pytest -q

cd ..\backend
.\gradlew.bat test --tests "*IdeaIntake*" --tests "*InternalAi*" --no-daemon --console=plain
$env:DOCKER_API_VERSION='1.40'
$env:JAVA_TOOL_OPTIONS='-Dapi.version=1.40'
.\gradlew.bat postgresTest --tests "*PostgreSqlIdeaIntakeWorkerTests*" --no-daemon --console=plain
```

최초 Backend 및 PostgreSQL 실행은 sandbox에서 Gradle distribution 네트워크 접근이 차단되어 제품 테스트 실행 전 실패했다. 코드 수정 없이 동일 명령을 승인된 Gradle/Docker 범위에서 재실행해 각각 성공했다.

최초 병렬 Frontend lint/build 호출은 제품 오류 없이 도구 제한시간에 도달해 결과가 불명확했다. lint와 build를 각각 단독 재실행하여 모두 성공했다. TaskRun 공통 기반을 변경하지 않아 Backend 전체 suite와 전체 `postgresTest`를 실행하지 않았다. Migration/DB schema 변경은 없다.

## 6. 사용자 검증과 남은 위험

사용자 Docker/OpenAI 절차는 [CONVERSATIONAL_INTAKE_RUNTIME_FIX.md](../verification/CONVERSATIONAL_INTAKE_RUNTIME_FIX.md)에 R3 항목으로 갱신했다. Codex는 수동 Docker/OpenAI/브라우저 검증 완료를 주장하지 않는다.

남은 위험:

- 운영에 설정된 OpenAI-compatible endpoint가 Chat Completions `json_schema` response format을 완전히 지원하는지는 사용자 Docker/OpenAI 검증이 필요하다. 미지원 4xx는 permanent provider execution failure로 드러나며 silent fallback으로 schema를 완화하지 않는다.
- `valueJson`은 기존 계약상 임의 JSON value이므로 생성된 Pydantic JSON Schema에서도 해당 property의 값 형태가 열려 있다. Field provenance와 source/status/null 상관관계는 Pydantic model validator와 Backend validator가 최종 차단한다.
- Repair는 구조 정규화 한 번만 허용한다. 두 번 모두 잘못된 실제 Provider 결과는 의도대로 permanent FAILED가 되며 사용자 재실행이 필요하다.

G7은 구현하지 않았고 commit/push도 수행하지 않았다.
