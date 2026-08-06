from pathlib import Path

from fastapi import UploadFile, status

from app.api.errors import ApiHttpException


ALLOWED_IMAGE_TYPES = {
    ".png": {"image/png"},
    ".jpg": {"image/jpeg"},
    ".jpeg": {"image/jpeg"},
    ".webp": {"image/webp"},
}
MAX_IMAGE_SIZE = 10 * 1024 * 1024
READ_CHUNK_SIZE = 1024 * 1024


async def read_and_validate_image(image: UploadFile) -> bytes:
    """Preserve the AIdev extension, MIME, empty, and size checks.

    Image signature and decode validation are deliberately deferred so this
    phase first locks down the original vertical-slice behavior.
    """

    image_data = bytearray()
    while chunk := await image.read(READ_CHUNK_SIZE):
        image_data.extend(chunk)
        if len(image_data) > MAX_IMAGE_SIZE:
            await image.seek(0)
            raise ApiHttpException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                code="IMAGE_TOO_LARGE",
                message="이미지 크기는 최대 10MB까지 허용됩니다.",
            )

    await image.seek(0)
    return validate_image_bytes(
        image.filename or "",
        image.content_type or "",
        bytes(image_data),
    )


def validate_image_bytes(
    filename: str,
    content_type: str,
    image_data: bytes,
) -> bytes:
    """Apply the preserved AIdev checks to an artifact payload."""

    extension = Path(filename).suffix.lower()
    if extension not in ALLOWED_IMAGE_TYPES:
        raise ApiHttpException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            code="UNSUPPORTED_IMAGE_TYPE",
            message="PNG, JPG, JPEG, WEBP 이미지만 업로드할 수 있습니다.",
        )
    if content_type not in ALLOWED_IMAGE_TYPES[extension]:
        raise ApiHttpException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            code="UNSUPPORTED_IMAGE_TYPE",
            message="이미지 확장자와 Content-Type이 일치하지 않습니다.",
        )
    if len(image_data) > MAX_IMAGE_SIZE:
        raise ApiHttpException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            code="IMAGE_TOO_LARGE",
            message="이미지 크기는 최대 10MB까지 허용됩니다.",
        )
    if not image_data:
        raise ApiHttpException(
            status_code=status.HTTP_400_BAD_REQUEST,
            code="EMPTY_IMAGE",
            message="빈 이미지 파일은 업로드할 수 없습니다.",
        )
    return image_data
