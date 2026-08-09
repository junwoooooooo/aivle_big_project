"""문장 exact equality 없이 Plan의 핵심 mechanics 구현 여부를 판정한다."""

from __future__ import annotations

import re

from app.tasks.concept_candidate.models import ConceptCandidateResult

from .distinctness import descriptor_values
from .models import MechanicsDescriptor, PortfolioPlan


def _tokens(value) -> set[str]:
    text = " ".join(value) if isinstance(value, list) else str(value)
    return {token for token in re.findall(r"[0-9a-z가-힣]+", text.casefold()) if len(token) >= 2}


def deterministic_plan_fidelity(plan: PortfolioPlan, candidate: ConceptCandidateResult,
                                candidate_mechanics: MechanicsDescriptor | None = None) -> tuple[str, list[str], list[str]]:
    if candidate_mechanics is None:
        from .mechanics import derive_candidate_mechanics
        candidate_mechanics = derive_candidate_mechanics(candidate)
    planned, actual = descriptor_values(plan.mechanics), descriptor_values(candidate_mechanics)
    matched = [key for key in planned if planned[key] == actual[key] and planned[key] != "OTHER"]
    missing = [key for key in planned if key not in matched]
    if "solutionMechanismType" in matched and len(matched) >= 3:
        return "PASS", matched, missing
    if "solutionMechanismType" not in matched and len(matched) <= 1:
        return "FAIL", matched, missing
    return "AMBIGUOUS", matched, missing
