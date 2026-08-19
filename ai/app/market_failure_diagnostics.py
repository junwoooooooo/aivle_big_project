"""Bounded, server-only diagnostics for a failed Market product subprocess.

This module reads only diagnostic artifacts that the frozen Research2 core already writes.  It
never returns prompts, concept text, provider responses, document bodies, URLs, or credentials.
"""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any


_STAGES = {"HARNESS_LLM", "HARNESS_GATE", "DRYRUN", "COLLECT", "UNKNOWN"}
_ADAPTERS = ("web", "kosis", "dart")
_NODE_PREFIXES = ("a1", "a2", "a3", "harness", "dryrun", "collect", *_ADAPTERS)
_SAFE_NAME = re.compile(r"^[A-Za-z][A-Za-z0-9_.-]{0,79}$")
_SAFE_NODE = re.compile(r"^[A-Za-z0-9_.-]{1,80}$")
_SENSITIVE = re.compile(
    r"(?i)(authorization|bearer|api.?key|secret|token|prompt|response|concept|sk-)"
)
_EXCEPTION = re.compile(r"([A-Za-z][A-Za-z0-9_.]{0,79}(?:Error|Exception|Failure))")
_HTTP_STATUS = re.compile(
    r"(?i)(?:error\s*code|status(?:_code)?|http)\D{0,20}([1-5][0-9]{2})"
)
_PROVIDER_CODE = re.compile(
    r"(?i)(?:error[_ ]?code|\bcode)\s*[\"']?\s*[:=]\s*[\"']?([A-Za-z][A-Za-z0-9_.-]{0,79})"
)
_WRAPPER_EXCEPTIONS = {"ProviderFailure", "HarnessError", "SystemExit"}
_MAX_HARNESS_BYTES = 256 * 1024
_MAX_RUNLOG_TAIL_BYTES = 512 * 1024


def collect_market_failure_diagnostics(workspace: str | Path) -> dict[str, Any] | None:
    """Return the latest safe diagnostic already written inside ``workspace``."""
    root = Path(workspace) / "runs-generated"
    if not root.is_dir():
        return None
    return _latest_harness_failure(root) or _latest_meter_failure(root)


def fallback_stderr_diagnostics(stderr: str) -> dict[str, Any] | None:
    """Extract only an exception class/status from traceback text; never retain its message."""
    candidates: list[tuple[str, str]] = []
    for line in stderr.splitlines():
        match = _EXCEPTION.search(line.strip())
        if not match:
            continue
        exception_type = _safe_name(match.group(1).rsplit(".", 1)[-1])
        if exception_type and exception_type not in _WRAPPER_EXCEPTIONS:
            candidates.append((exception_type, line))
    if not candidates:
        return None
    exception_type, source = candidates[0]
    return _diagnostic(
        stage="UNKNOWN",
        exception_type=exception_type,
        http_status=_http_status(source),
        error_code=_provider_code(source),
    )


def diagnostic_log_detail(diagnostic: dict[str, Any] | None) -> str:
    """Format only allowlisted scalar fields for ``ProviderFailure.safe_provider_message``."""
    if not diagnostic:
        return "시장조사 엔진 실패"
    parts = []
    for key in ("stage", "node", "exceptionType", "httpStatus", "errorCode", "attempt", "adapter"):
        value = diagnostic.get(key)
        if value is not None:
            parts.append(f"{key}={value}")
    return (" ".join(parts) or "시장조사 엔진 실패")[:300]


def _latest_harness_failure(root: Path) -> dict[str, Any] | None:
    for path in _latest_files(root / "harness", "무인_기록.json"):
        try:
            if path.stat().st_size > _MAX_HARNESS_BYTES:
                continue
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, ValueError, TypeError):
            continue
        interventions = payload.get("개입") if isinstance(payload, dict) else None
        if not isinstance(interventions, list) or not interventions:
            continue
        last = interventions[-1]
        if not isinstance(last, dict):
            continue
        kind = last.get("종류") if isinstance(last.get("종류"), str) else ""
        detail = last.get("상세") if isinstance(last.get("상세"), str) else ""
        if not kind and not detail:
            continue
        stage = "HARNESS_LLM" if "LLM 호출 실패" in kind else "HARNESS_GATE"
        attempt = payload.get("_시도")
        attempt = attempt if isinstance(attempt, int) and 0 < attempt <= 1_000 else None
        return _diagnostic(
            stage=stage,
            exception_type=_exception_type(kind) or _exception_type(detail),
            http_status=_http_status(detail),
            error_code=_provider_code(detail),
            attempt=attempt,
        )
    return None


def _latest_meter_failure(root: Path) -> dict[str, Any] | None:
    for path in _latest_files(root, "run.jsonl"):
        for raw_line in reversed(_tail_lines(path, _MAX_RUNLOG_TAIL_BYTES)):
            if ".llm_error" not in raw_line and '"status": "error"' not in raw_line:
                continue
            try:
                event = json.loads(raw_line)
            except (ValueError, TypeError):
                continue
            if not isinstance(event, dict):
                continue
            node = _safe_node(event.get("node"))
            status = event.get("status")
            if not ((node and node.endswith(".llm_error")) or status == "error"):
                continue
            payload = event.get("payload")
            error = payload.get("error") if isinstance(payload, dict) else None
            error = error if isinstance(error, str) else ""
            return _diagnostic(
                stage=_stage_from_node(node),
                node=node,
                exception_type=_exception_type(error),
                http_status=_http_status(error),
                error_code=_provider_code(error),
                adapter=_adapter_from_node(node),
            )
    return None


def _latest_files(root: Path, name: str) -> list[Path]:
    if not root.is_dir():
        return []
    try:
        files = [path for path in root.rglob(name) if path.is_file()]
        return sorted(files, key=lambda path: path.stat().st_mtime_ns, reverse=True)[:20]
    except OSError:
        return []


def _tail_lines(path: Path, limit: int) -> list[str]:
    try:
        with path.open("rb") as handle:
            size = handle.seek(0, 2)
            start = max(0, size - limit)
            handle.seek(start)
            data = handle.read(limit)
        if start:
            _, _, data = data.partition(b"\n")
        return data.decode("utf-8", "replace").splitlines()
    except OSError:
        return []


def _diagnostic(*, stage: str, node: str | None = None,
                exception_type: str | None = None, http_status: int | None = None,
                error_code: str | None = None, attempt: int | None = None,
                adapter: str | None = None) -> dict[str, Any]:
    return {
        "stage": stage if stage in _STAGES else "UNKNOWN",
        "node": _safe_node(node),
        "exceptionType": _safe_name(exception_type),
        "httpStatus": http_status if isinstance(http_status, int) and 100 <= http_status <= 599 else None,
        "errorCode": _safe_name(error_code),
        "attempt": attempt if isinstance(attempt, int) and 0 < attempt <= 1_000 else None,
        "adapter": adapter if adapter in _ADAPTERS else None,
    }


def _safe_name(value: Any) -> str | None:
    if not isinstance(value, str) or _SENSITIVE.search(value) or not _SAFE_NAME.fullmatch(value):
        return None
    return value


def _safe_node(value: Any) -> str | None:
    if not isinstance(value, str) or not _SAFE_NODE.fullmatch(value):
        return None
    lowered = value.lower()
    if not any(lowered == prefix
               or lowered.startswith(tuple(prefix + suffix for suffix in ("_", ".", "-")))
               for prefix in _NODE_PREFIXES):
        return None
    return value


def _exception_type(text: str) -> str | None:
    match = _EXCEPTION.search(text or "")
    return _safe_name(match.group(1).rsplit(".", 1)[-1]) if match else None


def _http_status(text: str) -> int | None:
    match = _HTTP_STATUS.search(text or "")
    return int(match.group(1)) if match else None


def _provider_code(text: str) -> str | None:
    match = _PROVIDER_CODE.search(text or "")
    return _safe_name(match.group(1)) if match else None


def _adapter_from_node(node: str | None) -> str | None:
    lowered = (node or "").lower()
    return next((adapter for adapter in _ADAPTERS if adapter in lowered), None)


def _stage_from_node(node: str | None) -> str:
    lowered = (node or "").lower()
    if "harness" in lowered:
        return "HARNESS_LLM"
    if "dryrun" in lowered or "stat_code" in lowered:
        return "DRYRUN"
    if _adapter_from_node(node) or lowered.startswith(("a1", "a2", "a3", "collect")):
        return "COLLECT"
    return "UNKNOWN"
