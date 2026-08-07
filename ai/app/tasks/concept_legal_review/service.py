import json
from datetime import date

from pydantic import ValidationError

from app.legal.pipeline import execute_legal_source_pipeline
from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_legal_review.models import (
    ConceptLegalReviewDomainResult, ConceptLegalReviewInput, ConceptLegalReviewProviderResult,
    OfficialEvidence,
)


SYSTEM_PROMPT = """Perform an official-evidence-based legal implementation feasibility pre-review.
Use only supplied evidence reference indexes. Do not invent statutes, provisions, facts, or citations.
Each material finding is an object with text and its own evidenceReferenceIndexes. Do not return parallel
coverage arrays or derived unions. IMPLEMENTABLE findings must cite at least one supplied official reference.
This is not legal advice.
Return only the strict schema."""

REVIEW_LABEL = "공식 근거 기반 법률 구현 가능성 사전검토"
REVIEW_LIMITATIONS = "공식 법령의 제한된 조문과 확인 시점을 기준으로 한 사전검토이며, 구체적 사실관계와 최신 시행 상태에 대한 전문가 확인이 필요할 수 있습니다."


def _source_text(value: ConceptLegalReviewInput) -> str:
    context = {field.fieldKey: field.value for field in value.sharedContext.fields}
    candidate = value.candidate
    activities = {
        "canonicalContext": context,
        "candidateActivities": {
            "actorRoles": candidate.actorRoles,
            "platformRole": candidate.platformRole,
            "transactionFlow": candidate.transactionFlow,
            "dataFlow": candidate.dataFlow,
            "physicalActivities": candidate.physicalActivities,
            "partnerRequirements": candidate.partnerRequirements,
            "channelHypothesis": candidate.channelHypothesis,
            "pricingHypothesis": candidate.pricingHypothesis,
            "operatingModel": candidate.operatingModel,
        },
    }
    return json.dumps(activities, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _official_evidence(source: dict) -> list[OfficialEvidence]:
    deduplicated: dict[tuple[str, str, str, str], OfficialEvidence] = {}
    for item in source["evidence"]:
        evidence = OfficialEvidence(
            referenceIndex=0,
            sourceType="OFFICIAL_LAW",
            lawId=item.get("lawId"),
            officialIdentifier=item["officialIdentifier"],
            lawName=item["lawName"],
            articleReference=item["articleReference"],
            title=item.get("title") or "",
            officialSourceUri=item["officialSourceUri"],
            jurisdiction="KR",
            promulgationDate=item.get("promulgationDate"),
            effectiveDate=item.get("effectiveDate"),
            retrievedAt=item["retrievedAt"],
            contentHash=item["contentHash"],
            boundedProvisionSummary=item["plainSummary"],
            queryKey=item["queryKey"],
            registryVersion=item["registryVersion"],
        )
        key = (evidence.officialIdentifier, evidence.articleReference,
               evidence.effectiveDate or "", evidence.contentHash)
        deduplicated.setdefault(key, evidence)
    return [item.model_copy(update={"referenceIndex": index})
            for index, item in enumerate(deduplicated.values())]


def _validate_coverage(provider: ConceptLegalReviewProviderResult,
                       evidence: list[OfficialEvidence]) -> tuple[dict[str, list[str]], list[dict], list[int]]:
    indexes = {item.referenceIndex for item in evidence}
    fields = ("requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures", "prohibitedVariants")
    strings: dict[str, list[str]] = {}
    coverage: list[dict] = []
    cited: set[int] = set()
    for field in fields:
        findings = getattr(provider, field)
        strings[field] = [finding.text for finding in findings]
        for finding_index, finding in enumerate(findings):
            refs = finding.evidenceReferenceIndexes
            if len(refs) != len(set(refs)) or any(index not in indexes for index in refs):
                raise ProviderFailure("RESULT_SCHEMA_INVALID", "EVIDENCE_REFERENCE_INVALID", 502, False)
            if provider.status in {"IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS"} and not refs:
                raise ProviderFailure("RESULT_SCHEMA_INVALID", "CONCEPT_LEGAL_FINDING_EVIDENCE_REQUIRED", 502, False)
            cited.update(refs)
            coverage.append({"findingType": field, "findingIndex": finding_index,
                "evidenceReferenceIndexes": refs})
    if provider.status in {"IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS"} and not cited:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "CONCEPT_LEGAL_EVIDENCE_REQUIRED", 502, False)
    return strings, coverage, sorted(cited)


def _needs_facts(source: dict, evidence: list[OfficialEvidence]) -> dict:
    missing = [item.get("question", "추가 사업 사실을 확인해야 합니다.")
               for item in source.get("requiredUserInputs", [])][:30]
    if not missing:
        missing = ["적용 가능한 공식 조문 근거를 특정하기 위한 사업 활동 정보가 부족합니다."]
    return ConceptLegalReviewDomainResult(
        status="NEEDS_FACTS", reviewedActivities=[], requiredControls=[],
        requiredPartnersAndQualifications=[], requiredDisclosures=[], prohibitedVariants=[],
        unknownFacts=missing, findingEvidence=[], officialEvidence=evidence,
        evidenceReferenceIndexes=[], expertReviewRecommended=True, reviewBasisDate=date.today(),
        safeUserSummary="공식 근거를 충분히 특정할 수 없어 추가 사실 확인이 필요합니다.",
        reviewLabel=REVIEW_LABEL, reviewLimitations=REVIEW_LIMITATIONS,
    ).model_dump(mode="json")


async def execute_concept_legal_review(task_input: dict) -> dict:
    try:
        value = ConceptLegalReviewInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    if value.sharedOfficialEvidence:
        evidence = [item.model_copy(update={"referenceIndex": index})
                    for index, item in enumerate(value.sharedOfficialEvidence)]
        source = {"requiredUserInputs": []}
    else:
        source = await execute_legal_source_pipeline("CONCEPT_LEGAL_VALIDATION", _source_text(value), {
            "mode": "FULL", "rerunCategories": [], "confirmedFacts": [],
            "registryVersion": value.sharedContext.registryVersion,
        })
        evidence = _official_evidence(source)
    if not evidence:
        return _needs_facts(source, evidence)
    provider_input = {
        "candidate": value.candidate.model_dump(mode="json"),
        "canonicalContext": [field.model_dump(mode="json") for field in value.sharedContext.fields],
        "officialEvidence": [item.model_dump(mode="json") for item in evidence],
    }
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(provider_input, ensure_ascii=False, sort_keys=True, default=str),
        response_schema=ConceptLegalReviewProviderResult.model_json_schema(),
        schema_name="concept_legal_review_v2", task_type="CONCEPT_LEGAL_REVIEW",
    )
    try:
        provider = ConceptLegalReviewProviderResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    finding_strings, finding_evidence, evidence_union = _validate_coverage(provider, evidence)
    provider_values = provider.model_dump(exclude={
        "requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures", "prohibitedVariants",
    })
    return ConceptLegalReviewDomainResult(
        **provider_values,
        **finding_strings,
        findingEvidence=finding_evidence,
        officialEvidence=evidence,
        evidenceReferenceIndexes=evidence_union,
        reviewLabel=REVIEW_LABEL,
        reviewLimitations=REVIEW_LIMITATIONS,
    ).model_dump(mode="json")
