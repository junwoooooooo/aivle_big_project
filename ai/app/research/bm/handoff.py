# -*- coding: utf-8 -*-
"""재무분석 Handoff — 노트북 셀 26. 선택 단계이고 **LLM 0회.**

`save_financial_handoff`(파일로 떨어뜨리기)는 **옮기지 않았다** — 노트북에서 결과를
손에 쥐려던 장치다. 서버는 값을 돌려주고 저장은 호출자가 정한다.
"""
from __future__ import annotations

from .contracts import (
    BMDecision,
    BMFinalResult,
    BMFinancialHandoff,
    ResolvedBMInput,
)


def get_required_financial_inputs(revenue_model: str | None) -> list[str]:
    model = (revenue_model or "").lower()

    if any(word in model for word in ["판매", "제품", "커머스"]):
        return ["price_base", "unit_cost"]
    if any(word in model for word in ["구독", "saas", "subscription"]):
        return ["price_base"]
    if any(word in model for word in ["수수료", "중개", "commission"]):
        return ["price_base"]
    if "광고" in model:
        return []
    return []


def build_financial_handoff(
    *,
    final_result: BMFinalResult,
    resolved: ResolvedBMInput,
) -> BMFinancialHandoff:
    """BM 판정 뒤 선택적으로 다음 재무 단계의 입력을 만든다."""
    if final_result.concept_id != resolved.concept_id:
        raise ValueError("최종 BM 결과와 정규화 입력의 concept_id가 다릅니다.")

    price = resolved.market_join_data.price_analysis
    market_size = resolved.market_join_data.market_size
    growth = resolved.market_join_data.growth_rate
    available_numeric = {
        "price_base": price.price_base,
        "unit_cost": None,
    }
    required_numeric = get_required_financial_inputs(resolved.revenue_model)
    missing = list(dict.fromkeys(
        name for name in required_numeric if available_numeric.get(name) is None
    ))

    if final_result.decision == BMDecision.BLOCKED:
        handoff_status = "BLOCKED"
    elif final_result.decision == BMDecision.REVISION_REQUIRED or missing:
        handoff_status = "PARTIAL"
    else:
        handoff_status = "READY"

    return BMFinancialHandoff(
        concept_id=resolved.concept_id,
        revenue_model=resolved.revenue_model,
        price_min=price.price_min,
        price_base=price.price_base,
        price_max=price.price_max,
        tam=market_size.tam,
        sam=market_size.sam,
        som=market_size.som,
        market_growth_rate=growth.value,
        expected_revenue=None,
        unit_cost=None,
        fixed_cost_items=[],
        variable_cost_items=[],
        missing_financial_inputs=missing,
        handoff_status=handoff_status,
    )
