"""문장 exact equality 없이 Plan의 핵심 mechanics 구현 여부를 판정한다."""

from __future__ import annotations

import re

from app.tasks.concept_candidate.models import ConceptCandidateResult

from .distinctness import semantic_key
from .models import PortfolioPlan


def _tokens(value) -> set[str]:
    text = " ".join(value) if isinstance(value, list) else str(value)
    return {token for token in re.findall(r"[0-9a-z가-힣]+", text.casefold()) if len(token) >= 2}


def _matches(left: str, right) -> bool:
    if semantic_key(left) == semantic_key(" ".join(right) if isinstance(right, list) else str(right)):
        return True
    return bool(_tokens(left) & _tokens(right))


def deterministic_plan_fidelity(plan: PortfolioPlan, candidate: ConceptCandidateResult) -> tuple[str, list[str], list[str]]:
    pairs = {
        "solutionMechanism": (plan.coreMechanism, candidate.solutionMechanism),
        "operatingModel": (plan.operatingApproach, candidate.operatingModel),
        "partnerModel": (plan.partnerApproach, candidate.partnerModel),
        "transactionFlow": (plan.transactionApproach, candidate.transactionFlow),
        "commercialModel": (plan.commercialApproach, candidate.revenueModel),
        "fulfillmentModel": (plan.fulfillmentApproach, candidate.physicalActivities),
    }
    matched = [key for key, values in pairs.items() if _matches(*values)]
    missing = [key for key in pairs if key not in matched]
    if "solutionMechanism" in matched and len(matched) >= 3:
        return "PASS", matched, missing
    if "solutionMechanism" not in matched and len(matched) <= 1:
        return "FAIL", matched, missing
    return "AMBIGUOUS", matched, missing
