import json
from pydantic import ValidationError
from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.finance_estimate.models import FinanceEstimateInput, FinanceEstimateResult
from app.tasks.finance_estimate.tavily import search_finance_benchmarks

SYSTEM_PROMPT="""TechOps 확정 사실을 바탕으로 사용자가 검토할 재무 추정 제안을 만든다. 이는 사용자 사실이
아니며 source=AI_ESTIMATE다. proposedValue와 함께 assumptions, explanation, confidence를 반환한다.
가능하면 가정과 범위를 설명한다. CAC 자체는 제안하지 않는다. proposalVersion 2 이상이면 직전 거절값과
다른 대안을 제안한다. strict schema만 반환하고 근거 없는 확정 사실처럼 표현하지 않는다."""

MARKET_BM_FINANCE_PROMPT = """You prepare one financial-input recommendation for a Korean business-planning service.
Use the supplied contextJson. It includes market-analysis evidence and assumptions (TAM, SAM, growth,
price hypothesis) and the business-model result. Use those upstream results when relevant to fieldKey.
Tavily evidence, when present, is external search context rather than verified truth: use it only as a
benchmark, name it as an assumption, and never present it as an observed project fact.

Return the strict schema only. Write explanation and every assumptions item in concise, natural Korean.
Explain the calculation basis so a founder can review it. If the context labels a market price, size,
growth rate, or BM input as an assumption, explicitly call it \"가정\". Give a non-negative KRW value
for cost fields. For monthlyChurnRate, return proposedValue as {"percent": number between 0 and 100}.
Never propose CAC itself. For proposalVersion >= 2, offer a materially different
alternative from rejectedProposalJson when available. source must be AI_ESTIMATE."""

ECONOMIC_SANITY_RULES = """
Financial economic-sanity rules:
- Determine the correct unit before proposing a cost. unitVariableCost, paymentFee, partnerPayout,
  shippingCost, and customerIncrementalInfraCost are per sale or per average monthly subscriber costs,
  never annual totals or one-off contract totals.
- Do not default these variable costs to zero. Zero is allowed only if the BM context explicitly makes
  the item not applicable (for example, no physical delivery). In that case, say \"해당 없음 가정\"
  in assumptions and explain why. Otherwise propose a conservative positive benchmark range value.
- For a digital subscription, include plausible per-subscriber usage, API, payment, or support costs in
  unitVariableCost/customerIncrementalInfraCost rather than treating all delivery cost as zero.
- For paymentFee, use a per-transaction monetary equivalent based on the market/BM price context.
- Never treat a monthly, annual, or initial amount as a per-unit amount. State the unit in Korean in
  the explanation, such as \"건당\" or \"구독자당 월\".

Price-anchor guardrails:
- Find the market price hypothesis or subscription price P in contextJson and use it as the anchor.
- For a digital subscription, unitVariableCost must normally be 1%~45% of P; paymentFee normally 1%~5%
  of P; customerIncrementalInfraCost normally 1%~20% of P. The combined per-subscriber variable cost
  must not exceed 70% of P without an explicit, evidence-backed exceptional reason.
- partnerPayout is 0 only when BM has no revenue-sharing partner. When a partner exists, it is a
  per-transaction/per-subscriber amount and normally must not exceed 30% of P. Never output a monthly,
  annual, or initial partner budget here.
- shippingCost is 0 for a purely digital service with no physical fulfilment. It may be positive only
  when BM explicitly includes a shipped physical product, and must then be per delivery.
- A proposed per-unit cost greater than the market price is economically implausible. Do not output it.
- Use Tavily snippets only when they actually support the benchmark. In assumptions state whether the
  basis is market/BM data, a Tavily benchmark, or a conservative assumption; never claim an unverified
  Tavily search result is a market observation.
"""

async def execute_finance_estimate(task_input:dict)->dict:
    try: value=FinanceEstimateInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST","FIELD_CONSTRAINT_VIOLATION",400,False) from failure
    tavily_evidence = await search_finance_benchmarks(value.fieldKey)
    prompt_input = value.model_dump(mode="json")
    prompt_input["tavilyEvidence"] = tavily_evidence
    raw=await execute_structured_prompt(MARKET_BM_FINANCE_PROMPT + ECONOMIC_SANITY_RULES,json.dumps(prompt_input,ensure_ascii=False,sort_keys=True),
        response_schema=FinanceEstimateResult.model_json_schema(),schema_name="finance_estimate_v1",task_type="FINANCE_ESTIMATE")
    try: return FinanceEstimateResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID","AI_RESULT_INVALID",502,False) from failure
