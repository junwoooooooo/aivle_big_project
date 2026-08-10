"""Concept Portfolio Engine V2의 격리된 계약 모델."""

from __future__ import annotations

from datetime import datetime, timezone
from enum import StrEnum
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.tasks.concept_candidate.models import ConceptCandidateResult


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ProviderMode(StrEnum):
    MOCK = "MOCK"
    REPLAY = "REPLAY"
    LIVE = "LIVE"


class ExplorationBreadth(StrEnum):
    EXPLORE = "EXPLORE"
    REFINE = "REFINE"
    AS_IS = "AS_IS"


class RunStage(StrEnum):
    CREATED = "CREATED"
    SAFETY_CHECKING = "SAFETY_CHECKING"
    SEED_ANALYZING = "SEED_ANALYZING"
    PLANNING = "PLANNING"
    PLAN_VALIDATING = "PLAN_VALIDATING"
    EXPANDING = "EXPANDING"
    CANDIDATE_VALIDATING = "CANDIDATE_VALIDATING"
    LEGAL_REVIEWING = "LEGAL_REVIEWING"
    LEGAL_RECOVERING = "LEGAL_RECOVERING"
    PORTFOLIO_VALIDATING = "PORTFOLIO_VALIDATING"
    READY = "READY"
    NEEDS_INPUT = "NEEDS_INPUT"
    FAILED = "FAILED"


class FailureCode(StrEnum):
    USER_INPUT_CONSTRAINED = "USER_INPUT_CONSTRAINED"
    PLAN_DUPLICATE = "PLAN_DUPLICATE"
    CANDIDATE_DUPLICATE = "CANDIDATE_DUPLICATE"
    ANCHOR_DRIFT = "ANCHOR_DRIFT"
    LOCK_VIOLATION = "LOCK_VIOLATION"
    LOCK_CONFLICT = "LOCK_CONFLICT"
    PLAN_FIDELITY_FAILED = "PLAN_FIDELITY_FAILED"
    LEGAL_REDESIGN_REQUIRED = "LEGAL_REDESIGN_REQUIRED"
    LEGAL_REPLAN_REQUIRED = "LEGAL_REPLAN_REQUIRED"
    LEGAL_LOCK_CONFLICT = "LEGAL_LOCK_CONFLICT"
    IDENTITY_DRIFT = "IDENTITY_DRIFT"
    DUPLICATE_WITH_PORTFOLIO = "DUPLICATE_WITH_PORTFOLIO"
    VARIATION_SPACE_LIMITED = "VARIATION_SPACE_LIMITED"
    PROVIDER_TRANSIENT = "PROVIDER_TRANSIENT"
    PROVIDER_PERMANENT = "PROVIDER_PERMANENT"
    RESULT_SCHEMA_INVALID = "RESULT_SCHEMA_INVALID"
    REPLAY_MISS = "REPLAY_MISS"
    DOWNSTREAM_HANDOFF_INVALID = "DOWNSTREAM_HANDOFF_INVALID"
    CONTENT_LANGUAGE_MISMATCH = "CONTENT_LANGUAGE_MISMATCH"
    PLAN_POOL_RESERVE_SHORTFALL = "PLAN_POOL_RESERVE_SHORTFALL"
    OUT_OF_SCOPE = "OUT_OF_SCOPE"
    CANDIDATE_FIDELITY_RECOVERABLE = "CANDIDATE_FIDELITY_RECOVERABLE"
    CANDIDATE_REGENERATION_EXHAUSTED = "CANDIDATE_REGENERATION_EXHAUSTED"
    CANONICALIZATION_LOW_CONFIDENCE = "CANONICALIZATION_LOW_CONFIDENCE"
    LEGAL_FACT_COMPLETION_EXHAUSTED = "LEGAL_FACT_COMPLETION_EXHAUSTED"
    LEGAL_FACT_COMPLETION_PROVIDER_NONCOMPLIANT = "LEGAL_FACT_COMPLETION_PROVIDER_NONCOMPLIANT"
    LEGAL_FACT_COMPLETION_RECHECK_FAILED = "LEGAL_FACT_COMPLETION_RECHECK_FAILED"
    LEGAL_FACT_DEPENDENCY_UNRESOLVED = "LEGAL_FACT_DEPENDENCY_UNRESOLVED"
    LEGAL_FACT_COMPLETION_CANDIDATE_INVALID = "LEGAL_FACT_COMPLETION_CANDIDATE_INVALID"
    LEGAL_FACT_COMPLETION_SCOPE_VIOLATION = "LEGAL_FACT_COMPLETION_SCOPE_VIOLATION"
    CONCEPT_FACT_CONSISTENCY_INVALID = "CONCEPT_FACT_CONSISTENCY_INVALID"
    CONCEPT_FACT_CONSISTENCY_REPAIR_FAILED = "CONCEPT_FACT_CONSISTENCY_REPAIR_FAILED"
    NO_LEGAL_READY_CANDIDATES = "NO_LEGAL_READY_CANDIDATES"
    LEGAL_REDESIGN_COMPLIANCE_EXHAUSTED = "LEGAL_REDESIGN_COMPLIANCE_EXHAUSTED"
    LEGAL_REDESIGN_LOOP_DETECTED = "LEGAL_REDESIGN_LOOP_DETECTED"


class PortfolioStatus(StrEnum):
    READY_FULL = "READY_FULL"
    READY_LIMITED = "READY_LIMITED"
    NEEDS_INPUT = "NEEDS_INPUT"
    FAILED = "FAILED"


class LegalRoute(StrEnum):
    ACCEPT = "ACCEPT"
    REDESIGN_WITHIN_LINEAGE = "REDESIGN_WITHIN_LINEAGE"
    REPLAN_REQUIRED = "REPLAN_REQUIRED"
    NEEDS_INPUT = "NEEDS_INPUT"
    SYSTEM_FAILURE = "SYSTEM_FAILURE"


class LegalRequirementNature(StrEnum):
    FACT_REQUIRED = "FACT_REQUIRED"
    STRUCTURAL_CHANGE = "STRUCTURAL_CHANGE"
    AMBIGUOUS = "AMBIGUOUS"


class SeedField(StrictModel):
    fieldKey: str
    value: str
    source: str
    decisionState: str


class CanonicalSeed(StrictModel):
    ideaBriefSnapshotId: str = "lab-idea-brief"
    ideaOverview: str
    problem: str
    targetUsers: str
    fields: list[SeedField]
    interpretation: dict[str, Any] = Field(default_factory=dict)
    fixtureName: str = "custom"

    def by_key(self) -> dict[str, SeedField]:
        return {field.fieldKey: field for field in self.fields}


class SafetyResult(StrictModel):
    decision: str
    categories: list[str] = Field(default_factory=list)
    restrictions: list[str] = Field(default_factory=list)
    userFacingReason: str

    @property
    def passed(self) -> bool:
        return self.decision != "BLOCK_OR_REFRAME"


class IdeaBriefLabContext(StrictModel):
    safetyReview: SafetyResult
    interpretation: dict[str, Any]
    commitmentCandidates: list[dict[str, Any]] = Field(default_factory=list)
    readiness: dict[str, Any]
    userFacingSummary: str
    contradictions: list[dict[str, Any]] = Field(default_factory=list)
    questions: list[dict[str, Any]] = Field(default_factory=list)


class OpportunityKernel(StrictModel):
    problemCore: str
    targetCore: str
    useContexts: list[str] = Field(min_length=1, max_length=10)
    intentComponents: list[str] = Field(min_length=1, max_length=12)
    mustPreserve: list[str] = Field(min_length=2, max_length=12)
    maySpecialize: list[str] = Field(max_length=10)
    forbiddenDriftSummary: str


class DesignSpaceAnalysis(StrictModel):
    opportunityKernel: OpportunityKernel
    semanticAnchors: dict[str, str]
    sourceLocks: dict[str, str]
    explicitBusinessLocks: dict[str, str]
    openDimensions: list[str]
    constrainedDimensions: list[str]
    explorationBreadth: ExplorationBreadth
    diversityCapacity: int = Field(ge=0, le=5)
    suggestedMaxConcepts: int = Field(ge=0, le=5)
    rationaleSummary: str


class ConceptThesis(StrictModel):
    targetSegmentThesis: str = Field(min_length=1, max_length=1000)
    useCaseThesis: str = Field(min_length=1, max_length=2000)
    valuePropositionThesis: str = Field(min_length=1, max_length=2000)
    offerThesis: str = Field(min_length=1, max_length=2000)
    solutionThesis: str = Field(min_length=1, max_length=3000)


BusinessRoleCode = Literal[
    "PRINCIPAL_OPERATOR", "MARKETPLACE", "INTERMEDIARY", "SAAS_TOOL", "ADVISORY",
    "AGGREGATOR", "PLATFORM_INFRASTRUCTURE", "OTHER",
]
OperatingModelCode = Literal[
    "OWN_OPERATED", "PARTNER_NETWORK", "PEER_TO_PEER", "AUTOMATED_DIGITAL",
    "EXPERT_NETWORK", "HYBRID", "OTHER",
]
DeliveryModelCode = Literal[
    "DIGITAL", "PHYSICAL_DELIVERY", "PARTNER_FULFILLED", "PICKUP", "ON_SITE",
    "SELF_SERVICE", "HYBRID", "OTHER",
]
TransactionModelCode = Literal[
    "ONE_OFF", "RECURRING", "BOOKING", "MATCHING", "PREORDER", "AUCTION",
    "USAGE_BASED", "OTHER",
]
MonetizationModelCode = Literal[
    "SUBSCRIPTION", "DIRECT_SALE", "COMMISSION", "SERVICE_FEE", "USAGE_FEE",
    "LICENSING", "ADVERTISING", "B2B_CONTRACT", "FREEMIUM", "OTHER",
]
CustomerInteractionCode = Literal[
    "APP", "WEB", "API", "OFFLINE", "OMNICHANNEL", "COMMUNITY", "ASSISTED",
    "SELF_SERVICE", "OTHER",
]


class BusinessArchitecture(StrictModel):
    businessRole: BusinessRoleCode
    operatingModel: OperatingModelCode
    partnerModel: OperatingModelCode
    deliveryModel: DeliveryModelCode
    transactionModel: TransactionModelCode
    monetizationModel: MonetizationModelCode
    customerInteractionModel: CustomerInteractionCode
    dataDependency: Literal["NONE", "LOW", "MATERIAL", "CORE"]
    physicalDependency: Literal["NONE", "LOW", "MATERIAL", "CORE"]


class ArchitectureDimensionDiagnostic(StrictModel):
    code: str
    confidence: Literal["HIGH", "MEDIUM", "LOW"]
    source: Literal["RULE", "SEMANTIC", "UNKNOWN"]


class CanonicalConceptDescriptor(StrictModel):
    thesis: ConceptThesis
    architecture: BusinessArchitecture
    mechanismFamily: str = Field(min_length=1, max_length=300)
    familyId: str = Field(pattern=r"^[A-Z0-9_:-]{2,160}$")
    familyLabelKo: str = Field(min_length=1, max_length=300)
    architectureDiagnostics: dict[str, ArchitectureDimensionDiagnostic] = Field(default_factory=dict)


class PortfolioPlanDraft(StrictModel):
    title: str
    oneLineConcept: str
    targetSegment: str
    problemFocus: str
    useContext: str
    valueProposition: str
    offerThesis: str
    solutionThesis: str
    coreMechanism: str
    customerInteraction: str
    valueDelivery: str
    operatingApproach: str
    partnerApproach: str
    transactionApproach: str
    commercialApproach: str
    fulfillmentApproach: str
    differentiatingMechanics: list[str] = Field(min_length=2, max_length=12)
    mainChanges: list[str]
    secondaryChanges: list[str]
    legalRiskHints: list[str]
    reasonForPortfolioRole: str


class PlanDraftPool(StrictModel):
    plans: list[PortfolioPlanDraft] = Field(min_length=1, max_length=8)


class PortfolioPlan(PortfolioPlanDraft):
    planId: str
    descriptor: CanonicalConceptDescriptor
    preservedAnchors: dict[str, str]
    preservedLocks: dict[str, str]
    selectionStatus: Literal["UNSELECTED", "SELECTED", "RESERVE"] = "UNSELECTED"
    selectionScore: float = Field(default=0.0, ge=0.0, le=2.0)
    selectionReason: str = "선택 평가 전"
    relationToPortfolio: str = "NOT_EVALUATED"


class DiversityAssessment(StrictModel):
    entityA: str
    entityB: str
    decision: str
    overlap: list[str]
    materialDifferences: list[str]
    whyDistinct: str
    deterministicLevel: str = "LEVEL_1"
    semanticJudgeUsed: bool = False
    familyA: str | None = None
    familyB: str | None = None
    relationConfidence: Literal["HIGH", "MEDIUM", "LOW"] = "HIGH"


class RejectedPlan(StrictModel):
    planId: str
    reasonCode: FailureCode
    safeSummary: str
    conflictPlanId: str | None = None


class PlanValidationResult(StrictModel):
    acceptedPlans: list[PortfolioPlan]
    reservePlans: list[PortfolioPlan] = Field(default_factory=list)
    rejectedPlans: list[RejectedPlan]
    diversity: list[DiversityAssessment]
    planningRounds: int = 1
    replenishmentRequested: int = 0


class PlanPoolStatus(StrictModel):
    requestedPoolSize: int
    returnedPoolSize: int
    initialTarget: int
    reserveTarget: int
    reserveAvailable: int
    status: str


class CandidateEnvelope(StrictModel):
    candidateId: str
    planId: str
    lineageId: str
    parentCandidateId: str | None = None
    redesignRound: int = Field(default=0, ge=0, le=2)
    candidateAttempt: int = Field(default=1, ge=1, le=2)
    slotIndex: int | None = Field(default=None, ge=1, le=5)
    recoverySource: Literal["INITIAL", "FIDELITY_REGENERATION", "RESERVE_PLAN", "REPLENISHED_PLAN", "LEGAL_FACT_COMPLETION", "LEGAL_REDESIGN", "LEGAL_REDESIGN_COMPLIANCE_REPAIR", "LEGAL_REPLAN"] = "INITIAL"
    descriptor: CanonicalConceptDescriptor
    candidate: ConceptCandidateResult


class CandidateValidation(StrictModel):
    candidateId: str
    schemaValid: bool
    hardLockPreserved: bool
    semanticAnchorPreserved: bool
    planFidelity: bool
    anchorDecision: str = "PASS"
    fidelityDecision: str = "PASS"
    contentLanguageValid: bool = True
    accepted: bool
    outcome: Literal["ACCEPTED", "RECOVERABLE_FIDELITY_FAILURE", "TERMINAL_INVALID"] = "TERMINAL_INVALID"
    semanticFidelityUsed: bool = False
    matchedIdentityComponents: list[str] = Field(default_factory=list, max_length=12)
    missingIdentityComponents: list[str] = Field(default_factory=list, max_length=12)
    reasonCodes: list[FailureCode] = Field(default_factory=list)
    safeSummary: str


class CandidatePortfolioPreparation(StrictModel):
    candidates: list[CandidateEnvelope]
    reports: list[CandidateValidation]
    usedPlans: list[PortfolioPlan]
    candidateGenerated: int = 0
    candidateAcceptedInitially: int = 0
    candidateRegenerated: int = 0
    candidateRecovered: int = 0
    reservePlansActivated: int = 0
    candidateRecoveryReplans: int = 0


class LegalPrecheck(StrictModel):
    candidateId: str
    label: str = "Structural risk precheck — not final legal review"
    directSeller: bool
    intermediary: bool
    regulatedPhysicalActivity: bool
    personalDataDependency: bool
    qualificationDependency: bool
    riskHints: list[str]


class LegalFactCompletenessResult(StrictModel):
    candidateId: str | None = None
    status: Literal["COMPLETE", "SEMANTIC_REQUIRED", "COMPLETABLE", "INVALID"]
    missingDesignFacts: list[str] = Field(default_factory=list)
    contradictions: list[str] = Field(default_factory=list)
    completionRequirements: list[str] = Field(default_factory=list)
    affectedFields: list[str] = Field(default_factory=list)
    roleSemantics: list[dict[str, Any]] = Field(default_factory=list)
    dependencyAssessments: list["LegalFactDependencyAssessment"] = Field(default_factory=list)
    structuredCompletionRequirements: list["LegalFactCompletionRequirement"] = Field(default_factory=list)
    architectureRoleConsistency: dict[str, str] | None = None
    safeSummary: str


class LegalCandidatePreparation(StrictModel):
    candidates: list[CandidateEnvelope]
    reports: list[LegalFactCompletenessResult]
    excludedCandidates: list[dict[str, Any]] = Field(default_factory=list)
    completionAttempted: int = 0
    completionValidated: int = 0
    completionAccepted: int = 0
    completionExhausted: int = 0
    roleSemanticBatchCalls: int = 0
    dependencySemanticBatchCalls: int = 0
    completionCompliance: list["LegalFactCompletionCompliance"] = Field(default_factory=list)
    consistencyReports: list["ConceptFactConsistencyResult"] = Field(default_factory=list)
    consistencyRepairAttempted: int = 0
    consistencyRepairAccepted: int = 0
    consistencyRepairExhausted: int = 0


class BusinessRoleSemanticItem(StrictModel):
    candidateId: str
    field: Literal["platformRole", "providerRole", "sellerRole", "intermediaryRole"]
    decision: Literal["MATCH", "EXPLICIT_ABSENCE", "MISMATCH", "UNKNOWN"]
    safeReason: str


class BusinessRoleSemanticBatch(StrictModel):
    results: list[BusinessRoleSemanticItem] = Field(min_length=1, max_length=20)


LegalFactDependencyType = Literal["PERSONAL_DATA", "PHYSICAL_ACTIVITY", "BUSINESS_PARTNER"]


class LegalFactDependencyAssessment(StrictModel):
    candidateId: str | None = None
    dependencyType: LegalFactDependencyType
    deterministicDecision: Literal["REQUIRED", "NOT_REQUIRED", "AMBIGUOUS"]
    semanticUsed: bool = False
    semanticDecision: Literal["REQUIRED", "NOT_REQUIRED", "UNKNOWN", "NOT_RUN"] = "NOT_RUN"
    finalDecision: Literal["REQUIRED", "NOT_REQUIRED", "UNKNOWN"]
    safeReason: str
    consistencyStatus: Literal["CONSISTENT", "POTENTIAL_CONFLICT", "NOT_ENOUGH_EVIDENCE"] = "NOT_ENOUGH_EVIDENCE"


class LegalFactDependencySemanticItem(StrictModel):
    candidateId: str
    dependencyType: LegalFactDependencyType
    decision: Literal["REQUIRED", "NOT_REQUIRED", "UNKNOWN"]
    safeReason: str


class LegalFactDependencySemanticBatch(StrictModel):
    results: list[LegalFactDependencySemanticItem] = Field(min_length=1, max_length=15)


class LegalFactCompletionRequirement(StrictModel):
    field: Literal[
        "platformRole", "providerRole", "sellerRole", "intermediaryRole",
        "transactionFlow", "paymentFlow", "personalDataUsage", "physicalActivities",
        "partnerRequirements", "targetRegion", "channels",
    ]
    reasonType: Literal[
        "MISSING_REQUIRED_FACT", "DEPENDENCY_UNKNOWN", "ROLE_MISMATCH",
        "TRANSACTION_INCOMPLETE", "PAYMENT_INCOMPLETE", "GENERAL_FACT_INCOMPLETE",
        "FACT_CONSISTENCY_REPAIR",
    ]
    dependencyType: LegalFactDependencyType | None
    instruction: str


class LegalFactCompletionPatch(StrictModel):
    platformRole: str | None
    providerRole: str | None
    sellerRole: str | None
    intermediaryRole: str | None
    transactionFlow: list[str] | None
    paymentFlow: list[str] | None
    personalDataUsage: list[str] | None
    physicalActivities: list[str] | None
    partnerRequirements: list[str] | None
    targetRegion: str | None
    channels: str | None


class LegalFactCompletionCompliance(StrictModel):
    candidateId: str
    status: Literal["PASS", "FAIL", "AMBIGUOUS"]
    satisfiedRequirements: list[str] = Field(default_factory=list)
    unsatisfiedRequirements: list[str] = Field(default_factory=list)
    changedFields: list[str] = Field(default_factory=list)
    unchangedRequiredFields: list[str] = Field(default_factory=list)
    safeSummary: str


class ConceptFactConsistencyIssue(StrictModel):
    field: Literal[
        "intermediaryRole", "sellerRole", "personalDataUsage",
        "physicalActivities", "partnerRequirements",
    ]
    relation: Literal[
        "SERVICE_PHYSICAL", "TRANSACTION_INTERMEDIARY", "TRANSACTION_SELLER",
        "PARTNER_OPERATION", "DATA_PERSONAL",
    ]
    status: Literal["POTENTIAL_CONFLICT", "INVALID_FACT"]
    safeReason: str
    repairInstruction: str


class ConceptFactConsistencyResult(StrictModel):
    candidateId: str | None = None
    status: Literal["CONSISTENT", "POTENTIAL_CONFLICT", "INVALID_FACT"]
    issues: list[ConceptFactConsistencyIssue] = Field(default_factory=list)
    safeSummary: str


class LegalLineageResolution(StrictModel):
    candidateId: str
    initialRoute: str
    recoveryAction: str
    recoveryCandidateId: str | None = None
    finalRoute: str
    finalResolution: Literal["ACCEPTED", "NEEDS_INPUT", "EXCLUDED_LEGAL", "SYSTEM_FAILURE"]
    finalAccepted: bool
    sourceStatus: str


class RedesignRequirementCompliance(StrictModel):
    status: Literal["PASS", "AMBIGUOUS", "FAIL"]
    satisfiedRequirements: list[str] = Field(default_factory=list)
    unsatisfiedRequirements: list[str] = Field(default_factory=list)
    safeSummary: str


class LegalRequirementNatureAssessment(StrictModel):
    nature: LegalRequirementNature
    affectedFields: list[str] = Field(default_factory=list)
    beforeSummary: str | None = None
    requiredStructure: str | None = None
    factQuestion: str | None = None
    safeReason: str


class LegalReview(StrictModel):
    candidateId: str
    route: LegalRoute
    productionStatus: str | None = None
    sourceStatus: str
    safeSummary: str
    requiredControls: list[str] = Field(default_factory=list)
    requiredPartnersAndQualifications: list[str] = Field(default_factory=list)
    redesignRequirements: list[str] = Field(default_factory=list)
    prohibitedVariants: list[str] = Field(default_factory=list)
    requiredDisclosures: list[str] = Field(default_factory=list)
    officialEvidenceReferences: list[dict[str, Any]] = Field(default_factory=list)
    deltaLegalReviews: list[dict[str, Any]] = Field(default_factory=list)
    conflictingLock: str | None = None
    currentValue: str | None = None
    requiredLegalChange: str | None = None
    reason: str | None = None
    possibleUserAction: str | None = None
    unknownFacts: list[str] = Field(default_factory=list)
    inputScope: str = "CANDIDATE"
    evidenceDiagnostics: dict[str, Any] = Field(default_factory=dict)
    reviewPhase: str | None = None
    factCompletenessStatus: str | None = None
    legalSourceStatus: str | None = None
    finalEvidenceJudgmentExecuted: bool | None = None
    recoveryResolution: str | None = None
    sourceQuestionCount: int = 0
    resolvedByFactPatternCount: int = 0
    designGapCount: int = 0
    externalFactCount: int = 0
    controlConvertibleCount: int = 0
    legalClarificationCount: int = 0


class HypothesisDecision(StrictModel):
    hypothesisType: str
    proposedValue: Any
    finalValue: Any | None = None
    source: str
    decisionStatus: str
    proposalVersion: int = 1
    locked: bool = False
    legalImpact: str = "NONE"
    legalReviewStatus: str = "NOT_REQUIRED"
    deltaLegalRequired: bool = False
    semanticStatus: Literal["VALID", "UNRESOLVED", "INVALID", "AMBIGUOUS", "UNASSESSED"] = "UNASSESSED"
    semanticReason: str | None = None

    @property
    def accepted(self) -> bool:
        return self.decisionStatus in {"ACCEPTED", "USER_EDITED_ACCEPTED"} and self.finalValue is not None

    @property
    def semanticallyReady(self) -> bool:
        return self.semanticStatus == "VALID"


class HypothesisValueAssessment(StrictModel):
    hypothesisType: str
    status: Literal["VALID", "UNRESOLVED", "INVALID", "AMBIGUOUS"]
    reason: str
    normalizedValue: Any | None = None


class SemanticHypothesisResult(StrictModel):
    hypothesisType: str
    decision: Literal["VALID", "INVALID"]
    safeReason: str


class SemanticHypothesisBatch(StrictModel):
    results: list[SemanticHypothesisResult] = Field(min_length=1, max_length=5)


class DeltaLegalResult(StrictModel):
    reviewToken: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    candidateId: str
    hypothesisTypes: list[str] = Field(min_length=1, max_length=5)
    status: str
    approved: bool
    legalReview: LegalReview


class FieldMapping(StrictModel):
    v2Field: str
    downstreamField: str
    source: str
    transformed: bool
    required: bool


class DownstreamHandoff(StrictModel):
    compatibility: str
    structureStatus: str
    contractStatus: str
    marketAnalysisSeedSnapshot: dict[str, Any]
    marketingSourceSnapshot: dict[str, Any]
    sourceProvenance: dict[str, Any]
    fieldMapping: list[FieldMapping]
    validationErrors: list[str] = Field(default_factory=list)


class TraceEvent(StrictModel):
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    runId: str
    stage: RunStage
    action: str
    entityId: str | None = None
    parentId: str | None = None
    status: str
    providerMode: ProviderMode
    providerCallNumber: int | None = None
    durationMs: int = 0
    safeSummary: str
    decision: str | None = None
    reasonCode: FailureCode | None = None


class ProviderUsage(StrictModel):
    logicalOperations: int = 0
    topLevelExternalOperations: int = 0
    topLevelOperationsByStage: dict[str, int] = Field(default_factory=dict)
    # 하위 두 필드는 이전 Notebook/recording reader 호환용이다. HTTP call 수를 뜻하지 않는다.
    externalProviderCalls: int = 0
    totalProviderCalls: int = 0
    callsByStage: dict[str, int] = Field(default_factory=dict)
    externalCallsByStage: dict[str, int] = Field(default_factory=dict)
    retries: int = 0
    durationMs: int = 0
    modeCounts: dict[str, int] = Field(default_factory=dict)
    tokenUsage: dict[str, int] | None = None
    reportedCost: float | None = None
    batchDiagnostics: list[dict[str, Any]] = Field(default_factory=list)


class SchemaCompatibilityItem(StrictModel):
    schemaName: str
    status: str
    failures: list[dict[str, str]] = Field(default_factory=list)


class SchemaPreflightReport(StrictModel):
    status: str
    schemas: list[SchemaCompatibilityItem]
    providerCalls: int = 0


class SemanticDistinctnessResult(StrictModel):
    decision: Literal["DUPLICATE", "VARIANT", "DISTINCT", "OUT_OF_SCOPE", "IN_SCOPE", "SPECIALIZATION"]
    overlappingMechanics: list[str] = Field(max_length=12)
    materiallyDifferentMechanics: list[str] = Field(max_length=12)
    safeSummary: str


class SemanticFidelityResult(StrictModel):
    decision: Literal["PASS", "ADAPTED", "FAIL"]
    matchedMechanics: list[str] = Field(max_length=12)
    missingMechanics: list[str] = Field(max_length=12)
    safeSummary: str


class ArchitectureConfidenceProfile(StrictModel):
    businessRole: Literal["HIGH", "MEDIUM", "LOW"]
    operatingModel: Literal["HIGH", "MEDIUM", "LOW"]
    partnerModel: Literal["HIGH", "MEDIUM", "LOW"]
    deliveryModel: Literal["HIGH", "MEDIUM", "LOW"]
    transactionModel: Literal["HIGH", "MEDIUM", "LOW"]
    monetizationModel: Literal["HIGH", "MEDIUM", "LOW"]
    customerInteractionModel: Literal["HIGH", "MEDIUM", "LOW"]


class SemanticArchitectureClassification(StrictModel):
    entityId: str
    architecture: BusinessArchitecture
    confidence: ArchitectureConfidenceProfile
    safeSummary: str


class SemanticArchitectureBatch(StrictModel):
    results: list[SemanticArchitectureClassification] = Field(min_length=1, max_length=8)


class RunSummary(StrictModel):
    safety: str
    requestedMaximum: int
    planned: int
    planSelected: int = 0
    planSelected: int = 0
    planDuplicatesRemoved: int
    candidatesExpanded: int
    candidateGenerated: int = 0
    candidateAcceptedInitially: int = 0
    candidateRegenerated: int = 0
    candidateRecovered: int = 0
    candidateAccepted: int = 0
    reservePlansActivated: int = 0
    candidateRecoveryReplans: int = 0
    legalFactCompletionAttempted: int = 0
    legalFactCompletionValidated: int = 0
    legalFactCompletionAccepted: int = 0
    legalFactCompletionExhausted: int = 0
    legalReady: int = 0
    legalFactDependencySemanticCalls: int = 0
    legalFactCompletionCompliancePassed: int = 0
    legalFactCompletionProviderNoncompliant: int = 0
    legalFactCompletionRecheckFailed: int = 0
    factConsistencyInvalid: int = 0
    factConsistencyRepairAttempted: int = 0
    factConsistencyRepairAccepted: int = 0
    factConsistencyRepairExhausted: int = 0
    legalRedesignAttempted: int = 0
    legalRedesignValidated: int = 0
    legalRedesignAccepted: int = 0
    legalRedesignExhausted: int = 0
    legalReplanAttempted: int = 0
    legalReplanValidated: int = 0
    legalReplanAccepted: int = 0
    legalReplanExhausted: int = 0
    legalAccepted: int
    legalReviewed: int = 0
    legalInitialReviewed: int = 0
    legalRecoveryReviewed: int = 0
    totalLegalReviewEvents: int = 0
    legalInitialAccepted: int = 0
    legalRecoveredAccepted: int = 0
    legalRedesigned: int
    replanned: int
    finalPortfolio: int
    portfolioStatus: PortfolioStatus
    selectedConcept: str | None
    downstreamHandoff: str
    providerCalls: int
    totalDurationMs: int
    failureStage: str | None = None
    failureCode: str | None = None


class FailureDiagnostics(StrictModel):
    failedStage: str
    failureCode: str
    safeSummary: str
    failedEntityId: str | None = None
    providerFailure: dict[str, Any] = Field(default_factory=dict)
    lastSuccessfulStage: str | None = None
    firstFailedStage: str | None = None
    lastTraceEvents: list[TraceEvent] = Field(default_factory=list, max_length=20)
    preLegalExclusionCountsByReason: dict[str, int] = Field(default_factory=dict)


class ConceptPortfolioResult(StrictModel):
    runId: str
    runStatus: PortfolioStatus
    runtimeStage: RunStage
    requestedMaxConcepts: int = Field(ge=1, le=5)
    producedConceptCount: int = Field(ge=0, le=5)
    concepts: list[CandidateEnvelope]
    rejectedPlans: list[RejectedPlan]
    legalSummaries: list[LegalReview]
    legalResolutions: list[LegalLineageResolution] = Field(default_factory=list)
    requiredInputs: list[dict[str, Any]]
    preLegalExclusions: list[dict[str, Any]] = Field(default_factory=list)
    unresolvedCandidates: list[dict[str, Any]] = Field(default_factory=list)
    trace: list[TraceEvent]
    providerUsage: ProviderUsage
    downstreamReadiness: str
    selectedConceptId: str | None = None
    handoff: DownstreamHandoff | None = None
    runSummary: RunSummary | None = None
    failureDiagnostics: FailureDiagnostics | None = None

    @model_validator(mode="after")
    def terminal_and_count_consistent(self):
        if self.runtimeStage not in {RunStage.READY, RunStage.NEEDS_INPUT, RunStage.FAILED}:
            raise ValueError("완료 결과는 반드시 terminal runtime stage여야 합니다")
        if self.producedConceptCount != len(self.concepts):
            raise ValueError("producedConceptCount가 concepts 길이와 다릅니다")
        if self.producedConceptCount > self.requestedMaxConcepts:
            raise ValueError("producedConceptCount는 requestedMaxConcepts 이하여야 합니다")
        return self
