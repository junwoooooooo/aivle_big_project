"""Deterministic BM decision ceiling and actionable gate causes (LLM 0)."""
from __future__ import annotations

OBSERVED_CELLS = ("CUSTOMER_SEGMENTS", "VALUE_PROPOSITIONS", "CHANNELS", "REVENUE_STREAMS")
PLANNED_CELLS = ("CUSTOMER_RELATIONSHIPS", "KEY_RESOURCES", "KEY_ACTIVITIES", "KEY_PARTNERS",
                 "COST_STRUCTURE")
CELL_SUBJECT = {"CUSTOMER_SEGMENTS": "MARKET_SIZE", "VALUE_PROPOSITIONS": "DEMAND",
                "CHANNELS": "CHANNEL", "REVENUE_STREAMS": "PRICE"}
RANK = {"PASS": 0, "CONDITIONAL": 1, "REVISION_REQUIRED": 2, "BLOCKED": 3}
CEILING = {"G1": "REVISION_REQUIRED", "G4": "CONDITIONAL", "G5": "CONDITIONAL"}


def _cause(subject, states, found=False):
    if found:
        return "UNCITED"
    if subject not in states:
        return "UNMAPPED"
    return "UNCOLLECTED" if states[subject] == "MISSING" else "UNCITED"


def evaluate(cells: list[dict], scorecard: list[dict] | None = None) -> list[dict]:
    by_cell = {cell.get("canvasCell"): cell for cell in cells}
    states = {row.get("subject"): row.get("state") for row in scorecard or []}
    reasons = []
    for name in OBSERVED_CELLS:
        cell = by_cell.get(name)
        if not cell or cell.get("status") == "BLOCKED":
            continue
        ids = list(cell.get("marketEvidenceIds") or [])
        if ids and cell.get("status") in ("VERIFIED", "PARTIAL"):
            continue
        reasons.append({"code": "G1", "cell": name,
                        "message": ("시장 근거 0건." if not ids else
                                    f"참고 근거 {len(ids)}건은 있지만 이 칸을 확인하지 못했다."),
                        "evidenceIds": ids,
                        "cause": _cause(CELL_SUBJECT.get(name), states, bool(ids))})
    if all((by_cell.get(name) or {}).get("status") not in ("VERIFIED", "PARTIAL")
           for name in PLANNED_CELLS):
        reasons.append({"code": "G4", "cell": None,
                        "message": "계획 5칸 전부 관측 미달.", "evidenceIds": [], "cause": "UNMAPPED"})
    if not any("demand_evidence" in (cell.get("sourceLabels") or []) for cell in cells):
        revenue = by_cell.get("REVENUE_STREAMS") or {}
        reasons.append({"code": "G5", "cell": "REVENUE_STREAMS",
                        "message": "수요 근거 인용 0건.",
                        "evidenceIds": list(revenue.get("marketEvidenceIds") or []),
                        "cause": _cause("DEMAND", states)})
    return reasons


def apply_decision(decision: str, reasons: list[dict]) -> str:
    rank = RANK[decision]
    for reason in reasons:
        rank = max(rank, RANK[CEILING[reason["code"]]])
    return next(name for name, value in RANK.items() if value == rank)
