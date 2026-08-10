import os
from fastapi import APIRouter, Header, HTTPException
from app.models.financial import FinancialReportRequest, FinancialReportResponse
from app.services.journey_provider import execute_structured_prompt

router = APIRouter(prefix="/internal/v1/financial", tags=["Financial AI"])

@router.post("/report", response_model=FinancialReportResponse)
async def create_report(body: FinancialReportRequest, x_internal_api_key: str | None = Header(default=None)):
    expected = os.getenv("AI_INTERNAL_SERVICE_TOKEN", "")
    if not expected or x_internal_api_key != expected:
        raise HTTPException(status_code=401, detail="invalid internal credential")
    system = """You are a Korean financial analyst. Analyze only the supplied calculated numbers; do not invent market facts, legal facts, prices, or guarantees. Return one JSON object with headline, findings, cautions, recommendedActions, disclaimer. Write concise Korean. Mention uncertainty and that the result is assumption-based."""
    result = await execute_structured_prompt(system, str(body.input))
    return FinancialReportResponse.model_validate(result)
