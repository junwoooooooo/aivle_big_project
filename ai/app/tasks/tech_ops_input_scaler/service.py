"""Deterministic Market/BM -> commercialization evidence handoff.

This component never asks an LLM to summarize upstream analysis.  It converts
the Market/BM payload into immutable, traceable facts before the advisor is
allowed to interpret them.
"""

from __future__ import annotations

import json
from typing import Any, Iterator

from app.tasks.tech_ops_input_scaler.models import (
    ConfirmedFact,
    ExternalEvidence,
    MarketSignal,
    TechOpsCommercializationInput,
)

MAX_FACTS = 150
MAX_FACTS_PER_SOURCE = MAX_FACTS // 2
MAX_VALUE_CHARS = 360
MAX_SIGNALS = 24
MAX_EVIDENCE = 20

_TRACE_MARKERS = (
    "runid", "conceptid", "generatedat", "createdat", "asof", "stages[",
    ".status", ".seconds", ".llmcalls", ".mode", "trace", "requestid",
)
_COMMERCIALIZATION_MARKERS = (
    "tam", "sam", "som", "growth", "cagr", "price", "demand", "compet",
    "proxy", "segment", "target", "customer", "problem", "solution", "feature",
    "value", "revenue", "cost", "margin", "channel", "model", "risk", "partner",
    "operation", "reservation", "booking", "noshow", "cancel", "integration", "api",
    "privacy", "subscription", "retention", "시장", "성장", "가격", "수요", "경쟁",
    "고객", "문제", "솔루션", "예약", "미용", "노쇼", "취소", "채널", "비용", "매출",
)


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
    if isinstance(value, str):
        value = value.strip()
    else:
        value = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    return value[:MAX_VALUE_CHARS] + ("…" if len(value) > MAX_VALUE_CHARS else "")


def _usable(value: Any) -> bool:
    return value not in (None, "", [], {})


def _facts(source: str, value: Any, start: int, limit: int) -> list[ConfirmedFact]:
    rows: list[ConfirmedFact] = []
    for path, leaf in _walk(value):
        if not _usable(leaf) or isinstance(leaf, bool):
            continue
        rows.append(ConfirmedFact(
            factId=f"FACT-{start + len(rows):03d}", path=f"{source}.{path}",
            value=_text(leaf), source=source,
        ))
        if len(rows) >= limit:
            break
    return rows


def _commercialization_rank(fact: ConfirmedFact, index: int) -> tuple[int, int]:
    text = f"{fact.path} {fact.value}".lower()
    trace_penalty = 4 if any(marker in text for marker in _TRACE_MARKERS) else 0
    relevance = sum(marker in text for marker in _COMMERCIALIZATION_MARKERS)
    return (trace_penalty - relevance, index)


def _prioritize_facts(facts: list[ConfirmedFact]) -> list[ConfirmedFact]:
    """Keep all facts, but surface business facts before run metadata."""
    return [fact for index, fact in sorted(
        enumerate(facts), key=lambda item: _commercialization_rank(item[1], item[0])
    )]


def _looks_like_url(value: str) -> bool:
    return value.startswith(("https://", "http://"))


def _evidence(market: Any, bm: Any) -> list[ExternalEvidence]:
    rows: list[ExternalEvidence] = []
    seen: set[str] = set()
    for source, payload in (("MARKET", market), ("BM", bm)):
        for path, leaf in _walk(payload):
            if not isinstance(leaf, str) or not _looks_like_url(leaf) or leaf in seen:
                continue
            seen.add(leaf)
            title = path.rsplit(".", 1)[-1].replace("_", " ") or source
            rows.append(ExternalEvidence(
                evidenceId=f"EXT-{len(rows) + 1:03d}", title=title,
                url=leaf, source=f"{source}.{path}",
            ))
            if len(rows) >= MAX_EVIDENCE:
                return rows
    return rows


def _product_summary(concept: Any, bm: Any, market: Any) -> str:
    preferred = ("concept_name", "conceptname", "product_name", "productname", "title", "name")
    for payload in (concept, bm, market):
        for path, leaf in _walk(payload):
            if any(key in path.lower() for key in preferred) and _usable(leaf):
                return _text(leaf)
    return "시장·BM 분석 결과를 기반으로 한 상용화 검증 대상"


def _signals(facts: list[ConfirmedFact]) -> list[MarketSignal]:
    keywords = ("tam", "sam", "som", "market", "growth", "cagr", "demand", "price", "compet", "customer", "proxy", "시장", "성장", "수요", "가격", "경쟁")
    rows: list[MarketSignal] = []
    for fact in facts:
        if fact.source != "MARKET" or not any(word in f"{fact.path} {fact.value}".lower() for word in keywords):
            continue
        source_type = "PROXY" if "proxy" in f"{fact.path} {fact.value}".lower() else "UPSTREAM"
        rows.append(MarketSignal(
            topic=fact.path, value=fact.value, sourceType=source_type,
            caveat="시장분석 원본에서 보존한 값이며, Proxy 여부는 원본 표기를 따른다.",
            basisIds=[fact.factId],
        ))
        if len(rows) >= MAX_SIGNALS:
            break
    return rows


def _assumptions(facts: list[ConfirmedFact]) -> list[str]:
    keywords = ("revenue", "price", "cost", "margin", "channel", "value", "model", "customer", "unit", "cac", "수익", "가격", "비용", "채널", "고객")
    rows: list[str] = []
    for fact in facts:
        if fact.source == "BM" and any(word in f"{fact.path} {fact.value}".lower() for word in keywords):
            rows.append(f"[{fact.factId}] {fact.path}: {fact.value}")
            if len(rows) >= MAX_SIGNALS:
                break
    return rows


def _advisor_facts(facts: list[ConfirmedFact]) -> list[ConfirmedFact]:
    """Prioritize commercialization-relevant facts while preserving Market/BM balance."""
    markers = (
        "tam", "sam", "som", "growth", "cagr", "price", "demand", "compet", "proxy",
        "revenue", "cost", "margin", "channel", "customer", "value", "model", "risk",
        "시장", "성장", "가격", "수요", "경쟁", "수익", "비용", "채널", "고객",
    )
    ranked = sorted(
        enumerate(facts),
        key=lambda item: _commercialization_rank(item[1], item[0]),
    )
    # Half of the prompt budget is reserved for each upstream module.  A large
    # market payload must never push every BM assumption out of the prompt.
    market = [fact for _, fact in ranked if fact.source == "MARKET"][:30]
    bm = [fact for _, fact in ranked if fact.source == "BM"][:30]
    return market + bm


async def scale_tech_ops_input(payload: dict) -> dict:
    """Build Layer 1 facts and Layer 2 upstream-source evidence without AI."""
    market = payload.get("marketResult") or {}
    bm = payload.get("bmResult") or {}
    market_facts = _facts("MARKET", market, 1, MAX_FACTS_PER_SOURCE)
    bm_facts = _facts("BM", bm, MAX_FACTS_PER_SOURCE + 1, MAX_FACTS_PER_SOURCE)
    facts = _prioritize_facts((market_facts + bm_facts)[:MAX_FACTS])
    normalized = TechOpsCommercializationInput(
        productSummary=_product_summary(payload.get("conceptHandoff") or {}, bm, market),
        marketSignals=_signals(facts),
        bmAssumptions=_assumptions(facts),
        missingInputs=[],
        layer1Facts=facts,
        layer2Evidence=_evidence(market, bm),
    )
    output = normalized.model_dump(mode="json")
    output["advisorFacts"] = [row.model_dump(mode="json") for row in _advisor_facts(facts)]
    return output
