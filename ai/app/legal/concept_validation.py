import json
import os
from typing import Any

from pydantic import ValidationError

from app.models.journey import (
    ConceptLegalValidationBatchInput,
    ConceptLegalValidationBatchResult,
    ConceptLegalValidationResult,
)
from app.services.journey_provider import ProviderFailure, execute_structured_prompt


SYSTEM = """Validate only whether the supplied Concept Draft complies with the supplied Legal Guardrail.
Do not invent statutes or legal sources. Compare the concept structure and activities against hardConstraints,
prohibitedPatterns, conditionalConstraints, requiredDisclosures, and requiredOperationalControls.
Return FAIL_LEGAL when any constraint is violated; otherwise return PASS. Return one JSON object only."""

BATCH_SYSTEM = """Validate each supplied Concept Draft only against the supplied Legal Guardrails and locked values.
Do not invent statutes, legal sources, or new guardrails. Return exactly one validation for every input candidateKey,
using the candidateKey unchanged. Do not omit, duplicate, or add candidate keys. Return one JSON object only."""


def _concept_validation_model() -> str | None:
    return os.getenv("AI_MODEL_CONCEPT_VALIDATION", "").strip() or None


async def execute_concept_legal_validation(task_input: dict[str, Any], text: str) -> dict[str, Any]:
    if task_input.get("validationMode") != "GUARDRAIL":
        raise ProviderFailure("INVALID_REQUEST", "CONCEPT_LEGAL_VALIDATION_MODE_INVALID", 400, False)
    try:
        prompt = {
            "input": json.loads(text),
            "output": {
                "status": "PASS|FAIL_LEGAL",
                "reasons": ["string"],
                "violatedStructureKeys": ["string"],
                "legalTrace": [
                    {"guardrailType": "string", "constraint": "string", "implementation": "string"}
                ],
            },
        }
        raw = await execute_structured_prompt(
            SYSTEM,
            json.dumps(prompt, ensure_ascii=False),
            model_override=_concept_validation_model(),
        )
        return ConceptLegalValidationResult.model_validate(raw).model_dump()
    except (ValidationError, ValueError, TypeError, json.JSONDecodeError) as failure:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "CONCEPT_LEGAL_VALIDATION_INVALID", 502, False
        ) from failure


async def execute_concept_legal_validation_batch(
    task_input: dict[str, Any], text: str
) -> dict[str, Any]:
    if task_input.get("validationMode") != "GUARDRAIL_BATCH":
        raise ProviderFailure("INVALID_REQUEST", "CONCEPT_LEGAL_VALIDATION_MODE_INVALID", 400, False)
    try:
        batch_input = ConceptLegalValidationBatchInput.model_validate(json.loads(text))
        prompt = {
            "input": batch_input.model_dump(),
            "output": {
                "validations": [
                    {
                        "candidateKey": "input candidateKey unchanged",
                        "status": "PASS|FAIL_LEGAL",
                        "reasons": ["string"],
                        "violatedStructureKeys": ["string"],
                        "legalTrace": [
                            {
                                "guardrailType": "string",
                                "constraint": "string",
                                "implementation": "string",
                            }
                        ],
                    }
                ]
            },
        }
        raw = await execute_structured_prompt(
            BATCH_SYSTEM,
            json.dumps(prompt, ensure_ascii=False),
            model_override=_concept_validation_model(),
        )
        result = ConceptLegalValidationBatchResult.model_validate(raw)
        expected_keys = [draft.candidateKey for draft in batch_input.conceptDrafts]
        actual_keys = [validation.candidateKey for validation in result.validations]
        if (
            len(set(expected_keys)) != len(expected_keys)
            or len(set(actual_keys)) != len(actual_keys)
            or set(actual_keys) != set(expected_keys)
        ):
            raise ProviderFailure(
                "RESULT_SCHEMA_INVALID",
                "CONCEPT_LEGAL_VALIDATION_CANDIDATE_KEYS_INVALID",
                502,
                False,
            )
        return result.model_dump()
    except ProviderFailure:
        raise
    except (ValidationError, ValueError, TypeError, json.JSONDecodeError) as failure:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID", "CONCEPT_LEGAL_VALIDATION_INVALID", 502, False
        ) from failure
