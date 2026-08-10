import asyncio
import json
from pathlib import Path

import pytest

from app.concept_portfolio_v2 import ConceptPortfolioEngine
from app.concept_portfolio_v2.legal_fact_completeness import (
    assess_legal_fact_completeness, normalized_requirements, validate_redesign_requirements,
)
from app.concept_portfolio_v2.language_policy import is_governance_placeholder


FIXTURES = Path(__file__).resolve().parents[2] / "fixtures" / "concept_portfolio_v2"


def run(value):
    return asyncio.run(value)


def payload(name="food_minimal"):
    return json.loads((FIXTURES / f"{name}.json").read_text(encoding="utf-8"))


def first_candidate():
    engine = ConceptPortfolioEngine()
    seed = engine.seed_adapter.adapt(payload())
    engine._reset()
    analysis = run(engine.analyze_seed(seed))
    plans = run(engine.prepare_portfolio_plans(seed, analysis, 1))
    prepared = run(engine.prepare_candidate_portfolio(seed, plans, 1))
    return engine, seed, prepared.candidates[0]


@pytest.mark.parametrize("value", ["OPEN", "가격 검증 필요", "유통 채널 정보가 필요합니다", "추후 확인"])
def test_80_live_placeholder_variants_are_detected(value):
    assert is_governance_placeholder(value)


def test_81_pure_digital_candidate_can_have_no_physical_activity():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={
        "solutionMechanism": "웹에서 반복 업무를 자동화",
        "transactionFlow": ["고객사가 운영사와 이용 계약", "운영사가 웹 서비스를 제공"],
        "paymentFlow": ["고객사가 운영사에 월 이용료를 결제"],
        "physicalActivities": [], "price": "월 정액 요금", "channels": "운영사 웹",
    })
    assert assess_legal_fact_completeness(candidate).status == "COMPLETE"


def test_82_physical_fulfillment_requires_actor_and_activity():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={
        "solutionMechanism": "고객 주문 상품을 배송",
        "transactionFlow": ["고객이 운영사에 주문하고 결제"],
        "paymentFlow": ["운영사가 고객 결제를 수취"],
        "physicalActivities": [], "price": "건별 요금", "channels": "운영사 앱",
    })
    report = assess_legal_fact_completeness(candidate)
    assert report.status == "COMPLETABLE" and "physicalActivities" in report.affectedFields


def test_83_app_order_flow_requires_explicit_data_purpose():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={
        "solutionMechanism": "앱 주문과 고객 연락을 지원",
        "personalDataUsage": [], "price": "건별 요금", "channels": "운영사 앱",
    })
    report = assess_legal_fact_completeness(candidate)
    assert "personalDataUsage" in report.affectedFields


def test_84_partner_network_requires_business_role_not_legal_guess():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={
        "partnerModel": "외부 전문가 파트너 네트워크", "partnerRequirements": [],
        "price": "건별 요금", "channels": "운영사 웹",
    })
    report = assess_legal_fact_completeness(candidate)
    assert "partnerRequirements" in report.affectedFields
    assert not any("법" in item for item in report.completionRequirements)


def test_85_unresolved_direct_sale_and_intermediation_conflict_is_invalid():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={
        "sellerRole": "플랫폼이 동일 거래에서 직접 판매",
        "intermediaryRole": "플랫폼이 동일 거래의 판매자와 구매자를 중개",
        "transactionFlow": ["고객이 서비스를 이용"], "price": "건별 요금", "channels": "운영사 앱",
    })
    assert assess_legal_fact_completeness(candidate).status == "INVALID"


def test_86_redesign_compliance_requires_substantive_field_change():
    _, _, envelope = first_candidate()
    parent = envelope.candidate
    failed = validate_redesign_requirements(parent, parent, ["결제 수취 주체와 정산 흐름을 명시"])
    child = parent.model_copy(update={"paymentFlow": ["운영사가 고객 결제를 수취하고 제휴사에 정산"]})
    passed = validate_redesign_requirements(parent, child, ["결제 수취 주체와 정산 흐름을 명시"])
    assert failed.status == "FAIL" and passed.status == "PASS"


def test_87_redesign_requirement_normalization_detects_same_loop():
    assert normalized_requirements(["결제 주체 명시"]) == normalized_requirements([" 결제   주체 명시 "])


def test_88_fact_completion_uses_same_lineage_and_one_attempt():
    result = run(ConceptPortfolioEngine().run_full(payload()))
    assert all(item.lineageId.startswith("L") for item in result.concepts)
    assert result.runSummary.legalFactCompletionAttempted == result.runSummary.legalFactCompletionAccepted
    assert all(item.candidateId.count("-F1") <= 1 for item in result.concepts)


def test_89_repeated_redesign_is_terminal_and_partial_portfolio_survives():
    result = run(ConceptPortfolioEngine().run_full(payload("second_redesign")))
    assert result.runStatus.value == "READY_LIMITED" and result.producedConceptCount == 4
    assert any(item.recoveryResolution == "LEGAL_REDESIGN_LOOP_DETECTED"
               for item in result.legalSummaries)
    assert result.runSummary.legalRedesignExhausted == 1


def test_90_legal_replan_metrics_distinguish_attempt_validate_accept():
    result = run(ConceptPortfolioEngine().run_full(payload("legal_replan")))
    summary = result.runSummary
    assert (summary.legalReplanAttempted, summary.legalReplanValidated,
            summary.legalReplanAccepted, summary.legalReplanExhausted) == (1, 1, 1, 0)
