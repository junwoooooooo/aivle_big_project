from typing import Literal

from pydantic import ConfigDict, BaseModel, Field

from app.contracts.concept_fingerprint import BusinessFingerprint, FingerprintDimension


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ConceptDistinctnessJudgeInput(StrictModel):
    candidateA: BusinessFingerprint
    candidateB: BusinessFingerprint


class ConceptDistinctnessJudgeResult(StrictModel):
    decision: Literal["DISTINCT", "DUPLICATE"]
    overlappingDimensions: list[FingerprintDimension] = Field(max_length=21)
    materiallyDifferentDimensions: list[FingerprintDimension] = Field(max_length=21)
    safeSummary: str = Field(min_length=1, max_length=1000)
