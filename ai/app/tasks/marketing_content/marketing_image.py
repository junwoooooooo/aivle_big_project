import asyncio
import base64
import json
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import quote

import httpx
from openai import AuthenticationError, OpenAI, OpenAIError, PermissionDeniedError, RateLimitError

from app.providers import ProviderFailure
from app.tasks.marketing_content.models import MarketingContentInput, MarketingContentResult


MAX_IMAGE_BYTES = 20 * 1024 * 1024


def _image_size(content_type: str) -> str:
    if content_type in {"BANNER", "LANDING_PAGE", "BLOG_INTRO", "EMAIL"}:
        return "1536x1024"
    if content_type == "POSTER":
        return "1024x1536"
    return "1024x1024"


def _image_prompt(value: MarketingContentInput, result: MarketingContentResult) -> str:
    source = value.source
    request = value.request
    facts = {
        "concept": source.conceptName,
        "targetCustomer": source.targetSegment,
        "problem": source.problem,
        "valueProposition": source.valueProposition,
        "positioning": source.positioning,
        "keyFeatures": source.keyFeatures,
        "differentiators": source.competitorDifferentiators,
        "channel": request.channel,
        "purpose": request.purpose,
        "tone": request.tone,
        "imageBrief": result.imageBrief,
        "headlineMeaning": result.title,
        "prohibitedClaims": source.prohibitedClaims,
        "requiredControls": source.communicationRequiredControls,
    }
    return (
        "Create a polished, photorealistic Korean commercial campaign key visual using only the "
        "provided facts. The final image must look like a professional retail or brand advertisement, "
        "with a clear hero subject, realistic materials, controlled studio lighting, intentional depth, "
        "and generous negative space for copy that will be rendered separately by the application. "
        "Do not draw any words, letters, numbers, logos, watermarks, UI labels, buttons, badges, claims, "
        "or legal text inside the image. Do not imply any prohibited claim. If a reference product image "
        "is provided, preserve the product's recognizable shape, color, proportions, packaging, and "
        "material details while upgrading only the setting, composition, lighting, and campaign mood. "
        "Avoid generic clip-art, collage layouts, excessive props, distorted packaging, and dominant text "
        "areas. Campaign facts:\n" + json.dumps(facts, ensure_ascii=False, sort_keys=True)
    )


async def _download_reference(project_id: int, artifact_id: str) -> tuple[bytes, str]:
    base_url = os.getenv("BACKEND_INTERNAL_BASE_URL", "").strip().rstrip("/")
    token = os.getenv("AI_INTERNAL_SERVICE_TOKEN", "").strip()
    if not base_url or not token:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    url = (f"{base_url}/internal/v1/ai/projects/{project_id}/evidence-artifacts/"
           f"{quote(artifact_id, safe='')}")
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get(url, headers={"X-AI-Internal-Token": token})
    except (httpx.TimeoutException, httpx.NetworkError) as failure:
        raise ProviderFailure("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", 503, True) from failure
    if response.status_code != 200:
        raise ProviderFailure("INVALID_REQUEST", "REFERENCE_RESOLUTION_FAILED", 400, False)
    content_type = response.headers.get("content-type", "").split(";", 1)[0].strip().lower()
    if content_type not in {"image/png", "image/jpeg"} or not 0 < len(response.content) <= MAX_IMAGE_BYTES:
        raise ProviderFailure("INVALID_REQUEST", "REFERENCE_RESOLUTION_FAILED", 400, False)
    return response.content, content_type


def _generate_image_sync(prompt: str, size: str, reference: tuple[bytes, str] | None) -> bytes:
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    model = os.getenv("AI_IMAGE_MODEL", "gpt-image-2").strip() or "gpt-image-2"
    if not api_key:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    client = OpenAI(api_key=api_key, timeout=240.0)
    temporary_path: Path | None = None
    try:
        if reference is None:
            response = client.images.generate(model=model, prompt=prompt, size=size, quality="high",
                output_format="jpeg", output_compression=88, background="opaque", n=1)
        else:
            image_bytes, content_type = reference
            suffix = ".png" if content_type == "image/png" else ".jpg"
            with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as temporary:
                temporary.write(image_bytes)
                temporary_path = Path(temporary.name)
            with temporary_path.open("rb") as image_file:
                response = client.images.edit(model=model, image=image_file, prompt=prompt, size=size,
                    quality="high", output_format="jpeg", output_compression=88,
                    background="opaque", n=1)
        if not response.data or not response.data[0].b64_json:
            raise ProviderFailure("EXECUTION_FAILED", "PERMANENT_EXECUTION_FAILURE", 500, False)
        generated = base64.b64decode(response.data[0].b64_json, validate=True)
        return _validate_generated_jpeg(generated)
    except ProviderFailure:
        raise
    except (AuthenticationError, PermissionDeniedError) as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False) from failure
    except RateLimitError as failure:
        raise ProviderFailure("RATE_LIMITED", "DEPENDENCY_RATE_LIMITED", 429, True) from failure
    except OpenAIError as failure:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", 503, True) from failure
    except (OSError, ValueError) as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    finally:
        if temporary_path is not None:
            temporary_path.unlink(missing_ok=True)


def _validate_generated_jpeg(content: bytes) -> bytes:
    if not 0 < len(content) <= MAX_IMAGE_BYTES or not content.startswith(b"\xff\xd8\xff"):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False)
    return content


async def _upload_image(content: bytes) -> str:
    base_url = os.getenv("BACKEND_INTERNAL_BASE_URL", "").strip().rstrip("/")
    token = os.getenv("AI_INTERNAL_SERVICE_TOKEN", "").strip()
    if not base_url or not token:
        raise ProviderFailure("DEPENDENCY_UNAVAILABLE", "AI_CONFIGURATION_INVALID", 503, False)
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(f"{base_url}/internal/v1/ai/marketing-artifacts", content=content,
                headers={"X-AI-Internal-Token": token, "Content-Type": "image/jpeg"})
    except (httpx.TimeoutException, httpx.NetworkError) as failure:
        raise ProviderFailure("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", 503, True) from failure
    if response.status_code != 201:
        raise ProviderFailure("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", 503, True)
    try:
        artifact_ref = response.json()["artifactRef"]
    except (ValueError, KeyError, TypeError) as failure:
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False) from failure
    if not isinstance(artifact_ref, str) or not re.fullmatch(
            r"ai-artifacts/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\.jpg",
            artifact_ref):
        raise ProviderFailure("RESULT_SCHEMA_INVALID", "AI_RESULT_INVALID", 502, False)
    return artifact_ref


async def generate_and_store_marketing_image(value: MarketingContentInput,
                                             result: MarketingContentResult) -> str:
    reference = None
    if value.request.referenceArtifactId:
        reference = await _download_reference(value.source.projectId, value.request.referenceArtifactId)
    content = await asyncio.to_thread(_generate_image_sync, _image_prompt(value, result),
                                      _image_size(value.request.contentType), reference)
    return await _upload_image(content)
