from app.models.marketing import (
    AdvertisingMood,
    BannerFormat,
    MarketingBannerRequest,
)
from app.services.prompt_service import (
    FORMAT_GUIDES,
    MOOD_GUIDES,
    build_banner_prompt,
)


def test_prompt_contains_inputs_and_mood_format_guides():
    request = MarketingBannerRequest(
        promotion_name="봄맞이 행사",
        main_banner="새로운 시작",
        supporting_copy="오늘만 제공되는 혜택",
        mood=AdvertisingMood.BRIGHT_FRIENDLY,
        banner_format=BannerFormat.SQUARE,
        emphasis_keywords=["봄", "혜택"],
    )

    prompt = build_banner_prompt(request)

    assert request.promotion_name in prompt
    assert request.main_banner in prompt
    assert request.supporting_copy in prompt
    assert "봄, 혜택" in prompt
    assert request.mood.value in prompt
    assert MOOD_GUIDES[request.mood] in prompt
    assert request.banner_format.value in prompt
    assert FORMAT_GUIDES[request.banner_format] in prompt
