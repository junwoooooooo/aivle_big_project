"""Deterministic Market evidence to observed BMC cell mapping (LLM 0)."""
from __future__ import annotations

from ..research.bm.contracts import CanvasStatus

OBSERVED = {"CUSTOMER_SEGMENTS", "VALUE_PROPOSITIONS", "CHANNELS", "REVENUE_STREAMS"}
SECTION_CELL = {
    "MARKET_SIZE": "CUSTOMER_SEGMENTS", "DEMAND": "VALUE_PROPOSITIONS",
    "CHANNEL": "CHANNELS", "PRICE": "REVENUE_STREAMS",
}
METRIC_CELL = {
    "문제 경험률": "VALUE_PROPOSITIONS", "채널·유통 조건": "CHANNELS",
    "가격·지불 조건": "REVENUE_STREAMS", "시장·카테고리 규모": "CUSTOMER_SEGMENTS",
}
CELL_LABEL = {
    "CUSTOMER_SEGMENTS": "market_size", "VALUE_PROPOSITIONS": "demand_evidence",
    "CHANNELS": "channel_analysis", "REVENUE_STREAMS": "price_analysis",
}


def apply(analysis, evidence: list[dict]):
    allowed = {str(item.get("id")) for item in evidence or [] if item.get("id")}
    grouped = {cell: [] for cell in OBSERVED}
    direct = {cell: 0 for cell in OBSERVED}
    for item in evidence or []:
        cell = SECTION_CELL.get(item.get("section")) or METRIC_CELL.get(item.get("metric"))
        item_id = str(item.get("id") or "")
        if cell not in grouped or not item_id or item_id in grouped[cell]:
            continue
        grouped[cell].append(item_id)
        if not item_id.startswith("C-SEC-"):
            direct[cell] += 1
    cells = []
    for item in analysis.canvas:
        cell = str(item.canvas_cell)
        if cell not in grouped:
            cells.append(item)
            continue
        ids = grouped[cell] or [item_id for item_id in item.market_evidence_ids if item_id in allowed]
        labels = [CELL_LABEL[cell]] if ids else [
            label for label in item.source_labels if label in {"concept_snapshot", "execution_constraints"}]
        update = {"market_evidence_ids": ids, "source_labels": labels or ["concept_snapshot"]}
        if item.status != CanvasStatus.BLOCKED:
            update["status"] = (CanvasStatus.VERIFIED if direct[cell] >= 2 else
                                CanvasStatus.PARTIAL if direct[cell] else CanvasStatus.UNVERIFIED)
        cells.append(item.model_copy(update=update))
    return analysis.model_copy(update={"canvas": cells})
