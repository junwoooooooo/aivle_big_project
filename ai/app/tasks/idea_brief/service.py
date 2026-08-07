import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.idea_brief.mapper import to_domain
from app.tasks.idea_brief.models import IdeaBriefDerivationInput, IdeaBriefProviderResult


SYSTEM_PROMPT = """You structure a business idea into an Idea Brief. Return only the strict schema.
Never mark a field USER_CONFIRMED or LOCKED. Ask two to four concise questions when facts are missing.
Do not claim legal approval. Keep all user-facing text in Korean."""

FINAL_SYNTHESIS_PROMPT = """You structure a business idea into its final Idea Brief assessment. Return only the strict schema.
Do not generate any new questions; clarificationQuestions must be an empty array. Recompute field proposals,
contradictions, missing fields, readiness, and userFacingSummary from the latest overview and canonical fields.
더 이상 새로운 질문을 생성하지 말고 현재 확정/입력된 사실을 기준으로 최종 Brief assessment만 수행한다.
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
    if validated_input.mode == "FINAL_SYNTHESIS" and provider.clarificationQuestions:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "FINAL_SYNTHESIS_QUESTIONS_FORBIDDEN", 502, False)
    return to_domain(provider).model_dump(mode="json")
