"""full canonical TaskRun payload adapter for the exact main advisory engine."""

from collections.abc import Awaitable, Callable
from typing import Any

from app.tasks.tech_ops_advisor.service import generate_tech_ops_advisory
from app.tasks.tech_ops_advisor.models import AdvisoryResult

ProgressSink = Callable[[str, str, dict[str, Any]], Awaitable[None]]


async def execute_tech_ops_advisory(
    payload: dict[str, Any], event_sink: ProgressSink | None = None,
) -> dict[str, Any]:
    """Adapt immutable full lineage input without changing the main engine body."""
    await _emit(event_sink, "SCALING", "job.tech-ops.advisory.scaling")
    concept = payload.get("conceptHandoff") or {}
    market = payload.get("marketResult") or {}
    business_model = payload.get("businessModelResult") or payload.get("bmResult") or {}
    confirmed_input = payload.get("techOpsInputSnapshot") or {}

    # main's engine accepts concept/market/bm/legal.  Preserve confirmed Phase A
    # input as an explicit BM-side operating context instead of dropping it.
    engine_input = {
        "concept": concept,
        "market": market,
        "bm": {
            "businessModel": business_model,
            "confirmedTechOpsInput": confirmed_input,
        },
        "legalHandoff": payload.get("legalHandoff"),
    }
    await _emit(event_sink, "EVIDENCE", "job.tech-ops.advisory.evidence")
    await _emit(event_sink, "GENERATING", "job.tech-ops.advisory.generating")
    result = await generate_tech_ops_advisory(engine_input)
    await _emit(event_sink, "VALIDATING", "job.tech-ops.advisory.validating")
    validated = AdvisoryResult.model_validate(result)
    if payload.get("legalHandoff") is None and not any(
        gate.status == "OPEN" and any(
            token in f"{gate.title} {gate.exitCriteria}" for token in ("법률", "규제", "개인정보", "계약")
        ) for gate in validated.gates
    ):
        raise ValueError("missing legal handoff requires an OPEN legal review gate")
    return validated.model_dump(mode="json")


async def _emit(sink: ProgressSink | None, stage: str, message_key: str) -> None:
    if sink is not None:
        await sink(stage, message_key, {})
