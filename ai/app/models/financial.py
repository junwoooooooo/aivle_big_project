from pydantic import BaseModel, ConfigDict, Field

class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")

class FinancialReportRequest(StrictModel):
    input: dict

class FinancialReportResponse(StrictModel):
    headline: str = Field(min_length=1, max_length=300)
    findings: list[str] = Field(min_length=1, max_length=5)
    cautions: list[str] = Field(min_length=1, max_length=5)
    recommendedActions: list[str] = Field(min_length=1, max_length=5)
    disclaimer: str = Field(min_length=1, max_length=500)
