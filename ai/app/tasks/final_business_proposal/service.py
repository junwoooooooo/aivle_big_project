import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.final_business_proposal.models import (
    FinalBusinessProposalInput, FinalBusinessProposalResult,
)
from app.tasks.final_business_proposal.prompts import SYSTEM_PROMPT
from app.tasks.marketing_content.models import lint_provider_schema


def _safe_validation_fields(failure: ValidationError) -> list[dict[str, str]]:
    expected = {
        "missing": "required", "extra_forbidden": "no extra field",
        "dict_type": "object", "list_type": "array", "string_type": "string",
        "int_type": "integer", "literal_error": "allowed literal",
    }
    fields = []
    for issue in failure.errors(include_url=False, include_context=False, include_input=False)[:12]:
        category = str(issue.get("type", "invalid"))[:80]
        location = ".".join(str(part) for part in issue.get("loc", ()))
        fields.append({
            "path": (location or "input")[:200],
            "category": category,
            "expectedType": expected.get(category, "valid contract value"),
        })
    return fields


def _close_evidence_vocabulary(node, allowed_types: list[str],
                               allowed_keys: list[str] | None = None) -> None:
    if isinstance(node, dict):
        properties = node.get("properties", {})
        evidence = properties.get("evidenceSourceTypes")
        if isinstance(evidence, dict):
            evidence["items"] = {"type": "string", "enum": allowed_types}
        evidence_keys = properties.get("evidenceKeys")
        if isinstance(evidence_keys, dict) and allowed_keys is not None:
            # A real proposal can expose hundreds of catalog keys. Repeating that runtime
            # vocabulary in all four evidenceKeys schema locations exceeds the provider's
            # total enum limit. The schema keeps the canonical key shape; exact catalog
            # membership remains fail-closed in _validate_evidence_types after generation.
            evidence_keys["items"] = {"type": "string", "pattern": r"^EV-[0-9a-f]{24}$"}
        for value in node.values():
            _close_evidence_vocabulary(value, allowed_types, allowed_keys)
    elif isinstance(node, list):
        for value in node:
            _close_evidence_vocabulary(value, allowed_types, allowed_keys)


def _validate_evidence_types(result: FinalBusinessProposalResult, allowed: set[str],
                             allowed_keys: set[str]) -> None:
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
    key_groups = [result.executiveDecisionSummary.evidenceKeys,
                  result.decisionRequest.evidenceKeys, result.appendix.evidenceKeys]
    key_groups.extend(section.evidenceKeys for section in result.sections)
    invalid_keys = sorted({key for group in key_groups for key in group if key not in allowed_keys})
    if invalid_keys:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "AI_EVIDENCE_REFERENCE_INVALID", 502, False,
            safe_diagnostics={"invalidEvidenceKeyCount": len(invalid_keys),
                              "allowedEvidenceKeyCount": len(allowed_keys)},
        )


async def execute_final_business_proposal(task_input: dict) -> dict:
    try:
        value = FinalBusinessProposalInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure(
            "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
            schema_name="final_business_proposal_input_v1",
            validation_fields=_safe_validation_fields(failure),
            safe_diagnostics={"stage": "INPUT_CONTRACT_VALIDATION"},
        ) from failure
    allowed_types = sorted({item.type for item in value.sourceManifest})
    allowed_keys = list(dict.fromkeys(value.allowedEvidenceKeys))
    catalog_keys = {item.evidenceKey for item in value.evidenceCatalog}
    if set(allowed_keys) != catalog_keys:
        raise ProviderFailure(
            "INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False,
            validation_fields=[{"path": "allowedEvidenceKeys", "category": "catalog_mismatch",
                                "expectedType": "exact evidence catalog keys"}],
            safe_diagnostics={"stage": "INPUT_CONTRACT_VALIDATION"},
        )
    schema = FinalBusinessProposalResult.model_json_schema()
    _close_evidence_vocabulary(schema, allowed_types, allowed_keys)
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
    _validate_evidence_types(result, set(allowed_types), set(allowed_keys))
    return result.model_dump(mode="json")
