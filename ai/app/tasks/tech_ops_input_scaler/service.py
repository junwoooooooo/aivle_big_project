"""Market/BM/TechOps Snapshot을 손실성 LLM 요약 없이 근거 ledger로 변환한다."""

import json
from typing import Any, Iterator

from app.tasks.tech_ops_input_scaler.models import ScaledTechOpsInput, TechOpsAdvisoryInput

MAX_VALUE = 420
RELEVANCE = ("tam", "sam", "som", "growth", "price", "demand", "compet", "customer",
             "revenue", "cost", "margin", "channel", "partner", "operation", "risk", "시장",
             "성장", "가격", "수요", "경쟁", "고객", "수익", "비용", "채널", "운영", "공급")
TRACE = ("runid", "createdat", "generatedat", ".seconds", ".status", "llmcalls", "trace")


def _walk(value: Any, path: str = "") -> Iterator[tuple[str, Any]]:
    if isinstance(value, dict):
        for key, item in value.items():
            yield from _walk(item, f"{path}.{key}" if path else str(key))
    elif isinstance(value, list):
        for index, item in enumerate(value[:30]):
            yield from _walk(item, f"{path}[{index}]")
    else:
        yield path, value


def _text(value: Any) -> str:
    raw = value.strip() if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
    return raw[:MAX_VALUE] + ("…" if len(raw) > MAX_VALUE else "")


def _facts(source: str, payload: Any, start: int, limit: int, status: str) -> list[dict[str, Any]]:
    rows = []
    for path, value in _walk(payload):
        if value in (None, "", [], {}) or isinstance(value, bool):
            continue
        rows.append({"factId": f"FACT-{start + len(rows):03d}", "path": f"{source}.{path}",
                     "value": _text(value), "source": source, "evidenceLevel": 1, "status": status})
        if len(rows) >= limit:
            break
    return rows


def _rank(row: dict[str, Any]) -> tuple[int, str]:
    text = f"{row['path']} {row['value']}".lower()
    return (sum(marker in text for marker in TRACE) * 10 - sum(marker in text for marker in RELEVANCE), row["factId"])


def _urls(*payloads: tuple[str, Any]) -> list[dict[str, Any]]:
    rows, seen = [], set()
    for source, payload in payloads:
        for path, value in _walk(payload):
            if not isinstance(value, str) or not value.startswith(("https://", "http://")) or value in seen:
                continue
            seen.add(value)
            rows.append({"evidenceId": f"EXT-{len(rows)+1:03d}", "title": path.rsplit(".", 1)[-1],
                         "url": value, "source": f"{source}.{path}", "evidenceLevel": 2,
                         "status": "UPSTREAM_SOURCE"})
            if len(rows) >= 20:
                return rows
    return rows


def _product(concept: Any, tech: Any) -> str:
    for payload in (concept, tech):
        for path, value in _walk(payload):
            if any(key in path.lower() for key in ("conceptname", "productname", "summary", "name")) and value:
                return _text(value)
    return "current Concept와 Market/BM에 연결된 상용화 검증 대상"


async def scale_tech_ops_input(payload: dict[str, Any]) -> dict[str, Any]:
    value = TechOpsAdvisoryInput.model_validate(payload)
    market = _facts("MARKET", value.marketResult, 1, 60, "UPSTREAM_ANALYSIS")
    bm = _facts("BM", value.businessModelResult, 61, 60, "UPSTREAM_ANALYSIS")
    tech = _facts("TECH_OPS", value.techOpsInputSnapshot, 121, 40, "USER_CONFIRMED_OR_ACCEPTED")
    concept = _facts("CONCEPT_LEGAL", {"concept": value.conceptHandoff, "legal": value.legalHandoff},
                     161, 20, "CANONICAL_HANDOFF")
    facts = market + bm + tech + concept
    advisor = sorted(market, key=_rank)[:30] + sorted(bm, key=_rank)[:30]
    advisor += sorted(tech + concept, key=_rank)[:30]
    user_evidence = list(value.techOpsInputSnapshot.get("evidenceReferences") or [])[:40]
    scaled = ScaledTechOpsInput(productSummary=_product(value.conceptHandoff, value.techOpsInputSnapshot),
        layer1Facts=facts, advisorFacts=advisor,
        layer2Evidence=_urls(("MARKET", value.marketResult), ("BM", value.businessModelResult)),
        userEvidence=user_evidence)
    return scaled.model_dump(mode="json")
