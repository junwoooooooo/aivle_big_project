from __future__ import annotations

import threading

from app.research.product_runner import _ProgressHeartbeat


def test_market_collection_heartbeat_is_observable_without_fake_stage() -> None:
    observed: list[dict] = []
    emitted = threading.Event()

    class Progress:
        def emit(self, event: dict) -> None:
            observed.append(event)
            emitted.set()

    heartbeat = _ProgressHeartbeat(Progress(), interval_seconds=0.01)
    heartbeat.start()
    try:
        assert emitted.wait(timeout=1.0)
    finally:
        heartbeat.stop()

    assert observed
    assert {event["stage"] for event in observed} == {"MARKET_COLLECTION"}
    assert {event["action"] for event in observed} == {"HEARTBEAT"}
    assert {event["status"] for event in observed} == {"RUNNING"}


def test_market_collection_heartbeat_failure_is_best_effort() -> None:
    attempted = threading.Event()

    class BrokenProgress:
        def emit(self, _event: dict) -> None:
            attempted.set()
            raise OSError("progress unavailable")

    heartbeat = _ProgressHeartbeat(BrokenProgress(), interval_seconds=0.01)
    heartbeat.start()
    try:
        assert attempted.wait(timeout=1.0)
    finally:
        heartbeat.stop()
