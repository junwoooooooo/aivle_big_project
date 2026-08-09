"""실제 Candidate 사업 필드에서 controlled mechanics code와 한국어 label을 도출한다."""

from __future__ import annotations

from typing import Iterable

from app.tasks.concept_candidate.models import ConceptCandidateResult

from .models import MechanicsDescriptor, MechanicsDimension


CONTROLLED_CODES = {
    "OTHER", "SUBSCRIPTION_PORTIONING", "GROUP_ORDER", "RECIPE_BUNDLE", "INVENTORY_COACH",
    "EMPLOYEE_FOOD_KIT", "PRODUCTION_RESERVATION", "UNMANNED_PORTIONING", "EXPERT_MEAL_GUIDANCE",
    "CENTRAL_PORTIONING", "LOCAL_RETAILER_NETWORK", "CURATED_SUPPLIER", "RETAIL_DATA_NETWORK",
    "B2B_SUPPLY", "PRODUCER_NETWORK", "LOCAL_STATION_NETWORK", "QUALIFIED_EXPERT_NETWORK",
    "RECURRING_DELIVERY", "PICKUP_POINT", "ON_DEMAND_DELIVERY", "STORE_PICKUP",
    "OFFICE_BULK_DELIVERY", "PRODUCER_DIRECT_DELIVERY", "STATION_PICKUP", "SCHEDULED_DELIVERY",
    "DIGITAL_ONLY", "DIRECT_OPERATOR", "MARKETPLACE", "CURATION_MARKET", "DIGITAL_COACH",
    "B2B_OPERATOR", "STATION_OPERATOR", "GUIDANCE_PLATFORM", "LOCAL_CENTER",
    "CHEF_PRODUCER", "RETAIL_DATA_PARTNER", "EMPLOYER_CATERING", "SPACE_LOGISTICS",
    "QUALIFIED_EXPERT", "SUBSCRIPTION", "AGGREGATED_ORDER", "BUNDLE_PURCHASE",
    "RECOMMENDATION_ORDER", "ENTERPRISE_ORDER", "PREORDER", "METERED_USE",
    "CONSULTATION_ORDER", "SUBSCRIPTION_FEE", "COMMISSION", "BUNDLE_SALE",
    "APP_SUBSCRIPTION", "B2B_CONTRACT", "TRANSACTION_COMMISSION", "USAGE_FEE",
    "SERVICE_SUBSCRIPTION", "AD_SUPPORTED", "DIRECT_SALE", "MOBILE_DIRECT", "COMMUNITY_APP",
    "CONTENT_COMMERCE", "MOBILE_ASSISTANT", "EMPLOYEE_PORTAL", "RESERVATION_MARKET",
    "KIOSK_APP", "APPOINTMENT_APP", "OFFLINE_DIRECT",
}


def dimension(code: str, label: str, detail: str = "") -> MechanicsDimension:
    return MechanicsDimension(code=code if code in CONTROLLED_CODES else "OTHER", labelKo=label, detailKo=detail)


def _text(*values: object) -> str:
    flattened: list[str] = []
    for value in values:
        if isinstance(value, Iterable) and not isinstance(value, (str, bytes, dict)):
            flattened.extend(str(item) for item in value)
        else:
            flattened.append(str(value))
    return " ".join(flattened).casefold()


def _pick(text: str, choices: list[tuple[tuple[str, ...], str, str]], default_label: str):
    for markers, code, label in choices:
        if any(marker.casefold() in text for marker in markers):
            return dimension(code, label, text[:300])
    return dimension("OTHER", default_label, text[:300])


def derive_candidate_mechanics(candidate: ConceptCandidateResult) -> MechanicsDescriptor:
    # Mechanics는 마케팅 문구가 아니라 실제 운영 필드에서만 도출한다.
    # conceptDefinition에는 원 아이디어나 Plan 문구가 섞일 수 있어 분류를 오염시킨다.
    solution = _text(candidate.solutionMechanism, candidate.featureSet)
    supply = _text(candidate.operatingModel, candidate.partnerModel, candidate.partnerRequirements)
    fulfillment = _text(candidate.physicalActivities, candidate.channels, candidate.transactionFlow)
    platform = _text(candidate.platformRole, candidate.providerRole, candidate.sellerRole, candidate.intermediaryRole)
    partner = _text(candidate.partnerModel, candidate.partnerRequirements, candidate.qualificationRequirements)
    transaction = _text(candidate.transactionFlow, candidate.paymentFlow, candidate.intermediaryRole)
    commercial = _text(candidate.revenueModel, candidate.price, candidate.paymentFlow)
    interaction = _text(candidate.channels, candidate.platformRole, candidate.featureSet)
    return MechanicsDescriptor(
        solutionMechanismType=_pick(solution, [
            (("구독", "정기 소분"), "SUBSCRIPTION_PORTIONING", "정기 소분 구독"),
            (("공동구매", "공동 주문"), "GROUP_ORDER", "공동구매"),
            (("레시피", "재료 번들"), "RECIPE_BUNDLE", "레시피·재료 번들"),
            (("냉장고", "보유 재료", "보충 추천"), "INVENTORY_COACH", "재고 인식·보충 코칭"),
            (("기업", "직원", "복지"), "EMPLOYEE_FOOD_KIT", "기업용 식재료 키트"),
            (("예약", "생산 전"), "PRODUCTION_RESERVATION", "생산 전 수요 예약"),
            (("무인", "스테이션"), "UNMANNED_PORTIONING", "무인 소분"),
            (("영양", "전문가", "상담"), "EXPERT_MEAL_GUIDANCE", "전문가 식단 동행"),
        ], "기타 솔루션 구조"),
        supplyModel=_pick(supply, [
            (("소분센터",), "CENTRAL_PORTIONING", "중앙 소분 공급"),
            (("식자재점", "소매점"), "LOCAL_RETAILER_NETWORK", "지역 소매점 공급망"),
            (("생산자",), "PRODUCER_NETWORK", "생산자 공급망"),
            (("데이터", "리테일"), "RETAIL_DATA_NETWORK", "리테일 데이터 연계"),
            (("기업", "급식"), "B2B_SUPPLY", "기업 공급망"),
            (("자격", "전문가"), "QUALIFIED_EXPERT_NETWORK", "자격 보유 전문가망"),
        ], "기타 공급 구조"),
        fulfillmentModel=_pick(fulfillment, [
            (("정기배송", "정기 배송"), "RECURRING_DELIVERY", "정기 배송"),
            (("거점수령", "거점 수령"), "PICKUP_POINT", "거점 수령"),
            (("온디맨드", "즉시 배송"), "ON_DEMAND_DELIVERY", "주문형 배송"),
            (("매장 픽업",), "STORE_PICKUP", "매장 픽업"),
            (("생산자 직배송",), "PRODUCER_DIRECT_DELIVERY", "생산자 직접 배송"),
            (("무인함", "스테이션"), "STATION_PICKUP", "무인 거점 수령"),
            (("배송",), "SCHEDULED_DELIVERY", "예약·일정 배송"),
            (("디지털", "온라인"), "DIGITAL_ONLY", "디지털 제공"),
        ], "기타 이행 구조"),
        platformRoleType=_pick(platform, [
            (("중개", "장터", "거래 연결"), "MARKETPLACE", "거래 중개 플랫폼"),
            (("코치", "추천"), "DIGITAL_COACH", "디지털 코치"),
            (("기업",), "B2B_OPERATOR", "기업 서비스 운영자"),
            (("상담", "전문가"), "GUIDANCE_PLATFORM", "전문가 연결 플랫폼"),
            (("판매", "운영"), "DIRECT_OPERATOR", "직접 운영·판매자"),
        ], "기타 플랫폼 역할"),
        partnerStructureType=_pick(partner, [
            (("소분센터",), "LOCAL_CENTER", "지역 소분센터"),
            (("식자재점", "소매점"), "LOCAL_RETAILER_NETWORK", "지역 소매점 제휴"),
            (("셰프", "큐레이션"), "CHEF_PRODUCER", "셰프·생산자 제휴"),
            (("기업", "급식"), "EMPLOYER_CATERING", "기업·급식 제휴"),
            (("생산자",), "PRODUCER_NETWORK", "생산자 네트워크"),
            (("공간", "물류"), "SPACE_LOGISTICS", "공간·물류 제휴"),
            (("자격", "전문가"), "QUALIFIED_EXPERT", "자격 보유 전문가"),
        ], "기타 파트너 구조"),
        transactionModel=_pick(transaction, [
            (("구독",), "SUBSCRIPTION", "구독 거래"),
            (("공동", "묶", "집계"), "AGGREGATED_ORDER", "주문 집계"),
            (("번들",), "BUNDLE_PURCHASE", "번들 구매"),
            (("추천",), "RECOMMENDATION_ORDER", "추천 후 주문"),
            (("기업",), "ENTERPRISE_ORDER", "기업 계약 주문"),
            (("예약",), "PREORDER", "사전 예약"),
            (("사용량",), "METERED_USE", "사용량 기반 거래"),
            (("상담",), "CONSULTATION_ORDER", "상담 결합 주문"),
        ], "기타 거래 구조"),
        commercialModel=_pick(commercial, [
            (("무료", "광고"), "AD_SUPPORTED", "광고 기반 무료 모델"),
            (("구독",), "SUBSCRIPTION_FEE", "구독료"),
            (("수수료",), "COMMISSION", "거래 수수료"),
            (("번들 판매",), "BUNDLE_SALE", "번들 판매"),
            (("앱", "프리미엄"), "APP_SUBSCRIPTION", "앱 구독료"),
            (("b2b", "기업 계약"), "B2B_CONTRACT", "기업 계약"),
            (("사용량",), "USAGE_FEE", "사용량 기반 과금"),
            (("상담",), "SERVICE_SUBSCRIPTION", "상담 결합 구독"),
            (("판매",), "DIRECT_SALE", "직접 판매"),
        ], "기타 수익 구조"),
        customerInteractionModel=_pick(interaction, [
            (("모바일", "앱"), "MOBILE_DIRECT", "모바일 직접 이용"),
            (("커뮤니티", "공동"), "COMMUNITY_APP", "커뮤니티 앱"),
            (("콘텐츠", "레시피"), "CONTENT_COMMERCE", "콘텐츠 커머스"),
            (("직원", "기업"), "EMPLOYEE_PORTAL", "직원 포털"),
            (("예약",), "RESERVATION_MARKET", "예약형 접점"),
            (("키오스크", "무인"), "KIOSK_APP", "키오스크·앱"),
            (("상담",), "APPOINTMENT_APP", "상담 예약 앱"),
            (("오프라인", "방문"), "OFFLINE_DIRECT", "오프라인 직접 접점"),
        ], "기타 고객 접점"),
    )
