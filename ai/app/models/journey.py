from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, ValidationInfo, model_validator
from pydantic_core import PydanticCustomError


class StrictResult(BaseModel):
    model_config = ConfigDict(extra="forbid")


NonBlankMarketingText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class IdeaOriginTarget(StrictResult):
    customerTypes: list[str]
    segment: str | None = None
    situation: str | None = None
    needs: list[str]


class IdeaOriginFixedValue(StrictResult):
    field: str
    value: str


class IdeaOriginDraft(StrictResult):
    productServiceDescription: str | None = None
    problem: list[str]
    target: IdeaOriginTarget | None = None
    solution: list[str]
    coreValue: list[str]
    primaryCategory: str | None = None
    targetRegion: str | None = None
    fixedValues: list[IdeaOriginFixedValue]
    confirmedValues: dict[str, Any]
    assumptions: list[str]
    pricingIntent: str | None = None
    revenueModelIntent: str | None = None
    salesChannelIntent: str | None = None
    knownUnitCost: str | None = None
    alternatives: list[str]
    knownCompetitors: list[str]
    differentiationIntent: str | None = None
    internalConstraints: list[str]


class IdeaInputMetadata(StrictResult):
    key: str
    sourceType: Literal["USER_CONFIRMED", "AI_PROPOSED"]
    requiredForStages: list[Literal["IDEA_ORIGIN", "LEGAL_PRECHECK", "CONCEPT_BUILD"]]
    status: Literal["MISSING", "AI_PROPOSED", "USER_CONFIRMED"]
    locked: bool
    fallbackPolicy: Literal["NO_FALLBACK", "AI_MAY_PROPOSE", "BLOCK_STAGE"]


class IdeaClarificationQuestion(StrictResult):
    targetField: str
    requirement: Literal["REQUIRED_FOR_IDEA_ORIGIN", "REQUIRED_FOR_LEGAL_PRECHECK"]
    question: str
    reason: str


class IdeaInterpretationResult(StrictResult):
    originalSourceSummary: str
    normalizedDescription: str
    facts: list[str]
    assumptions: list[str]
    constraints: list[str]
    openQuestions: list[str]
    readiness: Literal["UNDER_SPECIFIED", "APPROPRIATE", "OVER_SPECIFIED"]
    warnings: list[str]
    evidenceNeeds: list[str]
    originDraft: IdeaOriginDraft
    fieldMetadata: list[IdeaInputMetadata]
    clarificationQuestions: list[IdeaClarificationQuestion]

    @model_validator(mode="after")
    def require_questions_for_missing_origin_fields(self):
        question_targets = {
            question.targetField for question in self.clarificationQuestions
        }
        origin = self.originDraft
        required_values = {
            "productServiceDescription": origin.productServiceDescription,
            "problem": origin.problem,
            "target": origin.target,
            "solution": origin.solution,
            "coreValue": origin.coreValue,
            "primaryCategory": origin.primaryCategory,
            "targetRegion": origin.targetRegion,
            "fixedValues": origin.fixedValues,
        }
        missing_fields = sorted(
            field
            for field, value in required_values.items()
            if value is None
            or (isinstance(value, str) and not value.strip())
            or (isinstance(value, list) and not value)
        )
        missing_questions = [
            field for field in missing_fields if field not in question_targets
        ]
        if missing_questions:
            raise PydanticCustomError(
                "idea_missing_clarification",
                "Missing clarification questions for required Idea Origin fields",
                {"fields": ",".join(missing_questions)},
            )
        return self


OpportunityFieldKey = Literal[
    "problem", "targetCustomer", "beneficiaries", "usageContext", "desiredOutcome",
    "targetRegion", "fixedConstraints", "preferredConstraints", "openDecisions",
    "assumptions", "prohibitedApproaches", "regulatorySensitiveActivities",
]


class OpportunityBriefFieldProposal(StrictResult):
    fieldKey: OpportunityFieldKey
    valueJson: Any
    decisionStatus: Literal["PREFERRED", "OPEN", "ASSUMPTION"]
    sourceType: Literal["SOURCE_EXTRACTED", "AI_PROPOSED", "MISSING"]
    confidence: Annotated[float, Field(strict=True, ge=0.0, le=1.0)] | None

    @model_validator(mode="after")
    def validate_confidence(self):
        missing = self.sourceType == "MISSING"
        if (
            missing != (self.valueJson is None)
            or (missing and self.confidence is not None)
            or (self.confidence is not None and not 0 <= self.confidence <= 1)
        ):
            raise ValueError("field proposal value and confidence are invalid")
        return self


class OpportunityClarificationQuestion(StrictResult):
    id: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=80)]
    fieldKey: OpportunityFieldKey
    prompt: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=500)]
    type: Literal["FREE_TEXT", "SINGLE_SELECT", "MULTI_SELECT", "UNDECIDED"]
    options: list[str]
    allowUndecided: bool


class OpportunityBriefDraftResult(StrictResult):
    extractedFields: list[OpportunityBriefFieldProposal]
    fieldSuggestions: list[OpportunityBriefFieldProposal]
    assumptions: list[NonBlankMarketingText]
    openFields: list[OpportunityFieldKey]
    contradictions: list[NonBlankMarketingText]
    clarificationQuestions: Annotated[list[OpportunityClarificationQuestion], Field(max_length=4)]
    readiness: Literal["NEEDS_INPUT", "READY_FOR_CONFIRMATION"]
    userFacingSummary: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=2000)]

    @model_validator(mode="after")
    def require_bounded_questions(self):
        if self.readiness == "NEEDS_INPUT" and len(self.clarificationQuestions) < 2:
            raise ValueError("NEEDS_INPUT requires between two and four questions")
        return self


class LegalReviewResult(StrictResult):
    status: Literal[
        "PASS", "PASS_WITH_CONDITIONS", "REVISION_REQUIRED", "PROHIBITED",
        "INSUFFICIENT_INFORMATION", "EXPERT_REVIEW_REQUIRED",
    ]
    summary: str
    issues: list[str]
    conditions: list[str]
    prohibitedElements: list[str]
    researchNeeds: list[str]
    sourceVerified: Literal[False]
    disclaimer: str


class ConceptOriginTrace(StrictResult):
    structureKey: str
    sourceValue: Any
    conceptValue: Any


class ConceptLegalTrace(StrictResult):
    guardrailType: str
    constraint: str
    implementation: str


class ConceptCandidate(StrictResult):
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
    originTrace: list[ConceptOriginTrace]
    legalTrace: list[ConceptLegalTrace]


def _validate_concept_origin_trace(
    concept: ConceptCandidate, context: dict[str, Any], concept_index: int
) -> None:
    required = context.get("requiredOriginTrace")
    if required is None:
        return
    expected = {item["structureKey"]: item["sourceValue"] for item in required}
    required_keys = set(expected)
    field_mapping = {
        "target": "targetSegment",
        "pricing": "pricing",
        "pricingIntent": "pricing",
        "revenueModel": "revenueModel",
        "revenueModelIntent": "revenueModel",
        "channels": "channels",
        "salesChannelIntent": "channels",
    }
    traces = {}
    duplicate = False
    for trace in concept.originTrace:
        if trace.structureKey in traces:
            duplicate = True
        traces[trace.structureKey] = trace
    if duplicate or set(traces) != required_keys:
        raise PydanticCustomError(
            "concept_origin_trace_key_mismatch",
            "originTrace must contain every required key exactly once",
            {"conceptIndex": concept_index},
        )
    for key, source_value in expected.items():
        trace = traces[key]
        if trace.sourceValue != source_value:
            raise PydanticCustomError(
                "concept_origin_source_mismatch",
                "originTrace sourceValue must be preserved",
                {"conceptIndex": concept_index},
            )
        candidate_field = field_mapping.get(key)
        if candidate_field is not None and trace.conceptValue != getattr(
            concept, candidate_field
        ):
            raise PydanticCustomError(
                "concept_origin_value_mismatch",
                "originTrace conceptValue must match the candidate field",
                {"conceptIndex": concept_index},
            )


class SingleConceptGenerationResult(StrictResult):
    concept: ConceptCandidate

    @model_validator(mode="after")
    def preserve_required_origin_trace(self, info: ValidationInfo):
        _validate_concept_origin_trace(
            self.concept,
            info.context or {},
            (info.context or {}).get("slotIndex", 0),
        )
        return self


class ConceptGenerationResult(StrictResult):
    concepts: list[ConceptCandidate]

    @model_validator(mode="after")
    def preserve_required_origin_trace(self, info: ValidationInfo):
        context = info.context or {}
        desired_count = context.get("desiredCount")
        required = context.get("requiredOriginTrace")
        if desired_count is None or required is None:
            return self
        if len(self.concepts) != desired_count:
            raise PydanticCustomError(
                "concept_desired_count_mismatch",
                "Concept count must equal desiredCount",
                {"conceptIndex": -1},
            )
        for index, concept in enumerate(self.concepts):
            _validate_concept_origin_trace(concept, context, index)
        return self


class ConceptLegalValidationResult(StrictResult):
    status: Literal["PASS", "FAIL_LEGAL"]
    reasons: list[str]
    violatedStructureKeys: list[str]
    legalTrace: list[ConceptLegalTrace]


class ConceptLegalGuardrails(StrictResult):
    hardConstraints: list[Any]
    prohibitedPatterns: list[Any]
    conditionalConstraints: list[Any]
    requiredDisclosures: list[Any]
    requiredOperationalControls: list[Any]


class ConceptLegalValidationDraft(ConceptCandidate):
    candidateKey: NonBlankMarketingText


class ConceptLegalValidationBatchInput(StrictResult):
    guardrails: ConceptLegalGuardrails
    lockedValues: dict[str, Any]
    conceptDrafts: list[ConceptLegalValidationDraft] = Field(min_length=1)


class ConceptLegalValidationTrace(StrictResult):
    guardrailType: NonBlankMarketingText
    constraint: NonBlankMarketingText
    implementation: NonBlankMarketingText


class ConceptLegalValidationItem(StrictResult):
    candidateKey: NonBlankMarketingText
    status: Literal["PASS", "FAIL_LEGAL"]
    reasons: list[NonBlankMarketingText]
    violatedStructureKeys: list[NonBlankMarketingText]
    legalTrace: list[ConceptLegalValidationTrace]


class ConceptLegalValidationBatchResult(StrictResult):
    validations: list[ConceptLegalValidationItem] = Field(min_length=1)


class QuickAssessmentItem(StrictResult):
    conceptVersionId: int
    market: int
    customerValue: int
    feasibility: int
    differentiation: int
    revenuePotential: int
    legalRisk: int
    overallScore: float
    summary: str
    strengths: list[str]
    weaknesses: list[str]


class QuickAssessmentResult(StrictResult):
    assessments: list[QuickAssessmentItem]


class DetailedAnalysisItem(StrictResult):
    conceptVersionId: int
    marketAnalysis: str
    customerAnalysis: str
    businessModelAnalysis: str
    operationAnalysis: str
    riskAnalysis: str
    recommendation: str
    assumptions: list[str]
    researchNeeds: list[str]


class DetailedAnalysisResult(StrictResult):
    analyses: list[DetailedAnalysisItem]


class RoleAndContext(StrictResult):
    role: str
    situation: str
    goals: list[str]
    constraints: list[str]


class ProblemAndNeeds(StrictResult):
    problems: list[str]
    unmetNeeds: list[str]
    desiredOutcomes: list[str]


class BehaviorAndDecision(StrictResult):
    currentBehavior: list[str]
    decisionCriteria: list[str]
    barriers: list[str]
    informationSources: list[str]


class PersonaCardItem(StrictResult):
    name: str
    shortLabel: str
    roleAndContext: RoleAndContext
    problemAndNeeds: ProblemAndNeeds
    behaviorAndDecision: BehaviorAndDecision
    interviewFocus: list[str]


class PersonaCardGenerationResult(StrictResult):
    personas: list[PersonaCardItem]


class PersonaInterviewMessage(StrictResult):
    category: Literal["ROLE_AND_CONTEXT", "PROBLEM_AND_NEEDS", "BEHAVIOR_AND_DECISION"]
    question: str
    answer: str


class PersonaInterviewResult(StrictResult):
    messages: list[PersonaInterviewMessage]


class InterviewSynthesisResult(StrictResult):
    commonThemes: list[str]
    conflictingViews: list[str]
    criticalNeeds: list[str]
    decisionBarriers: list[str]
    implications: list[str]
    researchNeeds: list[str]


class PersonaMessage(StrictResult):
    personaId: int = Field(strict=True)
    personaName: NonBlankMarketingText
    message: NonBlankMarketingText
    rationale: NonBlankMarketingText


class ChannelPlanItem(StrictResult):
    channel: NonBlankMarketingText
    objective: NonBlankMarketingText
    message: NonBlankMarketingText


class LandingHero(StrictResult):
    headline: NonBlankMarketingText
    subheadline: NonBlankMarketingText
    cta: NonBlankMarketingText


class MarketingGenerationResult(StrictResult):
    positioning: NonBlankMarketingText
    coreMessage: NonBlankMarketingText
    slogans: list[str] = Field(min_length=1)
    personaMessages: list[PersonaMessage] = Field(min_length=1)
    channelPlan: list[ChannelPlanItem] = Field(min_length=1)
    socialCopies: list[str] = Field(min_length=1)
    emailCopies: list[str] = Field(default_factory=list)
    landingHero: LandingHero
    assumptions: list[str]
    warnings: list[str]


class PersonaFit(StrictResult):
    personaId: int = Field(strict=True)
    personaName: NonBlankMarketingText
    fit: Literal["LOW", "MEDIUM", "HIGH"]
    rationale: NonBlankMarketingText


class MarketingComparisonItem(StrictResult):
    assetId: int = Field(strict=True)
    assetVersionId: int = Field(strict=True)
    assetType: Literal["POSITIONING", "CORE_MESSAGE", "SLOGAN", "SOCIAL_COPY", "LANDING_HERO", "EMAIL_COPY", "CHANNEL_PLAN"]
    personaFit: list[PersonaFit] = Field(min_length=1)
    strengths: list[NonBlankMarketingText] = Field(min_length=1)
    risks: list[NonBlankMarketingText] = Field(min_length=1)
    recommendedContexts: list[NonBlankMarketingText] = Field(min_length=1)
    selectionSuggestion: NonBlankMarketingText


class MarketingComparisonResult(StrictResult):
    comparisons: list[MarketingComparisonItem] = Field(min_length=1)


class FinalReportResult(StrictResult):
    executiveSummary: NonBlankMarketingText
    idea: dict[str, Any]
    legalReview: dict[str, Any]
    selectedConcept: dict[str, Any]
    analysis: dict[str, Any]
    personaInsights: dict[str, Any]
    marketingStrategy: dict[str, Any]
    facts: list[NonBlankMarketingText]
    assumptions: list[NonBlankMarketingText]
    researchNeeds: list[NonBlankMarketingText]
    risks: list[NonBlankMarketingText]
    decision: Literal["GO", "CONDITIONAL_GO", "REWORK", "HOLD", "STOP"]
    decisionReasons: list[NonBlankMarketingText] = Field(min_length=1)
    nextActions: list[NonBlankMarketingText] = Field(min_length=1)
