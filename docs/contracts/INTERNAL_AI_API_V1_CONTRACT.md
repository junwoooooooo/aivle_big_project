# Internal Spring–AI API v1 Contract

- Status: CURRENT_CANONICAL
- Code Baseline Commit: e16bd316ac881f4c5fab076e65c14657f6a8c7d4
- Document Phase: P2
- Introduced In Commit: ca22117fd9da65f1b232b9aa34e9d6e085e7ee06
- Scope: Provider-neutral synchronous TaskAttempt execution contract between Spring WAS and AI Server
- Supersedes: Legacy direct-provider, artifact-service and presigned transfer contracts
- Implementation Status: IMPLEMENTED_FOR_13_TASK_REGISTRY

현재 구현은 provider-neutral Spring client, FastAPI `/internal/v1/ai/executions` envelope, service-token authentication, raw 2 MiB request/response guard, 13개 TaskType dispatcher와 현재 Journey의 Idea·Legal·Concept handler를 포함한다. 보존된 MVP task도 registry에 남아 있으며 task별 provider/domain 결과 검증 수준은 각 production handler가 정의한다.

이 문서는 Internal Spring–AI v1의 현재 canonical 계약이다. Public API Controller/path/status/envelope의 권위는 아니며 provider 또는 model 선택을 고정하지 않는다.

## 1. Boundary and execution model

- Base path는 `/internal/v1/ai`이고 유일한 실행 endpoint는 `POST /internal/v1/ai/executions`다.
- 하나의 HTTP request는 Spring Worker가 claim한 하나의 `TaskAttempt` 실행이다. AI Server는 동기적으로 처리해 success 또는 error JSON을 반환한다.
- AI Server는 stateless execution service다. 내부 업무 queue, 업무 RDB, callback, webhook, durable idempotency store를 요구하지 않는다.
- Spring은 외부 호출 동안 DB transaction을 유지하지 않는다. 연결 단절이나 응답 유실 때문에 실제 실행은 at-least-once일 수 있지만, 검증된 결과의 채택은 Spring이 정확히 한 번 수행한다.
- retry는 같은 TaskRun에 새 TaskAttempt와 새 execution identifier를 만든다. 사용자 rerun은 새 Domain Run과 새 TaskRun을 만든다.
- TaskRun/TaskAttempt, retry, timeout, idempotency, 최종 상태는 Spring이 source of truth다. HTTP 200만으로 TaskRun 성공이 되지 않는다.

## 2. Authentication and headers

| Header | Requirement | Rule |
|---|---|---|
| `Authorization` | required | `Bearer <internal-service-token>`; 사용자 JWT가 아닌 service credential |
| `Content-Type` | required | `application/json` |
| `X-Correlation-Id` | required | body `correlationId`와 같아야 하는 bounded opaque value |

Service token은 환경변수 또는 deployment Secret으로만 공급하며 body, application log, error 또는 fixture에 기록하지 않는다. TLS가 적용된 내부 network에서만 호출한다. 향후 mTLS 추가는 JSON body를 바꾸지 않는다. v1에는 별도 version header를 정의하지 않으며 body `contractVersion`이 유일한 version discriminator다. 사용자 JWT, refresh token, session ID, 사용자 credential은 전달하지 않는다.

## 3. Common execution request

```json
{
  "contractVersion": "1.0",
  "taskType": "IDEA_INTERPRETATION",
  "taskSchemaVersion": "1.0",
  "taskRunId": "opaque-execution-reference",
  "taskAttemptId": "opaque-attempt-reference",
  "correlationId": "opaque-correlation-id",
  "deadlineAt": "2026-08-02T00:30:00Z",
  "canonicalInputHash": "sha256:hex-digest",
  "locale": "ko-KR",
  "input": {}
}
```

모든 field는 required/non-null이다. Unknown top-level field는 `INVALID_REQUEST`다. `taskRunId`와 `taskAttemptId`는 echo/correlation용 opaque reference이며 AI Server가 Spring RDB를 조회하는 key가 아니다. owner/user/Project DB identifier를 lookup 목적으로 전달하지 않는다. 업무 관계는 request-local key로 표현한다. `deadlineAt`은 RFC 3339 UTC이며 만료된 요청은 실행하지 않는다. v1 locale은 `ko-KR`이고 다른 값은 해당 task schema가 명시적으로 확장하기 전까지 거부한다. `input`은 `taskType` discriminator와 `taskSchemaVersion`에 맞는 schema여야 한다.

### Canonical input hash

- 알고리즘은 SHA-256, encoding은 UTF-8 canonical JSON이다.
- hash 대상은 `contractVersion`, `taskType`, `taskSchemaVersion`, `locale`, `input`이다.
- 실행마다 변하는 `taskRunId`, `taskAttemptId`, `correlationId`, `deadlineAt`은 제외한다.
- Object key나 credential을 hash input에 넣지 않는다.
- Spring이 계산하고 AI Server가 검증·echo한다. 정확한 canonicalization fixture는 P2.6에서 고정한다.

## 4. ContractLimitProfile V1 and text chunks

| Limit | V1 value |
|---|---:|
| Request JSON hard maximum | 2 MiB |
| Response JSON hard maximum | 2 MiB |
| `TextContent` objects per execution | 64 |
| Total `TextChunk` objects across execution | 64 |
| `TextChunk` objects per `TextContent` | 1–64 |
| Text per chunk | 16,384 characters |
| Total extracted text per execution | 500,000 characters |

Provider effective limit가 더 작으면 Spring Service Policy가 command 수락 전에 capability 또는 payload를 차단한다. Contract 상한을 넘으면 `PAYLOAD_TOO_LARGE`다.

`TextContent` required fields는 `contentKey`, `contentType`=`TEXT`, `language`, `totalCharacters`, `contentHash`, `chunks`다. `TextChunk` required fields는 `index`, `text`, `characterCount`, `chunkHash`다. Character는 Unicode scalar value(code point) 단위로 계산한다. Index는 TextContent별 0부터 연속이어야 하고 누락·중복·순서 변경을 허용하지 않는다. `totalCharacters`는 모든 chunk `characterCount`의 합이고 `contentHash`는 chunk text를 index 순서대로 separator 없이 결합한 UTF-8 bytes의 hash다. Execution 전체의 TextContent는 최대 64개, 모든 TextContent에 걸친 TextChunk 합계도 최대 64개이므로 4,096 chunk로 해석할 수 없다. Empty chunk, duplicate contentKey/index와 선언값 불일치는 `INVALID_REQUEST`, 크기 초과는 `PAYLOAD_TOO_LARGE`다. HTML, binary, base64 FILE payload, FILE bytes는 허용하지 않는다.

## 5. Common success response

```json
{
  "contractVersion": "1.0",
  "taskType": "IDEA_INTERPRETATION",
  "taskSchemaVersion": "1.0",
  "taskRunId": "opaque-execution-reference",
  "taskAttemptId": "opaque-attempt-reference",
  "correlationId": "opaque-correlation-id",
  "canonicalInputHash": "sha256:hex-digest",
  "resultSchemaVersion": "1.0",
  "result": {},
  "warnings": [],
  "provenance": [],
  "usage": null
}
```

정상 구조화 결과는 HTTP 200이다. Request의 version/type/execution/correlation/hash를 정확히 echo한다. `warnings`와 `provenance`는 required arrays이며 `usage`는 required nullable provider-neutral summary다. AI Server가 output schema를 검증한 후 반환하고 Spring이 size, echo identity/hash, schema, provenance와 domain invariant를 독립적으로 다시 검증한다. Spring이 TaskResult를 `ADOPTED`한 뒤에만 업무 성공이다. Provider/model/SDK 이름, prompt, chain-of-thought, raw provider response는 결과에 포함하지 않는다.

## 6. Internal error envelope and public mapping

```json
{
  "error": {
    "code": "DEPENDENCY_UNAVAILABLE",
    "message": "A required dependency is temporarily unavailable.",
    "correlationId": "opaque-correlation-id",
    "taskRunId": "opaque-execution-reference",
    "taskAttemptId": "opaque-attempt-reference",
    "retryable": true,
    "details": []
  }
}
```

Error는 안전한 요약만 제공한다. Raw dependency/provider body, secret, prompt, stack trace 또는 storage identity는 금지한다.

| Internal code | HTTP | Retryable | TaskAttempt direction | Public Task error | Safe detail direction |
|---|---:|---|---|---|---|
| `INVALID_REQUEST` | 400 | no | FAILED | `AI_RESULT_INVALID` | invalid field/reason; raw value 제외 |
| `UNAUTHORIZED_INTERNAL_CALL` | 401/403 | no | FAILED | `AI_SERVICE_UNAVAILABLE` | authentication/authorization category only |
| `UNSUPPORTED_CONTRACT_VERSION` | 422 | no | FAILED | `AI_RESULT_INVALID` | supported major versions |
| `UNSUPPORTED_TASK_TYPE` | 422 | no | FAILED | `AI_RESULT_INVALID` | rejected discriminator |
| `UNSUPPORTED_TASK_SCHEMA_VERSION` | 422 | no | FAILED | `AI_RESULT_INVALID` | task type와 supported versions |
| `PAYLOAD_TOO_LARGE` | 413 | no | FAILED | `PAYLOAD_TOO_LARGE` | violated limit name |
| `DEADLINE_EXCEEDED` | 504 | yes | TIMED_OUT | `TASK_TIMEOUT` | 현재 Attempt는 terminal; 새 Attempt 가능 여부는 Spring policy가 결정 |
| `DEPENDENCY_UNAVAILABLE` | 503 | yes | FAILED | `AI_SERVICE_UNAVAILABLE` | dependency class only |
| `RATE_LIMITED` | 429 | yes | FAILED | `AI_SERVICE_UNAVAILABLE` | safe retry-after direction |
| `EXECUTION_FAILED` | 500 | reason별 고정 | FAILED | `AI_SERVICE_UNAVAILABLE` | `TRANSIENT_EXECUTION_FAILURE`=true; `PERMANENT_EXECUTION_FAILURE`/`SAFETY_POLICY_BLOCKED`=false |
| `RESULT_SCHEMA_INVALID` | 502 | no | FAILED | `AI_RESULT_INVALID` | schema/reason identifier |
| `INTERNAL_ERROR` | 500 | yes | FAILED | `AI_SERVICE_UNAVAILABLE` | generic internal category |

Internal request bug의 raw detail은 public에 숨긴다. 이미 public command가 202로 TaskRun을 만든 뒤 발생한 오류는 TaskRun의 terminal state/errorSummary에 기록되고, TaskRun GET은 200이다. Spring이 TaskRun을 만들기 전에 dependency unavailable로 command를 수락할 수 없는 경우에만 public 503이며 `taskRunId`는 null이다.

## 7. Task registry

모든 task/result schema version은 v1에서 `1.0`이다.

| Task type | Public command / Domain Run | Input schema | Result schema | Local keys | Bounds | External dependency / degraded | Forbidden output | Spring adoption rule |
|---|---|---|---|---|---|---|---|---|
| `IDEA_INTERPRETATION` | interpretation command / IdeaInterpretationRun | `IdeaInterpretationInputV1` | `IdeaInterpretationResultV1` | source, statement | text limits; options allowlist | model / no | auto IdeaVersion or user decision | facts/assumptions separation and adopted exact input |
| `LEGAL_REVIEW` | legal command / LegalReviewRun | `LegalReviewInputV1` | `LegalReviewResultV1` | idea item | findings/sources bounded | MOLEG_API, LEGAL_MCP / yes | legal advice claim | source identity/currentness and legal enum valid |
| `IDEA_LEGAL_PRECHECK` | Journey legal precheck / TaskRun | `LegalSourcePipelineInputV1` | `LegalSourcePipelineResultV1` | content, route, evidence | text/source limits | model, MOLEG_API / partial source allowed | invented statute or citation | exact task identity, registry and evidence references |
| `CONCEPT_LEGAL_VALIDATION` | Concept eligibility legal gate / TaskRun | `ConceptLegalValidationBatchInputV1` | `ConceptLegalValidationBatchResultV1` | content, candidate | candidate batch bounded by execution payload | model / no | unknown, duplicate or omitted candidate | exact candidateKey set and strict result fields |
| `CONCEPT_GENERATION` | generation command / ConceptGenerationRun | `ConceptGenerationInputV1` | `ConceptGenerationResultV1` | idea item, concept | candidateCount 1–10 | model / no | user selection | requested bounds and passing legal input |
| `QUICK_ASSESSMENT` | quick command / QuickAssessmentRun | `QuickAssessmentInputV1` | `QuickAssessmentResultV1` | concept, evidence | one concept; dimensions bounded | model / no | shortlist decision | exact concept and proposal disclosure |
| `DETAILED_ANALYSIS` | detailed command / DetailedAnalysisRun | `DetailedAnalysisInputV1` | `DetailedAnalysisResultV1` | concept, evidence | one type; arrays bounded | model; task-specific sources / optional warnings | deterministic overwrite | shortlist/type/schema and calculation boundary |
| `PERSONA_CARD_GENERATION` | persona card command / PersonaCardGenerationRun | `PersonaCardGenerationInputV1` | `PersonaCardGenerationResultV1` | concept, persona | personaCount 1–10 | model / no | real-customer/statistical claims | three layers and synthetic disclosure |
| `PERSONA_INTERVIEW` | interview command / PersonaInterview | `PersonaInterviewInputV1` | `PersonaInterviewResultV1` | persona, question | exactly one card; bounded questions | model / no | other persona context | isolation and synthetic disclosure |
| `INTERVIEW_SYNTHESIS` | synthesis command / InterviewSynthesis | `InterviewSynthesisInputV1` | `InterviewSynthesisResultV1` | interview | at least 2 adopted interviews | model / no | source interview mutation | included/excluded set and source preservation |
| `MARKETING_GENERATION` | generation command / MarketingGenerationRun | `MarketingGenerationInputV1` | `MarketingGenerationResultV1` | asset, persona | text/structured result only | model / no | binary, probability | exact workspace/evidence and safe type |
| `MARKETING_COMPARISON` | comparison command / MarketingComparisonRun | `MarketingComparisonInputV1` | `MarketingComparisonResultV1` | asset, persona | at least 2 asset versions | model / no | statistical A/B/probability | relative dimensions and caveats |
| `FINAL_REPORT_GENERATION` | report command / FinalReportGenerationRun | `FinalReportGenerationInputV1` | `FinalReportGenerationResultV1` | upstream statement | bounded immutable snapshots | model / no | decision change, PDF/binary | exact references and user decision preserved |

`PDF_EXPORT`는 AI task가 아니다. Spring이 생성·검증·저장한다.

### Task-specific collection limits

아래는 v1 hard maximum이며 Public command가 더 작은 상한을 정하면 더 작은 값이 적용된다. Nested text는 공통 2 MiB response limit도 만족해야 한다.

| Task | Collection maximums |
|---|---|
| `IDEA_INTERPRETATION` | TextContent 64, statement items per category 200, warnings/evidence needs 100 |
| `LEGAL_REVIEW` | findings 100, source references 200, conditions/warnings/expert reasons 100 |
| `IDEA_LEGAL_PRECHECK` | TextContent 64, routes/evidence/findings 200, required inputs/warnings 100 |
| `CONCEPT_LEGAL_VALIDATION` | TextContent 64; candidate set is the exact set encoded by the canonical batch content |
| `CONCEPT_GENERATION` | concepts 10, list fields per concept 50 |
| `QUICK_ASSESSMENT` | dimensions 20, evidence/assumptions/uncertainties/warnings 100 each |
| `DETAILED_ANALYSIS` | findings 200, evidence/assumptions/uncertainties/warnings 200 each |
| `PERSONA_CARD_GENERATION` | cards 10, items per three-layer section 50 |
| `PERSONA_INTERVIEW` | questions 50, answers/interpretations/evidence needs/warnings 100 each |
| `INTERVIEW_SYNTHESIS` | input interviews 100, each result collection 200 |
| `MARKETING_GENERATION` | target personas 10, proposal sections/warnings 100 |
| `MARKETING_COMPARISON` | asset versions 20, personas 10, dimensions 30, assessment items 200 |
| `FINAL_REPORT_GENERATION` | upstream references 500, report sections 50, items per section 200 |

## 8. Task-specific contracts

### IDEA_INTERPRETATION

`IdeaInterpretationInputV1`은 하나 이상의 verified `TextContent`, source-safe label, source statement keys, readiness/normalization options, locale와 limit profile을 가진다. 현재 Production `IdeaInterpretationResultV1`은 `originalSourceSummary`, `normalizedDescription`, 문자열 배열인 `facts`, `assumptions`, `constraints`, `openQuestions`, `readiness`, `warnings`, `evidenceNeeds`와 Idea Origin용 `originDraft`, `fieldMetadata`, `clarificationQuestions`를 가진다. 불확실성을 fact로 승격하거나 사용자 constraint를 삭제하거나 IdeaVersion/User Decision을 자동 생성하지 않는다. Provenance는 execution response envelope에 유지하며 현재 result body에는 중복 저장하지 않는다.

### LEGAL_REVIEW

`LegalReviewInputV1`은 exact confirmed IdeaVersion snapshot, normalized description, facts/assumptions/constraints, `jurisdiction=KR`, bounded options와 idea item keys를 가진다. AI Server는 `MOLEG_API`를 법령 identifier·원문·조문·현재성의 authoritative source로, `LEGAL_MCP`를 검색·탐색·연관 법령 발견에 사용한다. Secret은 AI Server 환경변수/deployment Secret이며 Spring payload에 없다.

`LegalReviewResultV1`은 legalResult, findings, sourceReferences, sourceCoverage, conditions, warnings, expertReviewReasons, provenance를 가진다. Legal result는 `PASS`, `PASS_WITH_CONDITIONS`, `REVISION_REQUIRED`, `PROHIBITED`, `INSUFFICIENT_INFORMATION`, `EXPERT_REVIEW_REQUIRED` 중 하나다. Source reference는 sourceChannel, lawIdentifier, lawName, article, observedAt, currentness, authoritative, degraded와 optional officialSourceUrl을 가진다. 한 source 실패는 missing channel/degraded를 명시한 success가 될 수 있다. 법률 자문이나 확정적 전문가 판단으로 표현하지 않는다.

### IDEA_LEGAL_PRECHECK and CONCEPT_LEGAL_VALIDATION

`IDEA_LEGAL_PRECHECK`는 `LegalSourcePipelineInputV1`과 `LegalSourcePipelineResultV1`을 사용한다. `CONCEPT_LEGAL_VALIDATION`의 현재 공식 Journey 경로는 `validationMode=GUARDRAIL_BATCH`인 `ConceptLegalValidationBatchInputV1`과 `ConceptLegalValidationBatchResultV1`을 사용한다. Batch 응답은 입력의 candidateKey를 정확히 한 번씩 반환해야 하며 누락, 중복, 알 수 없는 key와 extra field를 거부한다. 과거 단건 `GUARDRAIL` 및 source-pipeline 호환 분기는 유지되지만 현재 Concept eligibility producer의 canonical 경로는 Batch다.

### CONCEPT_GENERATION and QUICK_ASSESSMENT

`ConceptGenerationInputV1`은 exact IdeaVersion, passing LegalReview result/conditions, candidateCount 1–10과 bounded options다. Result proposal은 local concept key, title, targetProblem, targetUserContext, valueProposition, solutionOutline, differentiators, constraints, assumptions, evidenceNeeds, provenance를 가진다. 사용자 Selection을 생성하지 않는다.

`QuickAssessmentInputV1`은 exact ConceptVersion 하나, shared core snapshot과 quick options다. Result는 dimension assessments, evidence, assumptions, uncertainties, warnings, evidenceNeeds, provenance를 구분한다. Shortlist나 사용자 Decision을 생성하지 않는다.

### DETAILED_ANALYSIS

`analysisType`은 `MARKET`, `BUSINESS_MODEL`, `TECHNICAL_OPERATION`, `FINANCIAL`이다. 각 type은 별도 discriminated input/result section을 가진다. 공통 input은 exact shortlisted ConceptVersion과 shared snapshot이며 공통 result는 findings, assumptions, uncertainties, warnings, evidenceNeeds, provenance다.

FINANCIAL input의 `deterministicInputs`, `calculationRuleVersion`, `deterministicResults`, assumptions, evidenceNeeds는 Spring이 제공한다. AI Server는 `aiExplanation`, drivers, risks, caveats만 생성하며 결정론적 section을 수정하거나 source of truth로 덮어쓰지 않는다. Result에서도 deterministic section과 AI explanation을 분리한다.

### PERSONA_CARD_GENERATION, PERSONA_INTERVIEW, INTERVIEW_SYNTHESIS

Card generation input은 exact PersonaStudy, ConceptSelection, selected ConceptVersion, personaCount 1–10, bounded options다. Result는 하나 이상의 local persona key와 initial version의 roleAndContext, problemAndNeeds, behaviorAndDecision, mandatory syntheticDisclosure, provenance를 가진다. Demographic-only persona, 실제 고객 조사 claim, 구매확률, 시장점유율, 대표 모집단 통계는 금지한다.

Interview input은 PersonaCardVersion 정확히 하나, question set, selected concept context, bounded options만 가진다. 다른 Persona card/interview/answer/hidden context를 포함하지 않는다. Result는 questions, synthetic answers, interpretations, evidenceNeeds, warnings, syntheticDisclosure, provenance를 가진다. 실제 고객·전문가 인터뷰라고 표현하지 않는다.

Synthesis input은 같은 PersonaStudy의 adopted Interview result 둘 이상, exact included/excluded keys, options다. Result는 commonResponses, conflictingResponses, unresolvedQuestions, researchRecommendations, caveats, provenance를 가진다. 개별 Interview 원본을 수정하거나 덮어쓰지 않는다.

### MARKETING_GENERATION and MARKETING_COMPARISON

Generation input은 exact MarketingWorkspaceVersion, selected ConceptVersion, Persona/Interview/Synthesis evidence, assetType, generationInput이다. Result는 text 또는 structured asset proposal, target Persona keys, message rationale, warnings, provenance다. Binary image/audio/video, base64 artifact, AI local path/Storage reference, conversion probability는 금지한다.

Comparison input은 exact MarketingAssetVersion 둘 이상, Persona evidence, comparison dimensions다. Result는 dimension별 relative assessment, Persona별 strengths/risks, caveats, evidenceNeeds, provenance다. 통계적 A/B experiment claim, winner probability, conversion/market-share prediction을 생성하지 않는다.

### FINAL_REPORT_GENERATION

Input은 exact immutable upstream snapshots, facts, legalSources, AI proposals, assumptions, researchNeeds, user decisions, reportDecision, userRationale다. `reportDecision`은 `GO`, `CONDITIONAL_GO`, `REWORK`, `HOLD`, `STOP` 중 사용자가 제공한 값이다. Result는 structured sections, executiveSummary, supportingFindings, risks, unresolvedResearch, caveats, provenance다. AI는 user decision을 바꾸지 않는다. 결과는 FinalReportVersion proposal이며 Spring이 검증·snapshot 저장한다. PDF는 Spring 책임이고 Markdown/binary output은 없다.

## 9. Request-local references

`source-1`, `fact-1`, `concept-1`, `persona-1`, `interview-1`, `asset-1` 같은 key를 사용한다. Key는 request 안에서 유일한 의미 없는 bounded string이다. AI Server는 request에 존재하는 key만 result에서 참조하며 unknown key를 생성·echo하지 않는다. Spring이 결과 key를 실제 Domain reference로 매핑한다. 이 계약은 DB table/entity identifier 형식을 정의하지 않는다. 외부 법령 identifier는 authoritative external identifier이므로 local key와 별도다.

## 10. Internal provenance

각 provenance item은 `category`, `statementKey`, `sourceKeys`, `externalSourceReferences`, `generatedAt`, optional `confidence`, optional `uncertainty`, `verificationNeeded`, optional `caveat`를 가진다. Category는 `USER_INPUT`, `EXTERNAL_SOURCE_FACT`, `ASSUMPTION`, `AI_PROPOSAL`, `USER_DECISION`이다. AI Server는 `USER_DECISION`을 새로 생성하지 않고 request에 이미 존재하는 결정을 echo/reference할 수만 있다. Unknown local key와 source가 없는 external fact는 Spring adoption validation에서 거부한다.

## 11. Timeout, cancellation, retry and adoption

- `deadlineAt` 이후 새 provider/MCP call을 시작하지 않고 `DEADLINE_EXCEEDED`를 반환한다.
- Spring 연결 취소는 best-effort cancel 신호이며 AI-side persistent job이나 callback을 만들지 않는다.
- 같은 TaskAttempt response는 한 번만 채택한다. Late, duplicate, stale-lease response는 adopted result를 덮어쓰지 않고 Spring이 non-adopted evidence로 처리할 수 있다.
- Retry는 새 TaskAttempt이고 Domain rerun은 새 Domain Run/TaskRun이다.
- AI Server retry 권고는 참고 정보이며 Spring retry policy가 최종 결정한다.

## 12. Logging, security and privacy

기본 log 허용값은 correlationId, taskType, taskSchemaVersion, duration, HTTP/status, canonical input hash prefix, safe error code다. 사용자 전체 text, prompt, raw model response, JWT, service token, credential, legal API secret, FILE content, 개인정보, Storage identifier는 기록하지 않는다. Debug content logging은 기본 비활성화다. AI Server는 RDB/Object Storage를 조회하지 않고 FILE bytes, Storage URL/key, presigned URL, local path, base64/binary를 받거나 반환하지 않는다.

## 13. P2.6 fixture and validator source

P2.6 verification source는 [fixture root](fixtures/internal-ai-v1/README.md), `manifest.json`과 `validate_fixtures.py`다. Validator는 다음 명령으로 실행한다.

```text
python docs/contracts/fixtures/internal-ai-v1/validate_fixtures.py
```

Fixture case manifest는 다음 범위를 포함한다.

- common execution request success와 common internal error
- chunk order/hash success, chunk gap failure, canonical input hash fixture
- 13개 task 각각의 minimum valid request와 valid result
- `IDEA_LEGAL_PRECHECK`, `CONCEPT_LEGAL_VALIDATION`의 valid request/result와 required-field negative
- `TEXT` 수락, `PLAIN_TEXT` 및 잘못된 locale/language 거부
- legal degraded source result
- financial deterministic/result와 AI explanation boundary
- persona isolation과 synthetic disclosure
- marketing prohibited probability/statistical claim rejection
- final report user decision preservation
- unsupported contract/task schema version, deadline exceeded, result schema invalid
- unknown request-local reference rejection

Fixture는 이 문서의 77개 field table을 제한된 Markdown parser로 읽어 exact field, presence, nullability, type, bounds/enum과 named nested schema를 재귀 검증한다. Negative도 positive와 동일한 validator 경로에서 manifest의 단일 expected rule로 실패해야 한다. Actual object에 존재하는 named schema instance만 coverage로 인정하며 manifest 선언과 exact equality를 검사한다. Bounds cell은 지원 분류나 실행 handler가 없으면 `UNSUPPORTED_BOUND_SPEC`으로 실패한다. 일반 string의 단일 literal Bounds는 exact equality로 검증하며 불일치는 `STRING_LITERAL_MISMATCH`다. Public P2.4 contract와는 동명 consistency registry의 exact set/value equality, error mapping과 Financial/Persona/Marketing invariant equality를 검사한다.

2 MiB request/response hard limit은 원본 UTF-8 encoded transport byte length에 적용한다. Whitespace를 제거한 JSON, canonical JSON 또는 `json.dumps` 재직렬화 길이는 transport limit의 근거가 아니다. Internal error envelope도 response limit을 적용받는다. Boundary self-test는 정확히 2 MiB를 허용하고 2 MiB+1 byte와 multibyte UTF-8 초과를 각각 `REQUEST_BYTES_EXCEEDED` 또는 `RESPONSE_BYTES_EXCEEDED`로 거부한다.

## 14. Exact schema notation and common registry

### Public/Internal consistency registry

아래 registry는 [Public API v2 계약](PUBLIC_API_V2_CONTRACT.md)의 동명 표와 exact set/value equality를 유지한다.

| Registry | Values |
|---|---|
| Legal Result | `PASS`, `PASS_WITH_CONDITIONS`, `REVISION_REQUIRED`, `PROHIBITED`, `INSUFFICIENT_INFORMATION`, `EXPERT_REVIEW_REQUIRED` |
| Analysis Type | `MARKET`, `BUSINESS_MODEL`, `TECHNICAL_OPERATION`, `FINANCIAL` |
| Report Decision | `GO`, `CONDITIONAL_GO`, `REWORK`, `HOLD`, `STOP` |
| Provenance Category | `USER_INPUT`, `EXTERNAL_SOURCE_FACT`, `ASSUMPTION`, `AI_PROPOSAL`, `USER_DECISION` |
| Marketing Asset Type | `HEADLINE`, `BODY_COPY`, `CTA`, `CAMPAIGN_CONCEPT` |

| Invariant | Contract value |
|---|---|
| FINANCIAL_DETERMINISTIC_INPUT_OWNERSHIP | `SPRING_ONLY` |
| PERSONA_SYNTHETIC_DISCLOSURE | `REQUIRED` |
| MARKETING_PROBABILITY_CLAIMS | `FORBIDDEN` |
| MARKETING_STATISTICAL_AB_CLAIM | `FORBIDDEN` |

이 절의 표가 앞선 narrative보다 우선한다. 모든 object는 unknown field를 `REJECT`하고, 명시된 `extensions` field가 없는 v1 schema는 확장 key를 허용하지 않는다. `REQUIRED`+Nullable `YES`는 key가 반드시 존재하되 JSON `null`을 허용한다. `OPTIONAL`+Nullable `NO`는 omitted 가능하지만 존재하면 null이 아니다. Required array는 비어 있어도 되는 경우 `minItems=0`을 명시하며, omitted과 empty array는 각각 “정보 미제공”과 “검토했으나 항목 없음”으로 구분한다. Decimal은 JSON number가 아닌 canonical decimal string(`^-?(0|[1-9][0-9]*)(\.[0-9]+)?$`)이고 timestamp는 RFC 3339 UTC다. Opaque identifier/local key string은 trim 후 blank를 허용하지 않는다.

Canonical JSON은 UTF-8, Unicode NFC, object key Unicode code point 오름차순, insignificant whitespace 없음, string escaping은 JSON 표준 최소 escape, integer는 leading zero 없음, decimal은 위 canonical string 그대로, array order 보존 규칙을 사용한다. P2.6은 byte-for-byte fixture로 검증한다.

### InternalExecutionRequestV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| contractVersion | string | REQUIRED | NO | `1.0` | envelope version |
| taskType | string enum | REQUIRED | NO | §7 Task Registry의 13개 `Task type` 값 | input discriminator |
| taskSchemaVersion | string | REQUIRED | NO | `1.0` | selected task schema version |
| taskRunId | string | REQUIRED | NO | 1–128 | echo-only opaque execution reference |
| taskAttemptId | string | REQUIRED | NO | 1–128 | echo-only opaque attempt reference |
| correlationId | string | REQUIRED | NO | 1–128, `[A-Za-z0-9._-]+` | header와 exact match |
| deadlineAt | string timestamp | REQUIRED | NO | RFC 3339 UTC | expired request 실행 금지 |
| canonicalInputHash | string | REQUIRED | NO | `sha256:` + lowercase hex 64자 | version/type/schema/locale/input hash |
| locale | string | REQUIRED | NO | `ko-KR` | v1 locale |
| input | task-discriminated object | REQUIRED | NO | 해당 `*InputV1` | unknown field REJECT |

### InternalExecutionSuccessResponseV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| contractVersion | string | REQUIRED | NO | `1.0` | request echo |
| taskType | string enum | REQUIRED | NO | §7 Task Registry의 13개 `Task type` 값 | request `taskType`과 equality |
| taskSchemaVersion | string | REQUIRED | NO | `1.0` | request echo |
| taskRunId | string | REQUIRED | NO | 1–128 | request echo |
| taskAttemptId | string | REQUIRED | NO | 1–128 | request echo |
| correlationId | string | REQUIRED | NO | 1–128 | request echo |
| canonicalInputHash | string | REQUIRED | NO | SHA-256 format | request echo |
| resultSchemaVersion | string | REQUIRED | NO | `1.0` | selected result schema |
| result | task-discriminated object | REQUIRED | NO | 해당 `*ResultV1` | AI-side validation 후 반환 |
| warnings | array<WarningV1> | REQUIRED | NO | minItems 0, maxItems 100 | empty means no warning |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | result-level traceability |
| usage | UsageSummaryV1 | REQUIRED | YES | object or null | 측정 불가하면 null |

### InternalErrorResponseV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| error | InternalErrorBodyV1 | REQUIRED | NO | exactly one | unknown outer field REJECT |

### InternalErrorBodyV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| code | string enum | REQUIRED | NO | §6 Internal Error Mapping의 12개 `Internal code` 값 | primary code 하나; reason registry와 일치 |
| message | string | REQUIRED | NO | 1–512, blank 금지 | safe localized-neutral summary |
| correlationId | string | REQUIRED | NO | 1–128 | valid header value 또는 server-generated safe value |
| taskRunId | string | REQUIRED | YES | null 또는 1–128 | trusted parse 이후만 echo |
| taskAttemptId | string | REQUIRED | YES | null 또는 1–128 | trusted parse 이후만 echo |
| retryable | boolean | REQUIRED | NO | true/false | stable code/reason 기본값 |
| details | array<InternalErrorDetailV1> | REQUIRED | NO | minItems 0, maxItems 50 | raw value/provider body 금지 |

인증 실패, body parse 실패, body size 초과처럼 identifier를 신뢰할 수 없으면 두 task identifier는 null이다. 유효한 `X-Correlation-Id`가 없거나 invalid하면 AI Server가 새 safe correlation ID를 만든다. Header/body mismatch는 `INVALID_REQUEST`다. Missing/invalid token은 401, 인증된 internal principal의 endpoint 권한 부족은 403이며 모두 `UNAUTHORIZED_INTERNAL_CALL`을 사용한다. Unauthorized response는 body identifier를 echo하지 않는다.

### InternalErrorDetailV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| reason | string enum | REQUIRED | NO | 1–64 uppercase snake case | stable retry/adoption reason |
| field | string | OPTIONAL | NO | 1–128 | safe JSON field path; raw value 금지 |
| limitName | string | OPTIONAL | NO | 1–64 | violated named limit |
| supportedValues | array<string> | OPTIONAL | NO | minItems 1, maxItems 20; each 1–64 | safe enum/version values only |
| retryAfterSeconds | integer | OPTIONAL | NO | 0–86,400 | `RATE_LIMITED` safe backoff hint only |

Provider raw reason, 자유문장 또는 HTTP body로 retryability를 결정하지 않는다. 아래 registry 조합만 유효하고 Spring RetryPolicy/attempt limit가 실제 새 Attempt 생성을 최종 결정한다.

### Internal Error Reason Registry

`none`은 해당 detail field가 존재해서는 안 된다는 뜻이다. 모든 행에서 raw request value, provider raw reason/body, prompt, secret, stack trace는 forbidden detail이다.

| Internal code | Allowed reason | retryable | Required detail field | Optional detail field | Forbidden detail | TaskAttempt direction | Public mapping |
|---|---|---|---|---|---|---|---|
| `INVALID_REQUEST` | `JSON_PARSE_FAILED` | false | none | none | body identifiers/raw fragment | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `HEADER_BODY_CORRELATION_MISMATCH` | false | `field` | none | compared raw values | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `UNKNOWN_FIELD` | false | `field` | none | rejected value | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `FIELD_CONSTRAINT_VIOLATION` | false | `field` | `limitName` | rejected value | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `HASH_MISMATCH` | false | `field` | none | expected/actual digest | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `CHUNK_SEQUENCE_INVALID` | false | `field` | `limitName` | chunk text | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `REFERENCE_RESOLUTION_FAILED` | false | `field` | none | unresolved raw identifier | FAILED | `AI_RESULT_INVALID` |
| `UNAUTHORIZED_INTERNAL_CALL` | `SERVICE_TOKEN_MISSING` | false | none | none | request identifiers/token | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `UNAUTHORIZED_INTERNAL_CALL` | `SERVICE_TOKEN_INVALID` | false | none | none | request identifiers/token | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `UNAUTHORIZED_INTERNAL_CALL` | `INTERNAL_PRINCIPAL_FORBIDDEN` | false | none | none | principal/token detail | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `UNSUPPORTED_CONTRACT_VERSION` | `CONTRACT_VERSION_UNSUPPORTED` | false | `field`, `supportedValues` | none | raw body | FAILED | `AI_RESULT_INVALID` |
| `UNSUPPORTED_TASK_TYPE` | `TASK_TYPE_UNSUPPORTED` | false | `field`, `supportedValues` | none | raw body | FAILED | `AI_RESULT_INVALID` |
| `UNSUPPORTED_TASK_SCHEMA_VERSION` | `TASK_SCHEMA_VERSION_UNSUPPORTED` | false | `field`, `supportedValues` | none | raw body | FAILED | `AI_RESULT_INVALID` |
| `PAYLOAD_TOO_LARGE` | `REQUEST_BYTES_EXCEEDED` | false | `limitName` | none | payload/body | FAILED | `PAYLOAD_TOO_LARGE` |
| `PAYLOAD_TOO_LARGE` | `RESPONSE_BYTES_EXCEEDED` | false | `limitName` | none | response body | FAILED | `PAYLOAD_TOO_LARGE` |
| `PAYLOAD_TOO_LARGE` | `TEXT_CONTENT_COUNT_EXCEEDED` | false | `limitName` | `field` | text content | FAILED | `PAYLOAD_TOO_LARGE` |
| `PAYLOAD_TOO_LARGE` | `CHUNK_COUNT_EXCEEDED` | false | `limitName` | `field` | chunk text | FAILED | `PAYLOAD_TOO_LARGE` |
| `PAYLOAD_TOO_LARGE` | `CHUNK_CHARACTERS_EXCEEDED` | false | `limitName` | `field` | chunk text | FAILED | `PAYLOAD_TOO_LARGE` |
| `PAYLOAD_TOO_LARGE` | `TOTAL_CHARACTERS_EXCEEDED` | false | `limitName` | `field` | extracted text | FAILED | `PAYLOAD_TOO_LARGE` |
| `PAYLOAD_TOO_LARGE` | `TASK_COLLECTION_LIMIT_EXCEEDED` | false | `limitName` | `field` | collection content | FAILED | `PAYLOAD_TOO_LARGE` |
| `DEADLINE_EXCEEDED` | `REQUEST_DEADLINE_EXCEEDED` | true | none | none | dependency timing detail | TIMED_OUT | `TASK_TIMEOUT` |
| `DEPENDENCY_UNAVAILABLE` | `MODEL_DEPENDENCY_UNAVAILABLE` | true | none | none | dependency identity/raw body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `DEPENDENCY_UNAVAILABLE` | `MCP_DEPENDENCY_UNAVAILABLE` | true | none | none | MCP identity/raw body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `DEPENDENCY_UNAVAILABLE` | `LEGAL_SOURCE_DEPENDENCY_UNAVAILABLE` | true | none | none | secret/raw body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `DEPENDENCY_UNAVAILABLE` | `AI_CONFIGURATION_INVALID` | false | none | none | provider/model/secret identity | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `DEPENDENCY_UNAVAILABLE` | `LEGAL_CONFIGURATION_INVALID` | false | none | none | secret/config raw value | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `DEPENDENCY_UNAVAILABLE` | `MOLEG_AUTHENTICATION_FAILED` | false | none | none | credential/raw body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `DEPENDENCY_UNAVAILABLE` | `MOLEG_REQUEST_REJECTED` | false | none | none | request/raw body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `DEPENDENCY_UNAVAILABLE` | `MOLEG_RESPONSE_INVALID` | false | none | none | response body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `DEPENDENCY_UNAVAILABLE` | `MOLEG_DEPENDENCY_UNAVAILABLE` | true | none | none | dependency/raw body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `DEPENDENCY_UNAVAILABLE` | `MOLEG_RATE_LIMITED` | true | none | none | quota/raw body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `RATE_LIMITED` | `DEPENDENCY_RATE_LIMITED` | true | none | `retryAfterSeconds` | provider quota/body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `EXECUTION_FAILED` | `TRANSIENT_EXECUTION_FAILURE` | true | none | none | provider raw reason/body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `EXECUTION_FAILED` | `PERMANENT_EXECUTION_FAILURE` | false | none | none | provider raw reason/body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `EXECUTION_FAILED` | `SAFETY_POLICY_BLOCKED` | false | none | none | policy/provider raw body | FAILED | `AI_SERVICE_UNAVAILABLE` |
| `RESULT_SCHEMA_INVALID` | `RESULT_UNKNOWN_FIELD` | false | `field` | none | result raw value | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `RESULT_FIELD_CONSTRAINT_VIOLATION` | false | `field` | `limitName` | result raw value | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `RESULT_REFERENCE_INVALID` | false | `field` | none | unresolved raw identifier | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `RESULT_DOMAIN_INVARIANT_VIOLATION` | false | `field` | none | result content | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `AI_RESULT_INVALID` | false | none | none | result/provider raw content | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `LEGAL_ROUTING_CONTRACT_INVALID` | false | none | none | model result | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `LEGAL_SCREENING_CONTRACT_INVALID` | false | none | none | model result | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `LEGAL_CITATION_COVERAGE_INVALID` | false | none | none | model result | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `LEGAL_SCREENING_FIELD_INVALID` | false | none | none | model result | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `LEGAL_SOURCE_CONTRACT_INVALID` | false | none | none | result content | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `CONCEPT_LEGAL_VALIDATION_INVALID` | false | none | none | model result | FAILED | `AI_RESULT_INVALID` |
| `RESULT_SCHEMA_INVALID` | `CONCEPT_LEGAL_VALIDATION_CANDIDATE_KEYS_INVALID` | false | none | none | candidate values/model result | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `LEGAL_INPUT_CONTRACT_INCOMPLETE` | false | none | none | raw input | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `LEGAL_MODE_INVALID` | false | none | none | raw input | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `LEGAL_RERUN_CATEGORIES_INVALID` | false | none | none | raw input | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `LEGAL_CONFIRMED_FACTS_INVALID` | false | none | none | raw input | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `LEGAL_REGISTRY_VERSION_MISMATCH` | false | none | none | raw registry value | FAILED | `AI_RESULT_INVALID` |
| `INVALID_REQUEST` | `CONCEPT_LEGAL_VALIDATION_MODE_INVALID` | false | none | none | raw mode | FAILED | `AI_RESULT_INVALID` |
| `INTERNAL_ERROR` | `UNEXPECTED_INTERNAL_ERROR` | true | none | none | stack trace/internal topology | FAILED | `AI_SERVICE_UNAVAILABLE` |

12개 internal code 각각 하나 이상의 reason을 가진다. 같은 code/reason의 retryable 값은 고정이며 다른 조합은 `InternalErrorResponseV1` validation 실패다. `DEADLINE_EXCEEDED`의 현재 Attempt는 terminal이고 `retryable=true`는 새 Attempt가 정책상 가능할 수 있다는 의미뿐이다.

### UsageSummaryV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| unit | string enum | REQUIRED | NO | `TOKENS`, `CHARACTERS`, `OTHER` | provider-neutral unit |
| inputUnits | integer | REQUIRED | NO | 0–9,007,199,254,740,991 | non-negative |
| outputUnits | integer | REQUIRED | NO | 0–9,007,199,254,740,991 | non-negative |
| totalUnits | integer | REQUIRED | NO | 0–9,007,199,254,740,991 | inputUnits + outputUnits와 같아야 함 |
| estimated | boolean | REQUIRED | NO | true/false | 측정치 추정 여부 |

Provider/model 이름과 비용·가격은 포함하지 않는다. 일부 unit도 측정할 수 없으면 전체 `usage`가 null이다.

### WarningV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| code | string | REQUIRED | NO | 1–64 uppercase snake case | stable warning code |
| message | string | REQUIRED | NO | 1–512 | safe bounded explanation |
| sourceKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 50; LocalKey | request/output local references only |

### TextContentV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| contentKey | string | REQUIRED | NO | LocalKey 1–64 | execution-wide unique input key |
| contentType | string enum | REQUIRED | NO | `TEXT` | binary/HTML 금지 |
| language | string | REQUIRED | NO | `ko-KR` | content language |
| sourceLabel | string | OPTIONAL | NO | 1–128 | path/credential 없는 safe label |
| totalCharacters | integer | REQUIRED | NO | 1–500,000 | chunk characterCount 합 |
| contentHash | string | REQUIRED | NO | SHA-256 format | ordered concatenated text hash |
| chunks | array<TextChunkV1> | REQUIRED | NO | minItems 1, maxItems 64 | execution aggregate도 64 이하 |

### TextChunkV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| index | integer | REQUIRED | NO | 0–63 | TextContent별 0부터 연속 |
| text | string | REQUIRED | NO | 1–16,384 Unicode code points | empty 금지 |
| characterCount | integer | REQUIRED | NO | 1–16,384 | Unicode code point count와 일치 |
| chunkHash | string | REQUIRED | NO | SHA-256 format | text UTF-8 hash |

### RequestLocalReferenceV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| key | string | REQUIRED | NO | 1–64, `[A-Za-z0-9][A-Za-z0-9._-]{0,63}` | namespace 안에서 unique |
| namespace | string enum | REQUIRED | NO | `INPUT`, `OUTPUT_PROPOSAL` | input과 proposal identity 분리 |
| resourceType | string enum | REQUIRED | NO | Request-local Resource Type Registry | DB type/identifier가 아님 |

### Request-local Resource Type Registry

허용값은 `SOURCE_EXTRACTION`, `SOURCE_STATEMENT`, `IDEA_VERSION`, `LEGAL_REVIEW_RUN`, `CONCEPT_VERSION`, `SHORTLIST_DECISION`, `CONCEPT_SELECTION`, `EVIDENCE_ITEM`, `PERSONA_STUDY`, `PERSONA_CARD_VERSION`, `PERSONA_INTERVIEW_RESULT`, `MARKETING_WORKSPACE_VERSION`, `MARKETING_ASSET_VERSION`, `QUESTION`, `COMPARISON_DIMENSION`, `REPORT_UPSTREAM_RESOURCE`다. 각 task field가 지정한 type만 사용할 수 있고 같은 key/namespace의 중복은 금지한다.

Result는 등록된 `INPUT` key만 참조할 수 있다. 새 concept/persona/asset proposal을 만드는 task만 result에서 unique `OUTPUT_PROPOSAL` key를 선언할 수 있다. 외부 authoritative identifier는 이 schema가 아니라 `ExternalSourceReferenceV1`을 사용한다.

AI는 입력에 없던 local source reference를 임의 생성하지 않는다. Legal adapter가 실행 중 발견한 외부 법령 reference는 예외적으로 `ExternalSourceReferenceV1`/`LegalSourceReferenceV1`로 추가할 수 있지만 실제 adapter response로 관찰된 identifier와 citation이어야 하며 생성·추측한 identifier는 허용하지 않는다.

### ProvenanceItemV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| category | string enum | REQUIRED | NO | `USER_INPUT`, `EXTERNAL_SOURCE_FACT`, `ASSUMPTION`, `AI_PROPOSAL`, `USER_DECISION` | AI는 USER_DECISION 신규 생성 금지 |
| statementKey | string | REQUIRED | NO | LocalKey | result statement identity |
| sourceKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 50 | registered local keys only |
| externalSourceReferences | array<ExternalSourceReferenceV1> | REQUIRED | NO | minItems 0, maxItems 50 | external facts citation |
| generatedAt | string timestamp | REQUIRED | NO | RFC 3339 UTC | AI generation time |
| confidence | decimal string | OPTIONAL | NO | 0–1 inclusive | calibrated claim 아님 |
| uncertainty | string | OPTIONAL | NO | 1–512 | uncertainty explanation |
| verificationNeeded | boolean | REQUIRED | NO | true/false | follow-up evidence 필요 여부 |
| caveat | string | OPTIONAL | NO | 1–512 | bounded caveat |

### ExternalSourceReferenceV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| sourceChannel | string enum | REQUIRED | NO | `MOLEG_API`, `LEGAL_MCP`, `OTHER_PUBLIC_SOURCE` | external channel |
| externalIdentifier | string | REQUIRED | NO | 1–256 | authoritative external identifier |
| title | string | REQUIRED | NO | 1–512 | source display name |
| locator | string | OPTIONAL | NO | 1–256 | article/section locator |
| observedAt | string timestamp | REQUIRED | NO | RFC 3339 UTC | retrieval time |
| currentness | string enum | REQUIRED | NO | `CURRENT`, `UNCERTAIN`, `OUTDATED` | observation status |
| authoritative | boolean | REQUIRED | NO | true/false | channel-specific authority |
| degraded | boolean | REQUIRED | NO | true/false | degraded retrieval/coverage |
| officialSourceUrl | string | OPTIONAL | NO | HTTPS, maxLength 2048 | external provenance URL; Storage URL 아님 |

### IdeaStatementItemV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| key | string | REQUIRED | NO | LocalKey | stable request-local statement key |
| text | string | REQUIRED | NO | 1–4,000 | blank 금지 |
| provenanceCategory | string enum | REQUIRED | NO | `USER_INPUT`, `EXTERNAL_SOURCE_FACT`, `ASSUMPTION`, `AI_PROPOSAL`, `USER_DECISION` | fact/assumption 구분 |
| sourceReferences | array<string> | REQUIRED | NO | minItems 0, maxItems 50 | registered input keys |
| confidence | decimal string | OPTIONAL | NO | 0–1 | optional uncertainty signal |
| uncertainty | string | OPTIONAL | NO | 1–512 | optional explanation |
| verificationNeeded | boolean | REQUIRED | NO | true/false | 검증 필요 여부 |

## 15. Shared task item schema registry

### LegalFindingV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| findingKey | string | REQUIRED | NO | LocalKey | result statement key |
| findingType | string enum | REQUIRED | NO | `REGULATORY`, `PROHIBITION`, `LICENSING`, `CONSUMER`, `PRIVACY`, `OTHER` | controlled category |
| severity | string enum | REQUIRED | NO | `INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | legal result와 독립 차원 |
| claim | string | REQUIRED | NO | 1–4,000 | legal advice claim 금지 |
| affectedIdeaItemKeys | array<string> | REQUIRED | NO | minItems 1, maxItems 50 | registered input keys |
| requiredAction | string | OPTIONAL | NO | 1–2,000 | remediation direction |
| sourceReferenceKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 50 | result legal sources |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | claim traceability |

### LegalSourceReferenceV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| sourceKey | string | REQUIRED | NO | LocalKey | result-local citation key |
| sourceChannel | string enum | REQUIRED | NO | `MOLEG_API`, `LEGAL_MCP` | coordinated adapter channel |
| lawIdentifier | string | REQUIRED | NO | 1–256 | external identifier |
| lawName | string | REQUIRED | NO | 1–512 | blank 금지 |
| article | string | OPTIONAL | NO | 1–256 | article/paragraph locator |
| observedAt | string timestamp | REQUIRED | NO | RFC 3339 UTC | retrieval time |
| currentness | string enum | REQUIRED | NO | `CURRENT`, `UNCERTAIN`, `OUTDATED` | currentness result |
| authoritative | boolean | REQUIRED | NO | true/false | MOLEG-confirmed claim만 true 가능 |
| degraded | boolean | REQUIRED | NO | true/false | partial coverage marker |
| officialSourceUrl | string | OPTIONAL | NO | HTTPS, maxLength 2048 | external provenance only |

### SourceCoverageV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| attemptedChannels | array<string enum> | REQUIRED | NO | minItems 1, maxItems 2; MOLEG_API/LEGAL_MCP | unique, configured attempts |
| successfulChannels | array<string enum> | REQUIRED | NO | minItems 0, maxItems 2 | attempted subset |
| missingChannels | array<string enum> | REQUIRED | NO | minItems 0, maxItems 2 | attempted minus successful |
| degraded | boolean | REQUIRED | NO | true/false | true iff missingChannels non-empty |
| warnings | array<WarningV1> | REQUIRED | NO | minItems 0, maxItems 20 | missing/degraded explanation |

`degraded=true`인데 `missingChannels`가 비면 invalid다. `authoritative=true` legal fact에는 successful `MOLEG_API` source가 필요하다. LEGAL_MCP 단독 실패는 transport failure가 아닐 수 있다. 두 channel 모두 실패하고 근거가 부족하면 legalResult는 `INSUFFICIENT_INFORMATION` 또는 `EXPERT_REVIEW_REQUIRED`여야 한다.

### EvidenceItemV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| evidenceKey | string | REQUIRED | NO | LocalKey | evidence identity |
| summary | string | REQUIRED | NO | 1–2,000 | bounded claim |
| category | string enum | REQUIRED | NO | `USER_INPUT`, `EXTERNAL_SOURCE_FACT`, `DETERMINISTIC_CALCULATION`, `AI_PROPOSAL` | evidence class |
| sourceKeys | array<string> | REQUIRED | NO | minItems 1, maxItems 50 | registered local/external keys |
| verificationNeeded | boolean | REQUIRED | NO | true/false | unresolved evidence marker |

### ConceptProposalV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| proposalKey | string | REQUIRED | NO | output LocalKey | unique OUTPUT_PROPOSAL key |
| title | string | REQUIRED | NO | 1–200 | blank 금지 |
| targetProblem | string | REQUIRED | NO | 1–4,000 | problem statement |
| targetUserContext | string | REQUIRED | NO | 1–4,000 | context, not demographic-only |
| valueProposition | string | REQUIRED | NO | 1–4,000 | proposal |
| solutionOutline | string | REQUIRED | NO | 1–8,000 | bounded outline |
| differentiators | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | empty allowed |
| constraints | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | user constraints preserved |
| assumptions | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | facts와 분리 |
| evidenceNeeds | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | research needs |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 100 | AI_PROPOSAL required |

### ConceptSnapshotV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| conceptVersionKey | string | REQUIRED | NO | INPUT/CONCEPT_VERSION LocalKey | exact immutable input identity |
| title | string | REQUIRED | NO | 1–200 | blank 금지 |
| targetProblem | string | REQUIRED | NO | 1–4,000 | exact snapshot |
| targetUserContext | string | REQUIRED | NO | 1–4,000 | exact snapshot |
| valueProposition | string | REQUIRED | NO | 1–4,000 | exact snapshot |
| solutionOutline | string | REQUIRED | NO | 1–8,000 | exact snapshot |
| differentiators | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | immutable content |
| constraints | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | immutable content |
| assumptions | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | immutable content |
| evidenceNeeds | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | immutable content |

### AssessmentDimensionV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| dimensionKey | string | REQUIRED | NO | LocalKey | controlled request-local dimension |
| label | string | REQUIRED | NO | 1–128 | display label |
| assessment | string | REQUIRED | NO | 1–4,000 | proposal, not decision |
| rating | string enum | OPTIONAL | NO | `LOW`, `MEDIUM`, `HIGH`, `UNKNOWN` | ordinal only, probability 아님 |
| evidenceKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 50 | EvidenceItem keys |
| caveats | array<string> | REQUIRED | NO | minItems 0, maxItems 50; each 1–512 | limitations |

### DetailedFindingV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| findingKey | string | REQUIRED | NO | LocalKey | result identity |
| category | string enum | REQUIRED | NO | `MARKET`, `BUSINESS_MODEL`, `TECHNICAL_OPERATION`, `FINANCIAL` | analysis type finding category |
| summary | string | REQUIRED | NO | 1–4,000 | bounded finding |
| impact | string enum | REQUIRED | NO | `POSITIVE`, `NEGATIVE`, `MIXED`, `UNKNOWN` | non-probabilistic direction |
| evidenceKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 50 | evidence references |
| assumptions | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | assumption disclosure |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | traceability |

### FinancialExplanationV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| summary | string | REQUIRED | NO | 1–4,000 | deterministic result 설명만 수행 |
| drivers | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–1,000 | causal caveat 포함 |
| risks | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–1,000 | bounded risks |
| caveats | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–1,000 | uncertainty |

### PersonaCardProposalV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| proposalKey | string | REQUIRED | NO | output LocalKey | unique OUTPUT_PROPOSAL key |
| roleAndContext | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | layer 1 |
| problemAndNeeds | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | layer 2 |
| behaviorAndDecision | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | layer 3 |
| syntheticDisclosure | string | REQUIRED | NO | 1–512 | synthetic persona 명시 |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 100 | AI_PROPOSAL required |

### PersonaCardSnapshotV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| personaCardVersionKey | string | REQUIRED | NO | INPUT/PERSONA_CARD_VERSION LocalKey | exact immutable version |
| roleAndContext | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | layer 1 |
| problemAndNeeds | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | layer 2 |
| behaviorAndDecision | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | layer 3 |
| syntheticDisclosure | string | REQUIRED | NO | 1–512 | synthetic source disclosure |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 100 | original card provenance |

### InterviewQuestionAnswerV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| questionKey | string | REQUIRED | NO | LocalKey | input question reference |
| question | string | REQUIRED | NO | 1–2,000 | exact/normalized question |
| syntheticAnswer | string | REQUIRED | NO | 1–8,000 | 실제 응답 claim 금지 |
| interpretation | string | REQUIRED | NO | 1–4,000 | AI proposal |
| evidenceNeeds | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 50 | validation needs |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | synthetic traceability |

### SynthesisResponseItemV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| itemKey | string | REQUIRED | NO | LocalKey | synthesis statement identity |
| summary | string | REQUIRED | NO | 1–4,000 | bounded synthesis |
| interviewKeys | array<string> | REQUIRED | NO | minItems 1, maxItems 100 | included adopted interviews only |
| caveat | string | OPTIONAL | NO | 1–1,000 | limitation |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 50 | source preservation |

### MarketingAssetProposalV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| proposalKey | string | REQUIRED | NO | output LocalKey | unique OUTPUT_PROPOSAL key |
| assetType | string enum | REQUIRED | NO | `HEADLINE`, `BODY_COPY`, `CTA`, `CAMPAIGN_CONCEPT` | text/structured only |
| content | string | REQUIRED | NO | 1–16,000 | binary/base64/path 금지 |
| targetPersonaKeys | array<string> | REQUIRED | NO | minItems 1, maxItems 10 | input Persona keys |
| messageRationale | string | REQUIRED | NO | 1–4,000 | no conversion probability |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 100 | AI_PROPOSAL required |

### MarketingComparisonAssessmentV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| dimensionKey | string | REQUIRED | NO | LocalKey | requested dimension |
| assetKeys | array<string> | REQUIRED | NO | minItems 2, maxItems 20 | exact compared versions |
| relativeAssessment | string | REQUIRED | NO | 1–4,000 | relative, non-statistical |
| personaStrengths | array<string> | REQUIRED | NO | minItems 0, maxItems 100 | bounded statements |
| risks | array<string> | REQUIRED | NO | minItems 0, maxItems 100 | bounded statements |
| caveats | array<string> | REQUIRED | NO | minItems 1, maxItems 100 | A/B limitation required |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 100 | evidence traceability |

### ReportFindingV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| findingKey | string | REQUIRED | NO | LocalKey | report statement identity |
| category | string enum | REQUIRED | NO | `FACT`, `LEGAL_SOURCE`, `AI_PROPOSAL`, `ASSUMPTION`, `RESEARCH_NEED`, `USER_DECISION` | source class |
| text | string | REQUIRED | NO | 1–8,000 | bounded content |
| sourceKeys | array<string> | REQUIRED | NO | minItems 1, maxItems 100 | exact upstream keys |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 100 | traceability |

### ReportSectionV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| sectionKey | string | REQUIRED | NO | LocalKey | unique section identity |
| title | string | REQUIRED | NO | 1–200 | blank 금지 |
| summary | string | REQUIRED | NO | 1–8,000 | structured snapshot content |
| findings | array<ReportFindingV1> | REQUIRED | NO | minItems 0, maxItems 200 | empty allowed |
| caveats | array<string> | REQUIRED | NO | minItems 0, maxItems 100 | limitations |

### MarketAnalysisInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| context | string | REQUIRED | NO | 1–8,000, blank 금지 | market analysis context only |
| evidenceKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 100; unique INPUT/EVIDENCE_ITEM LocalKey | unknown/duplicate key 금지 |

### BusinessModelAnalysisInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| constraints | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–1,000 | blank/duplicate constraint 금지 |
| evidenceKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 100; unique INPUT/EVIDENCE_ITEM LocalKey | registered keys only |

### TechnicalOperationAnalysisInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| operatingConstraints | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–1,000 | blank/duplicate constraint 금지 |
| evidenceKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 100; unique INPUT/EVIDENCE_ITEM LocalKey | registered keys only |

### FinancialMetricV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| metricCode | string | REQUIRED | NO | 1–64, `[A-Z][A-Z0-9_]*` | array 내 unique metric/period tuple |
| value | decimal string | REQUIRED | NO | maxLength 128; canonical decimal regex | JSON number 금지; leading plus/zero와 exponent 금지 |
| currencyCode | string | OPTIONAL | NO | exactly 3 uppercase ASCII letters | monetary value only |
| unit | string | OPTIONAL | NO | 1–32, blank 금지 | currencyCode와 동시 존재 금지 |
| period | string | OPTIONAL | NO | 1–64, blank 금지 | metric period label |

`currencyCode`와 `unit` 중 정확히 하나가 필요하다. Metric tuple 중복과 non-canonical decimal은 validation error다.

### FinancialAnalysisInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| deterministicInputs | array<FinancialMetricV1> | REQUIRED | NO | minItems 1, maxItems 200 | Spring-calculated input snapshot |
| calculationRuleVersion | string | REQUIRED | NO | 1–64, `[A-Za-z0-9._-]+` | Spring deterministic rule version |
| deterministicResults | array<FinancialMetricV1> | REQUIRED | NO | minItems 1, maxItems 200 | Spring-calculated results |
| assumptions | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 100 | explicit assumptions |
| evidenceNeeds | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 100 | unresolved evidence |

### MarketAnalysisResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| findings | array<DetailedFindingV1> | REQUIRED | NO | minItems 0, maxItems 200 | MARKET category only |
| caveats | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–1,000 | bounded limitations |

### BusinessModelAnalysisResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| findings | array<DetailedFindingV1> | REQUIRED | NO | minItems 0, maxItems 200 | BUSINESS_MODEL category only |
| caveats | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–1,000 | bounded limitations |

### TechnicalOperationAnalysisResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| findings | array<DetailedFindingV1> | REQUIRED | NO | minItems 0, maxItems 200 | TECHNICAL_OPERATION category only |
| caveats | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–1,000 | bounded limitations |

### FinancialAnalysisResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| inputSnapshotHash | string | REQUIRED | NO | `sha256:` + lowercase hex 64자 | request financial input snapshot hash equality |
| aiExplanation | FinancialExplanationV1 | REQUIRED | NO | one named object | drivers/risks/caveats sole owner |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 200 | deterministic references and AI proposal |

`deterministicInputs`, `calculationRuleVersion`, `deterministicResults`와 outer `drivers`, `risks`, `caveats`는 forbidden fields다.

### PersonaInterviewQuestionV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| questionKey | string | REQUIRED | NO | 1–64 LocalKey; INPUT/QUESTION namespace | questions array 내 unique |
| text | string | REQUIRED | NO | 1–2,000, blank 금지 | exact interview question |

### IncludedInterviewResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| interviewKey | string | REQUIRED | NO | 1–64 LocalKey; INPUT/PERSONA_INTERVIEW_RESULT | includedInterviews 내 unique; same Study |
| responses | array<InterviewQuestionAnswerV1> | REQUIRED | NO | minItems 1, maxItems 50; unique questionKey | adopted result snapshot |

### MarketingAssetSnapshotV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| assetVersionKey | string | REQUIRED | NO | 1–64 LocalKey; INPUT/MARKETING_ASSET_VERSION | assets array 내 unique |
| assetType | string enum | REQUIRED | NO | Marketing Asset Type Registry: `HEADLINE`, `BODY_COPY`, `CTA`, `CAMPAIGN_CONCEPT` | exact version type |
| content | string | REQUIRED | NO | 1–16,000, blank 금지 | text only; binary/path 금지 |

### MarketingComparisonDimensionV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| dimensionKey | string | REQUIRED | NO | 1–64 LocalKey; INPUT/COMPARISON_DIMENSION | dimensions array 내 unique |
| label | string | REQUIRED | NO | 1–128, blank 금지 | relative comparison dimension |

모든 위 named object도 unknown field를 REJECT한다. Key uniqueness는 enclosing execution input 전체와 각 array 모두에서 검증한다.

## 16. Exact task input/result schema registry

각 Input/Result object는 unknown field를 REJECT한다. Common success의 provenance와 warnings가 있어도 아래 result-level provenance/warnings는 해당 business content에 대한 채택 대상이며 생략할 수 없다.

### IdeaInterpretationInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| textContents | array<TextContentV1> | REQUIRED | NO | minItems 1, maxItems 64; aggregate chunks max 64 | exact verified extractions |
| sourceReferences | array<RequestLocalReferenceV1> | REQUIRED | NO | minItems 1, maxItems 64; INPUT/SOURCE_EXTRACTION | contentKey와 1:1 |
| normalizationMode | string enum | REQUIRED | NO | `PRESERVE_CONSTRAINTS` | v1 only mode |
| maxOpenQuestions | integer | REQUIRED | NO | 1–50 | output bound |
| preserveSourceWording | boolean | REQUIRED | NO | true/false | false여도 제약 삭제 금지 |

### IdeaInterpretationResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| originalSourceSummary | string | REQUIRED | NO | 1–8,000 | source-faithful summary |
| normalizedDescription | string | REQUIRED | NO | 1–16,000 | confirmed IdeaVersion 아님 |
| facts | array<string> | REQUIRED | NO | minItems 0, maxItems 200 | 원문에서 확인한 사실 |
| assumptions | array<string> | REQUIRED | NO | minItems 0, maxItems 200 | facts와 분리 |
| constraints | array<string> | REQUIRED | NO | minItems 0, maxItems 200 | 사용자 constraint 삭제 금지 |
| openQuestions | array<string> | REQUIRED | NO | minItems 0, maxItems 50 | clarificationQuestions.question과 같은 순서 |
| readiness | string enum | REQUIRED | NO | `UNDER_SPECIFIED`, `APPROPRIATE`, `OVER_SPECIFIED` | domain meaning 유지 |
| warnings | array<string> | REQUIRED | NO | minItems 0, maxItems 100 | empty allowed |
| evidenceNeeds | array<string> | REQUIRED | NO | minItems 0, maxItems 100 | research needs |
| originDraft | JSON value | REQUIRED | NO | exact object | nullable/array fields도 생략 금지 |
| fieldMetadata | array<IdeaInputMetadataV1> | REQUIRED | NO | minItems 0, maxItems 100 | `MISSING`이면 `sourceType=AI_PROPOSED`, `locked=false` |
| clarificationQuestions | array<IdeaClarificationQuestionV1> | REQUIRED | NO | minItems 0, maxItems 50 | 누락된 Origin 필수 필드별 질문 필요 |

`originDraft`는 `productServiceDescription`, `problem`, `target`, `solution`, `coreValue`, `primaryCategory`, `targetRegion`, `fixedValues`, `confirmedValues`, `assumptions`, `pricingIntent`, `revenueModelIntent`, `salesChannelIntent`, `knownUnitCost`, `alternatives`, `knownCompetitors`, `differentiationIntent`, `internalConstraints`만 가진다. `fieldMetadata` 항목은 `key`, `sourceType`, `requiredForStages`, `status`, `locked`, `fallbackPolicy`만, `clarificationQuestions` 항목은 `targetField`, `requirement`, `question`, `reason`만 가진다. Adoption은 이 exact 구조와 fact/assumption 분리, constraint 보존을 검증하며 IdeaVersion/User Decision을 자동 생성하지 않는다.

### IdeaInputMetadataV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| key | string | REQUIRED | NO | 1–200 | Idea Origin field key |
| sourceType | string enum | REQUIRED | NO | `USER_CONFIRMED`, `AI_PROPOSED` | model output은 확정값을 임의 승격하지 않음 |
| requiredForStages | array<string> | REQUIRED | NO | minItems 0, maxItems 3; each 1–200 | values are `IDEA_ORIGIN`, `LEGAL_PRECHECK`, `CONCEPT_BUILD` |
| status | string enum | REQUIRED | NO | `MISSING`, `AI_PROPOSED`, `USER_CONFIRMED` | current value state |
| locked | boolean | REQUIRED | NO | true/false | missing/proposed는 false |
| fallbackPolicy | string enum | REQUIRED | NO | `NO_FALLBACK`, `AI_MAY_PROPOSE`, `BLOCK_STAGE` | downstream fallback rule |

### IdeaClarificationQuestionV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| targetField | string | REQUIRED | NO | 1–200 | missing or legal-sensitive field |
| requirement | string enum | REQUIRED | NO | `REQUIRED_FOR_IDEA_ORIGIN`, `REQUIRED_FOR_LEGAL_PRECHECK` | question gate |
| question | string | REQUIRED | NO | 1–2,000 | user-facing question |
| reason | string | REQUIRED | NO | 1–2,000 | why the answer is required |

### LegalReviewInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| ideaVersionKey | string | REQUIRED | NO | INPUT/IDEA_VERSION LocalKey | exact confirmed version |
| normalizedDescription | string | REQUIRED | NO | 1–16,000 | immutable snapshot |
| facts | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | confirmed facts |
| assumptions | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | explicit assumptions |
| constraints | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | preserved constraints |
| jurisdiction | string enum | REQUIRED | NO | `KR` | Korean review only |
| includeRelatedStatutes | boolean | REQUIRED | NO | true/false | bounded adapter option |

### LegalReviewResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| legalResult | string enum | REQUIRED | NO | `PASS`, `PASS_WITH_CONDITIONS`, `REVISION_REQUIRED`, `PROHIBITED`, `INSUFFICIENT_INFORMATION`, `EXPERT_REVIEW_REQUIRED` | business result, not transport status |
| findings | array<LegalFindingV1> | REQUIRED | NO | minItems 0, maxItems 100 | source keys resolve |
| sourceReferences | array<LegalSourceReferenceV1> | REQUIRED | NO | minItems 0, maxItems 200 | external citations |
| sourceCoverage | SourceCoverageV1 | REQUIRED | NO | exact object | degraded invariants apply |
| conditions | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–2,000 | passing conditions |
| warnings | array<WarningV1> | REQUIRED | NO | minItems 0, maxItems 100 | source/legal caveats |
| expertReviewReasons | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–2,000 | required when expert result |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | external facts cited |

Adoption은 coverage consistency와 MOLEG authority 규칙을 검사한다. `EXPERT_REVIEW_REQUIRED`는 전문가 판정이 아니라 review gate다.

### ConceptGenerationInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| ideaVersionKey | string | REQUIRED | NO | INPUT/IDEA_VERSION LocalKey | exact version |
| legalReviewKey | string | REQUIRED | NO | INPUT/LEGAL_REVIEW_RUN LocalKey | exact passing run |
| normalizedDescription | string | REQUIRED | NO | 1–16,000 | idea snapshot |
| facts | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | confirmed facts |
| assumptions | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | assumptions |
| constraints | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | constraints |
| legalResult | string enum | REQUIRED | NO | `PASS`, `PASS_WITH_CONDITIONS` | legal gate |
| legalConditions | array<string> | REQUIRED | NO | minItems 0, maxItems 100 | adopted conditions |
| candidateCount | integer | REQUIRED | NO | 1–10 | exact requested count |
| generationFocuses | array<string enum> | REQUIRED | NO | minItems 0, maxItems 5; `VALUE`, `DELIVERY`, `DIFFERENTIATION`, `RISK`, `OPERABILITY` | unique options |

### ConceptGenerationResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| concepts | array<ConceptProposalV1> | REQUIRED | NO | minItems 1, maxItems 10; candidateCount와 동일 | unique output keys |
| warnings | array<WarningV1> | REQUIRED | NO | minItems 0, maxItems 100 | bounded warnings |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | AI proposal traceability |

User Selection 생성이 없어야 채택한다.

### QuickAssessmentInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| conceptVersionKey | string | REQUIRED | NO | INPUT/CONCEPT_VERSION LocalKey | exact one concept |
| concept | ConceptSnapshotV1 | REQUIRED | NO | one object | exact immutable input snapshot |
| sharedEvidence | array<EvidenceItemV1> | REQUIRED | NO | minItems 0, maxItems 100 | shared core evidence |
| dimensionKeys | array<string> | REQUIRED | NO | minItems 1, maxItems 20; LocalKey | unique requested dimensions |

### QuickAssessmentResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| dimensions | array<AssessmentDimensionV1> | REQUIRED | NO | minItems 1, maxItems 20 | exact requested keys |
| evidence | array<EvidenceItemV1> | REQUIRED | NO | minItems 0, maxItems 100 | result evidence |
| assumptions | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 100 | no fact promotion |
| uncertainties | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–1,000 | limitations |
| warnings | array<WarningV1> | REQUIRED | NO | minItems 0, maxItems 100 | empty allowed |
| evidenceNeeds | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 100 | research needs |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | AI proposal |

Shortlist/User Decision field가 없어야 채택한다.

### DetailedAnalysisInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| conceptVersionKey | string | REQUIRED | NO | INPUT/CONCEPT_VERSION LocalKey | exact shortlisted concept |
| shortlistDecisionKey | string | REQUIRED | NO | INPUT/SHORTLIST_DECISION LocalKey | user decision reference |
| analysisType | string enum | REQUIRED | NO | `MARKET`, `BUSINESS_MODEL`, `TECHNICAL_OPERATION`, `FINANCIAL` | discriminator |
| sharedEvidence | array<EvidenceItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | shared snapshot |
| marketInput | MarketAnalysisInputV1 | OPTIONAL | NO | one named object | present iff `analysisType=MARKET`; otherwise omitted |
| businessModelInput | BusinessModelAnalysisInputV1 | OPTIONAL | NO | one named object | present iff `analysisType=BUSINESS_MODEL`; otherwise omitted |
| technicalOperationInput | TechnicalOperationAnalysisInputV1 | OPTIONAL | NO | one named object | present iff `analysisType=TECHNICAL_OPERATION`; otherwise omitted |
| financialInput | FinancialAnalysisInputV1 | OPTIONAL | NO | one named object | present iff `analysisType=FINANCIAL`; otherwise omitted |

Discriminator에 맞는 section 정확히 하나만 존재해야 한다. 비선택 section은 omitted이어야 하고 explicit null은 허용하지 않는다.

### DetailedAnalysisResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| analysisType | string enum | REQUIRED | NO | Analysis Type Registry: `MARKET`, `BUSINESS_MODEL`, `TECHNICAL_OPERATION`, `FINANCIAL` | request `analysisType`과 equality |
| findings | array<DetailedFindingV1> | REQUIRED | NO | minItems 0, maxItems 200 | non-financial/financial common findings |
| marketResult | MarketAnalysisResultV1 | OPTIONAL | NO | one named object | present iff `analysisType=MARKET`; otherwise omitted |
| businessModelResult | BusinessModelAnalysisResultV1 | OPTIONAL | NO | one named object | present iff `analysisType=BUSINESS_MODEL`; otherwise omitted |
| technicalOperationResult | TechnicalOperationAnalysisResultV1 | OPTIONAL | NO | one named object | present iff `analysisType=TECHNICAL_OPERATION`; otherwise omitted |
| financialResult | FinancialAnalysisResultV1 | OPTIONAL | NO | one named object | present iff `analysisType=FINANCIAL`; otherwise omitted |
| warnings | array<WarningV1> | REQUIRED | NO | minItems 0, maxItems 100 | empty allowed |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | traceability |

`FinancialAnalysisResultV1`은 `inputSnapshotHash`, `aiExplanation`, `provenance`만 소유한다. Deterministic fields와 outer drivers/risks/caveats는 금지한다. `inputSnapshotHash`는 Spring이 계산한 financial input snapshot hash와 equality를 검증한다. Discriminator section은 정확히 하나이고 비선택 result section은 omitted이어야 한다.

### PersonaCardGenerationInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| personaStudyKey | string | REQUIRED | NO | INPUT/PERSONA_STUDY LocalKey | exact study |
| conceptSelectionKey | string | REQUIRED | NO | INPUT/CONCEPT_SELECTION LocalKey | exact user selection |
| selectedConceptVersionKey | string | REQUIRED | NO | INPUT/CONCEPT_VERSION LocalKey | selected version |
| selectedConcept | ConceptSnapshotV1 | REQUIRED | NO | one object | immutable snapshot |
| personaCount | integer | REQUIRED | NO | 1–10 | requested count |
| diversityFocuses | array<string enum> | REQUIRED | NO | minItems 0, maxItems 3; `ROLE_CONTEXT`, `PROBLEM_NEEDS`, `BEHAVIOR_DECISION` | generation option |

### PersonaCardGenerationResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| personaCards | array<PersonaCardProposalV1> | REQUIRED | NO | minItems 1, maxItems 10; personaCount와 동일 | unique output keys |
| warnings | array<WarningV1> | REQUIRED | NO | minItems 0, maxItems 100 | empty allowed |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | AI proposal |

각 card는 세 layer와 synthetic disclosure를 가져야 한다. Demographic-only, 실제 조사, 구매확률, 시장점유율, 모집단 통계 content는 adoption 거부 대상이다.

### PersonaInterviewInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| personaStudyKey | string | REQUIRED | NO | INPUT/PERSONA_STUDY LocalKey | exact study |
| personaCardVersionKey | string | REQUIRED | NO | INPUT/PERSONA_CARD_VERSION LocalKey | exactly one card |
| personaCard | PersonaCardSnapshotV1 | REQUIRED | NO | one object | selected card snapshot only |
| selectedConceptVersionKey | string | REQUIRED | NO | INPUT/CONCEPT_VERSION LocalKey | concept context |
| questions | array<PersonaInterviewQuestionV1> | REQUIRED | NO | minItems 1, maxItems 50; unique questionKey | named question objects only |
| responseStyle | string enum | REQUIRED | NO | `CONCISE`, `STANDARD`, `DETAILED` | bounded option |

다른 Persona key/card/interview/answer context가 있으면 schema/domain validation을 거부한다.

### PersonaInterviewResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| responses | array<InterviewQuestionAnswerV1> | REQUIRED | NO | minItems 1, maxItems 50 | exact question set, no unknown keys |
| warnings | array<WarningV1> | REQUIRED | NO | minItems 0, maxItems 100 | empty allowed |
| syntheticDisclosure | string | REQUIRED | NO | 1–512 | actual interview 아님을 명시 |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | synthetic/AI proposal |

### InterviewSynthesisInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| personaStudyKey | string | REQUIRED | NO | INPUT/PERSONA_STUDY LocalKey | common study |
| includedInterviews | array<IncludedInterviewResultV1> | REQUIRED | NO | minItems 2, maxItems 100; unique interviewKey | same-study adopted results only |
| excludedInterviewKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 100 | disjoint from included |
| synthesisFocuses | array<string enum> | REQUIRED | NO | minItems 1, maxItems 4; `COMMON`, `CONFLICT`, `UNRESOLVED`, `RESEARCH` | unique |

### InterviewSynthesisResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| commonResponses | array<SynthesisResponseItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | source interviews retained |
| conflictingResponses | array<SynthesisResponseItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | conflicts not erased |
| unresolvedQuestions | array<SynthesisResponseItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | open issues |
| researchRecommendations | array<SynthesisResponseItemV1> | REQUIRED | NO | minItems 0, maxItems 200 | future real research |
| caveats | array<string> | REQUIRED | NO | minItems 1, maxItems 100; each 1–1,000 | synthetic limitation required |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | included source keys only |

Adoption은 모든 interview key가 같은 Study의 adopted result인지 확인하며 원본 Interview를 수정하지 않는다.

### MarketingGenerationInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| workspaceVersionKey | string | REQUIRED | NO | INPUT/MARKETING_WORKSPACE_VERSION LocalKey | exact workspace version |
| selectedConceptVersionKey | string | REQUIRED | NO | INPUT/CONCEPT_VERSION LocalKey | exact selected concept |
| personaEvidence | array<EvidenceItemV1> | REQUIRED | NO | minItems 1, maxItems 200 | Persona/interview/synthesis evidence |
| assetType | string enum | REQUIRED | NO | Marketing Asset Type Registry: `HEADLINE`, `BODY_COPY`, `CTA`, `CAMPAIGN_CONCEPT` | text/structured only |
| targetPersonaKeys | array<string> | REQUIRED | NO | minItems 1, maxItems 10 | exact Persona keys |
| generationBrief | string | REQUIRED | NO | 1–8,000 | bounded user brief |
| tone | string enum | REQUIRED | NO | `INFORMATIVE`, `EMPATHETIC`, `DIRECT`, `PROFESSIONAL` | bounded option |

### MarketingGenerationResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| assets | array<MarketingAssetProposalV1> | REQUIRED | NO | minItems 1, maxItems 20 | unique output keys |
| warnings | array<WarningV1> | REQUIRED | NO | minItems 0, maxItems 100 | empty allowed |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | AI proposal |

Binary/base64, file/path/Storage reference와 conversion probability content는 adoption 거부 대상이다.

### MarketingComparisonInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| workspaceVersionKey | string | REQUIRED | NO | INPUT/MARKETING_WORKSPACE_VERSION LocalKey | exact workspace version |
| assets | array<MarketingAssetSnapshotV1> | REQUIRED | NO | minItems 2, maxItems 20; unique assetVersionKey | exact immutable asset versions |
| personaEvidence | array<EvidenceItemV1> | REQUIRED | NO | minItems 1, maxItems 200 | exact evidence |
| comparisonDimensions | array<MarketingComparisonDimensionV1> | REQUIRED | NO | minItems 1, maxItems 30; unique dimensionKey | named comparison dimensions |

### MarketingComparisonResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| assessments | array<MarketingComparisonAssessmentV1> | REQUIRED | NO | minItems 1, maxItems 30 | exact requested dimensions |
| overallCaveats | array<string> | REQUIRED | NO | minItems 1, maxItems 100; each 1–1,000 | non-statistical limitation |
| evidenceNeeds | array<IdeaStatementItemV1> | REQUIRED | NO | minItems 0, maxItems 100 | research needs |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | input asset/persona refs |

통계적 A/B claim, winner probability, conversion/market-share prediction field 또는 content는 금지한다.

### FinalReportGenerationInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| upstreamReferences | array<RequestLocalReferenceV1> | REQUIRED | NO | minItems 1, maxItems 500; INPUT only | exact immutable set |
| facts | array<ReportFindingV1> | REQUIRED | NO | minItems 0, maxItems 500 | FACT category only |
| legalSources | array<LegalSourceReferenceV1> | REQUIRED | NO | minItems 0, maxItems 200 | exact citations |
| aiProposals | array<ReportFindingV1> | REQUIRED | NO | minItems 0, maxItems 500 | AI_PROPOSAL category |
| assumptions | array<ReportFindingV1> | REQUIRED | NO | minItems 0, maxItems 500 | ASSUMPTION category |
| researchNeeds | array<ReportFindingV1> | REQUIRED | NO | minItems 0, maxItems 500 | RESEARCH_NEED category |
| userDecisions | array<ReportFindingV1> | REQUIRED | NO | minItems 1, maxItems 100 | USER_DECISION category |
| reportDecision | string enum | REQUIRED | NO | `GO`, `CONDITIONAL_GO`, `REWORK`, `HOLD`, `STOP` | user-provided immutable value |
| userRationale | string | REQUIRED | NO | 1–8,000 | authenticated user input |

### FinalReportGenerationResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| reportDecision | string enum | REQUIRED | NO | Report Decision Registry: `GO`, `CONDITIONAL_GO`, `REWORK`, `HOLD`, `STOP` | request `reportDecision`과 equality; 변경 금지 |
| executiveSummary | string | REQUIRED | NO | 1–16,000 | structured proposal |
| sections | array<ReportSectionV1> | REQUIRED | NO | minItems 1, maxItems 50 | unique section keys |
| supportingFindings | array<ReportFindingV1> | REQUIRED | NO | minItems 0, maxItems 500 | exact sources |
| risks | array<ReportFindingV1> | REQUIRED | NO | minItems 0, maxItems 200 | risk findings |
| unresolvedResearch | array<ReportFindingV1> | REQUIRED | NO | minItems 0, maxItems 200 | research needs |
| caveats | array<string> | REQUIRED | NO | minItems 1, maxItems 100; each 1–1,000 | AI/report limitation |
| provenance | array<ProvenanceItemV1> | REQUIRED | NO | minItems 1, maxItems 500 | all section/finding traceability |

Spring은 `reportDecision` value equality, exact upstream references와 category separation을 검증한다. Result는 proposal일 뿐 FinalReportVersion 저장과 PDF 생성은 Spring 책임이며 Markdown/binary output은 금지한다.

### LegalConfirmedFactV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| key | string | REQUIRED | NO | 1–128, blank 금지 | confirmed Idea Origin field key |
| value | JSON value | REQUIRED | YES | structured or scalar JSON | exact confirmed value snapshot |
| source | string | REQUIRED | NO | 1–256, blank 금지 | safe confirmation source label |

### LegalSourcePipelineInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| mode | string enum | REQUIRED | NO | `FULL`, `INCREMENTAL` | pipeline mode |
| rerunCategories | array<string> | REQUIRED | NO | minItems 0, maxItems 10; unique | incremental categories only |
| confirmedFacts | array<LegalConfirmedFactV1> | REQUIRED | NO | minItems 0, maxItems 200 | confirmed values only |
| registryVersion | string | REQUIRED | NO | 1–128, blank 금지 | exact deployed legal registry |
| promptVersion | string | REQUIRED | NO | 1–128, blank 금지 | prompt contract identity |
| sourceSchemaVersion | string | REQUIRED | NO | 1–64, blank 금지 | legal source result schema identity |
| textContents | array<TextContentV1> | REQUIRED | NO | minItems 1, maxItems 64; aggregate chunks max 64 | canonical source text |

### LegalSourceRouteV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| routeId | string | REQUIRED | NO | 1–128, blank 금지 | registry route id; unique |
| topic | string | REQUIRED | NO | 1–512, blank 금지 | safe route label |
| status | string enum | REQUIRED | NO | `APPLIES`, `POSSIBLE`, `NOT_APPLICABLE`, `UNKNOWN` | route decision |
| evidenceQuotes | array<string> | REQUIRED | NO | minItems 0, maxItems 20; each 1–2,000 | verbatim input evidence |
| reason | string | REQUIRED | NO | 1–2,000, blank 금지 | bounded explanation |
| categories | array<string> | REQUIRED | NO | minItems 0, maxItems 10; unique | registry categories |

### LegalSourceReasoningV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| category | string | REQUIRED | NO | 1–128, blank 금지 | finding category |
| inputBasis | array<string> | REQUIRED | NO | minItems 0, maxItems 20; each 1–2,000 | source input quotes |
| regulatoryArea | string | REQUIRED | NO | 1–512, blank 금지 | regulatory area label |
| obligation | string | REQUIRED | NO | 1–4,000, blank 금지 | bounded obligation summary |
| consequence | string | REQUIRED | NO | 1–4,000, blank 금지 | bounded consequence summary |
| requiredAction | string | REQUIRED | NO | 1–4,000, blank 금지 | bounded action summary |
| evidenceIds | array<string> | REQUIRED | NO | minItems 0, maxItems 200; unique | references LegalSourceEvidenceV1 |

### LegalSourceFindingV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| category | string | REQUIRED | NO | 1–128, blank 금지 | registry category; unique in result |
| applicability | string enum | REQUIRED | NO | `APPLIES`, `POSSIBLE` | applicable finding only |
| summary | string | REQUIRED | NO | 1–4,000, blank 금지 | source-grounded summary |
| evidenceIds | array<string> | REQUIRED | NO | minItems 0, maxItems 200; unique | references evidence array |
| reasoning | LegalSourceReasoningV1 | REQUIRED | NO | exact object | category and evidence references match |

### LegalSourceEvidenceV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| evidenceId | string | REQUIRED | NO | 1–128, blank 금지 | execution-local unique id |
| routeId | string | REQUIRED | NO | 1–128, blank 금지 | references route |
| category | string | REQUIRED | NO | 1–128, blank 금지 | registry category |
| registryVersion | string | REQUIRED | NO | 1–128, blank 금지 | equals result registryVersion |
| lawName | string | REQUIRED | NO | 1–512, blank 금지 | official law name |
| article | string | REQUIRED | NO | 1–256, blank 금지 | official article identifier |
| title | string | REQUIRED | YES | maxLength 512 | optional article title |
| role | string enum | REQUIRED | NO | `REQUIREMENT`, `SANCTION`, `SCOPE`, `SUPPORTING` | screening role |
| plainSummary | string | REQUIRED | NO | 1–4,000, blank 금지 | grounded summary |
| whyRelevant | string | REQUIRED | NO | 1–4,000, blank 금지 | relevance explanation |
| excerpt | string | REQUIRED | NO | 1–4,000, blank 금지 | source excerpt |
| effectiveDate | string | REQUIRED | YES | maxLength 64 | source effective date |
| lawUrl | string | REQUIRED | NO | HTTPS URL maxLength 2,048 | official source URL |
| verifiedAt | string timestamp | REQUIRED | NO | RFC 3339 UTC `Z` | retrieval timestamp |

### LegalSourceQuestionV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| question | string | REQUIRED | NO | 1–2,000, blank 금지 | missing-information question |
| relatedRouteIds | array<string> | REQUIRED | NO | minItems 0, maxItems 20; unique | references route ids |

### LegalSourcePipelineResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| taskType | string enum | REQUIRED | NO | `IDEA_LEGAL_PRECHECK`, `CONCEPT_LEGAL_VALIDATION` | equals request taskType |
| sourceStatus | string enum | REQUIRED | NO | `SOURCE_COMPLETE`, `SOURCE_PARTIAL`, `REGISTRY_GAP` | source coverage outcome |
| registryVersion | string | REQUIRED | NO | 1–128, blank 금지 | exact registry identity |
| routes | array<LegalSourceRouteV1> | REQUIRED | NO | minItems 0, maxItems 200 | unique routeId |
| findings | array<LegalSourceFindingV1> | REQUIRED | NO | minItems 0, maxItems 200 | unique category |
| evidence | array<LegalSourceEvidenceV1> | REQUIRED | NO | minItems 0, maxItems 200 | unique evidenceId |
| requiredUserInputs | array<LegalSourceQuestionV1> | REQUIRED | NO | minItems 0, maxItems 100 | route references resolve |
| sourceWarnings | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–2,000 | safe source warnings |

### ConceptLegalValidationBatchInputV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| textContents | array<TextContentV1> | REQUIRED | NO | minItems 1, maxItems 64; aggregate chunks max 64 | canonical JSON batch text |
| validationMode | string | REQUIRED | NO | `GUARDRAIL_BATCH` | current Journey mode |
| guardrailVersionId | integer | REQUIRED | NO | 1–9,223,372,036,854,775,807 | exact persisted guardrail version |

### ConceptLegalValidationTraceV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| guardrailType | string | REQUIRED | NO | 1–128, blank 금지 | supplied guardrail section |
| constraint | string | REQUIRED | NO | 1–4,000, blank 금지 | supplied constraint |
| implementation | string | REQUIRED | NO | 1–4,000, blank 금지 | concept implementation trace |

### ConceptLegalValidationItemV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| candidateKey | string | REQUIRED | NO | LocalKey 1–64 | exact input candidateKey |
| status | string enum | REQUIRED | NO | `PASS`, `FAIL_LEGAL` | legal eligibility only |
| reasons | array<string> | REQUIRED | NO | minItems 0, maxItems 100; each 1–2,000 | bounded reasons |
| violatedStructureKeys | array<string> | REQUIRED | NO | minItems 0, maxItems 100; unique | exact concept structure keys |
| legalTrace | array<ConceptLegalValidationTraceV1> | REQUIRED | NO | minItems 0, maxItems 200 | supplied guardrail trace only |

### ConceptLegalValidationBatchResultV1

| Field | JSON type | Presence | Nullable | Bounds/enum | Semantic rule |
|---|---|---|---|---|---|
| validations | array<ConceptLegalValidationItemV1> | REQUIRED | NO | minItems 1, maxItems 10 | exact input candidateKey set; no missing, duplicate, unknown key |

## 17. P2.6 fixture readiness matrix

`required`는 fixture/validator coverage gate에 포함되어야 한다는 뜻이다. 현재 named field-table schema는 기존 65개, Idea Legal/Concept Legal 12개, 현재 Idea Origin metadata 2개를 합한 총 79개다. 실제 실행 결과는 이 narrative에 복사하지 않고 validator 출력과 [fixture root](fixtures/internal-ai-v1/README.md)를 verification source로 사용한다.

| Schema | Positive fixture required | Negative fixture required | Critical invariant | Public/domain matching source | P2.6 file name direction |
|---|---|---|---|---|---|
| InternalExecutionRequestV1 | YES | YES | discriminator/hash/header match | TaskRun contract | `common/execution-request.*.json` |
| InternalExecutionSuccessResponseV1 | YES | YES | exact echo and adopted validation | TaskRunPublicView | `common/execution-success.*.json` |
| InternalErrorResponseV1 | YES | YES | single safe error | Status/Error contract | `common/error-response.*.json` |
| InternalErrorBodyV1 | YES | YES | nullable trusted identifiers | Status/Error contract | `common/error-body.*.json` |
| InternalErrorDetailV1 | YES | YES | no raw rejected value | Status/Error contract | `common/error-detail.*.json` |
| UsageSummaryV1 | YES | YES | total equality/null usage | internal-only | `common/usage.*.json` |
| WarningV1 | YES | YES | registered source keys | Provenance contract | `common/warning.*.json` |
| TextContentV1 | YES | YES | content/chunk aggregate 64 | IdeaSource extraction | `common/text-content.*.json` |
| TextChunkV1 | YES | YES | index/count/hash | IdeaSource extraction | `common/text-chunk.*.json` |
| RequestLocalReferenceV1 | YES | YES | namespace and uniqueness | Domain exact references | `common/local-reference.*.json` |
| ProvenanceItemV1 | YES | YES | category/source resolution | Provenance contract | `common/provenance.*.json` |
| ExternalSourceReferenceV1 | YES | YES | external vs Storage URL | Legal source public view | `common/external-source.*.json` |
| IdeaStatementItemV1 | YES | YES | fact/assumption/source semantics | Public IdeaStatementItem | `common/idea-statement.*.json` |
| LegalFindingV1 | YES | YES | finding/source resolution | LegalFindingView | `shared/legal-finding.*.json` |
| LegalSourceReferenceV1 | YES | YES | MOLEG authority rule | LegalSourceReferenceView | `shared/legal-source.*.json` |
| SourceCoverageV1 | YES | YES | degraded iff missing | LegalReview model | `shared/source-coverage.*.json` |
| ConceptProposalV1 | YES | YES | output key and no selection | ConceptVersionView | `shared/concept-proposal.*.json` |
| ConceptSnapshotV1 | YES | YES | exact input version identity | ConceptVersionView | `shared/concept-snapshot.*.json` |
| AssessmentDimensionV1 | YES | YES | non-probability rating | QuickAssessmentRunView | `shared/assessment-dimension.*.json` |
| EvidenceItemV1 | YES | YES | source resolution | Provenance contract | `shared/evidence-item.*.json` |
| DetailedFindingV1 | YES | YES | type category/evidence | DetailedAnalysisRunView | `shared/detailed-finding.*.json` |
| FinancialExplanationV1 | YES | YES | no deterministic overwrite | Analysis model | `shared/financial-explanation.*.json` |
| PersonaCardProposalV1 | YES | YES | three layers/disclosure | PersonaCardVersionView | `shared/persona-card.*.json` |
| PersonaCardSnapshotV1 | YES | YES | exact isolated input card | PersonaCardVersionView | `shared/persona-card-snapshot.*.json` |
| InterviewQuestionAnswerV1 | YES | YES | synthetic answer/source question | PersonaInterviewView | `shared/interview-answer.*.json` |
| SynthesisResponseItemV1 | YES | YES | adopted interview sources | InterviewSynthesisView | `shared/synthesis-item.*.json` |
| MarketingAssetProposalV1 | YES | YES | text only/no probability | MarketingAssetVersionView | `shared/marketing-asset.*.json` |
| MarketingComparisonAssessmentV1 | YES | YES | relative non-statistical | MarketingComparisonRunView | `shared/marketing-comparison.*.json` |
| ReportSectionV1 | YES | YES | structured persisted section | FinalReportVersionView | `shared/report-section.*.json` |
| ReportFindingV1 | YES | YES | category/source separation | FinalReportVersionView | `shared/report-finding.*.json` |
| MarketAnalysisInputV1 | YES | YES | unique evidence keys/unknown reject | DetailedAnalysis request | `shared/market-analysis-input.*.json` |
| BusinessModelAnalysisInputV1 | YES | YES | bounded constraints/evidence | DetailedAnalysis request | `shared/business-model-input.*.json` |
| TechnicalOperationAnalysisInputV1 | YES | YES | bounded operating constraints | DetailedAnalysis request | `shared/technical-operation-input.*.json` |
| FinancialMetricV1 | YES | YES | canonical decimal and currency/unit XOR | Financial public contract | `shared/financial-metric.*.json` |
| FinancialAnalysisInputV1 | YES | YES | Spring deterministic snapshot | Financial public contract | `shared/financial-analysis-input.*.json` |
| MarketAnalysisResultV1 | YES | YES | MARKET findings only | DetailedAnalysisRunView | `shared/market-analysis-result.*.json` |
| BusinessModelAnalysisResultV1 | YES | YES | BUSINESS_MODEL findings only | DetailedAnalysisRunView | `shared/business-model-result.*.json` |
| TechnicalOperationAnalysisResultV1 | YES | YES | TECHNICAL_OPERATION findings only | DetailedAnalysisRunView | `shared/technical-operation-result.*.json` |
| FinancialAnalysisResultV1 | YES | YES | explanation sole ownership/no deterministic fields | Financial public contract | `shared/financial-analysis-result.*.json` |
| PersonaInterviewQuestionV1 | YES | YES | unique QUESTION key | Persona Interview request | `shared/persona-interview-question.*.json` |
| IncludedInterviewResultV1 | YES | YES | same-study adopted interview | Interview Synthesis model | `shared/included-interview-result.*.json` |
| MarketingAssetSnapshotV1 | YES | YES | exact text asset/version key | Marketing comparison request | `shared/marketing-asset-snapshot.*.json` |
| MarketingComparisonDimensionV1 | YES | YES | unique comparison dimension key | Marketing comparison request | `shared/marketing-comparison-dimension.*.json` |
| IdeaInterpretationInputV1 | YES | YES | verified chunks/local sources | interpretation create request | `tasks/idea-interpretation.input.*.json` |
| IdeaInterpretationResultV1 | YES | YES | facts/assumptions/constraints | IdeaInterpretationResultView | `tasks/idea-interpretation.result.*.json` |
| IdeaInputMetadataV1 | YES | YES | missing/proposed source and lock semantics | Idea Origin draft | `tasks/idea-interpretation.result.*.json` |
| IdeaClarificationQuestionV1 | YES | YES | target/requirement/question exact structure | Idea Origin draft | `tasks/idea-interpretation.result.*.json` |
| LegalReviewInputV1 | YES | YES | exact confirmed idea/KR | LegalReviewRun model | `tasks/legal-review.input.*.json` |
| LegalReviewResultV1 | YES | YES | degraded/authority/legal enum | LegalReviewRunView | `tasks/legal-review.result.*.json` |
| LegalConfirmedFactV1 | YES | YES | confirmed value snapshot | IdeaOriginVersion | `tasks/idea-legal-precheck.input.*.json` |
| LegalSourcePipelineInputV1 | YES | YES | TEXT/ko-KR and registry identity | LegalPrecheck TaskRun | `tasks/idea-legal-precheck.input.*.json` |
| LegalSourceRouteV1 | YES | YES | unique registry route | Legal source result | `tasks/idea-legal-precheck.result.*.json` |
| LegalSourceReasoningV1 | YES | YES | evidence references resolve | Legal source result | `tasks/idea-legal-precheck.result.*.json` |
| LegalSourceFindingV1 | YES | YES | unique category | Legal source result | `tasks/idea-legal-precheck.result.*.json` |
| LegalSourceEvidenceV1 | YES | YES | official source identity | Legal source result | `tasks/idea-legal-precheck.result.*.json` |
| LegalSourceQuestionV1 | YES | YES | route references resolve | Legal source result | `tasks/idea-legal-precheck.result.*.json` |
| LegalSourcePipelineResultV1 | YES | YES | task/registry/reference integrity | LegalSourcePipelineContract | `tasks/idea-legal-precheck.result.*.json` |
| ConceptLegalValidationBatchInputV1 | YES | YES | canonical batch and guardrail identity | ConceptJourneyService | `tasks/concept-legal-validation.input.*.json` |
| ConceptLegalValidationTraceV1 | YES | YES | supplied guardrail trace | Concept eligibility | `tasks/concept-legal-validation.result.*.json` |
| ConceptLegalValidationItemV1 | YES | YES | candidateKey exact once | Concept eligibility | `tasks/concept-legal-validation.result.*.json` |
| ConceptLegalValidationBatchResultV1 | YES | YES | exact candidate set | ConceptJourneyService | `tasks/concept-legal-validation.result.*.json` |
| ConceptGenerationInputV1 | YES | YES | passing legal gate/count | ConceptGenerationRun model | `tasks/concept-generation.input.*.json` |
| ConceptGenerationResultV1 | YES | YES | output proposals/no selection | ConceptCandidate/Version | `tasks/concept-generation.result.*.json` |
| QuickAssessmentInputV1 | YES | YES | exact one concept | QuickAssessmentRun model | `tasks/quick-assessment.input.*.json` |
| QuickAssessmentResultV1 | YES | YES | no shortlist decision | QuickAssessmentRunView | `tasks/quick-assessment.result.*.json` |
| DetailedAnalysisInputV1 | YES | YES | exactly one discriminator section | DetailedAnalysis request | `tasks/detailed-analysis.input.*.json` |
| DetailedAnalysisResultV1 | YES | YES | deterministic boundary | DetailedAnalysisRunView | `tasks/detailed-analysis.result.*.json` |
| PersonaCardGenerationInputV1 | YES | YES | exact study/selection/concept | Persona generation request | `tasks/persona-card-generation.input.*.json` |
| PersonaCardGenerationResultV1 | YES | YES | three layers/no statistics | PersonaCardVersionView | `tasks/persona-card-generation.result.*.json` |
| PersonaInterviewInputV1 | YES | YES | one Persona only | PersonaInterview model | `tasks/persona-interview.input.*.json` |
| PersonaInterviewResultV1 | YES | YES | synthetic disclosure | PersonaInterviewView | `tasks/persona-interview.result.*.json` |
| InterviewSynthesisInputV1 | YES | YES | same-study adopted set | InterviewSynthesis model | `tasks/interview-synthesis.input.*.json` |
| InterviewSynthesisResultV1 | YES | YES | source originals preserved | InterviewSynthesisView | `tasks/interview-synthesis.result.*.json` |
| MarketingGenerationInputV1 | YES | YES | exact workspace/persona evidence | Marketing generation request | `tasks/marketing-generation.input.*.json` |
| MarketingGenerationResultV1 | YES | YES | text only/no probability | MarketingAssetVersionView | `tasks/marketing-generation.result.*.json` |
| MarketingComparisonInputV1 | YES | YES | distinct assets/dimensions | Marketing comparison request | `tasks/marketing-comparison.input.*.json` |
| MarketingComparisonResultV1 | YES | YES | non-statistical comparison | MarketingComparisonRunView | `tasks/marketing-comparison.result.*.json` |
| FinalReportGenerationInputV1 | YES | YES | immutable inputs/user decision | FinalReport generation request | `tasks/final-report.input.*.json` |
| FinalReportGenerationResultV1 | YES | YES | decision equality/no binary | FinalReportVersionView | `tasks/final-report.result.*.json` |
