from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


VariationFocus = Literal[
    "CUSTOMER_EXPERIENCE", "OPERATING_MODEL_AND_PARTNERS", "REVENUE_AND_PRICING",
    "CHANNEL_AND_SCALE", "LOW_RISK_FAST_EXECUTION",
]


class BriefField(StrictModel):
    fieldKey: str = Field(min_length=1, max_length=80)
    value: str = Field(min_length=1, max_length=20_000)


class ConceptCandidateInput(StrictModel):
    ideaBriefSnapshotId: str = Field(min_length=1, max_length=64)
    variationFocus: VariationFocus
    fields: list[BriefField] = Field(min_length=1, max_length=32)


class ConceptCandidateResult(StrictModel):
    conceptName: str = Field(min_length=1, max_length=200)
    oneLineSummary: str = Field(min_length=1, max_length=500)
    targetSegment: str = Field(min_length=1, max_length=1000)
    problemScenario: str = Field(min_length=1, max_length=2000)
    valueProposition: str = Field(min_length=1, max_length=2000)
    solutionMechanism: str = Field(min_length=1, max_length=3000)
    actorRoles: list[str] = Field(min_length=1, max_length=20)
    platformRole: str = Field(min_length=1, max_length=1000)
    transactionFlow: list[str] = Field(min_length=1, max_length=20)
    dataFlow: list[str] = Field(max_length=20)
    physicalActivities: list[str] = Field(max_length=20)
    partnerRequirements: list[str] = Field(max_length=20)
    featureSet: list[str] = Field(min_length=1, max_length=30)
    channelHypothesis: str = Field(min_length=1, max_length=1000)
    pricingHypothesis: str = Field(min_length=1, max_length=1000)
    revenueModelHypothesis: str = Field(min_length=1, max_length=1000)
    operatingModel: str = Field(min_length=1, max_length=2000)
    assumptions: list[str] = Field(max_length=20)
    risks: list[str] = Field(max_length=20)
    legalImplementationHypothesis: str = Field(min_length=1, max_length=2000)
