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

    schema = (
        MarketingStrategyResult.model_json_schema()
    )

    if lint_provider_schema(schema):
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID",
            "PROVIDER_RESPONSE_SCHEMA_REJECTED",
            502,
            False,
        )

    raw = await execute_structured_prompt(
        SYSTEM_PROMPT,
        json.dumps(
            value.model_dump(mode="json"),
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

    allowed_refs = {
        f"{item.type}:{item.id}"
        for item in value.sourceManifest
    }

    if not set(result.evidenceRefs).issubset(
        allowed_refs
    ):
        raise ProviderFailure(
            "RESULT_SCHEMA_INVALID",
            "AI_EVIDENCE_REFERENCE_INVALID",
            502,
            False,
        )

    return result.model_dump(mode="json")