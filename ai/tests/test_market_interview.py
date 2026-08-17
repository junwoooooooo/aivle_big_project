import asyncio
import json

import pytest

from app.providers import ProviderFailure
from app.tasks.market_interview import deep_engine, service


def request_payload(sample_size=20):
    return {
        "contract": "market-interview-input-v2", "schemaVersion": "2.0", "synthetic": True,
        "sampleSize": sample_size,
        "source": {"marketSeedSnapshotId": "seed-1", "selectionId": 31, "selectionRevision": 4,
                   "marketSeedSnapshotHash": "sha256:" + "a" * 64, "bmPlanRevision": 3},
        "selectedConcept": {"identity": {"name": "예약 도우미", "targetUsers": "서울 소규모 매장"},
                            "solution": {"featureSet": ["예약 확인"]}},
        "validatedHypotheses": {}, "businessModel": {"plan": {}, "constraints": {}},
        "boundaries": ["실제 고객 조사가 아니다.", "통계를 추론하지 않는다.", "사업안을 변경하지 않는다."],
    }


def bank(target_count=80, comparison_count=20):
    cards, frame = {}, []
    for index in range(target_count + comparison_count):
        target = index < target_count
        pid = f"bank-{index:03d}"
        gender = "남" if index % 2 == 0 else "여"
        gender_text = "남성" if gender == "남" else "여성"
        band = ("20대", "30대", "40대", "50대", "60+")[index % 5]
        age = (25, 35, 45, 55, 65)[index % 5]
        region = "서울" if target else "부산"
        cards[pid] = (f"저는 만 {age}세 {gender_text}입니다. {region} 시 지역에 살고 있습니다. "
                      "2세대가구(부부+자녀) 형태의 3인 가구이고, 가구 안에서는 가구주입니다. "
                      "개인 월소득은 300~400만 원 미만 수준입니다. 현재 전업주부입니다.")
        frame.append({"pid_hash": pid, "gender": gender, "band": band})
    return cards, frame


THEMES = [
    {"axis": "LIKE", "title": "간단한 확인", "description": "확인이 간단함"},
    {"axis": "CONCERN", "title": "가격 부담", "description": "가격 조건 우려"},
    {"axis": "DIFFERENTIATION", "title": "차이 불명확", "description": "기존 방식과 비교"},
    {"axis": "USAGE_SCENE", "title": "매장 마감", "description": "마감 시 사용"},
    {"axis": "BARRIER", "title": "도입 시간", "description": "도입 시간 장벽"},
    {"axis": "SUGGESTION", "title": "설명 보완", "description": "설명 개선"},
]


def provider(claim=None, target_region="서울", unknown_theme=False):
    async def prompt(_system, user, **kwargs):
        payload = json.loads(user)
        name = kwargs["schema_name"]
        if name == "market_interview_target_criteria_v2":
            return {"criteria": {"ageMin": 0, "ageMax": 0, "genders": [],
                    "householdSizeMin": 0, "householdSizeMax": 0, "regions": [target_region],
                    "incomeKeywords": [], "jobKeywords": [], "hasChildren": 0, "householdRoles": []}}
        if name == "market_interview_answer_v2":
            answer = {"firstImpression": "업무를 확인하는 도구 같습니다.",
                      "restatement": "예약 확인을 돕습니다.", "like": "간단한 확인이 좋습니다.",
                      "concern": claim or "20% 할인이라면 써볼 수 있습니다.",
                      "differentiation": "기존 방식과 차이는 더 봐야 합니다.",
                      "relevance": "현재는 수기로 처리합니다.", "usageScene": "매장 마감 때 씁니다.",
                      "barrier": "도입 시간이 걸립니다.", "suggestion": "설명을 보완해 주세요."}
            return {"participantId": payload["participantId"], "answers": answer}
        if name == "market_interview_codebook_v2":
            themes = [dict(item) for item in THEMES]
            if claim: themes[1]["description"] = claim
            return {"themes": themes, "alternatives": ["수기 처리"],
                    "followUpQuestions": ["현재 방식은 무엇인가요?", "무엇이 걸리나요?", "어떤 설명이 필요한가요?"]}
        if name == "market_interview_assignment_v2":
            rows = []
            for item in payload["transcripts"]:
                rows.append({"participantId": item["participantId"],
                             "themeTitles": ["없는 주제"] if unknown_theme else [row["title"] for row in THEMES],
                             "alternativeLabel": "수기 처리", "comprehension": "accurate",
                             "differentiation": "unclear"})
            return {"assignments": rows}
        raise AssertionError(name)
    return prompt


def execute(monkeypatch, sample_size=20, **provider_options):
    monkeypatch.setattr(deep_engine, "load_bank", lambda: bank())
    monkeypatch.setattr(service, "execute_structured_prompt", provider(**provider_options))
    return asyncio.run(service.execute_market_interview(request_payload(sample_size)))


@pytest.mark.parametrize("size", [20, 40, 80])
def test_profile_bank_sample_paths_and_deterministic_8_to_2(monkeypatch, size):
    result = execute(monkeypatch, size)
    assert result["contract"] == "market-interview-result-v2"
    assert result["synthetic"] is True
    assert result["targeting"]["drawnSampleSize"] == size
    assert result["targeting"]["targetCount"] == int(size * .8)
    assert result["targeting"]["nonTargetCount"] == size - int(size * .8)
    assert len(result["transcriptProvenance"]) == len(result["codingTrace"]) == size
    assert all(item["participantId"].startswith("R") for item in result["transcriptProvenance"])
    assert "bank-" not in str(result)


def test_target_zero_fails_before_response_spend(monkeypatch):
    calls = []
    real = provider(target_region="제주")
    async def tracked(*args, **kwargs):
        calls.append(kwargs["schema_name"])
        return await real(*args, **kwargs)
    monkeypatch.setattr(deep_engine, "load_bank", lambda: bank())
    monkeypatch.setattr(service, "execute_structured_prompt", tracked)
    with pytest.raises(ProviderFailure) as failure:
        asyncio.run(service.execute_market_interview(request_payload()))
    assert failure.value.reason == "MARKET_INTERVIEW_TARGET_UNAVAILABLE"
    assert calls == ["market_interview_target_criteria_v2"]


def test_two_pass_coding_retains_identity_and_derives_mentions(monkeypatch):
    result = execute(monkeypatch)
    sampled = {item["participantId"] for item in result["transcriptProvenance"]}
    assert {item["participantId"] for item in result["codingTrace"]} == sampled
    assert all(theme["mentionCount"] == len(theme["participantIds"]) for theme in result["themes"])
    assert all(theme["targetCount"] + theme["nonTargetCount"] == theme["mentionCount"]
               for theme in result["themes"])
    assert result["crossRelationships"]
    assert all(row["overlapCount"] == len(row["respondentIds"]) for row in result["crossRelationships"])
    assert result["saturation"]["axisLabelCounts"] == {axis: 1 for axis in
        ("LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION")}
    assert result["saturation"]["alternativeSum"] == 20


def test_unknown_codebook_assignment_fails_closed(monkeypatch):
    with pytest.raises(ProviderFailure):
        execute(monkeypatch, unknown_theme=True)


@pytest.mark.parametrize("allowed", ["20% 할인이라면 써볼 수 있다.", "수수료 8%는 부담스럽다.",
                                      "가격이 10% 내려가면 검토한다."])
def test_literal_percentage_in_individual_response_is_allowed(monkeypatch, allowed):
    result = execute(monkeypatch, claim=allowed)
    assert allowed in str(result["interviews"])


@pytest.mark.parametrize("claim", ["응답자의 75%가 구매한다.", "고객의 80%가 선호한다.",
    "대부분의 고객이 구매한다.", "실제 사용자들은 만족한다.", "구매 확률은 70%다.",
    "구매 전환율은 35%다."])
def test_population_or_statistical_claim_is_rejected(monkeypatch, claim):
    with pytest.raises(ProviderFailure):
        execute(monkeypatch, claim=claim)
