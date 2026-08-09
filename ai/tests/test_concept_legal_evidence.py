import asyncio
import json

import pytest

from app.providers import ProviderFailure
from app.tasks.concept_legal_review import service
from concept_candidate_v2_fixture import valid_legal_fact_pattern


def task_input():
    return {
        "legalFactPattern": valid_legal_fact_pattern(),
        "factPatternHash": "sha256:" + "d" * 64,
        "externalFactContext": {
            "sourceSnapshotHash": "sha256:" + "a" * 64,
            "registryVersion": "legal-registry-v1",
            "facts": [{"factKey": "fixedJurisdiction", "value": "대한민국",
                "source": "USER_INPUT", "authority": "LOCKED"}],
        },
    }


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


def source(values=None, questions=None):
    return {"taskType": "CONCEPT_LEGAL_VALIDATION", "sourceStatus": "SOURCE_COMPLETE",
        "registryVersion": "legal-registry-v1", "routes": [], "findings": [],
        "evidence": [evidence()] if values is None else values,
        "requiredUserInputs": [{"question": question} for question in (questions or [])],
        "sourceWarnings": []}


def provider(index=0, coverage=True, status="IMPLEMENTABLE_WITH_CONTROLS", unknown=None):
    return {"status": status, "reviewedActivities": ["예약 연락처 처리"],
        "requiredControls": [{"text": "개인정보 처리방침을 공개합니다.",
            "evidenceReferenceIndexes": ([index] if coverage else [])}],
        "requiredPartnersAndQualifications": [], "requiredDisclosures": [], "prohibitedVariants": [],
        "evidenceReferenceIndexes": [index], "redesignRequirements": [],
        "unknownFacts": unknown or [],
        "expertReviewRecommended": True, "reviewBasisDate": "2026-08-07",
        "safeUserSummary": "공식 근거에 따른 통제를 반영하면 구현 가능성이 있습니다."}


def execute(monkeypatch, source_result=None, provider_result=None):
    captured = {"sourceCalls": 0}

    async def fake_source(_task_type, input_text, options):
        captured["sourceCalls"] += 1
        captured["sourceInput"] = json.loads(input_text)
        captured["sourceOptions"] = options
        return source_result if source_result is not None else source()

    async def fake_provider(_system, user, **_):
        captured["providerInput"] = json.loads(user)
        return provider_result if provider_result is not None else provider()

    monkeypatch.setattr(service, "execute_legal_source_pipeline", fake_source)
    monkeypatch.setattr(service, "execute_structured_prompt", fake_provider)
    return asyncio.run(service.execute_concept_legal_review(task_input())), captured


def test_concept_fact_pattern_and_legal_hypotheses_precede_legal_review(monkeypatch):
    result, captured = execute(monkeypatch)

    assert result["status"] == "IMPLEMENTABLE_WITH_CONTROLS"
    assert captured["sourceCalls"] == 1
    assert captured["sourceInput"]["legalFactPattern"]["commercialRoles"]["sellerRole"]["value"]
    assert captured["providerInput"]["legalFactPattern"]["paymentFlow"]["value"] == ["사업자가 구독료 결제"]
    assert captured["providerInput"]["legalFactPattern"]["hypotheses"]["revenueModel"]["legalSensitivity"] == "LEGAL_SENSITIVE"
    assert "preMarketSom" not in json.dumps(captured, ensure_ascii=False)


def test_each_concept_runs_its_own_source_pipeline(monkeypatch):
    calls = 0

    async def fake_source(*_):
        nonlocal calls
        calls += 1
        return source()

    async def fake_provider(*_, **__):
        return provider()

    monkeypatch.setattr(service, "execute_legal_source_pipeline", fake_source)
    monkeypatch.setattr(service, "execute_structured_prompt", fake_provider)
    asyncio.run(service.execute_concept_legal_review(task_input()))
    asyncio.run(service.execute_concept_legal_review(task_input()))
    assert calls == 2


def test_duplicate_official_evidence_is_removed(monkeypatch):
    result, captured = execute(monkeypatch, source([evidence(), evidence("EVD-002")]))
    assert len(captured["providerInput"]["officialEvidence"]) == 1
    assert len(result["officialEvidence"]) == 1


def test_design_gap_is_redesignable_without_user_question(monkeypatch):
    result, captured = execute(monkeypatch, source(questions=["플랫폼의 결제 주체와 정산 흐름을 정해야 합니다."]))
    assert result["status"] == "REDESIGNABLE"
    assert result["redesignRequirements"]
    assert result["unknownFacts"] == []
    assert "providerInput" not in captured


def test_only_external_reality_fact_can_be_needs_facts(monkeypatch):
    result, captured = execute(monkeypatch, source(questions=["현재 보유한 필수 영업 인허가가 있습니까?"]))
    assert result["status"] == "NEEDS_FACTS"
    assert result["unknownFacts"]
    assert result["redesignRequirements"] == []
    assert "providerInput" not in captured


def test_control_convertible_external_fact_is_sent_to_evidence_backed_provider(monkeypatch):
    question = "필요한 경우 자격 보유 파트너와 계약하는 통제 조건으로 전환할 수 있습니까?"
    result, captured = execute(monkeypatch, source(questions=[question]))
    assert result["status"] == "IMPLEMENTABLE_WITH_CONTROLS"
    assert captured["providerInput"]["unresolvedExternalFactQuestions"] == [question]


def test_empty_evidence_without_questions_is_retryable_source_failure(monkeypatch):
    with pytest.raises(ProviderFailure) as raised:
        execute(monkeypatch, source([]))
    assert raised.value.retryable is True
    assert raised.value.reason == "LEGAL_SOURCE_EVIDENCE_UNAVAILABLE"


def test_provider_design_gap_cannot_escalate_to_user(monkeypatch):
    result, _ = execute(monkeypatch, provider_result=provider(
        status="NEEDS_FACTS", unknown=["판매자 역할과 결제 주체를 정해야 합니다."]))
    assert result["status"] == "REDESIGNABLE"
    assert result["unknownFacts"] == []


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
    assert result["reviewedFactPatternHash"] == "sha256:" + "d" * 64
    assert "boundedOfficialText" not in serialized
    assert "제한된 조문 원문" not in serialized
    assert "providerBody" not in serialized
