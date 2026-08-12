import asyncio
import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.research import product_pipeline as pipeline, product_market_join
from app.research.bm.contracts import (
    BMAnalysisResult,
    BMCanvasItem,
    CanvasCell,
    CanvasStatus,
    ConceptSnapshot,
    MarketJoinData,
)
from app.research.bm.finalize import finalize_bm_analysis
from app.research.bm.handoff import build_financial_handoff
from app.research.bm.normalize import create_bm_analysis_input, resolve_bm_input
from app.services.journey_provider import ProviderFailure

import bm_adapter


FIXTURE = Path(__file__).parent / "fixtures" / "market_research" / "full.json"


def _market_result() -> dict:
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def _concept() -> dict:
    return {
        "concept_id": "fridge-optimizer",
        "name": "냉장고 재료 활용 최적화 서비스",
        "target": "식재료 낭비를 줄이려는 1인 가구",
        "problem": "보유 재료를 제때 활용하기 어렵다",
        "solution": "냉장고 재료를 기준으로 메뉴와 소비 순서를 추천한다",
        "_bm_plan": {
            "revenue_model": "월 구독",
            "channel": ["모바일 앱"],
            "differentiation": ["보유 재료 우선 추천"],
            "key_activities": ["재료 인식", "레시피 추천"],
            "key_resources": ["식재료 데이터"],
            "key_partners": ["식품 데이터 제공사"],
            "customer_relationship": "개인화 알림",
        },
        "_user_bm_plan": {
            "customer_relationship": "사용자가 확정한 알림",
            "key_activities": ["사용자가 확정한 활동"],
            "key_resources": ["사용자가 확정한 자원"],
            "key_partners": ["사용자가 확정한 파트너"],
        },
        "constraint": {"budget_krw": 5_000_000, "months": 10, "team": 2},
    }


def test_donor_snapshot_is_not_the_canonical_contract():
    donor_snapshot = bm_adapter._snapshot(_concept())

    assert type(donor_snapshot) is bm_adapter.ConceptSnapshot
    assert type(donor_snapshot) is not ConceptSnapshot
    with pytest.raises(ValidationError):
        MarketJoinData(
            concept_id="fridge-optimizer",
            concept_snapshot=donor_snapshot,
            market_size={},
            growth_rate={},
            competitor_analysis=[],
            price_analysis={},
            demand_evidence=[],
            market_size_calculation={},
        )


def test_market_join_bridges_to_canonical_snapshot_and_preserves_product_data():
    market_result = _market_result()
    concept = _concept()

    joined = product_market_join.build(
        market_result, concept, concept["concept_id"])

    snapshot = joined.concept_snapshot
    dumped = snapshot.model_dump(mode="python")
    assert type(snapshot) is ConceptSnapshot
    assert joined.concept_id == concept["concept_id"]
    assert snapshot.concept_name == concept["name"]
    assert snapshot.target_customer == concept["target"]
    assert snapshot.problem == concept["problem"]
    assert snapshot.solution == concept["solution"]
    assert snapshot.revenue_model == "월 구독"
    assert snapshot.channel == ["모바일 앱"]
    assert snapshot.differentiation == ["보유 재료 우선 추천"]
    assert dumped["customer_relationship"] == "사용자가 확정한 알림"
    assert dumped["key_activities"] == ["사용자가 확정한 활동"]
    assert dumped["key_resources"] == ["사용자가 확정한 자원"]
    assert dumped["key_partners"] == ["사용자가 확정한 파트너"]

    market = market_result["market"]
    assert joined.market_size.tam == market["tam"]["value"]
    assert joined.market_size.sam == market["sam"]["value"]
    assert joined.market_size.som is None
    assert joined.missing_items == market["notFound"]
    assert joined.evidence_list[0]["id"] == market_result["evidence"][0]["id"]

    source = create_bm_analysis_input(
        market_data=joined,
        execution_constraints=concept["constraint"],
    )
    resolved = resolve_bm_input(source)
    assert resolved.execution_constraints == concept["constraint"]
    assert resolved.market_join_data.concept_snapshot.model_dump(mode="python")[
        "key_activities"] == ["사용자가 확정한 활동"]


def _bm_task() -> dict:
    concept = _concept()
    return {
        "mode": "BM",
        "conceptId": concept["concept_id"],
        "marketResultJson": json.dumps(_market_result(), ensure_ascii=False),
        "conceptSnapshotJson": json.dumps(concept, ensure_ascii=False),
        "executionConstraints": concept["constraint"],
        "llmBudget": 1,
    }


def test_bm_product_maps_nested_contract_failure(monkeypatch):
    async def fail_contract(*_args, **_kwargs):
        try:
            MarketJoinData.model_validate({})
        except ValidationError as cause:
            raise pipeline._Hard("bm_adapter failed") from cause

    monkeypatch.setattr(pipeline, "_bm_product", fail_contract)

    with pytest.raises(ProviderFailure) as caught:
        asyncio.run(pipeline.run_market_research(_bm_task(), "bm-run", 1.0))

    assert caught.value.code == "RESULT_SCHEMA_INVALID"
    assert caught.value.reason == "RESULT_FIELD_CONSTRAINT_VIOLATION"
    assert caught.value.status_code == 502
    assert caught.value.retryable is False


def test_bm_product_emits_truthful_stage_order(monkeypatch):
    from app.research.bm import flow

    async def fake_flow(source, diagnostic_context=None):
        assert diagnostic_context is None
        resolved = resolve_bm_input(source)
        canvas = [
            BMCanvasItem(
                canvas_cell=cell,
                content=[],
                status=CanvasStatus.UNVERIFIED,
                reason="fixture model result",
            )
            for cell in CanvasCell
        ]
        analysis = BMAnalysisResult(
            concept_id=resolved.concept_id,
            concept_name=resolved.concept_name,
            canvas=canvas,
            market_fit_status="PARTIAL",
            consistency_status="PARTIAL",
            market_fit_summary="fixture",
            consistency_summary="fixture",
        )
        final = finalize_bm_analysis(
            bm_analysis=analysis,
            resolved=resolved,
            legal_context=source.legal_context,
        )
        return {
            "bm_analysis": analysis,
            "final_result": final,
            "financial_handoff": build_financial_handoff(
                final_result=final, resolved=resolved),
        }

    monkeypatch.setattr(flow, "run_bm_pipeline_flow", fake_flow)
    events = []

    result = asyncio.run(pipeline._bm_product(
        _market_result(),
        _concept(),
        "fridge-optimizer",
        "bm-observed-run",
        pipeline.Budget(1),
        {},
        _concept()["constraint"],
        None,
        events.append,
    ))

    assert result["mode"] == "BM"
    assert [event["stage"] for event in events] == [
        "BM_RESTORE", "BM_ADAPTER", "BM_MODEL", "BM_SERIALIZATION"]
    assert events[-1]["status"] == "COMPLETED"


def test_bm_product_maps_model_failure_as_transient(monkeypatch):
    async def fail_model(*_args, **_kwargs):
        try:
            raise RuntimeError("provider detail must not cross the boundary")
        except RuntimeError as cause:
            raise pipeline._Hard("bm_model failed") from cause

    monkeypatch.setattr(pipeline, "_bm_product", fail_model)

    with pytest.raises(ProviderFailure) as caught:
        asyncio.run(pipeline.run_market_research(_bm_task(), "bm-run", 1.0))

    assert caught.value.code == "EXECUTION_FAILED"
    assert caught.value.reason == "TRANSIENT_EXECUTION_FAILURE"
    assert caught.value.status_code == 502
    assert caught.value.retryable is True
    assert "provider detail" not in str(caught.value)


def test_bm_product_maps_deadline(monkeypatch):
    async def too_slow(*_args, **_kwargs):
        await asyncio.sleep(1)

    monkeypatch.setattr(pipeline, "_bm_product", too_slow)

    with pytest.raises(ProviderFailure) as caught:
        asyncio.run(pipeline.run_market_research(_bm_task(), "bm-run", 0.001))

    assert caught.value.code == "DEADLINE_EXCEEDED"
    assert caught.value.reason == "REQUEST_DEADLINE_EXCEEDED"
    assert caught.value.status_code == 504
    assert caught.value.retryable is True
