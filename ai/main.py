from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from pydantic import BaseModel

from legal import service as legal_service

app = FastAPI(
    title="AIVLE Test AI Server",
    version="0.1.0"
)

# 법령 조사 파이프라인 (POST /legal-review, GET /legal/health)
app.include_router(legal_service.router)
# APIRouter에는 예외 처리기를 달 수 없어 앱에 등록한다.
# Spring이 보낸 요청 모양이 안 맞을 때 원인을 로그로 남긴다.
app.add_exception_handler(
    RequestValidationError, legal_service.log_validation_error
)


# AI 서버 실행 상태 확인
@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "service": "ai-server"
    }


class TestRequest(BaseModel):
    message: str


# Spring Boot가 보낸 값을 그대로 돌려주는 테스트 API
@app.post("/api/v1/test")
def connection_test(request: TestRequest):
    return {
        "success": True,
        "received_message": request.message,
        "reply": f"AI 서버가 '{request.message}'를 정상적으로 받았습니다."
    }