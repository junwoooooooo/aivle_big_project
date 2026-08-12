from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class AdvisoryInput(StrictModel):
    concept: dict[str, Any]
    market: dict[str, Any]
    bm: dict[str, Any] = Field(default_factory=dict)
    legalHandoff: dict[str, Any] | None = None


class Gate(StrictModel):
    title: str
    owner: str
    status: Literal["OPEN", "READY", "BLOCKED"]
    exitCriteria: str
    basisIds: list[str] = Field(default_factory=list)


class Advice(StrictModel):
    area: Literal["MARKET_BM", "PRODUCT_TECH", "OPERATIONS", "RISK_GATE", "PARTNER_SUPPLY", "PILOT", "SCALE"]
    priority: Literal["CRITICAL", "HIGH", "MEDIUM", "LOW"]
    advice: str
    validationMethod: str
    basisIds: list[str] = Field(default_factory=list)


class CostAdvice(StrictModel):
    category: str
    driver: str
    trigger: str
    measurementUnit: str
    behavior: Literal["FIXED", "VARIABLE", "STEP", "UNKNOWN"]
    pilotMeasurement: str
    note: str
    basisIds: list[str] = Field(default_factory=list)


class Readiness(StrictModel):
    topic: Literal["DATA_AI", "CUSTOMER_TRUST", "OBSERVABILITY_SLA", "SCALABILITY"]
    priority: Literal["CRITICAL", "HIGH", "MEDIUM", "LOW"]
    assessment: str
    watchouts: list[str]
    controls: list[str]
    validationMethod: str
    basisIds: list[str] = Field(default_factory=list)


class PilotPlan(StrictModel):
    objective: str
    scope: list[str]
    metrics: list[str]
    stopConditions: list[str]
    scaleConditions: list[str]


class AdvisoryResult(StrictModel):
    productName: str = "상용화 검증 대상"
    decision: Literal["GO", "CONDITIONAL_GO", "REVISE", "NO_GO"]
    summary: str
    advice: list[Advice] = Field(min_length=1, max_length=10)
    gates: list[Gate] = Field(min_length=1, max_length=10)
    operatingCosts: list[CostAdvice] = Field(min_length=1, max_length=8)
    readiness: list[Readiness] = Field(min_length=1, max_length=4)
    pilotPlan: PilotPlan
    disclaimer: str
    layer1Facts: list[dict[str, Any]] = Field(default_factory=list)
    layer2Evidence: list[dict[str, Any]] = Field(default_factory=list)


class AdvisoryExpansion(StrictModel):
    operatingCosts: list[CostAdvice] = Field(min_length=5, max_length=8)
    readiness: list[Readiness] = Field(min_length=4, max_length=4)
    gates: list[Gate] = Field(min_length=6, max_length=10)
