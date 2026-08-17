import asyncio
import json
from collections import Counter

import pytest
from pydantic import ValidationError

from app.providers import ProviderFailure
from app.tasks.market_interview import deep_engine, service
from app.tasks.market_interview.models import MarketInterviewResult


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
    monkeypatch.setattr(service, "execute_market_interview_prompt", provider(**provider_options))
    return asyncio.run(service.execute_market_interview(request_payload(sample_size)))


def execute_with_provider(monkeypatch, prompt, sample_size=20):
    monkeypatch.setattr(deep_engine, "load_bank", lambda: bank())
    monkeypatch.setattr(service, "execute_market_interview_prompt", prompt)
    return asyncio.run(service.execute_market_interview(request_payload(sample_size)))


@pytest.mark.parametrize("size", [20, 40, 80])
def test_profile_bank_sample_paths_and_deterministic_8_to_2(monkeypatch, size):
    result = execute(monkeypatch, size)
    assert result["contract"] == "market-interview-result-v2"
    assert result["synthetic"] is True
    assert result["targeting"]["drawnSampleSize"] == size
    assert result["targeting"]["attemptedCount"] == size
    assert result["targeting"]["usableCount"] == size
    assert result["targeting"]["failedCount"] == 0
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
    monkeypatch.setattr(service, "execute_market_interview_prompt", tracked)
    with pytest.raises(ProviderFailure) as failure:
        asyncio.run(service.execute_market_interview(request_payload()))
    assert failure.value.reason == "MARKET_INTERVIEW_TARGET_UNAVAILABLE"
    assert calls == ["market_interview_target_criteria_v2"]


def test_targeting_uses_problem_context_and_actual_bank_taxonomy(monkeypatch):
    seen = {}
    real = provider()
    async def tracked(system, user, **kwargs):
        if kwargs["schema_name"] == "market_interview_target_criteria_v2":
            seen["system"] = system
            seen["user"] = json.loads(user)
        return await real(system, user, **kwargs)

    payload = request_payload()
    payload["selectedConcept"]["solution"]["problemScenario"] = "예약 누락으로 반복 업무가 생긴다"
    monkeypatch.setattr(deep_engine, "load_bank", lambda: bank())
    monkeypatch.setattr(service, "execute_market_interview_prompt", tracked)

    asyncio.run(service.execute_market_interview(payload))

    assert seen["user"] == {"targetUsers": "서울 소규모 매장",
                             "problemScenario": "예약 누락으로 반복 업무가 생긴다"}
    assert "실제 shipped profile bank 광역지역 어휘" in seen["system"]
    assert "서울" in seen["system"] and "전업주부" in seen["system"]


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


@pytest.mark.parametrize("claim", ["응답자의 75%가 구매한다.", "75%의 응답자가 구매한다.",
    "고객의 80%가 선호한다.", "80%의 고객이 선호한다.",
    "응답자 중 75%가 구매한다.", "고객 중 80%가 선호한다.",
    "참여자 중 60%가 긍정적이다.", "20명 중 15명(75%)이 긍정적이다.",
    "대부분의 고객이 구매한다.", "실제 사용자들은 만족한다.", "구매 확률은 70%다.",
    "구매 전환율은 35%다."])
def test_population_or_statistical_claim_is_rejected(monkeypatch, claim):
    with pytest.raises(ProviderFailure):
        execute(monkeypatch, claim=claim)


def _with_usable_count(result, usable):
    provenance = list(result["transcriptProvenance"])
    kept = provenance[:usable]
    kept_ids = {row["participantId"] for row in kept}
    groups = {row["participantId"]: row["group"] for row in provenance}
    result["transcriptProvenance"] = kept
    result["codingTrace"] = [row for row in result["codingTrace"] if row["participantId"] in kept_ids]
    result["participants"] = [row for row in result["participants"] if row["participantId"] in kept_ids]
    result["interviews"] = [row for row in result["interviews"] if row["participantId"] in kept_ids]
    result["respondentFailures"] = [{
        "participantId": row["participantId"], "group": row["group"], "attempts": 1,
        "code": "PERMANENT_PROVIDER_FAILURE",
    } for row in provenance[usable:]]
    result["targeting"].update({
        "usableCount": usable,
        "failedCount": len(provenance) - usable,
        "targetCount": sum(row["group"] == "TARGET" for row in kept),
        "nonTargetCount": sum(row["group"] == "COMPARISON" for row in kept),
    })
    for theme in result["themes"]:
        ids = [item for item in theme["participantIds"] if item in kept_ids]
        theme.update({
            "participantIds": ids,
            "mentionCount": len(ids),
            "targetCount": sum(groups[item] == "TARGET" for item in ids),
            "nonTargetCount": sum(groups[item] == "COMPARISON" for item in ids),
        })
    for relation in result["crossRelationships"]:
        ids = [item for item in relation["respondentIds"] if item in kept_ids]
        relation.update({"respondentIds": ids, "overlapCount": len(ids)})
    comprehension = Counter(row["comprehension"] for row in result["codingTrace"])
    differentiation = Counter(row["differentiation"] for row in result["codingTrace"])
    result["comprehension"] = {name: comprehension[name]
                               for name in ("accurate", "partial", "misunderstood")}
    result["differentiation"] = {name: differentiation[name]
                                 for name in ("different", "similar", "unclear")}
    result["saturation"].update({
        "participantCount": usable,
        "codedParticipantCount": usable,
        "alternativeSum": usable,
        "maxMentionByAxis": {axis: max(
            (theme["mentionCount"] for theme in result["themes"] if theme["axis"] == axis),
            default=0,
        ) for axis in ("LIKE", "CONCERN", "DIFFERENTIATION", "USAGE_SCENE", "BARRIER", "SUGGESTION")},
    })
    return result


def test_result_contract_rejects_usable_sample_below_requested_half(monkeypatch):
    assert MarketInterviewResult.model_validate(_with_usable_count(execute(monkeypatch, 80), 40))
    with pytest.raises(ValidationError):
        MarketInterviewResult.model_validate(_with_usable_count(execute(monkeypatch, 80), 39))


def test_result_contract_rejects_targeting_counts_not_derived_from_provenance(monkeypatch):
    result = execute(monkeypatch)
    result["targeting"]["targetCount"] -= 1
    result["targeting"]["nonTargetCount"] += 1
    with pytest.raises(ValidationError):
        MarketInterviewResult.model_validate(result)


def test_result_contract_rejects_theme_counts_not_derived_from_membership(monkeypatch):
    result = execute(monkeypatch)
    result["themes"][0]["targetCount"] -= 1
    result["themes"][0]["nonTargetCount"] += 1
    with pytest.raises(ValidationError):
        MarketInterviewResult.model_validate(result)


def test_result_contract_rejects_duplicate_theme_respondent_id(monkeypatch):
    result = execute(monkeypatch)
    result["themes"][0].update({
        "participantIds": ["R001", "R001"], "mentionCount": 2,
        "targetCount": 2, "nonTargetCount": 0,
    })
    with pytest.raises(ValidationError):
        MarketInterviewResult.model_validate(result)


def test_result_contract_rejects_duplicate_cross_relationship_respondent_id(monkeypatch):
    result = execute(monkeypatch)
    result["crossRelationships"][0].update({"respondentIds": ["R001", "R001"], "overlapCount": 2})
    with pytest.raises(ValidationError):
        MarketInterviewResult.model_validate(result)


def test_transient_respondent_failure_retries_once_then_succeeds(monkeypatch):
    base = provider()
    attempts = Counter()

    async def flaky(*args, **kwargs):
        payload = json.loads(args[1])
        if kwargs["schema_name"] == "market_interview_answer_v2":
            rid = payload["participantId"]
            attempts[rid] += 1
            if rid == "R001" and attempts[rid] == 1:
                raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True)
        return await base(*args, **kwargs)

    result = execute_with_provider(monkeypatch, flaky)
    assert attempts["R001"] == 2
    assert result["targeting"]["usableCount"] == 20
    assert result["respondentFailures"] == []


def test_permanent_respondent_failure_keeps_other_responses_and_truthful_counts(monkeypatch):
    base = provider()
    calls = Counter()

    async def partially_failing(*args, **kwargs):
        payload = json.loads(args[1])
        if kwargs["schema_name"] == "market_interview_answer_v2":
            rid = payload["participantId"]
            calls[rid] += 1
            if rid == "R001":
                raise ProviderFailure("EXECUTION_FAILED", "PERMANENT_EXECUTION_FAILURE", 500, False)
        return await base(*args, **kwargs)

    result = execute_with_provider(monkeypatch, partially_failing)
    sampled = {row["participantId"] for row in result["transcriptProvenance"]}
    assert calls["R001"] == 1
    assert "R001" not in sampled
    assert result["targeting"]["attemptedCount"] == 20
    assert result["targeting"]["usableCount"] == 19
    assert result["targeting"]["failedCount"] == 1
    assert result["targeting"]["targetCount"] + result["targeting"]["nonTargetCount"] == 19
    assert result["respondentFailures"] == [{
        "participantId": "R001", "group": "TARGET", "attempts": 1,
        "code": "PERMANENT_PROVIDER_FAILURE",
    }]
    assert all(theme["mentionCount"] == 19 and "R001" not in theme["participantIds"]
               for theme in result["themes"])
    assert any("유효 응답 19명" in item for item in result["limitations"])


def test_usable_sample_below_half_fails_closed(monkeypatch):
    base = provider()

    async def insufficient(*args, **kwargs):
        payload = json.loads(args[1])
        if (kwargs["schema_name"] == "market_interview_answer_v2"
                and int(payload["participantId"][1:]) <= 11):
            raise ProviderFailure("EXECUTION_FAILED", "PERMANENT_EXECUTION_FAILURE", 500, False)
        return await base(*args, **kwargs)

    with pytest.raises(ProviderFailure) as failure:
        execute_with_provider(monkeypatch, insufficient)
    assert failure.value.reason == "MARKET_INTERVIEW_USABLE_SAMPLE_INSUFFICIENT"


def test_eighty_respondents_never_exceed_configured_concurrency(monkeypatch):
    base = provider()
    active = 0
    maximum = 0

    async def measured(*args, **kwargs):
        nonlocal active, maximum
        if kwargs["schema_name"] != "market_interview_answer_v2":
            return await base(*args, **kwargs)
        active += 1
        maximum = max(maximum, active)
        try:
            await asyncio.sleep(0.002)
            return await base(*args, **kwargs)
        finally:
            active -= 1

    monkeypatch.setattr(deep_engine, "interview_concurrency", lambda: 3)
    result = execute_with_provider(monkeypatch, measured, 80)
    assert result["targeting"]["usableCount"] == 80
    assert maximum <= 3
