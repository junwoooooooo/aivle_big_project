from typing import Literal

from pydantic import ConfigDict, BaseModel, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class BusinessFingerprint(StrictModel):
    targetUsers: str = Field(max_length=2000)
    problemScenario: str = Field(max_length=3000)
    coreValue: str = Field(max_length=3000)
    solutionMechanism: str = Field(max_length=4000)
    revenueModel: str = Field(max_length=2000)
    channels: str = Field(max_length=2000)
    platformRole: str = Field(max_length=2000)
    operatingModel: str = Field(max_length=3000)
    partnerModel: str = Field(max_length=3000)
    transactionFlow: list[str] = Field(max_length=20)
    providerRole: str = Field(max_length=2000)
    sellerRole: str = Field(max_length=2000)
    intermediaryRole: str = Field(max_length=2000)


class ConceptDistinctnessJudgeInput(StrictModel):
    candidateA: BusinessFingerprint
    candidateB: BusinessFingerprint


class ConceptDistinctnessJudgeResult(StrictModel):
    decision: Literal["DISTINCT", "DUPLICATE"]
    overlappingDimensions: list[str] = Field(max_length=13)
    materiallyDifferentDimensions: list[str] = Field(max_length=13)
    safeSummary: str = Field(min_length=1, max_length=1000)
