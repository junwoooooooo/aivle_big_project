# -*- coding: utf-8 -*-
from __future__ import annotations

import importlib
import json
import os
import sys
import types

import pytest


AI_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESEARCH = os.path.join(AI_ROOT, "app", "research")
R2 = os.path.join(RESEARCH, "research2")
for path in (R2, os.path.join(R2, "service"), os.path.join(R2, "blocks"),
             os.path.join(R2, "harness"), AI_ROOT):
    if path not in sys.path:
        sys.path.insert(0, path)

pdf_text = importlib.import_module("pdf_text")
gate = importlib.import_module("gate")
verdict = importlib.import_module("verdict")
cards = importlib.import_module("cards")
c_chain = importlib.import_module("c_chain")
from app.research import pipeline, serialize  # noqa: E402


COLUMNS = {
    "enabled": True, "min_words": 40, "band": 10, "min_gap": 25,
    "max_cross_ratio": 0.1, "min_column_share": 0.15,
    "y_tolerance": 3, "cross_margin": 2,
}


class FakePage:
    def __init__(self, words, fallback="OLD", width=400, fail=False):
        self.width = width
        self._words = words
        self._fallback = fallback
        self._fail = fail

    def extract_text(self):
        return self._fallback

    def extract_words(self):
        if self._fail:
            raise ValueError("word layer failure")
        return self._words


def _column(x0, x1, prefix, count=20):
    return [{"x0": x0, "x1": x1, "top": index * 10, "text": f"{prefix}{index}"}
            for index in range(count)]


def test_pdf_two_and_three_columns_use_column_reading_order():
    two = _column(10, 170, "L") + _column(230, 390, "R")
    text, reason = pdf_text._page_text(FakePage(two), COLUMNS)
    assert reason == "column_order"
    assert text.index("L19") < text.index("R0")

    three = (_column(10, 170, "A") + _column(215, 385, "B")
             + _column(430, 590, "C"))
    text, reason = pdf_text._page_text(FakePage(three, width=600), COLUMNS)
    assert reason == "column_order"
    assert text.index("A19") < text.index("B0") < text.index("C0")


def test_pdf_uncertain_layout_and_word_failure_fall_back_page_locally():
    fake_columns = _column(10, 170, "L", 45) + _column(230, 390, "R", 5)
    assert pdf_text._page_text(FakePage(fake_columns, "PRESENTATION"), COLUMNS) == (
        "PRESENTATION", "unbalanced_columns")
    assert pdf_text._page_text(FakePage([], "RECOVERED", fail=True), COLUMNS) == (
        "RECOVERED", "word_extraction_failed")
    single = _column(10, 390, "S", 45)
    assert pdf_text._page_text(FakePage(single, "SINGLE"), COLUMNS)[0] == "SINGLE"


def test_pdf_scan_keeps_existing_unreadable_reason(monkeypatch):
    class Pdf:
        pages = [FakePage([], fallback="")]

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

    monkeypatch.setitem(sys.modules, "pdfplumber", types.SimpleNamespace(open=lambda _raw: Pdf()))
    text, reason = pdf_text.extract(b"%PDF-fake", {**COLUMNS, "columns": COLUMNS})
    assert text == ""
    assert reason == "텍스트층 없음(스캔본 추정)"


def _empty_ledger(run_id):
    return types.SimpleNamespace(run_id=run_id, rows=[])


def _render_notes(run_id, notes):
    report = c_chain.render_report(
        {}, [], [], [], _empty_ledger(run_id), [], [], {}, None,
        {"assumptions": {"자료_부재_확정": {}},
         "consistency": {"report_notes": {"independent_topdown_blocked": notes}}},
    )
    return report.not_found["independent_topdown_blocked"]


def test_report_notes_are_run_scoped_with_generic_fallback_only():
    notes = {"_공용": ["공용 안내"], "run-A": ["A 사업 전용 결론"]}
    assert _render_notes("run-A", notes) == ["A 사업 전용 결론"]
    assert _render_notes("run-B", notes) == ["공용 안내"]
    assert "A 사업 전용 결론" not in _render_notes("run-B", notes)


def test_removed_business_assumptions_are_not_executable():
    path = os.path.join(R2, "rules", "assumptions.v1.json")
    rules = json.load(open(path, encoding="utf-8"))
    assert set(rules["by_role"]) == {"연환산"}
    for role in ("침투율", "단가", "세그먼트비중", "추정점유율", "도달가능비중", "1년차획득률"):
        assert role not in rules["by_role"]


def test_harness_rejects_allowed_nonobservable_role_without_actual_value():
    vocab = {
        "template": {"required_roles": {}, "허용_자리": {"enabled": False}},
        "var_role": {"_가정_역할": ["침투율", "연환산"]},
    }
    formulas = [{"formula_id": "F", "template": "T",
                 "vars": [{"var_id": "v", "var_role": "침투율", "_observable": False}]}]
    result = gate.check_template_roles(
        formulas, vocab, assumptions={"by_role": {"연환산": {"value": 12}}})
    assert result["passed"] is False
    assert "관측으로 채우거나 식에서 제거/재설계" in result["violations"][0]["why"]


def _market_ledger(confirmed):
    return {"report": {"headline_numbers": []}, "confirmed": confirmed}


def test_market_observations_deduplicate_slot_copies_but_keep_independent_sources(monkeypatch):
    rows = [
        {"slot_id": "S1", "claim_type": "TAM", "fact_id": "F1", "trace_id": "T1",
         "metric": "시장규모", "value": 100, "unit": "원", "period": "2025", "source_url": "u1"},
        {"slot_id": "S2", "claim_type": "SAM", "fact_id": "F1", "trace_id": "T1",
         "metric": "시장규모", "value": 100, "unit": "원", "period": "2025", "source_url": "u1"},
        {"slot_id": "S3", "claim_type": "TAM", "fact_id": "F2", "trace_id": "T2",
         "metric": "시장규모", "value": 100, "unit": "원", "period": "2025", "source_url": "u2"},
    ]
    monkeypatch.setattr(verdict, "_confirmed", lambda led, _types: led["confirmed"])
    observations = verdict._market_observations(_market_ledger(rows))
    assert len(observations) == 2
    copied = next(item for item in observations if item["trace_id"] == "T1")
    assert copied["slot_ids"] == ["S1", "S2"]
    assert set(copied["claim_types"]) == {"TAM", "SAM"}


def test_market_without_money_observation_does_not_invent_tam_or_sam(monkeypatch):
    count = [{"slot_id": "S1", "claim_type": "TAM", "fact_id": "F1", "trace_id": "T1",
              "metric": "사업체수", "value": 1000, "unit": "개", "period": "2025",
              "source_url": "u1"}]
    monkeypatch.setattr(verdict, "_confirmed", lambda led, _types: led["confirmed"])
    result = verdict.judge_market(_market_ledger(count), {}, {})
    assert result["TAM_추정"] is None and result["SAM_추정"] is None
    assert "미확보" in result["사유"]


def test_som_missing_segment_ratio_never_defaults_to_whole_market(monkeypatch):
    monkeypatch.setattr(verdict, "_pick_base", lambda *_args: (1000, [{"slot_id": "S"}], ""))
    monkeypatch.setattr(verdict, "_rules", lambda: {"assumptions": {"by_role": {"연환산": {"value": 12}}}})
    led = {"report": {"headline_numbers": []}}
    hyp = {"9_SOM_초기점유": {"가정_침투율": 0.1},
           "6_수익_가격": {"제안값_krw_월": 10000}}
    result = verdict.judge_som(led, hyp)
    assert result["추정"]["입력"]["세그먼트비중"] is None
    assert result["추정"]["목표 고객 수"] is None
    assert result["추정"]["값"] is None


def _card_result(monkeypatch, estimate, grade="근거 없음"):
    monkeypatch.setattr(cards.bm_scorer, "load_ledger", lambda _run: {
        "run_id": "r", "slots": [], "ledger_rows": [], "facts": {}})
    monkeypatch.setattr(cards.V, "build", lambda *_args: {
        "concept": "c", "시장_추정": estimate})
    monkeypatch.setattr(cards, "weakest", lambda _grades, _ladder: grade)
    return cards.build("r", "c")["카드"][0]


def test_grade_none_calculated_card_and_serializer_suppress_numeric_value(monkeypatch):
    estimate = {"TAM_추정": {"값": 123, "입력": {}, "근거": [], "가정": []}}
    card = _card_result(monkeypatch, estimate)
    assert card["값"] is None
    assert serialize._figure(estimate["TAM_추정"], "KRW", "근거 없음") is None


def test_growth_card_uses_percent_scale_and_keeps_ratio_internal(monkeypatch):
    estimate = {"성장률_추정": {"값": 0.15, "값_퍼센트": 15.0,
                                "입력": {}, "근거": [], "가정": []}}
    card = _card_result(monkeypatch, estimate, grade="확정")
    assert card["단위"] == "%" and card["값"] == 15.0
    assert card["_비율_원값"] == 0.15


def test_public_envelope_and_stage_names_are_unchanged():
    assert serialize.ENVELOPE == (
        "runId", "conceptId", "asOf", "generatedAt", "mode", "stages", "degradations",
        "scorecard", "market", "canvas", "bm", "evidence", "summary", "notes")
    assert pipeline.STAGES_FULL == (
        "harness", "dryrun", "collect", "verdict", "canvas", "cards", "summary")
