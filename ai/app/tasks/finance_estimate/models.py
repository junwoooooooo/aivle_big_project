from typing import Literal
from pydantic import BaseModel, ConfigDict, Field, model_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


EstimateField = Literal[
    "annualFixedLaborCost","annualFixedRentAndManagementCost","annualFixedInfrastructureCost",
    "initialDevelopmentAndRnDCost","initialEquipmentAndInfrastructureCost","initialPatentAndLicensingCost",
    "threeYearTargets","totalMarketingCost","totalSalesCost","unitVariableCost","paymentFee",
    "partnerPayout","shippingCost","customerIncrementalInfraCost",
    "unitPrice","monthlySubscriptionPrice","monthlyChurnRate","newCustomerCount",
]


class FinanceEstimateInput(StrictModel):
    contextJson: str = Field(min_length=1,max_length=100_000)
    fieldKey: EstimateField
    proposalVersion: int = Field(strict=True,ge=1,le=20)
    rejectedProposalJson: str = Field(max_length=20_000)


class Money(StrictModel):
    amount: float = Field(strict=True,ge=0)
    currency: str = Field(min_length=1,max_length=20)


class YearValue(StrictModel):
    year: int = Field(strict=True,ge=1,le=3)
    value: float = Field(strict=True,ge=0)


class Targets(StrictModel):
    metric: Literal["salesVolume","customerCount","subscriberCount","transactionCount"]
    unit: str = Field(min_length=1,max_length=50)
    years: list[YearValue] = Field(min_length=3,max_length=3)


class Rate(StrictModel):
    percent: float = Field(strict=True, ge=0, le=100)

class Count(StrictModel):
    count: int = Field(strict=True, ge=1)


class FinanceEstimateResult(StrictModel):
    fieldKey: EstimateField
    proposedValue: Money | Targets | Rate | Count
    assumptions: list[str] = Field(min_length=1,max_length=20)
    explanation: str = Field(min_length=1,max_length=2000)
    confidence: Literal["LOW","MEDIUM","HIGH"]
    source: Literal["AI_ESTIMATE"]

    @model_validator(mode="after")
    def value_matches_field(self):
        if self.fieldKey == "threeYearTargets" and not isinstance(self.proposedValue,Targets):
            raise ValueError("threeYearTargets requires Targets")
        if self.fieldKey == "monthlyChurnRate" and not isinstance(self.proposedValue, Rate):
            raise ValueError("monthlyChurnRate requires Rate")
        if self.fieldKey == "newCustomerCount" and not isinstance(self.proposedValue, Count):
            raise ValueError("newCustomerCount requires Count")
        if self.fieldKey not in {"threeYearTargets", "monthlyChurnRate", "newCustomerCount"} and not isinstance(self.proposedValue,Money):
            raise ValueError("cost estimate requires Money")
        return self
