import json

from app.api.executions import internal_error
from app.providers.structured import _retry_after_ms


class Response:
    def __init__(self, retry_after: str):
        self.headers = {"Retry-After": retry_after}


def test_retry_after_seconds_is_safely_bounded():
    assert _retry_after_ms(Response("7")) == 7_000
    assert _retry_after_ms(Response("999")) == 15_000
    assert _retry_after_ms(Response("invalid")) is None


def test_internal_error_carries_only_safe_retry_after_metadata():
    response = internal_error("correlation", "RATE_LIMITED", "DEPENDENCY_RATE_LIMITED",
                              429, True, "run", "attempt", retry_after_ms=7_000)
    body = json.loads(response.body)
    assert body["error"]["details"] == [{
        "reason": "DEPENDENCY_RATE_LIMITED", "retryAfterMs": 7_000,
    }]


def test_internal_error_carries_only_allowlisted_diagnostic_stage():
    response = internal_error(
        "correlation", "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
        400, False, "run", "attempt",
        validation_fields=[{
            "path": "sourceManifest.0.type", "category": "missing",
            "expectedType": "required",
        }],
        safe_diagnostics={"stage": "INPUT_CONTRACT_VALIDATION", "raw": "must-not-cross"},
    )
    body = json.loads(response.body)
    detail = body["error"]["details"][0]
    assert detail["diagnosticStage"] == "INPUT_CONTRACT_VALIDATION"
    assert detail["fields"][0]["path"] == "sourceManifest.0.type"
    assert "raw" not in detail


def test_internal_error_drops_non_allowlisted_diagnostic_stage_and_raw_detail():
    response = internal_error(
        "correlation", "EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
        502, True, "run", "attempt",
        safe_diagnostics={
            "stage": "harness gate/raw",
            "detail": "HARNESS_X must remain server-side",
        },
    )
    body = json.loads(response.body)

    assert body["error"]["details"] == [{"reason": "TRANSIENT_EXECUTION_FAILURE"}]
    assert "HARNESS_X" not in response.body.decode("utf-8")
