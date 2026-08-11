import asyncio
import base64
from io import BytesIO

import pytest
from PIL import Image
from pydantic import ValidationError

from app.models.marketing import BannerFormat, MarketingBannerRequest
from app.models.marketing_copy import GeneratedMarketingCopy
from app.providers import ProviderFailure
from app.services.banner_text_service import BOLD_FONT_PATH, REGULAR_FONT_PATH, _wrap_text, add_text_to_banner
from app.services.prompt_service import build_banner_prompt
from app.tasks.marketing_visual.models import MarketingVisualInput
from app.tasks.marketing_visual.service import execute_marketing_visual
from app.utils.image_validator import validate_image_bytes


def image_bytes(format_name="PNG", size=(320, 240)):
    output = BytesIO(); Image.new("RGB", size, "white").save(output, format=format_name); return output.getvalue()


def payload(source_bytes=None):
    data = image_bytes() if source_bytes is None else source_bytes
    return {
        "contract": "marketing-visual-generation-input-v1",
        "marketingContentId": "content-1", "marketingRevisionId": "revision-1",
        "source": {
            "contract": "marketing-source-snapshot-v1", "schemaVersion": "2.0", "snapshotId": "source-1",
            "hash": "sha256:" + "1" * 64, "createdAt": "2026-08-11T00:00:00Z", "projectId": 1,
            "selectionId": 1, "conceptId": "concept-1", "marketAnalysisSeedSnapshotId": "seed-1",
            "marketAnalysisSeedSnapshotHash": "sha256:" + "2" * 64, "conceptName": "한글 상품",
            "targetSegment": "고객", "problem": "문제", "valueProposition": "가치", "positioning": "포지셔닝",
            "keyFeatures": ["특징"], "pricing": "가격", "targetRegion": "대한민국", "revenueModel": "판매",
            "price": "확인 필요", "channels": ["온라인"], "competitorDifferentiators": ["차별점"],
            "preMarketSomShare": {"targetSharePercent": 1, "horizonYears": 1, "rationale": "", "assumptions": []},
            "preMarketSom": {"amount": 1, "currency": "KRW", "period": "", "calculationBasis": "", "assumptions": [], "confidence": ""},
            "legalStatus": "검토", "allowedClaims": ["편리함"], "prohibitedClaims": ["100% 보장"],
            "requiredDisclosures": ["개인차가 있습니다"], "requiredControls": ["과장 금지"],
            "communicationRequiredControls": [], "officialEvidenceReferences": [], "sourceSnapshotHash": "sha256:" + "3" * 64,
        },
        "content": {"contract": "marketing-content-result-v1", "contentType": "BANNER", "title": "한글 제목",
            "body": "한글 본문", "callToAction": "확인하기", "hashtags": [], "imageBrief": "상품 중심",
            "legalReview": {"compliant": True, "warnings": [], "requiredDisclosuresApplied": ["개인차가 있습니다"]}, "artifactRefs": []},
        "sourceImage": {"artifactId": "artifact-1", "originalFilename": "상품.png", "mediaType": "image/png", "sizeBytes": len(data)},
        "visual": {"promotionName": "여름 행사", "mainBanner": "한글 메인 문구", "supportingCopy": "한글 보조 문구",
            "mood": "밝고 친근한", "bannerFormat": "가로형 배너", "emphasisKeywords": ["한글", "행사"]},
        "resolvedSourceImage": {"bytesBase64": base64.b64encode(data).decode()},
    }


def test_input_validation_and_prompt_contract():
    parsed = MarketingVisualInput.model_validate(payload())
    prompt = build_banner_prompt(MarketingBannerRequest(
        promotion_name=parsed.visual.promotionName, main_banner=parsed.visual.mainBanner,
        supporting_copy=parsed.visual.supportingCopy, mood=parsed.visual.mood,
        banner_format=parsed.visual.bannerFormat, emphasis_keywords=parsed.visual.emphasisKeywords))
    assert "한글 메인 문구" in prompt and "글자, 로고, 워터마크를 직접 생성하지 않는다" in prompt
    invalid = payload(); invalid["visual"]["mood"] = "없는 분위기"
    with pytest.raises(ValidationError): MarketingVisualInput.model_validate(invalid)


def test_image_validation_composition_wrapping_and_fonts():
    assert validate_image_bytes("상품.png", "image/png", image_bytes())
    assert BOLD_FONT_PATH.exists() and REGULAR_FONT_PATH.exists()
    banner = add_text_to_banner(image_bytes=image_bytes(size=(1536, 1024)), badge="특별 행사",
        headline="아주 긴 한글 헤드라인도 자동으로 줄바꿈됩니다", subheadline="가독성과 대비를 유지하는 한글 보조 문구입니다",
        banner_format=BannerFormat.LANDSCAPE)
    assert banner.startswith(b"\xff\xd8\xff")
    draw = __import__("PIL.ImageDraw", fromlist=["ImageDraw"]).Draw(Image.new("RGB", (300, 100)))
    font = __import__("PIL.ImageFont", fromlist=["ImageFont"]).truetype(str(REGULAR_FONT_PATH), 20)
    assert len(_wrap_text(draw, "띄어쓰기 없는아주긴한글문구테스트", font, 80)) > 1


def test_execution_decodes_provider_image_and_preserves_legal_context():
    captured = {}
    def copy_generator(request, legal):
        captured["legal"] = legal
        return GeneratedMarketingCopy(badge="행사", headline="편리한 한글 상품", subheadline="지금 확인하세요")
    def banner_generator(**kwargs):
        captured["prompt"] = kwargs["prompt"]
        return {"image_bytes": b"\xff\xd8\xff" + b"visual", "model": "gpt-image-2", "size": "1536x1024", "quality": "high"}
    result = asyncio.run(execute_marketing_visual(payload(), copy_generator=copy_generator, banner_generator=banner_generator))
    assert base64.b64decode(result["banner"]["imageBase64"]).startswith(b"\xff\xd8\xff")
    assert captured["legal"]["prohibitedClaims"] == ["100% 보장"]
    assert result["legalReview"]["requiredDisclosuresApplied"] == ["개인차가 있습니다"]


def test_guardrail_invalid_source_and_invalid_provider_response():
    def prohibited_copy(request, legal):
        return GeneratedMarketingCopy(badge="행사", headline="100% 보장", subheadline="확인")
    with pytest.raises(ProviderFailure, match="SAFETY_POLICY_BLOCKED"):
        asyncio.run(execute_marketing_visual(payload(), copy_generator=prohibited_copy,
            banner_generator=lambda **kwargs: {}))
    with pytest.raises(ProviderFailure, match="SOURCE_IMAGE_INVALID"):
        asyncio.run(execute_marketing_visual(payload(b"not-image"), copy_generator=prohibited_copy,
            banner_generator=lambda **kwargs: {}))
    def safe_copy(request, legal):
        return GeneratedMarketingCopy(badge="행사", headline="한글 상품", subheadline="확인")
    with pytest.raises(ProviderFailure, match="AI_RESULT_INVALID"):
        asyncio.run(execute_marketing_visual(payload(), copy_generator=safe_copy,
            banner_generator=lambda **kwargs: {"image_bytes": b"bad", "model": "gpt-image-2", "size": "1536x1024", "quality": "high"}))
