import asyncio

from app.tasks.tech_ops_proposal import service


def test_proposal_is_non_null_and_versioned_alternative_can_differ(monkeypatch):
    calls = []
    async def prompt(_system, user, **_kwargs):
        calls.append(user)
        amount = 100 if len(calls) == 1 else 180
        return {"deliveryOrProductionMethod":{"method":"직접 운영","operatingModel":"예약 운영","partnerModel":"구장 제휴"},
                "expectedMonthlyThroughputOrSales":{"amount":amount,"unit":"건"},
                "technicalSupplyOperationalConstraints":["월별 용량 점검"],
                "assumptions":["초기 제휴 구장 3곳"],"explanation":"사용자 검토용 제안입니다."}
    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    first = asyncio.run(service.execute_tech_ops_proposal({"contextJson":"{}","proposalVersion":1,"rejectedProposalJson":""}))
    second = asyncio.run(service.execute_tech_ops_proposal({"contextJson":"{}","proposalVersion":2,"rejectedProposalJson":str(first)}))
    assert first["expectedMonthlyThroughputOrSales"]["amount"] == 100
    assert second["expectedMonthlyThroughputOrSales"]["amount"] == 180
