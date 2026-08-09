"""Provider-owned Plan Draft를 system-owned canonical Plan으로 정규화한다."""

from __future__ import annotations

from .models import DesignSpaceAnalysis, PortfolioPlan, PortfolioPlanDraft
from .mechanics import GenericConceptNormalizer


def normalize_plan_drafts(drafts: list[PortfolioPlanDraft], analysis: DesignSpaceAnalysis,
                          *, start_index: int = 1, prefix: str = "P") -> list[PortfolioPlan]:
    return [PortfolioPlan(
        **draft.model_dump(mode="json"), planId=f"{prefix}{index}",
        descriptor=GenericConceptNormalizer.from_plan(draft),
        preservedAnchors=dict(analysis.semanticAnchors),
        preservedLocks=dict(analysis.explicitBusinessLocks),
    ) for index, draft in enumerate(drafts, start_index)]
