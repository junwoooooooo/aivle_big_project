import json
from datetime import date

from pydantic import ValidationError

from app.legal.pipeline import execute_legal_source_pipeline
from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_legal_review.models import (
    ConceptLegalReviewDomainResult, ConceptLegalReviewInput, ConceptLegalReviewProviderResult,
    LegalQuestionClassificationBatch, OfficialEvidence,
)


SYSTEM_PROMPT = """정의된 Concept Legal Fact Pattern과 공식 법령 근거만으로 법률 구현 가능성을 사전검토한다.
질문은 '이 구조와 통제조건대로 구현하면 법적으로 구현 가능한가'이다. 현재 사업자가 모든 조건을
이미 충족했다고 추정하지 않는다. supplied evidence reference index만 사용하고 법령, 조문, 사실,
인용을 만들지 않는다. 모든 material finding은 자체 evidenceReferenceIndexes를 가진다.
CONCEPT_GENERATED/AI_HYPOTHESIS/PROPOSED 사업 설계값은 미확정 외부 사실이 아니라 이번 검토에서
그 설계대로 구현한다고 가정하는 값이다. provenance만을 이유로 사용자 확인을 요구하지 않는다.
NEEDS_FACTS는 기존 인허가 보유, 기존 필수 계약 파트너, 실제 고정 관할, 보유 특허·라이선스처럼
Concept가 설계할 수 없는 외부 현실 사실에만 사용한다. 결제 주체, 플랫폼 역할, 개인정보 처리,
파트너 역할 같은 설계 누락은 REDESIGNABLE과 redesignRequirements로 반환한다. 이 결과는 법률
외부 사실을 확정하지 않아도 '해당 인허가를 보유한 파트너만 사용' 같은 강제 통제조건으로
구조적으로 구현할 수 있을 때만 IMPLEMENTABLE_WITH_CONTROLS를 사용할 수 있으며 사실 보유를 추정하지 않는다.
IMPLEMENTABLE/IMPLEMENTABLE_WITH_CONTROLS는 architecture 변경이 필요 없으므로 redesignRequirements를 비운다.
고지·처리방침·운영 제한은 requiredControls/requiredDisclosures에, 결제 주체나 사업 역할 자체의 변경은
REDESIGNABLE의 redesignRequirements에만 둔다.
evidenceReferenceIndexes에는 입력의 allowedEvidenceReferenceIndexes에 있는 정수만 사용한다.
자문이 아니다. strict schema만 반환한다."""

REPAIR_PROMPT = """법률 판단, 상태, finding 문구, 통제, 요약을 변경하지 않는다.
이전 결과의 evidenceReferenceIndexes만 supplied officialEvidence에 다시 연결한다.
allowedEvidenceReferenceIndexes에 없는 번호나 새로운 근거를 만들지 않는다. strict schema만 반환한다."""

CONTRACT_REPAIR_PROMPT = """법률 판단의 status, 요약, 검토 활동, 외부 unknown fact, 금지 variant,
근거 index를 바꾸지 않는다. IMPLEMENTABLE/IMPLEMENTABLE_WITH_CONTROLS이면 redesignRequirements를 비우고,
그 내용이 구조 변경이 아닌 통제·고지라면 기존 officialEvidence index를 사용해 적절한 control/disclosure finding에
반영한다. IMPLEMENTABLE 계열과 REDESIGNABLE은 unknownFacts를 비운다. REDESIGNABLE은 redesignRequirements만
사용한다. NEEDS_FACTS는 unknownFacts만
사용하고 redesignRequirements를 비운다. 새로운 법률 사실이나 근거를 만들지 말고 strict schema만 반환한다."""

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


def _question_kind(question: str) -> str:
    normalized = " ".join(question.casefold().split())
    unavoidable_markers = (
        "현재 보유", "기존 보유", "실제 고정 관할", "보유 특허", "보유 라이선스",
        "인허가가 있", "인허가를 보유", "기존 필수 계약", "이미 체결",
    )
    design_markers = (
        "결제 주체", "정산 흐름", "처리 방식", "플랫폼 역할", "판매자 역할",
        "개인정보 처리", "파트너 역할", "거래 흐름", "운영 주체",
    )
    convertible_markers = (
        "보유 사업자와 계약", "자격 보유 파트너", "통제 조건으로", "필수 통제로",
    )
    control_intent = any(marker in normalized for marker in convertible_markers) and any(
        marker in normalized for marker in ("조건", "제한", "사용", "전환", "계약"))
    possession_intent = any(marker in normalized for marker in unavoidable_markers)
    explicit_current_fact = _is_external_fact_question(question) and any(
        marker in normalized for marker in ("현재", "기존", "이미", "실제", "체결되어", "보유하고"))
    if possession_intent or explicit_current_fact:
        return "UNAVOIDABLE_EXTERNAL_FACT"
    if control_intent:
        return "CONTROL_CONVERTIBLE"
    if any(marker in normalized for marker in design_markers):
        return "DESIGN_GAP"
    if _is_external_fact_question(question):
        return "UNAVOIDABLE_EXTERNAL_FACT"
    return "AMBIGUOUS"


def _governed_value(value: ConceptLegalReviewInput, path: str):
    pattern = value.legalFactPattern
    mapping = {
        "platformRole": pattern.platformRole.value,
        "providerRole": pattern.commercialRoles.providerRole.value,
        "sellerRole": pattern.commercialRoles.sellerRole.value,
        "intermediaryRole": pattern.commercialRoles.intermediaryRole.value,
        "transactionFlow": pattern.transactionFlow.value,
        "paymentFlow": pattern.paymentFlow.value,
        "personalDataUsage": pattern.personalDataUsage.value,
        "physicalActivities": pattern.physicalActivities.value,
        "partnerRequirements": pattern.partnerRoles.partnerRequirements.value,
        "qualificationRequirements": pattern.qualificationRequirements.value,
        "targetRegion": pattern.hypotheses.targetRegion.value,
        "price": pattern.hypotheses.price.value,
        "channels": pattern.hypotheses.channels.value,
    }
    return mapping[path]


_QUESTION_FACT_FIELDS = {
    "결제": ("paymentFlow",), "정산": ("paymentFlow", "transactionFlow"),
    "거래": ("transactionFlow",), "플랫폼": ("platformRole",),
    "판매자": ("sellerRole",), "판매 주체": ("sellerRole",),
    "제공자": ("providerRole",), "제공 주체": ("providerRole",),
    "중개": ("intermediaryRole",), "개인정보": ("personalDataUsage",),
    "물리": ("physicalActivities",), "배송": ("physicalActivities", "transactionFlow"),
    "파트너": ("partnerRequirements",), "자격": ("qualificationRequirements", "partnerRequirements"),
    "채널": ("channels",), "가격": ("price",), "지역": ("targetRegion",),
}
_PLACEHOLDER_MARKERS = ("정보가 필요", "확인이 필요", "검증 필요", "미정", "추후", "tbd", "open")


def _substantive_fact(value) -> bool:
    values = value if isinstance(value, list) else [value]
    return any(str(item).strip() and not any(marker in str(item).casefold()
        for marker in _PLACEHOLDER_MARKERS) for item in values)


def _question_resolved_by_fact_pattern(question: str, value: ConceptLegalReviewInput) -> bool:
    fields = tuple(dict.fromkeys(field for marker, mapped in _QUESTION_FACT_FIELDS.items()
                                 if marker in question for field in mapped))
    return bool(fields) and all(_substantive_fact(_governed_value(value, field)) for field in fields)


async def _classify_questions(questions: list[str]) -> dict[str, str]:
    classified = {question: _question_kind(question) for question in questions}
    ambiguous = [question for question, kind in classified.items() if kind == "AMBIGUOUS"]
    if not ambiguous:
        return classified
    schema = LegalQuestionClassificationBatch.model_json_schema()
    raw = await execute_structured_prompt(
        "법률 결론을 만들지 말고 질문이 사업 설계 누락, 실제 외부 보유 사실, 설계 가능한 통제조건, 법률 판단용 추가 확인 중 무엇인지 분류한다. 입력 질문을 그대로 한 번씩 반환하고 strict schema만 사용한다.",
        json.dumps({"questions": ambiguous}, ensure_ascii=False), response_schema=schema,
        schema_name="legal_question_classification_v1", task_type="LEGAL_QUESTION_CLASSIFICATION")
    batch = LegalQuestionClassificationBatch.model_validate(raw)
    returned = [item.question for item in batch.results]
    if sorted(returned) != sorted(ambiguous) or len(returned) != len(set(returned)):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_QUESTION_CLASSIFICATION_MISMATCH", 502, False)
    classified.update({item.question: item.kind for item in batch.results})
    return classified


def _terminal_result(status: str, requirements: list[str], evidence: list[OfficialEvidence],
                     value: ConceptLegalReviewInput, diagnostics: dict | None = None) -> dict:
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
        finalEvidenceJudgmentExecuted=False, **(diagnostics or {}),
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


def _runtime_provider_schema(allowed_indexes: list[int]) -> dict:
    schema = ConceptLegalReviewProviderResult.model_json_schema()
    schema["properties"]["evidenceReferenceIndexes"]["items"]["enum"] = allowed_indexes
    finding = schema["$defs"]["EvidenceBackedFinding"]["properties"]["evidenceReferenceIndexes"]
    finding["items"]["enum"] = allowed_indexes
    finding["minItems"] = 1
    return schema


def _binding_diagnostic(provider: ConceptLegalReviewProviderResult,
                        evidence: list[OfficialEvidence]) -> dict:
    allowed = sorted(item.referenceIndex for item in evidence)
    returned = list(provider.evidenceReferenceIndexes)
    invalid = sorted({item for item in returned if item not in allowed})
    duplicates = sorted({item for item in returned if returned.count(item) > 1})
    finding_type = None
    finding_index = None
    for field in ("requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures", "prohibitedVariants"):
        for index, finding in enumerate(getattr(provider, field)):
            refs = finding.evidenceReferenceIndexes
            if not refs or len(refs) != len(set(refs)) or any(item not in allowed for item in refs):
                finding_type, finding_index = field, index
                invalid.extend(item for item in refs if item not in allowed)
                duplicates.extend(item for item in refs if refs.count(item) > 1)
                break
        if finding_type:
            break
    return {"allowedEvidenceReferenceIndexes": allowed,
            "returnedTopLevelIndexes": returned, "invalidIndexes": sorted(set(invalid)),
            "duplicateIndexes": sorted(set(duplicates)), "findingType": finding_type,
            "findingIndex": finding_index}


def _judgment_without_references(provider: ConceptLegalReviewProviderResult) -> dict:
    payload = provider.model_dump(mode="json")
    payload.pop("evidenceReferenceIndexes", None)
    for field in ("requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures", "prohibitedVariants"):
        payload[field] = [item["text"] for item in payload[field]]
    return payload


def _status_invariant_violation(raw: object) -> bool:
    if not isinstance(raw, dict):
        return False
    status = raw.get("status")
    redesign = raw.get("redesignRequirements") or []
    unknown = raw.get("unknownFacts") or []
    return ((status in {"IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS", "NEEDS_FACTS"} and bool(redesign))
            or (status in {"IMPLEMENTABLE", "IMPLEMENTABLE_WITH_CONTROLS"} and bool(unknown))
            or (status == "REDESIGNABLE" and bool(unknown)))


def _protected_contract_judgment(raw: dict) -> dict:
    protected = {key: raw.get(key) for key in (
        "status", "reviewedActivities", "prohibitedVariants", "evidenceReferenceIndexes",
        "expertReviewRecommended", "reviewBasisDate", "safeUserSummary",
    )}
    if raw.get("status") == "NEEDS_FACTS":
        protected["unknownFacts"] = raw.get("unknownFacts")
    return protected


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
    unresolved = [question for question in questions if not _question_resolved_by_fact_pattern(question, value)]
    resolved_count = len(questions) - len(unresolved)
    classification = await _classify_questions(unresolved)
    design_gaps = [question for question in unresolved if classification[question] == "DESIGN_GAP"]
    unavoidable = [question for question in unresolved
                   if classification[question] == "UNAVOIDABLE_EXTERNAL_FACT"]
    convertible = [question for question in unresolved if classification[question] == "CONTROL_CONVERTIBLE"]
    clarifications = [question for question in unresolved if classification[question] == "LEGAL_CLARIFICATION"]
    diagnostics = {"legalSourceStatus": source.get("sourceStatus"), "sourceQuestionCount": len(questions),
                   "resolvedByFactPatternCount": resolved_count, "designGapCount": len(design_gaps),
                   "externalFactCount": len(unavoidable), "controlConvertibleCount": len(convertible),
                   "legalClarificationCount": len(clarifications)}
    if design_gaps:
        return _terminal_result("REDESIGNABLE", design_gaps, evidence, value, diagnostics)
    if unavoidable:
        return _terminal_result("NEEDS_FACTS", unavoidable, evidence, value, diagnostics)
    if not evidence:
        if convertible:
            return _terminal_result("REDESIGNABLE", convertible, evidence, value, diagnostics)
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "LEGAL_SOURCE_EVIDENCE_UNAVAILABLE", 503, True)

    provider_input = {
        "legalFactPattern": value.legalFactPattern.model_dump(mode="json"),
        "confirmedExternalFacts": [fact.model_dump(mode="json") for fact in value.externalFactContext.facts],
        "officialEvidence": [item.model_dump(mode="json") for item in evidence],
        "allowedEvidenceReferenceIndexes": [item.referenceIndex for item in evidence],
        # 이전 provider input key는 통제 전환 가능 질문을 담는 용도로 하위 호환 유지한다.
        "unresolvedExternalFactQuestions": convertible,
        "controlConvertibleQuestions": convertible,
        "legalClarifications": clarifications,
    }
    allowed_indexes = provider_input["allowedEvidenceReferenceIndexes"]
    runtime_schema = _runtime_provider_schema(allowed_indexes)
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(provider_input, ensure_ascii=False, sort_keys=True, default=str),
        response_schema=runtime_schema,
        schema_name="concept_legal_review_v3", task_type="CONCEPT_LEGAL_REVIEW",
    )
    try:
        provider = ConceptLegalReviewProviderResult.model_validate(raw)
    except ValidationError as failure:
        if not _status_invariant_violation(raw):
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "PYDANTIC_RESULT_VALIDATION_FAILED", 502, False,
                schema_name="concept_legal_review_v3",
                validation_fields=_validation_fields(failure, "result"),
            ) from failure
        repaired_raw = await execute_structured_prompt(
            CONTRACT_REPAIR_PROMPT, json.dumps({
                "previousLegalResult": raw,
                "officialEvidence": provider_input["officialEvidence"],
                "allowedEvidenceReferenceIndexes": allowed_indexes,
                "failureCode": "LEGAL_STATUS_REDESIGN_INVARIANT",
            }, ensure_ascii=False, sort_keys=True, default=str), response_schema=runtime_schema,
            schema_name="concept_legal_review_v3_contract_repair",
            task_type="LEGAL_RESULT_CONTRACT_REPAIR")
        try:
            provider = ConceptLegalReviewProviderResult.model_validate(repaired_raw)
        except ValidationError as repair_failure:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_STATUS_INVARIANT_REPAIR_FAILED",
                                  502, False, schema_name="concept_legal_review_v3_contract_repair") from repair_failure
        protected_before = _protected_contract_judgment(raw)
        protected_after = _protected_contract_judgment(repaired_raw)
        if protected_before != protected_after:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_STATUS_INVARIANT_REPAIR_MUTATED_JUDGMENT",
                                  502, False, schema_name="concept_legal_review_v3_contract_repair")
    if provider.status == "NEEDS_FACTS":
        provider_classification = await _classify_questions(provider.unknownFacts)
        design_gaps = [question for question in provider.unknownFacts
                       if provider_classification[question] == "DESIGN_GAP"
                       and not _question_resolved_by_fact_pattern(question, value)]
        if design_gaps:
            return _terminal_result("REDESIGNABLE", design_gaps, evidence, value, diagnostics)
        actual_external = [question for question in provider.unknownFacts
                           if provider_classification[question] == "UNAVOIDABLE_EXTERNAL_FACT"]
        if not actual_external:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_PROVIDER_REPEATED_RESOLVED_FACT_REQUEST",
                                  502, False, schema_name="concept_legal_review_v3")
    try:
        finding_strings, finding_evidence, evidence_union = _validate_coverage(provider, evidence)
    except ProviderFailure as binding_failure:
        if binding_failure.reason not in {
                "EVIDENCE_REFERENCE_INVALID", "CONCEPT_LEGAL_FINDING_EVIDENCE_REQUIRED",
                "CONCEPT_LEGAL_EVIDENCE_REQUIRED"}:
            raise
        first_diagnostic = _binding_diagnostic(provider, evidence)
        repair_input = {"previousLegalResult": provider.model_dump(mode="json"),
                        "officialEvidence": provider_input["officialEvidence"],
                        "allowedEvidenceReferenceIndexes": allowed_indexes,
                        "failureCode": binding_failure.reason}
        repaired_raw = await execute_structured_prompt(
            REPAIR_PROMPT, json.dumps(repair_input, ensure_ascii=False, sort_keys=True, default=str),
            response_schema=runtime_schema, schema_name="concept_legal_review_v3_citation_repair",
            task_type="LEGAL_EVIDENCE_BINDING_REPAIR")
        try:
            repaired = ConceptLegalReviewProviderResult.model_validate(repaired_raw)
        except ValidationError as repair_validation:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_EVIDENCE_BINDING_REPAIR_FAILED", 502, False,
                                  schema_name="concept_legal_review_v3_citation_repair",
                                  safe_diagnostics={**first_diagnostic, "repairAttempted": True}) from repair_validation
        if _judgment_without_references(repaired) != _judgment_without_references(provider):
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_EVIDENCE_BINDING_REPAIR_MUTATED_RESULT", 502, False,
                                  schema_name="concept_legal_review_v3_citation_repair",
                                  safe_diagnostics={**first_diagnostic, "repairAttempted": True})
        try:
            finding_strings, finding_evidence, evidence_union = _validate_coverage(repaired, evidence)
            provider = repaired
        except ProviderFailure as second_failure:
            raise ProviderFailure("RESULT_SCHEMA_INVALID", "LEGAL_EVIDENCE_BINDING_REPAIR_FAILED", 502, False,
                                  schema_name="concept_legal_review_v3_citation_repair",
                                  safe_diagnostics={**_binding_diagnostic(repaired, evidence),
                                                    "repairAttempted": True}) from second_failure
    provider_values = provider.model_dump(exclude={
        "requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures",
        "prohibitedVariants", "evidenceReferenceIndexes",
    })
    return ConceptLegalReviewDomainResult(
        **provider_values, **finding_strings, findingEvidence=finding_evidence,
        officialEvidence=evidence, evidenceReferenceIndexes=evidence_union,
        reviewedFactPatternSchemaVersion="2.0", reviewedFactPatternHash=value.factPatternHash,
        reviewLabel=REVIEW_LABEL, reviewLimitations=REVIEW_LIMITATIONS,
        finalEvidenceJudgmentExecuted=True, **diagnostics,
    ).model_dump(mode="json")


def _validation_fields(failure: ValidationError, prefix: str) -> list[dict[str, str]]:
    return [{
        "path": f"{prefix}." + ".".join(str(part) for part in issue.get("loc", ())),
        "category": str(issue.get("type", "invalid"))[:80],
        "expectedType": "valid contract value",
    } for issue in failure.errors()[:12]]
