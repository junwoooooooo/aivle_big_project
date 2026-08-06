import asyncio
import copy

import pytest

from app.legal import boundary
from app.services.journey_provider import ProviderFailure


def source_result():
    evidence = {
        "evidenceId": "OLD-1", "routeId": "privacy", "category": "PRIVACY_AND_DATA",
        "registryVersion": "legal-registry-v1", "lawName": "개인정보 보호법", "article": "제15조",
        "title": "개인정보의 수집·이용", "role": "REQUIREMENT", "plainSummary": "필요한 정보만 수집한다.",
        "whyRelevant": "위치정보를 처리할 수 있다.", "excerpt": "정보주체의 동의를 받은 경우 수집할 수 있다.",
        "effectiveDate": "2026-01-01", "lawUrl": "https://www.law.go.kr/법령/개인정보보호법",
        "verifiedAt": "2026-08-05T00:00:00Z",
    }
    return {"taskType": "IDEA_LEGAL_PRECHECK", "sourceStatus": "SOURCE_COMPLETE",
        "registryVersion": "legal-registry-v1", "routes": [{"routeId": "privacy", "topic": "개인정보",
            "status": "APPLIES", "evidenceQuotes": ["위치"], "reason": "위치정보 처리", "categories": ["PRIVACY_AND_DATA"]}],
        "findings": [], "evidence": [evidence, {**evidence, "evidenceId": "OLD-2"}],
        "requiredUserInputs": [], "sourceWarnings": []}


def normalized():
    return {"rules": [{"ruleId": "RULE-1", "ruleType": "REQUIRED_CONTROL", "structureKey": "locationData",
        "title": "위치정보 최소 처리", "description": "반납 지점 추천에 필요한 통제다.",
        "normalizedRequirement": "추천 기능에 필요한 최소 위치정보만 목적 범위에서 처리한다.",
        "evidenceIds": ["EVD-001"], "severity": "HIGH", "sourceStatus": "COMPLETE",
        "appliesWhen": {"collectsLocationData": True}, "userFacingReason": "불필요한 위치 추적을 방지한다.",
        "alternatives": ["사용자가 직접 지역을 선택한다."], "requiredQualifications": [],
        "requiredPartnerRole": None, "requiredDisclosure": "수집 목적과 보관 기간을 고지한다.",
        "affectedBriefFields": ["regulatorySensitiveActivities"], "professionalReviewRecommended": False,
        "userActionOptions": []}], "questions": [], "conflicts": [], "userActionOptions": []}


def task_input(locked=False):
    return {"confirmedBriefVersionId": 1, "confirmedBriefHash": "sha256:" + "a" * 64,
        "briefFields": [{"fieldKey": "regulatorySensitiveActivities", "value": ["위치정보"],
            "decisionStatus": "LOCKED" if locked else "PREFERRED", "sourceType": "USER_CONFIRMED",
            "userConfirmed": True}], "mode": "FULL", "rerunCategories": [], "confirmedFacts": [],
        "registryVersion": "legal-registry-v1"}


def execute(monkeypatch, normalized_result=None, source=None, locked=False):
    async def fake_source(*_): return source or source_result()
    async def fake_provider(*_): return normalized_result or normalized()
    monkeypatch.setattr(boundary, "execute_legal_source_pipeline", fake_source)
    monkeypatch.setattr(boundary, "execute_structured_prompt", fake_provider)
    return asyncio.run(boundary.execute_regulatory_boundary("brief", task_input(locked)))


def test_normalizes_only_from_official_evidence_and_deduplicates_category_copies(monkeypatch):
    result = execute(monkeypatch)
    assert result["status"] == "READY"
    assert len(result["evidence"]) == 1
    assert result["rules"][0]["evidenceIds"] == ["EVD-001"]


def test_rejects_unknown_normalization_field(monkeypatch):
    value = normalized(); value["unknown"] = True
    with pytest.raises(ProviderFailure) as failure: execute(monkeypatch, value)
    assert failure.value.reason == "BOUNDARY_NORMALIZATION_CONTRACT_INVALID"


@pytest.mark.parametrize("rule_type", ["LEGAL_TITLE", "PASS", "DENY"])
def test_rejects_unknown_rule_type(monkeypatch, rule_type):
    value = normalized(); value["rules"][0]["ruleType"] = rule_type
    with pytest.raises(ProviderFailure): execute(monkeypatch, value)


def test_rejects_missing_or_invented_evidence_reference(monkeypatch):
    value = normalized(); value["rules"][0]["evidenceIds"] = ["INVENTED-9"]
    with pytest.raises(ProviderFailure) as failure: execute(monkeypatch, value)
    assert failure.value.reason == "BOUNDARY_EVIDENCE_REFERENCE_INVALID"


def test_rejects_plain_summary_copied_as_normalized_requirement(monkeypatch):
    value = normalized(); value["rules"][0]["normalizedRequirement"] = "필요한 정보만 수집한다."
    with pytest.raises(ProviderFailure) as failure: execute(monkeypatch, value)
    assert failure.value.reason == "BOUNDARY_RULE_NOT_NORMALIZED"


def test_deduplicates_same_canonical_rule_and_merges_evidence(monkeypatch):
    value = normalized(); duplicate = copy.deepcopy(value["rules"][0]); duplicate["ruleId"] = "RULE-2"
    duplicate["normalizedRequirement"] = "  추천 기능에 필요한 최소 위치정보만 목적 범위에서 처리한다.  "
    value["rules"].append(duplicate)
    result = execute(monkeypatch, value)
    assert len(result["rules"]) == 1


def test_distinguishes_needs_input_from_locked_conflict(monkeypatch):
    needs = normalized(); needs["questions"] = [{"questionId": "Q-1", "fieldKey": "targetRegion",
        "question": "어느 지역에서 운영합니까?", "reason": "적용 법령 범위를 정해야 합니다.",
        "answerType": "TEXT", "options": [], "required": True, "relatedRuleIds": [], "relatedEvidenceIds": []}]
    assert execute(monkeypatch, needs)["status"] == "NEEDS_INPUT"
    blocked = normalized(); blocked["conflicts"] = [{"conflictId": "C-1",
        "affectedFieldKey": "regulatorySensitiveActivities", "lockedValue": ["위치정보"],
        "conflictingRuleIds": ["RULE-1"], "reason": "현재 고정 방식은 통제 조건과 충돌합니다.",
        "userActionOptions": ["해당 조건을 OPEN으로 변경"]}]
    assert execute(monkeypatch, blocked, locked=True)["status"] == "BLOCKED"


def test_rejects_conflict_for_non_locked_field(monkeypatch):
    value = normalized(); value["conflicts"] = [{"conflictId": "C-1",
        "affectedFieldKey": "regulatorySensitiveActivities", "lockedValue": ["위치정보"],
        "conflictingRuleIds": ["RULE-1"], "reason": "충돌", "userActionOptions": ["수정"]}]
    with pytest.raises(ProviderFailure) as failure: execute(monkeypatch, value, locked=False)
    assert failure.value.reason == "BOUNDARY_LOCKED_CONFLICT_INVALID"
