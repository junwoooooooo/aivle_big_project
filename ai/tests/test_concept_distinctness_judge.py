import asyncio

from app.tasks.concept_distinctness_judge import service
from app.tasks.concept_distinctness_judge.models import ConceptDistinctnessJudgeResult


def fingerprint(revenue="월 정액 멤버십", mechanism="개인 참가자를 즉석 경기 팀으로 연결"):
    return {
        "targetUsers": "혼자 참가하는 직장인", "problemScenario": "경기 인원을 모으기 어렵다",
        "coreValue": "당일 경기 참여", "solutionMechanism": mechanism,
        "revenueModel": revenue, "channels": "제휴 풋살장", "platformRole": "참가자 연결 중개",
        "operatingModel": "당일 수요를 모아 팀 구성", "partnerModel": "풋살장 제휴",
        "transactionFlow": ["개인 신청", "팀 자동 구성"], "providerRole": "풋살장",
        "sellerRole": "플랫폼", "intermediaryRole": "참가자와 구장 연결",
        "featureSet": ["즉석 팀 구성", "구장 예약"],
        "actorRoles": ["참가자", "풋살장", "중개 플랫폼"], "price": "경기당 1만원",
        "paymentFlow": ["참가자가 플랫폼에 결제", "플랫폼이 구장에 정산"],
        "personalDataUsage": ["연락처와 경기 선호"], "physicalActivities": ["풋살 경기 참여"],
        "partnerRequirements": ["제휴 풋살장"], "qualificationRequirements": [],
    }


def test_schema_is_strict_and_safe():
    schema = ConceptDistinctnessJudgeResult.model_json_schema()
    assert schema["additionalProperties"] is False
    assert "reasoning" not in str(schema).lower()


def test_semantic_equivalent_membership_and_matching_can_be_duplicate(monkeypatch):
    async def prompt(_system, _user, **_kwargs):
        return {"decision": "DUPLICATE", "overlappingDimensions": ["revenueModel", "solutionMechanism"],
                "materiallyDifferentDimensions": [], "safeSummary": "표현만 다르고 구조가 같습니다."}

    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    result = asyncio.run(service.execute_concept_distinctness_judge({
        "candidateA": fingerprint(),
        "candidateB": fingerprint("매달 비용을 내는 구독 회원제", "개인 참가자를 당일 경기 인원에 자동 배정"),
    }))
    assert result["decision"] == "DUPLICATE"


def test_materially_different_mechanism_can_be_distinct(monkeypatch):
    async def prompt(_system, _user, **_kwargs):
        return {"decision": "DISTINCT", "overlappingDimensions": ["targetUsers"],
                "materiallyDifferentDimensions": ["solutionMechanism", "operatingModel", "revenueModel"],
                "safeSummary": "핵심 운영 구조가 다릅니다."}

    monkeypatch.setattr(service, "execute_structured_prompt", prompt)
    result = asyncio.run(service.execute_concept_distinctness_judge({
        "candidateA": fingerprint(), "candidateB": fingerprint("거래 수수료", "구장 SaaS 예약 도구"),
    }))
    assert result["decision"] == "DISTINCT"
