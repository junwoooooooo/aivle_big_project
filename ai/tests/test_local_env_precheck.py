from __future__ import annotations

import subprocess
import sys
import os
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "check_local_env.py"


def test_compose_precheck_reports_only_presence_and_path_state(tmp_path: Path) -> None:
    bank = tmp_path / "bank"
    bank.mkdir()
    secret = "must-not-appear-in-output"
    env_file = tmp_path / ".env"
    env_file.write_text("\n".join([
        "AI_PROVIDER=openai",
        f"AI_API_KEY={secret}",
        "AI_MODEL=test-model",
        f"AI_INTERNAL_SERVICE_TOKEN={secret}",
        f"JWT_SECRET={secret}",
        f"POSTGRES_PASSWORD={secret}",
        f"MINIO_ROOT_PASSWORD={secret}",
        f"MARKET_RESEARCH_OPENAI_API_KEY={secret}",
        f"TWIN_BANK_HOST_DIR={bank}",
    ]), encoding="utf-8")

    completed = subprocess.run(
        [sys.executable, str(SCRIPT), "--compose", "--env-file", str(env_file)],
        check=False, capture_output=True, text=True, env=_clean_environment(),
    )

    assert completed.returncode == 0
    assert "MARKET_KEY               SET" in completed.stdout
    assert "TWIN_BANK_PATH           EXISTS" in completed.stdout
    assert secret not in completed.stdout


def test_compose_precheck_does_not_treat_general_ai_key_as_market_key(tmp_path: Path) -> None:
    bank = tmp_path / "bank"
    bank.mkdir()
    env_file = tmp_path / ".env"
    env_file.write_text("\n".join([
        "AI_PROVIDER=openai", "AI_API_KEY=general-only", "AI_MODEL=test-model",
        "AI_INTERNAL_SERVICE_TOKEN=token", "JWT_SECRET=jwt",
        "POSTGRES_PASSWORD=db", "MINIO_ROOT_PASSWORD=minio",
        f"TWIN_BANK_HOST_DIR={bank}",
    ]), encoding="utf-8")

    completed = subprocess.run(
        [sys.executable, str(SCRIPT), "--compose", "--env-file", str(env_file)],
        check=False, capture_output=True, text=True, env=_clean_environment(),
    )

    assert completed.returncode == 1
    assert "MARKET_KEY               MISSING" in completed.stdout
    assert "general-only" not in completed.stdout


def _clean_environment() -> dict[str, str]:
    environment = dict(os.environ)
    for name in (
        "AI_PROVIDER", "AI_API_KEY", "AI_MODEL", "AI_INTERNAL_SERVICE_TOKEN",
        "JWT_SECRET", "POSTGRES_PASSWORD", "MINIO_ROOT_PASSWORD",
        "MARKET_RESEARCH_OPENAI_API_KEY", "OPENAI_API_KEY", "TWIN_BANK_HOST_DIR",
        "MOLEG_API_KEY",
    ):
        environment.pop(name, None)
    return environment
