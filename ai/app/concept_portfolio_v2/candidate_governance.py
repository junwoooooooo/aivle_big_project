"""Candidate Draft에 system-owned governance를 결정론적으로 적용한다."""

from __future__ import annotations

from app.tasks.concept_candidate.models import (
    ConceptCandidateDraft, ConceptCandidateResult, SemanticField, ValueSemantics,
)

from .models import CanonicalSeed, ExplorationBreadth
from .language_policy import is_governance_placeholder


DIRECT_CANDIDATE_LOCKS = {"targetRegion", "revenueModel", "price", "channels", "differentiators"}
HYPOTHESIS_CANDIDATE_FIELDS = DIRECT_CANDIDATE_LOCKS | {
    "preMarketSomShareHypothesis", "preMarketSomHypothesis",
}
CONSTRAINT_FIELDS = {"budgetConstraint", "teamConstraint", "timelineConstraint", "otherConstraint"}


def _semantics(seed: CanonicalSeed, strategy: ExplorationBreadth, candidate_index: int) -> list[ValueSemantics]:
    by_key = seed.by_key()
    original = strategy == ExplorationBreadth.AS_IS and candidate_index == 1
    result: list[ValueSemantics] = []
    for key in SemanticField.__args__:
        if key in HYPOTHESIS_CANDIDATE_FIELDS:
            direct = by_key.get(key)
            if key in DIRECT_CANDIDATE_LOCKS and direct and direct.decisionState == "LOCKED" and direct.value.strip():
                source = direct.source if direct.source in {"USER_INPUT", "USER_CONFIRMED"} else "USER_INPUT"
                result.append(ValueSemantics(fieldKey=key, source=source, authority="LOCKED", decision="ACCEPTED"))
                continue
            result.append(ValueSemantics(fieldKey=key, source="AI_HYPOTHESIS", authority="OPEN", decision="PROPOSED"))
            continue
        if original and key in {"conceptDefinition", "problemScenario", "targetUsers"}:
            result.append(ValueSemantics(fieldKey=key, source="USER_INPUT", authority="LOCKED", decision="ACCEPTED"))
            continue
        result.append(ValueSemantics(fieldKey=key, source="CONCEPT_GENERATED",
                                     authority="REVIEWABLE", decision="PROPOSED"))
    return result


def normalize_candidate_draft(draft: ConceptCandidateDraft, seed: CanonicalSeed,
                              strategy: ExplorationBreadth, candidate_index: int) -> ConceptCandidateResult:
    values = draft.model_dump(mode="json")
    by_key = seed.by_key()
    for key in DIRECT_CANDIDATE_LOCKS:
        field = by_key.get(key)
        if field and field.decisionState == "LOCKED" and field.value.strip():
            values[key] = field.value
    open_defaults = {
        "targetRegion": "대한민국",
        "revenueModel": "수익모델 검증 필요",
        "price": "가격 검증 필요",
        "channels": "채널 검증 필요",
        "differentiators": "차별화 가설 검증 필요",
    }
    for key, default in open_defaults.items():
        if not values.get(key) or is_governance_placeholder(values[key]):
            values[key] = default
    original = strategy == ExplorationBreadth.AS_IS and candidate_index == 1
    if original:
        values["conceptDefinition"] = seed.ideaOverview
        values["problemScenario"] = seed.problem
        values["targetUsers"] = seed.targetUsers
    constraints = [f"{item.fieldKey}={item.value}" for item in seed.fields
                   if item.fieldKey in CONSTRAINT_FIELDS and item.decisionState == "LOCKED" and item.value.strip()]
    known = by_key.get("knownCompetitors")
    if known and known.decisionState == "LOCKED" and known.value.strip():
        constraints.append(f"knownCompetitors={known.value}")
    values["constraintCompliance"] = constraints
    return ConceptCandidateResult.model_validate({
        **values, "schemaVersion": "2.0", "generationStrategy": strategy.value,
        "candidateIndex": candidate_index, "originalCandidate": original,
        "valueSemantics": [item.model_dump(mode="json") for item in _semantics(seed, strategy, candidate_index)],
    })


def candidate_result_to_draft(candidate: ConceptCandidateResult) -> ConceptCandidateDraft:
    return ConceptCandidateDraft.model_validate({
        key: getattr(candidate, key) for key in ConceptCandidateDraft.model_fields
    })
