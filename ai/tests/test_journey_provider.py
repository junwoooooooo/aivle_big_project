import asyncio
import json
import logging

import pytest

from app.services import journey_provider


def concept_input(desired_count=3):
    required = [
        {"structureKey": "problem", "sourceValue": ["waste"]},
        {
            "structureKey": "target",
            "sourceValue": {"customerTypes": ["shopper"]},
        },
        {"structureKey": "coreValue", "sourceValue": ["saving"]},
        {"structureKey": "fixedValues", "sourceValue": []},
    ]
    return {
        "desiredCount": desired_count,
        "round": 0,
        "ideaOrigin": {},
        "lockedValues": {},
        "requiredOriginTrace": required,
        "legalGuardrail": {},
        "negativeConstraints": [],
        "acceptedConcepts": [],
    }


def valid_concept(index, required):
    target = {"customerTypes": ["shopper"], "segment": f"segment-{index}"}
    values = {
        "problem": [f"solution-{index}"],
        "target": target,
        "coreValue": [f"value-{index}"],
        "fixedValues": [],
    }
    return {
        "conceptName": f"Concept {index}",
        "targetSegment": target,
        "positioning": f"Positioning {index}",
        "featureSet": ["feature"],
        "pricing": {"model": "fixed"},
        "revenueModel": {"type": "subscription"},
        "channels": ["direct"],
        "operatingModel": {"process": "direct"},
        "newAssumptions": [],
        "newBusinessActivities": [],
        "originTrace": [
            {
                "structureKey": item["structureKey"],
                "sourceValue": item["sourceValue"],
                "conceptValue": values[item["structureKey"]],
            }
            for item in required
        ],
        "legalTrace": [],
    }


def valid_idea_result():
    return {
        "originalSourceSummary": "입력 요약",
        "normalizedDescription": "정규화 설명",
        "facts": [],
        "assumptions": [],
        "constraints": [],
        "openQuestions": ["초기 지역은 어디입니까?"],
        "readiness": "UNDER_SPECIFIED",
        "warnings": [],
        "evidenceNeeds": [],
        "originDraft": {
            "productServiceDescription": "서비스 설명",
            "problem": ["문제"],
            "target": {"customerTypes": [], "segment": None, "situation": None, "needs": []},
            "solution": ["해결책"],
            "coreValue": ["핵심 가치"],
            "primaryCategory": "기타",
            "targetRegion": None,
            "fixedValues": [{
                "field": "productServiceDescription",
                "value": "서비스 설명",
            }],
            "confirmedValues": {},
            "assumptions": [],
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
            "key": "targetRegion",
            "sourceType": "AI_PROPOSED",
            "requiredForStages": ["IDEA_ORIGIN"],
            "status": "MISSING",
            "locked": False,
            "fallbackPolicy": "BLOCK_STAGE",
        }],
        "clarificationQuestions": [{
            "targetField": "targetRegion",
            "requirement": "REQUIRED_FOR_IDEA_ORIGIN",
            "question": "초기 지역은 어디입니까?",
            "reason": "Idea Origin 확정에 필요합니다.",
        }],
    }


def test_idea_interpretation_repairs_one_invalid_provider_result(monkeypatch, caplog):
    responses = [
        {"originalSourceSummary": "입력 요약", "unexpected": True},
        valid_idea_result(),
    ]

    async def fake_prompt(system, user):
        return responses.pop(0)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(
        journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
    )

    assert result["readiness"] == "UNDER_SPECIFIED"
    assert responses == []
    assert "<unknown-field>" in caplog.text
    assert "unexpected" not in caplog.text


def test_idea_interpretation_rejects_invalid_repair(monkeypatch):
    async def invalid_prompt(system, user):
        return {"originalSourceSummary": "입력 요약"}

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", invalid_prompt)

    with pytest.raises(journey_provider.ProviderFailure) as failure:
        asyncio.run(
            journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
        )

    assert failure.value.code == "RESULT_SCHEMA_INVALID"
    assert failure.value.retryable is False


def test_idea_interpretation_regenerates_once_after_invalid_json(monkeypatch):
    responses = [
        journey_provider.ProviderFailure(
            "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False
        ),
        valid_idea_result(),
    ]

    async def fake_prompt(system, user):
        response = responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)

    result = asyncio.run(
        journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
    )

    assert result["normalizedDescription"] == "정규화 설명"
    assert responses == []


def test_idea_interpretation_repairs_missing_required_questions(monkeypatch, caplog):
    caplog.set_level(logging.INFO)
    missing_question_result = valid_idea_result()
    missing_question_result["clarificationQuestions"] = []
    missing_question_result["openQuestions"] = []
    responses = [missing_question_result]

    async def fake_prompt(system, user):
        return responses.pop(0)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)

    result = asyncio.run(
        journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
    )

    assert result["clarificationQuestions"][0]["targetField"] == "targetRegion"
    assert "clarification auto-completed" in caplog.text
    assert "targetRegion" in caplog.text
    assert responses == []


def test_idea_interpretation_serializes_all_closed_contract_fields(monkeypatch):
    provider_result = valid_idea_result()
    for field in (
        "pricingIntent",
        "revenueModelIntent",
        "salesChannelIntent",
        "knownUnitCost",
        "differentiationIntent",
    ):
        provider_result["originDraft"].pop(field)
    provider_result["originDraft"]["target"].pop("situation")

    async def fake_prompt(system, user):
        return provider_result

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)

    result = asyncio.run(
        journey_provider.execute_journey_task("IDEA_INTERPRETATION", "아이디어")
    )

    origin = result["originDraft"]
    assert origin["pricingIntent"] is None
    assert origin["revenueModelIntent"] is None
    assert origin["salesChannelIntent"] is None
    assert origin["knownUnitCost"] is None
    assert origin["differentiationIntent"] is None
    assert origin["target"]["situation"] is None


def prompt_payload(user):
    start = user.find("{")
    payload, _ = json.JSONDecoder().raw_decode(user[start:])
    return payload


def valid_single_result(slot_index, task_input):
    return {
        "concept": valid_concept(slot_index, task_input["requiredOriginTrace"])
    }


def invalid_single_result(slot_index, task_input):
    result = valid_single_result(slot_index, task_input)
    result["concept"]["originTrace"][1].pop("conceptValue")
    return result


def test_concept_generation_fans_out_three_valid_slots(monkeypatch):
    task_input = concept_input()
    calls = []

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        calls.append((system, payload))
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert len(calls) == 3
    assert [item["conceptName"] for item in result["concepts"]] == [
        "Concept 0",
        "Concept 1",
        "Concept 2",
    ]
    assert all("concepts" not in payload for _, payload in calls)


def test_concept_generation_repairs_only_slot_zero(monkeypatch):
    task_input = concept_input()
    calls = []

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        phase = "repair" if system.startswith("Repair exactly") else "initial"
        calls.append((phase, payload))
        if payload["slotIndex"] == 0 and phase == "initial":
            return invalid_single_result(0, task_input)
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert len(calls) == 4
    assert [(phase, payload["slotIndex"]) for phase, payload in calls].count(
        ("repair", 0)
    ) == 1
    assert not any(
        phase == "repair" and payload["slotIndex"] in {1, 2}
        for phase, payload in calls
    )
    assert result["concepts"][1] == valid_concept(
        1, task_input["requiredOriginTrace"]
    )
    assert result["concepts"][2] == valid_concept(
        2, task_input["requiredOriginTrace"]
    )


def test_concept_generation_repairs_origin_trace_extra_field(monkeypatch):
    task_input = concept_input()
    calls = []

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        phase = "repair" if system.startswith("Repair exactly") else "initial"
        calls.append((phase, payload["slotIndex"]))
        if payload["slotIndex"] == 0 and phase == "initial":
            invalid = valid_single_result(0, task_input)
            invalid["concept"]["originTrace"][0]["unexpected"] = "forbidden"
            return invalid
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert len(calls) == 4
    assert ("repair", 0) in calls
    assert len(result["concepts"]) == 3


def test_concept_generation_repairs_slots_zero_and_two(monkeypatch):
    task_input = concept_input()
    calls = []

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        phase = "repair" if system.startswith("Repair exactly") else "initial"
        calls.append((phase, payload["slotIndex"]))
        if payload["slotIndex"] in {0, 2} and phase == "initial":
            return invalid_single_result(payload["slotIndex"], task_input)
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert len(calls) == 5
    assert sorted(item for item in calls if item[0] == "repair") == [
        ("repair", 0),
        ("repair", 2),
    ]
    assert len(result["concepts"]) == 3


def test_concept_generation_fails_when_one_slot_repair_is_invalid(monkeypatch):
    task_input = concept_input()
    calls = []

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        calls.append((system, payload["slotIndex"]))
        if payload["slotIndex"] == 0:
            return invalid_single_result(0, task_input)
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    with pytest.raises(journey_provider.ProviderFailure) as failure:
        asyncio.run(
            journey_provider.execute_journey_task(
                "CONCEPT_GENERATION", json.dumps(task_input)
            )
        )

    assert failure.value.code == "RESULT_SCHEMA_INVALID"
    assert failure.value.reason == "AI_RESULT_INVALID"
    assert failure.value.status_code == 502
    assert len(calls) == 4


def test_concept_generation_supports_desired_count_one(monkeypatch):
    task_input = concept_input(desired_count=1)
    calls = []

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        calls.append(payload["slotIndex"])
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert calls == [0]
    assert len(result["concepts"]) == 1


def test_concept_generation_preserves_slot_order_after_out_of_order_completion(
    monkeypatch,
):
    monkeypatch.setenv("AI_CONCEPT_GENERATION_CONCURRENCY", "3")
    task_input = concept_input()
    completion_order = []
    delays = {0: 0.02, 1: 0.04, 2: 0.0}

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        slot_index = payload["slotIndex"]
        await asyncio.sleep(delays[slot_index])
        completion_order.append(slot_index)
        return valid_single_result(slot_index, task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    result = asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert completion_order == [2, 0, 1]
    assert [item["conceptName"] for item in result["concepts"]] == [
        "Concept 0",
        "Concept 1",
        "Concept 2",
    ]


def test_concept_generation_passes_variation_focus_to_each_slot(monkeypatch):
    task_input = concept_input()
    payloads = []

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        payloads.append(payload)
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert [payload["variationFocus"] for payload in payloads] == list(
        journey_provider.CONCEPT_VARIATION_FOCUSES
    )


def test_concept_generation_passes_locked_values_and_trace_to_every_slot(monkeypatch):
    task_input = concept_input()
    task_input["lockedValues"] = {"pricingIntent": {"model": "fixed"}}
    payloads = []

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        payloads.append(payload)
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert len(payloads) == 3
    assert all(
        payload["lockedValues"] == task_input["lockedValues"] for payload in payloads
    )
    assert all(
        payload["requiredOriginTrace"] == task_input["requiredOriginTrace"]
        for payload in payloads
    )


def test_concept_generation_never_calls_batch_full_regeneration(monkeypatch):
    task_input = concept_input()
    systems = []

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        systems.append(system)
        if payload["slotIndex"] == 0 and not system.startswith("Repair exactly"):
            return invalid_single_result(0, task_input)
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert len(systems) == 4
    assert not any("full regeneration" in system.lower() for system in systems)
    assert not any('{"concepts":' in system for system in systems)


def test_concept_generation_logs_do_not_expose_raw_idea_or_candidate(
    monkeypatch, caplog
):
    task_input = concept_input()
    task_input["ideaOrigin"] = {"description": "RAW_IDEA_MARKER"}

    async def fake_prompt(system, user):
        payload = prompt_payload(user)
        if payload["slotIndex"] == 0 and not system.startswith("Repair exactly"):
            invalid = invalid_single_result(0, task_input)
            invalid["concept"]["positioning"] = "RAW_CANDIDATE_MARKER"
            return invalid
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert "RAW_IDEA_MARKER" not in caplog.text
    assert "RAW_CANDIDATE_MARKER" not in caplog.text


def test_concept_generation_respects_configured_concurrency(monkeypatch):
    task_input = concept_input()
    monkeypatch.setenv("AI_CONCEPT_GENERATION_CONCURRENCY", "2")
    active = 0
    maximum_active = 0

    async def fake_prompt(system, user):
        nonlocal active, maximum_active
        payload = prompt_payload(user)
        active += 1
        maximum_active = max(maximum_active, active)
        await asyncio.sleep(0.01)
        active -= 1
        return valid_single_result(payload["slotIndex"], task_input)

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    asyncio.run(
        journey_provider.execute_journey_task(
            "CONCEPT_GENERATION", json.dumps(task_input)
        )
    )

    assert maximum_active == 2


def valid_conversation_result():
    return {
        "extractedFields": [],
        "fieldSuggestions": [{
            "fieldKey": "problem", "valueKind": "TEXT",
            "textValue": "food waste", "listValue": [],
            "decisionStatus": "OPEN", "sourceType": "AI_PROPOSED",
            "confidence": 0.7,
        }],
        "assumptions": [],
        "openFields": ["targetCustomer", "targetRegion"],
        "contradictions": [],
        "clarificationQuestions": [
            {"id": "q1", "fieldKey": "targetCustomer", "prompt": "Who has this problem?",
             "type": "FREE_TEXT", "options": [], "allowUndecided": True},
            {"id": "q2", "fieldKey": "targetRegion", "prompt": "Which region?",
             "type": "FREE_TEXT", "options": [], "allowUndecided": True},
        ],
        "readiness": "NEEDS_INPUT",
        "userFacingSummary": "I need two details.",
    }


def test_conversation_intake_uses_dedicated_prompt_and_strict_schema(monkeypatch):
    captured = {}

    async def fake_prompt(system, user, **kwargs):
        captured["system"] = system
        captured["user"] = user
        captured["schema"] = kwargs["response_schema"]
        return valid_conversation_result()

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    input_value = {"conversationContract": "opportunity-brief-v1", "messages": []}
    result = asyncio.run(journey_provider.execute_journey_task(
        "IDEA_INTERPRETATION", json.dumps(input_value)
    ))

    assert result["fieldSuggestions"][0]["sourceType"] == "AI_PROPOSED"
    assert "Opportunity Brief" in captured["system"]
    assert captured["schema"] == journey_provider.ProviderOpportunityBriefDraftResult.model_json_schema()


def test_conversation_intake_never_accepts_user_confirmed_from_ai(monkeypatch):
    calls = 0

    async def fake_prompt(system, user, **kwargs):
        nonlocal calls
        calls += 1
        result = valid_conversation_result()
        result["fieldSuggestions"][0]["sourceType"] = "USER_CONFIRMED"
        return result

    monkeypatch.setattr(journey_provider, "execute_structured_prompt", fake_prompt)
    with pytest.raises(journey_provider.ProviderFailure) as failure:
        asyncio.run(journey_provider.execute_journey_task(
            "IDEA_INTERPRETATION",
            json.dumps({"conversationContract": "opportunity-brief-v1"}),
        ))
    assert failure.value.code == "RESULT_SCHEMA_INVALID"
    assert calls == 2
