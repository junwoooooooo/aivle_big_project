"""법률 판단 전에 사업 사실패턴의 구조적 완결성만 검사한다."""

from __future__ import annotations

import re
from typing import Any, Literal

from app.tasks.concept_candidate.models import ConceptCandidateResult

from .language_policy import is_governance_placeholder
from .models import (
    BusinessRoleSemanticItem, CanonicalConceptDescriptor, LegalFactCompletionCompliance,
    LegalFactCompletionRequirement, LegalFactCompletenessResult, LegalFactDependencyAssessment,
    LegalFactDependencySemanticItem, LegalFactDependencyType, RedesignRequirementCompliance,
)


_EMPTY_MARKERS = {
    "없음", "해당없음", "해당사항없음", "미정", "추후확인", "검토필요",
    "필요한역할", "관련파트너", "필요한개인정보처리", "필요한자격",
}
_ROLE_FIELDS = ("platformRole", "providerRole", "sellerRole", "intermediaryRole")
FactPresence = Literal["PRESENT", "EXPLICIT_ABSENCE", "UNKNOWN", "EMPTY"]
RoleSemanticStatus = Literal["MATCH", "EXPLICIT_ABSENCE", "MISMATCH", "AMBIGUOUS"]
_EXPLICIT_ABSENCE_MARKERS = (
    "하지않", "아님", "아니며", "아닌", "없음", "해당없음", "사용하지않", "수행하지않", "담당하지않",
)
_UNKNOWN_MARKERS = (
    "미정", "확인필요", "검토필요", "검증필요", "추후결정", "정해지지않음", "관련역할",
    "unknown", "tbd", "notprovided",
)
_DEPENDENCY_FIELD = {
    "PERSONAL_DATA": "personalDataUsage",
    "PHYSICAL_ACTIVITY": "physicalActivities",
    "BUSINESS_PARTNER": "partnerRequirements",
}
_PERSONAL_REQUIRED = (
    "회원계정", "이메일", "전화번호", "연락처", "배송주소", "예약자", "사용자프로필",
    "고객별기록", "고객식별자", "고객연락", "위치정보", "답변데이터",
)
_PERSONAL_ABSENT = (
    "로그인없음", "익명사용", "익명으로만", "개인식별정보저장안함",
    "개인정보처리하지않", "로컬처리만",
)
_PERSONAL_AMBIGUOUS = ("개인화", "추천", "ai분석", "사용자설정", "맞춤형피드백")
_PHYSICAL_REQUIRED = (
    "배송", "포장", "운송", "픽업", "수거", "회수", "현장", "대면", "방문", "보관",
    "조립", "설치", "제조", "조리", "정기점검", "수리",
)
_PHYSICAL_ABSENT = ("물리적이행없음", "순수디지털", "온라인으로만", "비대면디지털")
_DIGITAL_ONLY = ("ai분석", "온라인피드백", "웹상호작용", "디지털제공", "소프트웨어", "api")
_PARTNER_REQUIRED = (
    "외부공급업체", "계약된제휴업체", "전문가파트너", "배송파트너", "장비공급사",
    "운영위탁사", "제휴전문가", "파트너네트워크", "물류서비스제공업체", "제조사와의계약",
)
_PARTNER_ABSENT = (
    "외부사업파트너를사용하지않음", "외부파트너없음", "운영사가모든서비스를직접제공",
    "별도운영파트너없음",
)


def _norm(value: Any) -> str:
    return re.sub(r"[^0-9a-z가-힣]+", "", str(value or "").casefold())


def _substantive(value: Any) -> bool:
    if isinstance(value, list):
        return any(_substantive(item) for item in value)
    normalized = _norm(value)
    return bool(normalized and normalized not in _EMPTY_MARKERS and not is_governance_placeholder(str(value)))


def classify_fact_presence(value: Any, field: str | None = None) -> FactPresence:
    """역할의 존재, 명시적 부재, 미정, 공백을 서로 다른 사업 사실로 분류한다."""
    if value is None or value == "" or value == []:
        return "EMPTY"
    normalized = _norm(value)
    if not normalized:
        return "EMPTY"
    if is_governance_placeholder(str(value)) or any(marker in normalized for marker in _UNKNOWN_MARKERS):
        return "UNKNOWN"
    if any(marker in normalized for marker in _EXPLICIT_ABSENCE_MARKERS):
        return "EXPLICIT_ABSENCE"
    return "PRESENT"


_ROLE_ACTIONS = {
    "platformRole": ("플랫폼", "운영사", "운영", "거래 기준", "고객 접점"),
    "providerRole": ("제공", "이행", "공급", "수행", "전문가", "제휴사", "파트너"),
    "sellerRole": ("판매", "계약", "수취", "판매자", "계약 책임"),
    "intermediaryRole": ("중개", "매칭", "거래 연결", "판매자와 구매자", "수요자와 제공자"),
}
_CLEARLY_OTHER_ROLE = {
    "providerRole": ("결제 정산", "대금 정산", "광고 집행"),
    "sellerRole": ("앱 화면", "ui 관리", "화면 운영"),
    "intermediaryRole": ("배송 담당", "배송 업무", "배달 수행", "물류 이행"),
}


def assess_role_semantics(field: str, value: Any) -> RoleSemanticStatus:
    """역할 값의 존재와 별도로, 해당 필드가 실제 역할 질문에 답하는지 검사한다."""
    text = str(value or "").strip()
    normalized = _norm(text)
    presence = classify_fact_presence(value, field)
    if presence in {"EMPTY", "UNKNOWN"}:
        return "AMBIGUOUS"

    actions = _ROLE_ACTIONS[field]
    action_present = any(_norm(marker) in normalized for marker in actions)
    # "직접 제공하지 않고 제휴 전문가가 제공"은 provider 부재가 아니라 제공 주체의 위임이다.
    delegated_provider = (field == "providerRole" and any(marker in normalized for marker in
        ("제휴", "파트너", "전문가", "판매자")) and any(marker in normalized for marker in ("제공", "이행", "수행")))
    if delegated_provider:
        return "MATCH"
    if presence == "EXPLICIT_ABSENCE":
        return "EXPLICIT_ABSENCE"
    if action_present:
        return "MATCH"
    if any(_norm(marker) in normalized for marker in _CLEARLY_OTHER_ROLE.get(field, ())):
        return "MISMATCH"
    return "AMBIGUOUS"


def _role_contextually_complete(candidate: ConceptCandidateResult, field: str) -> bool:
    transaction = _norm(candidate.transactionFlow)
    roles = _norm(candidate.actorRoles)
    if field == "intermediaryRole":
        return ("직접" in transaction or "운영사" in transaction) and "중개" not in transaction
    if field == "providerRole":
        return any(marker in transaction + roles for marker in
                   ("운영사제공", "파트너제공", "전문가제공", "판매자이행"))
    return False


def _contains(candidate: ConceptCandidateResult, markers: tuple[str, ...]) -> bool:
    text = _norm(" ".join(str(getattr(candidate, field)) for field in (
        "conceptDefinition", "solutionMechanism", "featureSet", "actorRoles", "transactionFlow",
        "paymentFlow", "operatingModel", "partnerModel",
    )))
    return any(_norm(marker) in text for marker in markers)


def _dependency_text(candidate: ConceptCandidateResult) -> str:
    fields = (
        "conceptDefinition", "solutionMechanism", "actorRoles", "platformRole", "providerRole",
        "sellerRole", "intermediaryRole", "transactionFlow", "paymentFlow", "personalDataUsage",
        "physicalActivities", "partnerRequirements", "operatingModel", "partnerModel", "channels",
    )
    return _norm(" ".join(str(getattr(candidate, field)) for field in fields))


def _has_any(text: str, markers: tuple[str, ...]) -> bool:
    return any(_norm(marker) in text for marker in markers)


def _dependency_fact_is_substantive(candidate: ConceptCandidateResult,
                                    dependency_type: LegalFactDependencyType) -> bool:
    field = _DEPENDENCY_FIELD[dependency_type]
    value = getattr(candidate, field)
    text = _norm(value)
    if not _substantive(value):
        return False
    if dependency_type == "PERSONAL_DATA":
        return not _has_any(text, _PERSONAL_ABSENT)
    if dependency_type == "PHYSICAL_ACTIVITY":
        return _has_any(text, _PHYSICAL_REQUIRED) and not _has_any(text, _PHYSICAL_ABSENT)
    return (_has_any(text, ("계약", "제휴", "공급업체", "파트너", "위탁", "협력", "제조사", "물류"))
            and not _has_any(text, _PARTNER_ABSENT))


def assess_legal_fact_dependency(
    candidate: ConceptCandidateResult,
    dependency_type: LegalFactDependencyType,
    descriptor: CanonicalConceptDescriptor | None = None,
    semantic_item: LegalFactDependencySemanticItem | dict[str, Any] | None = None,
) -> LegalFactDependencyAssessment:
    """법률 적용 여부가 아니라 Candidate 사업구조의 fact dependency만 판정한다."""
    text = _dependency_text(candidate)
    field_text = _norm(getattr(candidate, _DEPENDENCY_FIELD[dependency_type]))
    architecture = descriptor.architecture if descriptor else None

    if dependency_type == "PERSONAL_DATA":
        if _has_any(field_text + text, _PERSONAL_ABSENT):
            deterministic = "NOT_REQUIRED"
            reason = "개인 식별정보를 처리하지 않는 구조가 명시되었습니다."
        elif _dependency_fact_is_substantive(candidate, dependency_type) or _has_any(text, _PERSONAL_REQUIRED):
            deterministic = "REQUIRED"
            reason = "개인 단위 정보의 처리 항목 또는 목적이 명시되었습니다."
        elif architecture and architecture.dataDependency == "NONE" and not _has_any(text, _PERSONAL_AMBIGUOUS):
            deterministic = "NOT_REQUIRED"
            reason = "데이터 비의존 구조이며 개인 단위 처리 신호가 없습니다."
        else:
            deterministic = "AMBIGUOUS"
            reason = "개인화·추천 또는 일반 데이터 표현만으로 개인정보 dependency를 확정할 수 없습니다."
    elif dependency_type == "PHYSICAL_ACTIVITY":
        if _has_any(field_text + text, _PHYSICAL_ABSENT):
            deterministic = "NOT_REQUIRED"
            reason = "물리적 이행이 없는 디지털 구조가 명시되었습니다."
        elif _dependency_fact_is_substantive(candidate, dependency_type) or _has_any(text, _PHYSICAL_REQUIRED):
            deterministic = "REQUIRED"
            reason = "배송·방문·설치 등 물리적 이행이 명시되었습니다."
        elif _has_any(text, _DIGITAL_ONLY) or (architecture and architecture.physicalDependency == "NONE"):
            deterministic = "NOT_REQUIRED"
            reason = "확인된 이행은 순수 디지털이며 물리 활동 신호가 없습니다."
        else:
            deterministic = "AMBIGUOUS"
            reason = "사업 이행이 물리 활동을 포함하는지 추가 의미 판정이 필요합니다."
    else:
        p2p_only = bool(architecture and architecture.businessRole in {"MARKETPLACE", "INTERMEDIARY"}
                        and architecture.operatingModel == "PEER_TO_PEER")
        if _has_any(field_text + text, _PARTNER_ABSENT):
            deterministic = "NOT_REQUIRED"
            reason = "별도 사업 파트너가 없는 직접 운영 구조가 명시되었습니다."
        elif _dependency_fact_is_substantive(candidate, dependency_type) or _has_any(text, _PARTNER_REQUIRED):
            deterministic = "REQUIRED"
            reason = "사업 이행에 필요한 외부 계약·운영 파트너가 명시되었습니다."
        elif p2p_only and not _has_any(text, _PARTNER_REQUIRED):
            deterministic = "NOT_REQUIRED"
            reason = "판매자·구매자는 P2P 참가자이며 별도 사업 파트너 구조가 없습니다."
        elif architecture and architecture.partnerModel == "OWN_OPERATED" and not _has_any(text, _PARTNER_REQUIRED):
            deterministic = "NOT_REQUIRED"
            reason = "직접 운영 구조이며 외부 사업 파트너 신호가 없습니다."
        else:
            deterministic = "AMBIGUOUS"
            reason = "외부 참여자와 계약·운영 파트너를 구분하기 위한 의미 판정이 필요합니다."

    if isinstance(semantic_item, LegalFactDependencySemanticItem):
        semantic_decision, semantic_reason = semantic_item.decision, semantic_item.safeReason
    elif isinstance(semantic_item, dict):
        semantic_decision = str(semantic_item.get("decision", "UNKNOWN"))
        semantic_reason = str(semantic_item.get("safeReason", "dependency 의미 판정 근거가 없습니다."))
    else:
        semantic_decision, semantic_reason = "NOT_RUN", reason
    final = deterministic if deterministic != "AMBIGUOUS" else (
        semantic_decision if semantic_decision != "NOT_RUN" else "UNKNOWN")

    consistency = "NOT_ENOUGH_EVIDENCE"
    if architecture:
        if (dependency_type == "PHYSICAL_ACTIVITY" and final == "NOT_REQUIRED"
                and (architecture.physicalDependency in {"MATERIAL", "CORE"}
                     or architecture.deliveryModel in {"PHYSICAL_DELIVERY", "ON_SITE", "PICKUP"})):
            consistency = "POTENTIAL_CONFLICT"
        elif (dependency_type == "PERSONAL_DATA" and final == "NOT_REQUIRED"
              and architecture.dataDependency in {"MATERIAL", "CORE"}):
            consistency = "POTENTIAL_CONFLICT"
        elif (dependency_type == "BUSINESS_PARTNER" and final == "NOT_REQUIRED"
              and architecture.partnerModel in {"PARTNER_NETWORK", "EXPERT_NETWORK"}):
            consistency = "POTENTIAL_CONFLICT"
        elif final in {"REQUIRED", "NOT_REQUIRED"}:
            consistency = "CONSISTENT"

    return LegalFactDependencyAssessment(
        dependencyType=dependency_type, deterministicDecision=deterministic,
        semanticUsed=deterministic == "AMBIGUOUS" and semantic_item is not None,
        semanticDecision=semantic_decision, finalDecision=final,
        safeReason=semantic_reason if deterministic == "AMBIGUOUS" else reason,
        consistencyStatus=consistency)


def assess_legal_fact_dependencies(
    candidate: ConceptCandidateResult,
    descriptor: CanonicalConceptDescriptor | None = None,
    semantic_decisions: dict[str, LegalFactDependencySemanticItem | dict[str, Any]] | None = None,
) -> list[LegalFactDependencyAssessment]:
    return [assess_legal_fact_dependency(candidate, dependency_type, descriptor,
        (semantic_decisions or {}).get(dependency_type)) for dependency_type in
        ("PERSONAL_DATA", "PHYSICAL_ACTIVITY", "BUSINESS_PARTNER")]


def assess_legal_fact_completeness(
    candidate: ConceptCandidateResult,
    semantic_decisions: dict[str, BusinessRoleSemanticItem | dict[str, Any]] | None = None,
    dependency_semantic_decisions: dict[str, LegalFactDependencySemanticItem | dict[str, Any]] | None = None,
    descriptor: CanonicalConceptDescriptor | None = None,
) -> LegalFactCompletenessResult:
    """도메인 법률지식 없이 역할·거래·이행·데이터·파트너 구조만 검사한다."""
    missing: list[str] = []
    contradictions: list[str] = []
    affected: list[str] = []
    role_semantics: list[dict[str, Any]] = []
    structured: list[LegalFactCompletionRequirement] = []

    def add_requirement(field: str, reason_type: str, instruction: str,
                        dependency_type: LegalFactDependencyType | None = None):
        missing.append(instruction)
        affected.append(field)
        structured.append(LegalFactCompletionRequirement(
            field=field, reasonType=reason_type, dependencyType=dependency_type,
            instruction=instruction))

    for field in _ROLE_FIELDS:
        value = getattr(candidate, field)
        presence = classify_fact_presence(value, field)
        deterministic = assess_role_semantics(field, value)
        semantic_item = (semantic_decisions or {}).get(field)
        if isinstance(semantic_item, BusinessRoleSemanticItem):
            semantic_status = semantic_item.decision
            semantic_reason = semantic_item.safeReason
        elif isinstance(semantic_item, dict):
            semantic_status = str(semantic_item.get("decision", "UNKNOWN"))
            semantic_reason = str(semantic_item.get("safeReason", "의미 판정 근거가 없습니다."))
        else:
            semantic_status = "NOT_RUN"
            semantic_reason = "deterministic 판정이 불명확하여 semantic 판정이 필요합니다."
        final_status = deterministic if deterministic != "AMBIGUOUS" else semantic_status
        if final_status == "NOT_RUN":
            final_status = "AMBIGUOUS"
        reason = ("역할 의미가 필드와 일치합니다." if final_status == "MATCH" else
                  "역할이 명시적으로 존재하지 않습니다." if final_status == "EXPLICIT_ABSENCE" else
                  "다른 역할의 설명이 들어 있어 해당 역할을 다시 명시해야 합니다." if final_status == "MISMATCH" else
                  semantic_reason)
        role_semantics.append({
            "field": field,
            "status": final_status,
            "presence": presence,
            "deterministicStatus": deterministic,
            "semanticUsed": deterministic == "AMBIGUOUS" and semantic_item is not None,
            "semanticStatus": semantic_status,
            "finalStatus": final_status,
            "safeReason": reason,
        })
        if final_status == "AMBIGUOUS":
            if presence == "UNKNOWN" or not _role_contextually_complete(candidate, field):
                add_requirement(field, "ROLE_MISMATCH",
                    f"{field}의 역할 존재·부재와 해당 책임을 의미에 맞게 명시해야 합니다.")
        elif final_status not in {"MATCH", "EXPLICIT_ABSENCE"}:
            add_requirement(field, "ROLE_MISMATCH",
                f"{field}의 역할 존재·부재와 해당 책임을 의미에 맞게 명시해야 합니다.")
    if not _substantive(candidate.transactionFlow):
        add_requirement("transactionFlow", "TRANSACTION_INCOMPLETE",
                        "주문·계약·제공 주체를 포함한 transactionFlow가 필요합니다.")
    if not _substantive(candidate.paymentFlow):
        add_requirement("paymentFlow", "PAYMENT_INCOMPLETE",
                        "결제 수취 및 정산 주체를 포함한 paymentFlow가 필요합니다.")
    for field, requirement in (
        ("targetRegion", "검토 가정으로 사용할 서비스 대상 국가·지역을 targetRegion에 명시해야 합니다."),
        ("channels", "고객 접점과 판매·제공 채널을 channels에 명시해야 합니다."),
    ):
        if not _substantive(getattr(candidate, field)):
            add_requirement(field, "GENERAL_FACT_INCOMPLETE", requirement)
    # 가격은 architecture fact가 아니라 선택 후 확정할 수 있는 Hypothesis다. 미정이면 handoff gate가 차단한다.

    dependencies = assess_legal_fact_dependencies(
        candidate, descriptor, dependency_semantic_decisions)
    dependency_instruction = {
        "PERSONAL_DATA": "실제 처리하는 개인 단위 정보와 처리 목적을 personalDataUsage에 명시해야 합니다.",
        "PHYSICAL_ACTIVITY": "물리적 이행 활동과 수행 주체를 physicalActivities에 명시해야 합니다.",
        "BUSINESS_PARTNER": "외부 사업 파트너의 주체와 역할을 partnerRequirements에 명시해야 합니다.",
    }
    unknown_instruction = {
        "PERSONAL_DATA": "사업 설계를 명확히 하여 개인정보 dependency의 존재·부재를 확정해야 합니다.",
        "PHYSICAL_ACTIVITY": "사업 설계를 명확히 하여 물리 활동 dependency의 존재·부재를 확정해야 합니다.",
        "BUSINESS_PARTNER": "사업 설계를 명확히 하여 외부 사업 파트너 dependency의 존재·부재를 확정해야 합니다.",
    }
    for dependency in dependencies:
        field = _DEPENDENCY_FIELD[dependency.dependencyType]
        if dependency.finalDecision == "REQUIRED" and not _dependency_fact_is_substantive(
                candidate, dependency.dependencyType):
            add_requirement(field, "MISSING_REQUIRED_FACT",
                            dependency_instruction[dependency.dependencyType], dependency.dependencyType)
        elif (dependency.finalDecision == "UNKNOWN"
              and (descriptor is not None or dependency_semantic_decisions is not None)):
            add_requirement(field, "DEPENDENCY_UNKNOWN",
                            unknown_instruction[dependency.dependencyType], dependency.dependencyType)

    present_role_text = " ".join(getattr(candidate, field) for field in _ROLE_FIELDS
                                 if classify_fact_presence(getattr(candidate, field), field) == "PRESENT")
    direct = any(marker in present_role_text for marker in ("직접 판매", "직접 제공", "직접 수취"))
    intermediary = (classify_fact_presence(candidate.intermediaryRole, "intermediaryRole") == "PRESENT"
                    and any(marker in candidate.intermediaryRole for marker in ("중개", "연결", "매칭")))
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
                "SEMANTIC_REQUIRED": "deterministic 역할 판정이 불명확하여 batch semantic 판정이 필요합니다.",
                "COMPLETABLE": "동일 Concept 안에서 누락된 사업 사실을 한 번 보완할 수 있습니다.",
                "INVALID": "사업 역할과 거래 구조의 중대한 모순을 먼저 재생성해야 합니다."}[status])
    return LegalFactCompletenessResult(status=status, missingDesignFacts=missing,
        contradictions=contradictions, completionRequirements=requirements,
        affectedFields=affected, roleSemantics=role_semantics,
        dependencyAssessments=dependencies, structuredCompletionRequirements=structured,
        safeSummary=summary)


def validate_legal_fact_completion(
    parent: ConceptCandidateResult,
    child: ConceptCandidateResult,
    requirements: list[LegalFactCompletionRequirement],
    child_report: LegalFactCompletenessResult,
    candidate_id: str,
) -> LegalFactCompletionCompliance:
    """전체 Candidate 품질과 별도로 요청된 fact patch의 이행만 검사한다."""
    changed = [requirement.field for requirement in requirements
               if _norm(getattr(parent, requirement.field)) != _norm(getattr(child, requirement.field))]
    changed = list(dict.fromkeys(changed))
    dependency_by_type = {item.dependencyType: item for item in child_report.dependencyAssessments}
    role_by_field = {item["field"]: item for item in child_report.roleSemantics}
    satisfied: list[str] = []
    unsatisfied: list[str] = []
    ambiguous: list[str] = []
    unchanged: list[str] = []

    for requirement in requirements:
        field = requirement.field
        field_changed = field in changed
        if requirement.dependencyType:
            assessment = dependency_by_type.get(requirement.dependencyType)
            if not assessment or assessment.finalDecision == "UNKNOWN":
                ambiguous.append(requirement.instruction)
                if not field_changed:
                    unchanged.append(field)
                continue
            if assessment.finalDecision == "NOT_REQUIRED":
                satisfied.append(requirement.instruction)
                continue
            substantive = _dependency_fact_is_substantive(child, requirement.dependencyType)
            if substantive and (field_changed or requirement.reasonType == "DEPENDENCY_UNKNOWN"):
                satisfied.append(requirement.instruction)
            else:
                unsatisfied.append(requirement.instruction)
                if not field_changed:
                    unchanged.append(field)
            continue

        if requirement.reasonType == "ROLE_MISMATCH":
            role = role_by_field.get(field, {})
            passed = field_changed and role.get("finalStatus") in {"MATCH", "EXPLICIT_ABSENCE"}
        else:
            passed = field_changed and _substantive(getattr(child, field))
        if passed:
            satisfied.append(requirement.instruction)
        else:
            unsatisfied.append(requirement.instruction)
            if not field_changed:
                unchanged.append(field)

    if unsatisfied:
        status = "FAIL"
        summary = "Completion Provider가 요청된 사업 사실을 실제로 보완하지 않았습니다."
    elif ambiguous:
        status = "AMBIGUOUS"
        summary = "Completion 이후에도 dependency의 존재·부재를 확정할 수 없습니다."
    else:
        status = "PASS"
        summary = "요청된 사업 사실만 범위 안에서 보완되었습니다."
    return LegalFactCompletionCompliance(
        candidateId=candidate_id, status=status,
        satisfiedRequirements=satisfied,
        unsatisfiedRequirements=[*unsatisfied, *ambiguous],
        changedFields=changed, unchangedRequiredFields=list(dict.fromkeys(unchanged)),
        safeSummary=summary)


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
