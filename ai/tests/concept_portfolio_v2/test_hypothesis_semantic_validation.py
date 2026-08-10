import asyncio
import json
from pathlib import Path

import pytest

from app.concept_portfolio_v2 import ConceptPortfolioEngine, ProviderGateway
from app.concept_portfolio_v2.hypothesis_validation import assess_hypothesis_value
from app.concept_portfolio_v2.models import HypothesisDecision
from app.concept_portfolio_v2.providers import MockPortfolioProvider


FIXTURES = Path(__file__).resolve().parents[2] / "fixtures" / "concept_portfolio_v2"


def run(value):
    return asyncio.run(value)


@pytest.mark.parametrize(("kind", "value"), [
    ("TARGET_REGION", "대상 지역은 명시되지 않았습니다."),
    ("PRICE", "가격 정보는 미제공"),
    ("REVENUE_MODEL", "수익모델 검증 필요"),
    ("CHANNELS", "채널 검증 필요"),
    ("DIFFERENTIATORS", "차별화 가설 검증 필요"),
])
def test_98_semantic_placeholders_are_unresolved(kind, value):
    assert assess_hypothesis_value(kind, value).status == "UNRESOLVED"


@pytest.mark.parametrize(("kind", "value"), [
    ("TARGET_REGION", "대한민국"),
    ("PRICE", "월 구독료 19,900원 가설"),
    ("CHANNELS", "모바일 앱"),
])
def test_99_actual_hypothesis_values_are_valid(kind, value):
    assert assess_hypothesis_value(kind, value).status == "VALID"


def _hypothesis(kind, value):
    return HypothesisDecision(hypothesisType=kind, proposedValue=value,
                              source="AI_HYPOTHESIS", decisionStatus="PROPOSED")


def test_100_confirm_all_accepts_only_semantically_valid_values():
    engine = ConceptPortfolioEngine()
    hypotheses = [
        _hypothesis("TARGET_REGION", "대상 지역은 명시되지 않았습니다."),
        _hypothesis("REVENUE_MODEL", "월 구독"),
        _hypothesis("PRICE", "가격 정보는 미제공"),
        _hypothesis("CHANNELS", "모바일 앱"),
        _hypothesis("DIFFERENTIATORS", "반복 업무 자동화로 처리 시간 단축"),
        _hypothesis("PRE_MARKET_SOM_SHARE", {"targetSharePercent": 2.0, "horizonYears": 3,
                                              "rationale": "초기 가설", "assumptions": ["시장 검증"]}),
        _hypothesis("PRE_MARKET_SOM", {"amount": 100000000.0, "currency": "KRW", "period": "연간",
                                        "calculationBasis": "고객 수와 객단가 가설", "assumptions": ["초기 고객"],
                                        "confidence": "LOW"}),
    ]
    confirmed = engine.confirm_hypotheses(hypotheses, confirm_all_proposed=True)
    assert sum(item.accepted for item in confirmed) == 5
    unresolved = [item for item in confirmed if item.semanticStatus == "UNRESOLVED"]
    assert {item.hypothesisType for item in unresolved} == {"TARGET_REGION", "PRICE"}
    assert all(item.finalValue is None and item.decisionStatus == "PROPOSED" for item in unresolved)


def test_101_downstream_blocks_accepted_placeholder_defensively():
    payload = json.loads((FIXTURES / "food_minimal.json").read_text(encoding="utf-8"))
    engine = ConceptPortfolioEngine()
    result = run(engine.run_full(payload))
    seed = engine.seed_adapter.adapt(payload)
    selected = result.concepts[0]
    hypotheses = engine.confirm_hypotheses(
        engine.build_or_load_current_hypothesis_contract(selected), confirm_all_proposed=True)
    hypotheses[0] = hypotheses[0].model_copy(update={
        "proposedValue": "대상 지역은 명시되지 않았습니다.",
        "finalValue": "대상 지역은 명시되지 않았습니다.",
        "decisionStatus": "ACCEPTED", "semanticStatus": "VALID",
    })
    handoff = engine.build_downstream_handoff(seed, selected, hypotheses, result.legalSummaries)
    assert handoff.contractStatus == "CONTRACT_FAIL"
    assert any("UNRESOLVED_HYPOTHESES: TARGET_REGION" in item for item in handoff.validationErrors)


def test_102_full_valid_hypotheses_still_contract_pass():
    payload = json.loads((FIXTURES / "food_minimal.json").read_text(encoding="utf-8"))
    result = run(ConceptPortfolioEngine().run_full(payload, auto_confirm_hypotheses=True))
    assert result.handoff and result.handoff.contractStatus == "CONTRACT_PASS"


def test_103_auto_confirm_cannot_bypass_unresolved_price():
    class PlaceholderProvider(MockPortfolioProvider):
        async def expand(self, seed, plan, candidate_index):
            draft = await super().expand(seed, plan, candidate_index)
            return draft.model_copy(update={"price": "가격 정보는 미제공"})

    payload = json.loads((FIXTURES / "food_minimal.json").read_text(encoding="utf-8"))
    engine = ConceptPortfolioEngine(gateway=ProviderGateway(provider=PlaceholderProvider()))
    result = run(engine.run_full(payload, auto_confirm_hypotheses=True))
    assert result.handoff is None
    assert result.downstreamReadiness == "PENDING_HYPOTHESIS_CONFIRMATION"
    assert any(item.action == "HYPOTHESIS_SEMANTIC_UNRESOLVED" for item in result.trace)
