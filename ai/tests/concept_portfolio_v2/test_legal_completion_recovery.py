import asyncio
import json
from pathlib import Path

import pytest

from app.concept_portfolio_v2 import ConceptPortfolioEngine
from app.concept_portfolio_v2.legal_fact_completeness import (
    assess_legal_fact_completeness, classify_fact_presence, normalized_requirements,
    validate_redesign_requirements,
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


def test_91_direct_seller_with_explicit_no_intermediary_is_complete():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={
        "platformRole": "운영사가 고객 접점과 거래를 직접 운영",
        "providerRole": "운영사가 서비스를 직접 제공",
        "sellerRole": "운영사가 고객에게 직접 판매",
        "intermediaryRole": "제3자 거래를 중개하지 않음",
        "transactionFlow": ["사용자가 운영사에 주문하고 운영사가 판매·이행"],
        "paymentFlow": ["사용자가 운영사에 결제"],
    })
    assert assess_legal_fact_completeness(candidate).status == "COMPLETE"


def test_92_marketplace_can_explicitly_deny_platform_seller_role():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={
        "platformRole": "플랫폼이 고객과 제휴 판매자의 거래 기준을 운영",
        "providerRole": "제휴 판매자가 서비스를 제공",
        "sellerRole": "플랫폼은 직접 판매하지 않으며 제휴 판매자가 판매 책임을 부담",
        "intermediaryRole": "플랫폼이 고객과 제휴 판매자의 거래를 중개",
        "transactionFlow": ["고객 주문을 플랫폼이 제휴 판매자에게 전달하고 판매자가 이행"],
        "paymentFlow": ["플랫폼이 결제를 수취한 뒤 판매자에게 정산"],
        "partnerRequirements": ["제휴 판매자가 판매와 이행을 담당"],
    })
    assert assess_legal_fact_completeness(candidate).status == "COMPLETE"


def test_93_explicit_absent_provider_is_complete_when_other_provider_is_clear():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={
        "providerRole": "플랫폼 운영사에는 해당 없음",
        "sellerRole": "제휴 판매자가 고객에게 판매",
        "intermediaryRole": "플랫폼이 거래를 중개",
        "transactionFlow": ["제휴 판매자가 고객에게 서비스를 제공하고 이행"],
        "partnerRequirements": ["제휴 판매자가 제공 책임을 부담"],
    })
    assert assess_legal_fact_completeness(candidate).status == "COMPLETE"


def test_94_empty_intermediary_is_contextually_complete_for_direct_transaction():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={
        "sellerRole": "운영사가 고객에게 직접 판매", "intermediaryRole": "",
        "transactionFlow": ["사용자가 운영사와 직접 계약하고 운영사가 직접 이행"],
    })
    assert assess_legal_fact_completeness(candidate).status == "COMPLETE"


def test_95_unknown_intermediary_remains_completable():
    _, _, envelope = first_candidate()
    candidate = envelope.candidate.model_copy(update={"intermediaryRole": "미정"})
    report = assess_legal_fact_completeness(candidate)
    assert report.status == "COMPLETABLE" and "intermediaryRole" in report.affectedFields


@pytest.mark.parametrize("value", ["중개하지 않음", "제3자 판매자가 없음", "외부 파트너를 사용하지 않음"])
def test_96_explicit_negative_facts_are_valid_absence(value):
    assert classify_fact_presence(value) == "EXPLICIT_ABSENCE"


@pytest.mark.parametrize("value", ["확인 필요", "미정", "관련 역할", "추후 결정"])
def test_97_unknown_role_values_are_not_explicit_absence(value):
    assert classify_fact_presence(value) == "UNKNOWN"
