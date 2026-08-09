"""Plan/Candidate가 공유하는 3단계 business mechanics distinctness."""

from __future__ import annotations

import re

from .models import DiversityAssessment, MechanicsDescriptor


ALIASES = {
    "지역식료품점직접배송네트워크": "LOCAL_RETAILER_DIRECT",
    "제휴소매점고객직배송플랫폼": "LOCAL_RETAILER_DIRECT",
    "지역판매자네트워크": "LOCAL_RETAILER_NETWORK",
    "제휴소매점네트워크": "LOCAL_RETAILER_NETWORK",
    "직접배송": "DIRECT_DELIVERY",
    "고객직배송": "DIRECT_DELIVERY",
}


def semantic_key(value: str) -> str:
    normalized = re.sub(r"[^0-9a-z가-힣]+", "", value.casefold())
    return ALIASES.get(normalized, normalized.upper())


def descriptor_values(value: MechanicsDescriptor) -> dict[str, str]:
    return {key: semantic_key(item) for key, item in value.model_dump().items()}


def deterministic_distinctness(entity_a: str, entity_b: str, left: MechanicsDescriptor,
                               right: MechanicsDescriptor) -> DiversityAssessment:
    a, b = descriptor_values(left), descriptor_values(right)
    overlap = [key for key in a if a[key] == b[key]]
    differences = [key for key in a if a[key] != b[key]]
    if not differences:
        decision, level, summary = "DUPLICATE", "LEVEL_1", "canonical business mechanics가 같습니다."
    elif len(differences) >= 2:
        decision, level, summary = "DISTINCT", "LEVEL_2", f"{', '.join(differences)}가 구조적으로 다릅니다."
    else:
        decision, level, summary = "AMBIGUOUS", "LEVEL_3", "한 mechanics 차이만 있어 semantic judge가 필요합니다."
    return DiversityAssessment(entityA=entity_a, entityB=entity_b, decision=decision,
                               overlap=overlap, materialDifferences=differences,
                               whyDistinct=summary, deterministicLevel=level,
                               semanticJudgeUsed=False)
