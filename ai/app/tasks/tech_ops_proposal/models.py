from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class TechOpsProposalInput(StrictModel):
    contextJson: str = Field(min_length=1, max_length=100_000)
    proposalVersion: int = Field(strict=True, ge=1, le=20)
    rejectedProposalJson: str = Field(max_length=20_000)


class DeliveryMethod(StrictModel):
    method: str = Field(min_length=1, max_length=3000)
    operatingModel: str = Field(max_length=3000)
    partnerModel: str = Field(max_length=3000)


class Throughput(StrictModel):
    amount: float = Field(strict=True, ge=0)
    unit: str = Field(min_length=1, max_length=100)


class TechOpsProposalResult(StrictModel):
    deliveryOrProductionMethod: DeliveryMethod
    expectedMonthlyThroughputOrSales: Throughput
    technicalSupplyOperationalConstraints: list[str] = Field(min_length=1, max_length=20)
    assumptions: list[str] = Field(min_length=1, max_length=20)
    explanation: str = Field(min_length=1, max_length=2000)
