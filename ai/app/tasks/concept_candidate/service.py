import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_candidate.models import ConceptCandidateInput, ConceptCandidateResult


SYSTEM_PROMPT = """입력된 Market Seed로 서로 구별되는 하나의 ConceptCandidateV2를 설계한다.
반드시 한국어 사용자 문구와 strict schema만 반환한다. USER_INPUT 또는 USER_CONFIRMED + LOCKED 값은
source까지 포함하여 의미와 구체적 조건을 그대로 보존하며 임의로 일반화하거나 변경하지 않는다. 비어 있던 targetRegion은 현재 공식
법률검토 지원 범위인 대한민국으로 제안하고, revenueModel, price, channels, differentiators와 함께
AI_HYPOTHESIS + OPEN + PROPOSED로 표시한다. pre-market SOM 두 값은
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
        result = ConceptCandidateResult.model_validate(raw)
        by_key = {item.fieldKey: item for item in result.valueSemantics}
        region_seed = next((item for item in value.fields if item.fieldKey == "targetRegion"), None)
        region_semantics = by_key["targetRegion"]
        if region_seed is not None and region_seed.authority == "LOCKED":
            if (region_seed.source not in ("USER_INPUT", "USER_CONFIRMED")
                    or result.targetRegion.strip().casefold() != region_seed.value.strip().casefold()
                    or region_semantics.source != region_seed.source
                    or region_semantics.authority != "LOCKED"
                    or region_semantics.decision != "ACCEPTED"):
                raise ValueError("locked targetRegion semantics must be preserved")
        elif (region_semantics.source != "AI_HYPOTHESIS"
                or region_semantics.authority != "OPEN"
                or region_semantics.decision != "PROPOSED"
                or not _kr_compatible(result.targetRegion)):
            raise ValueError("open targetRegion must be a KR-compatible AI hypothesis")
        return result.model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    except ValueError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure


def _kr_compatible(value: str) -> bool:
    normalized = " ".join(value.casefold().split())
    foreign = ("미국", "일본", "중국", "캐나다", "호주", "영국", "유럽", "해외", "global",
               "usa", "united states", "japan", "china")
    if any(marker in normalized for marker in foreign):
        return False
    return normalized in {"kr", "kor"} or any(marker in normalized for marker in (
        "대한민국", "한국", "국내", "전국", "서울", "부산", "인천", "대구", "대전", "광주",
        "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
        "republic of korea", "south korea"))
