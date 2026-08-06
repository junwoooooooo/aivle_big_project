import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.models.journey import IdeaInterpretationResult


def valid_result():
    return {
        "originalSourceSummary": "소상공인의 재고 낭비를 줄이는 서비스",
        "normalizedDescription": "소상공인 재고 예측 서비스",
        "facts": ["소상공인을 대상으로 한다"],
        "assumptions": ["모바일 사용을 선호할 수 있다"],
        "constraints": [],
        "openQuestions": ["초기 서비스 지역은 어디입니까?"],
        "readiness": "UNDER_SPECIFIED",
        "warnings": [],
        "evidenceNeeds": [],
        "originDraft": {
            "productServiceDescription": "재고 예측을 제공한다",
            "problem": ["재고 낭비"],
            "target": {"customerTypes": ["B2B"], "segment": "소상공인", "situation": None, "needs": []},
            "solution": ["수요 예측"],
            "coreValue": ["재고 낭비 감소"],
            "primaryCategory": "재고관리 SaaS",
            "targetRegion": None,
            "fixedValues": [{"field": "target", "value": "소상공인"}],
            "confirmedValues": {},
            "assumptions": ["모바일 사용 선호"],
            "pricingIntent": None,
            "revenueModelIntent": None,
            "salesChannelIntent": None,
            "knownUnitCost": None,
            "alternatives": [],
            "knownCompetitors": [],
            "differentiationIntent": None,
            "internalConstraints": [],
        },
        "fieldMetadata": [{
            "key": "targetRegion", "sourceType": "AI_PROPOSED", "requiredForStages": ["IDEA_ORIGIN"],
            "status": "MISSING", "locked": False, "fallbackPolicy": "BLOCK_STAGE",
        }],
        "clarificationQuestions": [{
            "targetField": "targetRegion", "requirement": "REQUIRED_FOR_IDEA_ORIGIN",
            "question": "초기 서비스 지역은 어디입니까?", "reason": "Idea Origin 확정에 필요합니다.",
        }],
    }


def test_idea_origin_contract_accepts_structured_draft():
    value = IdeaInterpretationResult.model_validate(valid_result())
    assert value.originDraft.targetRegion is None
    assert value.clarificationQuestions[0].targetField == "targetRegion"


def test_idea_origin_contract_rejects_extra_fields():
    payload = valid_result()
    payload["originDraft"]["unexpected"] = True
    with pytest.raises(ValidationError):
        IdeaInterpretationResult.model_validate(payload)


def test_idea_origin_contract_rejects_missing_required_field_question():
    payload = valid_result()
    payload["clarificationQuestions"] = []
    payload["openQuestions"] = []

    with pytest.raises(ValidationError) as failure:
        IdeaInterpretationResult.model_validate(payload)

    assert failure.value.errors()[0]["type"] == "idea_missing_clarification"
    assert "targetRegion" in failure.value.errors()[0]["ctx"]["fields"]


def test_documented_idea_response_fixture_matches_production_model():
    fixture_path = (
        Path(__file__).resolve().parents[2]
        / "docs/contracts/fixtures/internal-ai-v1/tasks/idea-interpretation.response.valid.json"
    )
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))

    IdeaInterpretationResult.model_validate(fixture["result"])
