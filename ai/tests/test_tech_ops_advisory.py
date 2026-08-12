import asyncio

import pytest

from app.providers import ProviderFailure
from app.tasks.tech_ops_advisor import service
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
    return {"productName": "예약 운영 서비스", "decision": "CONDITIONAL_GO", "summary": "근거에 연결된 상용화 판단을 구체적인 파일럿 조건과 운영 기록으로 검증해야 하며 고객 흐름과 파트너 예외를 함께 확인해야 합니다.",
        "advice": [{"area": area, "priority": "HIGH", "advice": "실제 고객 흐름과 운영 예외를 파일럿에서 구체적인 기록으로 검증해야 합니다.", "validationMethod": "이벤트 로그와 인터뷰", "basisIds": ["FACT-001"]} for area in areas],
        "gates": gates, "operatingCosts": [{"category": f"비용{i}", "driver": "운영 처리", "trigger": "요청 발생",
            "measurementUnit": "처리 건", "behavior": "VARIABLE", "pilotMeasurement": "처리 건과 소요 시간을 기록합니다.",
            "note": "금액 예측 아님", "basisIds": ["FACT-001"]} for i in range(5)],
        "readiness": [{"topic": topic, "priority": "HIGH", "assessment": "현재 확인 필요",
            "watchouts": ["예외 흐름", "누락 기록"], "controls": ["접근 통제", "운영 점검"],
            "validationMethod": "주간 운영 기록", "basisIds": ["FACT-001"]} for topic in topics],
        "pilotPlan": {"objective": "핵심 흐름 검증", "scope": ["파트너 2곳"], "metrics": ["완료율"],
            "stopConditions": ["장애 임계 초과"], "scaleConditions": ["목표 충족"]},
        "disclaimer": "가정 기반 자문이며 법률 또는 재무 보증이 아닙니다."}


def test_scaler_preserves_balanced_market_bm_and_user_facts():
    scaled = asyncio.run(scale_tech_ops_input(task_input()))
    sources = {item["source"] for item in scaled["advisorFacts"]}
    assert {"MARKET", "BM", "TECH_OPS"}.issubset(sources)
    assert scaled["userEvidence"][0]["source"] == "USER_PROVIDED_EVIDENCE"


def test_invalid_first_result_is_repaired_exactly_once(monkeypatch):
    calls = []
    async def prompt(*_args, **_kwargs):
        calls.append(1); return {} if len(calls) == 1 else result()
    async def external(*_args): return [], {"failOpen": True}
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    monkeypatch.setattr(service, "collect_external_evidence", external)
    output = asyncio.run(service.execute_tech_ops_advisory(task_input()))
    assert len(calls) == 2 and len(output["advice"]) == 7


def test_invalid_repair_fails_without_fake_success(monkeypatch):
    calls = []
    async def prompt(*_args, **_kwargs): calls.append(1); return {}
    async def external(*_args): return [], {"failOpen": True}
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    monkeypatch.setattr(service, "collect_external_evidence", external)
    with pytest.raises(ProviderFailure) as failure:
        asyncio.run(service.execute_tech_ops_advisory(task_input()))
    assert failure.value.reason == "AI_RESULT_INVALID" and len(calls) == 2


def test_missing_legal_requires_open_legal_gate(monkeypatch):
    async def prompt(*_args, **_kwargs): return result(legal_open=False)
    async def external(*_args): return [], {"failOpen": True}
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    monkeypatch.setattr(service, "collect_external_evidence", external)
    with pytest.raises(ProviderFailure):
        asyncio.run(service.execute_tech_ops_advisory(task_input(False)))
