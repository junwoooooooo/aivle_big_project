from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.contracts.concept_fingerprint import BusinessFingerprint


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


ConceptGenerationStrategy = Literal["EXPLORE", "REFINE", "AS_IS"]
VariationFocus = Literal[
    "CUSTOMER_EXPERIENCE", "OPERATING_MODEL_AND_PARTNERS", "REVENUE_AND_PRICING",
    "CHANNEL_AND_SCALE", "LOW_RISK_FAST_EXECUTION",
]
ValueSource = Literal[
    "USER_INPUT", "USER_CONFIRMED", "AI_DERIVED", "AI_HYPOTHESIS", "CONCEPT_GENERATED", "ANALYSIS_RESULT",
]
Authority = Literal["LOCKED", "REVIEWABLE", "OPEN"]
DecisionStatus = Literal[
    "PROPOSED", "ACCEPTED", "USER_EDITED_ACCEPTED", "REJECTED", "ALTERNATIVE_PROPOSED",
]
SemanticField = Literal[
    "conceptName", "conceptDefinition", "introduction", "coreValue", "targetUsers",
    "industryCategory", "researchScope",
    "targetRegion", "revenueModel", "price", "channels", "differentiators",
    "preMarketSomShareHypothesis", "preMarketSomHypothesis", "actorRoles", "platformRole",
    "problemScenario", "solutionMechanism", "featureSet", "operatingModel", "partnerModel",
    "providerRole", "sellerRole", "intermediaryRole",
    "transactionFlow", "paymentFlow", "personalDataUsage",
    "physicalActivities", "partnerRequirements", "qualificationRequirements", "advertisingClaims",
]


class BriefField(StrictModel):
    fieldKey: str = Field(min_length=1, max_length=80)
    value: str = Field(min_length=1, max_length=20_000)
    source: ValueSource
    authority: Authority


class ReplacementContext(StrictModel):
    round: int = Field(strict=True, ge=1, le=2)
    previousCandidate: BusinessFingerprint
    rejectionReason: str = Field(min_length=1, max_length=80)
    conflictSource: Literal[
        "ELIGIBLE_CONCEPT", "CURRENT_SLOT_HISTORY", "CANDIDATE_VALIDATION", "LEGAL_REVIEW",
    ]
    closestConflict: BusinessFingerprint | None = None
    overlappingDimensions: list[str] = Field(max_length=21)
    materiallyDifferentDimensions: list[str] = Field(max_length=21)
    mustChangeDimensions: list[str] = Field(min_length=2, max_length=7)
    safeCorrectionInstruction: str = Field(min_length=1, max_length=1000)


class ConceptCandidateInput(StrictModel):
    ideaBriefSnapshotId: str = Field(min_length=1, max_length=64)
    generationStrategy: ConceptGenerationStrategy
    candidateIndex: int = Field(strict=True, ge=1, le=5)
    originalCandidate: bool
    diversityFocus: VariationFocus
    fields: list[BriefField] = Field(min_length=3, max_length=32)
    acceptedConceptFingerprints: list[BusinessFingerprint] = Field(default_factory=list, max_length=5)
    rejectedConceptFingerprints: list[BusinessFingerprint] = Field(default_factory=list, max_length=15)
    currentSlotPreviousFingerprints: list[BusinessFingerprint] = Field(default_factory=list, max_length=5)
    replacementContext: ReplacementContext | None = None

    @model_validator(mode="after")
    def original_is_only_first_as_is_candidate(self):
        expected = self.generationStrategy == "AS_IS" and self.candidateIndex == 1
        if self.originalCandidate != expected:
            raise ValueError("originalCandidate must identify Candidate 1 of AS_IS generation")
        return self


class ValueSemantics(StrictModel):
    fieldKey: SemanticField
    source: ValueSource
    authority: Authority
    decision: DecisionStatus


class PreMarketSomShareHypothesis(StrictModel):
    targetSharePercent: float = Field(strict=True, gt=0, le=100)
    horizonYears: int = Field(strict=True, ge=1, le=10)
    rationale: str = Field(min_length=1, max_length=2000)
    assumptions: list[str] = Field(min_length=1, max_length=20)


class PreMarketSomHypothesis(StrictModel):
    amount: float = Field(strict=True, ge=0)
    currency: str = Field(min_length=1, max_length=10)
    period: str = Field(min_length=1, max_length=100)
    calculationBasis: str = Field(min_length=1, max_length=2000)
    assumptions: list[str] = Field(min_length=1, max_length=20)
    confidence: Literal["LOW", "MEDIUM", "HIGH"]


class ConceptCandidateDraft(StrictModel):
    """Provider-owned business content without system-owned governance metadata."""

    conceptName: str = Field(min_length=1, max_length=200)
    conceptDefinition: str = Field(min_length=1, max_length=1000)
    introduction: str = Field(min_length=1, max_length=2000)
    coreValue: str = Field(min_length=1, max_length=2000)
    targetUsers: str = Field(min_length=1, max_length=1000)
    industryCategory: str = Field(min_length=1, max_length=500)
    researchScope: str = Field(min_length=1, max_length=1000)
    targetRegion: str = Field(min_length=1, max_length=1000)
    revenueModel: str = Field(min_length=1, max_length=1000)
    price: str = Field(min_length=1, max_length=1000)
    channels: str = Field(min_length=1, max_length=1000)
    differentiators: str = Field(min_length=1, max_length=2000)
    preMarketSomShareHypothesis: PreMarketSomShareHypothesis
    preMarketSomHypothesis: PreMarketSomHypothesis
    problemScenario: str = Field(min_length=1, max_length=2000)
    solutionMechanism: str = Field(min_length=1, max_length=3000)
    featureSet: list[str] = Field(min_length=1, max_length=30)
    actorRoles: list[str] = Field(min_length=1, max_length=20)
    platformRole: str = Field(min_length=1, max_length=1000)
    operatingModel: str = Field(min_length=1, max_length=2000)
    partnerModel: str = Field(min_length=1, max_length=2000)
    providerRole: str = Field(min_length=1, max_length=1000)
    sellerRole: str = Field(min_length=1, max_length=1000)
    intermediaryRole: str = Field(min_length=1, max_length=1000)
    transactionFlow: list[str] = Field(min_length=1, max_length=20)
    paymentFlow: list[str] = Field(min_length=1, max_length=20)
    personalDataUsage: list[str] = Field(max_length=20)
    physicalActivities: list[str] = Field(max_length=20)
    partnerRequirements: list[str] = Field(max_length=20)
    qualificationRequirements: list[str] = Field(max_length=20)
    advertisingClaims: list[str] = Field(max_length=20)
    constraintCompliance: list[str] = Field(max_length=20)


class ConceptCandidateResult(ConceptCandidateDraft):
    """Canonical CandidateV2 after deterministic normalization."""

    schemaVersion: Literal["2.0"]
    generationStrategy: ConceptGenerationStrategy
    candidateIndex: int = Field(strict=True, ge=1, le=5)
    originalCandidate: bool
    valueSemantics: list[ValueSemantics] = Field(min_length=31, max_length=31)

    @model_validator(mode="after")
    def semantics_are_complete_and_som_is_proposed(self):
        expected = set(SemanticField.__args__)
        actual = [item.fieldKey for item in self.valueSemantics]
        if len(actual) != len(set(actual)) or set(actual) != expected:
            raise ValueError("valueSemantics must cover every governed field exactly once")
        by_key = {item.fieldKey: item for item in self.valueSemantics}
        for key in ("preMarketSomShareHypothesis", "preMarketSomHypothesis"):
            item = by_key[key]
            if item.source != "AI_HYPOTHESIS" or item.authority != "OPEN" or item.decision != "PROPOSED":
                raise ValueError("pre-market SOM values must remain proposed AI hypotheses")
        expected_original = self.generationStrategy == "AS_IS" and self.candidateIndex == 1
        if self.originalCandidate != expected_original:
            raise ValueError("originalCandidate must identify Candidate 1 of AS_IS generation")
        if expected_original:
            for key in ("conceptDefinition", "problemScenario", "targetUsers"):
                item = by_key[key]
                if item.source not in ("USER_INPUT", "USER_CONFIRMED") \
                        or item.authority != "LOCKED" or item.decision != "ACCEPTED":
                    raise ValueError("AS_IS Candidate 1 must preserve original user value semantics")
        return self
