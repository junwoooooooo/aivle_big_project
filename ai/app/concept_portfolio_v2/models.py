"""Concept Portfolio Engine V2의 격리된 계약 모델."""

from __future__ import annotations

from datetime import datetime, timezone
from enum import StrEnum
from typing import Any

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


class OpportunityAnchor(StrictModel):
    problemCore: str
    targetUserCore: str
    intentCore: str
    allowedSpecializations: list[str] = Field(max_length=10)
    forbiddenDrifts: list[str] = Field(max_length=10)


class DesignSpaceAnalysis(StrictModel):
    opportunityAnchor: OpportunityAnchor
    semanticAnchors: dict[str, str]
    sourceLocks: dict[str, str]
    explicitBusinessLocks: dict[str, str]
    openDimensions: list[str]
    constrainedDimensions: list[str]
    explorationBreadth: ExplorationBreadth
    diversityCapacity: int = Field(ge=0, le=5)
    suggestedMaxConcepts: int = Field(ge=0, le=5)
    rationaleSummary: str


class MechanicsDescriptor(StrictModel):
    solutionMechanismType: str
    supplyModel: str
    fulfillmentModel: str
    platformRoleType: str
    partnerStructureType: str
    transactionModel: str
    commercialModel: str
    customerInteractionModel: str


class PortfolioPlanDraft(StrictModel):
    title: str
    oneLineConcept: str
    coreMechanism: str
    customerInteraction: str
    valueDelivery: str
    operatingApproach: str
    partnerApproach: str
    transactionApproach: str
    commercialApproach: str
    fulfillmentApproach: str
    mechanics: MechanicsDescriptor
    differentiatingMechanics: list[str] = Field(min_length=2, max_length=12)
    mainChanges: list[str]
    secondaryChanges: list[str]
    legalRiskHints: list[str]
    reasonForPortfolioRole: str


class PlanDraftPool(StrictModel):
    plans: list[PortfolioPlanDraft] = Field(min_length=1, max_length=8)


class PortfolioPlan(PortfolioPlanDraft):
    planId: str
    preservedAnchors: dict[str, str]
    preservedLocks: dict[str, str]


class DiversityAssessment(StrictModel):
    entityA: str
    entityB: str
    decision: str
    overlap: list[str]
    materialDifferences: list[str]
    whyDistinct: str
    deterministicLevel: str = "LEVEL_1"
    semanticJudgeUsed: bool = False


class RejectedPlan(StrictModel):
    planId: str
    reasonCode: FailureCode
    safeSummary: str
    conflictPlanId: str | None = None


class PlanValidationResult(StrictModel):
    acceptedPlans: list[PortfolioPlan]
    rejectedPlans: list[RejectedPlan]
    diversity: list[DiversityAssessment]


class CandidateEnvelope(StrictModel):
    candidateId: str
    planId: str
    lineageId: str
    parentCandidateId: str | None = None
    redesignRound: int = Field(default=0, ge=0, le=2)
    mechanics: MechanicsDescriptor
    candidate: ConceptCandidateResult


class CandidateValidation(StrictModel):
    candidateId: str
    schemaValid: bool
    hardLockPreserved: bool
    semanticAnchorPreserved: bool
    planFidelity: bool
    anchorDecision: str = "PASS"
    fidelityDecision: str = "PASS"
    accepted: bool
    reasonCodes: list[FailureCode] = Field(default_factory=list)
    safeSummary: str


class LegalPrecheck(StrictModel):
    candidateId: str
    label: str = "Structural risk precheck — not final legal review"
    directSeller: bool
    intermediary: bool
    regulatedPhysicalActivity: bool
    personalDataDependency: bool
    qualificationDependency: bool
    riskHints: list[str]


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

    @property
    def accepted(self) -> bool:
        return self.decisionStatus in {"ACCEPTED", "USER_EDITED_ACCEPTED"} and self.finalValue is not None


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
    externalProviderCalls: int = 0
    totalProviderCalls: int = 0
    callsByStage: dict[str, int] = Field(default_factory=dict)
    externalCallsByStage: dict[str, int] = Field(default_factory=dict)
    retries: int = 0
    durationMs: int = 0
    modeCounts: dict[str, int] = Field(default_factory=dict)
    tokenUsage: dict[str, int] | None = None
    reportedCost: float | None = None


class SchemaCompatibilityItem(StrictModel):
    schemaName: str
    status: str
    failures: list[dict[str, str]] = Field(default_factory=list)


class SchemaPreflightReport(StrictModel):
    status: str
    schemas: list[SchemaCompatibilityItem]
    providerCalls: int = 0


class SemanticDistinctnessResult(StrictModel):
    decision: str
    overlappingMechanics: list[str] = Field(max_length=12)
    materiallyDifferentMechanics: list[str] = Field(max_length=12)
    safeSummary: str


class SemanticFidelityResult(StrictModel):
    decision: str
    matchedMechanics: list[str] = Field(max_length=12)
    missingMechanics: list[str] = Field(max_length=12)
    safeSummary: str


class RunSummary(StrictModel):
    safety: str
    requestedMaximum: int
    planned: int
    planDuplicatesRemoved: int
    candidatesExpanded: int
    legalAccepted: int
    legalRedesigned: int
    replanned: int
    finalPortfolio: int
    portfolioStatus: PortfolioStatus
    selectedConcept: str | None
    downstreamHandoff: str
    providerCalls: int
    totalDurationMs: int


class ConceptPortfolioResult(StrictModel):
    runId: str
    runStatus: PortfolioStatus
    runtimeStage: RunStage
    requestedMaxConcepts: int = Field(ge=1, le=5)
    producedConceptCount: int = Field(ge=0, le=5)
    concepts: list[CandidateEnvelope]
    rejectedPlans: list[RejectedPlan]
    legalSummaries: list[LegalReview]
    requiredInputs: list[dict[str, Any]]
    trace: list[TraceEvent]
    providerUsage: ProviderUsage
    downstreamReadiness: str
    selectedConceptId: str | None = None
    handoff: DownstreamHandoff | None = None
    runSummary: RunSummary | None = None

    @model_validator(mode="after")
    def terminal_and_count_consistent(self):
        if self.runtimeStage not in {RunStage.READY, RunStage.NEEDS_INPUT, RunStage.FAILED}:
            raise ValueError("완료 결과는 반드시 terminal runtime stage여야 합니다")
        if self.producedConceptCount != len(self.concepts):
            raise ValueError("producedConceptCount가 concepts 길이와 다릅니다")
        if self.producedConceptCount > self.requestedMaxConcepts:
            raise ValueError("producedConceptCount는 requestedMaxConcepts 이하여야 합니다")
        return self
