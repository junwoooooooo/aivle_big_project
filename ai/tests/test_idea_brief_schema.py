import asyncio
from typing import Any, get_args, get_origin, get_type_hints

import pytest

from app.providers import ProviderFailure
from app.tasks.idea_brief import service
from app.tasks.idea_brief.mapper import to_domain
from app.tasks.idea_brief.models import IdeaBriefProviderResult


FIELD_METADATA = [
    {"fieldKey": "ideaOverview", "requiredForConcept": True, "regulatorySensitive": False},
    {"fieldKey": "problem", "requiredForConcept": True, "regulatorySensitive": False},
    {"fieldKey": "targetUsers", "requiredForConcept": True, "regulatorySensitive": False},
    {"fieldKey": "targetRegion", "requiredForConcept": False, "regulatorySensitive": False},
]


def provider_result(decision="ALLOW", questions=None, contradictions=None):
    return {
        "safetyReview": {
            "decision": decision,
            "categories": [] if decision == "ALLOW" else ["CLEAR_EXPLOITATION"],
            "restrictions": [],
            "userFacingReason": "안전 확인 결과입니다.",
        },
        "interpretation": {
            "interpretedProblem": "지역 음식물 폐기 문제",
            "interpretedTargetUsers": "지역 식당",
            "usageContext": "영업 종료 후",
            "industryCategory": "폐기물 관리",
            "researchScope": "수거 및 감축 서비스",
            "conciseIdeaDefinition": "식당의 음식물 폐기를 줄이는 서비스",
            "targetRegionInterpretation": "",
            "relevantKnownCompetitorContext": "",
        },
        "commitmentCandidates": [],
        "clarificationQuestions": questions or [],
        "contradictions": contradictions or [],
        "readiness": {"status": "READY_FOR_REVIEW", "score": 90, "missingFieldKeys": []},
        "userFacingSummary": "입력하신 아이디어를 이렇게 이해했습니다.",
    }


def task_input(mode="INITIAL"):
    return {
        "mode": mode,
        "ideaOverview": "식당 음식물 폐기를 줄이는 서비스",
        "fields": [
            {"fieldKey": "ideaOverview", "value": "식당 음식물 폐기를 줄이는 서비스", "decisionState": "LOCKED"},
            {"fieldKey": "problem", "value": "음식물 폐기", "decisionState": "LOCKED"},
            {"fieldKey": "targetUsers", "value": "지역 식당", "decisionState": "LOCKED"},
        ],
        "attachmentFileIds": [],
        "attachmentDocuments": [],
        "fieldMetadata": FIELD_METADATA,
    }


def test_provider_schema_is_closed_and_fully_typed():
    schema = IdeaBriefProviderResult.model_json_schema()
    _assert_closed_schema(schema, schema)
    for model in _model_types(IdeaBriefProviderResult):
        assert Any not in get_type_hints(model).values()


def test_provider_to_domain_preserves_safety_and_interpretation():
    provider = IdeaBriefProviderResult.model_validate(provider_result())
    result = to_domain(provider).model_dump(mode="json")
    assert result["safetyReview"]["decision"] == "ALLOW"
    assert result["interpretation"]["interpretedTargetUsers"] == "지역 식당"
    assert "fields" not in result


def test_explicit_user_text_commitment_is_reviewable_not_locked(monkeypatch):
    candidate = {
        "fieldKey": "price", "value": "월 9,900원", "evidenceQuote": "월 9,900원 구독",
        "source": "AI_DERIVED", "origin": "USER_TEXT", "authority": "REVIEWABLE",
    }

    async def prompt(_system, _user, **_kwargs):
        result = provider_result()
        result["commitmentCandidates"] = [candidate]
        return result

    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    result = asyncio.run(service.execute_idea_brief_derivation(task_input()))
    assert result["commitmentCandidates"] == [candidate]
    assert result["commitmentCandidates"][0]["authority"] == "REVIEWABLE"


def test_locked_form_value_suppresses_conflicting_extraction(monkeypatch):
    async def prompt(_system, _user, **_kwargs):
        result = provider_result()
        result["commitmentCandidates"] = [{
            "fieldKey": "price", "value": "월 9,900원", "evidenceQuote": "월 9,900원",
            "source": "AI_DERIVED", "origin": "USER_TEXT", "authority": "REVIEWABLE",
        }]
        return result

    value = task_input()
    value["fields"].append({"fieldKey": "price", "value": "월 12,000원", "decisionState": "LOCKED"})
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    result = asyncio.run(service.execute_idea_brief_derivation(value))
    assert result["commitmentCandidates"] == []


def test_optional_legal_details_are_not_follow_up_targets():
    schema = IdeaBriefProviderResult.model_json_schema()
    serialized = str(schema)
    assert "payment" not in serialized
    assert "personalData" not in serialized
    assert "requiredPartners" not in serialized
    assert "targetRegion" not in serialized.split("ClarificationQuestion", 1)[-1].split("Contradiction", 1)[0]


def test_blocked_safety_result_removes_questions(monkeypatch):
    async def prompt(_system, _user, **_kwargs):
        return provider_result("BLOCK_OR_REFRAME", questions=[{
            "targetFieldKey": "problem", "prompt": "문제를 알려주세요.",
            "type": "FREE_TEXT", "options": [], "allowUndecided": False,
        }])

    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    result = asyncio.run(service.execute_idea_brief_derivation(task_input()))
    assert result["safetyReview"]["decision"] == "BLOCK_OR_REFRAME"
    assert result["questions"] == []


def test_complete_minimal_seed_drops_unnecessary_questions(monkeypatch):
    async def prompt(_system, _user, **_kwargs):
        return provider_result(questions=[{
            "targetFieldKey": "targetUsers", "prompt": "사용자를 다시 알려주세요.",
            "type": "FREE_TEXT", "options": [], "allowUndecided": False,
        }])

    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    result = asyncio.run(service.execute_idea_brief_derivation(task_input()))
    assert result["questions"] == []
    assert result["readiness"]["status"] == "READY_FOR_REVIEW"


def test_final_synthesis_rejects_new_questions(monkeypatch):
    async def prompt(_system, _user, **_kwargs):
        return provider_result(questions=[{
            "targetFieldKey": "problem", "prompt": "문제를 다시 알려주세요.",
            "type": "FREE_TEXT", "options": [], "allowUndecided": False,
        }])

    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_idea_brief_derivation(task_input("FINAL_SYNTHESIS")))
    assert raised.value.reason == "FINAL_SYNTHESIS_QUESTIONS_FORBIDDEN"


def _assert_closed_schema(node: dict, root: dict) -> None:
    if "$ref" in node:
        target = root
        for part in node["$ref"].removeprefix("#/").split("/"):
            target = target[part]
        _assert_closed_schema(target, root)
        return
    if node.get("type") == "object":
        assert node.get("additionalProperties") is False
        assert node.get("properties")
        for value in node["properties"].values():
            _assert_closed_schema(value, root)
    if node.get("type") == "array":
        assert node.get("items")
        _assert_closed_schema(node["items"], root)
    for choice in node.get("anyOf", []) + node.get("oneOf", []):
        _assert_closed_schema(choice, root)


def _model_types(root):
    found = {root}
    pending = [root]
    while pending:
        model = pending.pop()
        for hint in get_type_hints(model).values():
            for value in get_args(hint) or (hint,):
                origin = get_origin(value)
                candidate = get_args(value)[0] if origin is list else value
                if isinstance(candidate, type) and hasattr(candidate, "model_fields") and candidate not in found:
                    found.add(candidate)
                    pending.append(candidate)
    return found
