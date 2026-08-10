# -*- coding: utf-8 -*-
"""시장조사·BM 오케스트레이터 — 8단계를 **선언 목록**으로 돌린다.

    FULL     1단계. 저장된 수집을 재채점 → 7과목 성적표 + 근거 원장 (+ 요약)
    BM       2단계. 같은 원장 위에서 BM 분석 1회 → 캔버스 9칸 + 판정
    RESCORE  FULL 과 같되 요약을 건너뛴다 (LLM 0회 · 무료 재채점)

⚠ **`collect`(A1~A3 수집)는 아직 이 오케스트레이터가 돌리지 않는다.** 전 구간은 LLM 80회·
   3.5분이고, 지금 배선은 `--from a4`(저장된 수집 재채점) 위에 서 있다. 단계 목록에는
   자리를 두되 상태를 `SKIPPED` + `degradation` 으로 **값으로 남긴다** — 안 돈 것을
   안 돌았다고 적는 것이 「조용한 실패」를 막는 유일한 방법이다.

**층 경계** — 여기는 «나르기»만 한다. 값을 만들지 않고, 판정하지 않고, 등급을 매기지 않는다.
번역(한글 키 → camelCase)과 allowlist 는 전부 `serialize.py` **한 곳**에 있다.
"""
from __future__ import annotations

import asyncio
import io
import json
import os
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone

from . import serialize
from .runner import RESEARCH_HOME, _SAFE_RUN_ID, _fail

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
    path = os.path.join(RESEARCH_HOME, "runs", run_id, "result.json")
    with io.open(path, encoding="utf-8") as handle:
        return json.load(handle)


# ══════════════════════════════════════════════════════════════
# 진입점
# ══════════════════════════════════════════════════════════════
async def run_market_research(task_input: dict, run_id: str,
                              timeout_seconds: float) -> dict:
    """`mode` 로 갈린다. 어느 갈래든 **봉투는 같고** 해당 없는 칸은 `null` 이다."""
    mode = (task_input.get("mode") or "FULL").strip().upper()
    if mode not in ("FULL", "BM", "RESCORE"):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", f"모드 불명: {mode}")

    concept_id = task_input.get("conceptId")
    if not isinstance(concept_id, str) or not concept_id.strip():
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "conceptId 없음")

    # ── 이름표 해석. **명시 입력이 항상 이긴다** ──────────────────────────────
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

    try:
        if mode == "BM":
            payload = await _bm(source_run, concept_path, concept_id, run_id, budget)
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
          run_id: str, budget: Budget, rescore: bool) -> dict:
    import cards as CARDS                                          # noqa: PLC0415
    import scorecard as SCORECARD                                  # noqa: PLC0415
    import verdict as VERDICT                                      # noqa: PLC0415

    ledger = Run()

    # ── 아직 이 경로로는 안 도는 단계들. 「안 돌았다」를 값으로 남긴다 ─────────
    for name in ("harness", "dryrun", "collect"):
        ledger.stage(name)
        ledger.degrade(name, "NOT_WIRED",
                       "저장된 수집(--from a4) 위에서 돈다 — 이 단계는 이번 실행에서 돌지 않았다")

    verdict = _timed(ledger, "verdict", lambda: VERDICT.build(source_run, concept_path))
    cards_doc = _timed(ledger, "cards", lambda: CARDS.build(source_run, concept_path))
    score = _timed(ledger, "scorecard",
                   lambda: SCORECARD.build(source_run, concept_path, verdict=verdict))

    cards = cards_doc.get("카드") or []
    evidence = serialize.evidence(cards)
    evidence_ids = {item["id"] for item in evidence}

    result = _read_result(source_run)
    market = serialize.market(
        verdict, cards,
        ((result.get("report") or {}).get("not_found") or {}),
        result.get("coverage_caveat"), evidence_ids,
        # 슬롯 정의 — 「S2」를 사람이 읽는 문구로 옮기는 데 쓴다. 원장에 이미 있어 새 I/O 는 없다.
        slots=((result.get("input") or {}).get("slots") or []))

    summary = _summary(ledger, budget, source_run, concept_path, evidence_ids, rescore)

    return serialize.envelope(
        runId=run_id, conceptId=concept_id,
        asOf=str(result.get("reference_date") or _now()[:10]),
        generatedAt=_now(), mode="FULL",
        stages=[stage.as_contract() for stage in ledger.stages],
        degradations=ledger.degradations,
        scorecard=serialize.scorecard(score),
        market=market, canvas=None, bm=None,
        evidence=evidence, summary=summary,
        notes=list(serialize.NOTES_FULL))


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
              run_id: str, budget: Budget) -> dict:
    from .bm.flow import run_bm_pipeline_flow                      # noqa: PLC0415
    from .bm.normalize import create_bm_analysis_input             # noqa: PLC0415

    ledger = Run()
    material = await asyncio.to_thread(_bm_material, ledger, source_run,
                                       concept_path, concept_id)
    cards, market_join = material

    evidence = serialize.evidence(cards)

    stage = ledger.stage("bm_model")
    if not budget.can_afford(1):
        raise _Hard("BM 판정은 모델 호출 1회가 필수다 — 예산이 없다")

    began = time.monotonic()
    try:
        out = await run_bm_pipeline_flow(create_bm_analysis_input(market_data=market_join))
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
        canvas={"cells": serialize.canvas_cells(out["bm_analysis"].canvas, evidence)},
        bm=serialize.bm(out["final_result"], out["bm_analysis"]),
        evidence=evidence, summary=None,
        notes=list(serialize.NOTES_BM))


def _bm_material(ledger: Run, source_run: str, concept_path: str, concept_id: str):
    """카드와 `MarketJoinData` — **엔진 안에서** 만든다. BM 층은 원장을 직접 읽지 않는다."""
    import bm_adapter as ADAPTER                                   # noqa: PLC0415
    import canvas as CANVAS                                        # noqa: PLC0415
    import cards as CARDS                                          # noqa: PLC0415
    import verdict as VERDICT                                      # noqa: PLC0415

    ledger.stage("restore").status = "OK"
    verdict = _timed(ledger, "verdict", lambda: VERDICT.build(source_run, concept_path))
    cards_doc = _timed(ledger, "cards", lambda: CARDS.build(source_run, concept_path))

    def adapt():
        concept = json.load(io.open(os.path.join(RESEARCH_HOME, concept_path),
                                    encoding="utf-8"))
        return ADAPTER.build_from(CANVAS.build(source_run, concept_path), verdict,
                                  cards_doc, concept, source_run, concept_id)

    return cards_doc.get("카드") or [], _timed(ledger, "bm_adapter", adapt)
