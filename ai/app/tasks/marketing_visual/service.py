import asyncio
import base64
import binascii
import os
from collections.abc import Callable

from app.api.errors import ApiHttpException
from app.models.marketing import MarketingBannerRequest
from app.providers import ProviderFailure
from app.services.marketing_copy_service import (
    MarketingCopyGenerationError,
    generate_marketing_copy,
)
from app.services.openai_banner_service import (
    OpenAIBannerGenerationError,
    generate_banner_with_openai,
)
from app.services.prompt_service import build_banner_prompt
from app.tasks.marketing_visual.models import MarketingVisualInput, MarketingVisualResult
from app.utils.image_validator import validate_image_bytes


async def execute_marketing_visual(
    payload: dict,
    *,
    copy_generator: Callable = generate_marketing_copy,
    banner_generator: Callable = generate_banner_with_openai,
) -> dict:
    task_input = MarketingVisualInput.model_validate(payload)
    try:
        source_bytes = base64.b64decode(
            task_input.resolvedSourceImage.bytesBase64,
            validate=True,
        )
    except (binascii.Error, ValueError) as failure:
        raise ProviderFailure("EXECUTION_FAILED", "SOURCE_IMAGE_INVALID", 422, False) from failure
    try:
        source_bytes = validate_image_bytes(
            task_input.sourceImage.originalFilename,
            task_input.sourceImage.mediaType,
            source_bytes,
        )
    except ApiHttpException as failure:
        raise ProviderFailure("EXECUTION_FAILED", "SOURCE_IMAGE_INVALID", 422, False) from failure

    request = MarketingBannerRequest(
        promotion_name=task_input.visual.promotionName,
        main_banner=task_input.visual.mainBanner,
        supporting_copy=task_input.visual.supportingCopy,
        mood=task_input.visual.mood,
        banner_format=task_input.visual.bannerFormat,
        emphasis_keywords=task_input.visual.emphasisKeywords,
    )
    legal_context = {
        "allowedClaims": task_input.source.allowedClaims,
        "prohibitedClaims": task_input.source.prohibitedClaims,
        "requiredDisclosures": task_input.source.requiredDisclosures,
        "requiredControls": task_input.source.requiredControls,
    }
    if not (os.getenv("AI_API_KEY") or os.getenv("OPENAI_API_KEY")) and copy_generator is generate_marketing_copy:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    try:
        generated_copy = await asyncio.to_thread(copy_generator, request, legal_context)
    except MarketingCopyGenerationError as failure:
        raise ProviderFailure("EXECUTION_FAILED", "COPY_GENERATION_FAILED", 502, True) from failure

    rendered = " ".join((generated_copy.badge, generated_copy.headline, generated_copy.subheadline)).casefold()
    if any(claim.strip().casefold() in rendered for claim in task_input.source.prohibitedClaims if claim.strip()):
        raise ProviderFailure("EXECUTION_FAILED", "SAFETY_POLICY_BLOCKED", 422, False)

    prompt = build_banner_prompt(request)
    try:
        banner = await asyncio.to_thread(
            banner_generator,
            image_bytes=source_bytes,
            original_filename=task_input.sourceImage.originalFilename,
            prompt=prompt,
            banner_format=request.banner_format,
            marketing_copy=generated_copy,
        )
    except OpenAIBannerGenerationError as failure:
        reason = "IMAGE_COMPOSITION_FAILED" if "합성" in str(failure) else "IMAGE_GENERATION_FAILED"
        raise ProviderFailure("EXECUTION_FAILED", reason, 502, reason == "IMAGE_GENERATION_FAILED") from failure

    image_bytes = banner.get("image_bytes")
    if not isinstance(image_bytes, bytes) or not image_bytes.startswith(b"\xff\xd8\xff") or len(image_bytes) > 10 * 1024 * 1024:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False)
    result = MarketingVisualResult(
        contract="marketing-visual-generation-result-v1",
        generatedCopy=generated_copy.model_dump(mode="json"),
        promptPreview=prompt,
        banner={
            "imageBase64": base64.b64encode(image_bytes).decode("ascii"),
            "mediaType": "image/jpeg",
            "model": banner["model"],
            "size": banner["size"],
            "quality": banner["quality"],
        },
        legalReview={
            "compliant": True,
            "requiredDisclosuresApplied": task_input.source.requiredDisclosures,
            "requiredControlsApplied": task_input.source.requiredControls,
        },
    )
    return result.model_dump(mode="json")
