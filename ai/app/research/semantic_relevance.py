"""Deterministic evidence relevance boundary for Product Market results.

The collector remains responsible for finding evidence.  This boundary only prevents an
observed card whose business category contradicts the immutable concept from becoming a
representative Market/BM fact.  Rejected inputs also invalidate derived figures that depend
on them; no replacement value is invented.
"""
from __future__ import annotations

import copy
import re

_THEMES = {
    "exercise": ("운동", "헬스", "피트니스", "체육", "스포츠", "걷기", "활동량"),
    "food": ("도시락", "배달비", "배달 음식", "식품", "급식", "외식", "편의점", "식사"),
    "retail": ("백화점", "아울렛", "홈쇼핑", "온라인쇼핑", "온라인 쇼핑", "유통 매출"),
    "beauty": ("미용", "네일", "뷰티", "헤어", "살롱"),
    "operations": ("운영 자동화", "업무 자동화", "saas", "소프트웨어", "erp", "pos"),
    "commerce": ("마켓플레이스", "커머스", "상품 판매", "제품 판매", "거래 수수료", "gmv"),
    "subscription": ("구독", "멤버십", "membership", "subscription"),
}
_GENERIC = {
    "대한민국", "한국", "시장", "규모", "가격", "요금", "매출", "거래액", "서비스", "사업",
    "사용자", "고객", "대상", "지역", "전국", "월", "연", "관련", "기준", "통계", "현황",
}
_TOKEN = re.compile(r"[가-힣A-Za-z0-9]+")
_FIGURES = {
    "TAM_추정": "C-CALC-TAM",
    "SAM_추정": "C-CALC-SAM",
    "성장률_추정": "C-CALC-성장률",
}


def claim_type(card: dict) -> str:
    return str(card.get("_claim_type") or card.get("칸") or "")


def _core_text(concept: dict) -> str:
    values = [concept.get(key) for key in ("name", "problem", "target", "solution", "region")]
    fine = concept.get("_다듬기5") or {}
    values.append(fine.get("3_핵심_가치"))
    values.extend(_leaf_text(concept.get(key)) for key in ("_hypotheses_v2", "_bm_plan"))
    return " ".join(str(value) for value in values if value)


def _leaf_text(value) -> str:
    if isinstance(value, dict):
        return " ".join(_leaf_text(item) for item in value.values())
    if isinstance(value, (list, tuple)):
        return " ".join(_leaf_text(item) for item in value)
    return str(value or "")


def _themes(text: str) -> set[str]:
    lowered = (text or "").lower()
    return {name for name, words in _THEMES.items() if any(word in lowered for word in words)}


def _tokens(text: str) -> set[str]:
    return {token.lower() for token in _TOKEN.findall(text or "")
            if len(token) >= 2 and token not in _GENERIC}


def _decision(card: dict, concept_text: str, series: str) -> tuple[bool, str]:
    if card.get("종류") != "관측":
        return True, "derived"
    evidence_text = " ".join(str(card.get(key) or "")
                             for key in ("주제", "계량", "인용"))
    metric = str(card.get("계량") or "").lower()
    market_size_slot = claim_type(card) in {
        "TAM", "SAM", "MARKET_SIZE", "시장규모", "고객 세그먼트",
    }
    if market_size_slot and series in {"A", "B"} \
            and any(word in metric for word in ("거래액", "gmv", "전체 매출", "총매출")):
        return False, "MARKET_STRATEGY_DENOMINATOR_MISMATCH"
    if market_size_slot and series == "C" \
            and any(word in metric for word in ("인구", "사용자 수", "사업체 수", "사업장 수")):
        return False, "MARKET_STRATEGY_DENOMINATOR_MISMATCH"
    concept_themes, evidence_themes = _themes(concept_text), _themes(evidence_text)
    if concept_themes and evidence_themes and concept_themes.isdisjoint(evidence_themes):
        return False, "CONTRADICTORY_BUSINESS_CATEGORY"

    # 표시가격은 사업 카테고리나 명시된 대상과 연결되어야 한다. 단순히 원 단위라는
    # 이유로 배달비·도시락 가격을 다른 서비스의 대표 단가로 올리지 않는다.
    if claim_type(card) == "PRICE":
        overlap = _tokens(concept_text) & _tokens(evidence_text)
        if not (concept_themes & evidence_themes) and not overlap:
            return False, "PRICE_WITHOUT_SEMANTIC_LINK"
    return True, "RELEVANT_OR_NEUTRAL_DENOMINATOR"


def filter_market_material(concept: dict, verdict: dict, cards_doc: dict) -> tuple[dict, dict, dict]:
    """Return sanitized copies of verdict/cards and an auditable rejection report."""
    concept_text = _core_text(concept)
    series = str(((concept.get("_계열") or {}).get("계열") or "")).upper()
    cards = list((cards_doc or {}).get("카드") or [])
    kept, rejected = [], []
    for card in cards:
        if card.get("종류") != "관측":
            continue
        accepted, reason = _decision(card, concept_text, series)
        if accepted:
            kept.append(card)
        else:
            rejected.append({"id": card.get("카드_id"), "reason": reason,
                             "slot": claim_type(card), "subject": card.get("주제")})

    kept_ids = {str(card.get("카드_id")) for card in kept}
    for card in cards:
        if card.get("종류") == "관측":
            continue
        materials = {str(value) for value in (card.get("재료_카드_id") or []) if value}
        if materials and not materials.issubset(kept_ids):
            rejected.append({"id": card.get("카드_id"), "reason": "DERIVED_FROM_REJECTED_EVIDENCE"})
            continue
        kept.append(card)
        kept_ids.add(str(card.get("카드_id")))

    safe_verdict = copy.deepcopy(verdict)
    estimates = safe_verdict.get("시장_추정") or {}
    for key, card_id in _FIGURES.items():
        if card_id not in kept_ids and key in estimates:
            estimates[key] = {"값": None, "가정": ["관련성 게이트에서 계산 재료가 제외됨"]}

    safe_cards = {**(cards_doc or {}), "카드": kept}
    report = {"policy": "market-evidence-relevance-v1", "series": series,
              "conceptThemes": sorted(_themes(concept_text)),
              "accepted": [card.get("카드_id") for card in kept], "rejected": rejected}
    return safe_verdict, safe_cards, report


def align_scorecard(score: dict, cards_doc: dict) -> dict:
    """Keep user-visible coverage claims monotone with the post-gate evidence set."""
    out = copy.deepcopy(score)
    cards = list((cards_doc or {}).get("카드") or [])
    subjects = out.get("과목") or {}
    price = [card for card in cards if card.get("종류") == "관측" and claim_type(card) == "PRICE"]
    price_row = subjects.get("4_가격")
    if isinstance(price_row, dict):
        previous = int(price_row.get("n") or 0)
        price_row["n"] = len(price)
        if not price:
            price_row["상태"] = "미확보"
        elif len(price) < previous and price_row.get("상태") == "채워짐":
            price_row["상태"] = "부분"
    market_row = subjects.get("1_시장크기")
    if isinstance(market_row, dict) and market_row.get("값") is None:
        market_row["상태"] = "미확보"
    return out
