from __future__ import annotations

import asyncio
import re
from pathlib import Path

import pytest

from app.tasks import concept_refinement
from app.tasks.concept_portfolio_v2.observer import ProductionObservedConceptPortfolioEngine
from app.tasks.concept_portfolio_v2.selection_models import ConceptPortfolioSelectionActionResult
from app.tasks.concept_portfolio_v2.selection_service import ConceptPortfolioSelectionActionFacade
from app.validation import drift
from tests.tasks.test_concept_portfolio_v2_production import PRODUCTION_INPUT


@pytest.fixture(scope="module")
def selected_candidate():
    result = asyncio.run(ProductionObservedConceptPortfolioEngine().run_full(
        PRODUCTION_INPUT.seed, max_concepts=1, auto_confirm_hypotheses=False
    ))
    return result.concepts[0]


def _proposal(**changes):
    value = {
        "fieldKey": "price", "currentValue": "10,000원", "proposedValue": "11,000원",
        "title": "가격 조정", "beforeText": "10,000원", "afterText": "11,000원",
        "rationale": "시장 가격 반영", "source": "MARKET",
        "evidenceIds": ["E-1"], "legalRef": None,
    }
    value.update(changes)
    return value


def _material():
    return {
        "round": 1, "attempt": 1, "policyVersion": "REFINEMENT_POLICY_V1",
        "maxProposals": 6, "priceTolerance": 0.30, "listChangeAllowance": 1,
        "sourceBinding": {
            "businessValidationSessionId": "session-1", "marketVersionId": 91,
            "bmVersionId": 92, "marketSeedSnapshotId": "seed-1", "selectionId": 31,
            "selectionRevision": 4, "bmPlanRevision": 3,
        },
        "frozenFields": list(drift.FROZEN_FIELDS),
        "refinableFields": {**drift.REFINABLE_FIELDS, "keyActivities": "FREE_BM"},
        "currentEditableValues": {
            "price": "10,000원", "channels": ["Seed 채널"], "keyActivities": []
        },
        "frozenValues": {"conceptName": "Seed 사업안", "partnerRequirements": ["면허"]},
        "gateReasons": [], "canvas": None, "marketEvidence": [{"id": "E-1"}],
        "legalFindings": [{"reference": "법률 제1조"}],
        "allowedLegalRefs": ["법률 제1조"], "driftRejections": [], "userDeclined": [],
    }


def test_refine_from_market_returns_grounded_safe_proposal(monkeypatch, selected_candidate):
    async def proposal(_material, _concept):
        return [_proposal(fieldKey="keyActivities", currentValue="틀린 값", proposedValue=["고객 인터뷰"])]

    import app.tasks.concept_portfolio_v2.selection_service as service_module
    monkeypatch.setattr(service_module, "propose_refinements", proposal)
    result = asyncio.run(ConceptPortfolioSelectionActionFacade().run({
        "action": "REFINE_FROM_MARKET",
        "selectedCandidate": selected_candidate.model_dump(mode="json"),
        "refinementMaterial": _material(),
    }))
    assert len(result.refinementProposals) == 1
    assert result.refinementProposals[0].fieldKey == "keyActivities"
    assert result.refinementProposals[0].currentValue == []
    assert result.driftRejections == []


def test_market_proposal_without_evidence_is_rejected():
    passed, rejected = drift.filter_ungrounded(
        [_proposal(evidenceIds=[])], [{"id": "E-1"}], []
    )
    assert passed == [] and len(rejected) == 1


def test_invalid_evidence_is_removed_when_valid_evidence_remains():
    passed, rejected = drift.filter_ungrounded(
        [_proposal(evidenceIds=["E-1", "FAKE"])], [{"id": "E-1"}], []
    )
    assert rejected == []
    assert passed[0]["evidenceIds"] == ["E-1"]


def test_price_drift_uses_validated_current_value_not_candidate_value():
    current = {"price": "10,000원"}
    passed, rejected = drift.filter_proposals(
        [_proposal(currentValue="8,900원", proposedValue="12,500원")], current, {}
    )
    assert rejected == [] and passed[0]["currentValue"] == "10,000원"
    passed, rejected = drift.filter_proposals(
        [_proposal(proposedValue="13,500원")], current, {}
    )
    assert passed == [] and len(rejected) == 1


def test_field_missing_from_current_editable_values_is_rejected():
    passed, rejected = drift.filter_proposals(
        [_proposal(fieldKey="budget_krw", proposedValue=1000)], {"price": "10,000원"}, {}
    )
    assert passed == [] and "baseline" in rejected[0]["rejectionReason"]


def test_same_value_is_not_accepted_as_a_change():
    passed, rejected = drift.filter_proposals(
        [_proposal(proposedValue="10,000원")], {"price": "10,000원"}, {}
    )
    assert passed == [] and "같은" in rejected[0]["rejectionReason"]


def test_legal_reference_requires_exact_allowed_match():
    legal = _proposal(source="LEGAL", evidenceIds=[], legalRef="전자상거래법 제1조")
    passed, rejected = drift.filter_ungrounded([legal], [], ["전자상거래법 제1조"])
    assert len(passed) == 1 and rejected == []
    passed, rejected = drift.filter_ungrounded([legal], [], ["전자상거래법 제1조의2"])
    assert passed == [] and len(rejected) == 1


def test_frozen_field_change_is_rejected():
    with pytest.raises(drift.DriftRejection):
        drift.check("operatingModel", "중개", "직접 판매")


def test_price_over_thirty_percent_is_rejected():
    drift.check("price", 10000, 13000)
    with pytest.raises(drift.DriftRejection):
        drift.check("price", 10000, 13001)


def test_list_change_over_one_is_rejected():
    drift.check("channels", ["온라인", "매장"], ["온라인", "매장", "파트너"])
    with pytest.raises(drift.DriftRejection):
        drift.check("channels", ["온라인", "매장"], ["파트너", "방문"])


def test_provider_output_is_capped_at_six(monkeypatch):
    async def provider(**_kwargs):
        return {"proposals": [_proposal(title=f"제안 {index}") for index in range(8)]}
    monkeypatch.setattr(concept_refinement, "execute_structured_prompt", provider)
    assert len(asyncio.run(concept_refinement.propose_refinements(_material(), {}))) == 6


def test_existing_selection_result_actions_remain_valid():
    assert ConceptPortfolioSelectionActionResult(action="PREPARE_HYPOTHESES").action == "PREPARE_HYPOTHESES"
    assert ConceptPortfolioSelectionActionResult(action="BUILD_HANDOFF").action == "BUILD_HANDOFF"


def test_java_python_refinement_policy_v1_alignment():
    java = (Path(__file__).resolve().parents[2]
            / "backend/src/main/java/com/aivle/backend/pipeline/refinement/ConceptRefinementPolicy.java").read_text()

    def block(name: str) -> str:
        start = java.index(name)
        return java[start:java.index(";", start)]

    quoted = lambda value: set(re.findall(r'"([A-Za-z_]+)"', value))
    assert quoted(block("FROZEN_FIELDS")) == set(drift.FROZEN_FIELDS)
    pairs = dict(re.findall(r'"([A-Za-z]+)",\s*"([A-Z_]+)"', block("REFINABLE_FIELDS")))
    assert pairs == drift.REFINABLE_FIELDS
    assert quoted(block("FREE_WITH_EVIDENCE_FIELDS")) == set(drift.FREE_WITH_EVIDENCE_FIELDS)
    assert quoted(block("FREE_BM_FIELDS")) == set(drift.FREE_BM_FIELDS)
    assert float(re.search(r"PRICE_TOLERANCE\s*=\s*([0-9.]+)", java).group(1)) == drift.PRICE_TOLERANCE
    assert int(re.search(r"LIST_CHANGE_ALLOWANCE\s*=\s*([0-9]+)", java).group(1)) == drift.LIST_CHANGE_ALLOWANCE
