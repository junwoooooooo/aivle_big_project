import asyncio
import json

import pytest

from app.providers import ProviderFailure
from app.tasks.marketing_strategy import execute_marketing_strategy
from app.tasks.marketing_strategy.models import MarketingStrategyInput, MarketingStrategyResult
from app.tasks.marketing_strategy import service


def test_marketing_strategy_contract_import_and_schema():
    assert callable(execute_marketing_strategy)
    value = MarketingStrategyInput.model_validate({
        "contract": "marketing-strategy-input-v1",
        "projectId": 7,
        "sourceManifestHash": "sha256:" + "a" * 64,
        "sourceManifest": [{"type": "CURRENT_CONCEPT", "id": "concept-1"}],
        "sources": {"CURRENT_CONCEPT": {"conceptName": "자전거 운영 분석"}},
    })
    schema = MarketingStrategyResult.model_json_schema()
    assert value.projectId == 7
    assert "campaignRoadmap" in schema["properties"]
    assert "budgetGuidelines" in schema["properties"]
    assert "evidenceRefs" in schema["properties"]


def test_evidence_ref_canonicalizer_repairs_unique_type_and_deduplicates():
    assert service._canonicalize_evidence_refs(
        ["MARKET:invented", "MARKET:market-7", "CURRENT_CONCEPT:wrong"],
        ["CURRENT_CONCEPT:concept-1", "MARKET:market-7"],
    ) == ["MARKET:market-7", "CURRENT_CONCEPT:concept-1"]


def test_evidence_ref_canonicalizer_rejects_unknown_or_ambiguous_source():
    with pytest.raises(ProviderFailure) as failure:
        service._canonicalize_evidence_refs(
            ["UNKNOWN:any", "MARKET:invented"],
            ["CURRENT_CONCEPT:concept-1", "MARKET:market-1", "MARKET:market-2"],
        )
    assert failure.value.safe_diagnostics == {
        "allowedTypes": ["CURRENT_CONCEPT", "MARKET"],
        "invalidTypes": ["MARKET", "UNKNOWN"],
        "invalidRefCount": 2,
    }


def test_provider_schema_and_payload_use_closed_evidence_ref_vocabulary(monkeypatch):
    seen = {}

    async def provider(_system, user, **kwargs):
        seen["schema"] = kwargs["response_schema"]
        seen["payload"] = json.loads(user)
        return {
            "contract": "marketing-strategy-result-v1",
            "executiveSummary": "현재 사업안 기준 전략입니다.",
            "targetCustomers": ["운영 조직"],
            "positioning": "운영 효율을 돕는 분석 서비스",
            "coreMessages": ["운영 근거를 빠르게 확인"],
            "channelStrategies": [{"channel": "직접 제안", "objective": "상담",
                "audience": "운영 조직", "actions": ["제안서 전달"], "kpis": ["상담 건수"],
                "rationale": "조직 구매 과정에 맞춥니다."}],
            "contentPillars": ["운영 효율"],
            "campaignRoadmap": [{"phase": "검증", "objective": "상담",
                "actions": ["상담"], "kpis": ["상담 건수"]}],
            "budgetGuidelines": [], "risks": ["근거 확인 필요"],
            "evidenceRefs": ["CURRENT_CONCEPT:concept-1"],
        }

    monkeypatch.setattr(service, "execute_structured_prompt", provider)
    asyncio.run(execute_marketing_strategy({
        "contract": "marketing-strategy-input-v1", "projectId": 7,
        "sourceManifestHash": "sha256:" + "a" * 64,
        "sourceManifest": [{"type": "CURRENT_CONCEPT", "id": "concept-1"}],
        "sources": {"CURRENT_CONCEPT": {"name": "자전거 운영 분석"}},
    }))
    assert seen["schema"]["properties"]["evidenceRefs"]["items"]["enum"] == [
        "CURRENT_CONCEPT:concept-1",
    ]
    assert seen["payload"]["allowedEvidenceRefs"] == ["CURRENT_CONCEPT:concept-1"]
