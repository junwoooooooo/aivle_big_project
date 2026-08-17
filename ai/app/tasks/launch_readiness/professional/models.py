from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ProfessionalAnalysisRequest(StrictModel):
    moduleType: Literal["TECHNOLOGY", "OPERATIONS", "LAUNCH"]
    input: dict[str, str]


class Dimension(StrictModel):
    name: str = Field(min_length=2, max_length=80)
    score: int = Field(ge=0, le=100)
    status: Literal["READY", "CAUTION", "RISK"]
    finding: str = Field(min_length=20, max_length=700)


class Risk(StrictModel):
    title: str = Field(min_length=2, max_length=120)
    severity: Literal["CRITICAL", "HIGH", "MEDIUM", "LOW"]
    likelihood: Literal["HIGH", "MEDIUM", "LOW"]
    impact: str = Field(min_length=15, max_length=500)
    mitigation: str = Field(min_length=15, max_length=600)


class Gate(StrictModel):
    title: str = Field(min_length=2, max_length=120)
    status: Literal["PASS", "OPEN", "BLOCKED"]
    criterion: str = Field(min_length=20, max_length=600)
    evidenceNeeded: str = Field(min_length=10, max_length=400)


class Action(StrictModel):
    priority: Literal["P0", "P1", "P2"]
    title: str = Field(min_length=2, max_length=120)
    owner: str = Field(min_length=1, max_length=80)
    completionEvidence: str = Field(min_length=10, max_length=400)


class ProfessionalAnalysis(StrictModel):
    decision: Literal["READY", "CONDITIONAL", "REVISE"]
    score: int = Field(ge=0, le=100)
    summary: str = Field(min_length=50, max_length=1200)
    dimensions: list[Dimension] = Field(min_length=4, max_length=8)
    risks: list[Risk] = Field(min_length=3, max_length=8)
    gates: list[Gate] = Field(min_length=4, max_length=8)
    actions: list[Action] = Field(min_length=3, max_length=8)


class AnalysisReview(StrictModel):
    passed: bool
    score: int = Field(ge=0, le=100)
    feedback: list[str] = Field(min_length=1, max_length=8)
    unsupportedClaims: list[str] = Field(max_length=8)
