import asyncio
import pytest
from pydantic import ValidationError
from app.tasks.finance_estimate import service
from app.tasks.finance_estimate.models import FinanceEstimateResult


def test_estimate_preserves_ai_source_and_assumptions(monkeypatch):
    async def prompt(_system,_user,**_kwargs):
        return {"fieldKey":"annualFixedLaborCost","proposedValue":{"amount":120000000.0,"currency":"KRW"},
            "assumptions":["개발자 2명"],"explanation":"연간 회사 부담 비용을 포함했습니다.",
            "confidence":"MEDIUM","source":"AI_ESTIMATE"}
    monkeypatch.setattr(service,"execute_structured_prompt",prompt)
    result=asyncio.run(service.execute_finance_estimate({"contextJson":"{}","fieldKey":"annualFixedLaborCost",
        "proposalVersion":1,"rejectedProposalJson":""}))
    assert result["source"]=="AI_ESTIMATE"
    assert result["assumptions"]==["개발자 2명"]


def test_three_year_targets_uses_one_bounded_repair(monkeypatch):
    responses = [
        {"fieldKey":"threeYearTargets","proposedValue":{"amount":1.0,"currency":"KRW"},
         "assumptions":["가정"],"explanation":"잘못된 형식","confidence":"LOW","source":"AI_ESTIMATE"},
        {"fieldKey":"threeYearTargets","proposedValue":{"metric":"subscriberCount","unit":"명",
         "years":[{"year":1,"value":100.0},{"year":2,"value":250.0},{"year":3,"value":500.0}]},
         "assumptions":["계획 가정"],"explanation":"구독자 목표 가정입니다.","confidence":"MEDIUM","source":"AI_ESTIMATE"},
    ]
    async def prompt(*_args, **_kwargs):
        return responses.pop(0)
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    result = asyncio.run(service.execute_finance_estimate({"contextJson":"{}","fieldKey":"threeYearTargets",
        "proposalVersion":1,"rejectedProposalJson":""}))
    assert [item["year"] for item in result["proposedValue"]["years"]] == [1, 2, 3]
    assert responses == []


def test_typed_churn_and_customer_count_are_enforced():
    base = {"assumptions":["가정"],"explanation":"설명","confidence":"LOW","source":"AI_ESTIMATE"}
    FinanceEstimateResult.model_validate({**base, "fieldKey":"monthlyChurnRate", "proposedValue":{"percent":3.5}})
    FinanceEstimateResult.model_validate({**base, "fieldKey":"newCustomerCount", "proposedValue":{"count":10}})
    with pytest.raises(ValidationError):
        FinanceEstimateResult.model_validate({**base, "fieldKey":"newCustomerCount", "proposedValue":{"amount":10.0,"currency":"KRW"}})


def test_price_guardrail_rewrites_per_unit_cost(monkeypatch):
    async def prompt(*_args, **_kwargs):
        return {"fieldKey":"paymentFee","proposedValue":{"amount":9000.0,"currency":"KRW"},
            "assumptions":["가정"],"explanation":"수수료 가정","confidence":"LOW","source":"AI_ESTIMATE"}
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    context = '{"financialFields":{"revenueModel":{"value":"SUBSCRIPTION"},"monthlySubscriptionPrice":{"value":{"amount":10000}}}}'
    result = asyncio.run(service.execute_finance_estimate({"contextJson":context,"fieldKey":"paymentFee",
        "proposalVersion":1,"rejectedProposalJson":""}))
    assert result["proposedValue"]["amount"] == 400


def test_donor_economic_sanity_prompt_is_not_shortened():
    prompt = service.ECONOMIC_SANITY_RULES
    for required in (
        "해당 없음 가정", "per-subscriber usage, API, payment, or support costs",
        "per-transaction monetary equivalent", "건당", "구독자당 월", "1%~45%",
        "1%~5%", "1%~20%", "must not exceed 70%", "must not exceed 30%",
        "purely digital service", "greater than the market price", "Tavily benchmark",
        "annual total KRW budgets", "2,376,000 KRW",
    ):
        assert required in prompt


def test_three_year_repair_failure_stops_after_exactly_one_repair(monkeypatch):
    calls = 0

    async def invalid(*_args, **_kwargs):
        nonlocal calls
        calls += 1
        return {"fieldKey": "threeYearTargets", "proposedValue": {"amount": 1, "currency": "KRW"},
                "assumptions": ["가정"], "explanation": "잘못된 형식", "confidence": "LOW",
                "source": "AI_ESTIMATE"}

    monkeypatch.setattr(service, "execute_structured_prompt", invalid)
    with pytest.raises(service.ProviderFailure) as raised:
        asyncio.run(service.execute_finance_estimate({"contextJson": "{}", "fieldKey": "threeYearTargets",
            "proposalVersion": 1, "rejectedProposalJson": ""}))
    assert calls == 2
    assert raised.value.reason == "AI_RESULT_INVALID"
