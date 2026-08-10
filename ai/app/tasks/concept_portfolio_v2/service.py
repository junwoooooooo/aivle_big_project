"""Notebook과 동일한 ConceptPortfolioEngine을 사용하는 production entrypoint."""

from __future__ import annotations

from typing import Any

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway, ProviderMode


async def execute_concept_portfolio_v2(
    task_input: dict[str, Any], *, engine: ConceptPortfolioEngine | None = None,
) -> dict[str, Any]:
    payload = task_input.get("seed") if isinstance(task_input.get("seed"), dict) else task_input
    max_concepts = int(task_input.get("maxConcepts", 5))
    production_engine = engine or ConceptPortfolioEngine(
        mode=ProviderMode.LIVE, gateway=ProviderGateway(ProviderMode.LIVE))
    result = await production_engine.run_full(
        payload, max_concepts=max_concepts, auto_confirm_hypotheses=False)
    return result.model_dump(mode="json")
