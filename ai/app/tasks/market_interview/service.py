import re

from pydantic import ValidationError

from app.providers import ProviderFailure
from app.tasks.market_interview.deep_engine import execute_deep_interview
from app.tasks.market_interview.models import MarketInterviewInput
from app.tasks.market_interview.provider import execute_market_interview_prompt


# Shared semantic policy with Java MarketInterviewContract. A literal percentage is legal in an
# individual's price/discount/fee answer; only population/generalization structures are blocked.
STATISTICAL_CLAIM = re.compile(
    r"(?i)((?:응답자|참여자|고객|소비자)(?:들|들\s*중|의)?\s*\d+(?:\.\d+)?\s*%"
    r"|\d+(?:\.\d+)?\s*%\s*의\s*(?:응답자|참여자|고객|소비자)"
    r"|(?:응답자|참여자|고객|소비자)\s*중\s*\d+(?:\.\d+)?\s*%"
    r"|\d+\s*명\s*중\s*\d+\s*명\s*\(\s*\d+(?:\.\d+)?\s*%\s*\)"
    r"|대부분의\s*(?:시장|고객|소비자|응답자|참여자)"
    r"|전국\s*(?:소비자|고객|사용자)"
    r"|실제\s*(?:사용자|고객|소비자)(?:들)?(?:은|는|이|가)"
    r"|구매\s*확률(?:은|는|이|가)?\s*\d+(?:\.\d+)?\s*%"
    r"|구매\s*전환율(?:은|는|이|가)?\s*\d*(?:\.\d+)?\s*%)"
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
    result = await execute_deep_interview(value, execute_market_interview_prompt)
    _validate_boundaries(result)
    return result
