# -*- coding: utf-8 -*-
"""BM 핵심 판정 — 노트북 셀 10 의 뒷부분. **법률 데이터는 입력하지 않는다.**

노트북 셀 4(환경설정)에서 바뀐 것은 **클라이언트를 만드는 방법 하나뿐**이다:
`getpass` 대화형 입력이 없다. 서버에는 사람이 없고, 키가 없으면 **가짜 값을 만들지 않고
실패한다**(`AI_FIXTURE_MODE=false` 와 같은 결정 — Mock 이 없다).
"""
from __future__ import annotations

import json
import os
from typing import Any

from openai import AsyncOpenAI
from pydantic import ValidationError

from .contracts import (
    BMAnalysisResult,
    CanvasStatus,
    MarketJoinData,
    ResolvedBMInput,
)
from .prompt import ALLOWED_CANVAS_SOURCE_LABELS, BM_ANALYSIS_PROMPT


def default_model() -> str:
    """`BM_MODEL` → `AI_MODEL` 순. 노트북 기본값(`gpt-5.6-terra`)은 쓰지 않는다 —
    제품의 모델 선택은 `.env` 가 정한다."""
    return (os.getenv("BM_MODEL") or os.getenv("AI_MODEL") or "").strip()


def get_client() -> AsyncOpenAI:
    """⚠ `max_retries=0` 은 노트북 그대로다. **재시도를 여기서 하지 않는다** —
    이 호출은 260초짜리 여정 안에 있고, 조용한 재시도는 예산을 배로 먹는다."""
    key = (os.getenv("OPENAI_API_KEY") or "").strip()
    if not key:
        raise RuntimeError("OPENAI_API_KEY가 필요합니다.")
    base_url = (os.getenv("OPENAI_BASE_URL") or "").strip()
    return AsyncOpenAI(api_key=key, max_retries=0,
                       **({"base_url": base_url} if base_url else {}))


def validate_canvas_source_labels(result: BMAnalysisResult) -> BMAnalysisResult:
    """허용된 입력 출처만 남기고 출처 없는 Canvas 내용은 제거한다."""
    validated_canvas = []
    for item in result.canvas:
        labels = list(dict.fromkeys(
            label
            for label in item.source_labels
            if label in ALLOWED_CANVAS_SOURCE_LABELS
        ))
        update: dict[str, Any] = {"source_labels": labels}
        if item.content and not labels:
            update.update(
                content=[],
                market_evidence_ids=[],
                status=CanvasStatus.UNVERIFIED,
                reason="허용된 입력 출처 라벨이 없어 Canvas 내용을 제거했습니다.",
                missing_evidence=list(dict.fromkeys([
                    *item.missing_evidence,
                    "Canvas 내용의 입력 출처 라벨",
                ])),
            )
        validated_canvas.append(item.model_copy(update=update))
    return result.model_copy(update={"canvas": validated_canvas})


def validate_market_evidence_ids(
    result: BMAnalysisResult,
    market_data: MarketJoinData,
) -> BMAnalysisResult:
    """원장에 없는 근거 id 를 지운다 — **환각 인용 방어선**.

    ⚠ 키는 `id` 다. `card_id` 로 만들어 넣으면 `allowed_ids` 가 비어
      **모든** 인용이 조용히 사라진다(판 ㉜ 실측).
    """
    allowed_ids = {
        str(item["id"])
        for item in market_data.evidence_list
        if item.get("id") is not None
    }

    validated_canvas = []
    for item in result.canvas:
        validated_ids = [
            evidence_id
            for evidence_id in item.market_evidence_ids
            if evidence_id in allowed_ids
        ]
        validated_canvas.append(
            item.model_copy(
                update={"market_evidence_ids": validated_ids}
            )
        )

    return result.model_copy(update={"canvas": validated_canvas})


async def run_bm_analysis(
    *,
    resolved: ResolvedBMInput,
    client: AsyncOpenAI | None = None,
    model: str | None = None,
    diagnostic_context: dict[str, str] | None = None,
) -> BMAnalysisResult:
    api = client or get_client()
    payload = resolved.model_dump(mode="json")

    try:
        response = await api.responses.parse(
            model=model or default_model(),
            input=[
                {"role": "system", "content": BM_ANALYSIS_PROMPT},
                {
                    "role": "user",
                    "content": json.dumps(payload, ensure_ascii=False),
                },
            ],
            text_format=BMAnalysisResult,
        )
    except ValidationError as failure:
        from .diagnostics import log_bm_validation_failure

        log_bm_validation_failure(failure, diagnostic_context)
        raise

    if response.output_parsed is None:
        raise RuntimeError("BM 분석 결과를 구조화된 형식으로 받지 못했습니다.")

    result = response.output_parsed
    if result.concept_id != resolved.concept_id:
        raise ValueError("BM 분석 결과의 concept_id가 입력과 다릅니다.")

    result = validate_market_evidence_ids(
        result,
        resolved.market_join_data,
    )
    result = validate_canvas_source_labels(result)
    return result
