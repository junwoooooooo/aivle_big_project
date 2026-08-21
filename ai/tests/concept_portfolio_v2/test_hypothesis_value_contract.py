import pytest

from app.concept_portfolio_v2.hypothesis_value_contract import (
    HypothesisValueContractError,
    normalize_hypothesis_value,
)
from app.concept_portfolio_v2.models import HypothesisDecision


def test_existing_canonical_text_is_preserved_exactly():
    value = "  웹과 파트너 채널  "
    assert normalize_hypothesis_value("CHANNELS", value) == value
    assert normalize_hypothesis_value("PRICE", "월 9,900원") == "월 9,900원"


@pytest.mark.parametrize(("hypothesis_type", "expected"), [
    ("CHANNELS", "웹, 파트너 판매"),
    ("DIFFERENTIATORS", "빠른 설정, 기존 시스템 연동"),
])
def test_list_compatible_text_is_deterministically_normalized(hypothesis_type, expected):
    value = ["  " + expected.split(", ")[0], expected.split(", ")[1] + "\n"]
    assert normalize_hypothesis_value(hypothesis_type, value) == expected


@pytest.mark.parametrize("value", [[], ["웹", {"name": "파트너"}], ["웹", "  "], {"name": "웹"}])
def test_invalid_arbitrary_or_nested_text_proposal_is_rejected(value):
    with pytest.raises(HypothesisValueContractError):
        normalize_hypothesis_value("CHANNELS", value)


def test_legacy_hypothesis_decision_repairs_list_at_contract_load():
    value = HypothesisDecision(
        hypothesisType="CHANNELS", proposedValue=["웹", "파트너 판매"],
        finalValue=["웹", "파트너 판매"], source="AI_HYPOTHESIS",
        decisionStatus="USER_EDITED_ACCEPTED", proposalVersion=3, locked=True,
        semanticStatus="VALID", legalReviewStatus="PENDING", deltaLegalRequired=True,
    )
    assert value.proposedValue == "웹, 파트너 판매"
    assert value.finalValue == "웹, 파트너 판매"
