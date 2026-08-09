import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_candidate.models import ConceptCandidateDraft
from app.tasks.concept_candidate.service import _validation_fields, normalize_redesign
from app.tasks.concept_redesign.models import ConceptRedesignInput, ConceptRedesignResult


SYSTEM_PROMPT = """공급된 안전 구현 조건과 명시된 designGaps를 모두 해결하도록 Concept 후보의
운영 구조를 재설계한다. LegalFactPattern에 드러난 행위자, 판매·제공·중개 역할, 거래·결제·
개인정보 흐름을 모호하지 않게 보완한다. 한국어 사용자 문구와 사업 내용 strict schema만 반환한다.
schemaVersion, generationStrategy, candidateIndex, originalCandidate, valueSemantics, source, authority,
decision은 생성하지 않는다. 시스템이 원 후보의 LOCKED 값과 거버넌스를 강제 보존한다.
pre-market SOM은 사전 가설로 유지한다. 증거 ID, 법령 문구, 최종 법률 상태, 사용자 확인 상태는
만들지 않는다."""


async def execute_concept_redesign(task_input: dict) -> dict:
    try:
        value = ConceptRedesignInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure(
            "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
            validation_fields=_validation_fields(failure, "input"),
        ) from failure

    provider_input = {
        "previousCandidate": _business_content(value.candidate),
        "safeConstraints": value.safeConstraints,
        "prohibitedVariants": value.prohibitedVariants,
        "designGaps": value.designGaps,
        "legalFactPattern": value.legalFactPattern.model_dump(mode="json"),
        "lockedConstraints": _locked_constraints(value),
    }
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(provider_input, ensure_ascii=False, sort_keys=True),
        response_schema=ConceptCandidateDraft.model_json_schema(),
        schema_name="concept_redesign_draft_v1", task_type="CONCEPT_REDESIGN",
    )
    try:
        draft = ConceptCandidateDraft.model_validate(raw)
    except ValidationError as first_failure:
        repair_input = {
            "previousCandidate": raw,
            "failureCode": "CONTENT_FIELD_MISSING",
            "failedFields": _validation_fields(first_failure, "draft"),
            "requiredCorrection": "designGaps를 해결하면서 누락된 사업 내용 필드를 보완한다.",
            "generationStrategy": value.candidate.generationStrategy,
            "candidateIndex": value.candidate.candidateIndex,
            "diversityFocus": "LEGAL_REDESIGN",
            "lockedConstraints": _locked_constraints(value),
            "designGaps": value.designGaps,
        }
        repaired = await execute_structured_prompt(
            SYSTEM_PROMPT + "\n이 호출은 이전 초안의 실제 content 오류를 고치는 1회 한정 repair다.",
            json.dumps(repair_input, ensure_ascii=False, sort_keys=True),
            response_schema=ConceptCandidateDraft.model_json_schema(),
            schema_name="concept_redesign_draft_repair_v1", task_type="CONCEPT_REDESIGN",
        )
        try:
            draft = ConceptCandidateDraft.model_validate(repaired)
        except ValidationError as repaired_failure:
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID", "CONTENT_FIELD_MISSING", 502, False,
                schema_name="concept_redesign_draft_v1",
                validation_fields=_validation_fields(repaired_failure, "draft"),
            ) from repaired_failure

    try:
        return ConceptRedesignResult.model_validate(
            normalize_redesign(value.candidate, draft)
        ).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "PYDANTIC_RESULT_VALIDATION_FAILED", 502, False,
            schema_name="concept_redesign_v2",
            validation_fields=_validation_fields(failure, "result"),
        ) from failure


def _business_content(candidate) -> dict:
    payload = candidate.model_dump(mode="json")
    for key in ("schemaVersion", "generationStrategy", "candidateIndex", "originalCandidate", "valueSemantics"):
        payload.pop(key, None)
    return payload


def _locked_constraints(value: ConceptRedesignInput) -> list[dict]:
    candidate = value.candidate.model_dump(mode="json")
    return [{"fieldKey": semantic.fieldKey, "value": candidate[semantic.fieldKey],
             "source": semantic.source, "authority": semantic.authority,
             "decision": semantic.decision}
            for semantic in value.candidate.valueSemantics if semantic.authority == "LOCKED"]
