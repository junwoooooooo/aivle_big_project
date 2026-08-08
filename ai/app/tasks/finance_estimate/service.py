import json
from pydantic import ValidationError
from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.finance_estimate.models import FinanceEstimateInput, FinanceEstimateResult

SYSTEM_PROMPT="""TechOps 확정 사실을 바탕으로 사용자가 검토할 재무 추정 제안을 만든다. 이는 사용자 사실이
아니며 source=AI_ESTIMATE다. proposedValue와 함께 assumptions, explanation, confidence를 반환한다.
가능하면 가정과 범위를 설명한다. CAC 자체는 제안하지 않는다. proposalVersion 2 이상이면 직전 거절값과
다른 대안을 제안한다. strict schema만 반환하고 근거 없는 확정 사실처럼 표현하지 않는다."""

async def execute_finance_estimate(task_input:dict)->dict:
    try: value=FinanceEstimateInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST","FIELD_CONSTRAINT_VIOLATION",400,False) from failure
    raw=await execute_structured_prompt(SYSTEM_PROMPT,json.dumps(value.model_dump(mode="json"),ensure_ascii=False,sort_keys=True),
        response_schema=FinanceEstimateResult.model_json_schema(),schema_name="finance_estimate_v1",task_type="FINANCE_ESTIMATE")
    try: return FinanceEstimateResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID","AI_RESULT_INVALID",502,False) from failure
