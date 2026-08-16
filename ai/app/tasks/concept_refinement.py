"""One-call proposal generation for REFINE_FROM_MARKET; deterministic gates run elsewhere."""
from __future__ import annotations

import json
from typing import Any

from app.providers import execute_structured_prompt

MAX_PROPOSALS = 6
SYSTEM = """당신은 검증된 시장·BM·법률 근거로 현재 사업안을 좁게 다듬는 분석가다.
동결된 필드는 변경하지 말고 refinableFields에 있는 키만 사용한다.
목록 필드는 한 번에 한 항목만 추가하거나 교체하고 가격은 원본 대비 ±30% 안에서 제안한다.
MARKET 제안에는 제공된 marketEvidence의 실제 id를, LEGAL 제안에는 제공된 legalFindings의 실제 legalRef를 사용한다.
고칠 것이 없으면 빈 배열을 반환한다. 출력은 JSON 하나다:
{"proposals":[{"fieldKey":"...","currentValue":null,"proposedValue":null,"title":"...",
"beforeText":"...","afterText":"...","rationale":"...","source":"MARKET",
"evidenceIds":["..."],"legalRef":null}]}"""


async def propose_refinements(material: dict[str, Any], concept: dict[str, Any]) -> list[dict[str, Any]]:
    user = json.dumps({
        "concept": concept,
        "round": material.get("round", 1),
        "policyVersion": material.get("policyVersion"),
        "maxProposals": material.get("maxProposals", MAX_PROPOSALS),
        "priceTolerance": material.get("priceTolerance", 0.30),
        "listChangeAllowance": material.get("listChangeAllowance", 1),
        "frozenFields": material.get("frozenFields") or [],
        "refinableFields": material.get("refinableFields") or {},
        "gateReasons": material.get("gateReasons") or [],
        "canvas": material.get("canvas"),
        "marketEvidence": material.get("marketEvidence") or [],
        "legalFindings": material.get("legalFindings") or [],
        "previouslyRejectedByContract": material.get("driftRejections") or [],
        "previouslyDeclinedByUser": material.get("userDeclined") or [],
    }, ensure_ascii=False, sort_keys=True)
    payload = await execute_structured_prompt(system=SYSTEM, user=user, task_type="REFINE_FROM_MARKET")
    proposals = payload.get("proposals")
    if not isinstance(proposals, list):
        return []
    return [item for item in proposals[:MAX_PROPOSALS] if isinstance(item, dict)]
