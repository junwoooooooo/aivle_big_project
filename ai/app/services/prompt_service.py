from app.models.marketing import (
    AdvertisingMood,
    BannerFormat,
    MarketingBannerRequest,
)


# 광고 분위기별 이미지 표현 방식
MOOD_GUIDES = {
    AdvertisingMood.TRUSTWORTHY: (
        "안정적인 구도, 차분한 색상, 신뢰감을 주는 전문적인 디자인"
    ),
    AdvertisingMood.BRIGHT_FRIENDLY: (
        "밝은 색상, 부드러운 조명, 친근하고 활기찬 디자인"
    ),
    AdvertisingMood.EMOTIONAL: (
        "따뜻한 조명, 감성적인 색감, 이야기성이 느껴지는 디자인"
    ),
    AdvertisingMood.PROFESSIONAL: (
        "정돈된 구도, 절제된 색상, 전문적인 기업 광고 디자인"
    ),
    AdvertisingMood.BOLD: (
        "강한 대비, 역동적인 구도, 시선을 끄는 강렬한 디자인"
    ),
    AdvertisingMood.LUXURIOUS: (
        "고급스러운 색상, 세련된 조명, 프리미엄 광고 디자인"
    ),
    AdvertisingMood.MINIMAL: (
        "불필요한 요소를 제거한 단순한 구도와 넓은 여백"
    ),
}


# 배너 형식별 구성 방식
FORMAT_GUIDES = {
    BannerFormat.LANDSCAPE: (
        "상품을 화면 오른쪽 45~50% 영역에 배치하고, "
        "왼쪽 40% 영역은 문구를 넣을 수 있도록 단순한 배경과 "
        "넓은 여백으로 비워 둔다."
    ),
    BannerFormat.SQUARE: (
        "상품을 화면 오른쪽 아래 또는 아래쪽 45~55% 영역에 "
        "배치하고, 왼쪽 위 영역은 문구를 넣을 수 있도록 "
        "단순한 배경과 넓은 여백으로 비워 둔다."
    ),
    BannerFormat.PORTRAIT: (
        "상품을 화면 아래쪽 50~55% 영역에 배치하고, "
        "위쪽 35~40% 영역은 문구를 넣을 수 있도록 "
        "단순한 배경과 넓은 여백으로 비워 둔다."
    ),
}


def build_banner_prompt(
    request: MarketingBannerRequest
) -> str:
    """입력값을 이미지 생성용 프롬프트로 변환합니다."""

    mood_guide = MOOD_GUIDES[
        request.mood
    ]

    format_guide = FORMAT_GUIDES[
        request.banner_format
    ]

    keywords = (
        ", ".join(request.emphasis_keywords)
        if request.emphasis_keywords
        else "없음"
    )

    return f"""
광고 프로모션용 배경 이미지를 제작한다.

[프로모션 정보]
- 프로모션 이름: {request.promotion_name}
- 메인 메시지: {request.main_banner}
- 보조 메시지: {request.supporting_copy}
- 강조 키워드: {keywords}

[디자인 방향]
- 광고 분위기: {request.mood.value}
- 분위기 표현: {mood_guide}
- 배너 형식: {request.banner_format.value}
- 화면 구성: {format_guide}

[필수 조건]
- 업로드한 이미지를 광고의 핵심 피사체로 활용한다.
- 피사체가 잘리거나 왜곡되지 않도록 한다.
- 이미지 안에 글자, 로고, 워터마크를 직접 생성하지 않는다.
- 실제 광고 배너에 사용할 수 있는 깔끔한 배경으로 제작한다.
- 지정한 문구 영역에는 상품이나 장식물을 배치하지 않는다.
- 상품이 문구 영역을 침범하지 않도록 명확한 여백을 확보한다.
- 상품이 화면 전체를 가득 채우지 않도록 크기를 조절한다.
- 국내 라이프스타일 커머스 프로모션처럼 밝고 정돈된 상품 광고
  비주얼로 제작한다.
- 글자를 올리기 위한 크고 어두운 패널은 만들지 않는다.
""".strip()