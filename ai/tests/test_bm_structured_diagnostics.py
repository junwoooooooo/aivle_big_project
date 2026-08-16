import asyncio
import logging

import pytest
from pydantic import ValidationError

from app.research.bm.analyze import run_bm_analysis
from app.research.bm.contracts import (
    BMAnalysisResult,
    ConceptSnapshot,
    GrowthRateData,
    MarketJoinData,
    MarketSizeData,
    PriceAnalysisData,
)
from app.research.bm.diagnostics import (
    log_bm_validation_failure,
    safe_validation_diagnostics,
)
from app.research.bm.normalize import create_bm_analysis_input, resolve_bm_input


def _validation_error() -> ValidationError:
    with pytest.raises(ValidationError) as caught:
        BMAnalysisResult.model_validate({
            "concept_id": "secret-input-sk-do-not-log",
            "concept_name": "raw provider response must not log",
            "canvas": "prompt and user document body",
        })
    return caught.value


def _custom_canvas_validator_error() -> ValidationError:
    cells = list(BMAnalysisResult.model_json_schema()["$defs"]["CanvasCell"]["enum"])
    canvas = [{
        "canvas_cell": cell,
        "content": [],
        "source_labels": [],
        "market_evidence_ids": [],
        "status": "UNVERIFIED",
        "reason": "safe fixture",
        "missing_evidence": [],
    } for cell in cells[:-1]]
    with pytest.raises(ValidationError) as caught:
        BMAnalysisResult.model_validate({
            "concept_id": "c1",
            "concept_name": "concept",
            "canvas": canvas,
            "market_fit_status": "PARTIAL",
            "consistency_status": "PARTIAL",
            "market_fit_summary": "safe fixture",
            "consistency_summary": "safe fixture",
            "strengths": [],
            "weaknesses": [],
            "risks": [],
        })
    return caught.value


def _resolved():
    market = MarketJoinData(
        concept_id="c1",
        concept_snapshot=ConceptSnapshot(concept_name="concept"),
        market_size=MarketSizeData(),
        growth_rate=GrowthRateData(),
        competitor_analysis=[],
        price_analysis=PriceAnalysisData(),
        demand_evidence=[],
        market_size_calculation={},
    )
    return resolve_bm_input(create_bm_analysis_input(market_data=market))


def test_bm_json_schema_does_not_express_the_custom_exact_nine_validator():
    schema = BMAnalysisResult.model_json_schema()
    canvas = schema["properties"]["canvas"]
    item_properties = schema["$defs"]["BMCanvasItem"]["properties"]
    cells = schema["$defs"]["CanvasCell"]["enum"]

    assert {
        "concept_id", "concept_name", "canvas", "market_fit_status",
        "consistency_status", "strengths", "weaknesses", "risks",
    } <= set(schema["properties"])
    assert {
        "canvas_cell", "content", "source_labels", "market_evidence_ids", "status",
    } <= set(item_properties)
    assert len(cells) == 9
    assert "minItems" not in canvas
    assert "maxItems" not in canvas
    assert "uniqueItems" not in canvas
    assert any(issue["type"] == "value_error" for issue in
               safe_validation_diagnostics(_custom_canvas_validator_error()))


def test_safe_validation_diagnostics_exclude_input_url_and_context():
    diagnostics = safe_validation_diagnostics(_validation_error())
    rendered = repr(diagnostics)

    assert diagnostics
    assert all(set(item) == {"loc", "type", "msg"} for item in diagnostics)
    assert "secret-input" not in rendered
    assert "raw provider response" not in rendered
    assert "prompt and user document body" not in rendered
    assert "http" not in rendered


def test_safe_validation_log_contains_only_request_identity_and_error_shape(caplog):
    failure = _validation_error()
    with caplog.at_level(logging.WARNING, logger="app.research.bm.diagnostics"):
        log_bm_validation_failure(failure, {
            "taskRunId": "task-run-1",
            "taskAttemptId": "attempt-1",
            "correlationId": "correlation-1",
        })

    message = caplog.text
    assert "schemaName=BMAnalysisResult" in message
    assert "taskRunId=task-run-1" in message
    assert "taskAttemptId=attempt-1" in message
    assert "correlationId=correlation-1" in message
    assert "secret-input" not in message
    assert "raw provider response" not in message
    assert "prompt and user document body" not in message
    assert "sk-do-not-log" not in message


class _FailingResponses:
    def __init__(self, failure):
        self.failure = failure
        self.calls = 0

    async def parse(self, **_kwargs):
        self.calls += 1
        raise self.failure


class _FailingClient:
    def __init__(self, failure):
        self.responses = _FailingResponses(failure)


def test_run_bm_analysis_logs_validation_once_and_preserves_failure(caplog):
    failure = _validation_error()
    client = _FailingClient(failure)
    with caplog.at_level(logging.WARNING, logger="app.research.bm.diagnostics"):
        with pytest.raises(ValidationError) as caught:
            asyncio.run(run_bm_analysis(
                resolved=_resolved(),
                client=client,
                model="fixture-model",
                diagnostic_context={
                    "taskRunId": "task-run-2",
                    "taskAttemptId": "attempt-2",
                    "correlationId": "correlation-2",
                },
            ))

    assert caught.value is failure
    assert client.responses.calls == 1
    assert caplog.text.count("BM schema validation failed") == 1
