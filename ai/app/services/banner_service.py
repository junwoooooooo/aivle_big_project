from pathlib import Path
from uuid import uuid4


OUTPUT_DIRECTORY = Path(__file__).resolve().parents[2] / "outputs"


def create_mock_banner(
    image_bytes: bytes,
    original_filename: str,
) -> dict[str, str]:
    """Persist the upload as the AIdev Mock banner result.

    Local output storage and static serving are intentionally retained only as
    a vertical-slice baseline. A later phase will replace this boundary with
    S3-compatible object storage/MinIO.
    """

    extension = Path(original_filename).suffix.lower()
    banner_id = uuid4().hex
    output_filename = f"banner_{banner_id}{extension}"
    output_path = OUTPUT_DIRECTORY / output_filename
    OUTPUT_DIRECTORY.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(image_bytes)

    return {
        "banner_id": banner_id,
        "filename": output_filename,
        "preview_path": f"/outputs/{output_filename}",
    }
