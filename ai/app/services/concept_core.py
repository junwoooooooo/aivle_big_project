import asyncio
import hashlib
import json
import os
from typing import Any

from pydantic import ValidationError

from app.models.concept_core import ConceptExplorationResult, ConceptSkeleton
from app.services.journey_provider import ProviderFailure, execute_structured_prompt


FOCUSES = (
    "TARGET_AND_USER_EXPERIENCE",
    "OPERATING_MODEL_AND_PARTNERS",
    "REVENUE_AND_CHANNELS",
)
RULE_TYPES = {
    "PROHIBITED_ROLE", "PROHIBITED_ACTIVITY", "ALLOWED_PATTERN", "REQUIRED_CONTROL",
    "REQUIRED_PARTNER", "REQUIRED_DISCLOSURE", "UNRESOLVED_FACT",
}
PHASES = {"INITIAL", "REPAIR", "REDESIGN", "REPLACEMENT"}


def _configuration() -> int:
    raw = os.getenv("AI_CONCEPT_GENERATION_CONCURRENCY", "1").strip()
    try:
        value = int(raw)
    except ValueError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    if value not in {1, 2, 3}:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    return value


def _validate_input(task_input: dict[str, Any]) -> None:
    required = {
        "confirmedBriefVersionId", "confirmedBriefHash", "regulatoryBoundaryVersionId",
        "regulatoryBoundaryHash", "briefFields", "boundaryRules", "desiredCount",
        "maxInspectedCandidates", "maxReplacementRounds", "textContents",
    }
    optional = {"negativeConstraints", "testFailurePlan"}
    if set(task_input) - required - optional or not required.issubset(task_input):
        raise ProviderFailure("INVALID_REQUEST", "CONCEPT_EXPLORATION_INPUT_INVALID", 400, False)
    if task_input["desiredCount"] != 3 or task_input["maxInspectedCandidates"] > 9:
        raise ProviderFailure("INVALID_REQUEST", "CONCEPT_EXPLORATION_INPUT_INVALID", 400, False)
    if not isinstance(task_input["briefFields"], list) or not isinstance(task_input["boundaryRules"], list):
        raise ProviderFailure("INVALID_REQUEST", "CONCEPT_EXPLORATION_INPUT_INVALID", 400, False)
    if any(not isinstance(rule, dict) or rule.get("ruleType") not in RULE_TYPES for rule in task_input["boundaryRules"]):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "CONCEPT_UNKNOWN_RULE_TYPE", 502, False)
    if "testFailurePlan" in task_input and os.getenv("AI_CONCEPT_TEST_FAILURE_INJECTION", "false").lower() != "true":
        raise ProviderFailure("INVALID_REQUEST", "CONCEPT_FAILURE_INJECTION_DISABLED", 400, False)


def _prompt(task_input: dict[str, Any], slot_index: int, phase: str,
            invalid_result: dict[str, Any] | None, issues: list[dict[str, str]]) -> tuple[str, str]:
    focus = FOCUSES[slot_index % len(FOCUSES)]
    system = (
        "Create exactly one business Concept Skeleton as one JSON object. Follow requiredSchema exactly; "
        "unknown fields are forbidden. Never return sourceValue, originTrace, legalTrace, evidence IDs, "
        "law text, citations, final legal status, or formal legal advice. legalImplementationHypothesis is "
        "only an implementation hypothesis. Preserve LOCKED brief meaning and obey all boundary rules. "
        "Use the requested variationFocus."
    )
    payload: dict[str, Any] = {
        "slotIndex": slot_index,
        "attemptPhase": phase,
        "variationFocus": focus,
        "briefFields": task_input["briefFields"],
        "boundaryRules": task_input["boundaryRules"],
        "negativeConstraints": task_input.get("negativeConstraints", []),
        "requiredSchema": ConceptSkeleton.model_json_schema(),
    }
    if phase == "REPAIR":
        system = "Repair only strict JSON/schema defects in one Concept Skeleton. Do not redesign its business meaning. " + system
        payload["invalidResult"] = invalid_result
        payload["validationIssues"] = issues
    elif phase in {"REDESIGN", "REPLACEMENT"}:
        system = "Generate a structurally different compliant candidate without reproducing rejected roles or activities. " + system
    return system, json.dumps(payload, ensure_ascii=False, separators=(",", ":"))


def _issues(failure: ValidationError) -> list[dict[str, str]]:
    return [{"path": ".".join(map(str, item["loc"])), "type": item["type"]}
            for item in failure.errors(include_input=False, include_url=False)[:20]]


def _failure_outcome(failure: ProviderFailure) -> str:
    return "TRANSIENT_PROVIDER_FAILURE" if failure.retryable else "PERMANENT_PROVIDER_FAILURE"


async def _call(task_input: dict[str, Any], slot_index: int, phase: str,
                attempt_number: int, semaphore: asyncio.Semaphore,
                invalid_result: dict[str, Any] | None = None,
                issues: list[dict[str, str]] | None = None) -> dict[str, Any]:
    failure_plan = task_input.get("testFailurePlan", {})
    if os.getenv("AI_CONCEPT_TEST_FAILURE_INJECTION", "false").lower() == "true":
        raw_plan = os.getenv("AI_CONCEPT_TEST_FAILURE_PLAN", "").strip()
        if raw_plan:
            try:
                environment_plan = json.loads(raw_plan)
            except json.JSONDecodeError as failure:
                raise ProviderFailure(
                    "DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False
                ) from failure
            if not isinstance(environment_plan, dict):
                raise ProviderFailure(
                    "DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False
                )
            failure_plan = environment_plan
    injected = failure_plan.get(str(slot_index), {}).get(str(attempt_number))
    if injected:
        return {"attemptNumber": attempt_number, "phase": phase, "outcome": injected,
                "candidate": None, "safeFailureType": injected, "duplicateStatus": None,
                "negativeConstraint": {"failureType": injected}}
    system, user = _prompt(task_input, slot_index, phase, invalid_result, issues or [])
    try:
        async with semaphore:
            raw = await execute_structured_prompt(system, user)
    except ProviderFailure as failure:
        outcome = _failure_outcome(failure)
        return {"attemptNumber": attempt_number, "phase": phase, "outcome": outcome,
                "candidate": None, "safeFailureType": outcome, "duplicateStatus": None,
                "negativeConstraint": {"failureType": outcome}}
    try:
        concept = ConceptSkeleton.model_validate(raw)
        return {"attemptNumber": attempt_number, "phase": phase, "outcome": "VALID",
                "candidate": concept.model_dump(), "safeFailureType": None,
                "duplicateStatus": "UNIQUE", "negativeConstraint": {}}
    except ValidationError as failure:
        return {"attemptNumber": attempt_number, "phase": phase, "outcome": "SCHEMA_INVALID",
                "candidate": None, "safeFailureType": "SCHEMA_INVALID", "duplicateStatus": None,
                "negativeConstraint": {"failureType": "SCHEMA_INVALID",
                                       "validationIssues": _issues(failure)},
                "_invalid": raw, "_issues": _issues(failure)}


async def _slot(task_input: dict[str, Any], slot_index: int, phase: str,
                semaphore: asyncio.Semaphore) -> dict[str, Any]:
    first = await _call(task_input, slot_index, phase, 1, semaphore)
    attempts = [first]
    if first["outcome"] == "SCHEMA_INVALID":
        attempts.append(await _call(task_input, slot_index, "REPAIR", 2, semaphore,
                                    first.get("_invalid"), first.get("_issues")))
    elif first["outcome"] == "TRANSIENT_PROVIDER_FAILURE":
        attempts.append(await _call(task_input, slot_index, phase, 2, semaphore))
    if attempts[-1]["outcome"] == "VALID" and _requires_redesign(
        attempts[-1]["candidate"], task_input["boundaryRules"]
    ):
        attempts.append(await _call(
            task_input, slot_index, "REDESIGN", len(attempts) + 1, semaphore
        ))
    for attempt in attempts:
        attempt.pop("_invalid", None)
        attempt.pop("_issues", None)
    accepted = attempts[-1]["outcome"] == "VALID" and not _requires_redesign(
        attempts[-1]["candidate"], task_input["boundaryRules"]
    )
    return {"slotIndex": slot_index, "variationFocus": FOCUSES[slot_index % 3],
            "attempts": attempts, "accepted": accepted}


def _dedupe_key(candidate: dict[str, Any]) -> str:
    fields = {key: candidate[key] for key in (
        "targetSegment", "solutionMechanism", "platformRole", "actorRoles",
        "operatingModel", "revenueModelHypothesis", "partnerRequirements",
    )}
    canonical = json.dumps(fields, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode()).hexdigest()


def _requires_redesign(candidate: dict[str, Any], rules: list[dict[str, Any]]) -> bool:
    for rule in rules:
        if rule["ruleType"] == "REQUIRED_PARTNER" and not candidate["partnerRequirements"]:
            return True
    return False


async def execute_concept_exploration(task_input: dict[str, Any]) -> dict[str, Any]:
    _validate_input(task_input)
    semaphore = asyncio.Semaphore(_configuration())
    slots: list[dict[str, Any]] = []
    accepted: list[int] = []
    seen: set[str] = set()
    next_index = 0
    while len(accepted) < 3 and next_index < task_input["maxInspectedCandidates"]:
        width = min(3 - len(accepted), task_input["maxInspectedCandidates"] - next_index)
        phase = "INITIAL" if next_index < 3 else "REPLACEMENT"
        round_results = await asyncio.gather(
            *(_slot(task_input, next_index + offset, phase, semaphore) for offset in range(width)),
            return_exceptions=True,
        )
        for offset, result in enumerate(round_results):
            slot_index = next_index + offset
            if isinstance(result, Exception):
                result = {"slotIndex": slot_index, "variationFocus": FOCUSES[slot_index % 3],
                          "attempts": [{"attemptNumber": 1, "phase": phase,
                                        "outcome": "PERMANENT_PROVIDER_FAILURE", "candidate": None,
                                        "safeFailureType": "PERMANENT_PROVIDER_FAILURE",
                                        "duplicateStatus": None,
                                        "negativeConstraint": {"failureType": "PERMANENT_PROVIDER_FAILURE"}}],
                          "accepted": False}
            if result["accepted"]:
                candidate = result["attempts"][-1]["candidate"]
                key = _dedupe_key(candidate)
                if key in seen:
                    result["attempts"][-1]["duplicateStatus"] = "DUPLICATE"
                    result["attempts"][-1]["negativeConstraint"] = {
                        "failureType": "DUPLICATE", "requiredDifference": "business structure"
                    }
                    result["accepted"] = False
                else:
                    seen.add(key)
                    accepted.append(slot_index)
            slots.append(result)
        next_index += width
    value = {"slots": slots, "acceptedSlotIndices": accepted,
             "eligibleCandidateCount": len(accepted), "exhausted": len(accepted) < 3}
    return ConceptExplorationResult.model_validate(value).model_dump()
