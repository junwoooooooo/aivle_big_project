from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ActorRole(StrictModel):
    actor: str = Field(min_length=1)
    role: str = Field(min_length=1)
    responsibilities: list[str]
    permissions: list[str]
    prohibitedResponsibilities: list[str]


class TransactionStep(StrictModel):
    step: int = Field(ge=1)
    actor: str = Field(min_length=1)
    action: str = Field(min_length=1)
    assetOrPayment: str = Field(min_length=1)
    responsibility: str = Field(min_length=1)


class DataFlow(StrictModel):
    dataType: str = Field(min_length=1)
    collector: str = Field(min_length=1)
    purpose: str = Field(min_length=1)
    processor: str = Field(min_length=1)
    recipient: str = Field(min_length=1)
    retentionHypothesis: str = Field(min_length=1)


class PhysicalActivity(StrictModel):
    activity: str = Field(min_length=1)
    performer: str = Field(min_length=1)
    requiredQualificationHypothesis: str = Field(min_length=1)
    partnerDependency: str = Field(min_length=1)


class ConceptSkeleton(StrictModel):
    conceptName: str = Field(min_length=1)
    oneLineSummary: str = Field(min_length=1)
    targetSegment: Any
    problemScenario: str = Field(min_length=1)
    valueProposition: str = Field(min_length=1)
    solutionMechanism: str = Field(min_length=1)
    actorRoles: list[ActorRole] = Field(min_length=1)
    platformRole: str = Field(min_length=1)
    transactionFlow: list[TransactionStep] = Field(min_length=1)
    dataFlow: list[DataFlow]
    physicalActivities: list[PhysicalActivity]
    partnerRequirements: list[str]
    featureSet: list[str] = Field(min_length=1)
    channelHypothesis: list[str] = Field(min_length=1)
    pricingHypothesis: Any
    revenueModelHypothesis: Any
    operatingModel: Any
    assumptions: list[str]
    risks: list[str]
    legalImplementationHypothesis: str = Field(min_length=1)


class SlotAttemptResult(StrictModel):
    attemptNumber: int = Field(ge=1)
    phase: Literal["INITIAL", "REPAIR", "REDESIGN", "REPLACEMENT"]
    outcome: Literal[
        "VALID", "SCHEMA_INVALID", "TRANSIENT_PROVIDER_FAILURE", "PERMANENT_PROVIDER_FAILURE"
    ]
    candidate: ConceptSkeleton | None
    safeFailureType: str | None
    duplicateStatus: Literal["UNIQUE", "NEAR_DUPLICATE", "DUPLICATE"] | None
    negativeConstraint: dict[str, Any]


class SlotExecutionResult(StrictModel):
    slotIndex: int = Field(ge=0)
    variationFocus: Literal[
        "TARGET_AND_USER_EXPERIENCE",
        "OPERATING_MODEL_AND_PARTNERS",
        "REVENUE_AND_CHANNELS",
    ]
    attempts: list[SlotAttemptResult] = Field(min_length=1)
    accepted: bool


class ConceptExplorationResult(StrictModel):
    slots: list[SlotExecutionResult] = Field(min_length=3, max_length=9)
    acceptedSlotIndices: list[int]
    eligibleCandidateCount: int = Field(ge=0, le=3)
    exhausted: bool

