import copy
import asyncio
import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.models.executions import (
    IdeaConversationTurnInputV1,
    InternalExecutionRequestV1,
    InternalExecutionSuccessResponseV1,
)
from main import app
from app.models.journey import OpportunityBriefDraftResult
from app.models.idea_conversation_provider import ProviderOpportunityBriefDraftResult
from app.services import journey_provider


TOKEN = "conversation-contract-token"
FIXTURES = Path(__file__).resolve().parents[2] / "contracts" / "internal-ai"
client = TestClient(app)


def fixture(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def headers() -> dict[str, str]:
    return {
        "Authorization": f"Bearer {TOKEN}",
        "X-Correlation-Id": "correlation-conversation-1",
    }


def provider_result(domain_result: dict) -> dict:
    value = copy.deepcopy(domain_result)
    for collection in ("extractedFields", "fieldSuggestions"):
        for field in value[collection]:
            domain_value = field.pop("valueJson")
            field["valueKind"] = "TEXT_LIST" if isinstance(domain_value, list) else "TEXT"
            field["textValue"] = domain_value if isinstance(domain_value, str) else None
            field["listValue"] = domain_value if isinstance(domain_value, list) else []
    return value


def test_shared_request_fixture_passes_strict_pydantic_models():
    body = InternalExecutionRequestV1.model_validate(
        fixture("idea-conversation-turn-v1.request.json")
    )
    task_input = IdeaConversationTurnInputV1.model_validate(body.input)

    assert body.taskType == "IDEA_CONVERSATION_TURN"
    assert task_input.currentBrief is None
    assert task_input.attachments == []
    assert task_input.messages[-1].messageId == task_input.sourceMessageId


def test_shared_response_fixture_passes_strict_response_model():
    response = InternalExecutionSuccessResponseV1.model_validate(
        fixture("idea-conversation-turn-v1.response.json")
    )
    assert response.taskType == "IDEA_CONVERSATION_TURN"
    assert response.result["readiness"] == "NEEDS_INPUT"
    OpportunityBriefDraftResult.model_validate(response.result)


def test_canonical_task_uses_conversation_prompt_and_strict_result(monkeypatch):
    expected_result = fixture("idea-conversation-turn-v1.response.json")["result"]
    provider_value = provider_result(expected_result)
    captured = {}

    async def structured_prompt(system: str, user: str, **kwargs):
        captured["system"] = system
        captured["user"] = user
        captured["schema"] = kwargs["response_schema"]
        captured["schema_name"] = kwargs["schema_name"]
        return provider_value

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", structured_prompt)
    result = asyncio.run(journey_provider.execute_journey_task(
        "IDEA_CONVERSATION_TURN",
        json.dumps(fixture("idea-conversation-turn-v1.request.json")["input"], ensure_ascii=False),
    ))

    assert "Opportunity Brief" in captured["system"]
    assert '"conversationId": 301' in captured["user"]
    assert captured["schema"] == ProviderOpportunityBriefDraftResult.model_json_schema()
    assert captured["schema_name"] == "opportunity_brief_draft_v1"
    assert result["readiness"] == "NEEDS_INPUT"


def test_endpoint_accepts_fixture_and_dispatches_canonical_task(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    expected_result = fixture("idea-conversation-turn-v1.response.json")["result"]
    captured = {}

    async def provider(task_type: str, text: str, on_schema_repair=None):
        captured["taskType"] = task_type
        captured["input"] = json.loads(text)
        captured["repairCallback"] = on_schema_repair
        return expected_result

    monkeypatch.setattr("app.api.executions.execute_journey_task", provider)
    response = client.post(
        "/internal/v1/ai/executions",
        json=fixture("idea-conversation-turn-v1.request.json"),
        headers=headers(),
    )

    assert response.status_code == 200
    assert captured["taskType"] == "IDEA_CONVERSATION_TURN"
    assert captured["input"]["sourceMessageId"] == 403
    assert callable(captured["repairCallback"])
    assert response.json()["result"] == expected_result


def test_endpoint_records_one_safe_repair_warning(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    expected_result = fixture("idea-conversation-turn-v1.response.json")["result"]

    async def provider(task_type: str, text: str, on_schema_repair=None):
        on_schema_repair(5)
        return expected_result

    monkeypatch.setattr("app.api.executions.execute_journey_task", provider)
    response = client.post(
        "/internal/v1/ai/executions",
        json=fixture("idea-conversation-turn-v1.request.json"),
        headers=headers(),
    )

    assert response.status_code == 200
    assert response.json()["warnings"] == [{
        "code": "RESULT_SCHEMA_REPAIRED",
        "attemptPhase": "REPAIR",
        "issueCount": 5,
    }]


def test_conversation_input_rejects_missing_required_field_with_safe_path(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    body = fixture("idea-conversation-turn-v1.request.json")
    del body["input"]["currentBrief"]
    body["canonicalInputHash"] = canonical_hash(body)

    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())

    assert response.status_code == 400
    error = response.json()["error"]
    assert error["retryable"] is False
    assert error["details"][0]["reason"] == "FIELD_CONSTRAINT_VIOLATION"
    assert error["details"][0]["fields"][0] == {
        "path": "input.currentBrief", "expectedType": "required", "category": "missing"
    }
    assert "서울의 재활용" not in response.text


def test_conversation_input_rejects_extra_field(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    body = fixture("idea-conversation-turn-v1.request.json")
    body["input"]["unexpected"] = True
    body["canonicalInputHash"] = canonical_hash(body)

    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())

    assert response.status_code == 400
    field = response.json()["error"]["details"][0]["fields"][0]
    assert field["path"] == "input.unexpected"
    assert field["category"] == "extra_forbidden"


def test_unknown_task_type_is_rejected_without_dispatch(monkeypatch):
    monkeypatch.setenv("AI_INTERNAL_SERVICE_TOKEN", TOKEN)
    body = fixture("idea-conversation-turn-v1.request.json")
    body["taskType"] = "IDEA_CONVERSATION_UNKNOWN"
    body["canonicalInputHash"] = canonical_hash(body)

    response = client.post("/internal/v1/ai/executions", json=body, headers=headers())

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "UNSUPPORTED_TASK_TYPE"


def test_conversation_model_rejects_assistant_without_envelope():
    task_input = copy.deepcopy(fixture("idea-conversation-turn-v1.request.json")["input"])
    task_input["messages"].append({
        "messageId": 402, "sequence": 2, "role": "ASSISTANT", "messageType": "QUESTION_SET",
        "content": "질문", "envelope": None,
    })
    with pytest.raises(ValidationError):
        IdeaConversationTurnInputV1.model_validate(task_input)


def canonical_hash(body: dict) -> str:
    import hashlib
    import unicodedata

    canonical = json.dumps(
        {key: body[key] for key in (
            "contractVersion", "taskType", "taskSchemaVersion", "locale", "input"
        )},
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return "sha256:" + hashlib.sha256(
        unicodedata.normalize("NFC", canonical).encode("utf-8")
    ).hexdigest()
