import re

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.market_interview.deep_engine import execute_deep_interview
from app.tasks.market_interview.models import MarketInterviewInput


STATISTICAL_CLAIM = re.compile(
    r"(?i)(\d+(?:\.\d+)?\s*%|퍼센트|구매\s*전환율|전국\s*소비자|대부분의\s*(?:시장|고객|소비자)|실제\s*(?:사용자|고객)(?:들)?은)"
)


def _strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for child in value.values():
            yield from _strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from _strings(child)


def _validate_boundaries(result: dict) -> None:
    if any(STATISTICAL_CLAIM.search(text) for text in _strings(result)):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False)
    limitations = " ".join(result.get("limitations") or [])
    if "실제 고객" not in limitations or "대표성" not in limitations or "확인" not in limitations:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False)


async def execute_market_interview(task_input: dict) -> dict:
    try:
        value = MarketInterviewInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    result = await execute_deep_interview(value, execute_structured_prompt)
    _validate_boundaries(result)
    return result
