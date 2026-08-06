import asyncio
import json

from app.legal.moleg import LawMetadata
from app.legal import pipeline
from app.models.legal_source import LegalSourcePipelineResult


class FakeMolegClient:
    async def search_exact(self, law_name):
        return LawMetadata(law_name, "100", "LAW-100", "20260803", "https://law.example/100")

    async def articles(self, metadata):
        return [{"article": "제1조", "title": "처리방침", "text": "개인정보 처리자는 처리방침을 공개해야 한다."}]


async def fake_prompt(system, user):
    if "규제 경로" in system:
        return {"routes": [{"routeId": "personal_data", "status": "APPLIES",
            "evidenceQuotes": ["고객 이메일을 수집한다"], "reason": "개인정보 수집", "confidence": 0.95}],
            "additionalRouteCandidates": [], "missingInformation": []}
    return {"screenings": [{"citationId": "CIT-001", "role": "REQUIREMENT",
        "plainSummary": "개인정보 처리방침을 공개해야 합니다.",
        "whyRelevant": "고객 이메일을 수집하기 때문입니다."}]}


def test_legal_source_pipeline_contract(monkeypatch):
    monkeypatch.setenv("LEGAL_REGISTRY_VERSION", "legal-registry-v1")
    monkeypatch.setattr(pipeline, "MolegClient", FakeMolegClient)
    monkeypatch.setattr(pipeline, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(pipeline.execute_legal_source_pipeline("IDEA_LEGAL_PRECHECK",
        "고객 이메일을 수집한다", {"mode": "FULL", "rerunCategories": [],
            "confirmedFacts": [], "registryVersion": "legal-registry-v1"}))
    value = LegalSourcePipelineResult.model_validate(result)
    assert value.sourceStatus == "SOURCE_COMPLETE"
    assert value.registryVersion == "legal-registry-v1"
    assert value.evidence[0].registryVersion == value.registryVersion
    assert value.evidence[0].lawName == "개인정보 보호법"
    assert value.findings[0].reasoning.evidenceIds == [value.evidence[0].evidenceId]


def test_registry_contains_reference_route_set(monkeypatch):
    monkeypatch.setenv("LEGAL_REGISTRY_VERSION", "legal-registry-v1")
    registry = pipeline.LegalRegistry()
    assert len(registry.routes) == 27
    assert registry.categories_for_route("online_sales") == [
        "BUSINESS_REGISTRATION", "CONSUMER_PROTECTION", "TERMS_AND_CONTRACT"]


def test_routing_repairs_invalid_structure_once(monkeypatch):
    responses = [
        {"routes": "invalid"},
        {"routes": [{"routeId": "personal_data", "status": "APPLIES",
            "evidenceQuotes": ["고객 이메일"], "reason": "개인정보 처리", "confidence": 0.9}],
         "additionalRouteCandidates": [], "missingInformation": []},
    ]

    async def prompt(system, user):
        return responses.pop(0)

    monkeypatch.setattr(pipeline, "execute_structured_prompt", prompt)
    result = asyncio.run(pipeline._route("고객 이메일을 수집한다", pipeline.LegalRegistry()))

    assert result.routes[0].routeId == "personal_data"
    assert responses == []


def test_screening_repairs_missing_citation_once(monkeypatch):
    responses = [
        {"screenings": "invalid"},
        {"screenings": [{"citationId": "CIT-001", "role": "REQUIREMENT",
            "plainSummary": "처리방침을 공개해야 합니다.",
            "whyRelevant": "고객 이메일을 수집하기 때문입니다."}]},
    ]

    async def prompt(system, user):
        return responses.pop(0)

    monkeypatch.setattr(pipeline, "execute_structured_prompt", prompt)
    result = asyncio.run(pipeline._screen("고객 이메일을 수집한다", [{
        "citationId": "CIT-001", "routeId": "personal_data", "lawName": "개인정보 보호법",
        "article": "제30조", "title": "개인정보 처리방침", "excerpt": "처리방침을 공개해야 한다.",
    }]))

    assert result.screenings[0].citationId == "CIT-001"
    assert responses == []


def test_screening_uses_bounded_batches_and_preserves_exact_coverage(monkeypatch):
    batch_sizes = []

    async def prompt(system, user):
        payload = json.loads(user)
        candidates = payload["candidates"]
        batch_sizes.append(len(candidates))
        return {"screenings": [], "excludedCitationIds": [
            candidate["citationId"] for candidate in candidates
        ]}

    candidates = [{
        "citationId": f"CIT-{index:03d}",
        "routeId": "personal_data",
        "lawName": "개인정보 보호법",
        "article": f"제{index}조",
        "title": "후보 조문",
        "excerpt": "후보 조문 내용",
    } for index in range(1, 50)]
    monkeypatch.setattr(pipeline, "execute_structured_prompt", prompt)

    result = asyncio.run(pipeline._screen("고객 이메일을 수집한다", candidates))

    assert batch_sizes == [24, 24, 1]
    assert result.excludedCitationIds == [
        candidate["citationId"] for candidate in candidates
    ]


def test_screening_repair_receives_missing_candidate_context(monkeypatch):
    requests = []

    async def prompt(system, user):
        payload = json.loads(user)
        requests.append(payload)
        if "repairContext" not in payload:
            return {"screenings": "invalid"}
        candidate = payload["repairContext"]["candidates"][0]
        return {"screenings": [{
            "citationId": candidate["citationId"],
            "role": "REQUIREMENT",
            "plainSummary": "처리방침을 공개해야 합니다.",
            "whyRelevant": "고객 이메일을 수집하기 때문입니다.",
        }]}

    monkeypatch.setattr(pipeline, "execute_structured_prompt", prompt)
    result = asyncio.run(pipeline._screen("고객 이메일을 수집한다", [{
        "citationId": "CIT-001",
        "routeId": "personal_data",
        "lawName": "개인정보 보호법",
        "article": "제30조",
        "title": "개인정보 처리방침",
        "excerpt": "처리방침을 공개해야 한다.",
    }]))

    assert result.screenings[0].citationId == "CIT-001"
    assert requests[1]["repairContext"]["candidates"][0]["article"] == "제30조"


def test_screening_records_omitted_candidates_without_recursive_calls(monkeypatch):
    requested_batch_sizes = []

    async def prompt(system, user):
        payload = json.loads(user)
        context = payload.get("repairContext", payload)
        candidates = context["candidates"]
        requested_batch_sizes.append(len(candidates))
        return {"screenings": [{
            "citationId": candidates[0]["citationId"],
            "role": "SUPPORTING",
            "plainSummary": "관련 정의입니다.",
            "whyRelevant": "사업 범위 해석에 필요합니다.",
        }]}

    candidates = [{
        "citationId": f"CIT-{index:03d}",
        "routeId": "personal_data",
        "lawName": "개인정보 보호법",
        "article": f"제{index}조",
        "title": "후보 조문",
        "excerpt": "후보 조문 내용",
    } for index in range(1, 5)]
    monkeypatch.setattr(pipeline, "execute_structured_prompt", prompt)

    result = asyncio.run(pipeline._screen("고객 이메일을 수집한다", candidates))

    assert [item.citationId for item in result.screenings] == ["CIT-001"]
    assert result.excludedCitationIds == ["CIT-002", "CIT-003", "CIT-004"]
    assert result.coverageInferred is True
    assert requested_batch_sizes == [4]


def test_screening_discards_unknown_and_duplicate_ids_without_losing_coverage(monkeypatch):
    async def prompt(system, user):
        return {
            "screenings": [
                {"citationId": "CIT-001", "role": "REQUIREMENT",
                 "plainSummary": "신고가 필요합니다.",
                 "whyRelevant": "현재 사업에 적용됩니다."},
                {"citationId": "CIT-999", "role": "SUPPORTING",
                 "plainSummary": "알 수 없는 후보입니다.",
                 "whyRelevant": "현재 묶음 밖입니다."},
            ],
            "excludedCitationIds": ["CIT-001", "CIT-999"],
        }

    monkeypatch.setattr(pipeline, "execute_structured_prompt", prompt)
    result = asyncio.run(pipeline._screen("온라인 서비스를 운영한다", [{
        "citationId": "CIT-001",
        "routeId": "online_sales",
        "lawName": "전자상거래법",
        "article": "제12조",
        "title": "통신판매업자의 신고",
        "excerpt": "통신판매업자는 신고하여야 한다.",
    }]))

    assert [item.citationId for item in result.screenings] == ["CIT-001"]
    assert result.excludedCitationIds == []
