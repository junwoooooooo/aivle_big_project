import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_distinctness_judge.models import (
    ConceptDistinctnessJudgeInput,
    ConceptDistinctnessJudgeResult,
)


SYSTEM_PROMPT = """두 Concept의 이름이나 문체가 아니라 실제 사업 구조가 같은지 판정한다.
BusinessFingerprint v1의 21개 축 전체를 비교한다. 대상 사용자, 문제 상황, 핵심 가치, 해결 mechanism,
기능·행위자 역할, 수익·가격·결제 흐름, 채널, 플랫폼·운영·파트너 역할, 거래 흐름,
제공자/판매자/중개자 역할, 개인정보·물리활동·파트너·자격 요건을 함께 본다. 월 정액 멤버십과 매달 비용을 내는 구독 회원제,
개인 참가자 즉석 팀 연결과 당일 경기 인원 자동 배정처럼 표현만 다른 동일 구조는 DUPLICATE다.
같은 사용자라도 해결 mechanism, 운영모델 또는 수익모델이 실질적으로 다르면 DISTINCT다.
strict schema의 안전한 요약만 반환하고 chain-of-thought이나 내부 reasoning을 반환하지 않는다."""


SYSTEM_PROMPT += """
같은 문제, 같은 대상 사용자, 같은 Idea Brief 및 LOCKED 원본 조건은 그 자체로 DUPLICATE 증거가 아니다.
실제 해결 메커니즘, 기능 조합, 운영·파트너 역할, 거래·결제 흐름, 수익·가격 구조와 채널을 비교한다.
두 개 이상의 핵심 작동 축이 실질적으로 다르면 DISTINCT로 판정한다.
"""


async def execute_concept_distinctness_judge(task_input: dict) -> dict:
    try:
        value = ConceptDistinctnessJudgeInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT,
        json.dumps(value.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=ConceptDistinctnessJudgeResult.model_json_schema(),
        schema_name="concept_distinctness_judge_v1",
        task_type="CONCEPT_DISTINCTNESS_JUDGE",
    )
    try:
        return ConceptDistinctnessJudgeResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        fields = [{
            "path": "result." + ".".join(str(part) for part in issue.get("loc", ())),
            "category": str(issue.get("type", "invalid"))[:80],
            "expectedType": "valid contract value",
        } for issue in failure.errors()[:12]]
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "PYDANTIC_RESULT_VALIDATION_FAILED", 502, False,
            schema_name="concept_distinctness_judge_v1", validation_fields=fields,
        ) from failure
