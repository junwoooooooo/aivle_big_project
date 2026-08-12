from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Gate(StrictModel):
    title: str = Field(min_length=3)
    owner: str = Field(min_length=1)
    status: Literal["OPEN", "READY", "BLOCKED"]
    exitCriteria: str = Field(min_length=20)
    basisIds: list[str] = Field(min_length=1)


class Advice(StrictModel):
    area: Literal["MARKET_BM", "PRODUCT_TECH", "OPERATIONS", "RISK_GATE", "PARTNER_SUPPLY", "PILOT", "SCALE"]
    priority: Literal["CRITICAL", "HIGH", "MEDIUM", "LOW"]
    advice: str = Field(min_length=30)
    validationMethod: str = Field(min_length=10)
    basisIds: list[str] = Field(min_length=1)


class CostAdvice(StrictModel):
    category: str
    driver: str
    trigger: str
    measurementUnit: str
    behavior: Literal["FIXED", "VARIABLE", "STEP", "UNKNOWN"]
    pilotMeasurement: str
    note: str
    basisIds: list[str] = Field(min_length=1)


class Readiness(StrictModel):
    topic: Literal["DATA_AI", "CUSTOMER_TRUST", "OBSERVABILITY_SLA", "SCALABILITY"]
    priority: Literal["CRITICAL", "HIGH", "MEDIUM", "LOW"]
    assessment: str
    watchouts: list[str] = Field(min_length=2)
    controls: list[str] = Field(min_length=2)
    validationMethod: str
    basisIds: list[str] = Field(min_length=1)


class PilotPlan(StrictModel):
    objective: str
    scope: list[str] = Field(min_length=1)
    metrics: list[str] = Field(min_length=1)
    stopConditions: list[str] = Field(min_length=1)
    scaleConditions: list[str] = Field(min_length=1)


class AdvisoryResult(StrictModel):
    productName: str
    decision: Literal["GO", "CONDITIONAL_GO", "REVISE", "NO_GO"]
    summary: str = Field(min_length=50)
    advice: list[Advice] = Field(min_length=7, max_length=7)
    gates: list[Gate] = Field(min_length=6, max_length=10)
    operatingCosts: list[CostAdvice] = Field(min_length=5, max_length=8)
    readiness: list[Readiness] = Field(min_length=4, max_length=4)
    pilotPlan: PilotPlan
    disclaimer: str = Field(min_length=20)
    layer1Facts: list[dict[str, Any]]
    layer2Evidence: list[dict[str, Any]]

    @model_validator(mode="after")
    def exact_sets_and_basis(self):
        areas = {item.area for item in self.advice}
        topics = {item.topic for item in self.readiness}
        if areas != {"MARKET_BM", "PRODUCT_TECH", "OPERATIONS", "RISK_GATE", "PARTNER_SUPPLY", "PILOT", "SCALE"}:
            raise ValueError("advice areas must be exact")
        if topics != {"DATA_AI", "CUSTOMER_TRUST", "OBSERVABILITY_SLA", "SCALABILITY"}:
            raise ValueError("readiness topics must be exact")
        ids = {row.get("factId") for row in self.layer1Facts} | {row.get("evidenceId") for row in self.layer2Evidence}
        for item in [*self.advice, *self.gates, *self.operatingCosts, *self.readiness]:
            if any(basis not in ids for basis in item.basisIds):
                raise ValueError("unknown basis id")
        return self
