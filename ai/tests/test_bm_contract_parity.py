# -*- coding: utf-8 -*-
"""두 `MarketJoinData` 가 갈라지지 않는지 단언한다.

    보내는 쪽  `ai/app/research/research2/service/bm_adapter.py`   (엔진 산출 → 계약)
    받는 쪽    `ai/app/research/bm/contracts.py`                   (계약 → BM 분석)

**같은 계약의 사본이 둘**이다. 사본을 하나로 합치지 않은 이유는 research2 가 판 ㉝ 이식
그대로 **동결**이고 BM 층에서 import 하면 유리벽(엔진 import 0)이 무너지기 때문이다.
사본을 허용하는 대신 **갈라지면 빨개지는 검사**를 둔다 — 이것이 그 값이다.

⚠ 판 ㉜ 의 교훈: 자기가 만든 모델로 자기를 검증하면 언제나 통과한다. 그래서 이 검사는
   «둘이 같은가»만 본다. «노트북과 같은가»는 코드로 못 잰다 — 노트북이 저장소에 없다.
   `prompt.py` 는 그래서 손으로 옮기지 않고 **노트북에서 기계로 추출**했다.
"""
from __future__ import annotations

import importlib.util
import os
import sys

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
AI_ROOT = os.path.dirname(HERE)
ADAPTER = os.path.join(AI_ROOT, "app", "research", "research2",
                       "service", "bm_adapter.py")

sys.path.insert(0, AI_ROOT)

from app.research.bm import contracts as receiving  # noqa: E402


def _load_adapter():
    spec = importlib.util.spec_from_file_location("bm_adapter_under_test", ADAPTER)
    module = importlib.util.module_from_spec(spec)
    # `from __future__ import annotations` 라 전방 참조를 나중에 푼다 — 그때 pydantic 이
    # 모듈을 `sys.modules` 에서 찾는다. 등록을 빼면 `MarketJoinData` 가 영영 미완성이다.
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


sending = _load_adapter()


SHARED_MODELS = (
    "ConceptSnapshot",
    "MarketSizeData",
    "GrowthRateData",
    "PriceAnalysisData",
    "MarketJoinData",
)


@pytest.mark.parametrize("name", SHARED_MODELS)
def test_schema_is_identical_on_both_sides(name):
    """JSON 스키마가 **완전히** 같아야 한다 — 필드 하나, 기본값 하나가 어긋나도 실패."""
    left = getattr(sending, name).model_json_schema()
    right = getattr(receiving, name).model_json_schema()
    assert left == right, f"{name} 계약이 갈라졌다"


@pytest.mark.parametrize("name", ("MarketSizeData", "GrowthRateData", "PriceAnalysisData"))
def test_extra_is_forbidden_on_both_sides(name):
    """`extra="forbid"` 는 계약의 일부다. 풀리면 `price_base` 에 문자열을 넣던 사고가 돌아온다."""
    for module in (sending, receiving):
        assert getattr(module, name).model_config.get("extra") == "forbid"


def test_evidence_id_key_is_id_not_card_id():
    """보내는 쪽이 쓰는 키와 받는 쪽 검증기가 읽는 키가 **같은 글자**여야 한다.

    이게 어긋나면 컴파일도 테스트도 안 깨지고 인용만 **전부 조용히** 사라진다(판 ㉜ 실측).
    """
    card = {"카드_id": "C-F001", "종류": "관측", "값": 1.0}
    assert sending._evidence(card)["id"] == "C-F001"

    from app.research.bm.analyze import validate_market_evidence_ids  # noqa: PLC0415
    assert "item[\"id\"]" in validate_market_evidence_ids.__doc__ or True
    # 동작으로 확인한다 — 문서가 아니라 코드가 정본이다.
    market = receiving.MarketJoinData(
        concept_id="c1",
        concept_snapshot=receiving.ConceptSnapshot(),
        market_size=receiving.MarketSizeData(),
        growth_rate=receiving.GrowthRateData(),
        competitor_analysis=[],
        price_analysis=receiving.PriceAnalysisData(),
        demand_evidence=[],
        market_size_calculation={},
        evidence_list=[sending._evidence(card)],
    )
    result = _canvas_result(["C-F001", "C-없는것"])
    kept = validate_market_evidence_ids(result, market).canvas[0].market_evidence_ids
    assert kept == ["C-F001"]


def _canvas_result(evidence_ids):
    """9칸을 채운 최소 `BMAnalysisResult` — 첫 칸에만 인용을 단다."""
    cells = list(receiving.CanvasCell)
    canvas = [
        receiving.BMCanvasItem(
            canvas_cell=cell,
            content=["x"] if index == 0 else [],
            source_labels=["market_size"] if index == 0 else [],
            market_evidence_ids=evidence_ids if index == 0 else [],
            status=receiving.CanvasStatus.PARTIAL,
            reason="테스트",
        )
        for index, cell in enumerate(cells)
    ]
    return receiving.BMAnalysisResult(
        concept_id="c1", concept_name="n", canvas=canvas,
        market_fit_status="PARTIAL", consistency_status="PASS",
        market_fit_summary="", consistency_summary="",
    )
