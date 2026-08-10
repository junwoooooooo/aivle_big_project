"""법률 판단 전에 사업 사실패턴의 구조적 완결성만 검사한다."""

from __future__ import annotations

import re
from typing import Any

from app.tasks.concept_candidate.models import ConceptCandidateResult

from .language_policy import is_governance_placeholder
from .models import LegalFactCompletenessResult, RedesignRequirementCompliance


_EMPTY_MARKERS = {
    "없음", "해당없음", "해당사항없음", "미정", "추후확인", "검토필요",
    "필요한역할", "관련파트너", "필요한개인정보처리", "필요한자격",
}
_ROLE_FIELDS = ("platformRole", "providerRole", "sellerRole", "intermediaryRole")


def _norm(value: Any) -> str:
    return re.sub(r"[^0-9a-z가-힣]+", "", str(value or "").casefold())


def _substantive(value: Any) -> bool:
    if isinstance(value, list):
        return any(_substantive(item) for item in value)
    normalized = _norm(value)
    return bool(normalized and normalized not in _EMPTY_MARKERS and not is_governance_placeholder(str(value)))


def _contains(candidate: ConceptCandidateResult, markers: tuple[str, ...]) -> bool:
    text = _norm(" ".join(str(getattr(candidate, field)) for field in (
        "conceptDefinition", "solutionMechanism", "featureSet", "actorRoles", "transactionFlow",
        "paymentFlow", "operatingModel", "partnerModel",
    )))
    return any(_norm(marker) in text for marker in markers)


def assess_legal_fact_completeness(candidate: ConceptCandidateResult) -> LegalFactCompletenessResult:
    """도메인 법률지식 없이 역할·거래·이행·데이터·파트너 구조만 검사한다."""
    missing: list[str] = []
    contradictions: list[str] = []
    affected: list[str] = []

    for field in _ROLE_FIELDS:
        if not _substantive(getattr(candidate, field)):
            missing.append(f"{field}의 실제 사업 역할이 명시되지 않았습니다.")
            affected.append(field)
    if not _substantive(candidate.transactionFlow):
        missing.append("주문·계약·제공 주체를 포함한 transactionFlow가 필요합니다.")
        affected.append("transactionFlow")
    if not _substantive(candidate.paymentFlow):
        missing.append("결제 수취 및 정산 주체를 포함한 paymentFlow가 필요합니다.")
        affected.append("paymentFlow")
    for field, requirement in (
        ("targetRegion", "검토 가정으로 사용할 서비스 대상 국가·지역을 targetRegion에 명시해야 합니다."),
        ("price", "검토 가정으로 사용할 가격 또는 가격 결정 방식을 price에 명시해야 합니다."),
        ("channels", "고객 접점과 판매·제공 채널을 channels에 명시해야 합니다."),
    ):
        if not _substantive(getattr(candidate, field)):
            missing.append(requirement)
            affected.append(field)

    physical_context = _contains(candidate, ("배송", "픽업", "현장", "조립", "보관", "대면"))
    if physical_context and not _substantive(candidate.physicalActivities):
        missing.append("물리적 이행 활동과 수행 주체를 physicalActivities에 명시해야 합니다.")
        affected.append("physicalActivities")
    data_context = _contains(candidate, ("회원가입", "앱주문", "배송", "예약", "개인화", "고객연락", "계정"))
    if data_context and not _substantive(candidate.personalDataUsage):
        missing.append("서비스 흐름에서 처리하는 개인정보와 목적을 personalDataUsage에 명시해야 합니다.")
        affected.append("personalDataUsage")
    partner_context = _contains(candidate, ("파트너", "제휴", "공급자", "전문가", "판매자", "중개"))
    if partner_context and not _substantive(candidate.partnerRequirements):
        missing.append("외부 파트너의 사업상 역할과 기능을 partnerRequirements에 명시해야 합니다.")
        affected.append("partnerRequirements")

    role_text = " ".join(getattr(candidate, field) for field in _ROLE_FIELDS)
    direct = any(marker in role_text for marker in ("직접 판매", "직접 제공", "직접 수취"))
    intermediary = any(marker in role_text for marker in ("중개", "연결", "매칭"))
    if direct and intermediary and not any(marker in " ".join(candidate.transactionFlow)
                                           for marker in ("거래별", "일부", "제휴", "정산", "직접")):
        contradictions.append("직접 거래와 중개 역할이 동일 거래에서 어떻게 구분되는지 불명확합니다.")
        affected.extend(["sellerRole", "intermediaryRole", "transactionFlow"])

    affected = list(dict.fromkeys(affected))
    if contradictions:
        status = "INVALID"
    else:
        status = "COMPLETABLE" if missing else "COMPLETE"
    requirements = [*missing, *[f"모순 해소: {item}" for item in contradictions]]
    summary = ({"COMPLETE": "법률 사전검토에 필요한 사업 사실패턴이 구조적으로 완결되었습니다.",
                "COMPLETABLE": "동일 Concept 안에서 누락된 사업 사실을 한 번 보완할 수 있습니다.",
                "INVALID": "사업 역할과 거래 구조의 중대한 모순을 먼저 재생성해야 합니다."}[status])
    return LegalFactCompletenessResult(status=status, missingDesignFacts=missing,
        contradictions=contradictions, completionRequirements=requirements,
        affectedFields=affected, safeSummary=summary)


_REQUIREMENT_FIELDS = {
    "결제": ("paymentFlow", "transactionFlow"), "정산": ("paymentFlow", "transactionFlow"),
    "판매": ("sellerRole", "transactionFlow"), "제공": ("providerRole", "transactionFlow"),
    "중개": ("intermediaryRole", "platformRole"), "플랫폼": ("platformRole",),
    "개인정보": ("personalDataUsage",), "데이터": ("personalDataUsage",),
    "배송": ("physicalActivities", "transactionFlow"), "이행": ("physicalActivities", "providerRole"),
    "파트너": ("partnerRequirements", "partnerModel"), "자격": ("qualificationRequirements", "partnerRequirements"),
    "채널": ("channels",), "가격": ("price",), "지역": ("targetRegion",),
}


def validate_redesign_requirements(parent: ConceptCandidateResult, child: ConceptCandidateResult,
                                   requirements: list[str]) -> RedesignRequirementCompliance:
    satisfied: list[str] = []
    unsatisfied: list[str] = []
    ambiguous: list[str] = []
    for requirement in requirements:
        fields = tuple(dict.fromkeys(field for marker, mapped in _REQUIREMENT_FIELDS.items()
                                     if marker in requirement for field in mapped))
        if not fields:
            changed = _norm(child.model_dump(mode="json")) != _norm(parent.model_dump(mode="json"))
            (ambiguous if changed else unsatisfied).append(requirement)
            continue
        child_complete = all(_substantive(getattr(child, field)) for field in fields)
        changed = any(_norm(getattr(parent, field)) != _norm(getattr(child, field)) for field in fields)
        (satisfied if child_complete and changed else unsatisfied).append(requirement)
    status = "FAIL" if unsatisfied else ("AMBIGUOUS" if ambiguous else "PASS")
    return RedesignRequirementCompliance(status=status, satisfiedRequirements=satisfied,
        unsatisfiedRequirements=[*unsatisfied, *ambiguous],
        safeSummary=("Legal redesign 요구가 Candidate에 반영되었습니다." if status == "PASS" else
                     "Legal redesign 요구의 반영 여부를 추가 보완해야 합니다."))


def normalized_requirements(requirements: list[str]) -> tuple[str, ...]:
    return tuple(sorted(_norm(item) for item in requirements if _norm(item)))
