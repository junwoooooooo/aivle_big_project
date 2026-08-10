"""Plan identity와 Candidate actual descriptor 사이의 generic fidelity."""

from __future__ import annotations

import re

from app.tasks.concept_candidate.models import ConceptCandidateResult

from .mechanics import GenericConceptNormalizer
from .models import CanonicalConceptDescriptor, PortfolioPlan


def _tokens(value: str) -> set[str]:
    return {item for item in re.findall(r"[0-9a-z가-힣]+", value.casefold()) if len(item) >= 2}


def _overlap(left: str, right: str) -> float:
    a, b = _tokens(left), _tokens(right)
    return len(a & b) / max(1, min(len(a), len(b)))


def deterministic_plan_fidelity(
    plan: PortfolioPlan,
    candidate: ConceptCandidateResult,
    candidate_descriptor: CanonicalConceptDescriptor | None = None,
) -> tuple[str, list[str], list[str]]:
    actual = candidate_descriptor or GenericConceptNormalizer.from_candidate(candidate)
    planned = plan.descriptor
    scores = {
        "targetSegmentThesis": _overlap(planned.thesis.targetSegmentThesis,
                                         actual.thesis.targetSegmentThesis),
        "useCaseThesis": _overlap(planned.thesis.useCaseThesis, actual.thesis.useCaseThesis),
        "valuePropositionThesis": _overlap(planned.thesis.valuePropositionThesis,
                                            actual.thesis.valuePropositionThesis),
        "offerThesis": _overlap(planned.thesis.offerThesis, actual.thesis.offerThesis),
        "solutionThesis": _overlap(planned.thesis.solutionThesis, actual.thesis.solutionThesis),
    }
    definition_score = _overlap(plan.oneLineConcept, candidate.conceptDefinition)
    matched = [key for key, score in scores.items() if score >= 0.35]
    missing = [key for key in scores if key not in matched]
    identity_preserved = (scores["valuePropositionThesis"] >= 0.35
                          and scores["solutionThesis"] >= 0.35
                          and max(scores["targetSegmentThesis"], scores["useCaseThesis"]) >= 0.25)
    if identity_preserved:
        architecture_same = planned.architecture == actual.architecture
        return ("PASS" if architecture_same else "ADAPTED"), matched, missing
    if (scores["valuePropositionThesis"] >= 0.25
            and scores["solutionThesis"] >= 0.25
            and scores["offerThesis"] >= 0.20):
        return "ADAPTED", matched, missing
    primary_architecture_changes = sum(
        getattr(planned.architecture, key) != getattr(actual.architecture, key)
        for key in ("businessRole", "operatingModel", "transactionModel", "monetizationModel")
    )
    clear_replacement = (
        scores["solutionThesis"] < 0.08
        and scores["offerThesis"] < 0.08
        and definition_score < 0.08
        and (scores["valuePropositionThesis"] < 0.08
             or primary_architecture_changes >= 2)
    )
    if clear_replacement:
        return "FAIL", matched, missing
    return "AMBIGUOUS", matched, missing
