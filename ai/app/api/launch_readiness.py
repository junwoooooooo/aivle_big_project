import os

from fastapi import APIRouter, Header, HTTPException

from app.tasks.professional_readiness import analyze_professional_readiness
from app.tasks.professional_readiness.models import ProfessionalAnalysisRequest


router = APIRouter(prefix="/internal/v1/launch-readiness", tags=["Professional launch readiness AI"])


@router.post("/analyze")
async def analyze(body: ProfessionalAnalysisRequest, x_internal_api_key: str | None = Header(default=None)):
    expected = os.getenv("AI_INTERNAL_SERVICE_TOKEN", "").strip()
    if not expected or x_internal_api_key != expected:
        raise HTTPException(status_code=401, detail="invalid internal API key")
    return await analyze_professional_readiness(body.model_dump(mode="json"))
