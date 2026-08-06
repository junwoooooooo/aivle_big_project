import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.concept_legal_review.models import (
    ConceptLegalReviewDomainResult, ConceptLegalReviewInput, ConceptLegalReviewProviderResult,
)


SYSTEM_PROMPT = """Perform an official-evidence-based legal implementation feasibility review.
Use only the supplied evidence reference indexes. Never invent evidence IDs or quote legal text.
Return only the strict schema. This is not a perfect legal review or legal advice."""


async def execute_concept_legal_review(task_input: dict) -> dict:
    try:
        value = ConceptLegalReviewInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    if not value.sharedContext.officialEvidence:
        raise ProviderFailure("INVALID_REQUEST", "OFFICIAL_EVIDENCE_REQUIRED", 422, False)
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT, json.dumps(value.model_dump(mode="json"), ensure_ascii=False, sort_keys=True, default=str),
        response_schema=ConceptLegalReviewProviderResult.model_json_schema(),
        schema_name="concept_legal_review_v1", task_type="CONCEPT_LEGAL_REVIEW",
    )
    try:
        provider = ConceptLegalReviewProviderResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    evidence = {item.referenceIndex: item for item in value.sharedContext.officialEvidence}
    if len(set(provider.evidenceReferenceIndexes)) != len(provider.evidenceReferenceIndexes) or any(
        index not in evidence for index in provider.evidenceReferenceIndexes
    ):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "EVIDENCE_REFERENCE_INVALID", 502, False)
    return ConceptLegalReviewDomainResult(
        **provider.model_dump(exclude={"evidenceReferenceIndexes"}),
        evidenceReferences=[evidence[index] for index in provider.evidenceReferenceIndexes],
    ).model_dump(mode="json")
