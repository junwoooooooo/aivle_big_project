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
    assert "TWIN_BANK_HOST_DIR       EXISTS" in completed.stdout
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
        "TWIN_BANK_PATH", "MOLEG_API_KEY",
    ):
        environment.pop(name, None)
    return environment


def _required_lines(twin_line: str) -> str:
    return "\n".join([
        "AI_PROVIDER=openai", "AI_API_KEY=general", "AI_MODEL=test-model",
        "AI_INTERNAL_SERVICE_TOKEN=token", "JWT_SECRET=jwt",
        "POSTGRES_PASSWORD=db", "MINIO_ROOT_PASSWORD=minio",
        "MARKET_RESEARCH_OPENAI_API_KEY=market", twin_line,
    ])


def _run_precheck(tmp_path: Path, twin_line: str):
    env_file = tmp_path / ".env"
    env_file.write_text(_required_lines(twin_line), encoding="utf-8")
    return subprocess.run(
        [sys.executable, str(SCRIPT), "--compose", "--env-file", str(env_file)],
        check=False, capture_output=True, text=True, env=_clean_environment(),
    )


def test_twin_bank_host_dir_accepts_quoted_absolute_windows_path(tmp_path: Path) -> None:
    bank = tmp_path / "quoted bank"
    bank.mkdir()
    completed = _run_precheck(tmp_path, f'TWIN_BANK_HOST_DIR="{bank}"')
    assert completed.returncode == 0
    assert "TWIN_BANK_HOST_DIR       EXISTS" in completed.stdout
    assert str(bank) not in completed.stdout


def test_twin_bank_host_dir_accepts_repo_relative_path(tmp_path: Path) -> None:
    completed = _run_precheck(tmp_path, "TWIN_BANK_HOST_DIR=ai/tests/fixtures/twin_bank")
    assert completed.returncode == 0
    assert "TWIN_BANK_HOST_DIR       EXISTS" in completed.stdout


def test_twin_bank_host_dir_rejects_missing_and_regular_file(tmp_path: Path) -> None:
    missing = _run_precheck(tmp_path, f"TWIN_BANK_HOST_DIR={tmp_path / 'missing'}")
    regular_file = tmp_path / "bank.txt"
    regular_file.write_text("not a directory", encoding="utf-8")
    file_result = _run_precheck(tmp_path, f"TWIN_BANK_HOST_DIR={regular_file}")
    assert missing.returncode == 1 and file_result.returncode == 1
    assert "TWIN_BANK_HOST_DIR       MISSING" in missing.stdout
    assert "TWIN_BANK_HOST_DIR       MISSING" in file_result.stdout


def test_deprecated_twin_bank_path_is_not_a_compose_alias(tmp_path: Path) -> None:
    bank = tmp_path / "bank"
    bank.mkdir()
    completed = _run_precheck(tmp_path, f"TWIN_BANK_PATH={bank}")
    assert completed.returncode == 1
    assert "TWIN_BANK_HOST_DIR       MISSING" in completed.stdout
    assert "deprecated/unknown TWIN_BANK_PATH detected" in completed.stdout
    assert str(bank) not in completed.stdout
