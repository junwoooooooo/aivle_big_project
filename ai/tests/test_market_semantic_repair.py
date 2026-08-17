from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest

from app.research import serialize
from app.research.semantic_relevance import align_scorecard, filter_market_material

ROOT = Path(__file__).resolve().parents[1] / "app" / "research" / "research2"
for part in (ROOT, ROOT / "blocks"):
    if str(part) not in sys.path:
        sys.path.insert(0, str(part))
import a_desk  # noqa: E402


def _observed(card_id: str, slot: str, subject: str, metric: str = "가격") -> dict:
    canvas = {"PRICE": "수익원", "TAM": "고객 세그먼트"}.get(slot, slot)
    return {"카드_id": card_id, "종류": "관측", "칸": canvas, "_claim_type": slot,
            "주제": subject,
            "계량": metric, "인용": subject, "등급": "확정", "값": 9900}


def test_exercise_concept_rejects_food_retail_and_invalidates_derived_market() -> None:
    concept = {"name": "동네 운동 파트너 매칭", "problem": "운동 부족",
               "target": "직장인과 지역 주민", "solution": "운동 파트너 매칭",
               "_계열": {"계열": "B"}}
    cards = [
        _observed("C-exercise", "PRICE", "피트니스 앱 월 구독료"),
        _observed("C-lunch", "PRICE", "편의점 도시락 판매가"),
        _observed("C-delivery", "PRICE", "배달비"),
        _observed("C-retail", "TAM", "백화점 전체 매출", "거래액"),
        _observed("C-generic-transaction", "TAM", "대한민국 전체 시장", "거래액"),
        _observed("C-pop", "TAM", "대한민국 직장인 수", "인구"),
        {"카드_id": "C-CALC-TAM", "종류": "계산", "등급": "추정", "값": 1,
         "재료_카드_id": ["C-retail"]},
    ]
    verdict = {"시장_추정": {"TAM_추정": {"값": 1, "근거": [{"fact_id": "retail"}]}}}

    safe_verdict, safe_cards, report = filter_market_material(concept, verdict, {"카드": cards})

    ids = {card["카드_id"] for card in safe_cards["카드"]}
    assert {"C-exercise", "C-pop"}.issubset(ids)
    assert {"C-lunch", "C-delivery", "C-retail", "C-generic-transaction",
            "C-CALC-TAM"}.isdisjoint(ids)
    assert safe_verdict["시장_추정"]["TAM_추정"]["값"] is None
    assert {item["reason"] for item in report["rejected"]} >= {
        "CONTRADICTORY_BUSINESS_CATEGORY", "DERIVED_FROM_REJECTED_EVIDENCE"}
    assert "MARKET_STRATEGY_DENOMINATOR_MISMATCH" in {
        item["reason"] for item in report["rejected"]}


def test_price_gate_keeps_semantically_linked_b2b_and_commerce_prices() -> None:
    operations = {"name": "매장 운영 자동화 SaaS", "target": "소상공인 매장 운영자",
                  "solution": "운영 자동화 소프트웨어"}
    commerce = {"name": "지역 상품 거래 마켓플레이스", "target": "판매자와 소비자",
                "solution": "커머스 거래 중개"}

    _, ops_cards, _ = filter_market_material(
        operations, {}, {"카드": [_observed("C-saas", "PRICE", "매장 운영 SaaS 월 요금")]})
    _, commerce_cards, _ = filter_market_material(
        commerce, {}, {"카드": [_observed("C-fee", "PRICE", "마켓플레이스 거래 수수료")]})

    assert [card["카드_id"] for card in ops_cards["카드"]] == ["C-saas"]
    assert [card["카드_id"] for card in commerce_cards["카드"]] == ["C-fee"]
    assert serialize._price(ops_cards["카드"])["base"] == 9_900
    assert serialize._price(commerce_cards["카드"])["evidenceIds"] == ["C-fee"]


def test_scorecard_cannot_claim_rejected_price_or_market_size_is_filled() -> None:
    score = {"과목": {"1_시장크기": {"n": 2, "값": None, "상태": "채워짐"},
                      "4_가격": {"n": 3, "상태": "채워짐"}}}

    aligned = align_scorecard(score, {"카드": []})

    assert aligned["과목"]["1_시장크기"]["상태"] == "미확보"
    assert aligned["과목"]["4_가격"] == {"n": 0, "상태": "미확보"}


@pytest.mark.parametrize(("number_raw", "unit_raw", "expected"), [
    ("272조 398", "억원", 272_039_800_000_000),
    ("211조 1,448", "억원", 211_144_800_000_000),
    ("69조 2,799", "억원", 69_279_900_000_000),
])
def test_split_compound_korean_money_uses_unit_field_scale(
        number_raw: str, unit_raw: str, expected: int) -> None:
    rules = json.loads((ROOT / "rules" / "units.v1.json").read_text(encoding="utf-8"))

    value, unit, _ = a_desk.parse_number(number_raw, unit_raw, rules)

    assert value == expected
    assert isinstance(value, int)
    assert unit == "원"
