"""Concept Portfolio V2 선택 이후 Production action 계약."""

from __future__ import annotations

from typing import Any, Literal

from pydantic import Field, model_validator

from app.concept_portfolio_v2.models import (
    CandidateEnvelope,
    CanonicalSeed,
    DeltaLegalResult,
    DownstreamHandoff,
    HypothesisDecision,
    LegalReview,
)

from .models import StrictModel


SelectionAction = Literal[
    "PREPARE_HYPOTHESES",
    "CONFIRM_HYPOTHESES",
    "PROPOSE_ALTERNATIVE",
    "DELTA_LEGAL",
    "BUILD_HANDOFF",
]
HypothesisType = Literal[
    "TARGET_REGION",
    "REVENUE_MODEL",
    "PRICE",
    "CHANNELS",
    "DIFFERENTIATORS",
    "PRE_MARKET_SOM_SHARE",
    "PRE_MARKET_SOM",
]
HYPOTHESIS_TYPES = tuple(HypothesisType.__args__)


class ProductionBinding(StrictModel):
    projectId: int = Field(gt=0)
    portfolioSelectionId: int = Field(gt=0)
    portfolioConceptId: str = Field(min_length=1, max_length=64)
    marketSeedSnapshotId: str = Field(min_length=1, max_length=64)


class ConceptPortfolioSelectionActionInput(StrictModel):
    action: SelectionAction
    expectedHypothesisRevision: int = Field(default=0, ge=0)
    seed: CanonicalSeed | None = None
    selectedCandidate: CandidateEnvelope | None = None
    baseLegalReview: LegalReview | None = None
    hypotheses: list[HypothesisDecision] = Field(default_factory=list, max_length=7)
    edits: dict[HypothesisType, Any] = Field(default_factory=dict, max_length=7)
    confirmAll: bool = False
    hypothesisType: HypothesisType | None = None
    rejectedValue: Any | None = None
    proposalVersion: int | None = Field(default=None, ge=2, le=20)
    approvedDeltaLegalResults: list[DeltaLegalResult] = Field(default_factory=list, max_length=5)
    productionBinding: ProductionBinding | None = None

    @model_validator(mode="after")
    def action_payload(self):
        if self.action == "PREPARE_HYPOTHESES":
            self._require(self.seed, self.selectedCandidate, self.baseLegalReview)
        elif self.action == "CONFIRM_HYPOTHESES":
            if len(self.hypotheses) != 7 or not self.confirmAll:
                raise ValueError("7개 가정의 명시적 전체 확인이 필요합니다")
        elif self.action == "PROPOSE_ALTERNATIVE":
            self._require(self.selectedCandidate, self.hypothesisType, self.proposalVersion)
            if self.rejectedValue is None:
                raise ValueError("거절한 가정 값이 필요합니다")
        elif self.action == "DELTA_LEGAL":
            self._require(self.seed, self.selectedCandidate)
            if len(self.hypotheses) != 7:
                raise ValueError("Delta Legal에는 7개 가정이 필요합니다")
        elif self.action == "BUILD_HANDOFF":
            self._require(
                self.seed,
                self.selectedCandidate,
                self.baseLegalReview,
                self.productionBinding,
            )
            if len(self.hypotheses) != 7:
                raise ValueError("Handoff에는 확정된 7개 가정이 필요합니다")
        return self

    @staticmethod
    def _require(*values: object) -> None:
        if any(value is None for value in values):
            raise ValueError("action 입력 계약이 불완전합니다")


class ConceptPortfolioSelectionActionResult(StrictModel):
    contract: Literal["concept-portfolio-v2-selection-action-result-v1"] = (
        "concept-portfolio-v2-selection-action-result-v1"
    )
    schemaVersion: Literal["1.0"] = "1.0"
    action: SelectionAction
    hypotheses: list[HypothesisDecision] = Field(default_factory=list, max_length=7)
    alternative: HypothesisDecision | None = None
    deltaLegalResult: DeltaLegalResult | None = None
    handoff: DownstreamHandoff | None = None
    marketSeedSnapshotHash: str | None = Field(default=None, pattern=r"^sha256:[0-9a-f]{64}$")
