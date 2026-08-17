import asyncio
import json
import logging
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.canonical_json import canonical_input_hash
from app.providers import ProviderFailure
from app.research import product_pipeline, product_runner


class _ChildProcess:
    def __init__(self, returncode: int, stderr: str = ""):
        self.returncode = returncode
        self._stderr = stderr.encode("utf-8")

    async def communicate(self):
        return b"", self._stderr


def _run_child(monkeypatch, tmp_path: Path, failure: Exception) -> dict:
    input_path = tmp_path / "input.json"
    output_path = tmp_path / "output.json"
    error_path = tmp_path / "error.json"
    workspace = tmp_path / "workspace"
    progress_path = tmp_path / "progress.jsonl"
    input_path.write_text(json.dumps({"concept_name": "safe concept"}), encoding="utf-8")
    workspace.mkdir()

    async def fail_market(*_args, **_kwargs):
        raise failure

    monkeypatch.setattr("app.research.pipeline.run_market_research", fail_market)
    monkeypatch.setattr("app.research.research2.runpath.exists", lambda _run_id: False)
    monkeypatch.setenv("RESEARCH2_RUNS_DIR", str(tmp_path / "previous-runs"))
    monkeypatch.setenv("RESEARCH2_GENERATED_RUNS_DIR", str(tmp_path / "previous-generated"))
    monkeypatch.setattr(sys, "argv", [
        "product_runner.py",
        "--input", str(input_path),
        "--output", str(output_path),
        "--error-output", str(error_path),
        "--workspace", str(workspace),
        "--run-id", "run-child-error",
        "--concept-id", "concept-child-error",
        "--as-of", "2026-08-17",
        "--progress-jsonl", str(progress_path),
    ])

    with pytest.raises(SystemExit) as exited:
        product_runner.main()

    assert exited.value.code == 1
    assert not output_path.exists()
    return json.loads(error_path.read_text(encoding="utf-8"))


def _raise_from_parent(monkeypatch, envelope: dict, stderr: str = "traceback fallback") -> ProviderFailure:
    async def create_subprocess(*command, **_kwargs):
        error_path = Path(command[command.index("--error-output") + 1])
        error_path.write_text(json.dumps(envelope, ensure_ascii=False), encoding="utf-8")
        return _ChildProcess(1, stderr)

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(product_pipeline._product_full(
            {"concept_name": "safe concept"}, "concept-1", "run-parent-error",
            "2026-08-17", 3, 30.0,
        ))
    return raised.value


def test_child_provider_failure_preserves_harness_diagnostics_in_parent(monkeypatch, tmp_path):
    envelope = _run_child(monkeypatch, tmp_path, ProviderFailure(
        "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True,
        upstream_status=504,
        provider_error_type="APITimeoutError",
        provider_error_param="responses.web_search",
        schema_name="market_harness_v1",
        safe_provider_message="provider request exceeded its safe deadline",
        safe_diagnostics={"stage": "harness", "detail": "APITimeoutError"},
    ))

    failure = _raise_from_parent(monkeypatch, envelope)

    assert failure.code == "DEPENDENCY_UNAVAILABLE"
    assert failure.reason == "MODEL_DEPENDENCY_UNAVAILABLE"
    assert failure.retryable is True
    assert failure.upstream_status == 504
    assert failure.provider_error_type == "APITimeoutError"
    assert failure.provider_error_param == "responses.web_search"
    assert failure.schema_name == "market_harness_v1"
    assert failure.safe_provider_message == "provider request exceeded its safe deadline"
    assert failure.safe_diagnostics == {"stage": "harness", "detail": "APITimeoutError"}
    assert failure.safe_diagnostics["detail"] != (
        "app.providers.structured.ProviderFailure: TRANSIENT_EXECUTION_FAILURE"
    )


def test_child_rate_limit_preserves_status_and_retry_after(monkeypatch):
    envelope = product_runner.provider_failure_envelope(ProviderFailure(
        "RATE_LIMITED", "DEPENDENCY_RATE_LIMITED", 429, True,
        upstream_status=429, retry_after_ms=12_000,
        safe_diagnostics={"stage": "collect", "detail": "provider rate limit"},
    ))

    failure = _raise_from_parent(monkeypatch, envelope)

    assert failure.code == "RATE_LIMITED"
    assert failure.reason == "DEPENDENCY_RATE_LIMITED"
    assert failure.status_code == 429
    assert failure.upstream_status == 429
    assert failure.retry_after_ms == 12_000


def test_child_unexpected_exception_preserves_class_stage_and_safe_message(monkeypatch):
    envelope = product_runner.unexpected_failure_envelope(
        RuntimeError("Research2 harness process exited before collection"), "harness",
    )

    failure = _raise_from_parent(monkeypatch, envelope)

    assert failure.code == "EXECUTION_FAILED"
    assert failure.reason == "TRANSIENT_EXECUTION_FAILURE"
    assert failure.safe_diagnostics == {
        "stage": "harness",
        "exceptionClass": "RuntimeError",
        "detail": "Research2 harness process exited before collection",
    }


def test_child_envelope_and_parent_log_redact_secrets_and_payloads(monkeypatch, caplog):
    secret = "sk-proj-child-secret-123456"
    envelope = product_runner.provider_failure_envelope(ProviderFailure(
        "DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True,
        safe_provider_message=f"Authorization: Bearer auth-secret {secret}",
        safe_diagnostics={
            "stage": "harness",
            "detail": f"OPENAI_API_KEY={secret} prompt=raw-customer-prompt",
            "prompt": "raw-customer-prompt",
            "providerResponse": {"content": "raw-provider-response"},
        },
    ))
    serialized = json.dumps(envelope, ensure_ascii=False)
    assert secret not in serialized
    assert "auth-secret" not in serialized
    assert "raw-customer-prompt" not in serialized
    assert "raw-provider-response" not in serialized

    parent_failure = _raise_from_parent(monkeypatch, envelope)
    from app.research import product_pipeline as pipeline
    from main import app

    async def fail_market(*_args, **_kwargs):
        raise parent_failure

    monkeypatch.setattr(pipeline, "run_market_research", fail_market)
    token = "child-transport-test-token"
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", token)
    task_input = {"mode": "FULL", "conceptId": "concept-1"}
    body = {
        "contractVersion": "1.0",
        "taskType": "MARKET_RESEARCH",
        "taskSchemaVersion": "1.0",
        "taskRunId": "run-child-transport",
        "taskAttemptId": "attempt-child-transport",
        "correlationId": "corr-child-transport",
        "deadlineAt": (datetime.now(timezone.utc) + timedelta(minutes=5))
        .isoformat(timespec="seconds").replace("+00:00", "Z"),
        "locale": "ko-KR",
        "input": task_input,
        "canonicalInputHash": canonical_input_hash(
            contract_version="1.0", task_type="MARKET_RESEARCH",
            task_schema_version="1.0", locale="ko-KR", input_value=task_input,
        ),
    }
    with caplog.at_level(logging.WARNING, logger="app.api.executions"):
        response = TestClient(app).post(
            "/internal/v1/ai/executions", json=body,
            headers={"Authorization": f"Bearer {token}",
                     "X-Correlation-Id": body["correlationId"]},
        )

    assert response.status_code == 503
    assert secret not in caplog.text
    assert "raw-customer-prompt" not in caplog.text
    assert "raw-provider-response" not in caplog.text
    assert "[REDACTED]" in caplog.text
    assert "safeDiagnostics" not in response.text


def test_parent_success_path_returns_child_market_result_unchanged(monkeypatch):
    expected = {
        "runId": "run-parent-success",
        "mode": "FULL",
        "market": {"tam": None, "sam": None, "som": None},
        "stages": [{"name": "harness", "status": "COMPLETED"}],
    }

    async def create_subprocess(*command, **_kwargs):
        output_path = Path(command[command.index("--output") + 1])
        output_path.write_text(json.dumps(expected, ensure_ascii=False), encoding="utf-8")
        return _ChildProcess(0)

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    actual = asyncio.run(product_pipeline._product_full(
        {"concept_name": "safe concept"}, "concept-1", "run-parent-success",
        "2026-08-17", 3, 30.0,
    ))

    assert actual == expected
