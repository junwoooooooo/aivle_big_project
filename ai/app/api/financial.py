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
    system = """당신은 한국어 재무 분석가다. 제공된 계산 수치만 분석하고 시장 사실, 법률 사실, 가격 또는 보장을 만들어내지 않는다.
headline, findings, cautions, recommendedActions, disclaimer를 가진 JSON 하나만 반환한다. 모든 값과 모든 문장을 반드시 자연스러운 한국어로 작성한다.
영문 제목, 영문 문장, 'Base scenario', 'Total revenue', 'Operating profit', 'Monte Carlo' 같은 영문 표현을 출력하지 않는다.
불확실성과 입력 가정 기반 분석임을 명시한다."""
    result = await execute_structured_prompt(system, str(body.input))
    return FinancialReportResponse.model_validate(result)
