import asyncio
from app.tasks.finance_estimate import service


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
