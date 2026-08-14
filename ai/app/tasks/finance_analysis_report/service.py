import json

from pydantic import ValidationError

from app.providers import ProviderFailure, execute_structured_prompt
from app.tasks.finance_analysis_report.models import (
    FinanceAnalysisReportInput,
    FinanceAnalysisReportResult,
)


SYSTEM_PROMPT = """You are a Korean financial analyst. Analyze only the supplied deterministic
calculation and Monte Carlo numbers. Do not invent market facts, legal facts, prices, probabilities,
or guarantees. Return one strict JSON object with headline, findings, cautions, recommendedActions,
disclaimer, source=AI_GENERATED_REPORT, providerStatus=SUCCEEDED, safeFailureReason=null.
Write concise Korean. Mention uncertainty and that the result is assumption-based."""


async def execute_finance_analysis_report(task_input: dict) -> dict:
    try:
        value = FinanceAnalysisReportInput.model_validate(task_input)
    except ValidationError as failure:
        raise ProviderFailure("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", 400, False) from failure
    raw = await execute_structured_prompt(
        SYSTEM_PROMPT,
        json.dumps(value.deterministicResult, ensure_ascii=False, sort_keys=True),
        response_schema=FinanceAnalysisReportResult.model_json_schema(),
        schema_name="finance_analysis_report_v1",
        task_type="FINANCE_ANALYSIS_REPORT",
    )
    try:
        return FinanceAnalysisReportResult.model_validate(raw).model_dump(mode="json")
    except ValidationError as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
