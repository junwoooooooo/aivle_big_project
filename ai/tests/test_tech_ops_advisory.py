import asyncio

import pytest

from app.tasks.tech_ops_advisor import runtime_adapter
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
    assert captured["bm"]["confirmedTechOpsInput"]["requiredFacts"]["ownedPersonnel"][0]["count"] == 1
    assert len(output["advice"]) == 7


def test_missing_legal_requires_open_legal_gate(monkeypatch):
    async def engine(_payload): return result(legal_open=False)
    monkeypatch.setattr(runtime_adapter, "generate_tech_ops_advisory", engine)
    with pytest.raises(ValueError, match="OPEN legal"):
        asyncio.run(runtime_adapter.execute_tech_ops_advisory(task_input(False)))


def test_progress_events_are_real_adapter_boundaries(monkeypatch):
    events = []
    async def engine(_payload): return result()
    async def sink(stage, key, params): events.append((stage, key, params))
    monkeypatch.setattr(runtime_adapter, "generate_tech_ops_advisory", engine)
    asyncio.run(runtime_adapter.execute_tech_ops_advisory(task_input(), sink))
    assert [stage for stage, _, _ in events] == ["SCALING", "EVIDENCE", "GENERATING", "VALIDATING"]
