"""Frozen Core의 trace와 continuation 경계를 읽기 전용으로 관측한다."""

from __future__ import annotations

import logging
from collections.abc import Callable
from typing import Any

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode
from app.concept_portfolio_v2.models import (
    CandidateEnvelope,
    CanonicalSeed,
    DesignSpaceAnalysis,
    ExplorationBreadth,
    LegalReview,
    PortfolioPlan,
)

from .models import ProductionTraceEvent


logger = logging.getLogger(__name__)
TraceSink = Callable[[ProductionTraceEvent], None]


class ProductionObservedConceptPortfolioEngine(ConceptPortfolioEngine):
    """Core 결과를 바꾸지 않고 trace fan-out과 Candidate 캡처만 수행한다."""

    def __init__(
        self,
        mode: ProviderMode | str = ProviderMode.MOCK,
        *,
        gateway: ProviderGateway | None = None,
        max_replans: int = 1,
        max_redesigns: int = 1,
        max_planning_rounds: int = 2,
        event_sink: TraceSink | None = None,
    ):
        self._production_event_sink = event_sink
        self._production_candidates: dict[str, CandidateEnvelope] = {}
        self._production_plans: dict[str, PortfolioPlan] = {}
        self._production_design: DesignSpaceAnalysis | None = None
        super().__init__(
            mode=mode,
            gateway=gateway,
            max_replans=max_replans,
            max_redesigns=max_redesigns,
            max_planning_rounds=max_planning_rounds,
        )

    def set_event_sink(self, event_sink: TraceSink | None) -> None:
        self._production_event_sink = event_sink

    def _reset(self):
        self._production_candidates.clear()
        self._production_plans.clear()
        self._production_design = None
        super()._reset()

    def _event(self, stage, action: str, status: str, summary: str, **kwargs: Any):
        super()._event(stage, action, status, summary, **kwargs)
        sink = self._production_event_sink
        if sink is None:
            return
        try:
            sink(ProductionTraceEvent.from_core(self.trace[-1], len(self.trace)))
        except Exception as failure:  # sink는 Core 성공 여부의 authority가 아니다.
            logger.warning(
                "CPV2 production trace sink failed eventStage=%s eventAction=%s exceptionType=%s",
                getattr(stage, "value", str(stage)),
                action,
                failure.__class__.__name__,
                exc_info=True,
            )

    async def analyze_seed(
        self,
        payload: dict[str, Any] | CanonicalSeed,
        exploration_override: ExplorationBreadth | str | None = None,
    ) -> DesignSpaceAnalysis:
        design = await super().analyze_seed(payload, exploration_override)
        self._production_design = design.model_copy(deep=True)
        return design

    async def expand_plan(
        self,
        seed: CanonicalSeed,
        plan: PortfolioPlan,
        candidate_index: int,
        *,
        candidate_id: str | None = None,
        lineage_id: str | None = None,
        recovery_source: str = "INITIAL",
    ) -> CandidateEnvelope:
        envelope = await super().expand_plan(
            seed,
            plan,
            candidate_index,
            candidate_id=candidate_id,
            lineage_id=lineage_id,
            recovery_source=recovery_source,
        )
        self._production_plans[plan.planId] = plan.model_copy(deep=True)
        return envelope

    async def review_legal_candidate(
        self, seed: CanonicalSeed, envelope: CandidateEnvelope
    ) -> LegalReview:
        self._production_candidates[envelope.candidateId] = envelope.model_copy(deep=True)
        return await super().review_legal_candidate(seed, envelope)

    def continuation_candidate(self, candidate_id: str) -> CandidateEnvelope | None:
        value = self._production_candidates.get(candidate_id)
        return value.model_copy(deep=True) if value is not None else None

    def continuation_plan(self, plan_id: str) -> PortfolioPlan | None:
        value = self._production_plans.get(plan_id)
        return value.model_copy(deep=True) if value is not None else None

    def continuation_design(self) -> DesignSpaceAnalysis | None:
        value = self._production_design
        return value.model_copy(deep=True) if value is not None else None
