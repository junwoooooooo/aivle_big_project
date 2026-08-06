from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


LegalCategory = Literal[
    "BUSINESS_REGISTRATION", "LICENSE_AND_PERMIT", "PRIVACY_AND_DATA",
    "TERMS_AND_CONTRACT", "INTELLECTUAL_PROPERTY", "CONSUMER_PROTECTION",
    "ADVERTISING_AND_MARKETING", "LABOR_AND_EMPLOYMENT", "INDUSTRY_SPECIFIC",
    "TAX_AND_FINANCIAL",
]


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class RouteDecision(StrictModel):
    routeId: str = Field(min_length=1)
    status: Literal["APPLIES", "POSSIBLE", "NOT_APPLICABLE", "UNKNOWN"]
    evidenceQuotes: list[str]
    reason: str = Field(min_length=1)
    confidence: float = Field(ge=0, le=1)


class MissingInformation(StrictModel):
    question: str = Field(min_length=1)
    relatedRouteIds: list[str]


class RoutingResult(StrictModel):
    routes: list[RouteDecision]
    additionalRouteCandidates: list[str]
    missingInformation: list[MissingInformation]


class Screening(StrictModel):
    citationId: str = Field(min_length=1)
    role: Literal["REQUIREMENT", "SANCTION", "SCOPE", "SUPPORTING", "EXCLUDE"]
    plainSummary: str
    whyRelevant: str


class ScreeningResult(StrictModel):
    screenings: list[Screening]
    excludedCitationIds: list[str] = Field(default_factory=list)
    coverageInferred: bool = False


class LegalRouteResult(StrictModel):
    routeId: str = Field(min_length=1)
    topic: str = Field(min_length=1)
    status: Literal["APPLIES", "POSSIBLE", "NOT_APPLICABLE", "UNKNOWN"]
    evidenceQuotes: list[str]
    reason: str = Field(min_length=1)
    categories: list[LegalCategory]


class LegalEvidence(StrictModel):
    evidenceId: str = Field(min_length=1)
    routeId: str = Field(min_length=1)
    category: LegalCategory
    registryVersion: str = Field(min_length=1)
    lawName: str = Field(min_length=1)
    article: str = Field(min_length=1)
    title: str
    role: Literal["REQUIREMENT", "SANCTION", "SCOPE", "SUPPORTING"]
    plainSummary: str = Field(min_length=1)
    whyRelevant: str = Field(min_length=1)
    excerpt: str = Field(min_length=1)
    effectiveDate: str | None
    lawUrl: str = Field(min_length=1)
    verifiedAt: str = Field(min_length=1)


class LegalReasoning(StrictModel):
    category: LegalCategory
    inputBasis: list[str]
    regulatoryArea: str = Field(min_length=1)
    obligation: str = Field(min_length=1)
    consequence: str = Field(min_length=1)
    requiredAction: str = Field(min_length=1)
    evidenceIds: list[str]


class LegalFinding(StrictModel):
    category: LegalCategory
    applicability: Literal["APPLIES", "POSSIBLE"]
    summary: str = Field(min_length=1)
    evidenceIds: list[str]
    reasoning: LegalReasoning


class LegalSourcePipelineResult(StrictModel):
    taskType: Literal["IDEA_LEGAL_PRECHECK", "CONCEPT_LEGAL_VALIDATION"]
    sourceStatus: Literal["SOURCE_COMPLETE", "SOURCE_PARTIAL", "REGISTRY_GAP"]
    registryVersion: str = Field(min_length=1)
    routes: list[LegalRouteResult]
    findings: list[LegalFinding]
    evidence: list[LegalEvidence]
    requiredUserInputs: list[MissingInformation]
    sourceWarnings: list[str]


BoundaryRuleType = Literal[
    "PROHIBITED_ROLE", "PROHIBITED_ACTIVITY", "ALLOWED_PATTERN", "REQUIRED_CONTROL",
    "REQUIRED_PARTNER", "REQUIRED_DISCLOSURE", "UNRESOLVED_FACT",
]
BoundarySourceStatus = Literal["COMPLETE", "PARTIAL", "WARNING", "UNAVAILABLE"]


class BoundaryRuleDraft(StrictModel):
    ruleId: str = Field(min_length=1)
    ruleType: BoundaryRuleType
    structureKey: str = Field(min_length=1)
    title: str = Field(min_length=1)
    description: str = Field(min_length=1)
    normalizedRequirement: str = Field(min_length=1)
    evidenceIds: list[str]
    severity: Literal["LOW", "MEDIUM", "HIGH", "CRITICAL"]
    sourceStatus: BoundarySourceStatus
    appliesWhen: dict[str, object]
    userFacingReason: str = Field(min_length=1)
    alternatives: list[str]
    requiredQualifications: list[str]
    requiredPartnerRole: str | None
    requiredDisclosure: str | None
    affectedBriefFields: list[str]
    professionalReviewRecommended: bool
    userActionOptions: list[str]


class BoundaryQuestionDraft(StrictModel):
    questionId: str = Field(min_length=1)
    fieldKey: str = Field(min_length=1)
    question: str = Field(min_length=1)
    reason: str = Field(min_length=1)
    answerType: Literal["TEXT", "SINGLE_SELECT", "MULTI_SELECT", "BOOLEAN"]
    options: list[str]
    required: bool
    relatedRuleIds: list[str]
    relatedEvidenceIds: list[str]


class BoundaryConflictDraft(StrictModel):
    conflictId: str = Field(min_length=1)
    affectedFieldKey: str = Field(min_length=1)
    lockedValue: object
    conflictingRuleIds: list[str] = Field(min_length=1)
    reason: str = Field(min_length=1)
    userActionOptions: list[str] = Field(min_length=1)


class BoundaryNormalizationResult(StrictModel):
    rules: list[BoundaryRuleDraft]
    questions: list[BoundaryQuestionDraft] = Field(max_length=4)
    conflicts: list[BoundaryConflictDraft]
    userActionOptions: list[str]


class RegulatoryBoundaryEvidence(StrictModel):
    evidenceId: str = Field(min_length=1)
    sourceType: Literal["OFFICIAL_LAW"]
    lawName: str = Field(min_length=1)
    article: str | None
    title: str | None
    effectiveDate: str | None
    officialUrl: str = Field(pattern=r"^https://www\.law\.go\.kr/")
    excerpt: str = Field(min_length=1)
    plainSummary: str = Field(min_length=1)
    whyRelevant: str = Field(min_length=1)
    sourceStatus: BoundarySourceStatus
    retrievedAt: str = Field(min_length=1)
    contentHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")


class RegulatoryBoundaryResult(StrictModel):
    taskType: Literal["REGULATORY_BOUNDARY_GENERATION"]
    sourceStatus: BoundarySourceStatus
    registryVersion: str = Field(min_length=1)
    routes: list[LegalRouteResult]
    evidence: list[RegulatoryBoundaryEvidence]
    rules: list[BoundaryRuleDraft]
    questions: list[BoundaryQuestionDraft] = Field(max_length=4)
    conflicts: list[BoundaryConflictDraft]
    status: Literal["READY", "NEEDS_INPUT", "BLOCKED"]
    userActionOptions: list[str]
    sourceWarnings: list[str]
