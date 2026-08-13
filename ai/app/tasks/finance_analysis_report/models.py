from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class FinanceAnalysisReportInput(StrictModel):
    snapshotId: str = Field(min_length=1, max_length=64)
    snapshotHash: str = Field(pattern=r"^sha256:[0-9a-f]{64}$")
    sourceMarketResearchVersionId: int = Field(gt=0)
    sourceBusinessModelVersionId: int = Field(gt=0)
    sourceTechOpsSnapshotId: str | None = Field(default=None, min_length=1, max_length=64)
    deterministicResult: dict


class FinanceAnalysisReportResult(StrictModel):
    headline: str = Field(min_length=1, max_length=300)
    findings: list[str] = Field(min_length=1, max_length=5)
    cautions: list[str] = Field(min_length=1, max_length=5)
    recommendedActions: list[str] = Field(min_length=1, max_length=5)
    disclaimer: str = Field(min_length=1, max_length=500)
    source: Literal["AI_GENERATED_REPORT"]
    providerStatus: Literal["SUCCEEDED"]
    # OpenAI strict structured output requires every property to be required.
    # The success contract therefore carries an explicit JSON null instead of
    # an optional/defaulted field that the provider may omit.
    safeFailureReason: None
