import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_candidate.models import ConceptCandidateInput, ConceptCandidateResult


SYSTEM_PROMPT = """Create one differentiated business concept for the supplied variation focus.
Return only the strict schema. Never create evidence IDs, legal text, a final legal status,
user confirmation state, origin trace, legal trace, or source trace. Use Korean user-facing text."""


async def execute_concept_candidate(task_input: dict) -> dict:
    try:
        value = ConceptCandidateInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(value.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=ConceptCandidateResult.model_json_schema(),
        schema_name="concept_candidate_v1", task_type="CONCEPT_CANDIDATE",
    )
    try:
        return ConceptCandidateResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
