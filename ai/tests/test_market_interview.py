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


def deep_provider(schema_name, overrides=None):
    value = provider_result()
    overrides = overrides or {}
    if schema_name == "market_interview_targeting_v1":
        return {"participants": value["participants"]}
    if schema_name.startswith("market_interview_transcript_"):
        participant_id = schema_name.removeprefix("market_interview_transcript_").removesuffix("_v1").upper()
        interview = next(item for item in value["interviews"] if item["participantId"] == participant_id)
        interview = deepcopy(interview)
        if participant_id in overrides.get("interviews", {}):
            interview.update(overrides["interviews"][participant_id])
        return {"interview": interview}
    if schema_name == "market_interview_codebook_v1":
        theme = deepcopy(value["themes"][0]); theme.pop("participantIds")
        return {"themes": [theme], "followUpQuestions": value["followUpQuestions"]}
    if schema_name == "market_interview_coding_v1":
        assignments = [{"participantId": f"P{i}", "themeTitles": ["도입 부담"]} for i in range(1, 5)]
        return {"assignments": overrides.get("assignments", assignments)}
    raise AssertionError(schema_name)


def execute(monkeypatch, result=None, overrides=None):
    async def prompt(*_args, **kwargs):
        return deep_provider(kwargs["schema_name"], overrides) if result is None else result
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    return asyncio.run(service.execute_market_interview(request_payload()))


def test_schema_and_synthetic_qualitative_structure(monkeypatch):
    result = execute(monkeypatch)
    assert result["contract"] == "market-interview-result-v1"
    assert result["synthetic"] is True
    assert len(result["participants"]) == len(result["interviews"]) == 4
    assert result["themes"] and result["followUpQuestions"]
    assert [item["participantId"] for item in result["transcriptProvenance"]] == ["P1", "P2", "P3", "P4"]
    assert [item["participantId"] for item in result["codingTrace"]] == ["P1", "P2", "P3", "P4"]
    assert result["saturation"]["assessment"] == "EXPLORATORY_ONLY"


@pytest.mark.parametrize("claim", ["고객의 80%가 구매합니다.", "대부분의 고객이 선호합니다.", "실제 사용자들은 만족합니다."])
def test_population_or_statistical_claim_is_rejected(monkeypatch, claim):
    async def prompt(*_args, **kwargs):
        value = deep_provider(kwargs["schema_name"])
        if kwargs["schema_name"] == "market_interview_codebook_v1":
            value["themes"][0]["description"] = claim
        return value
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    with pytest.raises(ProviderFailure):
        asyncio.run(service.execute_market_interview(request_payload()))


def test_simulated_quote_cannot_be_labeled_as_actual_evidence(monkeypatch):
    value = provider_result()["interviews"][0]
    value["questions"][0]["evidenceId"] = "E-1"
    with pytest.raises(ProviderFailure):
        execute(monkeypatch, overrides={"interviews": {"P1": value}})


def test_partial_qualitative_result_fails_closed(monkeypatch):
    async def prompt(*_args, **kwargs):
        value = deep_provider(kwargs["schema_name"])
        if kwargs["schema_name"] == "market_interview_codebook_v1":
            value["followUpQuestions"] = []
        return value
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    with pytest.raises(ProviderFailure):
        asyncio.run(service.execute_market_interview(request_payload()))


def test_invalid_provider_response_becomes_task_failure_contract(monkeypatch):
    value = {"participants": deepcopy(provider_result()["participants"])}
    value["participants"][0]["participantId"] = "CUSTOMER-1"
    with pytest.raises(ProviderFailure) as failure:
        execute(monkeypatch, value)
    assert failure.value.code == "RESULT_SCHEMA_INVALID"


def test_mentions_are_not_converted_to_percentages_or_population_claims(monkeypatch):
    result = execute(monkeypatch)
    serialized = str({"themes": result["themes"], "interviews": result["interviews"]})
    assert "%" not in serialized
    assert "대부분의 고객" not in serialized
    assert "구매 확률" not in serialized
    assert result["themes"][0]["participantIds"] == ["P1", "P2", "P3", "P4"]


def test_unknown_codebook_assignment_fails_closed(monkeypatch):
    assignments = [{"participantId": f"P{i}", "themeTitles": ["없는 주제"]} for i in range(1, 5)]
    with pytest.raises(ProviderFailure):
        execute(monkeypatch, overrides={"assignments": assignments})
