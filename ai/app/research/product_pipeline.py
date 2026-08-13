# -*- coding: utf-8 -*-
"""시장조사·BM 오케스트레이터.

공식 Product FULL은 선택한 임의 CPV2 concept snapshot을 Task 임시 workspace에
materialize하고 research2의 A1~A4 수집·검증과 B/C 결과 구성을 모두 실행한다.
저장된 run을 읽는 RESCORE/fixture 경로는 별도 회귀·진단 경로이며, 그 경로에서 실제로
실행하지 않은 단계만 SKIPPED/degradation으로 기록한다.

이 층은 donor 계산·판정 값을 바꾸지 않고 결과를 계약 형태로 운반한다. 한글 키에서
camelCase로의 변환과 allowlist는 `serialize.py`가 담당한다.
"""
from __future__ import annotations

import asyncio
import io
import json
import os
import sys
import tempfile
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import datetime, timezone

from pydantic import ValidationError

from . import serialize
from .runner import RESEARCH_HOME, _SAFE_RUN_ID, _fail

EventSink = Callable[[dict], None]


def _observe(event_sink: EventSink | None, stage: str, action: str, summary: str,
             status: str = "RUNNING", **optional) -> None:
    if event_sink is None:
        return
    try:
        event_sink({"stage": stage, "action": action, "status": status,
                    "safeSummary": summary, **optional})
    except Exception:
        pass

#: research2 는 **평평한 import** 로 서로를 부른다(`import fillaxis`·`import cards`).
#: 패키지가 아니므로(`__init__.py` 0개) `from service import …` 로 부르면 같은 모듈이
#: 두 개체로 실려 규칙 캐시가 갈라진다. 그래서 폴더들을 그대로 경로에 얹는다.
for _dir in (RESEARCH_HOME,
             os.path.join(RESEARCH_HOME, "service"),
             os.path.join(RESEARCH_HOME, "tools")):
    if _dir not in sys.path:
        sys.path.insert(0, _dir)


# ══════════════════════════════════════════════════════════════
# 단계 · 예산 · 실패 등급
# ══════════════════════════════════════════════════════════════
#: 8단계. **이름은 계약의 일부다** — 프론트가 이 이름으로 진행 상황을 그린다.
STAGES_FULL = ("harness", "dryrun", "collect", "verdict", "canvas", "cards", "summary")
STAGES_BM = ("restore", "cards", "bm_adapter", "bm_model")

#: 이름표 하나로 **(컨셉 파일, 원장)** 이 정해진다.
#:
#: 되짚기(`_concept_path_of`)는 원장에 적힌 `concept_id` 를 믿는데, **그것이 틀어진 원장이
#: 실제로 있다** — `data/concept.json` 이 작업용 파일이라 판마다 갈아 끼워졌고, 그때
#: `concept_id` 를 안 고친 원장이 `CPT-CAFE-INV`(카페 재고 SaaS)로 남았다
#: (`beauty-09c`·`beauty-11`·`beauty-13b`·`nailrobot-06`). 되짚으면 **조용히 다른 가설로
#: 판정한다** — 관측은 미용실인데 잣대가 카페가 된다.
#:
#: 그래서 표를 명시로 두고, 표의 짝이 맞는지 `test_market_research.py` 가 강제한다.
#: 값은 무료 RESCORE 로 후보를 비교해 고른 것이다(성적표는 완료 보고에 남겼다).
CONCEPTS = {
    "beauty-noshow":    ("data/concept_beauty-noshow.json",    "beauty-13"),
    "household-ledger": ("data/concept_household-ledger.json", "ledger-05"),
    "pet-treat":        ("data/concept_pet-treat.json",        "pet-treat-15"),
}


class Grade:
    """실패 등급. **HARD 면 결과가 없다. SOFT 면 그 칸만 비고 나머지는 나간다.**

    가르는 기준: 그 단계가 없으면 **결과가 거짓이 되는가**. 판정·카드가 없으면 성적표가
    거짓이므로 HARD 다. 요약이 없으면 문장이 없을 뿐 값·등급·경계는 카드가 들고 있다 —
    SOFT 다(판 ㉛ fail-closed 와 같은 결).
    """

    HARD = "HARD"
    SOFT = "SOFT"


@dataclass
class Stage:
    name: str
    status: str = "SKIPPED"
    seconds: int = 0
    llm_calls: int = 0

    def as_contract(self) -> dict:
        return {"name": self.name, "status": self.status,
                "seconds": max(0, int(self.seconds)), "llmCalls": max(0, int(self.llm_calls))}


@dataclass
class Budget:
    """LLM 호출 예산. **판당 상한은 규율이지 최적화가 아니다**(§4 「항목 상한 도달 = 중단·기록」).

    남은 예산이 최소 소요보다 적으면 그 단계를 **시작하지 않는다** — 완주 못 할 지출은
    시작조차 낭비다(전원 응시 원칙 ⓑ).
    """

    total: int
    spent: int = 0

    def remaining(self) -> int:
        return max(0, self.total - self.spent)

    def can_afford(self, minimum: int) -> bool:
        return self.remaining() >= minimum

    def charge(self, calls: int) -> None:
        self.spent += max(0, int(calls))


@dataclass
class Run:
    """한 실행의 장부. 단계·저하·사용량을 **값으로** 모은다."""

    stages: list[Stage] = field(default_factory=list)
    degradations: list[dict] = field(default_factory=list)

    def stage(self, name: str) -> Stage:
        item = Stage(name)
        self.stages.append(item)
        return item

    def degrade(self, stage: str, code: str, detail: str) -> None:
        self.degradations.append({"stage": stage, "code": code, "detail": detail})


def _now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _read_result(run_id: str) -> dict:
    from .research2 import runpath
    path = os.path.join(runpath.read_dir(run_id), "result.json")
    with io.open(path, encoding="utf-8") as handle:
        return json.load(handle)


# ══════════════════════════════════════════════════════════════
# 사용자가 채운 실행 계획 — 요청에서 온다
# ══════════════════════════════════════════════════════════════
#: 화면이 받는 계획 칸. **컨셉 계약이 주지 않는 넷**이다(입구계약서 §1).
#: 수익모델·채널·차별점은 여기 없다 — 가설 4(`_hypotheses_v2`)가 이미 사용자 승인을 거친다.
#: ⚠ 키 이름은 `bm_adapter.PLAN_FIELDS` 와 같아야 한다. 다르면 조용히 안 실린다.
PLAN_KEYS = ("key_activities", "key_resources", "key_partners", "customer_relationship")
_LIST_PLAN_KEYS = ("key_activities", "key_resources", "key_partners")

#: 비용 구조 칸의 재료. **정수만** — taskInput 부동소수점 금지(canonical hash).
CONSTRAINT_KEYS = ("budget_krw", "months", "team")


def _plan_material(raw) -> dict:
    """요청의 `planMaterial` 을 계획 재료로 정리한다.

    ⚠ **빈 값을 만들어 넣지 않는다.** 빈 문자열·빈 배열을 실으면 「없다」와 「비었다」가
    같아지고, 모델은 프롬프트 §5~§9 대로 어차피 `content=[]` 를 낸다. 빈 칸은 **키 자체를
    빼서** 뒷단(`_bm_plan`·컨셉 파생)이 채울 수 있게 둔다.
    """
    if not isinstance(raw, dict):
        return {}
    out: dict = {}
    for key in PLAN_KEYS:
        value = raw.get(key)
        if key in _LIST_PLAN_KEYS:
            items = [str(x).strip() for x in value if str(x).strip()] \
                if isinstance(value, (list, tuple)) else []
            if items:
                out[key] = items
        elif isinstance(value, str) and value.strip():
            out[key] = value.strip()
    return out


def _plan_constraints(raw) -> dict:
    """요청의 `executionConstraints` — **정수만 받는다.**

    ⚠ 실수를 조용히 반올림하지 않는다. 백엔드가 이미 400 으로 막지만, 여기서도 버린다 —
    같은 규칙을 두 층이 지키는 것이 「조용히 다른 값」보다 낫다.
    """
    if not isinstance(raw, dict):
        return {}
    return {key: raw[key] for key in CONSTRAINT_KEYS
            if isinstance(raw.get(key), int) and not isinstance(raw.get(key), bool)}


#: 비용 세 칸의 사람이 읽는 이름·단위. 사용자가 넣은 **숫자를 다시 적을 뿐** 판단하지 않는다.
_CONSTRAINT_LABEL = (("budget_krw", "예산", "원"), ("months", "기간", "개월"),
                     ("team", "인원", "명"))


def _user_planned_cells(plan_material: dict, plan_constraints: dict) -> dict:
    """사용자가 실제로 채운 칸 → 그 칸에 들어갈 **사용자의 문장**.

    **비운 칸은 넣지 않는다** — 안 쓴 칸까지 도장을 내리면 컨셉 서술이 채운 칸까지
    「사용자가 썼다」로 표시된다.
    """
    out: dict[str, list[str]] = {}
    for key, value in plan_material.items():
        cell = serialize.USER_PLAN_CELL.get(key)
        if not cell:
            continue
        out[cell] = list(value) if isinstance(value, list) else [str(value)]
    if plan_constraints:
        out[serialize.USER_PLAN_CELL["constraint"]] = [
            f"{label} {plan_constraints[key]:,}{unit}"
            for key, label, unit in _CONSTRAINT_LABEL if key in plan_constraints]
    return out


# ══════════════════════════════════════════════════════════════
# 진입점
# ══════════════════════════════════════════════════════════════
async def run_market_research(task_input: dict, run_id: str,
                              timeout_seconds: float,
                              event_sink: EventSink | None = None,
                              diagnostic_context: dict[str, str] | None = None) -> dict:
    """`mode` 로 갈린다. 어느 갈래든 **봉투는 같고** 해당 없는 칸은 `null` 이다."""
    mode = (task_input.get("mode") or "FULL").strip().upper()
    if mode not in ("FULL", "BM", "RESCORE"):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", f"모드 불명: {mode}")

    concept_id = task_input.get("conceptId")
    if not isinstance(concept_id, str) or not concept_id.strip():
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "conceptId 없음")

    # 공식 Product FULL 은 immutable CPV2 snapshot 문자열을 받아 매 실행마다 A1~A3를
    # 수행한다. sample label/sourceRun은 fixtureMode 또는 RESCORE에서만 허용한다.
    if mode == "FULL" and isinstance(task_input.get("conceptSnapshotJson"), str):
        try:
            concept = json.loads(task_input["conceptSnapshotJson"])
        except (TypeError, ValueError) as failure:
            raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                        "conceptSnapshotJson 형식 불량") from failure
        if not isinstance(concept, dict) or not concept:
            raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "컨셉 스냅샷 없음")
        arguments = (concept, concept_id.strip(), run_id,
                     str(task_input.get("asOf") or _now()[:10]),
                     int(task_input.get("llmBudget") or 3), timeout_seconds, event_sink)
        if diagnostic_context is None:
            return await _product_full(*arguments)
        return await _product_full(*arguments, task_input, diagnostic_context)

    if mode == "BM" and isinstance(task_input.get("marketResultJson"), str):
        try:
            market_result = json.loads(task_input["marketResultJson"])
            concept = json.loads(task_input.get("conceptSnapshotJson") or "{}")
            legal = json.loads(task_input.get("legalContextJson") or "null")
        except (TypeError, ValueError) as failure:
            raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                        "BM immutable source JSON 형식 불량") from failure
        if not isinstance(market_result, dict) or market_result.get("mode") != "FULL":
            raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "Market FULL 결과 없음")
        try:
            return await asyncio.wait_for(_bm_product(
                market_result, concept, concept_id.strip(), run_id,
                Budget(total=int(task_input.get("llmBudget") or 1)),
                _plan_material(task_input.get("planMaterial")),
                _plan_constraints(task_input.get("executionConstraints")),
                legal if isinstance(legal, dict) else None, event_sink,
                diagnostic_context),
                timeout=max(0.001, timeout_seconds))
        except asyncio.TimeoutError as failure:
            raise _fail("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED",
                        "BM Product 실행이 기한을 넘겼다") from failure
        except (serialize.ContractDrift, ValidationError, KeyError, TypeError) as failure:
            raise _fail("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION",
                        "BM Product 결과 계약을 검증할 수 없다") from failure
        except _Hard as failure:
            if isinstance(failure.__cause__,
                          (serialize.ContractDrift, ValidationError, KeyError, TypeError)):
                raise _fail("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION",
                            "BM Product 결과 계약을 검증할 수 없다") from failure
            raise _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
                        "BM Product 실행을 완료할 수 없다") from failure
        except Exception as failure:  # noqa: BLE001 - Product 경계 밖 500 유출 금지
            raise _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
                        "BM Product 실행을 완료할 수 없다") from failure

    # ── fixture/RESCORE 이름표 해석. **공식 Product 경로가 아니다** ───────────
    if mode == "FULL" and not bool(task_input.get("fixtureMode")):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                    "공식 FULL 실행에는 conceptSnapshotJson 이 필요하다")
    # donor의 fixture/RESCORE/recollect orchestration은 원형 module에 위임한다.
    # 아래 helper들은 full product BM adapter의 호환 API로 남겨 두지만 이 진입 경로의
    # sourceRun/inline concept/partial recollect 의미를 재구현하지 않는다.
    from .pipeline import run_market_research as run_main_market_research
    return await run_main_market_research(task_input, run_id, timeout_seconds)

    # 이 아래는 이전 호환 helper의 정적 구현이다. 공개 진입점에서는 도달하지 않는다.
    # 기존 테스트·스모크는 `sourceRun` 을 직접 보낸다 — 그 길을 막지 않는다.
    # 표에도 없고 `sourceRun` 도 없으면 **조용한 기본값을 만들지 않고 실패**시킨다.
    preset = CONCEPTS.get(concept_id.strip())
    source_run = task_input.get("sourceRun") or (preset[1] if preset else None)
    if not isinstance(source_run, str) or not _SAFE_RUN_ID.fullmatch(source_run):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                    "sourceRun 형식 불량 — 아는 라벨: " + ", ".join(sorted(CONCEPTS)))
    if not os.path.isdir(os.path.join(RESEARCH_HOME, "runs", source_run)):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "sourceRun 원장 없음")

    concept_path = (task_input.get("conceptPath")
                    or (preset[0] if preset else None)
                    or _concept_path_of(source_run))
    if not concept_path:
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "컨셉 파일을 못 찾았다")

    budget = Budget(total=int(task_input.get("llmBudget") or 0))
    started = time.monotonic()

    # 사용자가 BM 앞 단계에서 채운 실행 계획. **컨셉 계약이 주지 않는 것들**이라
    # (입구계약서 §1 의 선택 필드에 활동·자원·파트너·고객 관계가 없다) 화면이 따로 받는다.
    plan_material = _plan_material(task_input.get("planMaterial"))
    plan_constraints = _plan_constraints(task_input.get("executionConstraints"))

    try:
        if mode == "BM":
            payload = await _bm(source_run, concept_path, concept_id, run_id, budget,
                                plan_material, plan_constraints)
        else:
            payload = await asyncio.to_thread(
                _full, source_run, concept_path, concept_id, run_id, budget,
                mode == "RESCORE")
    except serialize.ContractDrift as drift:
        raise _fail("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", str(drift)[:400])
    except _Hard as hard:
        raise _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", str(hard)[:400])

    if time.monotonic() - started > timeout_seconds:
        raise _fail("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", "예산을 넘겼다")
    return payload


async def _product_full(concept: dict, concept_id: str, run_id: str, as_of: str,
                        llm_budget: int, timeout_seconds: float,
                        event_sink: EventSink | None = None,
                        task_input: dict | None = None,
                        diagnostic_context: dict[str, str] | None = None) -> dict:
    """Task 임시 workspace에서 donor 수집 전 구간을 실행하고 봉투만 회수한다."""
    if not _SAFE_RUN_ID.fullmatch(run_id):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "runId 형식 불량")
    ai_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    with tempfile.TemporaryDirectory(prefix="market-product-") as workspace:
        runtime_input = task_input or {}
        runtime_context = diagnostic_context or {}
        from .market_ledger_artifact import MarketLedgerArtifactError, persist, restore
        if runtime_context:
            try:
                await restore(runtime_input, runtime_context, workspace)
            except MarketLedgerArtifactError as failure:
                raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", str(failure)) from failure
        input_path = os.path.join(workspace, "input.json")
        runtime_path = os.path.join(workspace, "runtime.json")
        output_path = os.path.join(workspace, "output.json")
        progress_path = os.path.join(workspace, "progress.jsonl")
        with io.open(input_path, "w", encoding="utf-8") as handle:
            json.dump(concept, handle, ensure_ascii=False, sort_keys=True)
        with io.open(runtime_path, "w", encoding="utf-8") as handle:
            json.dump({"sourceRun": runtime_input.get("sourceRun"),
                       "recollect": runtime_input.get("recollect")},
                      handle, ensure_ascii=False, sort_keys=True)
        env = dict(os.environ)
        env["RESEARCH2_RUNS_DIR"] = os.path.join(workspace, "runs")
        command = [
            sys.executable, "-u", "-m", "app.research.product_runner",
            "--input", input_path, "--output", output_path,
            "--workspace", workspace, "--run-id", run_id,
            "--concept-id", concept_id, "--as-of", as_of,
            "--runtime-input", runtime_path,
            "--llm-budget", str(max(0, llm_budget)),
        ]
        if event_sink is not None:
            command.extend(["--progress-jsonl", progress_path])
        try:
            process = await asyncio.create_subprocess_exec(
                *command,
                cwd=ai_root, env=env,
                stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        except OSError as failure:
            raise _fail("DEPENDENCY_UNAVAILABLE", "MODEL_DEPENDENCY_UNAVAILABLE",
                        "시장조사 엔진을 시작할 수 없다") from failure
        async def forward_progress() -> None:
            offset = 0
            while True:
                try:
                    with io.open(progress_path, encoding="utf-8") as handle:
                        handle.seek(offset)
                        for line in handle:
                            try:
                                event = json.loads(line)
                                _observe(event_sink, event.pop("stage"), event.pop("action"),
                                         event.pop("safeSummary"), **event)
                            except (TypeError, ValueError):
                                continue
                        offset = handle.tell()
                except OSError:
                    pass
                if process.returncode is not None:
                    return
                await asyncio.sleep(0.1)

        progress_task = asyncio.create_task(forward_progress()) if event_sink is not None else None
        try:
            _stdout, stderr = await asyncio.wait_for(
                process.communicate(), timeout=max(1.0, timeout_seconds))
        except asyncio.TimeoutError as failure:
            process.kill()
            await process.wait()
            raise _fail("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED",
                        "시장조사 실행이 기한을 넘겼다") from failure
        finally:
            if progress_task is not None:
                await progress_task
        if process.returncode != 0:
            detail = stderr.decode("utf-8", "replace").strip().splitlines()
            raise _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE",
                        detail[-1] if detail else "시장조사 엔진 실패")
        try:
            with io.open(output_path, encoding="utf-8") as handle:
                result = json.load(handle)
        except (OSError, ValueError) as failure:
            raise _fail("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION",
                        "시장조사 결과 봉투를 읽을 수 없다") from failure
        if runtime_context:
            try:
                await persist(runtime_input, runtime_context, workspace, concept_id)
            except MarketLedgerArtifactError as failure:
                raise _fail("DEPENDENCY_UNAVAILABLE", "ARTIFACT_STORAGE_UNAVAILABLE", str(failure)) from failure
        return result


class _Hard(RuntimeError):
    """HARD 실패 — 결과를 내지 않는다."""


def _concept_path_of(source_run: str) -> str | None:
    """원장이 어느 컨셉으로 수집됐는지는 **원장이 안다**. 호출자가 다시 고르게 두지 않는다.

    ⚠ 원장은 컨셉을 **값으로만** 담고 경로는 안 남긴다(`input.concept` 은 dict 다).
      그런데 엔진 함수들은 경로를 받는다 — 그래서 `concept_id` 로 `data/` 를 되짚는다.
      **못 찾으면 실패한다.** 아무 컨셉이나 집으면 원장과 다른 가설로 판정하게 된다.
    """
    try:
        result = _read_result(source_run)
    except (OSError, ValueError):
        return None
    concept_id = ((result.get("input") or {}).get("concept") or {}).get("concept_id")
    if not concept_id:
        return None
    data_dir = os.path.join(RESEARCH_HOME, "data")
    for name in sorted(os.listdir(data_dir)):
        if not name.startswith("concept") or not name.endswith(".json"):
            continue
        try:
            with io.open(os.path.join(data_dir, name), encoding="utf-8") as handle:
                if json.load(handle).get("concept_id") == concept_id:
                    return os.path.join("data", name)
        except (OSError, ValueError):
            continue
    return None


# ══════════════════════════════════════════════════════════════
# FULL · RESCORE
# ══════════════════════════════════════════════════════════════
def _full(source_run: str, concept_path: str, concept_id: str,
          run_id: str, budget: Budget, rescore: bool,
          collection_wired: bool = False,
          event_sink: EventSink | None = None) -> dict:
    import cards as CARDS                                          # noqa: PLC0415
    import scorecard as SCORECARD                                  # noqa: PLC0415
    import verdict as VERDICT                                      # noqa: PLC0415

    ledger = Run()

    # ── 아직 이 경로로는 안 도는 단계들. 「안 돌았다」를 값으로 남긴다 ─────────
    if collection_wired:
        for name in ("harness", "dryrun", "collect"):
            ledger.stage(name).status = "OK"
    else:
        for name in ("harness", "dryrun", "collect"):
            ledger.stage(name)
            ledger.degrade(name, "NOT_WIRED",
                           "저장된 수집(--from a4) 위에서 돈다 — 이 단계는 이번 실행에서 돌지 않았다")

    verdict = _timed(ledger, "verdict", lambda: VERDICT.build(source_run, concept_path))
    _observe(event_sink, "MARKET_VERDICT", "COMPLETED", "시장 판정 계산 완료")
    cards_doc = _timed(ledger, "cards", lambda: CARDS.build(source_run, concept_path))
    _observe(event_sink, "MARKET_CARDS", "COMPLETED", "시장 결과 카드 구성 완료")
    score = _timed(ledger, "scorecard",
                   lambda: SCORECARD.build(source_run, concept_path, verdict=verdict))
    _observe(event_sink, "MARKET_SCORECARD", "COMPLETED", "시장 점수표 계산 완료")

    cards = cards_doc.get("카드") or []
    evidence = serialize.evidence(cards)
    evidence_ids = {item["id"] for item in evidence}

    result = _read_result(source_run)
    not_found = dict(((result.get("report") or {}).get("not_found") or {}))
    if collection_wired:
        # donor fixture의 특정 카페 SaaS 실험에서만 유효한 정적 보고 문구다.
        # Product 수집 결과의 실제 결측·경계 정보는 유지하고 이 블록만 내보내지 않는다.
        not_found.pop("independent_topdown_blocked", None)
    market = serialize.market(
        verdict, cards,
        not_found,
        result.get("coverage_caveat"), evidence_ids,
        # 슬롯 정의 — 「S2」를 사람이 읽는 문구로 옮기는 데 쓴다. 원장에 이미 있어 새 I/O 는 없다.
        slots=((result.get("input") or {}).get("slots") or []))

    summary = _summary(ledger, budget, source_run, concept_path, evidence_ids, rescore)
    if summary is None:
        reason = ledger.degradations[-1]["code"] if ledger.degradations else "NOT_AVAILABLE"
        _observe(event_sink, "MARKET_SUMMARY", "SKIPPED", "시장 요약을 생략했습니다.",
                 status="DEGRADED", reasonCode=reason)
    else:
        _observe(event_sink, "MARKET_SUMMARY", "COMPLETED", "시장 요약 생성 완료")

    result = serialize.envelope(
        runId=run_id, conceptId=concept_id,
        asOf=str(result.get("reference_date") or _now()[:10]),
        generatedAt=_now(), mode="FULL",
        stages=[stage.as_contract() for stage in ledger.stages],
        degradations=ledger.degradations,
        scorecard=serialize.scorecard(score),
        market=market, canvas=None, bm=None,
        evidence=evidence, summary=summary,
        notes=list(serialize.NOTES_FULL))
    _observe(event_sink, "MARKET_SERIALIZATION", "COMPLETED", "시장조사 결과 정리 완료",
             status="COMPLETED")
    return result


def _summary(ledger: Run, budget: Budget, source_run: str, concept_path: str,
             evidence_ids: set[str], rescore: bool) -> list[dict] | None:
    """SOFT 단계. 없으면 문장이 없을 뿐 **값·등급·경계는 카드가 이미 들고 있다.**"""
    stage = ledger.stage("summary")
    if rescore:
        ledger.degrade("summary", "MODE_RESCORE", "재채점 모드는 LLM 을 부르지 않는다")
        return None
    minimum = 3                                     # 검사 재시도 상한과 같다
    if not budget.can_afford(minimum):
        ledger.degrade("summary", "BUDGET_EXHAUSTED",
                       f"남은 예산 {budget.remaining()} < 최소 소요 {minimum} — 건너뛴다")
        return None

    import summary as SUMMARY                                      # noqa: PLC0415
    began = time.monotonic()
    try:
        doc = SUMMARY.summarize(source_run, concept_path, max_retry=minimum)
    except Exception as error:                      # noqa: BLE001 — SOFT 다. 판을 죽이지 않는다
        stage.status = "FAILED"
        stage.seconds = int(time.monotonic() - began)
        ledger.degrade("summary", "STAGE_FAILED", f"{type(error).__name__}: {error}"[:200])
        return None

    calls = int((doc.get("_사용량") or {}).get("calls") or 0)
    budget.charge(calls)
    stage.seconds = int(time.monotonic() - began)
    stage.llm_calls = calls
    stage.status = "OK" if doc.get("요약") else "FAILED"
    if not doc.get("요약"):
        ledger.degrade("summary", "CHECK_FAILED",
                       str(doc.get("_요약_없음") or "검사 미통과 — 요약을 버리고 카드만 낸다"))
    return serialize.summary_lines(doc, evidence_ids)


def _timed(ledger: Run, name: str, work):
    """HARD 단계. 여기서 죽으면 **결과가 거짓이 되므로** 결과를 내지 않는다."""
    stage = ledger.stage(name)
    began = time.monotonic()
    try:
        out = work()
    except Exception as error:                      # noqa: BLE001
        stage.status = "FAILED"
        stage.seconds = int(time.monotonic() - began)
        raise _Hard(f"{name} 실패 — {type(error).__name__}: {error}") from error
    stage.status = "OK"
    stage.seconds = int(time.monotonic() - began)
    return out


# ══════════════════════════════════════════════════════════════
# BM
# ══════════════════════════════════════════════════════════════
async def _bm(source_run: str, concept_path: str, concept_id: str,
              run_id: str, budget: Budget,
              plan_material: dict | None = None,
              plan_constraints: dict | None = None) -> dict:
    from .bm.flow import run_bm_pipeline_flow                      # noqa: PLC0415
    from .bm.normalize import create_bm_analysis_input             # noqa: PLC0415

    ledger = Run()
    material = await asyncio.to_thread(_bm_material, ledger, source_run,
                                       concept_path, concept_id,
                                       plan_material or {}, plan_constraints or {})
    cards, market_join, constraints = material

    evidence = serialize.evidence(cards)

    stage = ledger.stage("bm_model")
    if not budget.can_afford(1):
        raise _Hard("BM 판정은 모델 호출 1회가 필수다 — 예산이 없다")

    began = time.monotonic()
    try:
        # ⚠ `execution_constraints` 를 빼면 비용 구조 칸이 **항상 빈다** — 프롬프트 §8 이
        #   「예산·기간·비용 정보가 전혀 없으면 content=[]」 이라서, 조용히 정상처럼 보인다.
        out = await run_bm_pipeline_flow(create_bm_analysis_input(
            market_data=market_join, execution_constraints=constraints))
    except Exception as error:                      # noqa: BLE001
        stage.status = "FAILED"
        stage.seconds = int(time.monotonic() - began)
        raise _Hard(f"bm_model 실패 — {type(error).__name__}: {error}") from error
    budget.charge(1)
    stage.status = "OK"
    stage.seconds = int(time.monotonic() - began)
    stage.llm_calls = 1

    result = _read_result(source_run)
    return serialize.envelope(
        runId=run_id, conceptId=concept_id,
        asOf=str(result.get("reference_date") or _now()[:10]),
        generatedAt=_now(), mode="BM",
        stages=[s.as_contract() for s in ledger.stages],
        degradations=ledger.degradations,
        scorecard=None, market=None,
        canvas={"cells": serialize.canvas_cells(
            out["bm_analysis"].canvas, evidence,
            _user_planned_cells(plan_material, plan_constraints))},
        bm=serialize.bm(out["final_result"], out["bm_analysis"], out.get("financial_handoff")),
        evidence=evidence, summary=None,
        notes=list(serialize.NOTES_BM))


async def _bm_product(market_result: dict, concept: dict, concept_id: str,
                      run_id: str, budget: Budget, plan_material: dict,
                      plan_constraints: dict, legal_context: dict | None,
                      event_sink: EventSink | None = None,
                      diagnostic_context: dict[str, str] | None = None) -> dict:
    from .bm.flow import run_bm_pipeline_flow
    from .bm.normalize import create_bm_analysis_input
    from .product_market_join import build as build_market_join

    ledger = Run()
    ledger.stage("restore").status = "OK"
    _observe(event_sink, "BM_RESTORE", "COMPLETED", "시장조사 결과 복원 완료")
    if plan_material:
        concept = {**concept, "_user_bm_plan": dict(plan_material)}
    if plan_constraints:
        concept = {**concept, "constraint": {**(concept.get("constraint") or {}),
                                             **plan_constraints}}
    market_join = _timed(
        ledger, "bm_adapter",
        lambda: build_market_join(market_result, concept, concept_id))
    _observe(event_sink, "BM_ADAPTER", "COMPLETED", "Market-BM 연결 자료 준비 완료")
    evidence = list(market_result.get("evidence") or [])
    stage = ledger.stage("bm_model")
    if not budget.can_afford(1):
        raise _Hard("BM 판정은 모델 호출 1회가 필수다 — 예산이 없다")
    began = time.monotonic()
    try:
        source = create_bm_analysis_input(
            market_data=market_join, legal_data=legal_context,
            execution_constraints=plan_constraints)
        out = await run_bm_pipeline_flow(
            source, diagnostic_context=diagnostic_context)
    except Exception as error:
        stage.status = "FAILED"
        stage.seconds = int(time.monotonic() - began)
        raise _Hard(f"bm_model 실패 — {type(error).__name__}: {error}") from error
    budget.charge(1)
    stage.status = "OK"
    stage.seconds = int(time.monotonic() - began)
    stage.llm_calls = 1
    _observe(event_sink, "BM_MODEL", "COMPLETED", "Business Model 분석 완료")
    result = serialize.envelope(
        runId=run_id, conceptId=concept_id,
        asOf=str(market_result.get("asOf") or _now()[:10]), generatedAt=_now(), mode="BM",
        stages=[s.as_contract() for s in ledger.stages],
        degradations=ledger.degradations, scorecard=None, market=None,
        canvas={"cells": serialize.canvas_cells(
            out["bm_analysis"].canvas, evidence,
            _user_planned_cells(plan_material, plan_constraints))},
        bm=serialize.bm(out["final_result"], out["bm_analysis"], out.get("financial_handoff")),
        evidence=evidence, summary=None, notes=list(serialize.NOTES_BM))
    _observe(event_sink, "BM_SERIALIZATION", "COMPLETED", "Business Model 결과 정리 완료",
             status="COMPLETED")
    return result


def _bm_material(ledger: Run, source_run: str, concept_path: str, concept_id: str,
                 plan_material: dict, plan_constraints: dict):
    """카드 · `MarketJoinData` · 실행 제약 — **엔진 안에서** 만든다.

    BM 층은 원장을 직접 읽지 않는다. 실행 제약도 여기서 꺼낸다 — 컨셉 파일을 두 번 열지
    않으려고 `adapt()` 안에서 같이 뽑는다.

    <b>사용자 입력이 들어오는 유일한 자리다.</b> 컨셉 dict 가 `_snapshot()` 과
    `execution_constraints_of()` 두 함수의 유일한 입력이라, 여기서 한 번 병합하면 그 뒤로는
    아무것도 안 고쳐도 모델까지 간다.
    """
    import bm_adapter as ADAPTER                                   # noqa: PLC0415
    import canvas as CANVAS                                        # noqa: PLC0415
    import cards as CARDS                                          # noqa: PLC0415
    import verdict as VERDICT                                      # noqa: PLC0415

    ledger.stage("restore").status = "OK"
    verdict = _timed(ledger, "verdict", lambda: VERDICT.build(source_run, concept_path))
    cards_doc = _timed(ledger, "cards", lambda: CARDS.build(source_run, concept_path))
    held: dict = {}

    def adapt():
        concept = json.load(io.open(os.path.join(RESEARCH_HOME, concept_path),
                                    encoding="utf-8"))
        # 사용자가 쓴 것이 **이긴다.** 견본의 `_bm_plan` 은 컨셉 계약 밖의 손으로 쓴
        # 스텁이라, 사용자가 같은 칸을 채웠는데 그것이 이기면 입력이 조용히 무시된다.
        if plan_material:
            concept = {**concept, "_user_bm_plan": dict(plan_material)}
        if plan_constraints:
            concept = {**concept, "constraint": {**(concept.get("constraint") or {}),
                                                 **plan_constraints}}
        held["constraints"] = ADAPTER.execution_constraints_of(concept)
        return ADAPTER.build_from(CANVAS.build(source_run, concept_path), verdict,
                                  cards_doc, concept, source_run, concept_id)

    market_join = _timed(ledger, "bm_adapter", adapt)
    return cards_doc.get("카드") or [], market_join, held.get("constraints") or {}
