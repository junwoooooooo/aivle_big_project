# Journey 데이터 계약 (단계별로 오가는 값의 형태)

- Status: **AS_BUILT** — Spring `record` 뷰와 AI Pydantic 모델(`ai/app/models/journey.py`)에서 직접 읽음
- Baseline: 2026-08-05 / `581234a`
- 목적: **값의 스키마**. 엔드포인트·헤더·상태코드는 [`JOURNEY_API_SPEC.md`](JOURNEY_API_SPEC.md),
  단계 이동 조건은 [`JOURNEY_STATE_MACHINE_SPEC.md`](JOURNEY_STATE_MACHINE_SPEC.md)를 볼 것.

> **우선순위:** 코드 > 이 문서. `record` 시그니처와 Pydantic 모델이 정본이다.

---

## 1. 값이 흐르는 세 층

같은 "컨셉"이라도 층마다 형태가 다르다. 혼동이 잦으므로 먼저 구분한다.

```
[브라우저]  ──ApiResponse.data──▶  *View  (Spring record, 아래 §2)
[Spring]    ──taskInput chunk──▶  input.textContents[].chunks[].text  (§3)
[AI 서버]   ──result──────────▶  Pydantic 모델 (§4)  → Spring validator가 재검증 → 엔티티 컬럼
```

- **§2 (View)**: 프론트가 보는 형태. `JsonNode` 필드는 **DB에 저장된 JSON 컬럼을 그대로 통과**시킨 것이다.
- **§3 (내부 요청)**: AI 서버는 **Spring이 추출한 텍스트만** 받는다. 파일 bytes·presigned URL·JWT·
  엔티티 직렬화는 금지되어 있다.
- **§4 (AI 결과)**: `extra="forbid"`. 필드가 하나라도 남거나 빠지면 전체가 거부된다.

---

## 2. 프론트가 받는 값 — `*View` 스키마

`Long`은 JSON에서 number, `LocalDateTime`은 ISO-8601 문자열, `JsonNode`는 **임의 JSON**
(대응하는 AI 결과 조각이 §4에 있으면 그 형태다).

### 2-1. 아이디어

```
IdeaSourceView {
  id: Long, title: string|null, sourceType: "TEXT"|"FILE",
  originalText: string|null, originalFileReference: string|null, createdAt: datetime
}

IdeaVersionView {
  id: Long, versionNumber: int, normalizedDescription: string,
  facts: JsonNode, assumptions: JsonNode, constraints: JsonNode, openQuestions: JsonNode,
  readiness: "UNDER_SPECIFIED"|"APPROPRIATE"|"OVER_SPECIFIED",
  confirmed: boolean, createdAt: datetime
}

InterpretationView {
  id: Long, ideaSourceId: Long,
  state: "PENDING"|"RUNNING"|"SUCCEEDED"|"FAILED",
  taskRunId: string|null, retryable: boolean,
  result: JsonNode,            // = IdeaInterpretationResult (§4-1)
  error: string|null,
  ideaVersion: IdeaVersionView|null,
  createdAt: datetime, completedAt: datetime|null
}

LegalView {                    // 호환 경로 전용 (현재 여정 미사용)
  id: Long, state: string, taskRunId: string|null,
  legalStatus: string|null, result: JsonNode, sourceVerified: boolean,
  ideaVersionId: Long, createdAt: datetime, completedAt: datetime|null
}
```

`facts` / `assumptions` / `constraints` / `openQuestions`는 모두 **문자열 배열**이다
(AI 쪽 `list[str]`이 그대로 저장된다).

### 2-2. Idea Origin (해석 결과를 사용자가 확정하는 작업공간)

```
WorkspaceView {
  draft: OriginView|null,      // 상태 DRAFT
  confirmed: OriginView|null,  // 상태 CONFIRMED
  questions: QuestionView[],
  readiness: ReadinessView
}

OriginView {
  id: Long, versionNumber: int, state: "DRAFT"|"CONFIRMED",
  sourceIdeaVersionId: Long, snapshot: JsonNode,   // = IdeaOriginDraft (§4-1)
  ...
}

QuestionView {
  id: Long, originDraftVersionId: Long, targetField: string,
  requirement: "REQUIRED_FOR_IDEA_ORIGIN"|"REQUIRED_FOR_LEGAL_PRECHECK",
  ...   // question / reason / status / answer / answerSource
}

ReadinessView {
  ideaOrigin:    "READY"|"NEEDS_INPUT"|"BLOCKED",
  legalPrecheck: "READY"|"NEEDS_INPUT"|"BLOCKED",
  conceptBuild:  "READY"|"NEEDS_INPUT"|"BLOCKED"
}
```

**`ReadinessView`가 화면 진행의 신호등이다.** 계산 규칙은 상태 전이 명세 §3에 있다.

### 2-3. 법률 사전점검

```
StartView { runId: Long, taskRunId: string, state: string, retryable: boolean,
            ideaOriginVersionId: Long, inputSnapshotHash: string }

RunView   { id, taskRunId, state: "QUEUED"|"RUNNING"|"SUCCEEDED"|"FAILED",
            retryable, errorCode: string|null, ideaOriginVersionId,
            inputSnapshotHash, registryVersion, promptVersion, schemaVersion }

GuardrailView {
  id: Long, versionNumber: int,
  hardConstraints: JsonNode, prohibitedPatterns: JsonNode,
  conditionalConstraints: JsonNode, requiredDisclosures: JsonNode,
  requiredOperationalControls: JsonNode      // 전부 배열
}

VersionView {
  id, versionNumber, ideaOriginVersionId,
  status: "PASS"|"PASS_WITH_CONDITIONS"|"REVISION_REQUIRED"
        |"PROHIBITED"|"INSUFFICIENT_INFORMATION"|"EXPERT_REVIEW_REQUIRED",
  sourceStatus: string,          // SOURCE_VERIFIED / SOURCE_PARTIAL / REGISTRY_GAP 등
  summary: string,
  findings: JsonNode, evidence: JsonNode,
  requiredUserInputs: JsonNode,
  revisionSuggestions: JsonNode, // [{ targetField, proposedValue }, ...] — index로 수락한다
  conceptBuilderAllowed: boolean,
  sourceVerified: boolean,
  registryVersion: string,
  guardrails: GuardrailView|null
}

CurrentView { run: RunView|null, version: VersionView|null, stale: boolean }
RevisionApplyView { origin: WorkspaceView, precheck: StartView, appliedSuggestionCount: int }
```

**`stale: true`의 뜻:** 이 결과가 만들어진 Origin이 현재 확정 Origin이 아니거나,
현재 입력의 canonical hash가 달라졌다는 것. **화면은 결과를 유효한 것으로 취급하면 안 된다.**

**`revisionSuggestions`의 index가 API 인자다.** 배열 순서가 곧 계약이므로 저장된 순서를
프론트가 재정렬하면 엉뚱한 제안이 반영된다.

### 2-4. 컨셉

```
BatchView {
  id: Long,
  state: "GENERATING"|"VALIDATING_ORIGIN"|"VALIDATING_LEGAL"|"COMPLETED"|"NEEDS_INPUT"|"FAILED",
  currentRound: int, inspectedCandidates: int, eligibleCandidates: int,
  targetEligibleCount: int, maxReplacementRounds: int, maxInspectedCandidates: int,
  needsInput: JsonNode,          // 실패 사유 문자열 배열 (중복 제거됨)
  errorCode: string|null, retryable: boolean, stale: boolean,
  ideaOriginVersionId: Long, legalGuardrailSetId: Long,
  inputSnapshotHash: string,
  concepts: ConceptView[]
}

ConceptView {
  id: Long, conceptId: Long, displayOrder: int,
  name, oneLineSummary, targetCustomer, problem, solution,
  valueProposition, revenueModel: string,
  keyFeatures: JsonNode, differentiators: JsonNode,
  assumptions: JsonNode, risks: JsonNode,
  ideaVersionId: Long,
  eligibilityStatus: "PENDING"|"ELIGIBLE"|"REJECTED",
  targetSegment: JsonNode, positioning: string, pricing: JsonNode,
  channels: JsonNode, operatingModel: JsonNode, newBusinessActivities: JsonNode,
  originTrace: JsonNode,   // [{ structureKey, sourceValue, conceptValue }]
  legalTrace: JsonNode     // [{ guardrailType, constraint, implementation }]
}

QuickAssessmentView {
  conceptVersionId: Long, conceptName: string,
  market: int, customerValue: int, feasibility: int,
  differentiation: int, revenuePotential: int, legalRisk: int,
  overallScore: BigDecimal,          // ← JSON에서는 number. §5의 부동소수점 주의
  summary: string, strengths: JsonNode, weaknesses: JsonNode
}
QuickView { id, state, ideaVersionId, assessments: QuickAssessmentView[], error, completedAt }

ShortlistView { id, ideaVersionId, conceptVersionIds: Long[], reason: string, createdAt }

DetailedItemView {
  conceptVersionId: Long, conceptName: string,
  marketAnalysis, customerAnalysis, businessModelAnalysis,
  operationAnalysis, riskAnalysis, recommendation: string,
  assumptions: JsonNode, researchNeeds: JsonNode
}

FinancialView {                       // 전부 서버 계산값. AI가 만들지 않는다
  conceptVersionId: Long,
  unitPrice, variableCostPerCustomer, monthlyFixedCost, initialInvestment: BigDecimal,
  monthlyCustomers: int,
  monthlyRevenue, monthlyVariableCost, monthlyTotalCost, monthlyOperatingProfit: BigDecimal,
  breakEvenCustomers: int, paybackMonths: BigDecimal
}
DetailedView { id, state, ideaVersionId, analyses: DetailedItemView[],
               financials: { [conceptVersionId]: FinancialView }, error, completedAt }

SelectionView { id, ideaVersionId, conceptVersionId, conceptName, reason, createdAt }
```

**`DetailedView.financials`는 Map이다.** JSON에서는 `conceptVersionId`를 **문자열 키**로 갖는
객체가 된다 (`{"41": {...}}`). 배열이 아니다.

### 2-5. 페르소나·인터뷰

```
StudyView {
  id, conceptVersionId, conceptName,
  state: "DRAFT"|"GENERATING"|"READY"|"FAILED",
  syntheticNotice: string,      // ← 경계 표시. 제거 금지 (§6)
  error, createdAt, completedAt
}

PersonaView {
  id: Long, personaCardId: Long, displayOrder: int,
  name: string, shortLabel: string,
  roleAndContext: JsonNode,      // { role, situation, goals[], constraints[] }
  problemAndNeeds: JsonNode,     // { problems[], unmetNeeds[], desiredOutcomes[] }
  behaviorAndDecision: JsonNode, // { currentBehavior[], decisionCriteria[], barriers[], informationSources[] }
  interviewFocus: JsonNode,      // string[]
  selected: boolean,
  synthetic: boolean             // ← 항상 true. 경계 표시 (§6)
}

MessageView { sequenceNumber: int,
              category: "ROLE_AND_CONTEXT"|"PROBLEM_AND_NEEDS"|"BEHAVIOR_AND_DECISION",
              question: string, answer: string }

InterviewView { id, personaCardVersionId, personaDisplayOrder, personaName,
                state: "PENDING"|"RUNNING"|"SUCCEEDED"|"FAILED",
                messages: MessageView[], error, completedAt }

SynthesisView { id, state,
                commonThemes, conflictingViews, criticalNeeds,
                decisionBarriers, implications, researchNeeds: JsonNode,  // 전부 string[]
                error, completedAt }
```

### 2-6. 마케팅·최종 리포트

```
AssetView { id: Long, versionId: Long,
            assetType: "POSITIONING"|"CORE_MESSAGE"|"SLOGAN"|"SOCIAL_COPY"
                      |"LANDING_HERO"|"EMAIL_COPY"|"CHANNEL_PLAN",
            displayOrder: int, title: string, content: JsonNode,
            selected: boolean, version: int }

WorkspaceView { id, state, conceptVersionId, conceptName,
                strategy: JsonNode,      // = MarketingGenerationResult (§4-6)
                assets: AssetView[], error, completedAt }

ComparisonView { id, state, result: JsonNode, error, completedAt }
                                 // result = MarketingComparisonResult (§4-6)

ReportView { id, state, version: int|null,
             result: JsonNode,   // = FinalReportResult (§4-7)
             aiDecision: "GO"|"CONDITIONAL_GO"|"REWORK"|"HOLD"|"STOP"|null,
             userDecision: string|null,
             userDecisionReasons: JsonNode,   // string[]
             error, generatedAt }
```

`aiDecision`과 `userDecision`은 **별개 필드다.** AI 판단을 사용자 결정이 덮어쓰지 않는다.

---

## 3. Spring → AI 요청의 형태 (내부 v1)

`POST /internal/v1/ai/executions`. 정본은 `INTERNAL_AI_API_V1_CONTRACT.md`.
데이터 형태만 옮긴다.

```json
{
  "contractVersion": "1.0",
  "taskType": "IDEA_INTERPRETATION",
  "taskSchemaVersion": "1.0",
  "taskRunId": "...", "taskAttemptId": "...", "correlationId": "...",
  "deadlineAt": "2026-08-05T09:00:00Z",
  "canonicalInputHash": "sha256:...",
  "locale": "ko-KR",
  "input": {
    "textContents": [{
      "contentKey": "idea-source",
      "contentType": "PLAIN_TEXT",
      "language": "ko-KR",
      "totalCharacters": 1234,
      "contentHash": "sha256:...",
      "chunks": [{ "index": 0, "text": "...", "characterCount": 1234, "chunkHash": "sha256:..." }]
    }]
  }
}
```

**도메인 값은 전부 `chunks[].text` 안에 JSON 문자열로 직렬화되어 들어간다.**
(예: `taskInput("final-report-sources", input.toString())`) — AI 서버에는 별도 필드가 없다.

### 3-1. 양쪽이 강제하는 크기 한계

| 항목 | 값 | 강제하는 곳 |
|---|---|---|
| 요청/응답 JSON | ≤ 2 MiB | `InternalAiExecutionClient.MAX_JSON_BYTES`, AI `main.py` 미들웨어 |
| `textContents` 개수 | 1–64 | `executions.py validate_text_contents` |
| content당 chunk | 1–64, **총합도 64 이하** | 같은 곳 |
| chunk 텍스트 | 1–16,384자 (Spring은 16,000 코드포인트로 절단) | 같은 곳 |
| `input` 스냅샷 | ≤ 2 MiB, 유효 JSON | `TaskRunService.validateCreation` |
| `maxAttempts` | 1–20 | 같은 곳 |
| `deadlineAt` | `…Z`, **미래여야 함** | `executions.py` — 과거면 `DEADLINE_EXCEEDED` |
| `X-Correlation-Id` | 본문 `correlationId`와 **일치** | `executions.py` |

---

## 4. AI가 돌려주는 값 — 결과 스키마

모두 `StrictResult` (`extra="forbid"`). Spring 쪽 validator도 **최상위 필드 집합이 정확히
일치**해야 통과시킨다 (`Set.copyOf(result.propertyNames()).equals(expected)`).
**프롬프트가 필드를 하나 더 만들어도 전체가 거부된다.**

### 4-1. `IDEA_INTERPRETATION` → `IdeaInterpretationResult`

```
originalSourceSummary: str
normalizedDescription: str
facts, assumptions, constraints, openQuestions: list[str]
readiness: "UNDER_SPECIFIED"|"APPROPRIATE"|"OVER_SPECIFIED"
warnings, evidenceNeeds: list[str]
originDraft: IdeaOriginDraft
fieldMetadata: list[IdeaInputMetadata]
clarificationQuestions: list[IdeaClarificationQuestion]
```

```
IdeaOriginDraft {
  productServiceDescription: str|null
  problem: list[str]
  target: { customerTypes: list[str], segment: str|null,
            situation: str|null, needs: list[str] } | null
  solution, coreValue: list[str]
  primaryCategory, targetRegion: str|null
  fixedValues: list[{ field: str, value: str }]
  confirmedValues: dict[str, Any]
  assumptions: list[str]
  pricingIntent, revenueModelIntent, salesChannelIntent, knownUnitCost: str|null
  alternatives, knownCompetitors: list[str]
  differentiationIntent: str|null
  internalConstraints: list[str]
}

IdeaInputMetadata {
  key: str
  sourceType: "USER_CONFIRMED"|"AI_PROPOSED"
  requiredForStages: list["IDEA_ORIGIN"|"LEGAL_PRECHECK"|"CONCEPT_BUILD"]
  status: "MISSING"|"AI_PROPOSED"|"USER_CONFIRMED"
  locked: bool
  fallbackPolicy: "NO_FALLBACK"|"AI_MAY_PROPOSE"|"BLOCK_STAGE"
}

IdeaClarificationQuestion {
  targetField: str
  requirement: "REQUIRED_FOR_IDEA_ORIGIN"|"REQUIRED_FOR_LEGAL_PRECHECK"
  question: str
  reason: str
}
```

**교차 검증 (양쪽 모두 강제):** `originDraft`의 필수 8필드
(`productServiceDescription, problem, target, solution, coreValue, primaryCategory,
targetRegion, fixedValues`) 중 비어 있는 것이 있으면, 그 `targetField`에 대한
`clarificationQuestions` 항목이 **반드시 있어야 한다.** 없으면 거부
(AI: `idea_missing_clarification`, Spring: `AI_RESULT_INVALID`).

### 4-2. `IDEA_LEGAL_PRECHECK` (호환 경로 `LegalReviewResult`)

```
status: "PASS"|"PASS_WITH_CONDITIONS"|"REVISION_REQUIRED"
      |"PROHIBITED"|"INSUFFICIENT_INFORMATION"|"EXPERT_REVIEW_REQUIRED"
summary: str
issues, conditions, prohibitedElements, researchNeeds: list[str]
sourceVerified: Literal[False]     // ← 반드시 false. true면 계약 위반
disclaimer: str                    // ← 경계 표시 (§6)
```

여정 본선의 `IDEA_LEGAL_PRECHECK`는 `ai/app/legal/pipeline.py`(법제처 실연동)를 타며,
그 결과가 `LegalPrecheckVersion` + `LegalGuardrailSet`(§2-3)으로 물질화된다.

### 4-3. `CONCEPT_GENERATION` → `ConceptGenerationResult`

```
concepts: list[ConceptCandidate]

ConceptCandidate {
  conceptName: str
  targetSegment: dict
  positioning: str
  featureSet: list[str]
  pricing: dict
  revenueModel: dict
  channels: list[str]
  operatingModel: dict
  newAssumptions: list[str]
  newBusinessActivities: list[str]
  originTrace: list[{ structureKey: str, sourceValue: Any, conceptValue: Any }]
  legalTrace:  list[{ guardrailType: str, constraint: str, implementation: str }]
}
```

### 4-4. `CONCEPT_LEGAL_VALIDATION`

**두 모드가 있고 결과 형태가 다르다.**

단건 (`validationMode=GUARDRAIL`) → `ConceptLegalValidationResult`:
```
status: "PASS"|"FAIL_LEGAL"
reasons, violatedStructureKeys: list[str]
legalTrace: list[ConceptLegalTrace]
```

배치 (`validationMode=GUARDRAIL_BATCH`, 여정 본선) → `ConceptLegalValidationBatchResult`:
```
validations: list[{                       // min_length=1
  candidateKey: str (non-blank)
  status: "PASS"|"FAIL_LEGAL"
  reasons, violatedStructureKeys: list[str]
  legalTrace: list[{ guardrailType, constraint, implementation }]
}]
```

**`candidateKey`는 Spring이 보낸 값과 대조된다.** `ConceptJourneyService`가
`expectedKeys`(=`candidate-<draftId>`)와 응답 키 집합을 비교하고, 어긋나면 결과 전체를
거부한다 — 환각 방지 장치다.

배치 입력 형태:
```
ConceptLegalValidationBatchInput {
  guardrails: { hardConstraints, prohibitedPatterns, conditionalConstraints,
                requiredDisclosures, requiredOperationalControls: list[Any] }
  lockedValues: dict[str, Any]
  conceptDrafts: list[ConceptCandidate + candidateKey]   // min_length=1
}
```

### 4-5. `QUICK_ASSESSMENT` / `DETAILED_ANALYSIS`

```
QuickAssessmentResult { assessments: list[{
  conceptVersionId: int, market, customerValue, feasibility,
  differentiation, revenuePotential, legalRisk: int,
  overallScore: float, summary: str, strengths, weaknesses: list[str] }] }

DetailedAnalysisResult { analyses: list[{
  conceptVersionId: int,
  marketAnalysis, customerAnalysis, businessModelAnalysis,
  operationAnalysis, riskAnalysis, recommendation: str,
  assumptions, researchNeeds: list[str] }] }
```

`conceptVersionId`는 **Spring이 보낸 ID와 대조**된다. 재무 수치(`FinancialView`)는
AI 결과가 아니라 **서버가 사용자 입력으로 계산**한 값이다.

### 4-6. 페르소나·인터뷰·마케팅

```
PersonaCardGenerationResult { personas: list[{
  name, shortLabel: str,
  roleAndContext:      { role, situation: str, goals, constraints: list[str] },
  problemAndNeeds:     { problems, unmetNeeds, desiredOutcomes: list[str] },
  behaviorAndDecision: { currentBehavior, decisionCriteria, barriers, informationSources: list[str] },
  interviewFocus: list[str] }] }

PersonaInterviewResult { messages: list[{
  category: "ROLE_AND_CONTEXT"|"PROBLEM_AND_NEEDS"|"BEHAVIOR_AND_DECISION",
  question, answer: str }] }

InterviewSynthesisResult {
  commonThemes, conflictingViews, criticalNeeds,
  decisionBarriers, implications, researchNeeds: list[str] }

MarketingGenerationResult {
  positioning, coreMessage: str(non-blank)
  slogans: list[str]                       // min 1
  personaMessages: list[{ personaId: int(strict), personaName, message, rationale }]  // min 1
  channelPlan: list[{ channel, objective, message }]                                   // min 1
  socialCopies: list[str]                  // min 1
  emailCopies: list[str]                   // 기본 []
  landingHero: { headline, subheadline, cta }
  assumptions, warnings: list[str] }

MarketingComparisonResult { comparisons: list[{        // min 1
  assetId: int(strict), assetVersionId: int(strict),
  assetType: "POSITIONING"|"CORE_MESSAGE"|"SLOGAN"|"SOCIAL_COPY"
            |"LANDING_HERO"|"EMAIL_COPY"|"CHANNEL_PLAN",
  personaFit: list[{ personaId: int(strict), personaName,
                     fit: "LOW"|"MEDIUM"|"HIGH", rationale }],   // min 1
  strengths, risks, recommendedContexts: list[str],              // 각 min 1
  selectionSuggestion: str }] }
```

**`personaId` / `assetId` / `assetVersionId`는 `strict=True` 정수다.** 문자열 `"12"`나
실수 `12.0`은 거부된다. 그리고 `MarketingReportJourneyService.validateComparison()`이
**보낸 ID 집합과 대조**한다 — 환각 방지의 모범 사례.

### 4-7. `FINAL_REPORT_GENERATION` → `FinalReportResult`

```
executiveSummary: str(non-blank)
idea, legalReview, selectedConcept, analysis,
personaInsights, marketingStrategy: dict[str, Any]
facts, assumptions, researchNeeds, risks: list[str(non-blank)]
decision: "GO"|"CONDITIONAL_GO"|"REWORK"|"HOLD"|"STOP"
decisionReasons: list[str]     // min 1
nextActions: list[str]         // min 1
```

---

## 5. 값을 만들 때 반드시 지킬 것

### 5-1. task input에 **부동소수점 금지**

canonical input hash는 Spring `CanonicalInputHasher`와 AI `executions.py canonical_hash`가
**독립적으로 계산해 대조**한다. 규칙:

1. 해시 대상은 `{contractVersion, input, locale, taskSchemaVersion, taskType}` **5개뿐**
2. 객체 키는 **NFC 정규화 후 코드포인트 순 정렬**. 정규화 후 키가 충돌하면 에러
3. 문자열도 NFC 정규화
4. 구분자에 공백 없음 (`,` `:`)
5. **부동소수점 숫자 금지** — Spring이
   `"floating-point JSON numbers are not canonical task input"`으로 던진다

→ input에 `0.35`를 넣으면 **컴파일도 테스트도 통과하고 런타임에만 깨진다.**
비율이 필요하면 정수 basis point(`35`)나 문자열로 넣을 것.

> 주의: 이 금지는 **AI로 보내는 input**에 적용된다. AI가 **돌려주는** 결과의
> `overallScore: float`는 무관하다.

### 5-2. AI 결과에 실을 수 없는 필드

`TaskRunWorker.rejectForbiddenFields()`가 결과 JSON 전체를 재귀 순회하며 아래 키가
하나라도 있으면 거부한다.

```
storageUrl, objectKey, presignedUrl, localPath, fileBytes,
base64, prompt, rawProviderResponse, credential
```

프롬프트나 provider 원본 응답이 결과에 섞여 나가는 것을 구조적으로 막는 장치다.

### 5-3. 성공 응답 봉투는 정확히 12필드

`InternalAiExecutionClient.SUCCESS_FIELDS`:

```
contractVersion, taskType, taskSchemaVersion, taskRunId, taskAttemptId,
correlationId, canonicalInputHash, resultSchemaVersion, result,
warnings, provenance, usage
```

하나라도 빠지거나 남으면 `RESULT_UNKNOWN_FIELD`로 거부.

### 5-4. 새 TaskType을 추가할 때 같이 고쳐야 하는 곳

1. `ai/app/api/executions.py`의 TaskType 목록 — **두 군데** 있다
2. `_load_prompts.folders`와 `model_types` — **별개 dict**, 둘 다
3. `ai/app/models/journey.py`에 결과 모델
4. `ai/prompts/<folder>/{system.md,user.md}`
5. Spring 쪽 validator (패턴 A) **또는** `TaskRunWorker.validateResult()` (패턴 B)

절차는 `docs/architecture/AI_MODULE_INTEGRATION_GUIDE.md`.

---

## 6. 제거하면 안 되는 경계 표시

값에 실려 나가는 문구들이다. 편의상 지우거나 프론트에서 감추면 안 된다.

| 필드 | 값의 성격 |
|---|---|
| `LegalReviewResult.disclaimer`, `sourceVerified: false` | **법률 자문이 아님** |
| `StudyView.syntheticNotice`, `PersonaView.synthetic: true` | **가설이며 실제 고객 응답이 아님** |
| 재무 관련 표기 | **재무 자문 아님 · 외부 시장 데이터 미반영** |
| Mock/실제 구분 | 이 시스템에 **Mock이 없다**. 키가 없으면 가짜 결과 대신 `AI_CONFIGURATION_INVALID`로 실패한다 |
