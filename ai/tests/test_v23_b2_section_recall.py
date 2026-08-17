# -*- coding: utf-8 -*-
from __future__ import annotations

import copy
import inspect
import json
import os
import sys
import time
from pathlib import Path
from types import SimpleNamespace

import pytest

AI_ROOT = Path(__file__).resolve().parents[1]
R2 = AI_ROOT / "app" / "research" / "research2"
for path in (AI_ROOT, R2, R2 / "adapters", R2 / "blocks", R2 / "service"):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

import section_recall as section  # noqa: E402
from runlog import load_rules  # noqa: E402
from app.research import pipeline, product_market_join, product_pipeline, serialize  # noqa: E402
from app.research.bm import prompt as bm_prompt  # noqa: E402
from app.research.bm.analyze import validate_market_evidence_ids  # noqa: E402
from app.research.bm.contracts import (  # noqa: E402
    BMAnalysisResult, BMCanvasItem, CanvasCell, CanvasStatus,
)
import summary  # noqa: E402


RULES = load_rules()


def _doc(index: int, body: str = "온라인 플랫폼 입점 수수료는 10%이다.",
         *, url: str | None = None, retrieved: str | None = "2026-08-17"):
    return SimpleNamespace(
        slot_id=f"S{index}", trace_id=f"T{index}",
        url=url if url is not None else f"https://example{index}.com/report",
        text=body, content_status="usable", retrieved_at=retrieved,
    )


def _run(docs, provider, budget=10, deadline=None):
    return section.execute(
        docs={doc.trace_id: doc for doc in docs},
        ledger=SimpleNamespace(rows=[]), coverage=[], rules=RULES,
        call_budget=budget, deadline_monotonic=deadline, provider=provider)


def _all_sections(quote="온라인 플랫폼 입점 수수료는 10%이다."):
    return {name: [{"quote": quote}] for name in RULES["section_recall"]["sections"]}


def test_document_cap_is_eight_and_ninth_document_is_never_called():
    called = []

    def provider(doc, _sections, _reask):
        called.append(doc.trace_id)
        return _all_sections()

    result = _run([_doc(index) for index in range(9)], provider)
    assert result["selected_documents"] == 8
    assert result["attempts"] == 8
    assert len(called) == 8 and "T8" not in called


def test_empty_sections_are_valid_and_do_not_become_bad_json():
    result = _run([_doc(1)], lambda *_args: {}, budget=2)
    assert result["cards"] == []
    assert not any(row["code"] == "SECTION_RECALL_BAD_JSON"
                   for row in result["degradations"])


def test_exact_quote_and_qualitative_passage_are_promoted():
    quote = "온라인 플랫폼 입점 계약은 판매자 인증을 의무로 정한다."
    result = _run([_doc(1, quote)], lambda *_args: {
        "CHANNEL": [{"quote": quote}], "REGULATION": [{"quote": quote}]}, budget=1)
    assert len(result["cards"]) == 2
    assert all(card["값"] is None for card in result["cards"])
    assert all(card["인용"] == quote for card in result["cards"])


def test_paraphrase_is_rejected_by_exact_passage_gate():
    body = "온라인 플랫폼 입점 수수료는 10%이다."
    result = _run([_doc(1, body)], lambda *_args: {
        "CHANNEL": [{"quote": "온라인 판매 수수료는 십 퍼센트로 책정된다."}]}, budget=1)
    assert result["cards"] == []
    assert any(row["code"] == "SECTION_QUOTE_REJECTED" for row in result["degradations"])


def test_invalid_numeric_metadata_never_invents_value():
    quote = "온라인 플랫폼 입점 계약 조건을 공개했다."
    result = _run([_doc(1, quote)], lambda *_args: {
        "CHANNEL": [{"quote": quote, "number_raw": "999", "unit_raw": "원"}]}, budget=1)
    assert result["cards"][0]["값"] is None


def test_same_source_quote_is_deduplicated_but_independent_sources_are_preserved():
    quote = "온라인 플랫폼 입점 수수료는 10%이다."
    duplicate = _run([_doc(1, quote)], lambda *_args: {
        "CHANNEL": [{"quote": quote}, {"quote": quote}]}, budget=1)
    assert len(duplicate["cards"]) == 1

    independent = _run([_doc(1, quote), _doc(2, quote)],
                       lambda *_args: {"CHANNEL": [{"quote": quote}]}, budget=2)
    assert len(independent["cards"]) == 2
    assert {card["출처_url"] for card in independent["cards"]} == {
        "https://example1.com/report", "https://example2.com/report"}


@pytest.mark.parametrize("doc", [
    _doc(1, url=""),
    _doc(2, retrieved=None),
    _doc(3, url="https://cafe.naver.com/speculation"),
])
def test_promotion_requires_url_retrieved_at_and_allowed_source_kind(doc):
    called = []
    result = _run([doc], lambda *_args: called.append(True) or _all_sections(), budget=1)
    assert result["cards"] == [] and called == []


def test_public_cap_is_four_per_section_and_payload_bound_is_small():
    body = "\n".join(f"온라인 유통 조건 문장 번호 {index}이다." for index in range(6))
    passages = [{"quote": f"온라인 유통 조건 문장 번호 {index}이다."} for index in range(6)]
    result = _run([_doc(1, body)], lambda *_args: {"CHANNEL": passages}, budget=1)
    assert len(result["cards"]) == 4
    assert any(row["code"] == "SECTION_EVIDENCE_SAMPLED" for row in result["degradations"])
    policy_bound = (len(RULES["section_recall"]["sections"])
                    * RULES["section_recall"]["max_promoted_per_section"]
                    * RULES["section_recall"]["max_quote_chars"])
    assert policy_bound < 2 * 1024 * 1024


def test_promoted_card_uses_extended_evidence_allowlist_with_section_metadata():
    result = _run([_doc(1)], lambda *_args: {"CHANNEL": [{
        "quote": "온라인 플랫폼 입점 수수료는 10%이다.",
        "number_raw": "10", "unit_raw": "%"}]}, budget=1)
    public = serialize.evidence(result["cards"])[0]
    assert set(public) == set(serialize.evidence(result["cards"])[0])
    assert public["section"] == "CHANNEL" and public["placement"] == "HEAD"
    assert public["id"].startswith("C-SEC-")
    assert public["metric"] == "채널·유통 조건"


def test_budget_attempt_and_summary_reserve_contracts_are_bounded():
    cfg = RULES["section_recall"]
    assert cfg["max_documents"] == 8 and cfg["max_reasks"] == 2
    assert cfg["max_attempts"] == 10 and cfg["workers"] <= 4
    assert cfg["per_call_timeout_sec"] <= 45 and cfg["max_wall_sec"] <= 120
    assert cfg["deadline_guard_sec"] == 30 and cfg["summary_reserve"] == 3
    assert pipeline.COLLECT_CALLS + pipeline.HARNESS_CALLS + pipeline.SECTION_ATTEMPT_CAP + 3 == 96


def test_ten_attempt_cap_includes_failed_calls_and_hides_provider_detail():
    calls = []

    def fail(doc, _sections, _reask):
        calls.append(doc.trace_id)
        raise RuntimeError("SECRET provider response")

    result = _run([_doc(index) for index in range(8)], fail, budget=20)
    assert result["attempts"] == 10 and len(calls) == 10
    assert all("SECRET" not in row["detail"] for row in result["degradations"])
    assert result["cards"] == []


def test_deadline_guard_starts_no_provider_call_and_returns_soft_degradation():
    called = []
    result = _run([_doc(1)], lambda *_args: called.append(True), budget=10,
                  deadline=time.monotonic() + 20)
    assert called == [] and result["attempts"] == 0
    assert any(row["code"] == "SECTION_RECALL_TIMEOUT" for row in result["degradations"])


def test_hanging_batch_returns_at_one_timeout_and_starts_no_reask():
    rules = copy.deepcopy(RULES)
    rules["section_recall"].update({
        "per_call_timeout_sec": 0.08,
        "max_wall_sec": 0.20,
    })
    called = []

    def hangs(doc, _sections, reask):
        called.append((doc.trace_id, reask))
        time.sleep(0.5)
        return {}

    started = time.monotonic()
    result = section.execute(
        docs={doc.trace_id: doc for doc in [_doc(index) for index in range(4)]},
        ledger=SimpleNamespace(rows=[]), coverage=[], rules=rules,
        call_budget=10, provider=hangs)
    elapsed = time.monotonic() - started

    assert elapsed < 0.30
    assert result["attempts"] == 4 and result["base_attempts"] == 4
    assert result["reasks"] == 0 and len(called) == 4
    assert result["cards"] == [] and result["timeouts"] == 4
    assert any(row["code"] == "SECTION_RECALL_TIMEOUT"
               for row in result["degradations"])


def test_task_deadline_allows_first_batch_but_prevents_the_next_batch():
    rules = copy.deepcopy(RULES)
    rules["section_recall"].update({
        "per_call_timeout_sec": 0.08,
        "max_wall_sec": 0.30,
    })
    called = []

    def finite(doc, _sections, reask):
        called.append((doc.trace_id, reask))
        time.sleep(0.06)
        return {}

    result = section.execute(
        docs={doc.trace_id: doc for doc in [_doc(index) for index in range(5)]},
        ledger=SimpleNamespace(rows=[]), coverage=[], rules=rules,
        call_budget=10, provider=finite,
        deadline_monotonic=time.monotonic() + 30.10)

    assert result["attempts"] == 4 and len(called) == 4
    assert result["reasks"] == 0


def test_provider_bad_json_and_failure_do_not_destroy_base_result():
    bad = _run([_doc(1)], lambda *_args: "not json", budget=1)
    assert bad["cards"] == []
    assert any(row["code"] == "SECTION_RECALL_BAD_JSON" for row in bad["degradations"])


def test_rescore_and_bm_paths_do_not_execute_section_provider():
    full_source = inspect.getsource(pipeline._full)
    assert "if collect:" in full_source
    assert "SECTION.execute" not in full_source
    from app.research import product_pipeline
    assert "section_recall" not in inspect.getsource(product_pipeline._bm_product)


def _full_with_channel_evidence():
    return {
        "mode": "FULL", "market": {},
        "evidence": [{
            "id": "C-SEC-CH-channel", "kind": "관측", "metric": "채널·유통 조건",
            "subject": "채널·유통 조건", "period": None, "value": None, "unit": None,
            "grade": "실무 신뢰", "gradeReason": "source kind", "sourceUrl": "https://x.com",
            "sourceKind": "press", "retrievedAt": "2026-08-17",
            "quote": "온라인 플랫폼 입점 수수료 조건이 적용된다.", "caveats": [],
            "formula": None, "inputs": None, "materialIds": [], "assumptions": [],
        }],
    }


def _concept():
    return {"concept_id": "c", "name": "n", "target": "t", "problem": "p", "solution": "s"}


def test_bm_uses_exact_full_channel_evidence_without_new_section_call():
    joined = product_market_join.build(_full_with_channel_evidence(), _concept(), "c")
    assert [item["id"] for item in joined.channel_analysis] == ["C-SEC-CH-channel"]
    assert joined.channel_analysis[0] in joined.evidence_list


def test_noneligible_channel_identity_is_not_rejudged_from_current_quote():
    value = _full_with_channel_evidence()
    value["evidence"][0]["id"] = "C-SEC-channel"
    joined = product_market_join.build(value, _concept(), "c")
    assert joined.channel_analysis == []


def test_same_full_version_keeps_channel_material_when_current_rules_change(monkeypatch):
    from app.research.research2 import runlog

    value = _full_with_channel_evidence()
    before = product_market_join.build(value, _concept(), "c").channel_analysis
    monkeypatch.setattr(runlog, "load_rules", lambda: {
        "section_recall": {"channel_anchors": ["완전히-다른-현재-규칙"]}})
    after = product_market_join.build(value, _concept(), "c").channel_analysis

    assert before == after


def test_unknown_market_evidence_id_cannot_enter_bm_canvas():
    joined = product_market_join.build(_full_with_channel_evidence(), _concept(), "c")
    cells = [BMCanvasItem(
        canvas_cell=cell, content=["x"] if cell == CanvasCell.CHANNELS else [],
        source_labels=["channel_analysis"] if cell == CanvasCell.CHANNELS else [],
        market_evidence_ids=["C-SEC-CH-channel", "C-SEC-fake"] if cell == CanvasCell.CHANNELS else [],
        status=CanvasStatus.PARTIAL, reason="test") for cell in CanvasCell]
    result = BMAnalysisResult(
        concept_id="c", concept_name="n", canvas=cells,
        market_fit_status="PARTIAL", consistency_status="PASS",
        market_fit_summary="", consistency_summary="")
    guarded = validate_market_evidence_ids(result, joined)
    channels = next(item for item in guarded.canvas if item.canvas_cell == CanvasCell.CHANNELS)
    assert channels.market_evidence_ids == ["C-SEC-CH-channel"]


def test_channel_source_label_has_ai_java_parity():
    java = (AI_ROOT.parent / "backend" / "src" / "main" / "java" / "com" / "aivle"
            / "backend" / "taskrun" / "contract" / "MarketResearchContract.java").read_text(
                encoding="utf-8")
    assert "channel_analysis" in bm_prompt.ALLOWED_CANVAS_SOURCE_LABELS
    assert '"channel_analysis"' in java


def test_summary_accepts_merged_cards_without_rebuilding_or_extra_allowance(monkeypatch):
    card = {"카드_id": "C-SEC-x", "종류": "관측", "칸": "채널·유통 조건",
            "계량": "채널·유통 조건", "주제": "채널·유통 조건", "기간": None,
            "값": None, "단위": None, "등급": "추정", "가정": []}
    monkeypatch.setattr(summary.CARDS, "build", lambda *_args: pytest.fail("cards rebuilt"))
    monkeypatch.setattr(summary, "_call", lambda _prompt: (
        json.dumps({"요약": [{"칸": "채널·유통 조건", "문장": "관측됨", "카드_id": ["C-SEC-x"]}]},
                   ensure_ascii=False), {"in": 1, "out": 1}))
    monkeypatch.setattr(summary.CHECK, "check", lambda *_args: [])
    result = summary.summarize("r", "c", max_retry=1, cards_doc={"카드": [card]})
    assert result["_사용량"]["calls"] == 1 and result["요약"]


def test_public_envelope_and_stage_contracts_remain_exact():
    assert serialize.ENVELOPE == (
        "runId", "conceptId", "asOf", "generatedAt", "mode", "stages", "degradations",
        "scorecard", "market", "canvas", "bm", "evidence", "summary", "notes",
        "judgment", "prescriptions", "synthesis", "report")
    assert pipeline.STAGES_FULL == (
        "harness", "dryrun", "collect", "verdict", "canvas", "cards", "summary",
        "sections")


def test_production_sections_stage_loads_promoted_cards_without_market_recalculation(monkeypatch):
    promoted = [{"카드_id": "C-SEC-x"}]
    monkeypatch.setattr(section, "load", lambda _run: {
        "attempts": 2, "wall_seconds": 1.4, "cards": promoted, "degradations": []})
    monkeypatch.setattr(section, "load_cards", lambda _run: promoted)
    ledger = pipeline.Run()

    assert pipeline._sections(ledger, "source-run") == promoted
    assert ledger.stages[-1].as_contract() == {
        "name": "sections", "status": "OK", "seconds": 1, "llmCalls": 2}


def test_bm_promote_consumes_immutable_full_evidence_without_reclassification():
    market = {"evidence": [
        {"id": "C-SEC-CH-eligible", "metric": "채널·유통 조건", "quote": "stored"},
        {"id": "C-SEC-other", "metric": "규제·인증 요건", "quote": "stored"},
    ]}

    assert product_pipeline._promote_for_bm(market) is market
    joined = product_market_join.build(market, {
        "concept_id": "c", "name": "n", "target": "t", "problem": "p", "solution": "s"},
        "c")
    assert {item["id"] for item in joined.evidence_list} == {
        "C-SEC-CH-eligible", "C-SEC-other"}
    assert [item["id"] for item in joined.channel_analysis] == ["C-SEC-CH-eligible"]


def test_section_metadata_and_human_report_survive_allowlist_serialization():
    card = {"카드_id": "C-SEC-x", "종류": "관측", "계량": "규제·인증 요건",
            "주제": "규제·인증 요건", "등급": "실무 신뢰", "등급_근거": "official",
            "출처_url": "https://agency.example/rule", "kind": "official_page",
            "조회일": "2026-08-17", "인용": "인증 의무를 따라야 한다.",
            "_절": "REGULATION", "_갈래": "HEAD", "_발행사": "agency.example",
            "_표키": "table-1", "_원문값": "36,745억원"}
    [item] = serialize.evidence([card])
    assert {key: item[key] for key in ("section", "placement", "issuer", "tableKey", "raw")} == {
        "section": "REGULATION", "placement": "HEAD", "issuer": "agency.example",
        "tableKey": "table-1", "raw": "36,745억원"}

    human = serialize.report({"쓴_모델": "market-report-model", "유령_수": 0,
                              "컨셉_누출_수": 0, "머리말": "AI가 작성한 해설",
                              "절": [{"section": "REGULATION", "본문": "규제 해설"}]})
    assert human["sections"] == [{"subject": "REGULATION", "markdown": "규제 해설"}]
    assert human["writtenBy"] == "market-report-model"
