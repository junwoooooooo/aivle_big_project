from typing import Annotated

from fastapi import APIRouter, File, Form, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from pydantic import ValidationError

from app.models.contracts import MarketingBannerResult
from app.models.marketing import (
    AdvertisingMood,
    BannerFormat,
    MarketingBannerInput,
    MarketingBannerRequest,
)
from app.request_context import current_request_id
from app.services.banner_service import create_mock_banner
from app.services.prompt_service import build_banner_prompt
from app.utils.image_validator import read_and_validate_image


router = APIRouter(
    prefix="/api/v1/marketing/banners",
    tags=["Marketing Banner"],
)


def parse_keywords(keyword_text: str) -> list[str]:
    """Convert a comma-separated keyword field into normalized values."""

    return [
        keyword.strip()
        for keyword in keyword_text.split(",")
        if keyword.strip()
    ]


@router.post(
    "/generate",
    response_model=MarketingBannerResult,
)
async def generate_banner(
    request: Request,
    promotion_name: Annotated[str, Form(min_length=1, max_length=100)],
    main_banner: Annotated[str, Form(min_length=1, max_length=80)],
    supporting_copy: Annotated[str, Form(min_length=1, max_length=150)],
    mood: Annotated[AdvertisingMood, Form()],
    banner_format: Annotated[BannerFormat, Form()],
    image: Annotated[UploadFile, File()],
    emphasis_keywords: Annotated[str, Form()] = "",
):
    """Create the preserved AIdev marketing Mock result."""

    try:
        request_data = MarketingBannerRequest(
            promotion_name=promotion_name,
            main_banner=main_banner,
            supporting_copy=supporting_copy,
            mood=mood,
            banner_format=banner_format,
            emphasis_keywords=parse_keywords(emphasis_keywords),
        )
    except ValidationError as error:
        raise RequestValidationError(error.errors()) from error

    banner_prompt = build_banner_prompt(request_data)
    image_bytes = await read_and_validate_image(image)
    mock_banner = create_mock_banner(
        image_bytes=image_bytes,
        original_filename=image.filename or "image.png",
    )
    preview_url = (
        str(request.base_url).rstrip("/")
        + mock_banner["preview_path"]
    )

    return MarketingBannerResult(
        status="completed",
        message="Mock 배너를 생성했습니다.",
        data=MarketingBannerInput(
            **request_data.model_dump(mode="python")
        ),
        prompt_preview=banner_prompt,
        banner={
            "banner_id": mock_banner["banner_id"],
            "preview_url": preview_url,
            "mock": True,
        },
        image={
            "original_filename": image.filename,
            "content_type": image.content_type,
            "size": len(image_bytes),
        },
        request_id=current_request_id(request),
    )
