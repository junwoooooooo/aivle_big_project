import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.final_business_proposal.models import (
    FinalBusinessProposalInput, FinalBusinessProposalResult,
)
from app.tasks.final_business_proposal.prompts import SYSTEM_PROMPT
from app.tasks.marketing_content.models import lint_provider_schema


def _close_evidence_vocabulary(node, allowed_types: list[str]) -> None:
    if isinstance(node, dict):
        properties = node.get("properties", {})
        evidence = properties.get("evidenceSourceTypes")
        if isinstance(evidence, dict):
            evidence["items"] = {"type": "string", "enum": allowed_types}
        for value in node.values():
            _close_evidence_vocabulary(value, allowed_types)
    elif isinstance(node, list):
        for value in node:
            _close_evidence_vocabulary(value, allowed_types)


def _validate_evidence_types(result: FinalBusinessProposalResult, allowed: set[str]) -> None:
    groups = [result.executiveDecisionSummary.evidenceSourceTypes,
              result.decisionRequest.evidenceSourceTypes, result.appendix.evidenceSourceTypes]
    groups.extend(section.evidenceSourceTypes for section in result.sections)
    invalid = sorted({source_type for group in groups for source_type in group
                      if source_type not in allowed})
    if invalid:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "AI_EVIDENCE_REFERENCE_INVALID", 502, False,
            safe_diagnostics={"allowedTypes": sorted(allowed), "invalidTypes": invalid,
                              "invalidRefCount": sum(source_type not in allowed
                                                     for group in groups for source_type in group)},
        )


async def execute_final_business_proposal(task_input: dict) -> dict:
    try:
        value = FinalBusinessProposalInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    allowed_types = sorted({item.type for item in value.sourceManifest})
    schema = FinalBusinessProposalResult.model_json_schema()
    _close_evidence_vocabulary(schema, allowed_types)
    if lint_provider_schema(schema):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "PROVIDER_RESPONSE_SCHEMA_REJECTED", 502, False)
    payload = {**value.model_dump(mode="json"), "allowedEvidenceSourceTypes": allowed_types}
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT + "\n허용 source type: " + json.dumps(allowed_types, ensure_ascii=False),
        json.dumps(payload, ensure_ascii=False, sort_keys=True), response_schema=schema,
        schema_name="final_business_proposal_result_v1",
        task_type="FINAL_BUSINESS_PROPOSAL_GENERATION",
    )
    try:
        result = FinalBusinessProposalResult.model_validate(raw)
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    _validate_evidence_types(result, set(allowed_types))
    return result.model_dump(mode="json")
