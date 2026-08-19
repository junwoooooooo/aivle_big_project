import asyncio
import json
from collections import Counter

import pytest
from pydantic import ValidationError

from app.providers import ProviderFailure
from app.tasks.market_interview import deep_engine, service
from app.tasks.market_interview.models import MarketInterviewResult, TargetCriteria
from app.tasks.market_interview.models import MarketInterviewInput
from app.tasks.market_interview.questions import concept_board
from app.tasks.market_interview.semantic_integrity import assert_semantic_integrity


def request_payload(sample_size=20):
    return {
        "contract": "market-interview-input-v2", "schemaVersion": "2.0", "synthetic": True,
        "sampleSize": sample_size,
        "source": {"conceptRefinementFinalId": 17, "marketSeedSnapshotId": "seed-1", "selectionId": 31, "selectionRevision": 4,
                   "marketSeedSnapshotHash": "sha256:" + "a" * 64, "bmPlanRevision": 3},
        "conceptBoard": {"conceptName": "예약 도우미", "targetUsers": "서울 소규모 매장",
                         "problemScenario": "예약 누락으로 반복 업무가 생긴다",
                         "featureSet": ["예약 확인"], "differentiators": "예약 누락 방지", "priceKrw": 9900},
        "selectedConcept": {"identity": {"name": "예약 도우미", "targetUsers": "서울 소규모 매장"},
                            "solution": {"featureSet": ["예약 확인"]}},
        "validatedHypotheses": {}, "businessModel": {"plan": {}, "constraints": {}},
        "targetingContext": {"marketSeries": "B", "customerUnit": "PERSON",
                             "buyerType": "PERSON_BUYER", "denominator": "대상 개인 수",
                             "reason": "개인 이용자 조건으로 표집한다."},
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
        if name in {"market_interview_assignment_v3", "market_interview_assignment_single_v1"}:
            rows = []
            for item in payload["transcripts"]:
                selected = ["없는 주제"] if unknown_theme else [row["title"] for row in THEMES]
                rows.append({"participantId": item["participantId"],
                             "themeTitles": selected,
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
    payload["conceptBoard"]["problemScenario"] = "예약 누락으로 반복 업무가 생긴다"
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


def test_coding_batch_order_is_transport_only_and_evidence_is_server_derived(monkeypatch):
    base = provider()
    schemas = []

    async def reordered(*args, **kwargs):
        value = await base(*args, **kwargs)
        if kwargs["schema_name"] == "market_interview_assignment_v3":
            schemas.append(kwargs["response_schema"])
            value["assignments"].reverse()
        return value

    result = execute_with_provider(monkeypatch, reordered)
    assert [row["participantId"] for row in result["codingTrace"]] == [
        row["participantId"] for row in result["transcriptProvenance"]
    ]
    assert "themeEvidence" not in json.dumps(schemas, ensure_ascii=False)
    answers = {row["participantId"]: row for row in result["interviews"]}
    for trace in result["codingTrace"]:
        original_answers = [item["answer"] for item in answers[trace["participantId"]]["questions"]]
        assert all(any(evidence["quote"] in answer for answer in original_answers)
                   for evidence in trace["themeEvidence"])


@pytest.mark.parametrize("broken_ids", [{"R005"}, {"R005", "R007"}])
def test_partial_batch_preserves_valid_rows_and_single_recovers_only_broken(monkeypatch, broken_ids):
    base = provider()
    single_ids = []

    async def partial(*args, **kwargs):
        value = await base(*args, **kwargs)
        payload = json.loads(args[1])
        if kwargs["schema_name"] == "market_interview_assignment_v3":
            value["assignments"] = [row for row in value["assignments"]
                                    if row["participantId"] not in broken_ids]
        elif kwargs["schema_name"] == "market_interview_assignment_single_v1":
            single_ids.append(payload["transcripts"][0]["participantId"])
        return value

    result = execute_with_provider(monkeypatch, partial)
    assert set(single_ids) == broken_ids
    assert len(result["codingTrace"]) == 20


def test_two_schema_invalid_rows_use_single_fallback_without_recalling_valid_rows(monkeypatch):
    base = provider()
    broken_ids = {"R005", "R007"}
    single_ids = []

    async def invalid_classification(*args, **kwargs):
        value = await base(*args, **kwargs)
        payload = json.loads(args[1])
        if kwargs["schema_name"] == "market_interview_assignment_v3":
            for row in value["assignments"]:
                if row["participantId"] in broken_ids:
                    row["comprehension"] = "unsupported"
        elif kwargs["schema_name"] == "market_interview_assignment_single_v1":
            single_ids.append(payload["transcripts"][0]["participantId"])
        return value

    result = execute_with_provider(monkeypatch, invalid_classification)
    assert set(single_ids) == broken_ids
    assert len(result["codingTrace"]) == 20


def test_final_batch_partial_failure_does_not_discard_first_32(monkeypatch):
    base = provider()
    batch_starts = Counter()
    single_ids = []

    async def final_batch_partial(*args, **kwargs):
        value = await base(*args, **kwargs)
        payload = json.loads(args[1])
        if kwargs["schema_name"] == "market_interview_assignment_v3":
            first_id = payload["transcripts"][0]["participantId"]
            batch_starts[first_id] += 1
            if any(item["participantId"] == "R035" for item in payload["transcripts"]):
                value["assignments"] = [row for row in value["assignments"]
                                        if row["participantId"] != "R035"]
        elif kwargs["schema_name"] == "market_interview_assignment_single_v1":
            single_ids.append(payload["transcripts"][0]["participantId"])
        return value

    result = execute_with_provider(monkeypatch, final_batch_partial, sample_size=40)
    assert single_ids == ["R035"]
    assert batch_starts["R001"] == batch_starts["R009"] == batch_starts["R017"] == batch_starts["R025"] == 1
    assert len(result["codingTrace"]) == 40


def test_unknown_themes_become_zero_theme_without_dropping_respondent(monkeypatch):
    base = provider()

    async def unknown_for_one(*args, **kwargs):
        value = await base(*args, **kwargs)
        if kwargs["schema_name"] == "market_interview_assignment_v3":
            for row in value["assignments"]:
                if row["participantId"] == "R001":
                    row["themeTitles"] = ["없는 주제"]
        return value

    result = execute_with_provider(monkeypatch, unknown_for_one)
    trace = next(row for row in result["codingTrace"] if row["participantId"] == "R001")
    assert trace["themeTitles"] == [] and trace["themeEvidence"] == []
    assert "R001" in {row["participantId"] for row in result["interviews"]}
    assert result["saturation"]["codedParticipantCount"] == 20


@pytest.mark.parametrize("sample_size", [20, 40])
def test_single_coding_failure_degrades_to_unclassified_and_keeps_all_transcripts(monkeypatch, sample_size):
    base = provider()
    broken_id = "R005"

    async def broken_last_respondent(*args, **kwargs):
        value = await base(*args, **kwargs)
        payload = json.loads(args[1])
        if kwargs["schema_name"] == "market_interview_assignment_v3":
            value["assignments"] = [row for row in value["assignments"]
                                    if row["participantId"] != broken_id]
        elif kwargs["schema_name"] == "market_interview_assignment_single_v1" \
                and payload["transcripts"][0]["participantId"] == broken_id:
            value["assignments"][0]["comprehension"] = "unsupported"
        return value

    result = execute_with_provider(monkeypatch, broken_last_respondent, sample_size=sample_size)
    trace = next(row for row in result["codingTrace"] if row["participantId"] == broken_id)
    assert result["usableInterviewCount"] == sample_size
    assert result["codedInterviewCount"] == sample_size - 1
    assert result["codingFailureCount"] == 1
    assert trace == {
        "participantId": broken_id, "themeTitles": [], "themeEvidence": [],
        "comprehension": "unclassified", "differentiation": "unclassified",
        "alternativeLabel": "", "group": trace["group"], "classificationStatus": "UNCLASSIFIED",
    }
    assert broken_id in {row["participantId"] for row in result["interviews"]}
    assert all(broken_id not in theme["participantIds"] for theme in result["themes"])


def test_deterministic_excerpt_is_bounded_sentence_span_from_original_answer():
    answer = ("첫 문장입니다. " * 45) + "마지막 원문입니다."
    quote = deep_engine._deterministic_excerpt(answer)
    assert 1 <= len(quote) <= 500
    assert quote in answer
    assert quote.endswith(".")


def test_codebook_contract_gets_one_correction_call(monkeypatch):
    base = provider()
    calls = 0

    async def corrected(*args, **kwargs):
        nonlocal calls
        value = await base(*args, **kwargs)
        if kwargs["schema_name"] == "market_interview_codebook_v2":
            calls += 1
            if calls == 1:
                value["themes"][1]["title"] = value["themes"][0]["title"]
        return value

    result = execute_with_provider(monkeypatch, corrected)
    assert calls == 2
    assert result["targeting"]["usableCount"] == 20


def test_quote_resolver_recovers_original_typography_and_whitespace_span():
    answer = "고객은\u200b  “가격” — 조건을\n확인합니다."
    resolved = deep_engine._resolve_original_quote(answer, '"가격" - 조건을 확인합니다.')
    assert resolved == "“가격” — 조건을\n확인합니다."
    assert resolved in answer


def test_quote_resolver_rejects_paraphrase():
    assert deep_engine._resolve_original_quote(
        "가격 조건을 직접 확인합니다.", "가격이 합리적인지 살펴봅니다.",
    ) is None


def test_exclusion_block_reason_and_repair_attempts_are_safe_diagnostics():
    failure = deep_engine.CodingValidationFailure(
        "VERBATIM_QUOTE_MISMATCH", "codingBatches[0].assignments[3].themeEvidence[0].quote",
        batch_index=0, participant_id="R005",
    ).with_recovery(repair_attempts=1, exclusion_attempted=True,
                    exclusion_blocked_reason="TARGET_COVERAGE_THRESHOLD")
    provider_failure = deep_engine._coding_failure(failure)
    assert provider_failure.safe_diagnostics == {
        "stage": "CODING_EVIDENCE_VALIDATION", "batchIndex": 0,
        "rule": "VERBATIM_QUOTE_MISMATCH",
        "path": "codingBatches[0].assignments[3].themeEvidence[0].quote",
        "repairAttempts": 1, "exclusionAttempted": True,
        "participantId": "R005", "exclusionBlockedReason": "TARGET_COVERAGE_THRESHOLD",
    }


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
        "nonTargetCount": sum(row["group"] != "TARGET" for row in kept),
        "proxyCount": sum(row["group"] == "PROXY" for row in kept),
        "exploratoryCount": sum(row["group"] == "EXPLORATORY" for row in kept),
    })
    for theme in result["themes"]:
        ids = [item for item in theme["participantIds"] if item in kept_ids]
        theme.update({
            "participantIds": ids,
            "mentionCount": len(ids),
            "targetCount": sum(groups[item] == "TARGET" for item in ids),
            "nonTargetCount": sum(groups[item] != "TARGET" for item in ids),
        })
    for relation in result["crossRelationships"]:
        ids = [item for item in relation["respondentIds"] if item in kept_ids]
        relation.update({"respondentIds": ids, "overlapCount": len(ids)})
    comprehension = Counter(row["comprehension"] for row in result["codingTrace"])
    differentiation = Counter(row["differentiation"] for row in result["codingTrace"])
    result["comprehension"] = {name: comprehension[name]
                               for name in ("accurate", "partial", "misunderstood", "unclassified")}
    result["differentiation"] = {name: differentiation[name]
                                 for name in ("different", "similar", "unclear", "unclassified")}
    coded = sum(row["classificationStatus"] == "CODED" for row in result["codingTrace"])
    result.update({"usableInterviewCount": usable, "codedInterviewCount": coded,
                   "codingFailureCount": usable - coded})
    result["saturation"].update({
        "participantCount": usable,
        "codedParticipantCount": coded,
        "usableInterviewCount": usable,
        "codedInterviewCount": coded,
        "codingFailureCount": usable - coded,
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


def test_current_bicycle_concept_board_preserves_semantic_nouns():
    payload = request_payload()
    payload["selectedConcept"] = {
        "identity": {"conceptName": "스마트 킥포인트 - 데이터 분석 서비스",
                     "conceptDefinition": "AI 카메라 데이터로 자전거 관리 효율을 높이는 서비스",
                     "targetUsers": ["자전거 대여 운영 조직", "지자체"]},
        "solution": {"problemScenario": "방치된 공유 자전거의 회수와 재배치가 늦다",
                     "solutionMechanism": "AI 카메라로 자전거 주차 상태를 분석한다",
                     "featureSet": ["자전거 상태 모니터링"]},
    }
    payload["conceptBoard"] = {
        "conceptName": "스마트 킥포인트 - 데이터 분석 서비스",
        "targetUsers": "자전거 대여 운영 조직 · 지자체",
        "problemScenario": "방치된 공유 자전거의 회수와 재배치가 늦다",
        "featureSet": ["자전거 상태 모니터링"],
        "differentiators": "AI 카메라 데이터 분석", "priceKrw": 9900,
    }
    board = concept_board(MarketInterviewInput.model_validate(payload))
    assert "스마트 킥포인트" in board
    assert payload["conceptBoard"]["problemScenario"] in board
    assert payload["conceptBoard"]["differentiators"] in board
    assert "이름 미정" not in board


def test_bicycle_source_rejects_automotive_parking_result():
    concept = {"identity": {"conceptName": "자전거 관리 분석", "conceptDefinition": "자전거 대여 관리",
                            "targetUsers": ["자전거 대여 업체", "지자체"]},
               "solution": {"problemScenario": "공유 자전거 방치", "solutionMechanism": "AI 카메라 분석"}}
    result = {"themes": [{"title": "Smart Parking", "description": "Traditional parking management systems and parking spaces"}],
              "interviews": [{"questions": [{"answer": "Urban parking lot management is difficult."}]}],
              "followUpQuestions": ["Which parking space do you use?"]}
    with pytest.raises(ProviderFailure) as failure:
        assert_semantic_integrity(concept, result)
    assert failure.value.code == "MARKET_INTERVIEW_SEMANTIC_MISMATCH"


def test_b2b_organization_bank_is_exploratory_not_whole_bank_target():
    cards, frame = bank()
    no_conditions = TargetCriteria.model_validate({
        "ageMin": 0, "ageMax": 0, "genders": [], "householdSizeMin": 0, "householdSizeMax": 0,
        "regions": [], "incomeKeywords": [], "jobKeywords": [], "hasChildren": 0, "householdRoles": [],
    })
    panel, report = deep_engine.draw_panel(cards, frame, no_conditions, 20,
                                           "자전거 대여 업체와 지자체", "ORGANIZATION")
    assert report["representationStatus"] == "EXPLORATORY_ONLY"
    assert {row["group"] for row in panel} == {"EXPLORATORY"}
    assert "전체가 타겟" not in report["criteriaText"]


def test_theme_mention_count_is_exactly_unique_evidence_respondents(monkeypatch):
    result = execute(monkeypatch, 80)
    theme = result["themes"][0]
    kept = theme["participantIds"][:17]
    removed = set(theme["participantIds"]) - set(kept)
    theme.update({"participantIds": kept, "mentionCount": 17,
                  "targetCount": sum(next(row["group"] for row in result["codingTrace"] if row["participantId"] == rid) == "TARGET" for rid in kept),
                  "nonTargetCount": sum(next(row["group"] for row in result["codingTrace"] if row["participantId"] == rid) != "TARGET" for rid in kept)})
    for trace in result["codingTrace"]:
        if trace["participantId"] in removed:
            trace["themeTitles"] = [title for title in trace["themeTitles"] if title != theme["title"]]
            trace["themeEvidence"] = [item for item in trace["themeEvidence"] if item["themeTitle"] != theme["title"]]
    result["saturation"]["maxMentionByAxis"][theme["axis"]] = 17
    result["saturation"]["saturatedThemes"] = [item for item in result["saturation"]["saturatedThemes"] if theme["title"] not in item]
    validated = MarketInterviewResult.model_validate(result)
    checked = next(item for item in validated.themes if item.title == theme["title"])
    assert checked.mentionCount == len(set(checked.participantIds)) == 17
    assert not removed.intersection(checked.participantIds)
