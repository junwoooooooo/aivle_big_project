import pytest
from pydantic import ValidationError

from app.tasks.marketing_content.models import MarketingContentInput, MarketingContentResult


def request_input() -> dict:
    return {
        "source": {
            "conceptName": "Concept", "targetSegment": "Target", "problem": "Problem",
            "valueProposition": "Value", "positioning": "Position", "keyFeatures": ["Feature"],
            "pricing": "Fee", "channels": ["App"], "competitorDifferentiators": [],
            "allowedClaims": [], "prohibitedClaims": [], "requiredDisclosures": [],
            "sourceSnapshotHash": "sha256:" + "0" * 64,
        },
        "request": {
            "contract": "marketing-content-request-v1", "planningSnapshotId": "plan-1",
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
