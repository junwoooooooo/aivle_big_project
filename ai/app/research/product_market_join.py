"""Materialized Market result → donor MarketJoinData integration adapter.

분석값을 재계산하지 않고 Market 봉투의 값·근거 ID·등급·가정·경계를 계약 이름으로
옮긴다. 이 어댑터 덕분에 BM은 임시 AI 원장이 아니라 immutable Market version에 묶인다.
"""
from __future__ import annotations

from .bm.contracts import (
    ConceptSnapshot,
    GrowthRateData,
    MarketJoinData,
    MarketSizeData,
    PriceAnalysisData,
)


def _evidence(item: dict) -> dict:
    """Market 원장은 보존하고 BM 모델에는 판정에 필요한 compact view만 넘긴다.

    source URL·원문 quote·retrieval metadata 등은 immutable Market version에 이미
    보존되어 있다. BM은 evidence id로 원장 근거를 가리키므로 그 provenance 전문을
    다시 프롬프트에 싣지 않는다.
    """
    return {
        "id": item.get("id"), "kind": item.get("kind"),
        "metric": item.get("metric"), "subject": item.get("subject"),
        "period": item.get("period"), "value": item.get("value"), "unit": item.get("unit"),
        "grade": item.get("grade"),
        "section": item.get("section"),
        "caveats": list(item.get("caveats") or []),
        "assumptions": list(item.get("assumptions") or []),
    }


def build(market_result: dict, concept: dict, concept_id: str) -> MarketJoinData:
    import bm_adapter

    donor_snapshot = bm_adapter._snapshot(concept)
    canonical_snapshot = ConceptSnapshot.model_validate(
        donor_snapshot.model_dump(mode="python"))
    market = market_result.get("market") or {}
    tam, sam, growth = market.get("tam") or {}, market.get("sam") or {}, market.get("growth") or {}
    evidence = [_evidence(item) for item in (market_result.get("evidence") or [])]
    competitors = [item for item in evidence if item.get("section") == "COMPETITOR"]
    demand = [item for item in evidence if item.get("section") == "DEMAND"]
    channels = [item for item in evidence if item.get("section") == "CHANNEL"]
    price = market.get("price") or {}
    calculations = {
        "tam": tam.get("formula"), "tam_inputs": tam.get("inputs"),
        "tam_grade": tam.get("grade"), "tam_assumptions": list(tam.get("assumptions") or []),
        "sam": sam.get("formula"), "growth": growth.get("formula"),
        "growth_grade": growth.get("grade"),
        "price_base_kind": price.get("baseKind"),
        "price_base_note": price.get("baseNote"),
        "_등급_읽는_법": "계산값 등급은 약한 고리를 따른다. 가정이 섞이면 추정이다.",
        "_경계": "evidence_list[].caveats를 값과 함께 유지한다.",
    }
    return MarketJoinData(
        concept_id=concept_id,
        concept_snapshot=canonical_snapshot,
        market_size=MarketSizeData(tam=tam.get("value"), sam=sam.get("value"),
                                   som=(market.get("som") or {}).get("value"), unit="KRW"),
        growth_rate=GrowthRateData(value=growth.get("value"), unit="%/년"),
        competitor_analysis=competitors,
        price_analysis=PriceAnalysisData(
            price_min=price.get("min"), price_base=price.get("base"),
            price_max=price.get("max"), currency=price.get("currency")),
        demand_evidence=demand,
        channel_analysis=channels,
        market_size_calculation=calculations,
        missing_items=list(market.get("notFound") or []),
        evidence_list=evidence,
    )
