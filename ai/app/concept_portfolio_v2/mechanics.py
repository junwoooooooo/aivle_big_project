"""Plan과 Candidate가 공유하는 system-owned generic canonicalization."""

from __future__ import annotations

import re
from typing import Any, Iterable

from app.tasks.concept_candidate.models import ConceptCandidateResult

from .models import (
    BusinessArchitecture, CanonicalConceptDescriptor, ConceptThesis, PortfolioPlanDraft,
)


GENERIC_CODE_SETS = {
    "businessRole": {"PRINCIPAL_OPERATOR", "MARKETPLACE", "INTERMEDIARY", "SAAS_TOOL", "ADVISORY",
                     "AGGREGATOR", "PLATFORM_INFRASTRUCTURE", "OTHER"},
    "operatingModel": {"OWN_OPERATED", "PARTNER_NETWORK", "PEER_TO_PEER", "AUTOMATED_DIGITAL",
                       "EXPERT_NETWORK", "HYBRID", "OTHER"},
    "deliveryModel": {"DIGITAL", "PHYSICAL_DELIVERY", "PARTNER_FULFILLED", "PICKUP", "ON_SITE",
                      "SELF_SERVICE", "HYBRID", "OTHER"},
    "transactionModel": {"ONE_OFF", "RECURRING", "BOOKING", "MATCHING", "PREORDER", "AUCTION",
                         "USAGE_BASED", "OTHER"},
    "monetizationModel": {"SUBSCRIPTION", "DIRECT_SALE", "COMMISSION", "SERVICE_FEE", "USAGE_FEE",
                          "LICENSING", "ADVERTISING", "B2B_CONTRACT", "FREEMIUM", "OTHER"},
    "customerInteractionModel": {"APP", "WEB", "API", "OFFLINE", "OMNICHANNEL", "COMMUNITY",
                                 "ASSISTED", "SELF_SERVICE", "OTHER"},
}

ROLE_LABEL_KO = {
    "PRINCIPAL_OPERATOR": "직접 운영", "MARKETPLACE": "마켓플레이스", "INTERMEDIARY": "거래 중개",
    "SAAS_TOOL": "SaaS 도구", "ADVISORY": "전문가 자문", "AGGREGATOR": "수요·정보 집계",
    "PLATFORM_INFRASTRUCTURE": "플랫폼 인프라", "OTHER": "기타 역할",
}
OPERATING_LABEL_KO = {
    "OWN_OPERATED": "자체 운영", "PARTNER_NETWORK": "파트너 네트워크",
    "PEER_TO_PEER": "개인 간 네트워크", "AUTOMATED_DIGITAL": "디지털 자동화",
    "EXPERT_NETWORK": "전문가 네트워크", "HYBRID": "혼합 운영", "OTHER": "기타 운영",
}


def _text(*values: Any) -> str:
    flattened: list[str] = []
    for value in values:
        if isinstance(value, Iterable) and not isinstance(value, (str, bytes, dict)):
            flattened.extend(str(item) for item in value)
        else:
            flattened.append(str(value))
    return " ".join(flattened).casefold()


def _pick(text: str, choices: list[tuple[tuple[str, ...], str]], default: str = "OTHER") -> str:
    for markers, code in choices:
        if any(marker.casefold() in text for marker in markers):
            return code
    return default


def _architecture(*, role: str, operation: str, partner: str, delivery: str,
                  transaction: str, monetization: str, interaction: str,
                  data: str = "", physical: str = "") -> BusinessArchitecture:
    business_role = _pick(role, [
        (("marketplace", "마켓플레이스", "양면시장"), "MARKETPLACE"),
        (("중개", "매칭", "연결"), "INTERMEDIARY"),
        (("saas", "software", "소프트웨어", "업무 도구"), "SAAS_TOOL"),
        (("자문", "상담", "코칭", "전문가 지원"), "ADVISORY"),
        (("집계", "aggregat"), "AGGREGATOR"),
        (("api", "인프라", "infrastructure"), "PLATFORM_INFRASTRUCTURE"),
        (("직접 운영", "운영사", "직접 제공", "principal"), "PRINCIPAL_OPERATOR"),
    ], "PRINCIPAL_OPERATOR")
    operating = _pick(operation + " " + partner, [
        (("peer-to-peer", "p2p", "개인 간"), "PEER_TO_PEER"),
        (("전문가 네트워크", "전문가 풀", "자격 보유"), "EXPERT_NETWORK"),
        (("파트너", "제휴", "협력사", "network"), "PARTNER_NETWORK"),
        (("자동화", "ai", "알고리즘", "digital"), "AUTOMATED_DIGITAL"),
        (("하이브리드", "hybrid"), "HYBRID"),
        (("직접 운영", "자체 운영", "own-operated"), "OWN_OPERATED"),
    ], "OWN_OPERATED")
    partner_model = _pick(partner, [
        (("peer-to-peer", "p2p", "개인 간"), "PEER_TO_PEER"),
        (("전문가", "자격"), "EXPERT_NETWORK"),
        (("파트너", "제휴", "협력", "network"), "PARTNER_NETWORK"),
        (("자동화", "ai", "알고리즘"), "AUTOMATED_DIGITAL"),
        (("하이브리드", "hybrid"), "HYBRID"),
    ], "OWN_OPERATED")
    delivery_model = _pick(delivery, [
        (("partner fulfilled", "파트너 이행", "파트너 제공", "제휴사가"), "PARTNER_FULFILLED"),
        (("픽업", "수령", "pickup"), "PICKUP"),
        (("방문", "현장", "on-site"), "ON_SITE"),
        (("배송", "배달", "ship", "delivery"), "PHYSICAL_DELIVERY"),
        (("셀프", "self-service", "사용자 직접"), "SELF_SERVICE"),
        (("하이브리드", "hybrid"), "HYBRID"),
        (("디지털", "온라인", "download"), "DIGITAL"),
    ], "DIGITAL")
    transaction_model = _pick(transaction, [
        (("구독", "정기", "recurring"), "RECURRING"),
        (("예약", "booking"), "BOOKING"),
        (("매칭", "matching"), "MATCHING"),
        (("선주문", "사전 주문", "preorder"), "PREORDER"),
        (("경매", "auction"), "AUCTION"),
        (("사용량", "usage-based", "종량"), "USAGE_BASED"),
        (("일회", "건별", "one-off"), "ONE_OFF"),
    ], "ONE_OFF")
    monetization_model = _pick(monetization, [
        (("구독", "월 이용료", "정기요금", "subscription"), "SUBSCRIPTION"),
        (("수수료", "commission"), "COMMISSION"),
        (("사용량", "종량", "usage fee"), "USAGE_FEE"),
        (("라이선스", "licensing"), "LICENSING"),
        (("광고", "advertis"), "ADVERTISING"),
        (("b2b", "기업 계약", "연간 계약"), "B2B_CONTRACT"),
        (("프리미엄", "freemium", "무료+유료"), "FREEMIUM"),
        (("서비스 요금", "상담료", "service fee"), "SERVICE_FEE"),
        (("판매", "구매", "direct sale"), "DIRECT_SALE"),
    ], "OTHER")
    interaction_model = _pick(interaction, [
        (("omnichannel", "옴니채널", "온·오프라인", "온오프라인"), "OMNICHANNEL"),
        (("community", "커뮤니티"), "COMMUNITY"),
        (("api",), "API"),
        (("web", "웹"), "WEB"),
        (("app", "앱", "모바일"), "APP"),
        (("상담", "도움", "assisted", "전문가"), "ASSISTED"),
        (("셀프", "self-service", "사용자 직접"), "SELF_SERVICE"),
        (("오프라인", "방문", "현장"), "OFFLINE"),
    ], "OTHER")
    dependency = lambda text: "CORE" if any(x in text for x in ("필수", "핵심", "core")) else (
        "MATERIAL" if text.strip() else "NONE")
    return BusinessArchitecture(
        businessRole=business_role, operatingModel=operating, partnerModel=partner_model,
        deliveryModel=delivery_model, transactionModel=transaction_model,
        monetizationModel=monetization_model, customerInteractionModel=interaction_model,
        dataDependency=dependency(data), physicalDependency=dependency(physical),
    )


def _mechanism_family(solution: str) -> str:
    tokens = re.findall(r"[0-9a-z가-힣]+", solution.casefold())
    normalized = " ".join(tokens[:16]) or "기타 해결 방식"
    return normalized[:300]


def _descriptor(thesis: ConceptThesis, architecture: BusinessArchitecture) -> CanonicalConceptDescriptor:
    family_id = f"{architecture.businessRole}:{architecture.operatingModel}"
    family_label = (f"{ROLE_LABEL_KO[architecture.businessRole]} · "
                    f"{OPERATING_LABEL_KO[architecture.operatingModel]}")
    return CanonicalConceptDescriptor(
        thesis=thesis, architecture=architecture,
        mechanismFamily=_mechanism_family(thesis.solutionThesis),
        familyId=family_id, familyLabelKo=family_label,
    )


class GenericConceptNormalizer:
    """Provider 문구를 system-owned small code contract로 정규화한다."""

    @staticmethod
    def from_plan(plan: PortfolioPlanDraft) -> CanonicalConceptDescriptor:
        thesis = ConceptThesis(
            targetSegmentThesis=plan.targetSegment, useCaseThesis=plan.useContext,
            valuePropositionThesis=plan.valueProposition, offerThesis=plan.offerThesis,
            solutionThesis=plan.solutionThesis)
        architecture = _architecture(
            role=_text(plan.solutionThesis, plan.operatingApproach, plan.partnerApproach,
                       plan.transactionApproach),
            operation=plan.operatingApproach, partner=plan.partnerApproach,
            delivery=plan.fulfillmentApproach, transaction=plan.transactionApproach,
            monetization=plan.commercialApproach, interaction=plan.customerInteraction)
        return _descriptor(thesis, architecture)

    @staticmethod
    def from_candidate(candidate: ConceptCandidateResult) -> CanonicalConceptDescriptor:
        thesis = ConceptThesis(
            targetSegmentThesis=candidate.targetUsers, useCaseThesis=candidate.problemScenario,
            valuePropositionThesis=candidate.coreValue,
            offerThesis=_text(candidate.featureSet), solutionThesis=candidate.solutionMechanism)
        architecture = _architecture(
            role=_text(candidate.solutionMechanism, candidate.platformRole, candidate.providerRole,
                       candidate.sellerRole, candidate.intermediaryRole, candidate.transactionFlow),
            operation=candidate.operatingModel, partner=_text(candidate.partnerModel,
                                                               candidate.partnerRequirements),
            delivery=_text(candidate.physicalActivities, candidate.channels),
            transaction=_text(candidate.transactionFlow), monetization=_text(candidate.revenueModel,
                                                                              candidate.paymentFlow),
            interaction=_text(candidate.channels, candidate.platformRole),
            data=_text(candidate.personalDataUsage), physical=_text(candidate.physicalActivities))
        return _descriptor(thesis, architecture)


def derive_plan_descriptor(plan: PortfolioPlanDraft) -> CanonicalConceptDescriptor:
    return GenericConceptNormalizer.from_plan(plan)


def derive_candidate_descriptor(candidate: ConceptCandidateResult) -> CanonicalConceptDescriptor:
    return GenericConceptNormalizer.from_candidate(candidate)


# 이전 내부 호출명은 동일한 generic normalizer로 연결한다.
derive_candidate_mechanics = derive_candidate_descriptor
