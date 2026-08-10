import asyncio
import json
from pathlib import Path

import pytest

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway
from app.concept_portfolio_v2.diagnostics.notebook_view import show_idea_readiness
from app.concept_portfolio_v2.distinctness import deterministic_distinctness
from app.concept_portfolio_v2.mechanics import GenericConceptNormalizer
from app.concept_portfolio_v2.models import SemanticFidelityResult
from app.concept_portfolio_v2.providers import MockPortfolioProvider


FIXTURES = Path(__file__).resolve().parents[2] / "fixtures" / "concept_portfolio_v2"
GENERIC_DOMAINS = json.loads((FIXTURES / "generic_domains.json").read_text(encoding="utf-8"))


def run(value):
    return asyncio.run(value)


def drift(draft):
    return draft.model_copy(update={
        "solutionMechanism": "사용 행태 신호를 분석해 필요한 행동량을 예측하고 맞춤 실행안을 제안",
        "featureSet": ["행태 신호 분석", "맞춤 실행안"],
    })


class RecoveryProvider(MockPortfolioProvider):
    def __init__(self, *, bad_slots=(), semantic_fail=False, regeneration_pass=True,
                 initial_pool_size=None, replenishment_enabled=True):
        self.bad_slots = set(bad_slots)
        self.semantic_fail = semantic_fail
        self.regeneration_pass = regeneration_pass
        self.initial_pool_size = initial_pool_size
        self.replenishment_enabled = replenishment_enabled
        self.initial_expand_calls = 0
        self.bad_plan_ids = set()
        self.classifier_calls = 0

    async def plan_pool(self, seed, design, pool_size):
        plans = await super().plan_pool(seed, design, pool_size)
        return plans[:self.initial_pool_size] if self.initial_pool_size else plans

    async def expand(self, seed, plan, candidate_index):
        draft = await super().expand(seed, plan, candidate_index)
        self.initial_expand_calls += 1
        if self.initial_expand_calls <= 5 and candidate_index in self.bad_slots:
            self.bad_plan_ids.add(plan.planId)
        return drift(draft) if plan.planId in self.bad_plan_ids else draft

    async def judge_fidelity(self, plan, candidate):
        if self.semantic_fail and "행태 신호" in candidate.solutionMechanism:
            return SemanticFidelityResult(
                decision="FAIL", matchedMechanics=["targetSegmentThesis"],
                missingMechanics=["offerThesis", "solutionThesis"],
                safeSummary="Plan solution identity가 충분히 보존되지 않았습니다.")
        return await super().judge_fidelity(plan, candidate)

    async def regenerate_candidate(self, seed, design, plan, previous, failure_summary,
                                   missing_identity, candidate_index):
        recovered = await super().regenerate_candidate(
            seed, design, plan, previous, failure_summary, missing_identity, candidate_index)
        return recovered if self.regeneration_pass else drift(recovered)

    async def replenish_plans(self, *args, **kwargs):
        if not self.replenishment_enabled:
            return []
        return await super().replenish_plans(*args, **kwargs)

    async def classify_architectures(self, items):
        self.classifier_calls += 1
        return await super().classify_architectures(items)


async def prepare(payload, provider):
    engine = ConceptPortfolioEngine(gateway=ProviderGateway(provider=provider))
    seed = engine.seed_adapter.adapt(payload)
    engine._reset()
    await engine.check_safety(seed)
    analysis = await engine.analyze_seed(seed)
    plans = await engine.prepare_portfolio_plans(seed, analysis)
    candidates = await engine.prepare_candidate_portfolio(seed, plans)
    return engine, seed, plans, candidates


def test_initial_five_candidates_pass_without_recovery():
    _, _, _, result = run(prepare(GENERIC_DOMAINS[0], RecoveryProvider()))
    assert len(result.candidates) == 5
    assert result.candidateAcceptedInitially == 5
    assert result.candidateRegenerated == result.reservePlansActivated == 0


def test_ambiguous_fidelity_calls_semantic_and_adapted_keeps_five():
    engine, _, _, result = run(prepare(
        GENERIC_DOMAINS[1], RecoveryProvider(bad_slots={4})))
    assert len(result.candidates) == 5 and result.candidateRegenerated == 0
    report = next(item for item in result.reports if item.candidateId == "C4")
    assert report.accepted and report.semanticFidelityUsed and report.fidelityDecision == "ADAPTED"
    assert any(item.action == "SEMANTIC_FIDELITY_CHECKED" for item in engine.trace)


def test_semantic_fail_triggers_one_targeted_regeneration_and_full_validation():
    engine, _, _, result = run(prepare(GENERIC_DOMAINS[2], RecoveryProvider(
        bad_slots={4}, semantic_fail=True, regeneration_pass=True)))
    assert len(result.candidates) == 5
    assert result.candidateRegenerated == 1 and result.candidateRecovered == 1
    child = next(item for item in result.candidates if item.candidateAttempt == 2)
    child_report = next(item for item in result.reports if item.candidateId == child.candidateId)
    assert child.parentCandidateId == "C4" and child_report.accepted
    assert any(item.action == "CANDIDATE_REGENERATED" for item in engine.trace)


def test_candidate_portfolio_integration_three_pass_one_adapted_one_regenerated():
    class MixedProvider(MockPortfolioProvider):
        calls = 0
        failed_plan = None

        async def expand(self, seed, plan, candidate_index):
            draft = await super().expand(seed, plan, candidate_index)
            self.calls += 1
            if self.calls <= 5 and candidate_index == 4:
                return drift(draft)
            if self.calls <= 5 and candidate_index == 5:
                self.failed_plan = plan.planId
                return draft.model_copy(update={
                    "solutionMechanism": "패턴 분류 신호로 별도 실행안을 제안",
                    "featureSet": ["패턴 분류", "별도 실행안"],
                })
            return draft

        async def judge_fidelity(self, plan, candidate):
            if "패턴 분류 신호" in candidate.solutionMechanism:
                return SemanticFidelityResult(
                    decision="FAIL", matchedMechanics=["targetSegmentThesis"],
                    missingMechanics=["offerThesis", "solutionThesis"],
                    safeSummary="Plan identity 보완이 필요합니다.")
            return await super().judge_fidelity(plan, candidate)

    _, _, _, result = run(prepare(GENERIC_DOMAINS[0], MixedProvider()))
    assert len(result.candidates) == 5
    assert result.candidateAcceptedInitially == 4
    assert result.candidateRegenerated == 1 and result.candidateRecovered == 1
    assert any(item.candidateId == "C4" and item.fidelityDecision == "ADAPTED"
               for item in result.reports)


def test_regeneration_failure_activates_highest_marginal_reserve():
    _, _, plans, result = run(prepare(GENERIC_DOMAINS[3], RecoveryProvider(
        bad_slots={4}, semantic_fail=True, regeneration_pass=False)))
    assert len(result.candidates) == 5 and result.reservePlansActivated >= 1
    assert any(item.recoverySource == "RESERVE_PLAN" for item in result.candidates)
    assert plans.reservePlans


def test_reserve_shortfall_uses_candidate_replenishment():
    _, _, _, result = run(prepare(GENERIC_DOMAINS[4], RecoveryProvider(
        bad_slots={4, 5}, semantic_fail=True, regeneration_pass=False,
        initial_pool_size=6)))
    assert len(result.candidates) == 5
    assert result.reservePlansActivated == 1
    assert result.candidateRecoveryReplans >= 1
    assert any(item.recoverySource == "REPLENISHED_PLAN" for item in result.candidates)


def test_candidate_duplicate_skips_same_plan_regeneration_and_uses_reserve():
    class DuplicateCandidateProvider(MockPortfolioProvider):
        first = None
        calls = 0

        async def expand(self, seed, plan, candidate_index):
            draft = await super().expand(seed, plan, candidate_index)
            self.calls += 1
            if candidate_index == 1:
                self.first = draft
            if self.calls <= 5 and candidate_index == 5:
                return self.first
            return draft

    _, _, _, result = run(prepare(GENERIC_DOMAINS[0], DuplicateCandidateProvider()))
    duplicate = next(item for item in result.reports
                     if any(code.value == "CANDIDATE_DUPLICATE" for code in item.reasonCodes))
    assert duplicate.outcome == "TERMINAL_INVALID"
    assert result.candidateRegenerated == 0
    assert result.reservePlansActivated >= 1 and len(result.candidates) == 5


def test_exhausted_recovery_returns_ready_limited_three():
    provider = RecoveryProvider(bad_slots={4, 5}, semantic_fail=True,
                                regeneration_pass=False, initial_pool_size=5,
                                replenishment_enabled=False)
    result = run(ConceptPortfolioEngine(gateway=ProviderGateway(provider=provider)).run_full(
        GENERIC_DOMAINS[5]))
    assert result.runStatus.value == "READY_LIMITED"
    assert result.producedConceptCount == 3
    assert result.runSummary.candidateRegenerated == 2


@pytest.mark.parametrize("payload", GENERIC_DOMAINS[:3], ids=[item["domain"] for item in GENERIC_DOMAINS[:3]])
def test_generic_domain_fidelity_recovery_is_not_food_specific(payload):
    _, _, _, result = run(prepare(payload, RecoveryProvider(
        bad_slots={4}, semantic_fail=True, regeneration_pass=True)))
    assert len(result.candidates) == 5 and result.candidateRecovered == 1


def test_plan_selection_is_stable_when_provider_order_changes():
    class ReverseProvider(MockPortfolioProvider):
        async def plan_pool(self, seed, design, pool_size):
            return list(reversed(await super().plan_pool(seed, design, pool_size)))

    normal = run(prepare(GENERIC_DOMAINS[6], MockPortfolioProvider()))[2]
    reverse = run(prepare(GENERIC_DOMAINS[6], ReverseProvider()))[2]
    assert {item.title for item in normal.acceptedPlans} == {item.title for item in reverse.acceptedPlans}
    assert all(item.selectionReason and item.selectionScore > 0 for item in normal.acceptedPlans)


def test_unknown_architecture_is_not_forced_to_direct_operator_defaults():
    _, _, plans, _ = run(prepare(GENERIC_DOMAINS[0], MockPortfolioProvider()))
    draft = plans.acceptedPlans[0].model_copy(update={
        "solutionThesis": "일반적인 해결 방식을 제공",
        "operatingApproach": "운영 방식은 검증 예정",
        "partnerApproach": "외부 연계 구조는 검증 예정",
        "transactionApproach": "거래 방식은 검증 예정",
        "commercialApproach": "수익 방식은 검증 예정",
        "fulfillmentApproach": "이행 방식은 검증 예정",
        "customerInteraction": "접점 방식은 검증 예정",
    })
    descriptor = GenericConceptNormalizer.from_plan(draft)
    assert descriptor.architecture.businessRole == "OTHER"
    assert descriptor.architecture.operatingModel == "OTHER"
    assert descriptor.architecture.transactionModel == "OTHER"
    assert descriptor.architectureDiagnostics["businessRole"].confidence == "LOW"


def test_clear_marketplace_and_saas_rules_are_high_confidence():
    _, _, plans, _ = run(prepare(GENERIC_DOMAINS[0], MockPortfolioProvider()))
    base = plans.acceptedPlans[0]
    marketplace = GenericConceptNormalizer.from_plan(base.model_copy(update={
        "solutionThesis": "검증된 공급자와 수요자를 연결하는 마켓플레이스",
        "operatingApproach": "플랫폼이 거래 기준을 운영",
        "partnerApproach": "검증된 공급자 네트워크",
        "transactionApproach": "플랫폼 매칭 거래",
    }))
    saas = GenericConceptNormalizer.from_plan(base.model_copy(update={
        "solutionThesis": "반복 업무를 자동화하는 SaaS 소프트웨어 도구",
        "operatingApproach": "자동화 디지털 운영",
        "partnerApproach": "외부 API 연계",
        "transactionApproach": "정기 이용권 제공",
        "commercialApproach": "월 구독료",
    }))
    assert marketplace.architecture.businessRole == "MARKETPLACE"
    assert marketplace.architectureDiagnostics["businessRole"].confidence == "HIGH"
    assert saas.architecture.businessRole == "SAAS_TOOL"
    assert saas.architectureDiagnostics["businessRole"].confidence == "HIGH"


def test_low_confidence_primary_difference_does_not_force_distinct():
    _, _, plans, _ = run(prepare(GENERIC_DOMAINS[0], MockPortfolioProvider()))
    left = plans.acceptedPlans[0].descriptor
    architecture = left.architecture.model_copy(update={"businessRole": "OTHER"})
    diagnostics = dict(left.architectureDiagnostics)
    diagnostics["businessRole"] = diagnostics["businessRole"].model_copy(update={
        "code": "OTHER", "confidence": "LOW", "source": "UNKNOWN"})
    right = left.model_copy(update={"architecture": architecture,
                                    "architectureDiagnostics": diagnostics,
                                    "familyId": f"OTHER:{architecture.operatingModel}"})
    relation = deterministic_distinctness("A", "B", left, right)
    assert relation.decision == "VARIANT" and relation.relationConfidence == "LOW"


def test_low_confidence_architecture_invokes_batch_semantic_fallback():
    provider = RecoveryProvider()
    run(prepare(GENERIC_DOMAINS[0], provider))
    assert provider.classifier_calls >= 1


def test_legal_precheck_filters_placeholders_but_keeps_concrete_facts():
    engine, _, _, prepared = run(prepare(GENERIC_DOMAINS[0], MockPortfolioProvider()))
    base = prepared.candidates[0]
    placeholder = base.model_copy(update={"candidate": base.candidate.model_copy(update={
        "qualificationRequirements": ["해당 활동에 필요한 자격"],
        "personalDataUsage": ["필요한 경우 개인정보 처리"],
        "physicalActivities": ["필요한 경우 물리 활동"],
    })})
    assert not engine.legal_precheck(placeholder).qualificationDependency
    assert not engine.legal_precheck(placeholder).personalDataDependency
    assert not engine.legal_precheck(placeholder).regulatedPhysicalActivity
    concrete = base.model_copy(update={"candidate": base.candidate.model_copy(update={
        "qualificationRequirements": ["자동차 정비업 등록 사업자"],
        "personalDataUsage": ["배송을 위해 이름·주소·전화번호 처리"],
        "physicalActivities": ["사용자 주소로 상품 배송"],
    })})
    check = engine.legal_precheck(concrete)
    assert check.qualificationDependency and check.personalDataDependency and check.regulatedPhysicalActivity


def test_ready_for_review_score_zero_is_visible_as_diagnostic():
    engine = ConceptPortfolioEngine()
    seed = engine.seed_adapter.adapt(GENERIC_DOMAINS[0])
    context = engine.seed_adapter.local_context(seed).model_copy(update={
        "readiness": {"status": "READY_FOR_REVIEW", "score": 0, "missingFieldKeys": []}})
    shown = show_idea_readiness(context)
    assert shown["readinessDiagnostic"] == "READINESS_INCONSISTENT"
