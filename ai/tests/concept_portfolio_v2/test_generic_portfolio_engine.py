import asyncio
import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway
from app.concept_portfolio_v2.distinctness import deterministic_distinctness
from app.concept_portfolio_v2.mechanics import GENERIC_CODE_SETS, GenericConceptNormalizer
from app.concept_portfolio_v2.models import FailureCode, PortfolioPlanDraft
from app.concept_portfolio_v2.providers import MockPortfolioProvider
from app.tasks.concept_portfolio_v2.service import execute_concept_portfolio_v2
from app.tasks.concept_portfolio_v2.observer import ProductionObservedConceptPortfolioEngine


FIXTURE = Path(__file__).resolve().parents[2] / "fixtures" / "concept_portfolio_v2" / "generic_domains.json"
GENERIC_DOMAINS = json.loads(FIXTURE.read_text(encoding="utf-8"))


def run(value):
    return asyncio.run(value)


def stage(payload, *, gateway=None, engine=None):
    engine = engine or ConceptPortfolioEngine(gateway=gateway)
    seed = engine.seed_adapter.adapt(payload)
    engine._reset()
    run(engine.check_safety(seed))
    analysis = run(engine.analyze_seed(seed))
    validation = run(engine.prepare_portfolio_plans(seed, analysis))
    candidates = run(engine.expand_plans(seed, validation.acceptedPlans))
    accepted, reports = run(engine.validate_candidates(seed, validation.acceptedPlans, candidates))
    return engine, seed, analysis, validation, candidates, accepted, reports


@pytest.mark.parametrize("payload", GENERIC_DOMAINS, ids=[item["domain"] for item in GENERIC_DOMAINS])
def test_generic_domains_produce_valid_portfolios_without_domain_rules(payload):
    result = run(ConceptPortfolioEngine().run_full(payload, auto_confirm_hypotheses=True))
    assert result.runStatus.value in {"READY_FULL", "READY_LIMITED"}
    assert result.producedConceptCount >= 3
    assert result.handoff and result.handoff.contractStatus == "CONTRACT_PASS"
    assert not any(item.reasonCode in {FailureCode.ANCHOR_DRIFT, FailureCode.PLAN_FIDELITY_FAILED}
                   for item in result.rejectedPlans)
    for concept in result.concepts:
        architecture = concept.descriptor.architecture.model_dump()
        assert any(value != "OTHER" for key, value in architecture.items() if key in GENERIC_CODE_SETS)


def test_exact_business_renamed_is_duplicate():
    _, _, _, validation, *_ = stage(GENERIC_DOMAINS[0])
    descriptor = validation.acceptedPlans[0].descriptor
    assert deterministic_distinctness("원본", "이름만 변경", descriptor, descriptor).decision == "DUPLICATE"


def test_same_architecture_meaningful_target_or_use_case_is_variant():
    _, _, _, validation, *_ = stage(GENERIC_DOMAINS[1])
    left = validation.acceptedPlans[0].descriptor
    thesis = left.thesis.model_copy(update={
        "targetSegmentThesis": "야간 근무가 많은 소규모 운영팀",
        "useCaseThesis": "마감 직전 긴급 보고 상황"})
    right = left.model_copy(update={"thesis": thesis})
    assert deterministic_distinctness("A", "B", left, right).decision == "VARIANT"


def test_same_target_different_solution_mechanism_is_distinct():
    _, _, _, validation, *_ = stage(GENERIC_DOMAINS[3])
    left = validation.acceptedPlans[0].descriptor
    thesis = left.thesis.model_copy(update={"solutionThesis": "동료 학습자가 서로 과제를 교환하고 평가"})
    right = left.model_copy(update={"thesis": thesis, "mechanismFamily": "동료 상호 평가"})
    assert deterministic_distinctness("A", "B", left, right).decision == "DISTINCT"


def test_same_solution_marketplace_vs_direct_operator_is_distinct():
    _, _, _, validation, *_ = stage(GENERIC_DOMAINS[2])
    left = validation.acceptedPlans[0].descriptor
    alternative = "PRINCIPAL_OPERATOR" if left.architecture.businessRole == "MARKETPLACE" else "MARKETPLACE"
    architecture = left.architecture.model_copy(update={"businessRole": alternative})
    right = left.model_copy(update={"architecture": architecture,
                                    "familyId": f"{alternative}:{architecture.operatingModel}"})
    assert deterministic_distinctness("A", "B", left, right).decision == "DISTINCT"


def test_same_architecture_health_or_diet_wording_is_not_architecture_rejection():
    _, _, _, validation, *_ = stage(GENERIC_DOMAINS[0])
    left = validation.acceptedPlans[0].descriptor
    thesis = left.thesis.model_copy(update={
        "valuePropositionThesis": left.thesis.valuePropositionThesis + " 건강 목표를 강조",
        "offerThesis": left.thesis.offerThesis + " 식단 선호 옵션"})
    right = left.model_copy(update={"thesis": thesis})
    deterministic = deterministic_distinctness("A", "B", left, right)
    assert deterministic.decision == "AMBIGUOUS"
    semantic = run(MockPortfolioProvider().judge_distinctness(
        "PLAN", left.model_dump(mode="json"), right.model_dump(mode="json")))
    assert semantic.decision in {"DUPLICATE", "VARIANT"}


def test_provider_plan_draft_cannot_supply_canonical_code():
    _, _, _, validation, *_ = stage(GENERIC_DOMAINS[4])
    payload = validation.acceptedPlans[0].model_dump(exclude={
        "planId", "descriptor", "preservedAnchors", "preservedLocks"})
    payload["mechanics"] = {"businessRole": "PROVIDER_ARBITRARY_CODE"}
    with pytest.raises(ValidationError):
        PortfolioPlanDraft.model_validate(payload)
    assert "descriptor" not in PortfolioPlanDraft.model_fields


@pytest.mark.parametrize("payload", GENERIC_DOMAINS, ids=[item["domain"] for item in GENERIC_DOMAINS])
def test_plan_and_candidate_use_same_normalizer_and_fidelity_passes_or_adapts(payload):
    _, _, _, validation, _, accepted, reports = stage(payload)
    assert accepted
    assert all(report.fidelityDecision in {"PASS", "ADAPTED"} for report in reports if report.accepted)
    by_plan = {item.planId: item for item in validation.acceptedPlans}
    for candidate in accepted:
        deterministic_candidate = GenericConceptNormalizer.from_candidate(candidate.candidate)
        deterministic_plan = GenericConceptNormalizer.from_plan(by_plan[candidate.planId])
        assert candidate.descriptor.thesis == deterministic_candidate.thesis
        assert by_plan[candidate.planId].descriptor.thesis == deterministic_plan.thesis
        assert all(item.source in {"RULE", "SEMANTIC", "UNKNOWN"}
                   for item in candidate.descriptor.architectureDiagnostics.values())


def test_adaptive_planning_replenishes_after_duplicate_heavy_initial_pool():
    class DuplicateHeavyProvider(MockPortfolioProvider):
        replenishment_calls = 0

        async def plan_pool(self, seed, design, pool_size):
            plans = await super().plan_pool(seed, design, pool_size)
            base = plans[0]
            for index in range(1, min(5, len(plans))):
                plans[index] = base.model_copy(update={"title": f"표현만 바꾼 동일안 {index}"})
            return plans

        async def replenish_plans(self, *args, **kwargs):
            self.replenishment_calls += 1
            return await super().replenish_plans(*args, **kwargs)

    provider = DuplicateHeavyProvider()
    engine = ConceptPortfolioEngine(gateway=ProviderGateway(provider=provider))
    _, _, _, validation, *_ = stage(GENERIC_DOMAINS[5], engine=engine)
    assert provider.replenishment_calls >= 1
    assert len(validation.acceptedPlans) == 5
    assert validation.planningRounds >= 2


def test_open_space_exhausted_returns_ready_limited_without_artificial_five():
    class ExhaustedProvider(MockPortfolioProvider):
        async def plan_pool(self, seed, design, pool_size):
            return (await super().plan_pool(seed, design, pool_size))[:3]

        async def replenish_plans(self, *args, **kwargs):
            return []

    gateway = ProviderGateway(provider=ExhaustedProvider())
    result = run(ConceptPortfolioEngine(gateway=gateway).run_full(GENERIC_DOMAINS[6]))
    assert result.runStatus.value == "READY_LIMITED" and result.producedConceptCount == 3


def test_same_family_preference_is_soft_not_hard_rejection():
    _, _, _, validation, *_ = stage(GENERIC_DOMAINS[1])
    families = [item.descriptor.familyId for item in validation.acceptedPlans]
    assert len(validation.acceptedPlans) == 5
    assert max(families.count(value) for value in set(families)) <= 2


def test_production_entrypoint_uses_same_engine_and_contract():
    payload = GENERIC_DOMAINS[4]
    direct = run(ConceptPortfolioEngine().run_full(payload))
    injected_engine = ProductionObservedConceptPortfolioEngine()
    canonical_seed = injected_engine.seed_adapter.adapt(payload)
    task = run(execute_concept_portfolio_v2(
        {"seed": canonical_seed, "maxConcepts": 5}, engine=injected_engine))
    assert task["engineStatus"] == direct.runStatus.value
    assert task["producedConceptCount"] == direct.producedConceptCount
    assert [item["descriptor"] for item in task["concepts"]] == [
        item.descriptor.model_dump(mode="json") for item in direct.concepts]


def test_core_policy_files_contain_no_removed_food_specific_rules():
    root = Path(__file__).resolve().parents[2] / "app" / "concept_portfolio_v2"
    text = "\n".join((root / name).read_text(encoding="utf-8")
                     for name in ("anchor_policy.py", "mechanics.py", "plan_policy.py"))
    forbidden = ("SUPPLY_INTENT", "ENTERPRISE_DRIFT", "SUBSCRIPTION_PORTIONING",
                 "RECIPE_BUNDLE", "기업 급식", "학교 급식", "구내식당")
    assert not any(value in text for value in forbidden)
