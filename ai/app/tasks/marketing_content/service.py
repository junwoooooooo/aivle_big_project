import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.marketing_content.models import MarketingContentInput, MarketingContentResult
from app.tasks.marketing_content.prompts import SYSTEM_PROMPT


async def execute_marketing_content(task_input: dict) -> dict:
    try:
        value = MarketingContentInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT,
        json.dumps(value.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=MarketingContentResult.model_json_schema(),
        schema_name="marketing_content_result_v1",
        task_type="MARKETING_CONTENT_GENERATION",
    )
    try:
        result = MarketingContentResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    if result.contentType != value.request.contentType:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False)
    return result.model_dump(mode="json")
