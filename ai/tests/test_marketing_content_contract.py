import asyncio
from typing import Any, get_args, get_type_hints

import pytest
from pydantic import ValidationError

from app.tasks.marketing_content.models import (
    MarketingContentInput,
    MarketingContentResult,
    MarketingSourceSnapshot,
    lint_provider_schema,
)
from app.providers import ProviderFailure
from app.tasks.marketing_content import service


def request_input() -> dict:
    return {
        "source": {
            "contract": "marketing-source-snapshot-v1", "schemaVersion": "2.0",
            "snapshotId": "source-1", "hash": "sha256:" + "0" * 64,
            "createdAt": "2026-08-08T00:00:00Z", "projectId": 1, "selectionId": 2,
            "conceptId": "concept-1", "marketAnalysisSeedSnapshotId": "market-seed-1",
            "marketAnalysisSeedSnapshotHash": "sha256:" + "1" * 64,
            "conceptName": "Concept", "targetSegment": "Target", "problem": "Problem",
            "valueProposition": "Value", "positioning": "Position", "keyFeatures": ["Feature"],
            "pricing": "Fee", "channels": ["App"], "competitorDifferentiators": [],
            "targetRegion": "대한민국", "revenueModel": "구독", "price": "월 9,900원",
            "preMarketSomShare": {"targetSharePercent": 2.5, "horizonYears": 3},
            "preMarketSom": {"amount": 100000000, "currency": "KRW"},
            "legalStatus": "IMPLEMENTABLE_WITH_CONTROLS",
            "allowedClaims": [], "prohibitedClaims": [], "requiredDisclosures": [],
            "requiredControls": [], "communicationRequiredControls": [], "officialEvidenceReferences": [],
            "sourceSnapshotHash": "sha256:" + "0" * 64,
        },
        "request": {
            "contract": "marketing-content-request-v1", "marketingSourceSnapshotId": "source-1",
            "contentType": "BLOG_INTRO", "channel": "blog", "purpose": "launch",
            "tone": "clear", "length": "SHORT", "requiredPhrases": [],
            "excludedPhrases": [], "additionalInstruction": None,
        },
    }


def test_marketing_request_schema_is_closed_and_accepts_blog_intro() -> None:
    assert MarketingContentInput.model_validate(request_input()).request.contentType == "BLOG_INTRO"
    invalid = request_input()
    invalid["request"]["personaId"] = "forbidden"
    with pytest.raises(ValidationError):
        MarketingContentInput.model_validate(invalid)


def test_ai_result_schema_is_closed() -> None:
    valid = {
        "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
        "title": "Title", "body": "Body", "callToAction": None, "hashtags": [],
        "imageBrief": None,
        "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": []},
        "artifactRefs": [],
    }
    MarketingContentResult.model_validate(valid)
    with pytest.raises(ValidationError):
        MarketingContentResult.model_validate({**valid, "providerPayload": {}})


def test_provider_models_have_no_any_and_schema_is_strict_and_bounded() -> None:
    def contains_any(annotation: object) -> bool:
        return annotation is Any or any(contains_any(value) for value in get_args(annotation))

    for model in (MarketingSourceSnapshot, MarketingContentInput, MarketingContentResult):
        assert not any(contains_any(value) for value in get_type_hints(model, include_extras=True).values())
        assert lint_provider_schema(model.model_json_schema()) == []


def test_source_strings_and_arrays_are_bounded() -> None:
    invalid = request_input()
    invalid["source"]["conceptName"] = "x" * 501
    with pytest.raises(ValidationError):
        MarketingContentInput.model_validate(invalid)


def test_provider_result_with_prohibited_claim_is_blocked(monkeypatch) -> None:
    async def prohibited_result(*_args, **_kwargs):
        return {
            "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
            "title": "Title", "body": "전 지역 최저가 상품", "callToAction": None,
            "hashtags": [], "imageBrief": None,
            "legalReview": {"compliant": False, "warnings": [], "requiredDisclosuresApplied": []},
            "artifactRefs": [],
        }

    value = request_input()
    value["source"]["prohibitedClaims"] = ["전 지역 최저가"]
    monkeypatch.setattr(service, "execute_structured_prompt", prohibited_result)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_marketing_content(value))
    assert raised.value.reason == "SAFETY_POLICY_BLOCKED"
    assert raised.value.retryable is False
