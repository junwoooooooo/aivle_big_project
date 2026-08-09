"""Provider-owned Plan Draft를 system-owned canonical Plan으로 정규화한다."""

from __future__ import annotations

from .models import DesignSpaceAnalysis, PortfolioPlan, PortfolioPlanDraft


def normalize_plan_drafts(drafts: list[PortfolioPlanDraft], analysis: DesignSpaceAnalysis) -> list[PortfolioPlan]:
    return [PortfolioPlan(
        **draft.model_dump(mode="json"), planId=f"P{index}",
        preservedAnchors=dict(analysis.semanticAnchors),
        preservedLocks=dict(analysis.explicitBusinessLocks),
    ) for index, draft in enumerate(drafts, 1)]
