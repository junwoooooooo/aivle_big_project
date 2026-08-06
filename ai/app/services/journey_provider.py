import asyncio
import json
import logging
import os
import re
from copy import deepcopy
from pathlib import Path
from time import perf_counter
from typing import Any, Callable

import httpx
from pydantic import ValidationError

from app.models.journey import (
    ConceptGenerationResult,
    DetailedAnalysisResult,
    IdeaInterpretationResult,
    OpportunityBriefDraftResult,
    InterviewSynthesisResult,
    LegalReviewResult,
    MarketingComparisonResult,
    MarketingGenerationResult,
    PersonaCardGenerationResult,
    PersonaInterviewResult,
    QuickAssessmentResult,
    SingleConceptGenerationResult,
    FinalReportResult,
)
from app.models.idea_conversation_provider import (
    ProviderOpportunityBriefDraftResult,
    provider_result_to_domain,
)


PROMPT_ROOT = Path(__file__).resolve().parents[2] / "prompts"
logger = logging.getLogger(__name__)


class ProviderFailure(Exception):
    def __init__(
        self,
        code: str,
        reason: str,
        status_code: int,
        retryable: bool,
        *,
        upstream_status: int | None = None,
        provider_error_type: str | None = None,
        provider_error_param: str | None = None,
        schema_name: str | None = None,
    ):
        super().__init__(reason)
        self.code = code
        self.reason = reason
        self.status_code = status_code
        self.retryable = retryable
        self.upstream_status = upstream_status
        self.provider_error_type = provider_error_type
        self.provider_error_param = provider_error_param
        self.schema_name = schema_name


def _configuration(model_override: str | None = None) -> tuple[str, str, str]:
    provider = os.getenv("AI_PROVIDER", "").strip().lower()
    api_key = os.getenv("AI_API_KEY", "").strip()
    model = (model_override or "").strip() or os.getenv("AI_MODEL", "").strip()
    base_url = os.getenv("AI_BASE_URL", "").strip().rstrip("/")
    if provider not in {"openai", "openai-compatible"} or not api_key or not model:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    if provider == "openai" and not base_url:
        base_url = "https://api.openai.com/v1"
    if not base_url.startswith(("http://", "https://")):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    return api_key, model, base_url


def _load_prompts(task_type: str, text: str) -> tuple[str, str]:
    folders = {
        "IDEA_INTERPRETATION": "idea_interpretation",
        "IDEA_CONVERSATION_TURN": "idea_conversation_turn",
        "IDEA_CONVERSATION": "idea_conversation_turn",
        "LEGAL_REVIEW": "legal_review",
        "CONCEPT_GENERATION": "concept_generation",
        "QUICK_ASSESSMENT": "quick_assessment",
        "DETAILED_ANALYSIS": "detailed_analysis",
        "PERSONA_CARD_GENERATION": "persona_card_generation",
        "PERSONA_INTERVIEW": "persona_interview",
        "INTERVIEW_SYNTHESIS": "interview_synthesis",
        "MARKETING_GENERATION": "marketing_generation",
        "MARKETING_COMPARISON": "marketing_comparison",
        "FINAL_REPORT_GENERATION": "final_report_generation",
    }
    folder = folders.get(task_type)
    if folder is None:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    try:
        system = (PROMPT_ROOT / folder / "system.md").read_text(encoding="utf-8")
        template = (PROMPT_ROOT / folder / "user.md").read_text(encoding="utf-8")
    except OSError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    return system, template.replace("{{input}}", text)


def _extract_json(content: str) -> dict[str, Any]:
    fenced = re.search(r"```(?:json)?\s*([\s\S]*?)```", content, flags=re.IGNORECASE)
    candidate = fenced.group(1).strip() if fenced else content.strip()
    start = candidate.find("{")
    if start < 0:
        raise ValueError("JSON object not found")
    value, end = json.JSONDecoder().raw_decode(candidate[start:])
    if not isinstance(value, dict) or candidate[start + end:].strip():
        raise ValueError("Provider result is not one JSON object")
    return value


async def execute_journey_task(
    task_type: str,
    text: str,
    on_schema_repair: Callable[[int], None] | None = None,
) -> dict[str, Any]:
    if task_type == "CONCEPT_GENERATION":
        return await _execute_concept_generation(text)
    conversation_intake = _is_conversation_intake(task_type, text)
    prompt_type = "IDEA_CONVERSATION" if conversation_intake else task_type
    system, user = _load_prompts(prompt_type, text)
    result_schema = ProviderOpportunityBriefDraftResult.model_json_schema() if conversation_intake else None
    if conversation_intake:
        system, user = _conversation_contract_prompt(system, user, result_schema)
    try:
        if conversation_intake:
            raw_result = await execute_structured_prompt(
                system,
                user,
                response_schema=result_schema,
                schema_name="opportunity_brief_draft_v1",
                task_type="IDEA_CONVERSATION_TURN",
            )
        else:
            raw_result = await execute_structured_prompt(system, user)
    except ProviderFailure as first_failure:
        if (
            task_type != "IDEA_INTERPRETATION"
            or first_failure.code != "RESULT_SCHEMA_INVALID"
        ):
            raise
        logger.warning(
            "Journey provider JSON regeneration taskType=%s phase=initial",
            task_type,
        )
        raw_result = await execute_structured_prompt(system, user)
    model_types = {
        "IDEA_INTERPRETATION": IdeaInterpretationResult,
        "LEGAL_REVIEW": LegalReviewResult,
        "CONCEPT_GENERATION": ConceptGenerationResult,
        "QUICK_ASSESSMENT": QuickAssessmentResult,
        "DETAILED_ANALYSIS": DetailedAnalysisResult,
        "PERSONA_CARD_GENERATION": PersonaCardGenerationResult,
        "PERSONA_INTERVIEW": PersonaInterviewResult,
        "INTERVIEW_SYNTHESIS": InterviewSynthesisResult,
        "MARKETING_GENERATION": MarketingGenerationResult,
        "MARKETING_COMPARISON": MarketingComparisonResult,
        "FINAL_REPORT_GENERATION": FinalReportResult,
    }
    try:
        if conversation_intake:
            return provider_result_to_domain(raw_result).model_dump(
                by_alias=True, exclude_unset=True
            )
        model_type = model_types[task_type]
        return model_type.model_validate(raw_result).model_dump(
            by_alias=True,
            # Spring validates the Idea Origin object as a closed contract and
            # therefore requires nullable/defaulted properties to be present.
            exclude_unset=task_type != "IDEA_INTERPRETATION" or conversation_intake,
        )
    except KeyError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    except ValidationError as first_failure:
        provider_model_type = ProviderOpportunityBriefDraftResult if conversation_intake else model_type
        issues = _validation_issues(first_failure, provider_model_type)
        if conversation_intake:
            logger.warning(
                "Journey provider result schema invalid taskType=%s phase=initial issues=%s",
                task_type,
                issues,
            )
            if on_schema_repair is not None:
                on_schema_repair(len(issues))
            repaired_result = await _repair_conversation_result(raw_result, issues)
            try:
                return provider_result_to_domain(repaired_result).model_dump(
                    by_alias=True, exclude_unset=True
                )
            except ValidationError as repair_failure:
                logger.warning(
                    "Journey provider result schema invalid taskType=%s phase=repair issues=%s",
                    task_type,
                    _validation_issues(repair_failure, ProviderOpportunityBriefDraftResult),
                )
                raise ProviderFailure(
                    "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False
                ) from repair_failure
        if task_type != "IDEA_INTERPRETATION":
            logger.warning(
                "Journey provider result schema invalid taskType=%s phase=initial issues=%s",
                task_type,
                issues,
            )
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from first_failure

        missing_fields = _only_missing_clarification_fields(first_failure)
        if missing_fields:
            logger.info(
                "Journey provider clarification auto-completed taskType=%s fields=%s",
                task_type,
                missing_fields,
            )
            completed_result = _complete_missing_clarifications(
                raw_result, missing_fields
            )
            return model_type.model_validate(completed_result).model_dump(
                by_alias=True, exclude_unset=False
            )

        logger.warning(
            "Journey provider result schema invalid taskType=%s phase=initial issues=%s",
            task_type,
            issues,
        )
        repaired_result = await _repair_idea_interpretation_result(
            model_type, raw_result, issues
        )
        try:
            return model_type.model_validate(repaired_result).model_dump(
                by_alias=True, exclude_unset=False
            )
        except ValidationError as repair_failure:
            missing_fields = _only_missing_clarification_fields(repair_failure)
            if missing_fields:
                completed_result = _complete_missing_clarifications(
                    repaired_result, missing_fields
                )
                return model_type.model_validate(completed_result).model_dump(
                    by_alias=True, exclude_unset=False
                )
            logger.warning(
                "Journey provider result schema invalid taskType=%s phase=repair issues=%s",
                task_type,
                _validation_issues(repair_failure, model_type),
            )
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False
            ) from repair_failure


def _is_conversation_intake(task_type: str, text: str) -> bool:
    if task_type == "IDEA_CONVERSATION_TURN":
        return True
    if task_type != "IDEA_INTERPRETATION":
        return False
    try:
        value = json.loads(text)
        return isinstance(value, dict) and value.get("conversationContract") == "opportunity-brief-v1"
    except (TypeError, json.JSONDecodeError):
        return False


def _validation_issues(failure: ValidationError, model_type) -> list[dict[str, str]]:
    """Return bounded diagnostics without input values or provider output."""
    known_fields = _schema_property_names(model_type.model_json_schema())
    issues = []
    for error in failure.errors(
        include_url=False, include_context=True, include_input=False
    )[:20]:
        issue = {
            "path": ".".join(
                str(part) if isinstance(part, int)
                else part if part in known_fields
                else "<unknown-field>"
                for part in error["loc"]
            ),
            "type": error["type"],
        }
        if error["type"] == "idea_missing_clarification":
            fields = error.get("ctx", {}).get("fields", "")
            safe_fields = [field for field in fields.split(",") if field in known_fields]
            issue["fields"] = ",".join(safe_fields)
        issues.append(issue)
    return issues


def _conversation_contract_prompt(
    system: str, user: str, result_schema: dict[str, Any]
) -> tuple[str, str]:
    valid_example = ProviderOpportunityBriefDraftResult.model_validate({
        "extractedFields": [],
        "fieldSuggestions": [{
            "fieldKey": "problem",
            "valueKind": "TEXT",
            "textValue": "반복되는 고객 문제",
            "listValue": [],
            "decisionStatus": "OPEN",
            "sourceType": "AI_PROPOSED",
            "confidence": 0.72,
        }],
        "assumptions": [],
        "openFields": ["targetCustomer", "targetRegion"],
        "contradictions": [],
        "clarificationQuestions": [
            {
                "id": "target-customer-1",
                "fieldKey": "targetCustomer",
                "prompt": "이 문제를 가장 자주 겪는 대상은 누구인가요?",
                "type": "FREE_TEXT",
                "options": [],
                "allowUndecided": True,
            },
            {
                "id": "target-region-1",
                "fieldKey": "targetRegion",
                "prompt": "우선 검토할 국가 또는 지역은 어디인가요?",
                "type": "FREE_TEXT",
                "options": [],
                "allowUndecided": True,
            },
        ],
        "readiness": "NEEDS_INPUT",
        "userFacingSummary": "확인을 위해 두 가지 정보가 더 필요합니다.",
    }).model_dump(mode="json")
    contract = {
        "rules": [
            "Human-facing Korean text is allowed only in descriptive text fields.",
            "Machine enum fields must use the exact English literals in resultSchema.",
            "confidence must be a JSON number from 0.0 through 1.0, or null where allowed.",
            "clarificationQuestions.id must be a non-empty JSON string.",
            "clarificationQuestions.options must always be a JSON array.",
            "Return exactly one JSON object without Markdown or code fences.",
            "Do not add fields outside resultSchema.",
        ],
        "resultSchema": result_schema,
        "validExample": valid_example,
    }
    return system, user + "\n\nRESULT CONTRACT\n" + json.dumps(
        contract, ensure_ascii=False, separators=(",", ":")
    )


async def _repair_conversation_result(
    raw_result: dict[str, Any],
    issues: list[dict[str, str]],
) -> dict[str, Any]:
    schema = ProviderOpportunityBriefDraftResult.model_json_schema()
    system = (
        "You repair one Opportunity Brief result to the supplied strict JSON schema. "
        "Preserve valid information and meaning. Correct only types, canonical literals, "
        "and schema structure. Do not invent missing business facts. Return exactly one "
        "JSON object without Markdown or code fences."
    )
    user = json.dumps({
        "attemptPhase": "REPAIR",
        "validationIssues": issues,
        "resultSchema": schema,
        "invalidCandidate": raw_result,
    }, ensure_ascii=False, separators=(",", ":"))
    return await execute_structured_prompt(
        system,
        user,
        response_schema=schema,
        schema_name="opportunity_brief_draft_repair_v1",
        task_type="IDEA_CONVERSATION_TURN",
    )


def _concept_generation_context(text: str) -> dict[str, Any]:
    try:
        task_input = json.loads(text)
        desired_count = task_input["desiredCount"]
        required = task_input["requiredOriginTrace"]
        if (
            not isinstance(desired_count, int)
            or desired_count < 1
            or not isinstance(required, list)
            or not required
            or any(
                not isinstance(item, dict)
                or set(item) != {"structureKey", "sourceValue"}
                or not isinstance(item["structureKey"], str)
                or not item["structureKey"].strip()
                for item in required
            )
            or len({item["structureKey"] for item in required}) != len(required)
        ):
            raise ValueError
        return {
            "originalInput": task_input,
            "desiredCount": desired_count,
            "requiredOriginTrace": required,
        }
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as failure:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False
        ) from failure


def _only_missing_clarification_fields(failure: ValidationError) -> list[str]:
    errors = failure.errors(
        include_url=False, include_context=True, include_input=False
    )
    if not errors or any(
        error["type"] != "idea_missing_clarification" for error in errors
    ):
        return []
    allowed_fields = {
        "productServiceDescription", "problem", "target", "solution",
        "coreValue", "primaryCategory", "targetRegion", "fixedValues",
    }
    return sorted({
        field
        for error in errors
        for field in error.get("ctx", {}).get("fields", "").split(",")
        if field in allowed_fields
    })


def _complete_missing_clarifications(
    raw_result: dict[str, Any], missing_fields: list[str]
) -> dict[str, Any]:
    questions = {
        "productServiceDescription": "제공하려는 제품 또는 서비스를 한 문장으로 설명해 주세요.",
        "problem": "이 아이디어가 해결하려는 핵심 문제는 무엇입니까?",
        "target": "이 제품 또는 서비스를 가장 먼저 사용할 고객은 누구입니까?",
        "solution": "핵심 문제를 어떤 방식으로 해결합니까?",
        "coreValue": "고객에게 제공하는 가장 중요한 가치는 무엇입니까?",
        "primaryCategory": "이 아이디어의 주된 제품·서비스 카테고리는 무엇입니까?",
        "targetRegion": "서비스를 처음 제공할 국가 또는 지역은 어디입니까?",
        "fixedValues": "향후 Concept에서도 반드시 변경하지 않고 유지할 조건이나 값이 있습니까? 없다면 '없음'이라고 답해 주세요.",
    }
    completed = deepcopy(raw_result)
    clarification_items = completed.setdefault("clarificationQuestions", [])
    existing_targets = {
        item.get("targetField") for item in clarification_items
        if isinstance(item, dict)
    }
    for field in missing_fields:
        if field not in existing_targets:
            clarification_items.append({
                "targetField": field,
                "requirement": "REQUIRED_FOR_IDEA_ORIGIN",
                "question": questions[field],
                "reason": "Idea Origin 확정에 필요한 필수 입력입니다.",
            })
    completed["openQuestions"] = [
        item["question"] for item in clarification_items
    ]

    metadata_items = completed.setdefault("fieldMetadata", [])
    metadata_keys = {
        item.get("key") for item in metadata_items if isinstance(item, dict)
    }
    for field in missing_fields:
        if field not in metadata_keys:
            metadata_items.append({
                "key": field,
                "sourceType": "AI_PROPOSED",
                "requiredForStages": ["IDEA_ORIGIN"],
                "status": "MISSING",
                "locked": False,
                "fallbackPolicy": "BLOCK_STAGE",
            })
    return completed


def _schema_property_names(value: Any) -> set[str]:
    if isinstance(value, dict):
        names = set(value.get("properties", {}))
        for nested in value.values():
            names.update(_schema_property_names(nested))
        return names
    if isinstance(value, list):
        names: set[str] = set()
        for nested in value:
            names.update(_schema_property_names(nested))
        return names
    return set()


async def _repair_idea_interpretation_result(
    model_type, raw_result: dict[str, Any], issues: list[dict[str, str]]
) -> dict[str, Any]:
    system = (
        "당신은 JSON Contract 복구기입니다. 제공된 invalidResult의 의미를 바꾸거나 "
        "새로운 사실을 추가하지 말고 requiredSchema에 정확히 맞는 JSON 객체 하나만 반환하세요. "
        "모든 required 필드를 포함하고, 값이 없으면 스키마가 허용하는 null 또는 빈 배열을 "
        "사용하세요. extra field는 제거하세요. fieldMetadata의 status가 MISSING이면 "
        "sourceType은 AI_PROPOSED이고 locked는 false입니다. Idea Origin 필수 필드 "
        "productServiceDescription, problem, target, solution, coreValue, primaryCategory, "
        "targetRegion, fixedValues 중 null, 빈 문자열, 빈 배열인 각 필드에는 그 field 이름을 "
        "targetField로 쓰는 REQUIRED_FOR_IDEA_ORIGIN clarificationQuestions 항목을 반드시 "
        "추가하고, openQuestions에는 그 question 문자열을 같은 순서로 넣으세요. "
        "설명과 Markdown을 출력하지 마세요."
    )
    user = json.dumps(
        {
            "validationIssues": issues,
            "requiredSchema": model_type.model_json_schema(),
            "invalidResult": raw_result,
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return await execute_structured_prompt(system, user)


CONCEPT_VARIATION_FOCUSES = (
    "TARGET_AND_USER_EXPERIENCE",
    "OPERATING_MODEL_AND_PARTNERS",
    "REVENUE_AND_CHANNELS",
)


def _concept_generation_concurrency() -> int:
    raw_value = os.getenv("AI_CONCEPT_GENERATION_CONCURRENCY", "1").strip()
    try:
        value = int(raw_value)
    except ValueError as failure:
        raise ProviderFailure(
            "DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False
        ) from failure
    if value < 1 or value > 3:
        raise ProviderFailure(
            "DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False
        )
    return value


def _single_concept_issues(failure: ValidationError) -> list[dict[str, str]]:
    known_fields = _schema_property_names(
        SingleConceptGenerationResult.model_json_schema()
    )
    return [
        {
            "path": ".".join(
                str(part)
                if isinstance(part, int)
                else part
                if part in known_fields
                else "<unknown-field>"
                for part in error["loc"]
            ),
            "type": error["type"],
        }
        for error in failure.errors(
            include_url=False, include_context=True, include_input=False
        )[:20]
    ]


def _concept_slot_payload(
    context: dict[str, Any], slot_index: int, variation_focus: str
) -> dict[str, Any]:
    original = context["originalInput"]
    return {
        "slotIndex": slot_index,
        "variationFocus": variation_focus,
        "round": original.get("round", 0),
        "ideaOrigin": original.get("ideaOrigin", {}),
        "requiredOriginTrace": context["requiredOriginTrace"],
        "lockedValues": original.get("lockedValues", {}),
        "legalGuardrail": original.get("legalGuardrail", {}),
        "negativeConstraints": original.get("negativeConstraints", []),
        "acceptedConcepts": original.get("acceptedConcepts", []),
        "requiredSchema": SingleConceptGenerationResult.model_json_schema(),
    }


def _concept_slot_prompts(
    context: dict[str, Any],
    slot_index: int,
    variation_focus: str,
    phase: str,
    invalid_result: dict[str, Any] | None = None,
    validation_issues: list[dict[str, str]] | None = None,
) -> tuple[str, str]:
    payload = _concept_slot_payload(context, slot_index, variation_focus)
    if phase == "repair":
        payload["validationIssues"] = validation_issues or []
        payload["invalidResult"] = invalid_result
        system = (
            "Repair exactly one invalid Concept candidate. Return one JSON object with exactly "
            "the field concept containing a complete ConceptCandidate. Preserve the candidate's "
            "business meaning and do not invent defaults merely to satisfy JSON. Follow "
            "requiredSchema, requiredOriginTrace, lockedValues, legalGuardrail, and "
            "negativeConstraints. Do not return concepts, repairs, Markdown, or explanation."
        )
        return system, json.dumps(
            payload, ensure_ascii=False, separators=(",", ":")
        )
    return _load_prompts(
        "CONCEPT_GENERATION",
        json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
    )


async def _call_concept_slot(
    context: dict[str, Any],
    slot_index: int,
    variation_focus: str,
    phase: str,
    semaphore: asyncio.Semaphore,
    invalid_result: dict[str, Any] | None = None,
    validation_issues: list[dict[str, str]] | None = None,
) -> dict[str, Any]:
    system, user = _concept_slot_prompts(
        context,
        slot_index,
        variation_focus,
        phase,
        invalid_result,
        validation_issues,
    )
    started = perf_counter()
    try:
        async with semaphore:
            raw_result = await execute_structured_prompt(system, user)
    except ProviderFailure as failure:
        duration_ms = round((perf_counter() - started) * 1000)
        logger.warning(
            "Concept generation slot failed taskType=CONCEPT_GENERATION slotIndex=%s variationFocus=%s phase=%s validationPath=%s validationType=%s durationMs=%s",
            slot_index,
            variation_focus,
            phase,
            [""],
            [failure.code],
            duration_ms,
        )
        return {
            "slotIndex": slot_index,
            "variationFocus": variation_focus,
            "rawResult": None,
            "issues": [{"path": "", "type": failure.code}],
            "concept": None,
            "providerFailure": failure,
        }

    duration_ms = round((perf_counter() - started) * 1000)
    slot_context = {
        "slotIndex": slot_index,
        "requiredOriginTrace": context["requiredOriginTrace"],
    }
    try:
        validated = SingleConceptGenerationResult.model_validate(
            raw_result, context=slot_context
        )
        logger.info(
            "Concept generation slot valid taskType=CONCEPT_GENERATION slotIndex=%s variationFocus=%s phase=%s validationPath=[] validationType=[] durationMs=%s",
            slot_index,
            variation_focus,
            phase,
            duration_ms,
        )
        return {
            "slotIndex": slot_index,
            "variationFocus": variation_focus,
            "rawResult": raw_result,
            "issues": [],
            "concept": validated.concept,
            "providerFailure": None,
        }
    except ValidationError as failure:
        issues = _single_concept_issues(failure)
        logger.warning(
            "Concept generation slot invalid taskType=CONCEPT_GENERATION slotIndex=%s variationFocus=%s phase=%s validationPath=%s validationType=%s durationMs=%s",
            slot_index,
            variation_focus,
            phase,
            [issue["path"] for issue in issues],
            [issue["type"] for issue in issues],
            duration_ms,
        )
        return {
            "slotIndex": slot_index,
            "variationFocus": variation_focus,
            "rawResult": raw_result,
            "issues": issues,
            "concept": None,
            "providerFailure": None,
        }


async def _execute_concept_generation(text: str) -> dict[str, Any]:
    context = _concept_generation_context(text)
    desired_count = context["desiredCount"]
    concurrency = _concept_generation_concurrency()
    semaphore = asyncio.Semaphore(concurrency)
    started = perf_counter()
    slots = [
        (index, CONCEPT_VARIATION_FOCUSES[index % len(CONCEPT_VARIATION_FOCUSES)])
        for index in range(desired_count)
    ]
    initial_results = await asyncio.gather(
        *(
            _call_concept_slot(context, index, focus, "initial", semaphore)
            for index, focus in slots
        )
    )

    fatal_provider_failures = [
        result["providerFailure"]
        for result in initial_results
        if result["providerFailure"] is not None
        and result["providerFailure"].code != "RESULT_SCHEMA_INVALID"
    ]
    invalid_results = [
        result for result in initial_results if result["concept"] is None
    ]
    repair_results: list[dict[str, Any]] = []
    if invalid_results:
        repair_results = await asyncio.gather(
            *(
                _call_concept_slot(
                    context,
                    result["slotIndex"],
                    result["variationFocus"],
                    "repair",
                    semaphore,
                    result["rawResult"],
                    result["issues"],
                )
                for result in invalid_results
            )
        )

    fatal_provider_failures.extend(
        result["providerFailure"]
        for result in repair_results
        if result["providerFailure"] is not None
        and result["providerFailure"].code != "RESULT_SCHEMA_INVALID"
    )

    results_by_slot = {result["slotIndex"]: result for result in initial_results}
    for result in repair_results:
        results_by_slot[result["slotIndex"]] = result
    valid_slot_count = sum(
        result["concept"] is not None for result in results_by_slot.values()
    )
    failed_slot_indices = sorted(
        index
        for index, result in results_by_slot.items()
        if result["concept"] is None
    )
    total_duration_ms = round((perf_counter() - started) * 1000)
    log_method = logger.info if not failed_slot_indices else logger.warning
    log_method(
        "Concept generation fan-out taskType=CONCEPT_GENERATION desiredCount=%s concurrency=%s initialCallCount=%s repairCallCount=%s validSlotCount=%s failedSlotIndices=%s totalDurationMs=%s",
        desired_count,
        concurrency,
        len(initial_results),
        len(repair_results),
        valid_slot_count,
        failed_slot_indices,
        total_duration_ms,
    )

    if fatal_provider_failures:
        raise fatal_provider_failures[0]
    if failed_slot_indices:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False
        )

    aggregate = {
        "concepts": [
            results_by_slot[index]["concept"].model_dump(by_alias=True)
            for index in range(desired_count)
        ]
    }
    try:
        return ConceptGenerationResult.model_validate(
            aggregate, context=context
        ).model_dump(by_alias=True, exclude_unset=True)
    except ValidationError as failure:
        logger.warning(
            "Concept generation aggregate invalid taskType=CONCEPT_GENERATION desiredCount=%s validSlotCount=%s failedSlotIndices=%s validationType=%s totalDurationMs=%s",
            desired_count,
            valid_slot_count,
            list(range(desired_count)),
            [issue["type"] for issue in _single_concept_issues(failure)],
            round((perf_counter() - started) * 1000),
        )
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False
        ) from failure


async def execute_structured_prompt(
    system: str,
    user: str,
    model_override: str | None = None,
    response_schema: dict[str, Any] | None = None,
    schema_name: str | None = None,
    task_type: str | None = None,
) -> dict[str, Any]:
    api_key, model, base_url = _configuration(model_override)
    try:
        timeout_seconds = float(os.getenv("AI_PROVIDER_TIMEOUT_SECONDS", "60"))
        if timeout_seconds <= 0:
            raise ValueError
    except ValueError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    response_format = {"type": "json_object"}
    if response_schema is not None:
        response_format = {
            "type": "json_schema",
            "json_schema": {
                "name": schema_name or "structured_result",
                "strict": True,
                "schema": response_schema,
            },
        }
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
        "temperature": 0.1,
        "response_format": response_format,
    }
    try:
        async with httpx.AsyncClient(timeout=timeout_seconds) as client:
            response = await client.post(
                f"{base_url}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
                json=body,
            )
    except (httpx.TimeoutException, httpx.NetworkError) as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True) from failure
    if response.status_code in (401, 403):
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    if response.status_code == 429:
        raise ProviderFailure("RATE_LIMITED", "DEPENDENCY_RATE_LIMITED", 429, True)
    if response.status_code >= 500:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True)
    if response.status_code == 400 and response_schema is not None:
        provider_error_type, provider_error_param = _safe_provider_error(response)
        if provider_error_type == "invalid_request_error" and provider_error_param == "response_format":
            safe_task_type = task_type if task_type == "IDEA_CONVERSATION_TURN" else "STRUCTURED_TASK"
            safe_schema_name = schema_name if schema_name in {
                "opportunity_brief_draft_v1", "opportunity_brief_draft_repair_v1"
            } else "structured_result"
            logger.warning(
                "Provider response schema rejected taskType=%s model=%s upstreamStatus=400 providerErrorType=invalid_request_error providerErrorParam=response_format schemaName=%s",
                safe_task_type,
                model,
                safe_schema_name,
            )
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID",
                "PROVIDER_RESPONSE_SCHEMA_REJECTED",
                502,
                False,
                upstream_status=400,
                provider_error_type="invalid_request_error",
                provider_error_param="response_format",
                schema_name=safe_schema_name,
            )
    if response.status_code >= 400:
        raise ProviderFailure("EXECUTION_FAILED", "PERMANENT_EXECUTION_FAILURE", 500, False)
    if len(response.content) > 2 * 1024 * 1024:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False)
    try:
        payload = response.json()
        content = payload["choices"][0]["message"]["content"]
        if isinstance(content, list):
            content = "".join(part.get("text", "") for part in content if isinstance(part, dict))
        return _extract_json(content)
    except (KeyError, IndexError, TypeError, AttributeError, ValueError, json.JSONDecodeError) as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure


def _safe_provider_error(response) -> tuple[str | None, str | None]:
    try:
        payload = response.json()
        error = payload.get("error") if isinstance(payload, dict) else None
        if not isinstance(error, dict):
            return None, None
        error_type = error.get("type")
        error_param = error.get("param")
        return (
            error_type if error_type in {"invalid_request_error"} else None,
            error_param if error_param in {"response_format"} else None,
        )
    except (TypeError, ValueError, AttributeError):
        return None, None
