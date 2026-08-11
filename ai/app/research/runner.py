"""시장조사(research2) 파이프라인 어댑터 — 실험용 얇은 슬라이스.

research2 는 `ai/` 밖에 있는 독립 CLI 다. 이미지에 들어 있지 않고 compose 가
볼륨으로 붙인다 (`RESEARCH2_HOME`, 기본 `/app/research2`). 코드는 읽기전용이고
`runs/` 만 쓰기 가능하다.

지금은 `--from a4`(채점만) 만 부른다. 수집(A1·A2·A3)은 LLM 82회·3.5분이라
동기 계약(AI_SERVER_READ_TIMEOUT 75s)에 들어가지 않는다. 전 구간을 돌리려면
패턴 B(TaskRun 워커)로 옮겨야 하고, 그때 `TaskRunWorker.validateResult()` 에
분기 추가가 필수다 — 안 하면 결과가 조용히 버려진다.

stdout 은 판정에 쓰지 않는다. Windows 콘솔에서 CP949 로 깨지는 것을 확인했고,
정본은 `runs/<id>/result.json` (UTF-8) 이다. stderr 는 실패했을 때 사유로만 읽는다.
"""

import asyncio
import io
import json
import os
import re
import sys
from typing import Any

from app.services.journey_provider import ProviderFailure

# 엔진은 **이 패키지 안**에 있다 (판 ㉝ 이식). 예전 기본값 `/app/research2` 는
# 볼륨 마운트를 전제한 것이라 이미지가 자립하지 못했다.
RESEARCH_HOME = os.getenv(
    "RESEARCH2_HOME",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "research2"))

# runs/<이름> 으로 경로를 만들기 때문에 디렉터리 이름으로 쓸 수 있는 글자만 받는다.
# 이걸 안 걸면 sourceRun="../../etc" 같은 값이 그대로 경로가 된다.
_SAFE_RUN_ID = re.compile(r"[A-Za-z0-9._-]{1,128}")


# ── 실패 어휘는 **백엔드 화이트리스트 안에서만** 고른다 ────────────────────────
#   `InternalAiExecutionClient.ERROR_REASONS`(코드→사유) 와 `RETRYABLE_REASONS` 에
#   없는 값을 내면 `parseFailure` 가 응답 자체를 무효 처리하거나 사유를 뭉갠다.
#   판 ㉝ 착수 시 실측: 이 파일이 내던 reason **9개가 전부 미등록**이었고
#   `MODEL_EXECUTION_FAILED` 는 **코드조차 없었다**. 실패하면 원인을 잃는 상태였다.
#
#   그래서 **새 어휘를 추가하지 않고 기존 어휘로 접는다** — 상세는 `detail` 로 보낸다.
#   ⚠ retryable 값은 아래 표와 백엔드 `RETRYABLE_REASONS` 가 **정확히** 같아야 한다.
_ALLOWED = {
    ("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION"): (400, False),
    ("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE"): (503, True),
    ("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED"): (504, True),
    ("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE"): (502, True),
    ("EXECUTION_FAILED", "PERMANENT_EXECUTION_FAILURE"): (500, False),
    ("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION"): (502, False),
}


def _fail(code: str, reason: str, detail: str = "") -> ProviderFailure:
    """등록된 (코드, 사유) 조합만 낸다. 상세는 사유가 아니라 **메시지**로 간다.

    ⚠ 목록에 없는 조합을 부르면 여기서 터진다 — 조용히 뭉개지는 것보다 낫다.
    """
    if (code, reason) not in _ALLOWED:
        raise AssertionError(f"등록되지 않은 실패 어휘: {code}/{reason}")
    status_code, retryable = _ALLOWED[(code, reason)]
    return ProviderFailure(code, reason, status_code, retryable)


async def execute_market_research(task_input: dict[str, Any], run_id: str,
                                  timeout_seconds: float) -> dict[str, Any]:
    """저장된 수집을 재채점하고 지표·원장을 돌려준다. LLM 0회·네트워크 0회."""
    source_run = task_input.get("sourceRun")
    if not isinstance(source_run, str) or not _SAFE_RUN_ID.fullmatch(source_run):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "sourceRun 형식 불량")
    if not _SAFE_RUN_ID.fullmatch(run_id):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "runId 형식 불량")

    if not os.path.isdir(os.path.join(RESEARCH_HOME, "runs", source_run)):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "sourceRun 원장 없음")

    # 같은 --id 로 재실행하면 run.jsonl 이 append-only 라 지표가 2배로 보인다.
    # taskAttemptId 는 실행마다 새로 오므로 정상 경로에서는 안 겹치지만,
    # 겹치면 조용히 틀리는 종류라 여기서 막는다.
    if os.path.exists(os.path.join(RESEARCH_HOME, "runs", run_id)):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "runId 재사용")

    try:
        process = await asyncio.create_subprocess_exec(
            sys.executable, "-u", "run.py",
            "--from", "a4", "--source-run", source_run, "--id", run_id,
            cwd=RESEARCH_HOME,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
    except OSError:
        raise _fail("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE", "엔진 기동 실패")

    try:
        _, stderr = await asyncio.wait_for(process.communicate(), timeout=timeout_seconds)
    except asyncio.TimeoutError:
        process.kill()
        await process.wait()
        raise _fail("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", "엔진 실행이 예산을 넘겼다")

    if process.returncode != 0:
        detail = stderr.decode("utf-8", "replace").strip().splitlines()
        raise _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
                    f"엔진 종료코드 {process.returncode}: "
                    f"{detail[-1] if detail else 'no stderr'}"[:400])

    result_path = os.path.join(RESEARCH_HOME, "runs", run_id, "result.json")
    try:
        with io.open(result_path, encoding="utf-8") as handle:
            payload = json.load(handle)
    except (OSError, ValueError):
        raise _fail("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", "result.json 을 읽을 수 없다")

    report = payload.get("report")
    metrics = payload.get("metrics")
    if not isinstance(report, dict) or not isinstance(metrics, dict):
        raise _fail("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", "report·metrics 모양 불량")

    # conclusion·ledger 를 그대로 싣는다. 경계 문구(scope_note — "전사 매출은 시장
    # 내 매출이 아니다" 등)가 거기 들어 있고, 그건 지우면 안 되는 표시다.
    return {
        "runId": run_id,
        "sourceRun": source_run,
        "fromStage": "a4",
        "metrics": metrics,
        "adapters": payload.get("adapters", {}),
        "conclusion": report.get("conclusion", []),
        "ledger": report.get("ledger", []),
        "notFound": report.get("not_found", {}),
        "coverageCaveat": payload.get("coverage_caveat"),
    }
