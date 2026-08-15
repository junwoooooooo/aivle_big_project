from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone

import httpx

from app.tasks.concept_portfolio_v2.models import ProductionTraceEvent
from app.tasks.concept_portfolio_v2.progress_sender import ProductionProgressSender


def event(sequence: int) -> ProductionTraceEvent:
    return ProductionTraceEvent(
        sequence=sequence,
        stage="PLANNING",
        action="DRAFTS_GENERATED",
        status="PASS",
        safeSummary="실제 방향을 검토했습니다.",
        occurredAt=datetime.now(timezone.utc),
    )


def test_progress_sender_preserves_order_and_excludes_sensitive_fields():
    received = []

    async def post(payload):
        received.append(payload)

    async def scenario():
        async with ProductionProgressSender(
            task_run_id="run", task_attempt_id="attempt", correlation_id="correlation", post=post
        ) as sender:
            sender.emit(event(1))
            sender.emit(event(2))

    asyncio.run(scenario())
    assert [item["sequence"] for item in received] == [1, 2]
    assert all(item["taskRunId"] == "run" for item in received)
    forbidden = {"authorization", "prompt", "rawBody", "providerBody", "userInput"}
    assert all(forbidden.isdisjoint(item) for item in received)


def test_progress_callback_failure_does_not_escape_business_execution():
    async def failing_post(payload):
        raise OSError("callback unavailable")

    async def scenario():
        async with ProductionProgressSender(
            task_run_id="run", task_attempt_id="attempt", correlation_id="correlation",
            post=failing_post,
        ) as sender:
            sender.emit(event(1))
        return "business-result"

    assert asyncio.run(scenario()) == "business-result"


def test_progress_sender_uses_internal_header_and_logs_rejected_status(caplog):
    seen = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["internal"] = request.headers.get("X-AI-Internal-Token")
        seen["authorization"] = request.headers.get("Authorization")
        return httpx.Response(401, request=request)

    async def scenario():
        client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
        try:
            async with ProductionProgressSender(
                task_run_id="run", task_attempt_id="attempt", correlation_id="correlation",
                base_url="http://backend", token="service-secret", client=client,
            ) as sender:
                sender.emit(event(1))
        finally:
            await client.aclose()

    with caplog.at_level(logging.WARNING):
        asyncio.run(scenario())
    assert seen == {"internal": "service-secret", "authorization": None}
    assert "status=401" in caplog.text
    assert "service-secret" not in caplog.text
