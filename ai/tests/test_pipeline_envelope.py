# -*- coding: utf-8 -*-
"""오케스트레이터가 내는 **봉투**가 계약과 같은 모양인지 본다.

정본은 `backend/.../MarketResearchContract.java` 이고, **골든 픽스처가 두 언어의 접점**이다.
그래서 여기서는 자바 검증기를 파이썬으로 **다시 구현하지 않는다**(그러면 「같은 물음을 두
곳이 각자 푼다」의 일곱 번째가 된다). 대신 **픽스처와 키 집합을 대조**한다 — 한쪽이
스키마를 바꾸면 이 검사가 먼저 빨개진다.

⚠ 원장(`runs/`)은 저장소에 없다(`.gitignore`). 원장을 요구하는 검사는 **건너뛴다** —
   없는 것을 있는 척 통과시키지 않고, 건너뛴 사실이 보이게 둔다.
"""
from __future__ import annotations

import asyncio
import io
import json
import os
import sys

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
AI_ROOT = os.path.dirname(HERE)
sys.path.insert(0, AI_ROOT)

from app.research import pipeline, serialize  # noqa: E402
from app.research.bm.contracts import (  # noqa: E402
    BMAnalysisResult,
    BMCanvasItem,
    BMDecision,
    BMFinalResult,
    CanvasCell,
    CanvasStatus,
)

FIXTURES = os.path.join(HERE, "fixtures", "market_research")
#: ⚠ `beauty-13b` 는 이름과 달리 `CPT-CAFE-INV` 로 기록돼 있다 — 표에 든 원장을 쓴다.
SEED_RUN = "beauty-13"
HAS_LEDGER = os.path.isdir(os.path.join(pipeline.RESEARCH_HOME, "runs", SEED_RUN))
needs_ledger = pytest.mark.skipif(
    not HAS_LEDGER, reason=f"원장 runs/{SEED_RUN} 없음 — 저장소에 원장을 담지 않는다")


def _golden(name: str) -> dict:
    with io.open(os.path.join(FIXTURES, name), encoding="utf-8") as handle:
        return json.load(handle)


def _keys(node: dict) -> set[str]:
    """문서용 `_` 접두 칸은 계약이 아니다 — 픽스처에만 있고 결과에는 없다."""
    return {key for key in node if not key.startswith("_")}


# ══════════════════════════════════════════════════════════════
def test_envelope_rejects_a_field_the_contract_does_not_have():
    with pytest.raises(serialize.ContractDrift):
        serialize.envelope(runId="r", conceptId="c", madeUpField=1)


def test_envelope_fills_every_contract_field_even_when_unused():
    """빠진 칸은 **`null` 이지 없는 칸이 아니다** — 그래야 봉투를 한 번에 못박을 수 있다."""
    out = serialize.envelope(runId="r", conceptId="c")
    assert set(out) == set(serialize.ENVELOPE)
    assert out["market"] is None and out["canvas"] is None


def test_envelope_matches_both_golden_fixtures():
    assert set(serialize.ENVELOPE) == _keys(_golden("full.json"))
    assert set(serialize.ENVELOPE) == _keys(_golden("bm.json"))


# ══════════════════════════════════════════════════════════════
@needs_ledger
def test_full_mode_matches_the_golden_shape():
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-full", 600))
    golden = _golden("full.json")

    assert set(out) == _keys(golden)
    assert out["mode"] == "FULL"
    assert out["canvas"] is None and out["bm"] is None

    assert {s["subject"] for s in out["scorecard"]} == {s["subject"] for s in golden["scorecard"]}
    for row in out["scorecard"]:
        assert set(row) == set(golden["scorecard"][0])
        assert row["state"] in serialize.SCORE_STATES
        assert row["detail"].strip()

    assert set(out["market"]) == set(golden["market"])
    for stage in out["stages"]:
        assert set(stage) == set(golden["stages"][0])
        assert stage["status"] in ("OK", "SKIPPED", "FAILED")
    for item in out["degradations"]:
        assert set(item) == {"stage", "code", "detail"}


@needs_ledger
def test_evidence_carries_exactly_the_contract_keys_and_nothing_else():
    """**allowlist 검사.** 원장의 `슬롯`·`채택`·`연도` 는 나가면 안 된다."""
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-allowlist", 600))
    expected = set(_golden("full.json")["evidence"][0])
    assert out["evidence"], "근거가 0건이면 이 검사는 아무것도 못 본다"
    for item in out["evidence"]:
        assert set(item) == expected
        assert isinstance(item["caveats"], list)
        assert item["grade"] in serialize.GRADES
    ids = [item["id"] for item in out["evidence"]]
    assert len(ids) == len(set(ids)), "근거 id 는 유일해야 한다"


@needs_ledger
def test_market_figures_only_cite_evidence_that_exists():
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-refs", 600))
    known = {item["id"] for item in out["evidence"]}
    for name in ("tam", "sam", "som", "growth", "price"):
        figure = out["market"][name]
        if figure is None:
            continue
        assert set(figure["evidenceIds"]) <= known, f"{name} 이 없는 근거를 인용했다"


@needs_ledger
def test_rescore_spends_nothing():
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-free", 600))
    assert sum(stage["llmCalls"] for stage in out["stages"]) == 0
    assert any(item["code"] == "MODE_RESCORE" for item in out["degradations"])


@needs_ledger
def test_stages_that_did_not_run_say_so_instead_of_vanishing():
    """안 돈 단계를 목록에서 빼면 「안 돌았다」가 「돌았다」로 읽힌다."""
    out = asyncio.run(pipeline.run_market_research(
        {"mode": "RESCORE", "sourceRun": SEED_RUN, "conceptId": "smoke"},
        "test-envelope-skipped", 600))
    names = {stage["name"] for stage in out["stages"]}
    assert {"harness", "dryrun", "collect"} <= names
    assert {item["stage"] for item in out["degradations"]} >= {"harness", "dryrun", "collect"}


# ══════════════════════════════════════════════════════════════
# BM — 경계 파생(층 1)
# ══════════════════════════════════════════════════════════════
def _canvas_items(evidence_id: str) -> list[BMCanvasItem]:
    return [
        BMCanvasItem(
            canvas_cell=cell,
            content=["값"] if index == 0 else [],
            source_labels=["market_size"] if index == 0 else [],
            market_evidence_ids=[evidence_id] if index == 0 else [],
            status=CanvasStatus.PARTIAL, reason="사유")
        for index, cell in enumerate(CanvasCell)
    ]


def test_cell_caveats_are_derived_from_the_evidence_it_cites():
    """**이 프로젝트의 대표 검사.** 모델이 경계를 안 실어도 기계가 끌어온다(판 ㉜-b 0/2)."""
    boundary = "전사 매출 — 시장 매출 아님. 용도는 DART 경로 검증이다."
    evidence = [{"id": "C-F010", "caveats": [boundary]},
                {"id": "C-F999", "caveats": ["인용되지 않은 경계"]}]

    cells = serialize.canvas_cells(_canvas_items("C-F010"), evidence)

    cited = next(c for c in cells if c["marketEvidenceIds"])
    assert boundary in cited["caveats"], "인용한 근거의 경계가 칸에 없다"
    assert "인용되지 않은 경계" not in cited["caveats"], "인용하지 않은 경계까지 끌어오면 안 된다"
    for cell in cells:
        if not cell["marketEvidenceIds"]:
            assert cell["caveats"] == []


def test_canvas_cell_matches_the_golden_key_set():
    cells = serialize.canvas_cells(_canvas_items("C-F010"),
                                   [{"id": "C-F010", "caveats": []}])
    expected = set(_golden("bm.json")["canvas"]["cells"][0])
    assert len(cells) == 9
    for cell in cells:
        assert set(cell) == expected
    assert {cell["canvasCell"] for cell in cells} == {c.value for c in CanvasCell}


def test_bm_block_matches_the_golden_key_set():
    final = BMFinalResult(
        concept_id="c1", decision=BMDecision.CONDITIONAL, confidence="MEDIUM",
        summary="요약", canvas=_canvas_items("C-F010"),
        strengths=["s"], weaknesses=["w"], risks=["r"],
        market_fit_summary="a", consistency_summary="b",
        legal_context_used=False, legal_status="UNVERIFIED")
    analysis = BMAnalysisResult(
        concept_id="c1", concept_name="n", canvas=_canvas_items("C-F010"),
        market_fit_status="PARTIAL", consistency_status="PASS",
        market_fit_summary="a", consistency_summary="b")
    block = serialize.bm(final, analysis)
    golden = _golden("bm.json")["bm"]
    assert set(block) == set(golden)
    assert set(block["legal"]) == set(golden["legal"])


@needs_ledger
def test_bm_mode_derives_caveats_end_to_end(monkeypatch):
    """모델은 스텁이다 — 검사 대상은 **경계가 칸까지 도달하는가**이지 모델 품질이 아니다."""
    captured = {}

    async def _stub_flow(bm_input, **_):
        captured["evidence_ids"] = [
            item["id"] for item in bm_input.market_join_data.evidence_list]
        first = captured["evidence_ids"][0]
        analysis = BMAnalysisResult(
            concept_id=bm_input.concept_id, concept_name="n",
            canvas=_canvas_items(first),
            market_fit_status="PARTIAL", consistency_status="PASS",
            market_fit_summary="a", consistency_summary="b")
        final = BMFinalResult(
            concept_id=bm_input.concept_id, decision=BMDecision.CONDITIONAL,
            confidence="MEDIUM", summary="요약", canvas=analysis.canvas,
            strengths=[], weaknesses=[], risks=[],
            market_fit_summary="a", consistency_summary="b",
            legal_context_used=False, legal_status="UNVERIFIED")
        return {"bm_analysis": analysis, "final_result": final}

    import app.research.bm.flow as flow_module
    monkeypatch.setattr(flow_module, "run_bm_pipeline_flow", _stub_flow)

    out = asyncio.run(pipeline.run_market_research(
        {"mode": "BM", "sourceRun": SEED_RUN, "conceptId": "smoke", "llmBudget": 2},
        "test-envelope-bm", 600))

    assert out["mode"] == "BM"
    assert out["scorecard"] is None and out["market"] is None
    assert set(out) == _keys(_golden("bm.json"))

    by_id = {item["id"]: item for item in out["evidence"]}
    for cell in out["canvas"]["cells"]:
        want = {c for ref in cell["marketEvidenceIds"] for c in by_id[ref]["caveats"]}
        assert want <= set(cell["caveats"]), "인용한 근거의 경계가 칸에 도달하지 않았다"
    assert sum(stage["llmCalls"] for stage in out["stages"]) == 1


def test_layer_two_refuses_a_cell_that_dropped_a_boundary():
    """층 2. 파생이 회귀하면 **결과를 내지 않는다** — 조용한 소실은 출력이 멀쩡해 보인다."""
    cells = [{"canvasCell": "CHANNELS", "marketEvidenceIds": ["C-F010"], "caveats": []}]
    with pytest.raises(serialize.ContractDrift):
        serialize.assert_caveats_reached(
            cells, [{"id": "C-F010", "caveats": ["전사 매출 — 시장 매출 아님"]}])
