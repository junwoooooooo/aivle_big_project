from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class TechOpsAdvisoryInput(StrictModel):
    projectId: int
    techOpsInputSnapshotId: str
    sourceMarketSeedSnapshotId: str
    sourceMarketResearchVersionId: int
    sourceBusinessModelVersionId: int
    sourcePortfolioSelectionId: int
    selectedConceptId: str
    selectedConceptHash: str
    conceptHandoff: dict[str, Any]
    legalHandoff: dict[str, Any] | None
    marketResult: dict[str, Any]
    businessModelResult: dict[str, Any]
    techOpsInputSnapshot: dict[str, Any]


class ScaledTechOpsInput(StrictModel):
    productSummary: str
    layer1Facts: list[dict[str, Any]] = Field(max_length=180)
    advisorFacts: list[dict[str, Any]] = Field(max_length=100)
    layer2Evidence: list[dict[str, Any]] = Field(max_length=24)
    userEvidence: list[dict[str, Any]] = Field(max_length=40)
