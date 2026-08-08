import json
from datetime import date

from pydantic import ValidationError

from app.legal.pipeline import execute_legal_source_pipeline
from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_legal_review.models import (
    ConceptLegalReviewDomainResult, ConceptLegalReviewInput, ConceptLegalReviewProviderResult,
    OfficialEvidence,
)


SYSTEM_PROMPT = """정의된 Concept Legal Fact Pattern과 공식 법령 근거만으로 법률 구현 가능성을 사전검토한다.
질문은 '이 구조와 통제조건대로 구현하면 법적으로 구현 가능한가'이다. 현재 사업자가 모든 조건을
이미 충족했다고 추정하지 않는다. supplied evidence reference index만 사용하고 법령, 조문, 사실,
인용을 만들지 않는다. 모든 material finding은 자체 evidenceReferenceIndexes를 가진다.
NEEDS_FACTS는 기존 인허가 보유, 기존 필수 계약 파트너, 실제 고정 관할, 보유 특허·라이선스처럼
Concept가 설계할 수 없는 외부 현실 사실에만 사용한다. 결제 주체, 플랫폼 역할, 개인정보 처리,
파트너 역할 같은 설계 누락은 REDESIGNABLE과 redesignRequirements로 반환한다. 이 결과는 법률
외부 사실을 확정하지 않아도 '해당 인허가를 보유한 파트너만 사용' 같은 강제 통제조건으로
구조적으로 구현할 수 있을 때만 IMPLEMENTABLE_WITH_CONTROLS를 사용할 수 있으며 사실 보유를 추정하지 않는다.
자문이 아니다. strict schema만 반환한다."""

REVIEW_LABEL = "공식 근거 기반 법률 구현 가능성 사전검토"
REVIEW_LIMITATIONS = "공식 법령의 제한된 조문과 확인 시점을 기준으로 한 사전검토이며, 구체적 사실관계와 최신 시행 상태에 대한 전문가 확인이 필요할 수 있습니다."
EXTERNAL_FACT_POSSESSION_MARKERS = ("보유", "기존", "이미", "현재", "확보", "체결")
EXTERNAL_FACT_OBJECT_MARKERS = (
    "계약", "파트너", "인허가", "허가증", "특허", "라이선스", "면허", "등록증",
)


def _source_text(value: ConceptLegalReviewInput) -> str:
    payload = {
        "legalFactPattern": value.legalFactPattern.model_dump(mode="json"),
        "confirmedExternalFacts": [fact.model_dump(mode="json") for fact in value.externalFactContext.facts],
    }
    serialized = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    if "preMarketSom" in serialized:
        raise ProviderFailure("INVALID_REQUEST", "SOM_MUST_NOT_ENTER_LEGAL_REVIEW", 400, False)
    return serialized


def _official_evidence(source: dict) -> list[OfficialEvidence]:
    deduplicated: dict[tuple[str, str, str, str], OfficialEvidence] = {}
    for item in source["evidence"]:
        evidence = OfficialEvidence(
            referenceIndex=0, sourceType="OFFICIAL_LAW", lawId=item.get("lawId"),
            officialIdentifier=item["officialIdentifier"], lawName=item["lawName"],
            articleReference=item["articleReference"], title=item.get("title") or "",
            officialSourceUri=item["officialSourceUri"], jurisdiction="KR",
            promulgationDate=item.get("promulgationDate"), effectiveDate=item.get("effectiveDate"),
            retrievedAt=item["retrievedAt"], contentHash=item["contentHash"],
            boundedProvisionSummary=item["plainSummary"], queryKey=item["queryKey"],
            registryVersion=item["registryVersion"],
        )
        key = (evidence.officialIdentifier, evidence.articleReference,
               evidence.effectiveDate or "", evidence.contentHash)
        deduplicated.setdefault(key, evidence)
    return [item.model_copy(update={"referenceIndex": index})
            for index, item in enumerate(deduplicated.values())]


def _is_external_fact_question(question: str) -> bool:
    normalized = " ".join(question.split())
    fixed_jurisdiction = any(marker in normalized for marker in ("고정 관할", "실제 고정 국가", "실제 고정 지역"))
    held_external_fact = any(marker in normalized for marker in EXTERNAL_FACT_POSSESSION_MARKERS) \
        and any(marker in normalized for marker in EXTERNAL_FACT_OBJECT_MARKERS)
    return fixed_jurisdiction or held_external_fact


def _terminal_result(status: str, requirements: list[str], evidence: list[OfficialEvidence],
                     value: ConceptLegalReviewInput) -> dict:
    needs_facts = status == "NEEDS_FACTS"
    return ConceptLegalReviewDomainResult(
        status=status, reviewedActivities=[], requiredControls=[],
        requiredPartnersAndQualifications=[], requiredDisclosures=[], prohibitedVariants=[],
        redesignRequirements=[] if needs_facts else requirements,
        unknownFacts=requirements if needs_facts else [], findingEvidence=[], officialEvidence=evidence,
        evidenceReferenceIndexes=[], expertReviewRecommended=needs_facts, reviewBasisDate=date.today(),
        safeUserSummary=("Concept가 결정할 수 없는 외부 현실 사실의 확인이 필요합니다."
            if needs_facts else "법률검토 전에 Concept의 사업 구조를 보완해야 합니다."),
        reviewedFactPatternSchemaVersion="2.0", reviewedFactPatternHash=value.factPatternHash,
        reviewLabel=REVIEW_LABEL, reviewLimitations=REVIEW_LIMITATIONS,
    ).model_dump(mode="json")


def _validate_coverage(provider: ConceptLegalReviewProviderResult,
                       evidence: list[OfficialEvidence]) -> tuple[dict[str, list[str]], list[dict], list[int]]:
    indexes = {item.referenceIndex for item in evidence}
    if len(provider.evidenceReferenceIndexes) != len(set(provider.evidenceReferenceIndexes)) \
        or any(index not in indexes for index in provider.evidenceReferenceIndexes):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "EVIDENCE_REFERENCE_INVALID", 502, False)
    fields = ("requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures", "prohibitedVariants")
    strings: dict[str, list[str]] = {}
    coverage: list[dict] = []
    cited: set[int] = set(provider.evidenceReferenceIndexes)
    for field in fields:
        findings = getattr(provider, field)
        strings[field] = [finding.text for finding in findings]
        for finding_index, finding in enumerate(findings):
            refs = finding.evidenceReferenceIndexes
            if not refs or len(refs) != len(set(refs)) or any(index not in indexes for index in refs):
                raise ProviderFailure("RESULT_SCHEMA_INVALID", "CONCEPT_LEGAL_FINDING_EVIDENCE_REQUIRED", 502, False)
            cited.update(refs)
            coverage.append({"findingType": field, "findingIndex": finding_index,
                "evidenceReferenceIndexes": refs})
    if provider.status in {"IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS"} and not cited:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "CONCEPT_LEGAL_EVIDENCE_REQUIRED", 502, False)
    return strings, coverage, sorted(cited)


async def execute_concept_legal_review(task_input: dict) -> dict:
    try:
        value = ConceptLegalReviewInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure

    source = await execute_legal_source_pipeline("CONCEPT_LEGAL_VALIDATION", _source_text(value), {
        "mode": "FULL", "rerunCategories": [],
        "confirmedFacts": [fact.model_dump(mode="json") for fact in value.externalFactContext.facts],
        "registryVersion": value.externalFactContext.registryVersion,
    })
    evidence = _official_evidence(source)
    questions = [item.get("question", "").strip() for item in source.get("requiredUserInputs", [])
                 if item.get("question", "").strip()]
    design_gaps = [question for question in questions if not _is_external_fact_question(question)]
    if design_gaps:
        return _terminal_result("REDESIGNABLE", design_gaps, evidence, value)
    if questions:
        return _terminal_result("NEEDS_FACTS", questions, evidence, value)
    if not evidence:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "LEGAL_SOURCE_EVIDENCE_UNAVAILABLE", 503, True)

    provider_input = {
        "legalFactPattern": value.legalFactPattern.model_dump(mode="json"),
        "confirmedExternalFacts": [fact.model_dump(mode="json") for fact in value.externalFactContext.facts],
        "officialEvidence": [item.model_dump(mode="json") for item in evidence],
    }
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(provider_input, ensure_ascii=False, sort_keys=True, default=str),
        response_schema=ConceptLegalReviewProviderResult.model_json_schema(),
        schema_name="concept_legal_review_v3", task_type="CONCEPT_LEGAL_REVIEW",
    )
    try:
        provider = ConceptLegalReviewProviderResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    if provider.status == "NEEDS_FACTS":
        design_gaps = [question for question in provider.unknownFacts if not _is_external_fact_question(question)]
        if design_gaps:
            return _terminal_result("REDESIGNABLE", design_gaps, evidence, value)
    finding_strings, finding_evidence, evidence_union = _validate_coverage(provider, evidence)
    provider_values = provider.model_dump(exclude={
        "requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures",
        "prohibitedVariants", "evidenceReferenceIndexes",
    })
    return ConceptLegalReviewDomainResult(
        **provider_values, **finding_strings, findingEvidence=finding_evidence,
        officialEvidence=evidence, evidenceReferenceIndexes=evidence_union,
        reviewedFactPatternSchemaVersion="2.0", reviewedFactPatternHash=value.factPatternHash,
        reviewLabel=REVIEW_LABEL, reviewLimitations=REVIEW_LIMITATIONS,
    ).model_dump(mode="json")
