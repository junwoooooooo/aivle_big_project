import json
from pydantic import ValidationError
from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.finance_estimate.models import FinanceEstimateInput, FinanceEstimateResult
from app.tasks.finance_estimate.tavily import search_finance_benchmarks

MARKET_BM_FINANCE_PROMPT = """You prepare one financial-input recommendation for a Korean business-planning service.
Use the supplied contextJson. It includes TechOps facts, market-analysis evidence and assumptions, and the
business-model result. Tavily evidence, when present, is external search context rather than verified truth:
use it only as a benchmark, name it as an assumption, and never present it as an observed project fact.

Return the strict schema only. Write explanation and every assumptions item in concise, natural Korean.
Explain the calculation basis so a founder can review it. If the context labels an input as an assumption,
explicitly call it 가정. Give a non-negative KRW value for cost fields. For monthlyChurnRate, return
proposedValue as {\"percent\": number between 0 and 100}. Never propose CAC itself. For proposalVersion >= 2,
offer a materially different alternative from rejectedProposalJson. source must be AI_ESTIMATE."""

ECONOMIC_SANITY_RULES = """
Financial economic-sanity rules:
- unitVariableCost, paymentFee, partnerPayout, shippingCost, and customerIncrementalInfraCost are per sale
  or per average monthly subscriber costs, never annual totals or one-off contract totals.
- Do not default variable costs to zero unless the BM context explicitly makes the item not applicable.
- Find the market or subscription price P in contextJson and use it as the anchor.
- For a digital subscription, unitVariableCost normally stays within 1%~45% of P; paymentFee 1%~5%;
  customerIncrementalInfraCost 1%~20%; partnerPayout at most 30% unless evidence supports an exception.
- shippingCost is 0 only for a purely digital service with no physical fulfilment.
- Use Tavily snippets only as external benchmark context, never as a verified project observation.
"""

async def execute_finance_estimate(task_input:dict)->dict:
    try: value=FinanceEstimateInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST","FIELD_CONSTRAINT_VIOLATION",400,False) from failure
    tavily_evidence = await search_finance_benchmarks(value.fieldKey)
    prompt_input = value.model_dump(mode="json")
    prompt_input["tavilyEvidence"] = tavily_evidence
    raw=await execute_structured_prompt(MARKET_BM_FINANCE_PROMPT + ECONOMIC_SANITY_RULES,
        json.dumps(prompt_input,ensure_ascii=False,sort_keys=True),
        response_schema=FinanceEstimateResult.model_json_schema(),schema_name="finance_estimate_v1",task_type="FINANCE_ESTIMATE")
    try: return FinanceEstimateResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID","AI_RESULT_INVALID",502,False) from failure
