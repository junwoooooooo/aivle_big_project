from typing import Literal

from pydantic import Field, model_validator

from app.tasks.concept_candidate.models import (
    ConceptCandidateResult, PreMarketSomHypothesis, PreMarketSomShareHypothesis, StrictModel,
)


HypothesisType = Literal[
    "TARGET_REGION", "REVENUE_MODEL", "PRICE", "CHANNELS", "DIFFERENTIATORS",
    "PRE_MARKET_SOM_SHARE", "PRE_MARKET_SOM",
]
HypothesisValue = str | PreMarketSomShareHypothesis | PreMarketSomHypothesis


class ConceptHypothesisAlternativeInput(StrictModel):
    hypothesisType: HypothesisType
    rejectedValue: HypothesisValue
    proposalVersion: int = Field(strict=True, ge=2, le=20)
    candidate: ConceptCandidateResult


class ConceptHypothesisAlternativeResult(StrictModel):
    hypothesisType: HypothesisType
    proposedValue: HypothesisValue
    source: Literal["AI_HYPOTHESIS"]
    decisionStatus: Literal["ALTERNATIVE_PROPOSED"]
    proposalVersion: int = Field(strict=True, ge=2, le=20)

    @model_validator(mode="after")
    def value_matches_type(self):
        if self.hypothesisType == "PRE_MARKET_SOM_SHARE" and not isinstance(self.proposedValue, PreMarketSomShareHypothesis):
            raise ValueError("share hypothesis requires structured value")
        if self.hypothesisType == "PRE_MARKET_SOM" and not isinstance(self.proposedValue, PreMarketSomHypothesis):
            raise ValueError("SOM hypothesis requires structured value")
        if self.hypothesisType not in {"PRE_MARKET_SOM_SHARE", "PRE_MARKET_SOM"} and not isinstance(self.proposedValue, str):
            raise ValueError("text hypothesis requires text value")
        return self
