from io import BytesIO
from pathlib import Path

from PIL import (
    Image,
    ImageDraw,
    ImageFont,
    ImageStat,
    UnidentifiedImageError,
)

from app.models.marketing import BannerFormat


AI_ROOT_DIR = Path(__file__).resolve().parents[2]

FONT_DIR = AI_ROOT_DIR / "assets" / "fonts"

BOLD_FONT_PATH = FONT_DIR / "NotoSansKR-Bold.ttf"
REGULAR_FONT_PATH = FONT_DIR / "NotoSansKR-Regular.ttf"


class BannerTextCompositionError(RuntimeError):
    """배너 문구 합성 과정에서 발생한 오류."""

    pass


def _load_font(
    font_path: Path,
    size: int,
) -> ImageFont.FreeTypeFont:
    """지정된 한글 폰트를 불러온다."""

    if not font_path.exists():
        raise BannerTextCompositionError(
            f"폰트 파일을 찾을 수 없습니다: {font_path}"
        )

    font = ImageFont.truetype(
        str(font_path),
        size=size,
    )
    # 일부 환경에서 폰트 파일의 글자 높이를 정상적으로
    # 계산하지 못하는 경우에는 굵은 폰트로 안전하게 대체한다.
    font_bbox = font.getbbox("한글Ag")

    if (
        font_bbox[3] <= font_bbox[1]
        and font_path != BOLD_FONT_PATH
    ):
        return ImageFont.truetype(
            str(BOLD_FONT_PATH),
            size=size,
        )

    return font

def _get_text_width(
    draw: ImageDraw.ImageDraw,
    text: str,
    font: ImageFont.FreeTypeFont,
) -> int:
    """문구의 실제 픽셀 너비를 계산한다."""

    left, _, right, _ = draw.textbbox(
        (0, 0),
        text,
        font=font,
    )

    return right - left


def _wrap_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    font: ImageFont.FreeTypeFont,
    max_width: int,
) -> list[str]:
    """
    문구를 지정된 픽셀 너비에 맞춰 줄바꿈한다.
    먼저 띄어쓰기 단위로 나누고, 한 단어가 영역보다 길 때만
    글자 단위로 나눈다.
    """

    normalized_text = " ".join(text.split())

    if not normalized_text:
        return [""]

    lines: list[str] = []
    current_line = ""

    for word in normalized_text.split():
        candidate = (
            f"{current_line} {word}"
            if current_line
            else word
        )

        if _get_text_width(draw, candidate, font) <= max_width:
            current_line = candidate
            continue

        if current_line:
            lines.append(current_line)
            current_line = ""

        if _get_text_width(draw, word, font) <= max_width:
            current_line = word
            continue

        # 한 단어가 문구 영역보다 긴 경우에만 글자 단위로 나눈다.
        word_fragment = ""

        for character in word:
            fragment_candidate = word_fragment + character

            if (
                _get_text_width(
                    draw,
                    fragment_candidate,
                    font,
                )
                <= max_width
            ):
                word_fragment = fragment_candidate
                continue

            if word_fragment:
                lines.append(word_fragment)

            word_fragment = character

        current_line = word_fragment

    if current_line:
        lines.append(current_line)

    return lines


def _fit_multiline_text(
    draw: ImageDraw.ImageDraw,
    text: str,
    font_path: Path,
    max_width: int,
    max_lines: int,
    maximum_size: int,
    minimum_size: int,
) -> tuple[ImageFont.FreeTypeFont, list[str], int]:
    """
    문구가 지정된 줄 수 안에 들어오도록
    폰트 크기를 자동으로 줄인다.
    """

    for font_size in range(
        maximum_size,
        minimum_size - 1,
        -2,
    ):
        font = _load_font(font_path, font_size)

        lines = _wrap_text(
            draw=draw,
            text=text,
            font=font,
            max_width=max_width,
        )

        if len(lines) <= max_lines:
            line_bbox = draw.textbbox(
                (0, 0),
                "한글Ag",
                font=font,
            )

            line_height = line_bbox[3] - line_bbox[1]

            return font, lines, line_height

    # 최소 글자 크기에서도 지정된 줄 수를 넘으면
    # 마지막 줄 끝에 말줄임표를 붙인다.
    font = _load_font(font_path, minimum_size)

    all_lines = _wrap_text(
        draw=draw,
        text=text,
        font=font,
        max_width=max_width,
    )

    lines = all_lines[:max_lines]

    if len(all_lines) > max_lines and lines:
        last_line = lines[-1]

        while (
            last_line
            and _get_text_width(
                draw,
                last_line + "…",
                font,
            )
            > max_width
        ):
            last_line = last_line[:-1]

        lines[-1] = last_line.rstrip() + "…"

    line_bbox = draw.textbbox(
        (0, 0),
        "한글Ag",
        font=font,
    )

    line_height = line_bbox[3] - line_bbox[1]

    return font, lines, line_height


def _get_text_area(
    width: int,
    height: int,
    banner_format: BannerFormat,
) -> tuple[int, int, int, int]:
    """
    상품과 겹치지 않도록 배너 형식별 문구 안전 영역을 반환한다.

    이미지 생성 프롬프트에서 지정한 상품 위치와 동일한 방향으로
    가로형은 왼쪽, 정사각형은 왼쪽 위, 세로형은 위쪽을 사용한다.
    """

    if banner_format == BannerFormat.LANDSCAPE:
        return (
            int(width * 0.055),
            int(height * 0.12),
            int(width * 0.45),
            int(height * 0.88),
        )

    if banner_format == BannerFormat.SQUARE:
        return (
            int(width * 0.065),
            int(height * 0.075),
            int(width * 0.58),
            int(height * 0.48),
        )

    return (
        int(width * 0.07),
        int(height * 0.065),
        int(width * 0.93),
        int(height * 0.38),
    )

def _get_text_colors(
    image: Image.Image,
    text_area: tuple[int, int, int, int],
) -> tuple[
    tuple[int, int, int, int],
    tuple[int, int, int, int],
    tuple[int, int, int, int],
]:
    """
    문구 영역의 평균 밝기에 따라 기본 글자색과 보조 글자색,
    외곽선 색을 선택한다.
    """

    sampled_area = image.crop(
        text_area
    ).convert("RGB")

    red, green, blue = ImageStat.Stat(
        sampled_area
    ).mean

    luminance = (
        0.2126 * red
        + 0.7152 * green
        + 0.0722 * blue
    )

    if luminance >= 145:
        return (
            (28, 31, 35, 255),
            (67, 71, 76, 255),
            (255, 255, 255, 255),
        )

    return (
        (255, 255, 255, 255),
        (235, 238, 240, 255),
        (20, 22, 25, 255),
    )

def add_text_to_banner(
    *,
    image_bytes: bytes,
    badge:str,
    headline:str,
    subheadline:str,
    banner_format: BannerFormat,
) -> bytes:
    """
    AI가 생성한 배경 이미지 위에
    프로모션 이름, 메인 문구, 보조 문구를 합성한다.

     상품을 가리는 큰 배경 패널과 CTA 버튼은 사용하지 않는다.
    """

    try:
        source_image = Image.open(
            BytesIO(image_bytes)
        ).convert("RGBA")

    except (UnidentifiedImageError, OSError) as error:
        raise BannerTextCompositionError(
            "문구를 합성할 이미지가 올바르지 않습니다."
        ) from error

    width, height = source_image.size
    minimum_edge = min(width, height)

    text_area = _get_text_area(
        width=width,
        height=height,
        banner_format=banner_format,
    )

    (
        primary_color,
        secondary_color,
        outline_color,
    ) = _get_text_colors(
        image=source_image,
        text_area=text_area,
    )

    composed_image = source_image.copy()
    draw = ImageDraw.Draw(composed_image)

    (
        text_left,
        text_top,
        text_right,
        _,
    ) = text_area

    text_x = text_left
    text_y = text_top
    content_width = text_right - text_left

    # 프로모션 이름을 작은 포인트 배지로 표시한다.
    badge_font, badge_lines, _ = _fit_multiline_text(
        draw=draw,
        text=badge,
        font_path=BOLD_FONT_PATH,
        max_width=int(content_width * 0.85),
        max_lines=1,
        maximum_size=int(minimum_edge * 0.030),
        minimum_size=20,
    )

    badge_text = badge_lines[0]
    badge_bbox = draw.textbbox(
        (0, 0),
        badge_text,
        font=badge_font,
    )

    badge_padding_x = max(
        14,
        int(minimum_edge * 0.014),
    )
    badge_padding_y = max(
        8,
        int(minimum_edge * 0.008),
    )
    badge_width = (
        badge_bbox[2]
        - badge_bbox[0]
        + badge_padding_x * 2
    )
    badge_height = (
        badge_bbox[3]
        - badge_bbox[1]
        + badge_padding_y * 2
    )

    badge_box = (
        text_x,
        text_y,
        text_x + badge_width,
        text_y + badge_height,
    )

    draw.rounded_rectangle(
        badge_box,
        radius=badge_height // 2,
        fill=(197, 226, 79, 255),
    )

    draw.text(
        (
            text_x + badge_padding_x,
            text_y + badge_padding_y - badge_bbox[1],
        ),
        badge_text,
        font=badge_font,
        fill=(26, 35, 28, 255),
    )

    text_y += (
        badge_height
        + int(minimum_edge * 0.040)
    )

    # 프로모션 이름
    # 핵심 헤드라인은 최대 두 줄만 사용한다.
    main_font, main_lines, main_line_height = (
        _fit_multiline_text(
            draw=draw,
            text=headline,
            font_path=BOLD_FONT_PATH,
            max_width=content_width,
            max_lines=2,
            maximum_size=int(minimum_edge * 0.062),
            minimum_size=34,
        )
    )

    main_line_spacing = max(
        8,
        int(main_line_height * 0.18),
    )

    for line in main_lines:
        draw.text(
            (text_x, text_y),
            line,
            font=main_font,
            fill=primary_color,
            stroke_width=max(
                1,
                int(minimum_edge * 0.0015),
            ),
            stroke_fill=outline_color,
        )

        text_y += (
            main_line_height
            + main_line_spacing
        )

    text_y += int(minimum_edge * 0.030)

    # 보조 문구는 상품 설명을 방해하지 않도록 작고 짧게 표시한다.
    supporting_font, supporting_lines, supporting_line_height = (
        _fit_multiline_text(
            draw=draw,
            text=subheadline,
            font_path=REGULAR_FONT_PATH,
            max_width=content_width,
            max_lines=2,
            maximum_size=int(minimum_edge * 0.029),
            minimum_size=20,
        )
    )


    supporting_spacing = max(
        6,
        int(supporting_line_height * 0.20),
    )

    for line in supporting_lines:
        draw.text(
            (text_x, text_y),
            line,
            font=supporting_font,
            fill=secondary_color,
            stroke_width=1,
            stroke_fill=outline_color,
        )

        text_y += (
            supporting_line_height
            + supporting_spacing
        )

    # 최종 결과를 JPEG 바이트로 반환한다.
    output_buffer = BytesIO()

    composed_image.convert("RGB").save(
        output_buffer,
        format="JPEG",
        quality=94,
        optimize=True,
    )

    return output_buffer.getvalue()
