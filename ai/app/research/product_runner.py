"""공식 Product Market 실행용 격리 어댑터.

donor research2의 A1→A2→A3→A4/B/C를 그대로 호출하되 원장은 Task 단위 임시
workspace에만 둔다. 이 프로세스는 결과 봉투를 만든 뒤 종료되므로 AI 로컬 파일이
current authority가 되지 않는다.
"""
from __future__ import annotations

import argparse
import asyncio
import io
import json
import os
import re
import sys
import threading
from typing import Any

from app.providers import ProviderFailure
from app.research.progress_jsonl import SafeProgressJsonl
from app.research.runner import _safe_failure_detail


_SAFE_ERROR_FIELDS = 20
_SAFE_ERROR_DEPTH = 3
_SENSITIVE_DIAGNOSTIC_KEY = re.compile(
    r"(?i)(authorization|api.?key|token|secret|password|prompt|input|request|response|document|content)"
)


def _safe_error_value(value: Any, *, key: str = "", depth: int = 0) -> Any:
    if _SENSITIVE_DIAGNOSTIC_KEY.search(key):
        return "[REDACTED]"
    if value is None or isinstance(value, bool):
        return value
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return value if value == value and abs(value) != float("inf") else None
    if isinstance(value, str):
        return _safe_failure_detail(value)
    if depth >= _SAFE_ERROR_DEPTH:
        return "[TRUNCATED]"
    if isinstance(value, dict):
        result: dict[str, Any] = {}
        for raw_key, item in list(value.items())[:_SAFE_ERROR_FIELDS]:
            safe_key = re.sub(r"[^A-Za-z0-9_.-]", "_", str(raw_key))[:80] or "field"
            result[safe_key] = _safe_error_value(item, key=safe_key, depth=depth + 1)
        return result
    if isinstance(value, (list, tuple)):
        return [_safe_error_value(item, depth=depth + 1)
                for item in list(value)[:_SAFE_ERROR_FIELDS]]
    return _safe_failure_detail(type(value).__name__)


def provider_failure_envelope(failure: ProviderFailure) -> dict[str, Any]:
    return {
        "kind": "ProviderFailure",
        "code": _safe_failure_detail(failure.code)[:80],
        "reason": _safe_failure_detail(failure.reason)[:120],
        "statusCode": failure.status_code,
        "retryable": failure.retryable,
        "upstreamStatus": failure.upstream_status,
        "providerErrorType": _safe_error_value(failure.provider_error_type),
        "providerErrorParam": _safe_error_value(failure.provider_error_param),
        "schemaName": _safe_error_value(failure.schema_name),
        "retryAfterMs": failure.retry_after_ms,
        "safeProviderMessage": _safe_error_value(failure.safe_provider_message),
        "safeDiagnostics": _safe_error_value(failure.safe_diagnostics),
    }


def unexpected_failure_envelope(failure: Exception, stage: str) -> dict[str, Any]:
    return {
        "kind": "UnexpectedException",
        "exceptionClass": _safe_failure_detail(type(failure).__name__)[:160],
        "safeMessage": _safe_failure_detail(str(failure)),
        "stage": _safe_failure_detail(stage)[:120],
    }


def _write_error_output(path: str, payload: dict[str, Any]) -> None:
    try:
        with io.open(path, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, sort_keys=True)
    except OSError:
        # The parent retains a sanitized stderr fallback when the side channel is unavailable.
        pass


def _finish_failed(progress: SafeProgressJsonl, heartbeat: "_ProgressHeartbeat | None") -> None:
    if heartbeat is not None:
        heartbeat.stop()
    try:
        progress.emit({"stage": "MARKET_COLLECTION", "action": "FAILED", "status": "FAILED",
                       "safeSummary": "시장 근거 수집을 완료하지 못했습니다."})
    except Exception:
        pass
    try:
        progress.close()
    except Exception:
        pass


class _ProgressHeartbeat:
    """Report liveness without inventing Research2 internal stages."""

    def __init__(self, progress, interval_seconds: float = 20.0):
        self._progress = progress
        self._interval_seconds = max(0.001, float(interval_seconds))
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        self._thread = threading.Thread(
            target=self._run,
            name="market-collection-heartbeat",
            daemon=True,
        )
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout=1.0)
            self._thread = None

    def _run(self) -> None:
        while not self._stop.wait(self._interval_seconds):
            try:
                self._progress.emit({
                    "stage": "MARKET_COLLECTION",
                    "action": "HEARTBEAT",
                    "status": "RUNNING",
                    "safeSummary": "시장 근거 수집을 계속 진행하고 있습니다.",
                })
            except Exception:
                # Progress is observability only; it must never fail paid execution.
                continue


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--error-output", required=True)
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--concept-id", required=True)
    parser.add_argument("--as-of", required=True)
    parser.add_argument("--llm-budget", type=int, default=3)
    parser.add_argument("--timeout-seconds", type=float, default=20 * 60)
    parser.add_argument("--runtime-input", default="")
    parser.add_argument("--progress-jsonl", default="")
    args = parser.parse_args()

    progress = SafeProgressJsonl(args.progress_jsonl, truncate=True)
    heartbeat: _ProgressHeartbeat | None = None
    stage = "initialization"
    try:
        os.environ["RESEARCH2_RUNS_DIR"] = os.path.join(args.workspace, "runs")
        os.environ["RESEARCH2_GENERATED_RUNS_DIR"] = os.path.join(args.workspace, "runs-generated")
        with io.open(args.input, encoding="utf-8") as handle:
            concept = json.load(handle)
        runtime_input = {}
        if args.runtime_input:
            with io.open(args.runtime_input, encoding="utf-8") as handle:
                runtime_input = json.load(handle)
        progress.emit({"stage": "MARKET_COLLECTION", "action": "STARTED", "status": "RUNNING",
                       "safeSummary": "선택한 사업안의 시장 근거를 수집하고 있습니다."})
        heartbeat = _ProgressHeartbeat(progress)
        heartbeat.start()
        stage = "market-collection"
        # donor orchestrator가 dynamic collection, harness/dryrun gate, dual run path,
        # partial quarantine 및 recollect 의미를 한 곳에서 결정한다. 이 wrapper는
        # immutable snapshot을 donor textContents 계약으로 옮길 뿐이다.
        from app.research.pipeline import run_market_research
        from app.research.research2 import runpath
        if runpath.exists(args.concept_id) and not runpath.complete(args.concept_id):
            runpath.quarantine_partial(args.concept_id)
        task_input = {
            "mode": "FULL",
            "conceptId": args.concept_id,
            "asOf": args.as_of,
            "llmBudget": args.llm_budget,
            "textContents": [{
                "contentKey": "concept",
                "chunks": [{"text": json.dumps(concept, ensure_ascii=False, sort_keys=True)}],
            }],
        }
        if runtime_input.get("sourceRun"):
            task_input["sourceRun"] = runtime_input["sourceRun"]
        if isinstance(runtime_input.get("recollect"), dict):
            task_input["recollect"] = runtime_input["recollect"]
        result = asyncio.run(run_market_research(
            task_input, args.run_id, max(1.0, args.timeout_seconds)))
        stage = "product-post-validation"
        result = _fail_closed_unverified_product_assumptions(result)
        heartbeat.stop()
        heartbeat = None
        progress.emit({"stage": "MARKET_COLLECTION", "action": "COMPLETED", "status": "RUNNING",
                       "safeSummary": "시장 근거 수집을 완료했습니다."})
        progress.emit({"stage": "MARKET_SERIALIZATION", "action": "COMPLETED",
                       "status": "COMPLETED", "safeSummary": "시장조사 결과 정리 완료"})
        progress.close()
        stage = "result-serialization"
        with io.open(args.output, "w", encoding="utf-8") as handle:
            json.dump(result, handle, ensure_ascii=False, sort_keys=True)
    except ProviderFailure as failure:
        _finish_failed(progress, heartbeat)
        _write_error_output(args.error_output, provider_failure_envelope(failure))
        raise SystemExit(1) from None
    except Exception as failure:
        _finish_failed(progress, heartbeat)
        _write_error_output(args.error_output, unexpected_failure_envelope(failure, stage))
        raise SystemExit(1) from None


def _fail_closed_unverified_product_assumptions(result: dict) -> dict:
    """Keep main rules exact, but never present fixture-derived numbers as Product facts.

    Main's assumption rules are an audited experiment asset. They include numeric
    hypotheses with zero upstream sources. A Product result may expose those as
    documented assumptions, but it must not publish an estimate whose arithmetic
    depends on them as if it were a current-project result.
    """
    market = result.get("market")
    if not isinstance(market, dict):
        return result
    blocked: list[str] = []
    for name in ("tam", "sam", "som"):
        estimate = market.get(name)
        if not isinstance(estimate, dict):
            continue
        factors = estimate.get("factors") or []
        if any(
            isinstance(factor, dict)
            and factor.get("basis") == "가정"
            and int(factor.get("sourceCount") or 0) == 0
            for factor in factors
        ):
            market[name] = None
            blocked.append(name.upper())
    if blocked:
        result.setdefault("degradations", []).append({
            "stage": "product_post_validation",
            "code": "UNVERIFIED_NUMERIC_ASSUMPTION",
            "detail": "관측 근거가 없는 main 가정에 의존한 "
                      + ", ".join(blocked) + " 계산은 Product 결과에서 공개하지 않았다",
        })
    return result


if __name__ == "__main__":
    main()
