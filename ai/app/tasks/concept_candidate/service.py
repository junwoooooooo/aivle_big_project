import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_candidate.models import ConceptCandidateInput, ConceptCandidateResult


SYSTEM_PROMPT = """입력된 Market Seed로 서로 구별되는 하나의 ConceptCandidateV2를 설계한다.
반드시 한국어 사용자 문구와 strict schema만 반환한다. USER_INPUT + LOCKED 값은 의미와 구체적
조건을 그대로 보존하며 임의로 일반화하거나 변경하지 않는다. 비어 있던 revenueModel, price,
channels, differentiators는 AI_HYPOTHESIS + OPEN + PROPOSED로 제안한다. pre-market SOM 두 값은
실제 시장분석 결과가 아닌 AI_HYPOTHESIS + OPEN + PROPOSED로 명시한다. AS_IS의 Candidate 1은
사용자 원안을 새로운 아이디어로 재해석하지 않고 구조화만 한다. 이름이나 표현만 바꾼 기존
후보를 만들지 않는다. providerRole, sellerRole, intermediaryRole은 각 참여자의 실제 거래상 역할을
명시하고 해당 역할이 없으면 그 이유를 포함해 '해당 없음'으로 분명히 쓴다. 증거 ID, 법령 문구,
최종 법률 상태, 사용자 확인 상태는 만들지 않는다. acceptedConceptFingerprints에 이미 채택된 후보가
있으면 이름이 아니라 solutionMechanism, operatingModel, revenueModel, transactionFlow와 역할 중 실제
사업 축을 달리 설계한다."""


async def execute_concept_candidate(task_input: dict) -> dict:
    try:
        value = ConceptCandidateInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(value.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=ConceptCandidateResult.model_json_schema(),
        schema_name="concept_candidate_v2", task_type="CONCEPT_CANDIDATE",
    )
    try:
        return ConceptCandidateResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
