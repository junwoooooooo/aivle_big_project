import asyncio
import logging
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

from app.canonical_json import canonical_input_hash
from app.providers import ProviderFailure
from app.research import runner


class _FailedProcess:
    def __init__(self, stderr: str):
        self.returncode = 17
        self._stderr = stderr.encode("utf-8")

    async def communicate(self):
        return b"", self._stderr


def _run_failed_subprocess(monkeypatch, stderr: str) -> ProviderFailure:
    from app.research.research2 import runpath

    monkeypatch.setattr(runpath, "exists", lambda run_id: run_id == "source-run")

    async def create_subprocess(*_args, **_kwargs):
        return _FailedProcess(stderr)

    monkeypatch.setattr(asyncio, "create_subprocess_exec", create_subprocess)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(runner.execute_market_research(
            {"sourceRun": "source-run"}, "new-attempt", 5.0,
        ))
    return raised.value


def test_subprocess_stderr_is_preserved_as_bounded_safe_diagnostics(monkeypatch):
    failure = _run_failed_subprocess(
        monkeypatch,
        "httpx.ReadTimeout: upstream connection exceeded 60 seconds",
    )

    assert failure.code == "EXECUTION_FAILED"
    assert failure.reason == "TRANSIENT_EXECUTION_FAILURE"
    assert failure.safe_diagnostics == {
        "component": "market-research",
        "detail": "엔진 종료코드 17: httpx.ReadTimeout: upstream connection exceeded 60 seconds",
    }


def test_subprocess_stderr_redacts_secrets_and_raw_payloads(monkeypatch):
    secret = "sk-proj-secretvalue123456"
    failure = _run_failed_subprocess(
        monkeypatch,
        f"Authorization: Bearer top-secret {secret} OPENAI_API_KEY={secret} "
        'prompt="raw user document" request={"content":"private"}',
    )

    detail = failure.safe_diagnostics["detail"]
    assert secret not in detail
    assert "top-secret" not in detail
    assert "raw user document" not in detail
    assert "private" not in detail
    assert detail.count("[REDACTED]") >= 3
    assert len(detail) <= 600


def test_execution_logger_includes_only_safe_market_diagnostics(monkeypatch, caplog):
    token = "market-diagnostics-token"
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", token)
    from app.research import product_pipeline
    from main import app

    async def fail_market(*_args, **_kwargs):
        raise ProviderFailure(
            "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", 502, True,
            safe_diagnostics={
                "component": "market-research",
                "detail": "httpx.ReadTimeout after 60 seconds",
            },
        )

    monkeypatch.setattr(product_pipeline, "run_market_research", fail_market)
    task_input = {"mode": "FULL", "conceptId": "concept-1"}
    body = {
        "contractVersion": "1.0",
        "taskType": "MARKET_RESEARCH",
        "taskSchemaVersion": "1.0",
        "taskRunId": "run-market-diagnostics",
        "taskAttemptId": "attempt-market-diagnostics",
        "correlationId": "corr-market-diagnostics",
        "deadlineAt": (datetime.now(timezone.utc) + timedelta(minutes=5))
        .isoformat(timespec="seconds").replace("+00:00", "Z"),
        "locale": "ko-KR",
        "input": task_input,
        "canonicalInputHash": canonical_input_hash(
            contract_version="1.0",
            task_type="MARKET_RESEARCH",
            task_schema_version="1.0",
            locale="ko-KR",
            input_value=task_input,
        ),
    }

    with caplog.at_level(logging.WARNING, logger="app.api.executions"):
        response = TestClient(app).post(
            "/internal/v1/ai/executions",
            json=body,
            headers={
                "Authorization": f"Bearer {token}",
                "X-Correlation-Id": body["correlationId"],
            },
        )

    assert response.status_code == 502
    assert response.json()["error"]["details"] == [
        {"reason": "TRANSIENT_EXECUTION_FAILURE"}
    ]
    assert "safeDiagnostics={'component': 'market-research', " in caplog.text
    assert "httpx.ReadTimeout after 60 seconds" in caplog.text

