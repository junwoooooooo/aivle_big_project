import asyncio

import pytest

from app.progress.safe_task_progress import SafeTaskProgressSender
from app.tasks.tech_ops_advisor import runtime_adapter
from app.tasks.tech_ops_advisor import service as advisory_service
from app.tasks.tech_ops_input_scaler import scale_tech_ops_input


def task_input(legal=True):
    return {"projectId": 1, "techOpsInputSnapshotId": "snap-1", "sourceMarketSeedSnapshotId": "seed-1",
        "sourceMarketResearchVersionId": 11, "sourceBusinessModelVersionId": 12,
        "sourcePortfolioSelectionId": 13, "selectedConceptId": "concept-1",
        "selectedConceptHash": "sha256:" + "a" * 64,
        "conceptHandoff": {"conceptName": "예약 운영 서비스"},
        "legalHandoff": {"status": "CURRENT"} if legal else None,
        "marketResult": {"tam": 1000, "price": 9900, "source": "https://example.com/market"},
        "businessModelResult": {"revenueModel": "SUBSCRIPTION", "channel": "파트너"},
        "techOpsInputSnapshot": {"requiredFacts": {"ownedPersonnel": [{"role": "운영", "count": 1}]},
            "requiredDecisions": {"delivery": {"value": "직접 운영", "decision": "ACCEPTED"}},
            "evidenceReferences": [{"evidenceId": "user-1", "source": "USER_PROVIDED_EVIDENCE"}]}}


def result(legal_open=True):
    areas = ["MARKET_BM", "PRODUCT_TECH", "OPERATIONS", "RISK_GATE", "PARTNER_SUPPLY", "PILOT", "SCALE"]
    topics = ["DATA_AI", "CUSTOMER_TRUST", "OBSERVABILITY_SLA", "SCALABILITY"]
    gates = [{"title": "법률·개인정보 검토" if legal_open and i == 0 else f"구체 게이트 {i}",
              "owner": "담당", "status": "OPEN", "exitCriteria": "흐름과 증빙 및 통과 조건을 실제 운영 기록으로 확인합니다.",
              "basisIds": ["FACT-001"]} for i in range(6)]
    return {"productName": "예약 운영 서비스", "decision": "CONDITIONAL_GO", "summary": "근거 기반 판단",
        "advice": [{"area": area, "priority": "HIGH", "advice": "파일럿 검증", "validationMethod": "이벤트 로그", "basisIds": ["FACT-001"]} for area in areas],
        "gates": gates, "operatingCosts": [{"category": f"비용{i}", "driver": "운영 처리", "trigger": "요청 발생",
            "measurementUnit": "처리 건", "behavior": "VARIABLE", "pilotMeasurement": "처리 건 기록",
            "note": "금액 예측 아님", "basisIds": ["FACT-001"]} for i in range(5)],
        "readiness": [{"topic": topic, "priority": "HIGH", "assessment": "현재 확인 필요",
            "watchouts": ["예외"], "controls": ["점검"], "validationMethod": "운영 기록", "basisIds": ["FACT-001"]} for topic in topics],
        "pilotPlan": {"objective": "핵심 흐름 검증", "scope": ["파트너 2곳"], "metrics": ["완료율"],
            "stopConditions": ["장애 임계 초과"], "scaleConditions": ["목표 충족"]},
        "disclaimer": "가정 기반 자문", "layer1Facts": [{"factId": "FACT-001"}], "layer2Evidence": []}


def test_main_scaler_preserves_balanced_market_and_bm_facts():
    scaled = asyncio.run(scale_tech_ops_input({
        "conceptHandoff": task_input()["conceptHandoff"],
        "marketResult": task_input()["marketResult"],
        "bmResult": {"revenueModel": "SUBSCRIPTION", "channel": "파트너", "price": 9900},
    }))
    sources = {item["source"] for item in scaled["advisorFacts"]}
    assert {"MARKET", "BM"}.issubset(sources)


def test_full_adapter_passes_canonical_sources_and_confirmed_input(monkeypatch):
    captured = {}
    async def engine(payload): captured.update(payload); return result()
    monkeypatch.setattr(runtime_adapter, "generate_tech_ops_advisory", engine)
    output = asyncio.run(runtime_adapter.execute_tech_ops_advisory(task_input()))
    assert captured["market"]["tam"] == 1000
    assert captured["bm"]["businessModel"]["revenueModel"] == "SUBSCRIPTION"
    assert captured["bm"]["USER_CONFIRMED_TECH_OPS"]["requiredFacts"]["ownedPersonnel"][0]["count"] == 1
    assert len(output["advice"]) == 7


def test_confirmed_tech_ops_fact_keeps_distinct_prompt_path_and_result_provenance(monkeypatch):
    captured = {}
    generated = result()
    generated["layer1Facts"] = [
        {"factId": "FACT-001", "path": "MARKET.tam", "value": "1000", "source": "MARKET", "status": "CONFIRMED_FACT"},
        {"factId": "FACT-002", "path": "BM.businessModel.channel", "value": "파트너", "source": "BM", "status": "CONFIRMED_FACT"},
        {"factId": "FACT-003", "path": "BM.USER_CONFIRMED_TECH_OPS.requiredDecisions.delivery.value", "value": "직접 운영", "source": "BM", "status": "CONFIRMED_FACT"},
    ]
    generated["advice"][0]["basisIds"] = ["FACT-003"]

    async def engine(payload):
        captured.update(payload)
        return generated

    monkeypatch.setattr(runtime_adapter, "generate_tech_ops_advisory", engine)
    output = asyncio.run(runtime_adapter.execute_tech_ops_advisory(task_input()))

    assert "USER_CONFIRMED_TECH_OPS" in captured["bm"]
    facts = {fact["factId"]: fact for fact in output["layer1Facts"]}
    assert facts["FACT-001"]["source"] == "MARKET"
    assert facts["FACT-002"]["source"] == "BM"
    assert facts["FACT-003"]["source"] == "TECH_OPS"
    assert facts["FACT-003"]["status"] == "USER_CONFIRMED_OR_ACCEPTED"
    assert output["advice"][0]["basisIds"] == ["FACT-003"]


def test_real_scaler_fact_path_is_restored_without_changing_basis_id():
    engine_payload = {
        "conceptHandoff": task_input()["conceptHandoff"],
        "marketResult": task_input()["marketResult"],
        "bmResult": {
            "businessModel": task_input()["businessModelResult"],
            "USER_CONFIRMED_TECH_OPS": task_input()["techOpsInputSnapshot"],
        },
    }

    scaled = asyncio.run(scale_tech_ops_input(engine_payload))
    confirmed = next(
        fact for fact in scaled["layer1Facts"]
        if fact["path"].startswith("BM.USER_CONFIRMED_TECH_OPS.")
    )
    fact_id = confirmed["factId"]
    assert confirmed["source"] == "BM"  # exact main scaler behavior before the full adapter boundary
    assert any(fact["factId"] == fact_id for fact in scaled["advisorFacts"])

    report = {"layer1Facts": scaled["layer1Facts"], "advice": [{"basisIds": [fact_id]}]}
    runtime_adapter._restore_confirmed_tech_ops_provenance(report)
    restored = next(fact for fact in report["layer1Facts"] if fact["factId"] == fact_id)

    assert restored["path"].startswith("BM.USER_CONFIRMED_TECH_OPS.")
    assert restored["source"] == "TECH_OPS"
    assert restored["status"] == "USER_CONFIRMED_OR_ACCEPTED"
    assert report["advice"][0]["basisIds"] == [fact_id]


def test_missing_legal_requires_open_legal_gate(monkeypatch):
    async def engine(_payload): return result(legal_open=False)
    monkeypatch.setattr(runtime_adapter, "generate_tech_ops_advisory", engine)
    with pytest.raises(ValueError, match="OPEN legal"):
        asyncio.run(runtime_adapter.execute_tech_ops_advisory(task_input(False)))


def test_progress_events_are_real_adapter_boundaries(monkeypatch):
    events = []
    async def engine(_payload): return result()
    def sink(event): events.append(event)
    monkeypatch.setattr(runtime_adapter, "generate_tech_ops_advisory", engine)
    asyncio.run(runtime_adapter.execute_tech_ops_advisory(task_input(), sink))
    assert [event["stage"] for event in events] == ["SCALING", "EVIDENCE", "GENERATING", "VALIDATING"]
    assert all(event["status"] == "RUNNING" and event["safeSummary"] for event in events)


def test_real_sender_and_main_engine_share_one_dict_sync_progress_contract(monkeypatch):
    observed = []
    provider_calls = []

    async def post(payload):
        observed.append(payload)

    async def evidence(*_args, **_kwargs):
        return [], {"queries": [], "status": "SKIPPED"}

    async def advice(*_args, **_kwargs):
        provider_calls.append("advice")
        return result()

    async def expansion(*_args, **_kwargs):
        provider_calls.append("expansion")
        return {
            "operatingCosts": result()["operatingCosts"],
            "readiness": result()["readiness"],
            "gates": result()["gates"],
        }

    monkeypatch.setattr(advisory_service, "collect_external_evidence", evidence)
    monkeypatch.setattr(advisory_service, "_request_advice", advice)
    monkeypatch.setattr(advisory_service, "_request_operations_expansion", expansion)

    async def run():
        async with SafeTaskProgressSender(
            task_run_id="run-1", task_attempt_id="attempt-1",
            correlation_id="correlation-1", post=post,
        ) as progress:
            output = await runtime_adapter.execute_tech_ops_advisory(
                task_input(), event_sink=progress.emit,
            )
        return output

    output = asyncio.run(run())
    assert output["decision"] == "CONDITIONAL_GO"
    assert provider_calls and provider_calls[0] == "advice"
    assert [event["stage"] for event in observed] == [
        "SCALING", "EVIDENCE", "GENERATING", "VALIDATING",
    ]
    assert all(event["taskRunId"] == "run-1" for event in observed)
