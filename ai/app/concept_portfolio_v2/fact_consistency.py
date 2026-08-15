"""Legal 전에 명백한 Candidate 사업 사실 자기모순만 차단한다."""

from __future__ import annotations

import re
from typing import Any

from app.tasks.concept_candidate.models import ConceptCandidateResult

from .legal_fact_completeness import assess_role_semantics, classify_fact_presence
from .models import (
    CanonicalConceptDescriptor, ConceptFactConsistencyIssue, ConceptFactConsistencyResult,
)


_PHYSICAL_MARKERS = (
    "배송", "운송", "픽업", "수거", "회수", "현장 방문", "방문 설치", "설치", "조립",
    "보관", "제조", "조리", "수리", "대면 수행",
)
_DIGITAL_MARKERS = (
    "온라인", "웹", "앱", "api", "saas", "소프트웨어", "디지털", "ai 분석",
    "예약페이지 연결", "링크 연결", "자가 사용",
)
_DIGITAL_ONLY_ACTIONS = (
    "ai 분석", "온라인 피드백", "saas", "소프트웨어 제공", "api 제공",
    "예약페이지 연결", "링크 연결", "디지털 결과 제공",
)
_INTERMEDIARY_ACTIONS = ("중개", "매칭", "거래 연결", "예약 연결", "수요자와 제공자", "판매자와 구매자")
_PARTNER_ONLY = ("협력", "제휴", "파트너", "공급업체", "crm 업체", "외부 업체")
_PERSONAL_REQUIRED = ("이메일", "전화번호", "연락처", "주소", "회원 계정", "사용자 식별", "예약자 정보")
_PERSONAL_ABSENT = ("개인정보를 처리하지 않", "개인식별정보를 저장하지 않", "익명으로만")


def _norm(value: Any) -> str:
    return re.sub(r"[^0-9a-z가-힣]+", "", str(value or "").casefold())


def _has(text: Any, markers: tuple[str, ...]) -> bool:
    normalized = _norm(text)
    return any(_norm(marker) in normalized for marker in markers)


def assess_concept_fact_consistency(
    candidate: ConceptCandidateResult,
    descriptor: CanonicalConceptDescriptor,
    candidate_id: str | None = None,
) -> ConceptFactConsistencyResult:
    """도메인 법칙을 만들지 않고 field 간 명백한 자기모순만 판정한다."""
    issues: list[ConceptFactConsistencyIssue] = []
    architecture = descriptor.architecture
    service_text = " ".join((
        candidate.conceptDefinition, candidate.solutionMechanism, candidate.operatingModel,
        candidate.channels, " ".join(candidate.featureSet),
    ))
    physical_text = " ".join(candidate.physicalActivities)
    physical_claimed = _has(physical_text, _PHYSICAL_MARKERS)
    digital_claimed = _has(service_text, _DIGITAL_MARKERS)
    service_physical = _has(service_text, _PHYSICAL_MARKERS)
    digital_only_action = _has(service_text, _DIGITAL_ONLY_ACTIONS)
    digital_architecture = (
        architecture.deliveryModel in {"DIGITAL", "SELF_SERVICE"}
        and architecture.physicalDependency == "NONE")
    physical_architecture = (
        architecture.deliveryModel in {"PHYSICAL_DELIVERY", "PICKUP", "ON_SITE"}
        or architecture.physicalDependency in {"MATERIAL", "CORE"})
    if (physical_claimed and not service_physical
            and (digital_architecture or digital_only_action)):
        issues.append(ConceptFactConsistencyIssue(
            field="physicalActivities", relation="SERVICE_PHYSICAL", status="INVALID_FACT",
            safeReason="순수 디지털 서비스 구조와 배송·방문·설치 등 물리 활동이 동시에 명시되었습니다.",
            repairInstruction="서비스 방식에 맞게 physicalActivities의 잘못된 물리 활동만 정정합니다."))
    elif physical_claimed and digital_claimed and not physical_architecture:
        issues.append(ConceptFactConsistencyIssue(
            field="physicalActivities", relation="SERVICE_PHYSICAL", status="POTENTIAL_CONFLICT",
            safeReason="디지털 서비스 설명과 물리 활동 표현의 관계가 충분히 명확하지 않습니다.",
            repairInstruction="실제 물리 이행이 있는지 확인해 physicalActivities를 명확히 합니다."))

    intermediary_status = assess_role_semantics("intermediaryRole", candidate.intermediaryRole)
    if (classify_fact_presence(candidate.intermediaryRole, "intermediaryRole") == "PRESENT"
            and not _has(candidate.intermediaryRole, _INTERMEDIARY_ACTIONS)
            and _has(candidate.intermediaryRole, _PARTNER_ONLY)):
        issues.append(ConceptFactConsistencyIssue(
            field="intermediaryRole", relation="TRANSACTION_INTERMEDIARY", status="INVALID_FACT",
            safeReason="중개 역할 필드에 파트너 협력 설명만 있고 거래 연결 책임이 없습니다.",
            repairInstruction="실제 거래 방식에 맞춰 intermediaryRole의 중개 존재·부재만 정정합니다."))
    elif intermediary_status == "MISMATCH":
        issues.append(ConceptFactConsistencyIssue(
            field="intermediaryRole", relation="TRANSACTION_INTERMEDIARY", status="INVALID_FACT",
            safeReason="intermediaryRole이 중개 책임이 아닌 다른 업무를 설명합니다.",
            repairInstruction="거래 방식에 맞춰 intermediaryRole만 정정합니다."))

    if assess_role_semantics("sellerRole", candidate.sellerRole) == "MISMATCH":
        issues.append(ConceptFactConsistencyIssue(
            field="sellerRole", relation="TRANSACTION_SELLER", status="INVALID_FACT",
            safeReason="sellerRole이 거래·결제의 판매 또는 계약 책임을 설명하지 않습니다.",
            repairInstruction="거래·결제 흐름에 맞춰 sellerRole만 정정합니다."))

    partner_text = " ".join(candidate.partnerRequirements)
    direct_without_partner = _has(candidate.partnerModel, ("외부 파트너 없음", "모두 직접 제공", "직접 운영"))
    external_partner = _has(partner_text, ("외부", "제휴", "계약 파트너", "위탁", "공급업체"))
    if (architecture.partnerModel == "OWN_OPERATED" and direct_without_partner and external_partner):
        issues.append(ConceptFactConsistencyIssue(
            field="partnerRequirements", relation="PARTNER_OPERATION", status="INVALID_FACT",
            safeReason="외부 파트너가 없는 직접 운영 구조와 외부 파트너 요구가 동시에 명시되었습니다.",
            repairInstruction="직접 운영 구조에 맞춰 partnerRequirements만 정정합니다."))

    data_text = " ".join((candidate.solutionMechanism, " ".join(candidate.transactionFlow)))
    personal_text = " ".join(candidate.personalDataUsage)
    if _has(data_text, _PERSONAL_REQUIRED) and _has(personal_text, _PERSONAL_ABSENT):
        issues.append(ConceptFactConsistencyIssue(
            field="personalDataUsage", relation="DATA_PERSONAL", status="INVALID_FACT",
            safeReason="개인 단위 정보를 사용하는 흐름과 개인정보 비처리 설명이 동시에 존재합니다.",
            repairInstruction="실제 데이터 사용에 맞춰 personalDataUsage만 정정합니다."))

    status = ("INVALID_FACT" if any(item.status == "INVALID_FACT" for item in issues) else
              "POTENTIAL_CONFLICT" if issues else "CONSISTENT")
    summary = ({
        "CONSISTENT": "검사 대상 사업 사실 사이에 명백한 모순이 없습니다.",
        "POTENTIAL_CONFLICT": "사업 사실 관계가 불명확하지만 명백한 자기모순은 아닙니다.",
        "INVALID_FACT": "Legal 전에 정정해야 할 명백한 사업 사실 자기모순이 있습니다.",
    }[status])
    return ConceptFactConsistencyResult(
        candidateId=candidate_id, status=status, issues=issues, safeSummary=summary)
