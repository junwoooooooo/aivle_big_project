import asyncio
import json

import pytest

from app.providers import ProviderFailure
from app.tasks.concept_legal_review import service


def candidate():
    return {
        "conceptName": "예약 도우미", "oneLineSummary": "예약 확인을 돕습니다.",
        "targetSegment": "예약 서비스 이용자", "problemScenario": "반복 확인이 필요합니다.",
        "valueProposition": "확인 업무를 줄입니다.", "solutionMechanism": "온라인 알림과 예약 관리를 제공합니다.",
        "actorRoles": ["이용자", "예약 사업자"], "platformRole": "예약 중개",
        "transactionFlow": ["이용자가 예약", "사업자가 확정"], "dataFlow": ["연락처 처리"],
        "physicalActivities": [], "partnerRequirements": [], "featureSet": ["예약 알림"],
        "channelHypothesis": "웹과 앱", "pricingHypothesis": "월 구독",
        "revenueModelHypothesis": "사업자 구독", "operatingModel": "예약 중개 운영",
        "assumptions": [], "risks": [], "legalImplementationHypothesis": "개인정보 최소 처리",
    }


def task_input():
    return {"candidate": candidate(), "sharedContext": {
        "sourceSnapshotHash": "sha256:" + "a" * 64,
        "registryVersion": "legal-registry-v1",
        "fields": [
            {"fieldKey": "problem", "value": "예약 확인 업무", "provenance": "SOURCE_EXTRACTED"},
            {"fieldKey": "targetCustomers", "value": "예약 서비스 이용자", "provenance": "SOURCE_EXTRACTED"},
            {"fieldKey": "usageContext", "value": "온라인 예약", "provenance": "SOURCE_EXTRACTED"},
            {"fieldKey": "targetRegion", "value": "대한민국", "provenance": "SOURCE_EXTRACTED"},
            {"fieldKey": "personalData", "value": "예약 연락처", "provenance": "SOURCE_EXTRACTED"},
        ],
    }}


def evidence(evidence_id="EVD-001"):
    return {
        "evidenceId": evidence_id, "routeId": "personal_data", "category": "PRIVACY_AND_DATA",
        "registryVersion": "legal-registry-v1", "lawName": "개인정보 보호법", "article": "제30조",
        "title": "개인정보 처리방침의 수립 및 공개", "role": "REQUIREMENT",
        "plainSummary": "개인정보 처리방침을 수립하고 공개해야 합니다.",
        "whyRelevant": "예약 연락처를 처리하기 때문입니다.", "excerpt": "제한된 조문 원문",
        "effectiveDate": "2025-03-13", "lawUrl": "https://www.law.go.kr/법령/개인정보보호법",
        "verifiedAt": "2026-08-07T00:00:00Z", "sourceType": "OFFICIAL_LAW",
        "lawId": "LAW-100", "officialIdentifier": "MST-100", "articleReference": "제30조",
        "officialSourceUri": "https://www.law.go.kr/법령/개인정보보호법", "jurisdiction": "KR",
        "promulgationDate": "2025-01-01", "retrievedAt": "2026-08-07T00:00:00Z",
        "contentHash": "sha256:" + "b" * 64, "boundedOfficialText": "제한된 조문 원문",
        "queryKey": "sha256:" + "c" * 64,
    }


def source(values=None):
    return {"taskType": "CONCEPT_LEGAL_VALIDATION", "sourceStatus": "SOURCE_COMPLETE",
        "registryVersion": "legal-registry-v1", "routes": [], "findings": [],
        "evidence": [evidence()] if values is None else values,
        "requiredUserInputs": [], "sourceWarnings": []}


def provider(index=0, coverage=True):
    return {"status": "IMPLEMENTABLE_WITH_CONTROLS", "reviewedActivities": ["예약 연락처 처리"],
        "requiredControls": ["개인정보 처리방침을 공개합니다."],
        "requiredPartnersAndQualifications": [], "requiredDisclosures": [], "prohibitedVariants": [],
        "unknownFacts": [], "evidenceReferenceIndexes": [index],
        "findingEvidence": ([{"findingType": "requiredControls", "findingIndex": 0,
            "evidenceReferenceIndexes": [index]}] if coverage else []),
        "expertReviewRecommended": True, "reviewBasisDate": "2026-08-07",
        "safeUserSummary": "공식 근거에 따른 통제를 반영하면 구현 가능성이 있습니다."}


def execute(monkeypatch, source_result=None, provider_result=None):
    captured = {}
    async def fake_source(*_): return source_result if source_result is not None else source()
    async def fake_provider(_system, user, **_):
        captured.update(json.loads(user))
        return provider_result if provider_result is not None else provider()
    monkeypatch.setattr(service, "execute_legal_source_pipeline", fake_source)
    monkeypatch.setattr(service, "execute_structured_prompt", fake_provider)
    return asyncio.run(service.execute_concept_legal_review(task_input())), captured


def test_canonical_context_and_candidate_activities_feed_official_retrieval(monkeypatch):
    result, captured = execute(monkeypatch)
    assert result["status"] == "IMPLEMENTABLE_WITH_CONTROLS"
    assert captured["canonicalContext"][0]["provenance"] == "SOURCE_EXTRACTED"
    assert captured["candidate"]["platformRole"] == "예약 중개"
    assert captured["officialEvidence"][0]["articleReference"] == "제30조"


def test_duplicate_official_evidence_is_removed(monkeypatch):
    duplicate = evidence("EVD-002")
    result, captured = execute(monkeypatch, source([evidence(), duplicate]))
    assert len(captured["officialEvidence"]) == 1
    assert len(result["officialEvidence"]) == 1


def test_empty_evidence_cannot_pass_and_becomes_needs_facts(monkeypatch):
    result, captured = execute(monkeypatch, source([]))
    assert result["status"] == "NEEDS_FACTS"
    assert result["evidenceReferenceIndexes"] == []
    assert captured == {}


def test_invalid_evidence_index_is_rejected(monkeypatch):
    with pytest.raises(ProviderFailure) as raised:
        execute(monkeypatch, provider_result=provider(index=99))
    assert raised.value.reason == "EVIDENCE_REFERENCE_INVALID"


def test_each_material_finding_requires_evidence(monkeypatch):
    with pytest.raises(ProviderFailure) as raised:
        execute(monkeypatch, provider_result=provider(coverage=False))
    assert raised.value.reason == "CONCEPT_LEGAL_FINDING_EVIDENCE_REQUIRED"


def test_source_timeout_remains_retryable_dependency_failure(monkeypatch):
    async def timeout(*_):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MOLEG_DEPENDENCY_UNAVAILABLE", 503, True)
    monkeypatch.setattr(service, "execute_legal_source_pipeline", timeout)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_concept_legal_review(task_input()))
    assert raised.value.retryable is True
    assert raised.value.reason == "MOLEG_DEPENDENCY_UNAVAILABLE"


def test_user_safe_result_has_source_metadata_without_raw_provider_or_official_text(monkeypatch):
    result, _ = execute(monkeypatch)
    serialized = json.dumps(result, ensure_ascii=False)
    assert result["officialEvidence"][0]["lawName"] == "개인정보 보호법"
    assert result["officialEvidence"][0]["effectiveDate"] == "2025-03-13"
    assert "boundedOfficialText" not in serialized
    assert "제한된 조문 원문" not in serialized
    assert "providerBody" not in serialized
