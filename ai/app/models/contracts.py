from pydantic import BaseModel

from app.models.marketing import MarketingBannerInput


class HealthResponse(BaseModel):
    status: str
    service: str
    request_id: str


class EchoResponse(BaseModel):
    success: bool
    received_message: str
    reply: str
    request_id: str


class MarketingBannerInfo(BaseModel):
    banner_id: str
    preview_url: str
    mock: bool


class UploadedImageInfo(BaseModel):
    original_filename: str | None
    content_type: str | None
    size: int


class MarketingBannerResult(BaseModel):
    status: str
    message: str
    data: MarketingBannerInput
    prompt_preview: str
    banner: MarketingBannerInfo
    image: UploadedImageInfo
    request_id: str


class AiServerErrorDetail(BaseModel):
    code: str
    message: str
    retryable: bool


class AiServerErrorResponse(BaseModel):
    request_id: str
    error: AiServerErrorDetail
