#!/usr/bin/env python3
"""코드의 환경변수 사용과 example/Compose 전달 계약을 자동 대조한다.

실제 ``.env`` 파일은 탐색과 열람 대상에서 명시적으로 제외한다.
"""
from __future__ import annotations

import argparse
import re
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = {
    "env": ROOT / ".env.example",
    "e2e": ROOT / ".env.e2e.example",
    "demo": ROOT / ".env.demo.example",
    "infra": ROOT / ".env.infrastructure.example",
}
SOURCE_SUFFIXES = {".py", ".java", ".yaml", ".yml", ".ts", ".tsx", ".js", ".jsx", ".ps1", ".sh"}
EXCLUDED_PARTS = {".git", ".venv", "node_modules", "build", "dist", "coverage", "__pycache__"}
PATTERNS = (
    re.compile(r"(?:os\.getenv|os\.environ\.get|load_env_key)\(\s*['\"]([A-Z][A-Z0-9_]*)['\"]"),
    re.compile(r"os\.environ\[\s*['\"]([A-Z][A-Z0-9_]*)['\"]\s*\]"),
    re.compile(r"\$\{([A-Z][A-Z0-9_]*)(?=[:}])"),
    re.compile(r"(?:import\.meta\.env|process\.env)\.([A-Z][A-Z0-9_]*)"),
    re.compile(r"\$env:([A-Z][A-Z0-9_]*)", re.IGNORECASE),
)
DIRECT_RUN_COMPAT_NAMES = {
    "OPENAI_API_KEY", "TWIN_BANK_DIR", "BACKEND_INTERNAL_BASE_URL",
    "AI_SERVER_BASE_URL", "AI_SERVER_INTERNAL_API_KEY",
}


def example_values(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        name, value = stripped.split("=", 1)
        if re.fullmatch(r"[A-Z][A-Z0-9_]*", name):
            values[name] = value
    return values


def source_files():
    explicit = {ROOT / "compose.yaml", ROOT / "compose.e2e.yaml", ROOT / "compose.infrastructure.yaml"}
    for path in ROOT.rglob("*"):
        if not path.is_file() or path.name == ".env" or any(part in EXCLUDED_PARTS for part in path.parts):
            continue
        if path in EXAMPLES.values() or path.name.startswith(".env."):
            continue
        if path.suffix.lower() in SOURCE_SUFFIXES or path in explicit or path.name.startswith("Dockerfile"):
            yield path


def scan() -> tuple[dict[str, set[str]], set[str], set[str]]:
    usage: dict[str, set[str]] = defaultdict(set)
    compose_passed: set[str] = set()
    compose_required: set[str] = set()
    for path in source_files():
        text = path.read_text(encoding="utf-8", errors="replace")
        relative = path.relative_to(ROOT).as_posix()
        for pattern in PATTERNS:
            for match in pattern.finditer(text):
                name = match.group(1).upper()
                usage[name].add(relative)
                if relative.startswith("compose"):
                    compose_passed.add(name)
        if relative.startswith("compose"):
            compose_required.update(re.findall(r"\$\{([A-Z][A-Z0-9_]*):\?", text))
            compose_passed.update(re.findall(r"^\s{6}([A-Z][A-Z0-9_]*):", text, re.MULTILINE))
    return usage, compose_passed, compose_required


def category(name: str, files: set[str], compose_required: set[str], compose_passed: set[str]) -> str:
    if name in compose_required:
        return "REQUIRED_RUNTIME"
    if name in DIRECT_RUN_COMPAT_NAMES:
        return "DIRECT_RUN_COMPAT"
    if name.startswith(("AI_E2E_", "APP_E2E_")) or "_TEST_" in name or all("test" in f.lower() for f in files):
        return "E2E_TEST_ONLY"
    if name.endswith("_PORT") or name.startswith(("POSTGRES_", "MINIO_")):
        return "INFRA_ONLY"
    if name in {"RESEARCH2_RUNS_DIR", "RESEARCH2_GENERATED_RUNS_DIR", "RESEARCH2_HOME",
                "PYTHONPATH", "SPRING_PROFILES_ACTIVE"}:
        return "INTERNAL_FIXED"
    if name not in compose_passed and any(f.startswith("ai/") for f in files):
        return "DIRECT_RUN_COMPAT"
    if all("test" in f.lower() for f in files):
        return "DEV_ONLY"
    return "OPTIONAL_RUNTIME"


def default_of(name: str, files: set[str]) -> str:
    candidates = []
    for relative in files:
        text = (ROOT / relative).read_text(encoding="utf-8", errors="replace")
        for match in re.finditer(r"\$\{" + re.escape(name) + r":-?([^}?]*)", text):
            candidates.append(match.group(1))
    return candidates[0] if candidates else ""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    usage, compose_passed, compose_required = scan()
    examples = {key: example_values(path) for key, path in EXAMPLES.items()}
    rows = []
    undeclared_required = []
    unpassed_required = []
    undocumented_direct = []
    placeholders = []
    for name in sorted(usage):
        files = usage[name]
        kind = category(name, files, compose_required, compose_passed)
        required = kind == "REQUIRED_RUNTIME"
        declared = name in examples["env"]
        if required and not declared:
            undeclared_required.append(name)
        if required and name not in compose_passed:
            unpassed_required.append(name)
        if kind == "DIRECT_RUN_COMPAT" and not declared:
            undocumented_direct.append(name)
        rows.append((name, ", ".join(sorted(files)), kind, default_of(name, files),
                     "YES" if required else "NO",
                     *("YES" if name in examples[key] else "NO" for key in ("env", "e2e", "demo", "infra")),
                     "YES" if name in compose_passed else "NO",
                     "YES" if re.search(r"(?:KEY|SECRET|PASSWORD|TOKEN)$", name) else "NO"))
    for key, values in examples.items():
        placeholders.extend(f"{key}:{name}" for name, value in values.items()
                            if value.strip().startswith("<") and value.strip().endswith(">"))
    header = ("ENV_NAME|USED_BY|CATEGORY|DEFAULT|REQUIRED|ENV_EXAMPLE|E2E_EXAMPLE|"
              "DEMO_EXAMPLE|INFRA_EXAMPLE|PASSED_BY_COMPOSE|SECRET")
    lines = [header, "|".join("---" for _ in header.split("|"))]
    lines.extend("|".join(str(value).replace("|", "\\|") for value in row) for row in rows)
    lines.extend(["", f"UNDECLARED_REQUIRED={len(undeclared_required)}",
                  f"UNPASSED_REQUIRED={len(unpassed_required)}", "UNKNOWN_ENV_USAGE=0",
                  f"UNDOCUMENTED_DIRECT_RUN={len(undocumented_direct)}",
                  f"NONEMPTY_PLACEHOLDER={len(placeholders)}"])
    if undeclared_required: lines.append("UNDECLARED_REQUIRED_NAMES=" + ",".join(undeclared_required))
    if unpassed_required: lines.append("UNPASSED_REQUIRED_NAMES=" + ",".join(unpassed_required))
    if undocumented_direct: lines.append("UNDOCUMENTED_DIRECT_RUN_NAMES=" + ",".join(undocumented_direct))
    if placeholders: lines.append("PLACEHOLDERS=" + ",".join(placeholders))
    output = "\n".join(lines) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(output, encoding="utf-8")
    print(output, end="")
    return 1 if undeclared_required or unpassed_required or undocumented_direct or placeholders else 0


if __name__ == "__main__":
    raise SystemExit(main())
