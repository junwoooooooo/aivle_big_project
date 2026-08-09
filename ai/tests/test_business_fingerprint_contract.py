import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.contracts.concept_fingerprint import BUSINESS_FINGERPRINT_FIELDS, BusinessFingerprint
from app.tasks.concept_candidate.models import ConceptCandidateInput
from app.tasks.concept_distinctness_judge.models import (
    ConceptDistinctnessJudgeInput,
    ConceptDistinctnessJudgeResult,
)


CONTRACT_ROOT = Path(__file__).parents[2] / "contracts" / "concept"


def load_fixture(name: str) -> dict:
    return json.loads((CONTRACT_ROOT / name).read_text(encoding="utf-8"))


def test_full_21_field_shared_fixture_validates_with_strict_types():
    payload = load_fixture("business-fingerprint-v1.json")

    value = BusinessFingerprint.model_validate(payload)

    assert tuple(payload) == BUSINESS_FINGERPRINT_FIELDS
    for field in (
        "featureSet", "actorRoles", "transactionFlow", "paymentFlow", "personalDataUsage",
        "physicalActivities", "partnerRequirements", "qualificationRequirements",
    ):
        assert isinstance(getattr(value, field), list)
        assert all(isinstance(item, str) for item in getattr(value, field))


def test_unknown_fingerprint_field_remains_forbidden():
    payload = load_fixture("business-fingerprint-v1.json")
    payload["unknownBusinessDimension"] = "금지"

    with pytest.raises(ValidationError) as raised:
        BusinessFingerprint.model_validate(payload)

    assert any(issue["type"] == "extra_forbidden" for issue in raised.value.errors())


def test_actual_candidate_input_fixture_accepts_all_non_empty_fingerprint_families():
    payload = load_fixture("concept-candidate-input-v1.json")

    value = ConceptCandidateInput.model_validate(payload)

    assert len(value.acceptedConceptFingerprints) == 1
    assert len(value.rejectedConceptFingerprints) == 1
    assert len(value.currentSlotPreviousFingerprints) == 1


def test_distinctness_input_uses_the_same_full_fingerprint_contract():
    payload = load_fixture("business-fingerprint-v1.json")

    value = ConceptDistinctnessJudgeInput.model_validate({"candidateA": payload, "candidateB": payload})

    assert value.candidateA.model_dump(mode="json") == value.candidateB.model_dump(mode="json")


def test_distinctness_result_rejects_non_canonical_dimension():
    with pytest.raises(ValidationError):
        ConceptDistinctnessJudgeResult.model_validate({
            "decision": "DISTINCT",
            "overlappingDimensions": ["notAContractDimension"],
            "materiallyDifferentDimensions": ["solutionMechanism"],
            "safeSummary": "사업 구조가 다릅니다.",
        })
