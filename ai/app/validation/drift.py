"""REFINEMENT_POLICY_V1 deterministic drift and evidence gate."""
from __future__ import annotations

import re
from typing import Any

FROZEN_FIELDS = (
    "sellerRole", "providerRole", "intermediaryRole", "transactionFlow", "paymentFlow",
    "personalDataUsage", "physicalActivities", "partnerRequirements",
    "qualificationRequirements", "advertisingClaims", "conceptName",
    "conceptDefinition", "coreValue", "operatingModel", "platformRole",
)
PRICE_TOLERANCE = 0.30
LIST_CHANGE_ALLOWANCE = 1
REFINABLE_FIELDS = {
    "price": "PRICE_BAND",
    "channels": "LIST_ADD_OR_SWAP",
    "differentiators": "LIST_ADD_OR_SWAP",
    "targetRegion": "NARROW_ONLY",
    "targetUsers": "NARROW_ONLY",
    "featureSet": "SUBSET_ONLY",
    "revenueModel": "STRUCTURE_ONLY",
}
FREE_WITH_EVIDENCE_FIELDS = ("preMarketSomShare", "preMarketSom")
FREE_BM_FIELDS = ("keyActivities", "keyResources", "keyPartners", "customerRelationships")
FIELD_ALIASES = {
    "TARGET_REGION": "targetRegion", "REVENUE_MODEL": "revenueModel", "PRICE": "price",
    "CHANNELS": "channels", "DIFFERENTIATORS": "differentiators",
    "PRE_MARKET_SOM_SHARE": "preMarketSomShare", "PRE_MARKET_SOM": "preMarketSom",
    "TARGET_USERS": "targetUsers", "FEATURE_SET": "featureSet",
}

_TOKEN = re.compile(r"[0-9A-Za-z가-힣]+")
_MONEY = re.compile(r"\d[\d,]*")
_LIST_SPLIT = re.compile(r"[,·]|\s/\s")


class DriftRejection(Exception):
    def __init__(self, field: str, reason: str) -> None:
        super().__init__(f"{field}: {reason}")
        self.field = field
        self.reason = reason


def canonical_field(field: str) -> str:
    return FIELD_ALIASES.get(field, field)


def tokens(value: Any) -> set[str]:
    text = " ".join(map(str, value)) if isinstance(value, (list, tuple)) else str(value or "")
    return {match.group(0).lower() for match in _TOKEN.finditer(text)}


def _amount(value: Any) -> float | None:
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return float(value)
    values = [float(item.replace(",", "")) for item in _MONEY.findall(str(value or ""))]
    return max(values) if values else None


def as_items(value: Any) -> list[str]:
    if isinstance(value, (list, tuple)):
        return [str(item).strip() for item in value if str(item).strip()]
    text = str(value or "")
    if not text.strip():
        return []
    masked: list[str] = []
    original: list[str] = []
    depth = 0
    for at, char in enumerate(text):
        if char in "([{":
            depth += 1
        elif char in ")]}":
            depth = max(0, depth - 1)
        thousands = char == "," and 0 < at < len(text) - 1 and text[at - 1].isdigit() and text[at + 1].isdigit()
        masked.append("\x00" if (depth > 0 or thousands) and char in ",·" else char)
        original.append(char)
    pieces: list[str] = []
    cursor = 0
    for part in _LIST_SPLIT.split("".join(masked)):
        piece = "".join(original[cursor:cursor + len(part)])
        cursor += len(part) + 1
        if piece.strip():
            pieces.append(piece.strip())
    return pieces


def check(field: str, current: Any, proposed: Any, frozen: dict[str, Any] | None = None) -> None:
    if field in FROZEN_FIELDS:
        if current != proposed:
            raise DriftRejection(field, "동결된 칸은 변경할 수 없다")
        return
    if field in FREE_WITH_EVIDENCE_FIELDS or field in FREE_BM_FIELDS:
        if field == "keyPartners" and tokens(proposed) & tokens((frozen or {}).get("partnerRequirements")):
            raise DriftRejection(field, "동결된 파트너 요건과 겹친다")
        return
    rule = REFINABLE_FIELDS.get(field)
    if rule is None:
        raise DriftRejection(field, "드리프트 계약에 없는 칸이다")
    if rule == "PRICE_BAND":
        before, after = _amount(current), _amount(proposed)
        if before is None or after is None or before <= 0:
            raise DriftRejection(field, "가격 변경 폭을 확인할 수 없다")
        if abs(after - before) / before > PRICE_TOLERANCE + 1e-9:
            raise DriftRejection(field, "원본 대비 ±30%를 넘는다")
    elif rule == "LIST_ADD_OR_SWAP":
        before, after = as_items(current), as_items(proposed)
        removed = len([item for item in before if item not in after])
        added = len([item for item in after if item not in before])
        if removed > LIST_CHANGE_ALLOWANCE or added > LIST_CHANGE_ALLOWANCE:
            raise DriftRejection(field, "한 번에 1개까지만 더하거나 바꿀 수 있다")
    elif rule == "NARROW_ONLY":
        fresh = tokens(proposed) - tokens(current)
        if fresh or not tokens(proposed):
            raise DriftRejection(field, "원본 안에서 좁히기만 할 수 있다")
    elif rule == "SUBSET_ONLY":
        if any(item not in as_items(current) for item in as_items(proposed)):
            raise DriftRejection(field, "원본 항목의 부분집합만 허용한다")
    elif rule == "STRUCTURE_ONLY":
        before, after = tokens(current), tokens(proposed)
        if before and not before & after:
            raise DriftRejection(field, "수익 모델 자체는 변경할 수 없다")


def filter_ungrounded(proposals: list[dict[str, Any]], evidence: list[dict[str, Any]],
                      allowed_legal_refs: list[str]) -> tuple[list[dict], list[dict]]:
    allowed = {str(item.get("id")) for item in evidence if item.get("id") is not None}
    legal_refs = {str(value) for value in allowed_legal_refs}
    passed: list[dict] = []
    rejected: list[dict] = []
    for proposal in proposals:
        source = str(proposal.get("source") or "MARKET")
        if source == "LEGAL":
            legal_ref = str(proposal.get("legalRef") or "").strip()
            if legal_ref and legal_ref in legal_refs:
                passed.append(proposal)
            else:
                rejected.append({**proposal, "rejectionReason": "확인 가능한 법률 근거가 없다"})
            continue
        ids = [str(value) for value in proposal.get("evidenceIds") or []]
        valid = [value for value in ids if value in allowed]
        if not valid:
            rejected.append({**proposal, "rejectionReason": "유효한 시장 근거가 0건이다"})
        else:
            passed.append({**proposal, "evidenceIds": valid})
    return passed, rejected


def filter_proposals(proposals: list[dict[str, Any]], current_values: dict[str, Any],
                     frozen_values: dict[str, Any]) -> tuple[list[dict], list[dict]]:
    passed: list[dict] = []
    rejected: list[dict] = []
    for raw in proposals:
        field = canonical_field(str(raw.get("fieldKey") or ""))
        proposal = {**raw, "fieldKey": field}
        if field not in current_values:
            rejected.append({**proposal, "rejectionReason": "현재 검증 baseline에 없는 칸이다"})
            continue
        if "proposedValue" not in proposal or proposal.get("proposedValue") is None:
            rejected.append({**proposal, "rejectionReason": "제안 값이 없다"})
            continue
        authoritative_current = current_values[field]
        if proposal.get("proposedValue") == authoritative_current:
            rejected.append({**proposal, "currentValue": authoritative_current,
                             "rejectionReason": "현재 값과 같은 제안이다"})
            continue
        try:
            check(field, authoritative_current, proposal.get("proposedValue"), frozen_values)
        except DriftRejection as failure:
            rejected.append({**proposal, "currentValue": authoritative_current,
                             "rejectionReason": failure.reason})
        else:
            passed.append({**proposal, "currentValue": authoritative_current})
    return passed, rejected
