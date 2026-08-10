from pydantic import BaseModel

class HealthResponse(BaseModel):
    status: str
    service: str
    request_id: str


class AiServerErrorDetail(BaseModel):
    code: str
    message: str
    retryable: bool


class AiServerErrorResponse(BaseModel):
    request_id: str
    error: AiServerErrorDetail
