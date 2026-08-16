import asyncio
import json
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
from app.tasks.marketing_content import marketing_image


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


def test_v26_exact_lineage_and_generation_context_are_validated_but_not_prompted(monkeypatch) -> None:
    value = request_input()
    value["source"].update({
        "schemaVersion": "2.1", "selectionRevision": 6, "bmPlanRevision": 4,
        "businessModel": {"key_activities": ["고객 지원"], "customer_relationship": "정기 안내"},
        "businessConstraints": {"budget_krw": 1000000, "months": 3, "team": 2},
    })
    value["generation"] = {
        "operation": "REGENERATE", "attempt": 1, "designVersion": "marketing-draft-v1",
    }
    captured = {}

    async def provider(_system, prompt, **_kwargs):
        captured.update(json.loads(prompt))
        return {
            "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
            "title": "검토할 초안", "body": "게시 전 확인이 필요한 본문", "callToAction": None,
            "hashtags": [], "imageBrief": "제품 중심 이미지",
            "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": []},
            "artifactRefs": [],
        }

    async def image(*_args):
        return "ai-artifacts/00000000-0000-4000-8000-000000000001.jpg"

    monkeypatch.setattr(service, "execute_structured_prompt", provider)
    monkeypatch.setattr(service, "generate_and_store_marketing_image", image)
    asyncio.run(service.execute_marketing_content(value))
    assert set(captured) == {"source", "request"}
    assert captured["source"]["selectionRevision"] == 6
    assert captured["source"]["businessModel"]["key_activities"] == ["고객 지원"]


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
    async def must_not_generate(*_args, **_kwargs):
        raise AssertionError("금지 표현 검증 전에 이미지를 생성하면 안 됩니다.")
    monkeypatch.setattr(service, "generate_and_store_marketing_image", must_not_generate)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_marketing_content(value))
    assert raised.value.reason == "SAFETY_POLICY_BLOCKED"
    assert raised.value.retryable is False


def test_missing_required_disclosure_is_blocked_before_image(monkeypatch) -> None:
    async def provider(*_args, **_kwargs):
        return {
            "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
            "title": "Title", "body": "Body", "callToAction": None, "hashtags": [],
            "imageBrief": "제품 이미지",
            "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": []},
            "artifactRefs": [],
        }

    async def must_not_generate(*_args, **_kwargs):
        raise AssertionError("필수 고지 검증 전에 이미지를 생성하면 안 됩니다.")

    value = request_input()
    value["source"]["requiredDisclosures"] = ["개인차가 있습니다"]
    monkeypatch.setattr(service, "execute_structured_prompt", provider)
    monkeypatch.setattr(service, "generate_and_store_marketing_image", must_not_generate)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_marketing_content(value))
    assert raised.value.reason == "SAFETY_POLICY_BLOCKED"


def test_no_reference_generation_returns_one_bounded_artifact(monkeypatch) -> None:
    async def provider(*_args, **_kwargs):
        return {
            "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
            "title": "Title", "body": "Body", "callToAction": None,
            "hashtags": [], "imageBrief": "제품 중심 스튜디오 컷",
            "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": []},
            "artifactRefs": [],
        }
    async def image(value, _result):
        assert value.request.referenceArtifactId is None
        return "ai-artifacts/00000000-0000-4000-8000-000000000001.jpg"
    monkeypatch.setattr(service, "execute_structured_prompt", provider)
    monkeypatch.setattr(service, "generate_and_store_marketing_image", image)
    result = asyncio.run(service.execute_marketing_content(request_input()))
    assert result["artifactRefs"] == ["ai-artifacts/00000000-0000-4000-8000-000000000001.jpg"]


def test_reference_artifact_is_forwarded_to_image_stage(monkeypatch) -> None:
    value = request_input()
    value["request"]["referenceArtifactId"] = "00000000-0000-4000-8000-000000000002"
    async def provider(*_args, **_kwargs):
        return {
            "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
            "title": "Title", "body": "Body", "callToAction": None,
            "hashtags": [], "imageBrief": "참고 제품을 유지한 키 비주얼",
            "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": []},
            "artifactRefs": [],
        }
    async def image(parsed, _result):
        assert parsed.request.referenceArtifactId == "00000000-0000-4000-8000-000000000002"
        return "ai-artifacts/00000000-0000-4000-8000-000000000003.jpg"
    monkeypatch.setattr(service, "execute_structured_prompt", provider)
    monkeypatch.setattr(service, "generate_and_store_marketing_image", image)
    result = asyncio.run(service.execute_marketing_content(value))
    assert len(result["artifactRefs"]) == 1


def test_provider_cannot_inject_artifact_refs(monkeypatch) -> None:
    async def provider(*_args, **_kwargs):
        return {
            "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
            "title": "Title", "body": "Body", "callToAction": None, "hashtags": [],
            "imageBrief": "제품 이미지",
            "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": []},
            "artifactRefs": ["ai-artifacts/00000000-0000-4000-8000-000000000099.jpg"],
        }

    async def image(*_args, **_kwargs):
        raise AssertionError("provider artifactRefs는 이미지 단계 전에 차단되어야 합니다.")

    monkeypatch.setattr(service, "execute_structured_prompt", provider)
    monkeypatch.setattr(service, "generate_and_store_marketing_image", image)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_marketing_content(request_input()))
    assert raised.value.reason == "AI_RESULT_INVALID"


def test_invalid_generated_artifact_is_mapped_safely(monkeypatch) -> None:
    async def provider(*_args, **_kwargs):
        return {
            "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
            "title": "Title", "body": "Body", "callToAction": None,
            "hashtags": [], "imageBrief": "제품 이미지",
            "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": []},
            "artifactRefs": [],
        }

    async def invalid_image(*_args, **_kwargs):
        return "ai-artifacts/not-a-generated-jpeg.png"

    monkeypatch.setattr(service, "execute_structured_prompt", provider)
    monkeypatch.setattr(service, "generate_and_store_marketing_image", invalid_image)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_marketing_content(request_input()))
    assert raised.value.reason == "AI_RESULT_INVALID"
    assert raised.value.retryable is False


def test_aidev_commercial_image_prompt_constraints_are_preserved() -> None:
    value = MarketingContentInput.model_validate(request_input())
    result = MarketingContentResult.model_validate({
        "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
        "title": "Title", "body": "Body", "callToAction": None, "hashtags": [],
        "imageBrief": "제품 중심 스튜디오 컷",
        "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": []},
        "artifactRefs": [],
    })
    prompt = marketing_image._image_prompt(value, result)
    for required in (
        "professional retail or brand advertisement", "clear hero subject", "realistic materials",
        "controlled studio lighting", "intentional depth", "generous negative space", "any words",
        "letters", "numbers", "logos", "watermarks", "UI labels", "buttons", "badges", "claims",
        "legal text", "Do not imply any prohibited claim", "recognizable shape", "color", "proportions",
        "packaging", "material details", "generic clip-art", "collage layouts", "excessive props",
        "distorted packaging", "dominant text areas",
    ):
        assert required in prompt


def test_image_pipeline_selects_generate_without_reference_and_edit_with_reference(monkeypatch) -> None:
    calls = []

    async def fake_thread(function, prompt, size, reference):
        calls.append((function, prompt, size, reference))
        return b"jpeg"

    async def fake_download(project_id, artifact_id):
        return b"reference", "image/jpeg"

    async def fake_upload(content):
        assert content == b"jpeg"
        return "ai-artifacts/00000000-0000-4000-8000-000000000004.jpg"

    monkeypatch.setattr(marketing_image.asyncio, "to_thread", fake_thread)
    monkeypatch.setattr(marketing_image, "_download_reference", fake_download)
    monkeypatch.setattr(marketing_image, "_upload_image", fake_upload)
    result = MarketingContentResult.model_validate({
        "contract": "marketing-content-result-v1", "contentType": "BLOG_INTRO",
        "title": "Title", "body": "Body", "callToAction": None, "hashtags": [], "imageBrief": None,
        "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": []},
        "artifactRefs": [],
    })
    without_reference = MarketingContentInput.model_validate(request_input())
    with_reference_json = request_input()
    with_reference_json["request"]["referenceArtifactId"] = "00000000-0000-4000-8000-000000000002"
    with_reference = MarketingContentInput.model_validate(with_reference_json)

    asyncio.run(marketing_image.generate_and_store_marketing_image(without_reference, result))
    asyncio.run(marketing_image.generate_and_store_marketing_image(with_reference, result))

    assert calls[0][3] is None
    assert calls[1][3] == (b"reference", "image/jpeg")


def test_generated_image_must_be_a_bounded_jpeg() -> None:
    assert marketing_image._validate_generated_jpeg(b"\xff\xd8\xffpayload") == b"\xff\xd8\xffpayload"
    with pytest.raises(ProviderFailure) as raised:
        marketing_image._validate_generated_jpeg(b"not-jpeg")
    assert raised.value.reason == "AI_RESULT_INVALID"
