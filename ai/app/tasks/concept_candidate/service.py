import json
from typing import Any

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_candidate.models import (
    ConceptCandidateDraft,
    ConceptCandidateInput,
    ConceptCandidateResult,
    SemanticField,
)


SYSTEM_PROMPT = """입력된 Market Seed로 서로 구별되는 하나의 Concept 후보 초안을 설계한다.
반드시 한국어 사용자 문구와 strict schema만 반환한다. 사업 내용만 생성하고 schemaVersion,
generationStrategy, candidateIndex, originalCandidate, valueSemantics, source, authority, decision은
생성하지 않는다. 이 메타데이터와 거버넌스는 시스템이 결정한다. USER_INPUT 또는
USER_CONFIRMED + LOCKED 값은 의미와 구체적 조건을 보존한다. 비어 있던 targetRegion은 현재 공식
법률검토 지원 범위인 대한민국과 호환되는 지역으로 제안한다. pre-market SOM 두 값은 실제
시장분석 결과가 아닌 사전 가설이다. AS_IS Candidate 1은 사용자 원안을 새 아이디어로 왜곡하지
않고 구조화한다. 이름이나 표현만 바꾼 finalConceptsToDifferentiateFrom과 같은 사업 구조를 만들지 않는다.
BusinessFingerprint 21개 필드는 고객 경험, 운영·파트너, 수익·가격, 채널·확장,
개인정보·물리활동·필수 파트너·자격 의존도를 비교하는 축이며 현재 diversityFocus의 primaryAxes를
우선해 실질적 차이를 만든다.
providerRole, sellerRole, intermediaryRole은 실제 거래상 역할을 명시하고 역할이 없으면 그 이유와
함께 '해당 없음'으로 쓴다. 증거 ID, 법령 문구, 최종 법률 상태, 사용자 확인 상태는 만들지 않는다."""


SYSTEM_PROMPT += """
finalConceptsToDifferentiateFrom은 최종 적격 Concept이므로 동일한 핵심 사업 작동 구조를 만들지 않는다.
currentSlotHistory는 현재 슬롯의 반복을 막는 강한 지역 문맥이다.
softNegativeExamples는 타 슬롯의 실제 거절 예시일 뿐 hard 금지 집합이 아니다.
replacementFeedback이 있으면 mustChangeDimensions 중 최소 두 축의 작동 방식을 실질적으로 바꾼다.
같은 문제, 같은 사용자, 같은 LOCKED 원본 조건 자체는 중복의 증거가 아니다.
"""


class CandidateInvariantError(ValueError):
    def __init__(self, code: str, field: str | None = None):
        self.code = code
        self.field = field
        super().__init__(f"{code}:{field}" if field else code)


VARIATION_RULES = {
    "CUSTOMER_EXPERIENCE": {
        "primaryAxes": ["problemScenario", "solutionMechanism", "featureSet", "coreValue"],
        "minimumChange": "고객의 문제 상황과 해결 작동 방식 중 최소 두 축을 기존 후보와 실질적으로 다르게 설계한다.",
    },
    "OPERATING_MODEL_AND_PARTNERS": {
        "primaryAxes": ["actorRoles", "operatingModel", "partnerModel", "providerRole",
                        "sellerRole", "intermediaryRole", "transactionFlow"],
        "minimumChange": "운영 주체, 파트너 관계, 거래 역할 중 최소 두 축을 기존 후보와 실질적으로 다르게 설계한다.",
    },
    "REVENUE_AND_PRICING": {
        "primaryAxes": ["revenueModel", "price", "paymentFlow"],
        "minimumChange": "수익 발생 주체와 과금 단위 또는 결제 흐름을 기존 후보와 실질적으로 다르게 설계한다.",
    },
    "CHANNEL_AND_SCALE": {
        "primaryAxes": ["channels", "platformRole", "transactionFlow", "operatingModel"],
        "minimumChange": "획득 채널과 유통·확장 메커니즘 중 최소 두 축을 기존 후보와 실질적으로 다르게 설계한다.",
    },
    "LOW_RISK_FAST_EXECUTION": {
        "primaryAxes": ["personalDataUsage", "physicalActivities", "partnerRequirements",
                        "qualificationRequirements", "operatingModel"],
        "minimumChange": "개인정보·물리활동·필수 파트너 의존을 줄이고 단기 실행 가능한 운영 구조를 명시한다.",
    },
}

HYPOTHESIS_FIELDS = {
    "targetRegion", "revenueModel", "price", "channels", "differentiators",
    "preMarketSomShareHypothesis", "preMarketSomHypothesis",
}
DIRECT_LOCK_FIELDS = {"targetRegion", "revenueModel", "price", "channels", "differentiators"}
AS_IS_DIRECT_FIELDS = {
    "conceptDefinition": "ideaOverview",
    "problemScenario": "problem",
    "targetUsers": "targetUsers",
}
CONSTRAINT_FIELDS = {"budgetConstraint", "teamConstraint", "timelineConstraint", "otherConstraint"}


async def execute_concept_candidate(task_input: dict) -> dict:
    try:
        value = ConceptCandidateInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure(
            "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
            validation_fields=_validation_fields(failure, "input"),
        ) from failure

    provider_input = value.model_dump(mode="json")
    provider_input["variationRule"] = VARIATION_RULES[value.diversityFocus]
    provider_input["finalConceptsToDifferentiateFrom"] = [
        item.model_dump(mode="json") for item in value.acceptedConceptFingerprints
    ]
    provider_input["currentSlotHistory"] = [
        item.model_dump(mode="json") for item in value.currentSlotPreviousFingerprints
    ]
    provider_input["softNegativeExamples"] = [
        item.model_dump(mode="json") for item in value.rejectedConceptFingerprints
    ]
    provider_input["replacementFeedback"] = (
        value.replacementContext.model_dump(mode="json") if value.replacementContext else None
    )
    for key in ("acceptedConceptFingerprints", "rejectedConceptFingerprints",
                "currentSlotPreviousFingerprints"):
        provider_input.pop(key, None)

    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(provider_input, ensure_ascii=False, sort_keys=True),
        response_schema=ConceptCandidateDraft.model_json_schema(),
        schema_name="concept_candidate_draft_v1", task_type="CONCEPT_CANDIDATE",
    )
    draft = await _validated_draft(raw, value)
    try:
        normalized = _normalize_candidate(value, draft)
        return ConceptCandidateResult.model_validate(normalized).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "PYDANTIC_RESULT_VALIDATION_FAILED", 502, False,
            schema_name="concept_candidate_v2",
            validation_fields=_validation_fields(failure, "result"),
        ) from failure
    except CandidateInvariantError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", str(failure), 502, False,
                              schema_name="concept_candidate_v2") from failure
    except ValueError as failure:
        reason = str(failure)
        diagnostic = reason if reason in {
            "LEGAL_JURISDICTION_UNSUPPORTED", "CONTENT_FIELD_MISSING",
            "CANDIDATE_METADATA_INVALID", "GOVERNANCE_SEMANTICS_MISMATCH",
        } else "RESULT_DOMAIN_INVARIANT_VIOLATION"
        raise ProviderFailure("RESULT_SCHEMA_INVALID", diagnostic, 502, False,
                              schema_name="concept_candidate_v2") from failure


async def _validated_draft(raw: dict[str, Any], value: ConceptCandidateInput) -> ConceptCandidateDraft:
    try:
        return ConceptCandidateDraft.model_validate(raw)
    except ValidationError as first_failure:
        fields = _validation_fields(first_failure, "draft")
        repair_input = {
            "previousCandidate": raw,
            "failureCode": "CONTENT_FIELD_MISSING",
            "failedFields": fields,
            "requiredCorrection": "누락되거나 계약에 맞지 않는 사업 내용 필드만 보완하고 완전한 초안을 반환한다.",
            "generationStrategy": value.generationStrategy,
            "candidateIndex": value.candidateIndex,
            "diversityFocus": value.diversityFocus,
            "variationRule": VARIATION_RULES[value.diversityFocus],
            "lockedConstraints": _locked_constraints(value),
        }
        repaired = await execute_structured_prompt(
            SYSTEM_PROMPT + "\n이 호출은 이전 초안의 실제 content 오류를 고치는 1회 한정 repair다.",
            json.dumps(repair_input, ensure_ascii=False, sort_keys=True),
            response_schema=ConceptCandidateDraft.model_json_schema(),
            schema_name="concept_candidate_draft_repair_v1", task_type="CONCEPT_CANDIDATE",
        )
        try:
            return ConceptCandidateDraft.model_validate(repaired)
        except ValidationError as repaired_failure:
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "CONTENT_FIELD_MISSING", 502, False,
                schema_name="concept_candidate_draft_v1",
                validation_fields=_validation_fields(repaired_failure, "draft"),
            ) from repaired_failure


def _normalize_candidate(value: ConceptCandidateInput, draft: ConceptCandidateDraft) -> dict[str, Any]:
    result = draft.model_dump(mode="json")
    seed = {field.fieldKey: field for field in value.fields}

    for field in DIRECT_LOCK_FIELDS:
        locked = seed.get(field)
        if locked is not None and locked.authority == "LOCKED":
            if locked.source not in {"USER_INPUT", "USER_CONFIRMED"}:
                raise CandidateInvariantError("GOVERNANCE_SEMANTICS_MISMATCH", field)
            result[field] = locked.value

    if value.generationStrategy == "AS_IS" and value.candidateIndex == 1:
        for candidate_field, seed_field in AS_IS_DIRECT_FIELDS.items():
            locked = seed.get(seed_field)
            if locked is None or locked.authority != "LOCKED":
                raise CandidateInvariantError("CANDIDATE_METADATA_INVALID", candidate_field)
            result[candidate_field] = locked.value

    if not _kr_compatible(str(result.get("targetRegion", ""))):
        raise CandidateInvariantError("LEGAL_JURISDICTION_UNSUPPORTED", "targetRegion")

    compliance = list(result.get("constraintCompliance") or [])
    for field in value.fields:
        if field.fieldKey in CONSTRAINT_FIELDS and field.authority == "LOCKED":
            if not any(_same_or_contains(field.value, item) for item in compliance):
                compliance.append(f"확정 제약 준수: {field.value}")
    result["constraintCompliance"] = compliance

    semantics = []
    for field in SemanticField.__args__:
        source, authority, decision = "CONCEPT_GENERATED", "OPEN", "PROPOSED"
        seed_field = seed.get(field)
        if field in DIRECT_LOCK_FIELDS and seed_field is not None and seed_field.authority == "LOCKED":
            source, authority, decision = seed_field.source, "LOCKED", "ACCEPTED"
        elif field in HYPOTHESIS_FIELDS:
            source, authority, decision = "AI_HYPOTHESIS", "OPEN", "PROPOSED"
        elif value.generationStrategy == "AS_IS" and value.candidateIndex == 1 \
                and field in AS_IS_DIRECT_FIELDS:
            original = seed[AS_IS_DIRECT_FIELDS[field]]
            source, authority, decision = original.source, "LOCKED", "ACCEPTED"
        semantics.append({"fieldKey": field, "source": source,
                          "authority": authority, "decision": decision})

    result.update({
        "schemaVersion": "2.0",
        "generationStrategy": value.generationStrategy,
        "candidateIndex": value.candidateIndex,
        "originalCandidate": value.generationStrategy == "AS_IS" and value.candidateIndex == 1,
        "valueSemantics": semantics,
    })
    return result


def normalize_redesign(original: ConceptCandidateResult, draft: ConceptCandidateDraft) -> dict[str, Any]:
    """Preserve system metadata and every authoritative value while accepting redesigned content."""
    result = draft.model_dump(mode="json")
    original_payload = original.model_dump(mode="json")
    semantics = {item.fieldKey: item.model_dump(mode="json") for item in original.valueSemantics}
    for field, semantic in semantics.items():
        if semantic["authority"] == "LOCKED":
            result[field] = original_payload[field]
        elif field in HYPOTHESIS_FIELDS:
            semantic.update(source="AI_HYPOTHESIS", authority="OPEN", decision="PROPOSED")
        else:
            semantic.update(source="CONCEPT_GENERATED", authority="OPEN", decision="PROPOSED")
    result.update({
        "schemaVersion": "2.0",
        "generationStrategy": original.generationStrategy,
        "candidateIndex": original.candidateIndex,
        "originalCandidate": original.originalCandidate,
        "valueSemantics": [semantics[field] for field in SemanticField.__args__],
    })
    return result


def _deduplicated_avoid_candidates(value: ConceptCandidateInput) -> list[dict[str, Any]]:
    unique: dict[str, dict[str, Any]] = {}
    for item in (*value.acceptedConceptFingerprints, *value.rejectedConceptFingerprints,
                 *value.currentSlotPreviousFingerprints):
        payload = item.model_dump(mode="json")
        key = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        unique.setdefault(key, payload)
    return list(unique.values())


def _locked_constraints(value: ConceptCandidateInput) -> list[dict[str, str]]:
    return [field.model_dump(mode="json") for field in value.fields if field.authority == "LOCKED"]


def _validation_fields(failure: ValidationError, prefix: str) -> list[dict[str, str]]:
    fields = []
    for issue in failure.errors()[:12]:
        location = ".".join(str(part) for part in issue.get("loc", ()))
        fields.append({
            "path": f"{prefix}.{location}" if location else prefix,
            "category": str(issue.get("type", "invalid"))[:80],
            "expectedType": "valid contract value",
        })
    return fields


def _same_or_contains(expected: str, actual: str) -> bool:
    first = " ".join(expected.casefold().split())
    second = " ".join(str(actual).casefold().split())
    return first == second or first in second


def _kr_compatible(value: str) -> bool:
    normalized = " ".join(value.casefold().split())
    foreign = ("미국", "일본", "중국", "캐나다", "호주", "영국", "유럽", "해외", "global",
               "usa", "united states", "japan", "china")
    if any(marker in normalized for marker in foreign):
        return False
    return normalized in {"kr", "kor"} or any(marker in normalized for marker in (
        "대한민국", "한국", "국내", "전국", "서울", "부산", "인천", "대구", "대전", "광주",
        "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
        "republic of korea", "south korea"))
