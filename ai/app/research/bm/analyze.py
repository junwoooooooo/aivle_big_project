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


#: 계획 5칸이 라벨을 잃었을 때 **기계가 대신 붙이는** 출처. 지어내는 것이 아니라
#: 「이 칸의 재료가 실제로 어느 입력에서 왔는지」를 적는 것이다 —
#: 비용 구조는 `execution_constraints`(사용자가 입력한 예산·기간), 나머지 넷은
#: `concept_snapshot`(컨셉의 `_bm_plan`·사용자가 BM 앞 화면에서 채운 칸)이 유일한 원천이다
#: (`research2/service/bm_adapter.py::PLAN_FIELDS` · `execution_constraints_of`).
_PLAN_FALLBACK_LABEL = {"COST_STRUCTURE": "execution_constraints"}
_PLAN_DEFAULT_LABEL = "concept_snapshot"


def validate_canvas_source_labels(result: BMAnalysisResult) -> BMAnalysisResult:
    """허용된 입력 출처만 남긴다. 출처를 못 붙인 **관측** 칸의 내용은 제거한다.

    ⚠ **계획 5칸은 지우지 않는다**(2026-08-15). 실측: 성공 3회 중 2회 **사용자가 쓴 계획
      문장이 통째로 사라졌다**(`expected.md:8119`). 모델이 라벨을 안 붙였다는 것은 모델의
      실수이지 「그 문장이 근거 없다」는 뜻이 아니다 — 계획 5칸의 내용은 **사용자가 직접 쓴
      것**이고 출처가 무엇인지 우리가 이미 안다. 그래서 기계가 라벨을 붙여 살린다.

      관측 4칸은 지금대로 지운다. 거기서 라벨을 못 붙였다는 것은 **실제로 시장 근거가
      없다**는 뜻이고, 그 빈칸이 곧 이 단계의 산출이다(「아직 당신 말뿐인 칸」).
    """
    from ...validation.gate import PLANNED_CELLS      # noqa: PLC0415 — 순환 없음(gate 는 잎)

    validated_canvas = []
    for item in result.canvas:
        labels = list(dict.fromkeys(
            label
            for label in item.source_labels
            if label in ALLOWED_CANVAS_SOURCE_LABELS
        ))
        update: dict[str, Any] = {"source_labels": labels}
        if item.content and not labels:
            if str(item.canvas_cell) in PLANNED_CELLS:
                update["source_labels"] = [
                    _PLAN_FALLBACK_LABEL.get(str(item.canvas_cell), _PLAN_DEFAULT_LABEL)]
            else:
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


#: 제품 정책의 온도. `app/providers/structured.py` 가 모든 구조화 호출에 쓰는 값과 같다 —
#: **이 모듈만 밖에 있었다.** 온도를 안 주면 SDK 기본값으로 돌아 같은 입력에 매번 다른 답이
#: 나오고, 9칸 스키마를 못 맞춰 통째로 실패하는 일이 잦아진다(실측: 시도 6회 중 3회).
_TEMPERATURE = 0.1


#: 9칸을 못 맞췄을 때 **한 번만** 더 묻는다. 조용한 재시도가 아니라 «빠진 칸을 알려 주는»
#: 재요청이라, 두 번째도 실패하면 그때는 실패로 둔다(fail-closed 유지 — 빈 칸을 지어내지
#: 않는다). 지금은 이 실패가 TaskRun 재시도로 나가면서 **한 판을 통째로 두 번 태운다.**
_REASK_ONCE = (
    "직전 응답이 Business Model Canvas 9개 칸을 각각 정확히 한 번씩 담지 못했다. "
    "9개 칸(CUSTOMER_SEGMENTS, VALUE_PROPOSITIONS, CHANNELS, CUSTOMER_RELATIONSHIPS, "
    "REVENUE_STREAMS, KEY_ACTIVITIES, KEY_RESOURCES, KEY_PARTNERS, COST_STRUCTURE)을 "
    "빠짐없이, 중복 없이 담아 같은 형식으로 다시 낸다. "
    "입력에 없는 내용을 새로 만들지 않는다 — 근거가 없는 칸은 content=[]로 두고 "
    "status를 UNVERIFIED 또는 PLAN으로 둔다."
)


#: 추론 모델(gpt-5.x 계열)에 쓸 **생각의 양**. `BM_REASONING_EFFORT` 로 준다.
#:
#: ⚠ **온도와 «둘 중 하나»다.** 2026-08-15 실측(다른 판): `effort=none` 이면 온도를 되찾고,
#:   `low` 이상이면 온도를 보내는 순간 400 이다. 그래서 이 값을 주면 온도를 안 보낸다
#:   (`none` 은 예외 — 추론이 꺼지므로 옛 동작 그대로다).
#:
#: ⚠ **왜 기본이 비어 있나.** `gpt-4o-mini` 같은 옛 모델은 이 인자를 아예 안 받는다.
#:   손잡이를 기본으로 켜면 **지금 도는 것이 400 으로 죽는다.** 모델을 바꾸는 사람이 같이 준다.
#:
#: ⚠ **읽는 시점을 `default_model()` 과 맞춘다**(2026-08-15 감사로 잡음). 모듈 로드 때 한 번만
#:   읽으면 «모델은 바뀌는데 손잡이는 안 바뀌는» 반쪽 상태가 생긴다 — 둘은 짝이라 그러면
#:   온도/추론 조합이 어긋나 400 이 난다. 한쪽만 상수로 두지 않는다.
def _effort() -> str:
    return (os.getenv("BM_REASONING_EFFORT") or "").strip()

#: 온도를 못 받는 모델을 **실행 중에 배운다.** 목록을 손으로 관리하지 않는다 —
#: `app/providers/structured.py` 의 `_MODEL_MODE` 와 같은 방식이고, 같은 이유다(목록은 낡는다).
#: 프로세스 안에서만 산다. BM 은 그 모듈을 안 타므로(자기 `AsyncOpenAI` 를 만든다) 여기 둔다.
_NO_TEMPERATURE: set[str] = set()

#: 400 이 **샘플링 인자 때문**인가. 다른 400(스키마·길이)까지 재시도하면 엉뚱한 데 돈을 쓴다.
_SAMPLING_PARAMS = ("temperature", "reasoning_effort")


def _sampling_rejected(error: Exception) -> bool:
    text = str(error).lower()
    return "400" in text and any(name in text for name in _SAMPLING_PARAMS)


def _knobs(model: str) -> dict[str, Any]:
    """이 모델에 보낼 손잡이. 온도와 `reasoning_effort` 는 위 ⚠ 대로 갈린다."""
    out: dict[str, Any] = {}
    effort = _effort()
    if effort:
        # ⚠ `responses.parse` 는 **`reasoning={"effort": …}`** 로 받는다.
        #   `reasoning_effort=` 는 chat/completions 쪽 이름이라 여기서는 `TypeError` 다
        #   (2026-08-15 실측: `AsyncResponses.parse() got an unexpected keyword argument`).
        #   `providers/structured.py` 는 HTTP 를 직접 쳐서 평평한 이름을 쓴다 — **두 경로가 다르다.**
        out["reasoning"] = {"effort": effort}
    if not effort or effort == "none":
        if model not in _NO_TEMPERATURE:
            out["temperature"] = _TEMPERATURE
    return out


async def _parse_once(api: AsyncOpenAI, model: str, payload: dict,
                      reask: str | None = None) -> BMAnalysisResult | None:
    """모델 호출 1회. 9칸을 못 맞추면 SDK 가 검증에서 터지므로 그대로 올라간다."""
    messages: list[dict[str, Any]] = [
        {"role": "system", "content": BM_ANALYSIS_PROMPT},
        {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
    ]
    if reask:
        messages.append({"role": "user", "content": reask})

    async def _call() -> BMAnalysisResult | None:
        response = await api.responses.parse(
            model=model, input=messages, text_format=BMAnalysisResult, **_knobs(model))
        return response.output_parsed

    try:
        return await _call()
    except Exception as error:                      # noqa: BLE001 — 아래에서 갈래를 가른다
        # **온도를 못 받는 모델이었다.** 배우고 한 번만 다시 보낸다. 그 밖의 400 은
        # 그대로 올린다 — 9칸 미충족 재요청(`_REASK_ONCE`)이 그 위에서 따로 돈다.
        if not _sampling_rejected(error) or model in _NO_TEMPERATURE:
            raise
        _NO_TEMPERATURE.add(model)
        return await _call()


async def run_bm_analysis(
    *,
    resolved: ResolvedBMInput,
    client: AsyncOpenAI | None = None,
    model: str | None = None,
    diagnostic_context: dict[str, str] | None = None,
) -> BMAnalysisResult:
    api = client or get_client()
    payload = resolved.model_dump(mode="json")
    name = model or default_model()

    try:
        parsed = await _parse_once(api, name, payload)
    except Exception:                               # noqa: BLE001 — 9칸 미충족이 여기로 온다
        # **한 번만** 더 묻는다. 이것도 실패하면 원래 예외가 아니라 두 번째 것이 올라가는데,
        # 같은 종류의 사유라 진단이 흐려지지 않는다.
        try:
            parsed = await _parse_once(api, name, payload, reask=_REASK_ONCE)
        except ValidationError as failure:
            # 재요청까지 9칸을 못 맞췄다 — 비밀 없는 진단을 남긴다(main 에서 옴).
            from .diagnostics import log_bm_validation_failure   # noqa: PLC0415

            log_bm_validation_failure(failure, diagnostic_context)
            raise

    # ⚠ 여기서는 **다시 묻지 않는다.** `None` 은 「스키마를 못 맞췄다」가 아니라
    #   「구조화 응답 자체가 안 왔다」이고, 그건 재요청으로 고쳐지는 종류가 아니다.
    #   괜히 한 번 더 부르면 고장 난 provider 에 돈만 두 배로 나간다.
    if parsed is None:
        raise RuntimeError("BM 분석 결과를 구조화된 형식으로 받지 못했습니다.")

    result = parsed
    if result.concept_id != resolved.concept_id:
        raise ValueError("BM 분석 결과의 concept_id가 입력과 다릅니다.")

    result = validate_market_evidence_ids(
        result,
        resolved.market_join_data,
    )
    result = validate_canvas_source_labels(result)
    return result
