import base64
import logging
import os
import tempfile
from pathlib import Path

from dotenv import load_dotenv
from openai import OpenAI, OpenAIError

from app.models.marketing import BannerFormat
from app.models.marketing_copy import GeneratedMarketingCopy
from app.services.banner_text_service import (
    BannerTextCompositionError,
    add_text_to_banner,
)

# ai 프로젝트 최상위 폴더
AI_ROOT_DIR = Path(__file__).resolve().parents[2]

# ai/.env 파일을 명시적으로 불러온다.
load_dotenv(AI_ROOT_DIR / ".env")

logger = logging.getLogger(__name__)


class OpenAIBannerGenerationError(RuntimeError):
    """OpenAI 이미지 생성 과정에서 발생한 오류."""

    pass


def get_banner_size(banner_format: BannerFormat) -> str:
    """
    사용자가 선택한 배너 형식을
    gpt-image-2 이미지 크기로 변환한다.
    """
    size_by_format = {
        BannerFormat.LANDSCAPE: "1536x1024",
        BannerFormat.SQUARE: "1024x1024",
        BannerFormat.PORTRAIT: "1024x1536",
    }

    return size_by_format[banner_format]


def generate_banner_with_openai(
    *,
    image_bytes: bytes,
    original_filename: str,
    prompt: str,
    banner_format: BannerFormat,
    marketing_copy: GeneratedMarketingCopy,
) -> dict[str, object]:
    """
    업로드 이미지를 참고하여 gpt-image-2로
    새로운 광고 배너 이미지를 생성한다.
    """
    api_key = os.getenv("AI_API_KEY") or os.getenv("OPENAI_API_KEY")

    if not api_key:
        raise OpenAIBannerGenerationError(
            "OPENAI_API_KEY가 설정되지 않았습니다."
        )

    # 복잡한 이미지 생성은 시간이 걸릴 수 있으므로
    # 요청 제한 시간을 180초로 설정한다.
    base_url = os.getenv("AI_BASE_URL", "").strip() or None
    client = OpenAI(api_key=api_key, base_url=base_url, timeout=180.0)

    output_size = get_banner_size(banner_format)

    # 업로드 이미지 확장자를 임시 파일에도 유지한다.
    input_suffix = Path(original_filename).suffix.lower()

    if input_suffix not in {".png", ".jpg", ".jpeg", ".webp"}:
        input_suffix = ".jpg"

    temporary_image_path: Path | None = None

    try:
        # OpenAI SDK에 전달하기 위한 임시 이미지 파일을 만든다.
        # Windows에서는 파일을 닫은 뒤 다시 열 수 있도록
        # delete=False를 사용한다.
        with tempfile.NamedTemporaryFile(
            suffix=input_suffix,
            delete=False,
        ) as temporary_file:
            temporary_file.write(image_bytes)
            temporary_image_path = Path(temporary_file.name)

        with temporary_image_path.open("rb") as input_image:
            result = client.images.edit(
                model=os.getenv("MARKETING_IMAGE_MODEL", "gpt-image-2"),
                image=input_image,
                prompt=prompt,
                size=output_size,
                quality="high",
                output_format="jpeg",
                output_compression=90,
                background="opaque",
                n=1,
            )

        if not result.data:
            raise OpenAIBannerGenerationError(
                "OpenAI에서 생성된 이미지 정보가 없습니다."
            )

        image_base64 = result.data[0].b64_json

        if not image_base64:
            raise OpenAIBannerGenerationError(
                "OpenAI에서 이미지 데이터를 받지 못했습니다."
            )

        generated_image_bytes = base64.b64decode(image_base64)

        # gpt-image-2가 생성한 배경 이미지에
        # gpt-4o-mini가 생성한 광고 카피를 합성한다.
        final_banner_bytes = add_text_to_banner(
            image_bytes=generated_image_bytes,
            badge=marketing_copy.badge,
            headline=marketing_copy.headline,
            subheadline=marketing_copy.subheadline,
            banner_format=banner_format,
        )

        return {
            "image_bytes": final_banner_bytes,
            "mock": False,
            "model": os.getenv("MARKETING_IMAGE_MODEL", "gpt-image-2"),
            "size": output_size,
            "quality": "high",
            "generated_copy": marketing_copy.model_dump(
                mode="json"
            )
        }

    except OpenAIBannerGenerationError:
        raise

    except BannerTextCompositionError as error:
        logger.exception(
            "배너 문구 합성에 실패했습니다."
        )

        raise OpenAIBannerGenerationError(
            "배너 문구 합성에 실패했습니다."
        ) from error

    except OpenAIError as error:
        logger.exception("OpenAI 이미지 생성 요청에 실패했습니다.")

        raise OpenAIBannerGenerationError(
            "OpenAI 이미지 생성 요청에 실패했습니다."
        ) from error

    except (ValueError, OSError) as error:
        logger.exception("생성 이미지 처리에 실패했습니다.")

        raise OpenAIBannerGenerationError(
            "생성된 이미지 처리에 실패했습니다."
        ) from error

    finally:
        # OpenAI에 전달하기 위해 만들었던 임시 파일을 삭제한다.
        if (
            temporary_image_path is not None
            and temporary_image_path.exists()
        ):
            temporary_image_path.unlink()
