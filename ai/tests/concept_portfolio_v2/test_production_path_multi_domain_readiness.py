import asyncio
import json
from pathlib import Path

import pytest

import app.concept_portfolio_v2.adapters as adapters_module
from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway
from app.concept_portfolio_v2.hypothesis_validation import assess_hypothesis_value
from app.concept_portfolio_v2.legal_fact_completeness import (
    assess_legal_fact_completeness, assess_role_semantics,
)
from app.concept_portfolio_v2.models import LegalReview, LegalRoute
from app.concept_portfolio_v2.providers import MockPortfolioProvider
from app.providers import ProviderFailure
from app.tasks.concept_portfolio_v2.service import execute_concept_portfolio_v2
from app.tasks.concept_portfolio_v2.observer import ProductionObservedConceptPortfolioEngine


SCENARIOS = json.loads((Path(__file__).resolve().parents[2] / "fixtures" /
    "concept_portfolio_v2" / "live_scenarios.json").read_text(encoding="utf-8"))


def run(value):
    return asyncio.run(value)


def payload(item):
    return {key: item[key] for key in ("ideaOverview", "problem", "targetUsers")}


class OneCandidateFailureProvider(MockPortfolioProvider):
    async def review_legal(self, candidate_id, candidate, seed):
        if candidate_id.split("-")[0] == "C3":
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_RESULT_SCHEMA_INVALID", 502, False,
                                  schema_name="concept_legal_review_result_v2")
        return await super().review_legal(candidate_id, candidate, seed)


class GlobalLegalFailureProvider(MockPortfolioProvider):
    async def review_legal(self, candidate_id, candidate, seed):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "LEGAL_CONFIGURATION_INVALID", 503, False)


class RepeatedCandidateFailureProvider(MockPortfolioProvider):
    async def review_legal(self, candidate_id, candidate, seed):
        if candidate_id.split("-")[0] in {"C2", "C3"}:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "SAME_LEGAL_CONTRACT_ERROR", 502, False,
                                  schema_name="concept_legal_review_result_v2")
        return await super().review_legal(candidate_id, candidate, seed)


class CandidateNeedsInputProvider(MockPortfolioProvider):
    async def review_legal(self, candidate_id, candidate, seed):
        if candidate_id.split("-")[0] == "C4":
            return LegalReview(
                candidateId=candidate_id, route=LegalRoute.NEEDS_INPUT,
                productionStatus="NEEDS_FACTS", sourceStatus="OFFICIAL_EVIDENCE",
                safeSummary="후보별 기존 계약 사실 확인이 필요합니다.",
                unknownFacts=["제휴 제공자와 체결한 기존 계약에 재위탁 허용 조항이 있는지"],
            )
        return await super().review_legal(candidate_id, candidate, seed)


def test_one_candidate_contract_failure_is_isolated_and_partial_portfolio_survives():
    engine = ConceptPortfolioEngine(gateway=ProviderGateway(provider=OneCandidateFailureProvider()))
    result = run(engine.run_full(payload(SCENARIOS[1])))
    assert result.runStatus.value == "READY_LIMITED"
    assert result.producedConceptCount == 4
    failed = next(item for item in result.legalSummaries if item.candidateId.split("-")[0] == "C3")
    assert failed.route == LegalRoute.SYSTEM_FAILURE
    assert failed.sourceStatus == "CANDIDATE_SYSTEM_FAILURE"


def test_global_legal_configuration_failure_stays_global_and_has_diagnostics():
    engine = ConceptPortfolioEngine(gateway=ProviderGateway(provider=GlobalLegalFailureProvider()))
    result = run(engine.run_full(payload(SCENARIOS[1])))
    assert result.runStatus.value == "FAILED"
    assert result.failureDiagnostics
    assert result.failureDiagnostics.failedStage == "LEGAL_REVIEWING"
    assert result.failureDiagnostics.failureCode == "PROVIDER_PERMANENT"
    assert result.runSummary and result.runSummary.legalReviewed == 0
    assert result.failureDiagnostics.providerFailure["safeProviderMessage"] == "LEGAL_CONFIGURATION_INVALID"


def test_repeated_candidate_contract_error_escalates_to_global_failure():
    engine = ConceptPortfolioEngine(gateway=ProviderGateway(provider=RepeatedCandidateFailureProvider()))
    result = run(engine.run_full(payload(SCENARIOS[1])))
    assert result.runStatus.value == "FAILED"
    assert result.failureDiagnostics.failedStage == "LEGAL_REVIEWING"
    assert any(item.action == "REPEATED_LEGAL_CONTRACT_FAILURE" for item in result.trace)
    assert sum(item.route == LegalRoute.SYSTEM_FAILURE for item in result.legalSummaries) == 2


def test_candidate_needs_input_is_actionable_and_does_not_block_other_candidates():
    engine = ConceptPortfolioEngine(gateway=ProviderGateway(provider=CandidateNeedsInputProvider()))
    result = run(engine.run_full(payload(SCENARIOS[2])))
    assert result.runStatus.value == "READY_LIMITED" and result.producedConceptCount == 4
    required = result.unresolvedCandidates[0]
    assert required["candidateId"].split("-")[0] == "C4" and required["scope"] == "CANDIDATE"
    assert required["unknownFacts"] and required["reason"] and required["possibleUserAction"]


def test_staged_and_run_full_use_the_same_state_machine_invariants():
    staged = ConceptPortfolioEngine()
    seed = staged.seed_adapter.adapt(payload(SCENARIOS[3]))
    staged._reset()
    safety = run(staged.check_safety(seed))
    assert safety.passed
    analysis = run(staged.analyze_seed(seed))
    plans = run(staged.prepare_portfolio_plans(seed, analysis))
    candidates = run(staged.prepare_candidate_portfolio(seed, plans))
    legal_ready = run(staged.prepare_legal_candidates(seed, candidates.candidates))
    reviews = run(staged.review_legal(seed, legal_ready.candidates))
    final, resolved_reviews, required, _, _ = run(staged.resolve_legal(
        seed, candidates.usedPlans, legal_ready.candidates, reviews))

    one_click = run(ConceptPortfolioEngine().run_full(payload(SCENARIOS[3])))
    assert [item.candidateId for item in final] == [item.candidateId for item in one_click.concepts]
    assert [(item.candidateId, item.route.value) for item in resolved_reviews] == [
        (item.candidateId, item.route.value) for item in one_click.legalSummaries]
    assert required == one_click.requiredInputs
    assert one_click.downstreamReadiness == "PENDING_HYPOTHESIS_CONFIRMATION"
    assert one_click.runStatus.value == "READY_FULL"


@pytest.mark.parametrize("item", SCENARIOS, ids=[item["scenarioId"] for item in SCENARIOS])
def test_seven_domain_scenario_matrix_reaches_a_usable_generic_portfolio(item):
    result = run(ConceptPortfolioEngine().run_full(payload(item)))
    assert result.runStatus.value in {"READY_FULL", "READY_LIMITED"}
    assert result.producedConceptCount >= 1
    assert all(concept.descriptor.familyId for concept in result.concepts)
    assert all(review.route in {LegalRoute.ACCEPT, LegalRoute.NEEDS_INPUT,
                                LegalRoute.SYSTEM_FAILURE} for review in result.legalSummaries)


@pytest.mark.parametrize(("kind", "value"), [
    ("TARGET_REGION", "일본"),
    ("TARGET_REGION", "미국 캘리포니아"),
    ("PRICE", "$19/month"),
    ("PRICE", "고객별 견적"),
    ("CHANNELS", "Slack 앱 디렉터리"),
])
def test_international_and_generic_hypothesis_values_are_valid(kind, value):
    assessment = assess_hypothesis_value(kind, value)
    if assessment.status == "AMBIGUOUS":
        from app.concept_portfolio_v2.models import HypothesisDecision
        decision = HypothesisDecision(hypothesisType=kind, proposedValue=value,
            source="AI_HYPOTHESIS", decisionStatus="PROPOSED", semanticStatus="AMBIGUOUS")
        resolved = run(ConceptPortfolioEngine().resolve_hypothesis_semantics([decision]))[0]
        assert resolved.semanticStatus == "VALID"
    else:
        assert assessment.status == "VALID"


def test_ambiguous_non_placeholder_hypothesis_uses_one_semantic_batch():
    engine = ConceptPortfolioEngine()
    result = run(engine.run_full(payload(SCENARIOS[1])))
    hypotheses = engine.build_or_load_current_hypothesis_contract(result.concepts[0])
    channels = next(index for index, item in enumerate(hypotheses) if item.hypothesisType == "CHANNELS")
    hypotheses[channels] = hypotheses[channels].model_copy(update={
        "proposedValue": "분기별 협력사 설명회", "semanticStatus": "AMBIGUOUS"})
    before = engine.gateway.usage.logicalOperations
    resolved = run(engine.resolve_hypothesis_semantics(hypotheses))
    assert next(item for item in resolved if item.hypothesisType == "CHANNELS").semanticStatus == "VALID"
    assert engine.gateway.usage.logicalOperations == before + 1


def test_semantic_batch_rejects_value_from_a_different_hypothesis_field():
    from app.concept_portfolio_v2.models import HypothesisDecision
    decision = HypothesisDecision(hypothesisType="TARGET_REGION", proposedValue="고객별 견적",
        source="AI_HYPOTHESIS", decisionStatus="PROPOSED", semanticStatus="AMBIGUOUS")
    resolved = run(ConceptPortfolioEngine().resolve_hypothesis_semantics([decision]))[0]
    assert resolved.semanticStatus == "INVALID"


@pytest.mark.parametrize("value", ["미정", "미제공", "명시되지 않음", "검증 필요", "TBD", "추후 결정"])
def test_unresolved_hypothesis_markers_remain_blocked(value):
    assert assess_hypothesis_value("TARGET_REGION", value).status == "UNRESOLVED"


def test_legal_precheck_respects_negation_and_positive_intermediation():
    engine = ConceptPortfolioEngine()
    result = run(engine.run_full(payload(SCENARIOS[2])))
    base = result.concepts[0]
    absent = base.model_copy(update={"candidate": base.candidate.model_copy(update={
        "sellerRole": "플랫폼은 직접 판매하지 않음", "intermediaryRole": "중개하지 않음"})})
    positive = base.model_copy(update={"candidate": base.candidate.model_copy(update={
        "intermediaryRole": "플랫폼이 제3자 거래를 중개"})})
    assert not engine.legal_precheck(absent).directSeller
    assert not engine.legal_precheck(absent).intermediary
    assert engine.legal_precheck(positive).intermediary


def test_business_role_presence_and_semantic_correctness_are_separate():
    assert assess_role_semantics("intermediaryRole", "배송 담당") == "MISMATCH"
    assert assess_role_semantics("sellerRole", "앱 화면 운영") == "MISMATCH"
    assert assess_role_semantics(
        "providerRole", "서비스를 직접 제공하지 않고 제휴 전문가가 제공") == "MATCH"

    engine = ConceptPortfolioEngine()
    result = run(engine.run_full(payload(SCENARIOS[2])))
    candidate = result.concepts[0].candidate.model_copy(update={"intermediaryRole": "배송 담당"})
    completeness = assess_legal_fact_completeness(candidate)
    assert completeness.status == "COMPLETABLE"
    assert next(item for item in completeness.roleSemantics
                if item["field"] == "intermediaryRole")["status"] == "MISMATCH"


def test_lock_profiles_preserve_system_governed_values_even_if_provider_changes_them():
    base = payload(SCENARIOS[1])
    profiles = [
        ({}, "EXPLORE"),
        ({"targetRegion": {"value": "일본", "decisionState": "LOCKED"},
          "channels": {"value": "Slack 앱 디렉터리", "decisionState": "LOCKED"}}, "REFINE"),
        ({"targetRegion": {"value": "미국 캘리포니아", "decisionState": "LOCKED"},
          "revenueModel": {"value": "B2B 연간 계약", "decisionState": "LOCKED"},
          "price": {"value": "고객별 견적", "decisionState": "LOCKED"},
          "channels": {"value": "직접 영업", "decisionState": "LOCKED"}}, "AS_IS"),
    ]
    for locks, expected in profiles:
        scenario = {**base, **locks, "fixtureName": "provider_wrong_lock"}
        engine = ConceptPortfolioEngine()
        seed = engine.seed_adapter.adapt(scenario)
        analysis = run(engine.analyze_seed(seed))
        assert analysis.explorationBreadth.value == expected
        result = run(engine.run_full(scenario))
        for concept in result.concepts:
            for key, governed in locks.items():
                assert getattr(concept.candidate, key) == governed["value"]


def test_current_legal_adapter_preserves_unknown_facts(monkeypatch):
    async def fake_review(_):
        return {
            "status": "NEEDS_FACTS", "safeUserSummary": "외부 사실 확인 필요",
            "unknownFacts": ["기존 계약의 재위탁 조항"], "requiredControls": [],
            "requiredPartnersAndQualifications": [], "redesignRequirements": [],
            "prohibitedVariants": [], "requiredDisclosures": [], "officialEvidence": [],
        }
    monkeypatch.setattr(adapters_module, "execute_concept_legal_review", fake_review)
    engine = ConceptPortfolioEngine()
    result = run(engine.run_full(payload(SCENARIOS[1])))
    reviewed = run(adapters_module.CurrentLegalAdapter().review(
        "C1", result.concepts[0].candidate, engine.seed_adapter.adapt(payload(SCENARIOS[1]))))
    assert reviewed.route == LegalRoute.NEEDS_INPUT
    assert reviewed.unknownFacts == ["기존 계약의 재위탁 조항"]


@pytest.mark.parametrize(("provider", "expected_status", "expected_count"), [
    (MockPortfolioProvider(), "READY_FULL", 5),
    (CandidateNeedsInputProvider(), "READY_LIMITED", 4),
    (GlobalLegalFailureProvider(), "FAILED", 0),
])
def test_production_entrypoint_keeps_run_full_status_contract(provider, expected_status, expected_count):
    engine = ProductionObservedConceptPortfolioEngine(gateway=ProviderGateway(provider=provider))
    canonical_seed = engine.seed_adapter.adapt(payload(SCENARIOS[1]))
    result = run(execute_concept_portfolio_v2(
        {"seed": canonical_seed, "maxConcepts": 5}, engine=engine))
    assert result["engineStatus"] == expected_status
    assert result["producedConceptCount"] == expected_count
    if expected_status != "FAILED":
        assert result["downstreamReadiness"] == "PENDING_HYPOTHESIS_CONFIRMATION"
