"""Fail closed when an observed BMC claim has no market citation (LLM 0)."""
from __future__ import annotations

from ..research.bm.contracts import CanvasStatus
from .mapping import OBSERVED


def enforce(analysis):
    cells = []
    for item in analysis.canvas:
        cell = str(item.canvas_cell)
        if (cell in OBSERVED and item.status in (CanvasStatus.VERIFIED, CanvasStatus.PARTIAL)
                and not item.market_evidence_ids):
            cells.append(item.model_copy(update={
                "status": CanvasStatus.UNVERIFIED,
                "reason": (item.reason + " — 시장 근거 인용이 없어 확인 주장을 유지할 수 없다.").strip(),
                "missing_evidence": list(dict.fromkeys([
                    *item.missing_evidence, "이 칸을 뒷받침하는 시장 근거 인용"])),
            }))
        else:
            cells.append(item)
    return analysis.model_copy(update={"canvas": cells})
