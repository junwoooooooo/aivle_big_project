"""Deterministic domain-drift guard for market-interview results."""

import json
import re
from typing import Any

from app.providers import ProviderFailure

_BICYCLE = ("자전거", "공유 자전거", "bicycle", "bike", "대여 업체", "자전거 관리")
_AUTOMOTIVE_PARKING = (
    "smart parking", "traditional parking", "parking management", "parking space",
    "parking lot", "urban parking", "주차장", "주차 공간", "자동차 주차", "차량 주차",
)


def _text(value: Any) -> str:
    return re.sub(r"\s+", " ", json.dumps(value, ensure_ascii=False)).lower()


def assert_semantic_integrity(selected_concept: dict[str, Any], result: dict[str, Any],
                              concept_board: dict[str, Any] | None = None) -> None:
    """Reject a strong cross-domain substitution while allowing adjacent vocabulary."""
    identity = selected_concept.get("identity") or {}
    solution = selected_concept.get("solution") or {}
    source_sections = [identity, solution, selected_concept.get("operation") or {}, concept_board or {}]
    source_strength = sum(any(anchor in _text(section) for anchor in _BICYCLE)
                          for section in source_sections)
    if source_strength < 2:
        return
    result_text = _text({
        "themes": result.get("themes"), "answers": result.get("interviews"),
        "questions": result.get("followUpQuestions"), "objections": result.get("objections"),
        "needs": result.get("unmetNeeds"),
    })
    bicycle_hits = sum(result_text.count(anchor) for anchor in _BICYCLE)
    conflicting_hits = sum(result_text.count(anchor) for anchor in _AUTOMOTIVE_PARKING)
    if conflicting_hits >= 3 and bicycle_hits == 0:
        raise ProviderFailure(
            "MARKET_INTERVIEW_SEMANTIC_MISMATCH",
            "MARKET_INTERVIEW_SEMANTIC_MISMATCH",
            422,
            False,
            safe_diagnostics={"sourceDomain": "BICYCLE_MANAGEMENT",
                              "conflictingDomain": "AUTOMOTIVE_PARKING",
                              "sourceSectionCoverage": source_strength,
                              "conflictingAnchorCount": conflicting_hits},
        )
