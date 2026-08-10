import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.tech_ops_proposal.models import TechOpsProposalInput, TechOpsProposalResult


SYSTEM_PROMPT = """확정된 Concept와 Legal 통제를 바탕으로 기술·운영 분석 전 사용자 검토용 제안을 만든다.
서비스 제공 방식, 월 처리량/판매량, 기술·공급·운영 제약을 모두 실제 값으로 제안하며 null placeholder를
반환하지 않는다. proposalVersion이 2 이상이고 rejectedProposalJson이 있으면 직전 거절값과 의미상 다른
대안을 만든다. 제안은 사용자 사실이나 Evidence가 아니며 strict schema의 안전한 설명만 반환한다."""


async def execute_tech_ops_proposal(task_input: dict) -> dict:
    try:
        value = TechOpsProposalInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    raw = await execute_structured_prompt(SYSTEM_PROMPT,
        json.dumps(value.model_dump(mode="json"), ensure_ascii=False, sort_keys=True),
        response_schema=TechOpsProposalResult.model_json_schema(), schema_name="tech_ops_proposal_v1",
        task_type="TECH_OPS_PROPOSAL")
    try:
        return TechOpsProposalResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
