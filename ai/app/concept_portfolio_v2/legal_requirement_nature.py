"""Legal 결과의 사실 확인 요구와 사업구조 변경 요구를 보수적으로 구분한다."""

from __future__ import annotations

from app.tasks.concept_candidate.models import ConceptCandidateResult

from .models import (
    LegalRequirementNature, LegalRequirementNatureAssessment, LegalReview, LegalRoute,
)


_QUESTION_MARKERS = (
    "확인 필요", "정보 필요", "알 수 없음", "어떤 주체", "여부 확인", "사실 확인",
    "확인해야", "알려달", "구체적인", "명확하지",
)
_CHANGE_MARKERS = (
    "제거", "변경", "전환", "대체", "분리", "제한", "금지", "귀속", "되도록",
    "직접 수취하지", "직접 판매하지", "구조로",
)
_FIELD_MARKERS = {
    "sellerRole": ("판매 주체", "판매자", "직접 판매", "판매 계약"),
    "providerRole": ("제공 주체", "서비스 제공자", "자격 보유", "자격을 보유"),
    "intermediaryRole": ("중개 역할", "중개 플랫폼", "연결·중개", "연결 중개"),
    "transactionFlow": ("거래 방식", "거래 구조", "계약 주체", "거래 주체"),
    "paymentFlow": ("결제 방식", "결제 구조", "결제 귀속", "수취"),
    "partnerRequirements": ("파트너", "공급업체", "제휴", "자격"),
    "personalDataUsage": ("개인정보", "데이터 사용"),
    "physicalActivities": ("물리 활동", "배송", "설치", "방문", "회수"),
}


def _affected_fields(text: str) -> list[str]:
    return [field for field, markers in _FIELD_MARKERS.items()
            if any(marker in text for marker in markers)]


def _before_summary(candidate: ConceptCandidateResult, fields: list[str]) -> str | None:
    values = []
    for field in fields:
        value = getattr(candidate, field, None)
        if isinstance(value, list):
            value = "; ".join(str(item) for item in value if str(item).strip())
        if value and str(value).strip():
            values.append(f"{field}={value}")
    return "; ".join(values) if values else None


def _fact_question(text: str) -> str:
    if "판매 주체" in text or "판매자" in text or "판매 계약" in text:
        return "해당 거래에서 고객과 직접 계약하는 판매 주체가 운영사입니까, 외부 판매자입니까?"
    if "자격" in text:
        return "실제 판매·제공 주체와 그 주체가 보유한 자격을 확인해 주십시오."
    if "계약" in text or "제휴" in text or "공급업체" in text:
        return "해당 파트너와 실제 계약이 체결되어 있고 필요한 권한이 포함되어 있는지 확인해 주십시오."
    return "현재 운영 계획에서 이 요구와 관련된 실제 사업 사실을 확인해 주십시오."


def classify_legal_requirement_nature(
    review: LegalReview, candidate: ConceptCandidateResult,
) -> LegalRequirementNatureAssessment:
    """법률 결론을 다시 판단하지 않고, 요구 문장의 작업 성격만 분류한다."""
    parts = [*review.redesignRequirements]
    if review.requiredLegalChange:
        parts.append(review.requiredLegalChange)
    if review.reason:
        parts.append(review.reason)
    text = " ".join(str(item).strip() for item in parts if str(item).strip())
    fields = _affected_fields(text)
    before = _before_summary(candidate, fields)
    asks_for_fact = any(marker in text for marker in _QUESTION_MARKERS)
    explicit_change = any(marker in text for marker in _CHANGE_MARKERS)

    # 구조 변경은 대상 field/mechanism, 현재 구조, 요구 구조가 함께 있어야 한다.
    if fields and before and explicit_change and not (asks_for_fact and not any(
            marker in text for marker in ("제거", "변경", "전환", "대체", "금지", "되도록"))):
        return LegalRequirementNatureAssessment(
            nature=LegalRequirementNature.STRUCTURAL_CHANGE,
            affectedFields=fields,
            beforeSummary=before,
            requiredStructure=text,
            safeReason="현재 구조와 구체적인 변경 대상 및 요구 구조가 함께 제시되었습니다.",
        )
    if asks_for_fact:
        return LegalRequirementNatureAssessment(
            nature=LegalRequirementNature.FACT_REQUIRED,
            affectedFields=fields,
            beforeSummary=before,
            factQuestion=_fact_question(text),
            safeReason="구체적인 구조 변경 없이 현재 사업 사실의 확인을 요구합니다.",
        )
    return LegalRequirementNatureAssessment(
        nature=LegalRequirementNature.AMBIGUOUS,
        affectedFields=fields,
        beforeSummary=before,
        factQuestion=_fact_question(text),
        safeReason="구체적인 구조 변경 invariant를 확인할 수 없어 사용자 사실 확인으로 보수 처리합니다.",
    )


def normalize_legal_requirement_route(
    review: LegalReview, candidate: ConceptCandidateResult,
) -> LegalReview:
    """REDESIGN/NEEDS_INPUT 경계만 정규화하고 Provider의 원본 설명은 보존한다."""
    if review.route not in {LegalRoute.REDESIGN_WITHIN_LINEAGE, LegalRoute.NEEDS_INPUT}:
        return review
    assessment = classify_legal_requirement_nature(review, candidate)
    diagnostics = {
        **review.evidenceDiagnostics,
        "legalRequirementNature": assessment.nature.value,
        "legalRequirementNatureReason": assessment.safeReason,
        "affectedFields": assessment.affectedFields,
    }
    if assessment.factQuestion:
        diagnostics["factQuestion"] = assessment.factQuestion
    if review.route == LegalRoute.NEEDS_INPUT:
        return review.model_copy(update={"evidenceDiagnostics": diagnostics})
    if assessment.nature == LegalRequirementNature.STRUCTURAL_CHANGE:
        return review.model_copy(update={"evidenceDiagnostics": diagnostics})

    unknown = list(review.unknownFacts) or list(review.redesignRequirements)
    if not unknown:
        unknown = [review.reason or review.safeSummary]
    return review.model_copy(update={
        "route": LegalRoute.NEEDS_INPUT,
        "inputScope": "CANDIDATE",
        "unknownFacts": unknown,
        "possibleUserAction": review.possibleUserAction or assessment.factQuestion,
        "recoveryResolution": "LEGAL_FACT_REQUIRED_NOT_REDESIGN",
        "evidenceDiagnostics": diagnostics,
    })
