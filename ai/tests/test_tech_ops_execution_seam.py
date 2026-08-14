from __future__ import annotations

from datetime import datetime, timedelta, timezone

from fastapi.testclient import TestClient

from app.canonical_json import canonical_input_hash
from app.progress.safe_task_progress import SafeTaskProgressSender
from app.tasks.tech_ops_advisor import runtime_adapter
from app.progress import safe_task_progress
from test_tech_ops_advisory import result, task_input


TOKEN = "tech-ops-seam-token"


def _body() -> dict:
    value = {
        "contractVersion": "1.0", "taskType": "TECH_OPS_ADVISORY",
        "taskSchemaVersion": "1.0", "taskRunId": "tech-run-1",
        "taskAttemptId": "tech-attempt-1", "correlationId": "tech-correlation-1",
        "deadlineAt": (datetime.now(timezone.utc) + timedelta(minutes=5))
            .isoformat().replace("+00:00", "Z"),
        "locale": "ko-KR", "input": task_input(),
    }
    value["canonicalInputHash"] = canonical_input_hash(
        contract_version="1.0", task_type=value["taskType"],
        task_schema_version="1.0", locale="ko-KR", input_value=value["input"],
    )
    return value


def _post(client: TestClient):
    body = _body()
    return client.post(
        "/internal/v1/ai/executions", json=body,
        headers={"Authorization": f"Bearer {TOKEN}",
                 "X-Correlation-Id": body["correlationId"]},
    )


def test_internal_execution_uses_actual_safe_sender_emit_signature(monkeypatch):
    observed = []

    async def post_progress(payload):
        observed.append(payload)

    def sender_factory(**kwargs):
        return SafeTaskProgressSender(**kwargs, post=post_progress)

    async def engine(_payload):
        return result()

    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    monkeypatch.setattr(safe_task_progress, "progress_sender_from_environment", sender_factory)
    monkeypatch.setattr(runtime_adapter, "generate_tech_ops_advisory", engine)
    from main import app
    with TestClient(app) as client:
        response = _post(client)

    assert response.status_code == 200, response.text
    assert response.json()["result"]["decision"] == "CONDITIONAL_GO"
    assert [event["stage"] for event in observed] == [
        "SCALING", "EVIDENCE", "GENERATING", "VALIDATING",
    ]


def test_runtime_adapter_programming_error_maps_to_internal_error(monkeypatch):
    async def broken(*_args, **_kwargs):
        raise TypeError("programming contract mismatch")

    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    monkeypatch.delenv("BACKEND_INTERNAL_BASE_URL", raising=False)
    monkeypatch.setattr(runtime_adapter, "execute_tech_ops_advisory", broken)
    from main import app
    with TestClient(app) as client:
        response = _post(client)

    assert response.status_code == 500
    error = response.json()["error"]
    assert error["code"] == "INTERNAL_ERROR"
    assert error["details"] == [{"reason": "UNEXPECTED_INTERNAL_ERROR"}]
