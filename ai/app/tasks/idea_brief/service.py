import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.idea_brief.mapper import to_domain
from app.tasks.idea_brief.models import IdeaBriefDerivationInput, IdeaBriefProviderResult


SYSTEM_PROMPT = """You structure a business idea into an Idea Brief. Return only the strict schema.
Use fieldMetadata as the source of truth for required and regulatory-sensitive fields. When clarification is needed,
prioritize missing required regulatory-sensitive fields, then other missing required fields, then unresolved blocking
contradictions, and only then optional OPEN or ASSUMPTION details. Never spend a clarification question on an optional
field while a required field remains missing. Never mark a field USER_CONFIRMED or LOCKED.
Do not claim legal approval. Keep all user-facing text in Korean."""

FINAL_SYNTHESIS_PROMPT = """You structure a business idea into its final Idea Brief assessment. Return only the strict schema.
Do not generate any new questions; clarificationQuestions must be an empty array. Recompute field proposals,
contradictions, missing fields, readiness, and userFacingSummary from the latest overview and canonical fields.
더 이상 새로운 질문을 생성하지 말고 현재 확정/입력된 사실을 기준으로 최종 Brief assessment만 수행한다.
Use fieldMetadata as the source of truth. Fill a required field with an AI_PROPOSED fieldSuggestion only when it can
be reasonably inferred from current facts. Do not present an inference as confirmed. Keep uncertain required fields
in readiness.missingFieldKeys. READY_FOR_REVIEW is forbidden while any required field remains missing.
Never mark a field USER_CONFIRMED or LOCKED. Do not claim legal approval. Keep all user-facing text in Korean."""


async def execute_idea_brief_derivation(task_input: dict) -> dict:
    try:
        validated_input = IdeaBriefDerivationInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    prompt = FINAL_SYNTHESIS_PROMPT if validated_input.mode == "FINAL_SYNTHESIS" else SYSTEM_PROMPT
    raw = await execute_structured_prompt(
        prompt,
        json.dumps(validated_input.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=IdeaBriefProviderResult.model_json_schema(),
        schema_name="idea_brief_derivation_v1",
        task_type="IDEA_BRIEF_DERIVATION",
    )
    try:
        provider = IdeaBriefProviderResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    metadata = {value.fieldKey: value for value in validated_input.fieldMetadata}
    completed = {
        value.fieldKey for value in validated_input.fields if value.value.strip()
    } | {
        value.fieldKey for value in provider.extractedFields
    } | {
        value.fieldKey for value in provider.fieldSuggestions
    }
    required_missing = [
        value.fieldKey for value in validated_input.fieldMetadata
        if value.requiredForConcept and value.fieldKey not in completed
    ]
    provider = provider.model_copy(update={
        "readiness": provider.readiness.model_copy(update={
            "status": "NEEDS_INPUT" if required_missing else provider.readiness.status,
            "missingFieldKeys": required_missing,
        })
    })
    if validated_input.mode == "FINAL_SYNTHESIS" and provider.clarificationQuestions:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "FINAL_SYNTHESIS_QUESTIONS_FORBIDDEN", 502, False)
    if validated_input.mode == "CLARIFICATION":
        if required_missing:
            questions = [
                value for value in provider.clarificationQuestions
                if value.targetFieldKey in required_missing
            ]
            questions.sort(key=lambda value: (
                not metadata[value.targetFieldKey].regulatorySensitive,
                provider.readiness.missingFieldKeys.index(value.targetFieldKey),
            ))
            provider = provider.model_copy(update={"clarificationQuestions": questions})
    return to_domain(provider).model_dump(mode="json")
