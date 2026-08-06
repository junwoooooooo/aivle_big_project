import json

from pydantic import ValidationError

from app.services.journey_provider import ProviderFailure, execute_structured_prompt
from app.tasks.idea_brief.mapper import to_domain
from app.tasks.idea_brief.models import IdeaBriefDerivationInput, IdeaBriefProviderResult


SYSTEM_PROMPT = """You structure a business idea into an Idea Brief. Return only the strict schema.
Never mark a field USER_CONFIRMED or LOCKED. Ask two to four concise questions when facts are missing.
Do not claim legal approval. Keep all user-facing text in Korean."""


async def execute_idea_brief_derivation(task_input: dict) -> dict:
    try:
        validated_input = IdeaBriefDerivationInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT,
        json.dumps(validated_input.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=IdeaBriefProviderResult.model_json_schema(),
        schema_name="idea_brief_derivation_v1",
        task_type="IDEA_BRIEF_DERIVATION",
    )
    try:
        provider = IdeaBriefProviderResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    return to_domain(provider).model_dump(mode="json")
