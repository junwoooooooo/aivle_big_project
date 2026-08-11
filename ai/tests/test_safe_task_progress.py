import asyncio

from app.progress.safe_task_progress import SafeTaskProgressSender


def test_sender_sequences_safe_events_monotonically():
    sent = []

    async def post(payload):
        sent.append(payload)

    async def run():
        async with SafeTaskProgressSender(
            task_run_id="run", task_attempt_id="attempt", correlation_id="correlation", post=post,
        ) as sender:
            sender.emit({"stage": "A1", "action": "COMPLETED", "status": "RUNNING",
                         "safeSummary": "공식 3개, 슬롯 9개"})
            sender.emit({"stage": "A2", "action": "COMPLETED", "status": "RUNNING",
                         "safeSummary": "경로 9개"})

    asyncio.run(run())
    assert [event["sequence"] for event in sent] == [1, 2]
    assert all(set(event) <= {
        "taskRunId", "taskAttemptId", "correlationId", "sequence", "stage", "action",
        "status", "safeSummary", "occurredAt", "reasonCode", "decision"
    } for event in sent)


def test_callback_failure_never_escapes_analysis_context():
    async def broken(_payload):
        raise RuntimeError("callback unavailable")

    async def run():
        async with SafeTaskProgressSender(
            task_run_id="run", task_attempt_id="attempt", correlation_id="correlation",
            post=broken, callback_timeout_seconds=0.05,
        ) as sender:
            sender.emit({"stage": "TWIN_GATE", "action": "COMPLETED", "status": "RUNNING",
                         "safeSummary": "검증 완료"})
        return "analysis-result"

    assert asyncio.run(run()) == "analysis-result"


def test_sender_drops_non_contract_fields_including_sensitive_material():
    sent = []

    async def post(payload):
        sent.append(payload)

    async def run():
        async with SafeTaskProgressSender(
            task_run_id="run", task_attempt_id="attempt", correlation_id="correlation", post=post,
        ) as sender:
            sender.emit({
                "stage": "MARKET_A3", "action": "PROGRESS", "status": "RUNNING",
                "safeSummary": "완료 슬롯 3/10", "prompt": "raw prompt",
                "providerBody": "raw provider response", "evidence": "complete evidence",
                "pid_hash": "respondent-id", "cardText": "bank card body",
            })

    asyncio.run(run())
    encoded = str(sent[0])
    for forbidden in ("raw prompt", "raw provider response", "complete evidence",
                      "respondent-id", "bank card body"):
        assert forbidden not in encoded
