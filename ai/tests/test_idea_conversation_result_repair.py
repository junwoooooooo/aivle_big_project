import asyncio
import copy
import json
import logging

import pytest
from pydantic import ValidationError

from app.models.idea_conversation_provider import (
    ProviderOpportunityBriefDraftResult,
    lint_openai_strict_schema,
    provider_result_to_domain,
)
from app.models.journey import OpportunityBriefDraftResult
from app.services import journey_provider


def valid_result():
    return {
        "extractedFields": [],
        "fieldSuggestions": [{
            "fieldKey": "problem",
            "valueKind": "TEXT",
            "textValue": "반복되는 음식물 폐기 문제",
            "listValue": [],
            "decisionStatus": "OPEN",
            "sourceType": "AI_PROPOSED",
            "confidence": 0.75,
        }],
        "assumptions": [],
        "openFields": ["targetCustomer", "targetRegion"],
        "contradictions": [],
        "clarificationQuestions": [
            {"id": "q1", "fieldKey": "targetCustomer", "prompt": "주요 사용자는 누구인가요?",
             "type": "FREE_TEXT", "options": [], "allowUndecided": True},
            {"id": "q2", "fieldKey": "targetRegion", "prompt": "대상 지역은 어디인가요?",
             "type": "SINGLE_SELECT", "options": ["서울", "부산"], "allowUndecided": True},
        ],
        "readiness": "NEEDS_INPUT",
        "userFacingSummary": "두 가지 정보가 더 필요합니다.",
    }


@pytest.mark.parametrize("mutate", [
    lambda value: value["fieldSuggestions"][0].update(decisionStatus="선호"),
    lambda value: value["fieldSuggestions"][0].update(confidence="0.75"),
    lambda value: value["clarificationQuestions"][0].update(id=1),
    lambda value: value["clarificationQuestions"][0].update(type="TEXT"),
    lambda value: value["clarificationQuestions"][0].update(options=None),
    lambda value: value.update(unexpected=True),
])
def test_result_model_rejects_noncanonical_provider_types(mutate):
    candidate = valid_result()
    mutate(candidate)

    with pytest.raises(ValidationError):
        ProviderOpportunityBriefDraftResult.model_validate(candidate)


def test_provider_schema_is_openai_typed_and_excludes_domain_any_value():
    schema = ProviderOpportunityBriefDraftResult.model_json_schema()
    serialized = json.dumps(schema)

    assert lint_openai_strict_schema(schema) == []
    assert "valueJson" not in serialized
    assert '"valueKind"' in serialized
    assert "{}" not in serialized


def test_schema_lint_detects_original_untyped_value_json_contract():
    issues = lint_openai_strict_schema(OpportunityBriefDraftResult.model_json_schema())

    assert "schema.$defs.OpportunityBriefFieldProposal.properties.valueJson:untyped" in issues


@pytest.mark.parametrize(("kind", "text_value", "list_value", "source_type", "confidence", "expected"), [
    ("TEXT", "문제", [], "AI_PROPOSED", 0.8, "문제"),
    ("TEXT_LIST", None, ["조건 A", "조건 B"], "SOURCE_EXTRACTED", 0.7, ["조건 A", "조건 B"]),
    ("MISSING", None, [], "MISSING", None, None),
])
def test_provider_value_kind_maps_deterministically_to_domain(
        kind, text_value, list_value, source_type, confidence, expected):
    candidate = valid_result()
    field = candidate["fieldSuggestions"][0]
    field.update(valueKind=kind, textValue=text_value, listValue=list_value,
                 sourceType=source_type, confidence=confidence)

    domain = provider_result_to_domain(candidate)

    assert domain.fieldSuggestions[0].valueJson == expected
    OpportunityBriefDraftResult.model_validate(domain.model_dump(mode="json"))


def test_initial_invalid_result_is_repaired_exactly_once(monkeypatch, caplog):
    invalid = valid_result()
    invalid["fieldSuggestions"][0]["decisionStatus"] = "선호"
    invalid["fieldSuggestions"][0]["confidence"] = "0.75"
    invalid["clarificationQuestions"][0]["id"] = 1
    invalid["clarificationQuestions"][0]["type"] = "TEXT"
    invalid["clarificationQuestions"][0]["options"] = None
    responses = [invalid, valid_result()]
    calls = []
    repair_counts = []

    async def provider(system, user, **kwargs):
        calls.append({"system": system, "user": user, **kwargs})
        return responses.pop(0)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", provider)
    caplog.set_level(logging.WARNING)
    result = asyncio.run(journey_provider.execute_journey_task(
        "IDEA_CONVERSATION_TURN",
        json.dumps({"conversationContract": "opportunity-brief-v1", "messages": []}),
        on_schema_repair=repair_counts.append,
    ))

    assert result["fieldSuggestions"][0]["decisionStatus"] == "OPEN"
    assert len(calls) == 2
    assert calls[0]["schema_name"] == "opportunity_brief_draft_v1"
    assert calls[1]["schema_name"] == "opportunity_brief_draft_repair_v1"
    assert calls[0]["response_schema"] == ProviderOpportunityBriefDraftResult.model_json_schema()
    assert calls[1]["response_schema"] == ProviderOpportunityBriefDraftResult.model_json_schema()
    assert repair_counts == [5]
    assert "선호" not in caplog.text
    assert "0.75" not in caplog.text


def test_invalid_repair_fails_permanently_without_third_call(monkeypatch):
    invalid = valid_result()
    invalid["clarificationQuestions"][0]["options"] = None
    calls = 0

    async def provider(system, user, **kwargs):
        nonlocal calls
        calls += 1
        return copy.deepcopy(invalid)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", provider)
    with pytest.raises(journey_provider.ProviderFailure) as failure:
        asyncio.run(journey_provider.execute_journey_task(
            "IDEA_CONVERSATION_TURN",
            json.dumps({"conversationContract": "opportunity-brief-v1", "messages": []}),
        ))

    assert calls == 2
    assert failure.value.code == "RESULT_SCHEMA_INVALID"
    assert failure.value.retryable is False


def test_provider_request_uses_pydantic_json_schema(monkeypatch):
    captured = {}
    result = valid_result()

    class Response:
        status_code = 200
        content = b"{}"

        def json(self):
            return {"choices": [{"message": {"content": json.dumps(result)}}]}

    class Client:
        def __init__(self, timeout):
            captured["timeout"] = timeout

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def post(self, url, headers, json):
            captured["body"] = json
            return Response()

    monkeypatch.setenv("AI_PROVIDER", "openai")
    monkeypatch.setenv("AI_API_KEY", "not-logged-test-key")
    monkeypatch.setenv("AI_MODEL", "test-model")
    monkeypatch.setattr(journey_provider.httpx, "AsyncClient", Client)
    schema = ProviderOpportunityBriefDraftResult.model_json_schema()

    actual = asyncio.run(journey_provider.execute_structured_prompt(
        "system", "user", response_schema=schema,
        schema_name="opportunity_brief_draft_v1",
    ))

    assert actual == result
    response_format = captured["body"]["response_format"]
    assert response_format["type"] == "json_schema"
    assert response_format["json_schema"] == {
        "name": "opportunity_brief_draft_v1", "strict": True, "schema": schema,
    }


def test_openai_response_format_400_maps_safely(monkeypatch, caplog):
    class Response:
        status_code = 400
        content = b"sensitive-provider-body-marker"

        def json(self):
            return {"error": {
                "type": "invalid_request_error",
                "param": "response_format",
                "message": "sensitive-provider-body-marker",
            }}

    class Client:
        def __init__(self, timeout):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def post(self, url, headers, json):
            return Response()

    monkeypatch.setenv("AI_PROVIDER", "openai")
    monkeypatch.setenv("AI_API_KEY", "sensitive-api-key-marker")
    monkeypatch.setenv("AI_MODEL", "gpt-4o-mini")
    monkeypatch.setattr(journey_provider.httpx, "AsyncClient", Client)
    caplog.set_level(logging.WARNING)

    with pytest.raises(journey_provider.ProviderFailure) as failure:
        asyncio.run(journey_provider.execute_structured_prompt(
            "sensitive-prompt-marker", "sensitive-user-marker",
            response_schema=ProviderOpportunityBriefDraftResult.model_json_schema(),
            schema_name="opportunity_brief_draft_v1",
            task_type="IDEA_CONVERSATION_TURN",
        ))

    assert failure.value.code == "RESULT_SCHEMA_INVALID"
    assert failure.value.reason == "PROVIDER_RESPONSE_SCHEMA_REJECTED"
    assert failure.value.status_code == 502
    assert failure.value.retryable is False
    assert failure.value.upstream_status == 400
    assert "sensitive-provider-body-marker" not in caplog.text
    assert "sensitive-api-key-marker" not in caplog.text
    assert "sensitive-prompt-marker" not in caplog.text
    assert "sensitive-user-marker" not in caplog.text


def test_single_fenced_json_object_has_bounded_parse_policy():
    assert journey_provider._extract_json(
        "```json\n" + json.dumps(valid_result()) + "\n```"
    ) == valid_result()
