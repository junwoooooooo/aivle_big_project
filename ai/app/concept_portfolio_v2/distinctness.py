"""Plan/Candidate가 공유하는 3단계 business mechanics distinctness."""

from __future__ import annotations

from .mechanics import CONTROLLED_CODES
from .models import DiversityAssessment, MechanicsDescriptor


def descriptor_values(value: MechanicsDescriptor) -> dict[str, str]:
    return {key: item.code if hasattr(item, "code") and item.code in CONTROLLED_CODES else "OTHER"
            for key, item in value.__iter__()}


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
