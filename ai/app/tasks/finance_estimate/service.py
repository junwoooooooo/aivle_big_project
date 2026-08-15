import json
import math

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.finance_estimate.models import FinanceEstimateInput, FinanceEstimateResult
from app.tasks.finance_estimate.tavily import search_finance_benchmarks


MARKET_BM_FINANCE_PROMPT = """You prepare one financial-input recommendation for a Korean business-planning service.
Use the supplied contextJson. It includes current market-analysis evidence and assumptions and the
business-model result. Tavily evidence, when present, is external benchmark context rather than verified
truth: name it as an assumption and never present it as an observed project fact.

Return the strict schema only. Write explanation and every assumptions item in concise, natural Korean.
Explain the calculation basis and unit so a founder can review it. For monthlyChurnRate, return
proposedValue as {\"percent\": number between 0 and 100}. For newCustomerCount, return
{\"count\": integer}. Never propose CAC itself. For proposalVersion >= 2, offer a materially different
alternative from rejectedProposalJson. source must be AI_ESTIMATE."""

ECONOMIC_SANITY_RULES = """
Financial economic-sanity rules:
- Determine the correct unit before proposing a cost. unitVariableCost, paymentFee, partnerPayout,
  shippingCost, and customerIncrementalInfraCost are per sale or per average monthly subscriber costs,
  never annual totals or one-off contract totals.
- Do not default these variable costs to zero. Zero is allowed only if the BM context explicitly makes
  the item not applicable (for example, no physical delivery). In that case, say "해당 없음 가정"
  in assumptions and explain why. Otherwise propose a conservative positive benchmark range value.
- For a digital subscription, include plausible per-subscriber usage, API, payment, or support costs in
  unitVariableCost/customerIncrementalInfraCost rather than treating all delivery cost as zero.
- For paymentFee, use a per-transaction monetary equivalent based on the market/BM price context.
- Never treat a monthly, annual, or initial amount as a per-unit amount. State the unit in Korean in
  the explanation, such as "건당" or "구독자당 월".

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
- totalMarketingCost and totalSalesCost are annual total KRW budgets, not per-customer or monthly costs.
  If explanation contains a calculation, proposedValue.amount must exactly equal the final KRW result of
  that calculation. Never write 2,376,000 KRW in prose while returning 2,376 KRW.
"""

THREE_YEAR_TARGET_RULES = """
When fieldKey is threeYearTargets, proposedValue MUST be a Targets object, never Money:
{"metric":"salesVolume"|"customerCount"|"subscriberCount"|"transactionCount",
 "unit":"Korean unit", "years":[{"year":1,"value":number},{"year":2,"value":number},{"year":3,"value":number}]}.
Choose subscriberCount for a subscription BM unless the context clearly supports another metric. All values
must be non-negative and must be described as planning assumptions, not observations.
"""


async def execute_finance_estimate(task_input: dict) -> dict:
    try:
        value = FinanceEstimateInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure

    prompt_input = value.model_dump(mode="json")
    prompt_input["tavilyEvidence"] = await search_finance_benchmarks(value.fieldKey)
    raw = await execute_structured_prompt(
        MARKET_BM_FINANCE_PROMPT + ECONOMIC_SANITY_RULES + THREE_YEAR_TARGET_RULES,
        json.dumps(prompt_input, ensure_ascii=False, sort_keys=True),
        response_schema=FinanceEstimateResult.model_json_schema(),
        schema_name="finance_estimate_v1",
        task_type="FINANCE_ESTIMATE",
    )
    try:
        result = FinanceEstimateResult.model_validate(raw)
    except ValidationError as failure:
        if value.fieldKey != "threeYearTargets":
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
        repair = await execute_structured_prompt(
            "Return only a valid finance_estimate_v1 result for threeYearTargets. proposedValue must contain "
            "metric, unit, and exactly years 1, 2, 3; never return Money.",
            json.dumps(prompt_input, ensure_ascii=False, sort_keys=True),
            response_schema=FinanceEstimateResult.model_json_schema(),
            schema_name="finance_estimate_v1_repair",
            task_type="FINANCE_ESTIMATE",
        )
        try:
            result = FinanceEstimateResult.model_validate(repair)
        except ValidationError as repair_failure:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from repair_failure
    return _apply_price_guardrails(result, value).model_dump(mode="json")


def _apply_price_guardrails(result: FinanceEstimateResult,
                            request: FinanceEstimateInput) -> FinanceEstimateResult:
    caps = {
        "unitVariableCost": 0.45,
        "paymentFee": 0.05,
        "partnerPayout": 0.30,
        "customerIncrementalInfraCost": 0.20,
        "shippingCost": 1.00,
    }
    cap_ratio = caps.get(request.fieldKey)
    if cap_ratio is None or not hasattr(result.proposedValue, "amount"):
        return result
    try:
        context = json.loads(request.contextJson)
        fields = context.get("financialFields", {})
        primary = "unitPrice" if fields.get("revenueModel", {}).get("value") == "ONE_TIME" \
            else "monthlySubscriptionPrice"
        secondary = "monthlySubscriptionPrice" if primary == "unitPrice" else "unitPrice"
        field_price = (fields.get(primary, {}).get("value", {}) or {}).get("amount") \
            or (fields.get(secondary, {}).get("value", {}) or {}).get("amount")
        market_price = context.get("marketAndBmReferences", {}).get("marketAnalysis", {}) \
            .get("price", {}).get("base")
        price = float(field_price or market_price or 0)
    except (TypeError, ValueError, json.JSONDecodeError):
        return result
    if price <= 0 or result.proposedValue.amount <= price * cap_ratio:
        return result
    conservative_amount = max(1, math.floor(price * cap_ratio * 0.8))
    result.proposedValue.amount = conservative_amount
    result.assumptions.append("가격 가설 대비 단위원가 상한을 적용한 보수적 추정입니다.")
    result.explanation = (
        f"가격 가설 {price:,.0f}원 기준의 단위 비용으로 보정했습니다. "
        f"{request.fieldKey}는 건당 또는 구독자당 월 {conservative_amount:,.0f}원으로 검토하세요."
    )
    return result
