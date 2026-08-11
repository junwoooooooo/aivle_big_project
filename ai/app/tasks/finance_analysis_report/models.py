from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class FinanceAnalysisReportInput(StrictModel):
    snapshotId: str = Field(min_length=1, max_length=64)
    snapshotHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    sourceMarketResearchVersionId: int = Field(gt=0)
    sourceBusinessModelVersionId: int = Field(gt=0)
    sourceTechOpsSnapshotId: str = Field(min_length=1, max_length=64)
    deterministicResult: dict


class FinanceAnalysisReportResult(StrictModel):
    headline: str = Field(min_length=1, max_length=300)
    findings: list[str] = Field(min_length=1, max_length=5)
    cautions: list[str] = Field(min_length=1, max_length=5)
    recommendedActions: list[str] = Field(min_length=1, max_length=5)
    disclaimer: str = Field(min_length=1, max_length=500)
    source: str = Field(pattern="^AI_GENERATED_REPORT$")
    providerStatus: str = Field(pattern="^SUCCEEDED$")
    safeFailureReason: None = None
