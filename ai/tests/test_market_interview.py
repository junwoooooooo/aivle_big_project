import asyncio
from copy import deepcopy

import pytest

from app.providers import ProviderFailure
from app.tasks.market_interview import service


def request_payload():
    return {
        "contract": "market-interview-input-v1", "schemaVersion": "1.0", "synthetic": True,
        "source": {"marketSeedSnapshotId": "seed-1", "selectionId": 31, "selectionRevision": 4,
                   "marketSeedSnapshotHash": "sha256:" + "a" * 64, "bmPlanRevision": 3},
        "selectedConcept": {"identity": {"name": "예약 도우미", "targetUsers": "소규모 매장"},
                            "solution": {"featureSet": ["예약 확인"]}},
        "validatedHypotheses": {"price": {"value": "월 9,900원"}, "channels": {"value": ["직접 판매"]}},
        "businessModel": {"plan": {"key_activities": ["고객 지원"]}, "constraints": {}},
        "boundaries": ["실제 고객 조사가 아니다.", "통계를 추론하지 않는다.", "사업안을 변경하지 않는다."],
    }


def provider_result():
    participants = [
        {"participantId": f"P{i}", "label": f"가상 참여자 {chr(64 + i)}",
         "profile": f"서로 다른 사용 맥락 {i}", "context": "도입 전 비교", "needs": ["명확한 설명"]}
        for i in range(1, 5)
    ]
    interviews = [{"participantId": f"P{i}", "questions": [
        {"question": "현재 어떻게 해결하나요?", "answer": "수기로 확인할 수 있습니다.", "uncertainty": "실제 현장 확인 필요"},
        {"question": "무엇이 망설여지나요?", "answer": "도입 시간이 걱정될 수 있습니다.", "uncertainty": "실제 우려 확인 필요"},
        {"question": "언제 고려할까요?", "answer": "반복 업무가 많을 때 고려할 수 있습니다.", "uncertainty": "사용 맥락 확인 필요"},
    ], "concerns": ["도입 시간"], "purchaseTriggers": ["간단한 설정"],
       "objections": ["지원 범위"], "unmetNeeds": ["초기 교육"]} for i in range(1, 5)]
    return {"contract": "market-interview-result-v1", "schemaVersion": "1.0", "synthetic": True,
            "participants": participants, "interviews": interviews,
            "themes": [{"title": "도입 부담", "description": "설정과 지원을 먼저 확인하려는 관점",
                        "participantIds": ["P1", "P2"]}],
            "objections": ["지원 범위"], "unmetNeeds": ["초기 교육"],
            "purchaseTriggers": ["간단한 설정"],
            "followUpQuestions": ["현재 어떻게 해결하나요?", "도입 전에 무엇을 확인하나요?", "필요한 지원은 무엇인가요?"],
            "limitations": ["실제 고객 조사 결과가 아닙니다.", "통계적 대표성이 없으며 실제 인터뷰로 확인해야 합니다."]}


def execute(monkeypatch, result):
    async def prompt(*_args, **_kwargs):
        return result
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    return asyncio.run(service.execute_market_interview(request_payload()))


def test_schema_and_synthetic_qualitative_structure(monkeypatch):
    result = execute(monkeypatch, provider_result())
    assert result["contract"] == "market-interview-result-v1"
    assert result["synthetic"] is True
    assert len(result["participants"]) == len(result["interviews"]) == 4
    assert result["themes"] and result["followUpQuestions"]


@pytest.mark.parametrize("claim", ["고객의 80%가 구매합니다.", "대부분의 고객이 선호합니다.", "실제 사용자들은 만족합니다."])
def test_population_or_statistical_claim_is_rejected(monkeypatch, claim):
    value = provider_result(); value["themes"][0]["description"] = claim
    with pytest.raises(ProviderFailure):
        execute(monkeypatch, value)


def test_simulated_quote_cannot_be_labeled_as_actual_evidence(monkeypatch):
    value = provider_result(); value["interviews"][0]["questions"][0]["evidenceId"] = "E-1"
    with pytest.raises(ProviderFailure):
        execute(monkeypatch, value)


def test_partial_qualitative_result_fails_closed(monkeypatch):
    value = provider_result(); value["followUpQuestions"] = []
    with pytest.raises(ProviderFailure):
        execute(monkeypatch, value)


def test_invalid_provider_response_becomes_task_failure_contract(monkeypatch):
    value = deepcopy(provider_result()); value["synthetic"] = False
    with pytest.raises(ProviderFailure) as failure:
        execute(monkeypatch, value)
    assert failure.value.code == "RESULT_SCHEMA_INVALID"
