# -*- coding: utf-8 -*-
"""입력 정규화 — 노트북 셀 8·16.

셀 18(전역변수 `market_input_data`/`market_agent_output` 탐색)은 **옮기지 않았다.**
노트북에서 앞 셀의 산출을 주워 오던 장치이고, 서버에서는 오케스트레이터가
`create_bm_analysis_input(...)` 에 값을 **명시적으로 건넨다**.
"""
from __future__ import annotations

from typing import Any

from pydantic import BaseModel

from .contracts import (
    BMAnalysisInput,
    LegalContext,
    MarketJoinData,
    ResolvedBMInput,
)


def resolve_bm_input(source: BMAnalysisInput) -> ResolvedBMInput:
    concept = source.market_join_data.concept_snapshot
    return ResolvedBMInput(
        concept_id=source.concept_id,
        concept_name=concept.concept_name or source.concept_id,
        target_customer=concept.target_customer or "",
        problem=concept.problem or "",
        solution=concept.solution or "",
        core_value=concept.core_value or "",
        differentiation=concept.differentiation,
        revenue_model=concept.revenue_model,
        market_join_data=source.market_join_data,
        execution_constraints=source.execution_constraints,
    )


def create_bm_analysis_input(
    *,
    market_data: MarketJoinData | dict[str, Any],
    legal_data: LegalContext | dict[str, Any] | None = None,
    execution_constraints: dict[str, Any] | None = None,
) -> BMAnalysisInput:
    """앞 단계의 실제 출력으로 BMAnalysisInput을 생성한다."""
    if isinstance(market_data, MarketJoinData):
        market_join_data = market_data
    else:
        if isinstance(market_data, BaseModel):
            market_data = market_data.model_dump(mode="python")
        market_join_data = MarketJoinData.model_validate(market_data)

    if legal_data is None:
        legal_context = None
    elif isinstance(legal_data, LegalContext):
        legal_context = legal_data
    else:
        if isinstance(legal_data, BaseModel):
            legal_data = legal_data.model_dump(mode="python")
        legal_context = LegalContext.model_validate(legal_data)

    return BMAnalysisInput(
        concept_id=market_join_data.concept_id,
        market_join_data=market_join_data,
        legal_context=legal_context,
        execution_constraints=dict(execution_constraints or {}),
    )
