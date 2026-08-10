"""사용자-facing V2 사업 문구의 ko-KR 정책과 governance placeholder 검사."""

from __future__ import annotations

import re
from typing import Any

from app.tasks.concept_candidate.models import ConceptCandidateDraft, ConceptCandidateResult

from .models import PortfolioPlanDraft


PLACEHOLDER_VALUES = {"OPEN", "LOCKED", "MISSING", "UNDECIDED", "TBD", "N/A", "NONE", "NULL"}
PLAN_FIELDS = (
    "title", "oneLineConcept", "targetSegment", "problemFocus", "useContext", "valueProposition",
    "offerThesis", "solutionThesis", "coreMechanism", "customerInteraction", "valueDelivery",
    "operatingApproach", "partnerApproach", "transactionApproach", "commercialApproach",
    "fulfillmentApproach", "reasonForPortfolioRole",
)
CANDIDATE_FIELDS = (
    "conceptName", "conceptDefinition", "introduction", "coreValue", "targetUsers",
    "industryCategory", "researchScope", "targetRegion", "revenueModel", "price", "channels",
    "differentiators", "problemScenario", "solutionMechanism", "platformRole", "operatingModel",
    "partnerModel", "providerRole", "sellerRole", "intermediaryRole",
)
CANDIDATE_LIST_FIELDS = (
    "featureSet", "actorRoles", "transactionFlow", "paymentFlow", "personalDataUsage",
    "physicalActivities", "partnerRequirements", "qualificationRequirements", "advertisingClaims",
    "constraintCompliance",
)


def is_korean_user_content(value: Any) -> bool:
    text = " ".join(str(item) for item in value) if isinstance(value, list) else str(value)
    hangul = len(re.findall(r"[가-힣]", text))
    latin = len(re.findall(r"[A-Za-z]", text))
    return hangul >= 2 and (latin == 0 or hangul / (hangul + latin) >= 0.15)


def plan_language_failures(plan: PortfolioPlanDraft) -> list[str]:
    failures = [field for field in PLAN_FIELDS if not is_korean_user_content(getattr(plan, field))]
    for field in ("differentiatingMechanics", "legalRiskHints"):
        values = getattr(plan, field)
        if values and not is_korean_user_content(values):
            failures.append(field)
    return failures


def candidate_language_failures(candidate: ConceptCandidateDraft | ConceptCandidateResult) -> list[str]:
    failures = [field for field in CANDIDATE_FIELDS if not is_korean_user_content(getattr(candidate, field))]
    for field in CANDIDATE_LIST_FIELDS:
        values = getattr(candidate, field)
        if values and not is_korean_user_content(values):
            failures.append(field)
    return failures


def is_governance_placeholder(value: Any) -> bool:
    if not isinstance(value, str):
        return False
    normalized = re.sub(r"\s+", "", value).casefold()
    if value.strip().upper() in PLACEHOLDER_VALUES:
        return True
    unresolved = (
        "정보가필요", "확인이필요", "추후확인", "검토필요", "검증필요", "미정", "결정필요",
        "명시되지않았", "제공되지않았", "미제공", "정보없음", "입력되지않았",
        "아직정해지지", "추후결정", "unknown", "notprovided",
    )
    return len(normalized) <= 80 and any(marker in normalized for marker in unresolved)
