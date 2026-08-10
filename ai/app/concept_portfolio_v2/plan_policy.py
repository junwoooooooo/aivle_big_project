"""명시적 Plan thesis와 generic OpportunityKernel/LOCK 검증."""

from __future__ import annotations

import re

from .anchor_policy import assess_anchor
from .models import DesignSpaceAnalysis, PortfolioPlan


def _tokens(value: str) -> set[str]:
    return {item for item in re.findall(r"[0-9a-z가-힣]+", value.casefold()) if len(item) >= 2}


def assess_plan_content(plan: PortfolioPlan, analysis: DesignSpaceAnalysis) -> tuple[str, list[str]]:
    reasons: list[str] = []
    anchor_decision, _ = assess_anchor(
        analysis.opportunityKernel, plan.problemFocus, plan.targetSegment,
        " ".join((plan.valueProposition, plan.offerThesis, plan.solutionThesis)),
        analysis.explorationBreadth)
    if anchor_decision == "OUT_OF_SCOPE":
        reasons.append("명시적 Plan thesis가 OpportunityKernel 범위를 벗어났습니다.")

    locks = analysis.explicitBusinessLocks
    commercial = f"{plan.commercialApproach} {plan.transactionApproach}".casefold()
    interaction = plan.customerInteraction.casefold()
    locked_commercial = f"{locks.get('price', '')} {locks.get('revenueModel', '')}".casefold()
    if locked_commercial.strip() and "무료" not in locked_commercial and any(
            phrase in commercial for phrase in ("완전 무료", "광고형 무료", "무료 전용")):
        reasons.append("LOCK된 가격/수익모델과 무료 전용 Plan이 충돌합니다.")
    channel = str(locks.get("channels") or "").casefold()
    if channel and any(word in channel for word in ("앱", "웹", "온라인", "api")) and any(
            phrase in interaction for phrase in ("오프라인 전용", "방문 전용", "전화 전용")):
        reasons.append("LOCK된 디지털 채널과 오프라인 전용 Plan이 충돌합니다.")
    if channel and any(word in channel for word in ("오프라인", "방문", "현장")) and any(
            phrase in interaction for phrase in ("온라인 전용", "앱 전용", "웹 전용")):
        reasons.append("LOCK된 현장 채널과 디지털 전용 Plan이 충돌합니다.")
    differentiator = str(locks.get("differentiators") or "")
    explicit_thesis = " ".join((plan.valueProposition, plan.offerThesis, plan.solutionThesis,
                                 " ".join(plan.differentiatingMechanics)))
    if differentiator and not (_tokens(differentiator) & _tokens(explicit_thesis)):
        reasons.append("LOCK된 차별화 thesis가 Plan의 명시적 thesis에 반영되지 않았습니다.")
    if reasons:
        return "FAIL", reasons
    if anchor_decision == "AMBIGUOUS":
        return "AMBIGUOUS", ["Opportunity 관계에 semantic 판정이 필요합니다."]
    return "PASS", []
