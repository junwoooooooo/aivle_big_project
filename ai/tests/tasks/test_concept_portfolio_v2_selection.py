from __future__ import annotations

import asyncio

import pytest
from pydantic import ValidationError

from app.api.executions import TASK_TYPES
from app.concept_portfolio_v2.models import DeltaLegalResult, LegalRoute
from app.concept_portfolio_v2.snapshot_hash import production_compatible_snapshot_hash
from app.tasks.concept_portfolio_v2.observer import ProductionObservedConceptPortfolioEngine
from app.tasks.concept_portfolio_v2.selection_models import ConceptPortfolioSelectionActionInput
from app.tasks.concept_portfolio_v2.selection_service import ConceptPortfolioSelectionActionFacade
from app.tasks.concept_portfolio_v2.service import ConceptPortfolioProductionContractError
from tests.tasks.test_concept_portfolio_v2_production import PRODUCTION_INPUT


@pytest.fixture(scope="module")
def selected_bundle():
    engine = ProductionObservedConceptPortfolioEngine()
    result = asyncio.run(engine.run_full(
        PRODUCTION_INPUT.seed, max_concepts=1, auto_confirm_hypotheses=False
    ))
    candidate = result.concepts[0]
    legal = next(item for item in result.legalSummaries
                 if item.candidateId == candidate.candidateId and item.route == LegalRoute.ACCEPT)
    return candidate, legal


def prepare_input(bundle):
    candidate, legal = bundle
    return {
        "action": "PREPARE_HYPOTHESES",
        "seed": PRODUCTION_INPUT.seed.model_dump(mode="json"),
        "selectedCandidate": candidate.model_dump(mode="json"),
        "baseLegalReview": legal.model_dump(mode="json"),
    }


def test_prepare_uses_frozen_core_and_returns_exactly_seven_with_target_region(selected_bundle):
    result = asyncio.run(ConceptPortfolioSelectionActionFacade(
        engine=ProductionObservedConceptPortfolioEngine()
    ).run(prepare_input(selected_bundle)))
    assert [item.hypothesisType for item in result.hypotheses] == [
        "TARGET_REGION", "REVENUE_MODEL", "PRICE", "CHANNELS", "DIFFERENTIATORS",
        "PRE_MARKET_SOM_SHARE", "PRE_MARKET_SOM",
    ]
    assert all(item.finalValue is None or item.locked for item in result.hypotheses)
    assert all(item.decisionStatus != "USER_EDITED_ACCEPTED" for item in result.hypotheses)


def test_confirm_is_manual_preserves_locked_and_only_five_types_trigger_delta(selected_bundle):
    facade = ConceptPortfolioSelectionActionFacade(engine=ProductionObservedConceptPortfolioEngine())
    prepared = asyncio.run(facade.run(prepare_input(selected_bundle)))
    edits = {}
    for item in prepared.hypotheses:
        if item.locked:
            continue
        if item.hypothesisType == "TARGET_REGION": edits[item.hypothesisType] = "서울"
        elif item.hypothesisType == "PRE_MARKET_SOM_SHARE": edits[item.hypothesisType] = item.proposedValue
        elif item.hypothesisType == "PRE_MARKET_SOM": edits[item.hypothesisType] = item.proposedValue
    confirmed = asyncio.run(facade.run({
        "action": "CONFIRM_HYPOTHESES",
        "hypotheses": [item.model_dump(mode="json") for item in prepared.hypotheses],
        "edits": edits,
        "confirmAll": True,
    }))
    by_type = {item.hypothesisType: item for item in confirmed.hypotheses}
    assert by_type["TARGET_REGION"].deltaLegalRequired is True
    assert by_type["PRE_MARKET_SOM_SHARE"].deltaLegalRequired is False
    assert by_type["PRE_MARKET_SOM"].deltaLegalRequired is False
    for before in prepared.hypotheses:
        if before.locked:
            assert by_type[before.hypothesisType] == before


def test_invalid_edit_remains_unconfirmed(selected_bundle):
    facade = ConceptPortfolioSelectionActionFacade(engine=ProductionObservedConceptPortfolioEngine())
    prepared = asyncio.run(facade.run(prepare_input(selected_bundle)))
    result = asyncio.run(facade.run({
        "action": "CONFIRM_HYPOTHESES",
        "hypotheses": [item.model_dump(mode="json") for item in prepared.hypotheses],
        "edits": {"PRICE": ""},
        "confirmAll": True,
    }))
    price = next(item for item in result.hypotheses if item.hypothesisType == "PRICE")
    assert price.finalValue is None
    assert price.decisionStatus == "PROPOSED"
    assert price.semanticStatus in {"INVALID", "UNRESOLVED"}


def test_alternative_increments_version_without_auto_confirmation(selected_bundle, monkeypatch):
    candidate, _ = selected_bundle
    async def fake_alternative(value):
        return {"hypothesisType": value["hypothesisType"], "proposedValue": "월 구독 대안",
            "source": "AI_HYPOTHESIS", "decisionStatus": "ALTERNATIVE_PROPOSED",
            "proposalVersion": value["proposalVersion"]}
    monkeypatch.setattr(
        "app.tasks.concept_portfolio_v2.selection_service.execute_concept_hypothesis_alternative",
        fake_alternative,
    )
    result = asyncio.run(ConceptPortfolioSelectionActionFacade().run({
        "action": "PROPOSE_ALTERNATIVE",
        "expectedHypothesisRevision": 3,
        "selectedCandidate": candidate.model_dump(mode="json"),
        "hypothesisType": "REVENUE_MODEL",
        "rejectedValue": "건별 결제",
        "proposalVersion": 4,
    }))
    assert result.alternative.proposalVersion == 4
    assert result.alternative.decisionStatus == "ALTERNATIVE_PROPOSED"
    assert result.alternative.finalValue is None


@pytest.mark.parametrize("approved,expected_status", [(True, "PASSED"), (False, "REJECTED")])
def test_delta_legal_preserves_approved_and_nonapproved_domain_result(selected_bundle, approved, expected_status):
    candidate, legal = selected_bundle
    facade = ConceptPortfolioSelectionActionFacade(engine=ProductionObservedConceptPortfolioEngine())
    prepared = asyncio.run(facade.run(prepare_input(selected_bundle)))
    confirmed = asyncio.run(facade.run({
        "action": "CONFIRM_HYPOTHESES",
        "hypotheses": [item.model_dump(mode="json") for item in prepared.hypotheses],
        "edits": {"TARGET_REGION": "서울"}, "confirmAll": True,
    }))
    delta = DeltaLegalResult(
        reviewToken="sha256:" + ("a" if approved else "b") * 64,
        candidateId=candidate.candidateId,
        hypothesisTypes=["TARGET_REGION"],
        status=expected_status,
        approved=approved,
        legalReview=legal.model_copy(update={
            "productionStatus": expected_status,
            "route": LegalRoute.ACCEPT if approved else LegalRoute.REPLAN_REQUIRED,
        }),
    )
    class FakeDeltaEngine:
        async def review_delta_legal(self, seed, selected, hypotheses):
            return delta
        def mark_delta_legal_reviewed(self, hypotheses, result):
            return [item.model_copy(update={"legalReviewStatus": result.status})
                    if item.deltaLegalRequired else item for item in hypotheses]
    result = asyncio.run(ConceptPortfolioSelectionActionFacade(engine=FakeDeltaEngine()).run({
        "action": "DELTA_LEGAL",
        "seed": PRODUCTION_INPUT.seed.model_dump(mode="json"),
        "selectedCandidate": candidate.model_dump(mode="json"),
        "hypotheses": [item.model_dump(mode="json") for item in confirmed.hypotheses],
    }))
    assert result.deltaLegalResult.approved is approved
    target = next(item for item in result.hypotheses if item.hypothesisType == "TARGET_REGION")
    assert target.legalReviewStatus == (expected_status if approved else "PENDING")


def test_build_handoff_binds_real_product_ids_and_keeps_canonical_contract(selected_bundle):
    candidate, legal = selected_bundle
    facade = ConceptPortfolioSelectionActionFacade(engine=ProductionObservedConceptPortfolioEngine())
    prepared = asyncio.run(facade.run(prepare_input(selected_bundle)))
    confirmed = asyncio.run(facade.run({
        "action": "CONFIRM_HYPOTHESES",
        "hypotheses": [item.model_dump(mode="json") for item in prepared.hypotheses],
        "edits": {}, "confirmAll": True,
    }))
    result = asyncio.run(facade.run({
        "action": "BUILD_HANDOFF",
        "seed": PRODUCTION_INPUT.seed.model_dump(mode="json"),
        "selectedCandidate": candidate.model_dump(mode="json"),
        "baseLegalReview": legal.model_dump(mode="json"),
        "hypotheses": [item.model_dump(mode="json") for item in confirmed.hypotheses],
        "approvedDeltaLegalResults": [],
        "productionBinding": {"projectId": 42, "portfolioSelectionId": 17,
            "portfolioConceptId": "product-concept", "marketSeedSnapshotId": "market-seed"},
    }))
    market = result.handoff.marketAnalysisSeedSnapshot
    assert result.handoff.compatibility == "PASS"
    assert market["contract"] == "market-analysis-seed-snapshot-v1"
    assert market["schemaVersion"] == "2.0"
    assert (market["projectId"], market["selectionId"], market["conceptId"], market["snapshotId"]) == (
        42, 17, "product-concept", "market-seed")
    assert result.marketSeedSnapshotHash == production_compatible_snapshot_hash(market)


def test_build_handoff_applies_only_supplied_delta_result(selected_bundle):
    candidate, legal = selected_bundle
    facade = ConceptPortfolioSelectionActionFacade(engine=ProductionObservedConceptPortfolioEngine())
    prepared = asyncio.run(facade.run(prepare_input(selected_bundle)))
    confirmed = asyncio.run(facade.run({
        "action": "CONFIRM_HYPOTHESES",
        "hypotheses": [item.model_dump(mode="json") for item in prepared.hypotheses],
        "edits": {}, "confirmAll": True,
    }))
    delta = DeltaLegalResult(
        reviewToken="sha256:" + "d" * 64,
        candidateId=candidate.candidateId,
        hypothesisTypes=["PRICE"], status="PASSED", approved=True,
        legalReview=legal,
    )
    result = asyncio.run(facade.run({
        "action": "BUILD_HANDOFF", "seed": PRODUCTION_INPUT.seed.model_dump(mode="json"),
        "selectedCandidate": candidate.model_dump(mode="json"),
        "baseLegalReview": legal.model_dump(mode="json"),
        "hypotheses": [item.model_dump(mode="json") for item in confirmed.hypotheses],
        "approvedDeltaLegalResults": [delta.model_dump(mode="json")],
        "productionBinding": {"projectId": 42, "portfolioSelectionId": 17,
            "portfolioConceptId": "product-concept", "marketSeedSnapshotId": "market-seed"},
    }))
    assert result.handoff.marketAnalysisSeedSnapshot["legalResult"]["deltaLegalReviews"] == [
        delta.model_dump(mode="json")
    ]


def test_handoff_domain_failure_exposes_only_safe_field_diagnostic(selected_bundle, monkeypatch):
    candidate, legal = selected_bundle
    prepared = asyncio.run(ConceptPortfolioSelectionActionFacade().run(prepare_input(selected_bundle)))
    confirmed = asyncio.run(ConceptPortfolioSelectionActionFacade().run({
        "action": "CONFIRM_HYPOTHESES",
        "hypotheses": [item.model_dump(mode="json") for item in prepared.hypotheses],
        "edits": {}, "confirmAll": True,
    }))
    class FailedAdapter:
        def build(self, *args):
            from app.concept_portfolio_v2.models import DownstreamHandoff
            return DownstreamHandoff(compatibility="FAIL", structureStatus="STRUCTURE_PASS",
                contractStatus="CONTRACT_FAIL", marketAnalysisSeedSnapshot={},
                marketingSourceSnapshot={}, sourceProvenance={}, fieldMapping=[],
                validationErrors=["sensitive business detail"])
    monkeypatch.setattr("app.tasks.concept_portfolio_v2.selection_service.CurrentDownstreamAdapter", FailedAdapter)
    facade = ConceptPortfolioSelectionActionFacade()
    with pytest.raises(ConceptPortfolioProductionContractError) as raised:
        asyncio.run(facade.run({
            "action": "BUILD_HANDOFF", "seed": PRODUCTION_INPUT.seed.model_dump(mode="json"),
            "selectedCandidate": candidate.model_dump(mode="json"),
            "baseLegalReview": legal.model_dump(mode="json"),
            "hypotheses": [item.model_dump(mode="json") for item in confirmed.hypotheses],
            "productionBinding": {"projectId": 42, "portfolioSelectionId": 17,
                "portfolioConceptId": "product-concept", "marketSeedSnapshotId": "market-seed"},
        }))
    assert raised.value.validation_fields == [{
        "path": "result.handoff.compatibility", "expectedType": "PASS",
        "category": "domain_invariant",
    }]


def test_selection_action_contract_is_strict_and_dispatcher_registered(selected_bundle):
    assert "CONCEPT_PORTFOLIO_V2_SELECTION_ACTION" in TASK_TYPES
    with pytest.raises(ValidationError):
        ConceptPortfolioSelectionActionInput.model_validate({
            **prepare_input(selected_bundle), "autoConfirmHypotheses": True
        })
    with pytest.raises(ValidationError):
        ConceptPortfolioSelectionActionInput.model_validate({
            "action": "CONFIRM_HYPOTHESES", "hypotheses": [], "edits": {}, "confirmAll": False
        })


def test_engine_default_selection_is_never_an_action_input_authority(selected_bundle):
    model = ConceptPortfolioSelectionActionInput.model_validate(prepare_input(selected_bundle))
    dumped = model.model_dump(mode="json")
    assert "engineDefaultConceptId" not in dumped
    assert "userSelectedConceptId" not in dumped
