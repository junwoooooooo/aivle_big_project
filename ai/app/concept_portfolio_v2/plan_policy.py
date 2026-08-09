"""system metadata integrity와 실제 Plan content/LOCK/intent 검사를 분리한다."""

from __future__ import annotations

import re

from .anchor_policy import assess_anchor
from .models import DesignSpaceAnalysis, ExplorationBreadth, PortfolioPlan


SUPPLY_INTENT = {"식재료", "공급", "소분", "배송", "구매", "판매", "연결", "장터", "구독"}


def _tokens(value: str) -> set[str]:
    return {item for item in re.findall(r"[0-9a-z가-힣]+", value.casefold()) if len(item) >= 2}


def assess_plan_content(plan: PortfolioPlan, analysis: DesignSpaceAnalysis) -> tuple[str, list[str]]:
    text = " ".join([
        plan.title, plan.oneLineConcept, plan.coreMechanism, plan.valueDelivery,
        plan.operatingApproach, plan.partnerApproach, plan.transactionApproach,
        plan.commercialApproach, plan.fulfillmentApproach,
    ])
    reasons: list[str] = []
    anchor_decision, _ = assess_anchor(
        analysis.opportunityAnchor, text, text, text, analysis.explorationBreadth)
    if anchor_decision == "FAIL":
        reasons.append("actual Plan content가 opportunity/intent anchor에서 이탈했습니다.")
    intent_tokens = _tokens(analysis.opportunityAnchor.intentCore)
    plan_tokens = _tokens(text)
    overlap = intent_tokens & plan_tokens
    if analysis.explorationBreadth == ExplorationBreadth.REFINE:
        original_supply = bool(intent_tokens & SUPPLY_INTENT)
        plan_supply = bool(plan_tokens & SUPPLY_INTENT)
        if (original_supply and not plan_supply) or len(overlap) < 2:
            reasons.append("REFINE Plan이 원 아이디어의 핵심 solution intent를 유지하지 못했습니다.")
    elif analysis.explorationBreadth == ExplorationBreadth.AS_IS:
        if intent_tokens & SUPPLY_INTENT and not plan_tokens & SUPPLY_INTENT:
            reasons.append("AS_IS Plan에서 원 사업의 공급·연결 intent가 사라졌습니다.")
        elif not overlap:
            reasons.append("AS_IS Plan이 원 사업 intent/commitment를 충분히 유지하지 못했습니다.")

    locks = analysis.explicitBusinessLocks
    commercial = plan.commercialApproach.casefold()
    interaction = plan.customerInteraction.casefold()
    if ("price" in locks or "revenueModel" in locks) and any(word in commercial for word in ("완전 무료", "무료 광고", "광고형 무료")):
        locked_text = f"{locks.get('price', '')} {locks.get('revenueModel', '')}"
        if "무료" not in locked_text:
            reasons.append("LOCK된 가격/수익모델과 무료 광고형 Plan이 충돌합니다.")
    channel = locks.get("channels")
    if channel and "모바일" in channel and any(word in interaction for word in ("오프라인 전용", "전화 전용", "방문 전용")):
        reasons.append("LOCK된 채널과 Plan 고객 접점이 충돌합니다.")
    differentiator = locks.get("differentiators")
    if differentiator and not (_tokens(differentiator) & plan_tokens):
        reasons.append("LOCK된 차별점이 실제 Plan content에 반영되지 않았습니다.")
    return ("PASS" if not reasons else "FAIL"), reasons
