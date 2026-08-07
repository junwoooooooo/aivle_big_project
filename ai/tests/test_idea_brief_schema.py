import asyncio
from typing import Any, get_args, get_origin, get_type_hints
import pytest

from app.tasks.idea_brief.mapper import to_domain
from app.tasks.idea_brief.models import IdeaBriefProviderResult
from app.tasks.idea_brief import service
from app.providers import ProviderFailure


FIELD_METADATA = [
    {"fieldKey": "physicalActivity", "requiredForConcept": True, "regulatorySensitive": True},
    {"fieldKey": "personalData", "requiredForConcept": True, "regulatorySensitive": True},
    {"fieldKey": "problem", "requiredForConcept": True, "regulatorySensitive": False},
    {"fieldKey": "assumptions", "requiredForConcept": False, "regulatorySensitive": False},
]


def test_provider_schema_is_closed_and_fully_typed():
    schema = IdeaBriefProviderResult.model_json_schema()
    _assert_closed_schema(schema, schema)
    for model in _model_types(IdeaBriefProviderResult):
        assert Any not in get_type_hints(model).values()


def test_provider_to_domain_mapping_is_deterministic():
    provider = IdeaBriefProviderResult.model_validate({
        "extractedFields": [{"fieldKey": "problem", "value": "폐기", "decisionState": "OPEN", "sourceReference": "overview"}],
        "fieldSuggestions": [{"fieldKey": "targetCustomers", "value": "식당", "decisionState": "PREFERRED", "rationale": "입력 기반"}],
        "clarificationQuestions": [],
        "contradictions": [],
        "readiness": {"status": "READY_FOR_REVIEW", "score": 90, "missingFieldKeys": []},
        "userFacingSummary": "검토할 수 있습니다.",
    })
    first = to_domain(provider).model_dump(mode="json")
    second = to_domain(provider).model_dump(mode="json")
    assert first == second
    assert [field["provenance"] for field in first["fields"]] == ["SOURCE_EXTRACTED", "AI_PROPOSED"]


def test_final_synthesis_uses_strict_mode_prompt_and_rejects_questions(monkeypatch):
    captured = {}
    async def prompt(system, _user, **kwargs):
        captured["system"] = system
        captured.update(kwargs)
        return {
            "extractedFields": [], "fieldSuggestions": [],
            "clarificationQuestions": [{"targetFieldKey": "problem", "prompt": "more?",
                "type": "FREE_TEXT", "options": [], "allowUndecided": True}],
            "contradictions": [],
            "readiness": {"status": "NEEDS_INPUT", "score": 20, "missingFieldKeys": ["problem"]},
            "userFacingSummary": "추가 확인이 필요합니다.",
        }
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    with pytest.raises(ProviderFailure) as raised:
        asyncio.run(service.execute_idea_brief_derivation({
            "mode": "FINAL_SYNTHESIS", "overview": "idea", "fields": [], "attachmentFileIds": [],
            "fieldMetadata": FIELD_METADATA,
        }))
    assert raised.value.reason == "FINAL_SYNTHESIS_QUESTIONS_FORBIDDEN"
    assert "더 이상 새로운 질문을 생성하지 말고" in captured["system"]
    assert captured["response_schema"]["additionalProperties"] is False


def test_clarification_keeps_required_questions_before_optional_questions(monkeypatch):
    async def prompt(_system, _user, **_kwargs):
        return {
            "extractedFields": [], "fieldSuggestions": [],
            "clarificationQuestions": [
                {"targetFieldKey": "assumptions", "prompt": "optional", "type": "FREE_TEXT",
                    "options": [], "allowUndecided": True},
                {"targetFieldKey": "problem", "prompt": "problem", "type": "FREE_TEXT",
                    "options": [], "allowUndecided": True},
                {"targetFieldKey": "physicalActivity", "prompt": "activity", "type": "FREE_TEXT",
                    "options": [], "allowUndecided": True},
            ],
            "contradictions": [],
            "readiness": {"status": "NEEDS_INPUT", "score": 30,
                "missingFieldKeys": ["problem", "physicalActivity"]},
            "userFacingSummary": "필수 정보가 필요합니다.",
        }
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)

    result = asyncio.run(service.execute_idea_brief_derivation({
        "mode": "CLARIFICATION", "overview": "idea", "fields": [], "attachmentFileIds": [],
        "fieldMetadata": FIELD_METADATA,
    }))

    assert [value["targetFieldKey"] for value in result["questions"]] == ["physicalActivity", "problem"]


def test_final_synthesis_allows_ai_proposal_and_keeps_unresolved_required_missing(monkeypatch):
    async def prompt(_system, _user, **_kwargs):
        return {
            "extractedFields": [],
            "fieldSuggestions": [{"fieldKey": "physicalActivity", "value": "오프라인 활동 없음",
                "decisionState": "PREFERRED", "rationale": "현재 설명에서 합리적으로 추론"}],
            "clarificationQuestions": [], "contradictions": [],
            "readiness": {"status": "NEEDS_INPUT", "score": 70,
                "missingFieldKeys": ["personalData"]},
            "userFacingSummary": "개인정보 항목은 사용자 확인이 필요합니다.",
        }
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)

    result = asyncio.run(service.execute_idea_brief_derivation({
        "mode": "FINAL_SYNTHESIS", "overview": "idea",
        "fields": [{"fieldKey": "problem", "value": "문제", "decisionState": "PREFERRED"}],
        "attachmentFileIds": [],
        "fieldMetadata": FIELD_METADATA,
    }))

    assert result["questions"] == []
    assert result["fields"][0]["provenance"] == "AI_PROPOSED"
    assert result["readiness"]["missingFieldKeys"] == ["personalData"]


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
