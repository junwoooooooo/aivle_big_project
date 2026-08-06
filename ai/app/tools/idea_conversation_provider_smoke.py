import asyncio
import json
import os

from app.models.idea_conversation_provider import (
    ProviderOpportunityBriefDraftResult,
    lint_openai_strict_schema,
)
from app.models.journey import OpportunityBriefDraftResult
from app.services.journey_provider import ProviderFailure, execute_journey_task


SUPPORTED_FIELDS = [
    "problem", "targetCustomer", "beneficiaries", "usageContext", "desiredOutcome",
    "targetRegion", "fixedConstraints", "preferredConstraints", "openDecisions",
    "assumptions", "prohibitedApproaches", "regulatorySensitiveActivities",
]


def synthetic_input() -> dict:
    return {
        "schemaVersion": "1.0",
        "conversationContract": "opportunity-brief-v1",
        "projectId": 1,
        "ownerId": 1,
        "conversationId": 1,
        "sourceMessageId": 1,
        "briefVersionId": None,
        "locale": "ko-KR",
        "supportedFields": SUPPORTED_FIELDS,
        "sourceRules": {
            "aiAllowed": ["SOURCE_EXTRACTED", "AI_PROPOSED", "MISSING"],
            "neverAutoConfirm": True,
            "neverDefaultAssumption": True,
        },
        "messages": [{
            "messageId": 1,
            "sequence": 1,
            "role": "USER",
            "messageType": "TEXT",
            "content": "도서관 좌석 이용 불편을 줄이는 예약 서비스를 검토하고 있습니다.",
            "envelope": None,
        }],
        "attachments": [],
        "currentBrief": None,
        "legacyIdeaSource": None,
    }


def main() -> int:
    provider = os.getenv("AI_PROVIDER", "").strip().lower() or "UNCONFIGURED"
    model = os.getenv("AI_MODEL", "").strip() or "UNCONFIGURED"
    schema_name = "opportunity_brief_draft_v1"

    schema = ProviderOpportunityBriefDraftResult.model_json_schema()
    schema_issues = lint_openai_strict_schema(schema)
    if schema_issues:
        print("upstreamStatus=NOT_CALLED")
        print("safeErrorType=LOCAL_SCHEMA_INVALID")
        print("safeErrorParam=response_format")
        print(f"schemaName={schema_name}")
        return 1

    try:
        result = asyncio.run(execute_journey_task(
            "IDEA_CONVERSATION_TURN",
            json.dumps(synthetic_input(), ensure_ascii=False, separators=(",", ":")),
        ))
        OpportunityBriefDraftResult.model_validate(result)
    except ProviderFailure as failure:
        print(f"upstreamStatus={failure.upstream_status or failure.status_code}")
        print(f"safeErrorType={failure.provider_error_type or failure.code}")
        print(f"safeErrorParam={failure.provider_error_param or failure.reason}")
        print(f"schemaName={failure.schema_name or schema_name}")
        return 1
    except Exception:
        print("upstreamStatus=NOT_AVAILABLE")
        print("safeErrorType=SMOKE_VALIDATION_FAILED")
        print("safeErrorParam=internal_contract")
        print(f"schemaName={schema_name}")
        return 1

    print(f"provider={provider}")
    print(f"model={model}")
    print("responseFormat=json_schema")
    print("providerStatus=2xx")
    print("providerSchemaValidation=PASSED")
    print("domainMappingValidation=PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
