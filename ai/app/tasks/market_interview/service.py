import json
import re

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.market_interview.models import MarketInterviewInput, MarketInterviewResult


SYSTEM_PROMPT = """당신은 실제 고객을 조사하는 조사원이 아니라, 현재 확정된 사업안을 여러 가상의 고객 관점에서 점검하는 정성 탐색 도구다.
서로 다른 맥락과 우려를 가진 가상 참여자 4명을 만들고, 비선도 질문으로 문제 상황·첫 반응·사용 맥락·망설임·구매 또는 사용 계기·미충족 요구를 탐색한다.
모든 참여자는 P1~P4와 '가상 참여자 A~D'처럼 명백한 가상 프로필이어야 하며 실제 인물 이름·연락처·민감 특성을 만들지 않는다.
답변은 simulated perspective일 뿐 실제 고객 발언이나 시장 근거가 아니다. 입력의 시장 검증 맥락을 참여자의 실제 경험처럼 바꾸어 말하지 않는다.
백분율, 구매율, 대표성, 모집단, '대부분의 고객', '실제 사용자들은' 같은 통계·사실 주장을 만들지 않는다. 참여자 수를 비율로 환산하지 않는다.
themes는 반복된 정성 관점만 설명하고 빈도나 시장 일반화를 쓰지 않는다. followUpQuestions는 이후 실제 고객에게 중립적으로 확인할 질문이다.
limitations에는 반드시 실제 고객 조사 아님, 통계적 대표성 없음, 실제 인터뷰로 확인 필요를 포함한다. strict schema만 반환한다."""

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
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT,
        json.dumps(value.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=MarketInterviewResult.model_json_schema(),
        schema_name="market_interview_result_v1",
        task_type="MARKET_INTERVIEW",
    )
    try:
        result = MarketInterviewResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    _validate_boundaries(result)
    return result
