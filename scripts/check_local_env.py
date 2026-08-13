#!/usr/bin/env python3
"""로컬 설정의 존재 여부만 검사한다. 비밀 값은 절대로 출력하지 않는다."""

from __future__ import annotations

import argparse
import os
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
COMPOSE_REQUIRED = (
    "AI_PROVIDER",
    "AI_API_KEY",
    "AI_MODEL",
    "AI_INTERNAL_SERVICE_TOKEN",
    "JWT_SECRET",
    "POSTGRES_PASSWORD",
    "MINIO_ROOT_PASSWORD",
)


def _read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        name, value = stripped.split("=", 1)
        if re.fullmatch(r"[A-Z][A-Z0-9_]*", name):
            values[name] = value.strip().strip('"').strip("'")
    return values


def _configured(values: dict[str, str], name: str) -> bool:
    return bool(os.environ.get(name, "").strip() or values.get(name, "").strip())


def _status(label: str, ok: bool, *, optional: bool = False) -> None:
    state = "SET" if ok else ("MISSING OPTIONAL" if optional else "MISSING")
    print(f"{label:<24} {state}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--compose", action="store_true", help="Compose 필수값과 Market 실행값 검사")
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    args = parser.parse_args()
    values = _read_env(args.env_file.resolve())

    missing = []
    names = COMPOSE_REQUIRED if args.compose else ("AI_PROVIDER", "AI_API_KEY", "AI_MODEL")
    for name in names:
        ok = _configured(values, name)
        _status(name, ok)
        if not ok:
            missing.append(name)

    market_ok = _configured(values, "MARKET_RESEARCH_OPENAI_API_KEY") \
        or _configured(values, "OPENAI_API_KEY")
    _status("MARKET_KEY", market_ok)
    if args.compose and not market_ok:
        missing.append("MARKET_KEY")

    twin_value = os.environ.get("TWIN_BANK_HOST_DIR", "").strip() \
        or values.get("TWIN_BANK_HOST_DIR", "").strip()
    twin_path = Path(twin_value) if twin_value else None
    if twin_path is not None and not twin_path.is_absolute():
        twin_path = (ROOT / twin_path).resolve()
    twin_ok = twin_path is not None and twin_path.is_dir()
    print(f"{'TWIN_BANK_HOST_DIR':<24} {'EXISTS' if twin_ok else 'MISSING'}")
    if args.compose and not twin_ok:
        missing.append("TWIN_BANK_HOST_DIR")
    alias_detected = _configured(values, "TWIN_BANK_PATH")
    if alias_detected and not _configured(values, "TWIN_BANK_HOST_DIR"):
        print("note: deprecated/unknown TWIN_BANK_PATH detected; rename to TWIN_BANK_HOST_DIR")

    _status("MOLEG_API_KEY", _configured(values, "MOLEG_API_KEY"), optional=True)
    return 1 if missing else 0


if __name__ == "__main__":
    raise SystemExit(main())
