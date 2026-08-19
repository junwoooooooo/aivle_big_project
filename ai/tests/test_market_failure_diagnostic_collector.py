from __future__ import annotations

import asyncio
import json
from pathlib import Path

import pytest

from app.api.executions import internal_error
from app.market_failure_diagnostics import (
    collect_market_failure_diagnostics,
    diagnostic_log_detail,
    fallback_stderr_diagnostics,
)
from app.providers import ProviderFailure
from app.research import product_pipeline


def _harness_record(tmp_path: Path, *, kind: str, detail: str, attempt: int = 1) -> Path:
    target = tmp_path / "runs-generated" / "harness" / "fixture" / "무인_기록.json"
    target.parent.mkdir(parents=True)
    target.write_text(json.dumps({
        "개입": [{"종류": kind, "상세": detail, "멈췄나": True}],
        "_시도": attempt,
        "_상한": 3,
        "결정": [{"왜": "must-not-be-collected"}],
    }, ensure_ascii=False), encoding="utf-8")
    return target


@pytest.mark.parametrize(("exception_type", "detail", "http_status"), [
    ("RateLimitError", "Error code: 429 - rate limited", 429),
    ("APIConnectionError", "connection failed", None),
    ("InternalServerError", "HTTP status 500", 500),
])
def test_harness_llm_failure_extracts_only_safe_fields(
        tmp_path: Path, exception_type: str, detail: str, http_status: int | None):
    _harness_record(
        tmp_path,
        kind=f"멈춤 — LLM 호출 실패({exception_type})",
        detail=detail,
    )

    assert collect_market_failure_diagnostics(tmp_path) == {
        "stage": "HARNESS_LLM",
        "node": None,
        "exceptionType": exception_type,
        "httpStatus": http_status,
        "errorCode": None,
        "attempt": 1,
        "adapter": None,
    }


def test_meter_failure_uses_latest_llm_error_node(tmp_path: Path):
    target = tmp_path / "runs-generated" / "run-1" / "run.jsonl"
    target.parent.mkdir(parents=True)
    target.write_text("\n".join([
        json.dumps({"node": "a2_design", "status": "ok", "payload": {"body": "private"}}),
        json.dumps({
            "node": "a3_web.llm_error",
            "status": "error",
            "payload": {"error": "APITimeoutError: HTTP status 504"},
        }),
    ]), encoding="utf-8")

    assert collect_market_failure_diagnostics(tmp_path) == {
        "stage": "COLLECT",
        "node": "a3_web.llm_error",
        "exceptionType": "APITimeoutError",
        "httpStatus": 504,
        "errorCode": None,
        "attempt": None,
        "adapter": "web",
    }


def test_missing_diagnostic_uses_constant_transient_fallback(tmp_path: Path):
    assert collect_market_failure_diagnostics(tmp_path) is None
    assert fallback_stderr_diagnostics(
        "ProviderFailure: TRANSIENT_EXECUTION_FAILURE"
    ) is None
    assert diagnostic_log_detail(None) == "시장조사 엔진 실패"


def test_traceback_fallback_keeps_exception_class_but_not_message_or_wrapper():
    diagnostic = fallback_stderr_diagnostics("""
Traceback (most recent call last):
openai.APIConnectionError: Bearer TESTSECRET prompt raw text
The above exception was the direct cause of the following exception:
app.providers.structured.ProviderFailure: TRANSIENT_EXECUTION_FAILURE
""")

    assert diagnostic["stage"] == "UNKNOWN"
    assert diagnostic["exceptionType"] == "APIConnectionError"
    serialized = json.dumps(diagnostic)
    assert "TESTSECRET" not in serialized
    assert "prompt raw text" not in serialized


def test_harness_secret_and_raw_text_never_enter_diagnostic_or_log_detail(tmp_path: Path):
    _harness_record(
        tmp_path,
        kind="멈춤 — LLM 호출 실패(RateLimitError)",
        detail=("Error code: 429 sk-TESTSECRET Authorization: Bearer TESTSECRET "
                "concept raw text prompt raw text response raw text"),
    )

    diagnostic = collect_market_failure_diagnostics(tmp_path)
    serialized = json.dumps(diagnostic)
    server_detail = diagnostic_log_detail(diagnostic)
    for forbidden in ("TESTSECRET", "Bearer", "concept raw", "prompt raw", "response raw"):
        assert forbidden not in serialized
        assert forbidden not in server_detail

    response = internal_error(
        "correlation", "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
        502, True, "run", "attempt", safe_diagnostics=diagnostic,
    )
    assert json.loads(response.body)["error"]["details"] == [{
        "reason": "TRANSIENT_EXECUTION_FAILURE",
        "diagnosticStage": "HARNESS_LLM",
    }]
    for forbidden in ("TESTSECRET", "Bearer", "concept raw", "prompt raw", "response raw"):
        assert forbidden not in response.body.decode("utf-8")


class _FailedProcess:
    returncode = 17

    async def communicate(self):
        return b"", b"ProviderFailure: TRANSIENT_EXECUTION_FAILURE\n"


def test_product_boundary_keeps_failure_semantics_and_attaches_safe_diagnostics(
        monkeypatch):
    async def create_subprocess(*command, **_kwargs):
        workspace = Path(command[command.index("--workspace") + 1])
        _harness_record(
            workspace,
            kind="멈춤 — LLM 호출 실패(RateLimitError)",
            detail="Error code: 429 sk-TESTSECRET prompt raw text",
        )
        return _FailedProcess()

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(product_pipeline._product_full(
            {"concept_name": "fixture"}, "concept-1", "run-observability",
            "2026-08-19", 500, 60.0,
        ))

    failure = raised.value
    assert failure.code == "EXECUTION_FAILED"
    assert failure.reason == "TRANSIENT_EXECUTION_FAILURE"
    assert failure.retryable is True
    assert failure.safe_diagnostics["stage"] == "HARNESS_LLM"
    assert failure.safe_diagnostics["exceptionType"] == "RateLimitError"
    assert failure.safe_diagnostics["httpStatus"] == 429
    assert "TESTSECRET" not in (failure.safe_provider_message or "")
    assert "prompt raw text" not in json.dumps(failure.safe_diagnostics)


def test_product_boundary_without_artifact_keeps_transient_fallback(monkeypatch):
    async def create_subprocess(*_command, **_kwargs):
        return _FailedProcess()

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(product_pipeline._product_full(
            {"concept_name": "fixture"}, "concept-1", "run-no-diagnostic",
            "2026-08-19", 500, 60.0,
        ))

    failure = raised.value
    assert failure.code == "EXECUTION_FAILED"
    assert failure.reason == "TRANSIENT_EXECUTION_FAILURE"
    assert failure.retryable is True
    assert failure.safe_diagnostics == {}
    assert failure.safe_provider_message == "시장조사 엔진 실패"


def test_collector_error_cannot_change_market_failure_semantics(monkeypatch):
    from app import market_failure_diagnostics

    async def create_subprocess(*_command, **_kwargs):
        return _FailedProcess()

    def collector_failure(_workspace):
        raise OSError("diagnostic storage unavailable")

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    monkeypatch.setattr(
        market_failure_diagnostics, "collect_market_failure_diagnostics", collector_failure,
    )
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(product_pipeline._product_full(
            {"concept_name": "fixture"}, "concept-1", "run-collector-error",
            "2026-08-19", 500, 60.0,
        ))

    failure = raised.value
    assert failure.code == "EXECUTION_FAILED"
    assert failure.reason == "TRANSIENT_EXECUTION_FAILURE"
    assert failure.retryable is True
    assert failure.safe_provider_message == "시장조사 엔진 실패"
