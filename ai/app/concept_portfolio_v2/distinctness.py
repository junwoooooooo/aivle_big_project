"""Concept Thesis와 Business Architecture를 분리한 Portfolio 관계 판정."""

from __future__ import annotations

import re

from .models import CanonicalConceptDescriptor, DiversityAssessment


PRIMARY_ARCHITECTURE = {
    "businessRole", "operatingModel", "partnerModel", "transactionModel", "monetizationModel",
}
SECONDARY_ARCHITECTURE = {"deliveryModel", "customerInteractionModel", "dataDependency", "physicalDependency"}


def _tokens(value: str) -> set[str]:
    return {item for item in re.findall(r"[0-9a-z가-힣]+", value.casefold()) if len(item) >= 2}


def _similarity(left: str, right: str) -> float:
    a, b = _tokens(left), _tokens(right)
    if not a and not b:
        return 1.0
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def descriptor_values(value: CanonicalConceptDescriptor) -> dict[str, str]:
    return value.architecture.model_dump(mode="json")


def _thesis_similarities(left: CanonicalConceptDescriptor,
                         right: CanonicalConceptDescriptor) -> dict[str, float]:
    a, b = left.thesis, right.thesis
    return {field: _similarity(getattr(a, field), getattr(b, field)) for field in (
        "targetSegmentThesis", "useCaseThesis", "valuePropositionThesis", "offerThesis", "solutionThesis")}


def deterministic_distinctness(entity_a: str, entity_b: str,
                               left: CanonicalConceptDescriptor,
                               right: CanonicalConceptDescriptor) -> DiversityAssessment:
    a, b = descriptor_values(left), descriptor_values(right)
    architecture_overlap = [key for key in a if a[key] == b[key]]
    architecture_differences = [key for key in a if a[key] != b[key]]
    thesis = _thesis_similarities(left, right)
    primary_differences = [key for key in architecture_differences if key in PRIMARY_ARCHITECTURE]
    secondary_differences = [key for key in architecture_differences if key in SECONDARY_ARCHITECTURE]
    meaningful_thesis = [key for key, score in thesis.items() if score < 0.55]
    almost_same_thesis = all(score >= 0.72 for score in thesis.values())

    if not architecture_differences and almost_same_thesis:
        decision, level = "DUPLICATE", "THESIS_AND_ARCHITECTURE"
        summary = "이름이나 표현을 제외한 Concept Thesis와 Business Architecture가 사실상 같습니다."
    elif primary_differences or thesis["solutionThesis"] < 0.30:
        decision, level = "DISTINCT", "PRIMARY_BUSINESS_CHOICE"
        summary = "핵심 solution 또는 주요 Business Architecture 선택이 다릅니다."
    elif meaningful_thesis:
        decision, level = "VARIANT", "MEANINGFUL_THESIS_VARIANT"
        summary = "Architecture family는 유사하지만 target/use case/value/offer thesis가 의미 있게 다릅니다."
    elif secondary_differences:
        decision, level = "VARIANT", "ARCHITECTURE_VARIANT"
        summary = "핵심 Concept은 유지하면서 delivery 또는 customer interaction이 달라진 Variant입니다."
    else:
        decision, level = "AMBIGUOUS", "SEMANTIC_REVIEW"
        summary = "표면 정규화만으로 중복과 Variant를 확정하기 어려워 semantic 판정이 필요합니다."
    return DiversityAssessment(
        entityA=entity_a, entityB=entity_b, decision=decision,
        overlap=architecture_overlap,
        materialDifferences=primary_differences + secondary_differences + meaningful_thesis,
        whyDistinct=summary, deterministicLevel=level, semanticJudgeUsed=False,
        familyA=left.familyId, familyB=right.familyId,
    )
