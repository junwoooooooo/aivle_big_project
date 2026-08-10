# -*- coding: utf-8 -*-
"""BM 파이프라인 — 노트북 셀 14. **모델 호출은 정확히 1회다.**"""
from __future__ import annotations

from typing import Any

from openai import AsyncOpenAI

from .analyze import run_bm_analysis
from .contracts import BMAnalysisInput
from .finalize import finalize_bm_analysis
from .normalize import resolve_bm_input


async def run_bm_pipeline_flow(
    bm_input: BMAnalysisInput,
    *,
    client: AsyncOpenAI | None = None,
    model: str | None = None,
) -> dict[str, Any]:
    resolved = resolve_bm_input(bm_input)

    # ResolvedBMInput에는 법률 정보를 넣지 않으므로 핵심 모델은 시장 데이터만 사용한다.
    bm_analysis = await run_bm_analysis(
        resolved=resolved,
        client=client,
        model=model,
    )

    final_result = finalize_bm_analysis(
        bm_analysis=bm_analysis,
        resolved=resolved,
        legal_context=bm_input.legal_context,
    )

    return {
        "bm_analysis": bm_analysis,
        "final_result": final_result,
    }
