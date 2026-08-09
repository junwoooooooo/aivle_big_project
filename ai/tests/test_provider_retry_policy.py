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
