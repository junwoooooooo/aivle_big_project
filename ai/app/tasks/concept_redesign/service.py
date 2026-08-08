import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_redesign.models import ConceptRedesignInput, ConceptRedesignResult


SYSTEM_PROMPT = """공급된 안전 구현 조건과 명시된 designGaps를 모두 해결하도록 ConceptCandidateV2의 운영 구조를 재설계한다.
LegalFactPattern에 드러난 행위자, 판매·제공·중개 역할, 거래·결제·개인정보 흐름을 모호하지 않게 보완한다.
한국어 사용자 문구와 strict schema만 반환한다. 원 후보의 USER_INPUT + LOCKED 값과 source,
authority, decision 의미를 변경하지 않는다. pre-market SOM은 AI 가설 상태로 유지한다.
증거 ID, 법령 문구, 최종 법률 상태, 사용자 확인 상태는 만들지 않는다."""


async def execute_concept_redesign(task_input: dict) -> dict:
    try:
        value = ConceptRedesignInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(value.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=ConceptRedesignResult.model_json_schema(),
        schema_name="concept_redesign_v2", task_type="CONCEPT_REDESIGN",
    )
    try:
        return ConceptRedesignResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
