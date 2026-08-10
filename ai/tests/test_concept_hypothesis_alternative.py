import asyncio

import pytest

from app.providers import ProviderFailure
from app.tasks.concept_hypothesis_alternative import service
from concept_candidate_v2_fixture import valid_candidate


def test_rejected_hypothesis_returns_versioned_alternative(monkeypatch):
    async def provider(*_, **__):
        return {"hypothesisType": "REVENUE_MODEL", "proposedValue": "거래당 수수료",
            "source": "AI_HYPOTHESIS", "decisionStatus": "ALTERNATIVE_PROPOSED", "proposalVersion": 2}
    monkeypatch.setattr(service, "execute_structured_prompt", provider)

    result = asyncio.run(service.execute_concept_hypothesis_alternative({
        "hypothesisType": "REVENUE_MODEL", "rejectedValue": "월 구독", "proposalVersion": 2,
        "candidate": valid_candidate(),
    }))

    assert result["decisionStatus"] == "ALTERNATIVE_PROPOSED"
    assert result["proposalVersion"] == 2


def test_mismatched_alternative_version_is_rejected(monkeypatch):
    async def provider(*_, **__):
        return {"hypothesisType": "REVENUE_MODEL", "proposedValue": "거래당 수수료",
            "source": "AI_HYPOTHESIS", "decisionStatus": "ALTERNATIVE_PROPOSED", "proposalVersion": 3}
    monkeypatch.setattr(service, "execute_structured_prompt", provider)

    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_concept_hypothesis_alternative({
            "hypothesisType": "REVENUE_MODEL", "rejectedValue": "월 구독", "proposalVersion": 2,
            "candidate": valid_candidate(),
        }))
    assert raised.value.reason == "AI_RESULT_INVALID"
