import asyncio

import pytest

from app.services import concept_core
from app.services.journey_provider import ProviderFailure


def task_input():
    return {
        "confirmedBriefVersionId": 11,
        "confirmedBriefHash": "sha256:" + "a" * 64,
        "regulatoryBoundaryVersionId": 21,
        "regulatoryBoundaryHash": "sha256:" + "b" * 64,
        "briefFields": [{"fieldKey": "problem", "value": "waste", "decisionStatus": "LOCKED"}],
        "boundaryRules": [{"ruleId": "r1", "ruleType": "REQUIRED_CONTROL",
                           "structureKey": "data", "normalizedRequirement": "minimize data",
                           "evidenceIds": ["e1"]}],
        "desiredCount": 3,
        "maxInspectedCandidates": 9,
        "maxReplacementRounds": 2,
        "textContents": [{"contentKey": "brief", "contentType": "TEXT", "language": "ko-KR",
                          "totalCharacters": 1, "contentHash": "sha256:x", "chunks": []}],
    }


def candidate(index):
    return {
        "conceptName": f"Concept {index}", "oneLineSummary": f"Summary {index}",
        "targetSegment": {"segment": f"segment-{index}"}, "problemScenario": "waste problem",
        "valueProposition": "less waste", "solutionMechanism": f"mechanism-{index}",
        "actorRoles": [{"actor": "operator", "role": "platform", "responsibilities": ["match"],
                        "permissions": ["schedule"], "prohibitedResponsibilities": ["unlicensed collection"]}],
        "platformRole": "matching only", "transactionFlow": [{"step": 1, "actor": "user",
        "action": "request", "assetOrPayment": "request", "responsibility": "accurate input"}],
        "dataFlow": [], "physicalActivities": [], "partnerRequirements": [f"partner-{index}"],
        "featureSet": ["matching"], "channelHypothesis": ["web"],
        "pricingHypothesis": {"model": "subscription"},
        "revenueModelHypothesis": {"model": f"fee-{index}"},
        "operatingModel": {"operator": f"platform-{index}"}, "assumptions": [], "risks": [],
        "legalImplementationHypothesis": "licensed partner performs regulated work",
    }


def prompt_payload(user):
    import json
    return json.loads(user)


def test_strict_schema_rejects_authoritative_traces(monkeypatch):
    calls = {}
    async def fake(system, user):
        value = candidate(prompt_payload(user)["slotIndex"])
        value["originTrace"] = []
        return value
    monkeypatch.setattr(concept_core, "execute_structured_prompt", fake)
    result = asyncio.run(concept_core.execute_concept_exploration(task_input()))
    assert result["eligibleCandidateCount"] == 0
    assert all(slot["attempts"][0]["outcome"] == "SCHEMA_INVALID" for slot in result["slots"][:3])


def test_mixed_failures_preserve_valid_and_repair_only_schema_invalid(monkeypatch):
    counts = {}
    async def fake(system, user):
        payload = prompt_payload(user); index = payload["slotIndex"]
        counts[index] = counts.get(index, 0) + 1
        if index == 1 and counts[index] == 1:
            return {"conceptName": "broken"}
        if index == 2 and counts[index] == 1:
            raise ProviderFailure("RATE_LIMITED", "DEPENDENCY_RATE_LIMITED", 429, True)
        return candidate(index)
    monkeypatch.setattr(concept_core, "execute_structured_prompt", fake)
    result = asyncio.run(concept_core.execute_concept_exploration(task_input()))
    assert result["acceptedSlotIndices"] == [0, 1, 2]
    assert [len(slot["attempts"]) for slot in result["slots"][:3]] == [1, 2, 2]
    assert result["slots"][1]["attempts"][1]["phase"] == "REPAIR"


def test_permanent_failure_does_not_block_sibling_repair_and_replacement(monkeypatch):
    counts = {}
    async def fake(system, user):
        payload = prompt_payload(user); index = payload["slotIndex"]
        counts[index] = counts.get(index, 0) + 1
        if index == 0:
            raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
        if index == 1 and counts[index] == 1:
            return {"conceptName": "broken"}
        return candidate(index)
    monkeypatch.setattr(concept_core, "execute_structured_prompt", fake)
    result = asyncio.run(concept_core.execute_concept_exploration(task_input()))
    assert result["slots"][0]["attempts"][0]["outcome"] == "PERMANENT_PROVIDER_FAILURE"
    assert result["slots"][1]["attempts"][1]["outcome"] == "VALID"
    assert result["acceptedSlotIndices"] == [1, 2, 3]


def test_duplicate_is_replaced(monkeypatch):
    async def fake(system, user):
        index = prompt_payload(user)["slotIndex"]
        return candidate(0 if index == 1 else index)
    monkeypatch.setattr(concept_core, "execute_structured_prompt", fake)
    result = asyncio.run(concept_core.execute_concept_exploration(task_input()))
    assert result["slots"][1]["attempts"][-1]["duplicateStatus"] == "DUPLICATE"
    assert result["acceptedSlotIndices"] == [0, 2, 3]


def test_redesign_required_candidate_is_redesigned_once(monkeypatch):
    value = task_input()
    value["boundaryRules"] = [{"ruleId": "r2", "ruleType": "REQUIRED_PARTNER",
                               "structureKey": "licensedPartner",
                               "normalizedRequirement": "use a licensed partner",
                               "evidenceIds": ["e2"]}]
    calls = {}
    async def fake(system, user):
        payload = prompt_payload(user); index = payload["slotIndex"]
        calls[index] = calls.get(index, 0) + 1
        result = candidate(index)
        if calls[index] == 1:
            result["partnerRequirements"] = []
        return result
    monkeypatch.setattr(concept_core, "execute_structured_prompt", fake)
    result = asyncio.run(concept_core.execute_concept_exploration(value))
    assert result["eligibleCandidateCount"] == 3
    assert all(slot["attempts"][1]["phase"] == "REDESIGN" for slot in result["slots"][:3])


def test_default_concurrency_is_one_and_invalid_value_fails(monkeypatch):
    monkeypatch.delenv("AI_CONCEPT_GENERATION_CONCURRENCY", raising=False)
    assert concept_core._configuration() == 1
    monkeypatch.setenv("AI_CONCEPT_GENERATION_CONCURRENCY", "4")
    with pytest.raises(ProviderFailure):
        concept_core._configuration()


def test_unknown_rule_type_is_rejected():
    value = task_input(); value["boundaryRules"][0]["ruleType"] = "MADE_UP"
    with pytest.raises(ProviderFailure) as failure:
        asyncio.run(concept_core.execute_concept_exploration(value))
    assert failure.value.reason == "CONCEPT_UNKNOWN_RULE_TYPE"


def test_environment_failure_plan_requires_explicit_development_flag(monkeypatch):
    monkeypatch.setenv("AI_CONCEPT_TEST_FAILURE_INJECTION", "true")
    monkeypatch.setenv(
        "AI_CONCEPT_TEST_FAILURE_PLAN",
        '{"0":{"1":"PERMANENT_PROVIDER_FAILURE"}}',
    )

    async def fake(system, user):
        return candidate(prompt_payload(user)["slotIndex"])

    monkeypatch.setattr(concept_core, "execute_structured_prompt", fake)
    result = asyncio.run(concept_core.execute_concept_exploration(task_input()))
    assert result["slots"][0]["attempts"][0]["outcome"] == "PERMANENT_PROVIDER_FAILURE"
    assert result["acceptedSlotIndices"] == [1, 2, 3]
