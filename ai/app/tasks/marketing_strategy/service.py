import json

from pydantic import ValidationError

from app.providers import (
    ProviderFailure,
    execute_structured_prompt,
)
from app.tasks.marketing_content.models import (
    lint_provider_schema,
)
from app.tasks.marketing_strategy.models import (
    MarketingStrategyInput,
    MarketingStrategyResult,
)
from app.tasks.marketing_strategy.prompts import (
    SYSTEM_PROMPT,
)


def _canonicalize_evidence_refs(refs: list[str], allowed_refs: list[str]) -> list[str]:
    allowed = set(allowed_refs)
    by_type: dict[str, list[str]] = {}
    for reference in allowed_refs:
        source_type, _separator, _source_id = reference.partition(":")
        by_type.setdefault(source_type, []).append(reference)

    canonical: list[str] = []
    invalid: list[str] = []
    for raw in refs:
        reference = str(raw).strip()
        if reference in allowed:
            resolved = reference
        else:
            source_type, separator, _source_id = reference.partition(":")
            candidates = by_type.get(source_type, []) if separator else []
            if len(candidates) == 1:
                resolved = candidates[0]
            else:
                invalid.append(source_type or "UNKNOWN")
                continue
        if resolved not in canonical:
            canonical.append(resolved)

    if invalid or not canonical:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID",
            "AI_EVIDENCE_REFERENCE_INVALID",
            502,
            False,
            safe_diagnostics={
                "allowedTypes": sorted(by_type),
                "invalidTypes": sorted(set(invalid)),
                "invalidRefCount": len(invalid),
            },
        )
    return canonical


async def execute_marketing_strategy(
    task_input: dict,
) -> dict:
    try:
        value = MarketingStrategyInput.model_validate(
            task_input
        )
    except ValidationError as failure:
        raise ProviderFailure(
            "INVALID_REQUEST",
            "FIELD_CONSTRAINT_VIOLATION",
            400,
            False,
        ) from failure

    allowed_refs = sorted({
        f"{item.type}:{item.id}"
        for item in value.sourceManifest
    })
    schema = MarketingStrategyResult.model_json_schema()
    schema["properties"]["evidenceRefs"]["items"] = {
        "type": "string",
        "enum": allowed_refs,
    }

    if lint_provider_schema(schema):
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID",
            "PROVIDER_RESPONSE_SCHEMA_REJECTED",
            502,
            False,
        )

    raw = await execute_structured_prompt(
        SYSTEM_PROMPT + (
            "\n허용된 evidenceRefs는 아래 목록뿐이다. 목록의 문자열을 정확히 복사하고 "
            "다른 TYPE이나 id를 생성하지 않는다.\n"
            + json.dumps(allowed_refs, ensure_ascii=False)
        ),
        json.dumps(
            {
                **value.model_dump(mode="json"),
                "allowedEvidenceRefs": allowed_refs,
            },
            ensure_ascii=False,
            sort_keys=True,
        ),
        response_schema=schema,
        schema_name="marketing_strategy_result_v1",
        task_type="MARKETING_STRATEGY_GENERATION",
    )

    try:
        result = (
            MarketingStrategyResult.model_validate(
                raw
            )
        )
    except ValidationError as failure:
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID",
            "AI_RESULT_INVALID",
            502,
            False,
        ) from failure

    result.evidenceRefs = _canonicalize_evidence_refs(result.evidenceRefs, allowed_refs)

    return result.model_dump(mode="json")
