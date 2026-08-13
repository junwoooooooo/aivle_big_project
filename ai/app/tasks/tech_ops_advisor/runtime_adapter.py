"""full canonical TaskRun payload adapter for the exact main advisory engine."""

from collections.abc import Callable
from typing import Any

from app.tasks.tech_ops_advisor.service import generate_tech_ops_advisory
from app.tasks.tech_ops_advisor.models import AdvisoryResult

ProgressEvent = dict[str, Any]
ProgressSink = Callable[[ProgressEvent], None]

_PROGRESS_SUMMARIES = {
    "SCALING": "기술·운영 입력과 상위 모듈 근거를 정리하고 있습니다.",
    "EVIDENCE": "기술·운영 자문에 사용할 근거를 확인하고 있습니다.",
    "GENERATING": "기술·운영 상용화 자문을 생성하고 있습니다.",
    "VALIDATING": "기술·운영 자문 결과 계약을 검증하고 있습니다.",
}


async def execute_tech_ops_advisory(
    payload: dict[str, Any], event_sink: ProgressSink | None = None,
) -> dict[str, Any]:
    """Adapt immutable full lineage input without changing the main engine body."""
    _emit(event_sink, "SCALING")
    concept = payload.get("conceptHandoff") or {}
    market = payload.get("marketResult") or {}
    business_model = payload.get("businessModelResult") or payload.get("bmResult") or {}
    confirmed_input = payload.get("techOpsInputSnapshot") or {}

    # The exact main engine accepts only concept/market/bm/legal. Keep the user
    # snapshot in the accepted `bm` envelope, but give its path an unmistakable
    # provenance boundary. The result post-processor restores its public source
    # and decision status without changing the engine body.
    engine_input = {
        "concept": concept,
        "market": market,
        "bm": {
            "businessModel": business_model,
            "USER_CONFIRMED_TECH_OPS": confirmed_input,
        },
        "legalHandoff": payload.get("legalHandoff"),
    }
    _emit(event_sink, "EVIDENCE")
    _emit(event_sink, "GENERATING")
    result = await generate_tech_ops_advisory(engine_input)
    _emit(event_sink, "VALIDATING")
    validated = AdvisoryResult.model_validate(result)
    if payload.get("legalHandoff") is None and not any(
        gate.status == "OPEN" and any(
            token in f"{gate.title} {gate.exitCriteria}" for token in ("법률", "규제", "개인정보", "계약")
        ) for gate in validated.gates
    ):
        raise ValueError("missing legal handoff requires an OPEN legal review gate")
    output = validated.model_dump(mode="json")
    _restore_confirmed_tech_ops_provenance(output)
    return output


def _restore_confirmed_tech_ops_provenance(result: dict[str, Any]) -> None:
    """Facts cited by advice keep their IDs while regaining user-input authority."""
    for fact in result.get("layer1Facts") or []:
        if not isinstance(fact, dict):
            continue
        path = str(fact.get("path") or "")
        if path.startswith("BM.USER_CONFIRMED_TECH_OPS."):
            fact["source"] = "TECH_OPS"
            fact["status"] = "USER_CONFIRMED_OR_ACCEPTED"


def _emit(sink: ProgressSink | None, stage: str) -> None:
    if sink is not None:
        sink({
            "stage": stage,
            "action": "UPDATED",
            "status": "RUNNING",
            "safeSummary": _PROGRESS_SUMMARIES[stage],
        })
