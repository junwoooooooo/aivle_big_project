import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_hypothesis_alternative.models import (
    ConceptHypothesisAlternativeInput, ConceptHypothesisAlternativeResult,
)


SYSTEM_PROMPT = """선택된 Concept의 거절된 시장 가설을 대체할 실질적으로 다른 가설 하나를 제안한다.
Concept의 LOCKED 값과 사업 정체성을 변경하지 않는다. 같은 표현을 바꿔 반복하지 않는다.
pre-market SOM은 분석 결과가 아닌 사전 가설로 유지한다. 한국어 값과 strict schema만 반환한다.
TARGET_REGION 대안은 현재 공식 법률검토 지원 범위인 대한민국 안에서만 제안한다.
source는 AI_HYPOTHESIS, decisionStatus는 ALTERNATIVE_PROPOSED로 고정한다."""


async def execute_concept_hypothesis_alternative(task_input: dict) -> dict:
    try:
        value = ConceptHypothesisAlternativeInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(value.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=ConceptHypothesisAlternativeResult.model_json_schema(),
        schema_name="concept_hypothesis_alternative_v1", task_type="CONCEPT_HYPOTHESIS_ALTERNATIVE",
    )
    try:
        result = ConceptHypothesisAlternativeResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    if result.hypothesisType != value.hypothesisType or result.proposalVersion != value.proposalVersion:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False)
    if result.hypothesisType == "TARGET_REGION" and not _kr_compatible(result.proposedValue):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_JURISDICTION_UNSUPPORTED", 422, False)
    return result.model_dump(mode="json")


def _kr_compatible(value: str) -> bool:
    normalized = " ".join(value.casefold().split())
    if any(marker in normalized for marker in (
            "미국", "일본", "중국", "유럽", "해외", "global", "usa", "united states", "japan", "china")):
        return False
    return normalized in {"kr", "kor"} or any(marker in normalized for marker in (
        "대한민국", "한국", "국내", "전국", "서울", "부산", "인천", "대구", "대전", "광주",
        "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
        "republic of korea", "south korea"))
