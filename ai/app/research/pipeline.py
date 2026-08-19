# -*- coding: utf-8 -*-
"""시장조사·BM 오케스트레이터 — 8단계를 **선언 목록**으로 돌린다.

    FULL     1단계. 저장된 수집을 재채점 → 7과목 성적표 + 근거 원장 (+ 요약)
    BM       2단계. 같은 원장 위에서 BM 분석 1회 → 캔버스 9칸 + 판정
    RESCORE  FULL 과 같되 요약을 건너뛴다 (LLM 0회 · 무료 재채점)

**`collect`(A1~A3 수집)는 이 오케스트레이터가 돌린다** — `_collect()` 가 그 자리다.
   원장이 **없을 때만** 산다(전 구간 LLM ≈80회 · 유료). 원장이 있으면 `--from a4` 로
   재채점만 하고, 그 사실을 `SKIPPED` + `degradation` 으로 **값으로 남긴다** — 안 돈 것을
   안 돌았다고 적는 것이 「조용한 실패」를 막는 유일한 방법이다.
   (⚠ 이 문단은 오랫동안 「아직 안 돌린다」라고 적혀 있었다. `_collect` 가 생긴 뒤에도
    안 고쳐서 **문서가 코드보다 두 판 뒤에** 있었다 — 판 ㉜ 에서 바로잡았다.)

**재수집** — `taskInput.recollect` 가 있으면 원장이 있어도 다시 산다. 설계부터 다시 돌고
   (`재설계`), `slots` 에 적힌 슬롯만 새로 사서 복원한 원장에 **합친다.** 지시가 없으면
   동작이 한 줄도 안 바뀐다. 판 ㉜ 까지 이 길이 없어서 **설계가 나쁜 채로 한 번 돌면 그
   사업안은 영영 그 설계를 썼다.** 지금은 AI 층까지만 배선돼 있다 — 내부 계약 v1·Java·
   openapi 무변경이라 **화면에서는 못 누르고 CLI·시험에서만 닿는다.**

**층 경계** — 여기는 «나르기»만 한다. 값을 만들지 않고, 판정하지 않고, 등급을 매기지 않는다.
번역(한글 키 → camelCase)과 allowlist 는 전부 `serialize.py` **한 곳**에 있다.
"""
from __future__ import annotations

import asyncio
import io
import json
import os
import shutil
import sys
import tempfile
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
STAGES_FULL = ("harness", "dryrun", "collect", "verdict", "canvas", "cards", "summary",
               # 판 ㊸ — 문서를 **절 단위로** 다시 읽어 2·8·9절을 만든다. SOFT.
               "sections")
#: ⚠ `promote` 는 2026-08-15 신설 — 원장의 `sections.json` 을 다시 세어 절 사실을 근거
#: 카드로 올린다. **LLM 0회**라 예산에 청구하지 않는다.
STAGES_BM = ("restore", "cards", "promote", "bm_adapter", "bm_model")

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
#:
#: ⚠ **이제 컨셉을 정하는 길이 둘이다.** 요청이 컨셉을 실어 보내면(`_inline_concept`)
#:   그것이 이기고, 표는 **원장만** 정한다. 그 길에는 표의 짝 검사가 닿지 않으므로
#:   `_assert_same_concept` 가 같은 일을 실행 시점에 한다 — 새 길이 그물을 비껴가면
#:   카페 사고를 만드는 장치가 그대로 부활한다.
CONCEPTS = {
    "beauty-noshow":    ("data/concept_beauty-noshow.json",    "beauty-13"),
    "household-ledger": ("data/concept_household-ledger.json", "ledger-05"),
    "pet-treat":        ("data/concept_pet-treat.json",        "pet-treat-15"),
    # 대기업 신사업 견본 (판 ㉛ · 계열 C). 경쟁사가 전부 공시법인이고 표시가가 공개돼 있어
    # COMPETITOR·PRICE 칸이 **자료 부재가 아닌 이유로** 비는지 가릴 수 있다.
    "hmr-solo":         ("data/concept_hmr-solo.json",         "hmr-01"),
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
    import runpath                                                 # noqa: PLC0415
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

    # ── 실린 컨셉. **여기가 사업안이 들어오는 문이다** ────────────────────────
    # 지금까지 `taskInput.concept` 은 화면이 `null` 을 보내던 죽은 자리였고, 실제 컨셉은
    # 이름표가 100% 정했다. 실린 컨셉이 있으면 **그것이 이긴다** — 이름표는 원장만 정한다.
    inline = _inline_concept(task_input)

    # 원장은 **두 자리**에 있을 수 있다 — 씨앗 `runs/`(`:ro`) 와 수집이 만든
    # `runs-generated/`. 답은 `runpath` 한 곳이다.
    import runpath                                                 # noqa: PLC0415

    # ── 원장이 없으면 **만든다** ─────────────────────────────────────────────
    # 새 사업안에는 원장이 없다. 예전에는 여기서 실패시켰고, 그래서 시장조사는 견본 셋
    # 위에서만 돌았다. 이름표를 원장 이름으로 쓴다 — 같은 사업안을 다시 누르면 만들어 둔
    # 원장을 **재채점**한다(수집은 유료다. 누를 때마다 다시 사지 않는다).
    collect = False
    if source_run is None:
        # **표에 없는 이름표 = 제품 사업안이다.** 원장 이름이 곧 `conceptId` 다 —
        # 1단계(FULL)가 그 이름으로 원장을 만든다.
        #
        # ⚠ 예전에는 이 갈래가 `inline is not None` 일 때만 돌았다. BM 요청은 컨셉을
        #   `concept-ref` 로 싣기 때문에 `_inline_concept` 이 `None` 이고, 그래서
        #   `source_run` 이 `None` 인 채로 「sourceRun 형식 불량」 400 을 맞았다 —
        #   **BM 2단계는 제품 사업안으로 한 번도 돌아본 적이 없다**(2026-08-12 실측).
        #   견본 라벨 4개만 표에 있어서 그 넷으로만 돌았고, 그 결과 냉동 간편식
        #   프로젝트의 BM 캔버스에 **미용실 고객 세그먼트**가 떴다.
        source_run = concept_id.strip()
        # ⚠ **디렉터리가 아니라 `result.json` 을 본다**(판 ㊲). 실측: 수집이 7분째 돌던 중
        #   컨테이너가 새로 만들어져 실행이 죽었고 `run.jsonl` 만 남았다. `exists()` 로 보면
        #   「원장이 있다」라서 다음 실행이 수집을 건너뛰고 그 반쪽을 읽으려다 400 으로 죽는다 —
        #   **그 사업안이 영영 수집을 못 하는 상태로 굳었다.** 재현 조건이 「실행이 죽었다」라
        #   흔하다.
        collect = not runpath.complete(source_run)

    # ── 재수집 지시 (판 ㉜) ──────────────────────────────────────────────────
    # **원장이 있으면 재채점뿐이었다.** 그래서 설계가 나쁜 채로 한 번 돌면 그 사업안은
    # 영영 그 설계를 썼다 — 판 ㉜ 이 그것을 「고쳐도 되돌릴 길이 없다」로 남겼다.
    # 엔진에는 부분 재수집이 **이미 완성**돼 있는데(`run.py --from a4 --collect-slots`)
    # 이 오케스트레이터가 그 인자를 안 넘겨서 닿지 못했을 뿐이다. 여기서 넘긴다.
    #
    # ⚠ **지시가 없으면 동작이 한 줄도 안 바뀐다.** 기본 경로는 종전 그대로다.
    # ⚠ 이번 판은 **AI 층까지만**이다 — 내부 계약 v1·InputFactory·openapi·Java 무변경이라
    #   화면에서는 못 누른다. CLI·시험에서만 닿는다(사용자 결정).
    recollect = _recollect(task_input.get("recollect"))
    if recollect:
        # 재수집도 **읽을 수 있는** 원장 위에서만 한다 — 반쪽 원장은 물려받을 수집이 없다.
        if not runpath.complete(source_run or ""):
            raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                        "재수집은 기존 원장 위에서만 한다 — sourceRun 원장이 없다")
        collect = True                  # 설계부터 다시 돈다(그것이 「재설계」다)

    if not isinstance(source_run, str) or not _SAFE_RUN_ID.fullmatch(source_run):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                    "sourceRun 형식 불량 — 아는 라벨: " + ", ".join(sorted(CONCEPTS)))
    if not collect and not runpath.complete(source_run):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION", "sourceRun 원장 없음")
    if collect and mode == "BM":
        # BM 은 1단계 원장 위에서만 선다. 여기서 수집을 시작하면 **캔버스가 근거 없이** 나온다.
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                    "BM 은 1단계 원장이 있어야 한다 — 시장조사를 먼저 실행하라")

    workdir = None
    if inline is not None:
        # 새로 수집하는 판은 대조할 원장이 아직 없다. 원장이 **이 컨셉으로** 만들어지므로
        # 짝은 구조적으로 맞는다 — 대조는 이미 있는 원장을 재채점할 때만 뜻이 있다.
        if not collect:
            _assert_same_concept(source_run, inline)
        workdir = tempfile.mkdtemp(prefix="research-concept-")
        # 엔진 함수들(`verdict.build`·`cards.build`·`bm_adapter`)은 dict 가 아니라 **경로**를
        # 받아 스스로 `json.load` 한다. 그래서 쓸 수 있는 자리에 파일로 떨군다.
        # `data/` 밖에 두는 이유는 `_concept_path_of` 의 되짚기와 부딪치지 않기 위해서다.
        concept_path = os.path.join(workdir, "concept.json")
        with io.open(concept_path, "w", encoding="utf-8") as handle:
            json.dump(inline, handle, ensure_ascii=False)
    else:
        concept_path = (task_input.get("conceptPath")
                        or (preset[0] if preset else None)
                        or _concept_path_of(source_run))
        if not concept_path:
            # **제품 사업안은 `data/` 에 파일이 없다.** 컨셉이 요청에 실려 들어왔을 뿐
            # 디스크에 쓰인 적이 없으므로 `_concept_path_of` 의 되짚기가 구조적으로 실패한다.
            # 그런데 **원장이 그 컨셉을 값으로 들고 있다**(`result.json.input.concept`) —
            # 되짚지 말고 그것을 쓴다. 이 갈래가 없으면 BM 2단계가 제품 사업안에서
            # 「컨셉 파일을 못 찾았다」로 죽는다(2026-08-12 실측).
            from_ledger = _concept_of_run(source_run)
            if from_ledger:
                workdir = tempfile.mkdtemp(prefix="research-concept-")
                concept_path = os.path.join(workdir, "concept.json")
                with io.open(concept_path, "w", encoding="utf-8") as handle:
                    json.dump(from_ledger, handle, ensure_ascii=False)
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
                mode == "RESCORE", collect, str(task_input.get("asOf") or "")[:10],
                recollect)
    except serialize.ContractDrift as drift:
        raise _fail("RESULT_SCHEMA_INVALID", "RESULT_FIELD_CONSTRAINT_VIOLATION", str(drift)[:400])
    except _Hard as hard:
        raise _fail("EXECUTION_FAILED", "TRANSIENT_EXECUTION_FAILURE", str(hard)[:400])
    finally:
        if workdir:
            shutil.rmtree(workdir, ignore_errors=True)

    if time.monotonic() - started > timeout_seconds:
        raise _fail("DEADLINE_EXCEEDED", "REQUEST_DEADLINE_EXCEEDED", "예산을 넘겼다")
    return payload


class _Hard(RuntimeError):
    """HARD 실패 — 결과를 내지 않는다."""


def _inline_concept(task_input: dict) -> dict | None:
    """요청에 실린 컨셉. **지금까지 죽어 있던 자리다.**

    화면이 컨셉 자리에 `null` 을 보내던 자리라(`MarketResearchPage.jsx:46`) 백엔드는 그것을
    `"null"` 문자열로 날랐다. 그래서 **못 읽으면 조용히 없는 것으로 본다** — 견본 이름표
    경로가 그대로 돌아야 한다. BM 모드가 싣는 `concept-ref`(`conceptId=…` 한 줄)도 여기서
    걸러진다.

    ⚠ 청크는 **빈 문자열로 잇는다.** 원문을 코드포인트로 자른 것이라 사이에 무엇이든 끼우면
      JSON 이 깨진다(`MarketResearchInputFactory.textContents`).
    """
    for content in task_input.get("textContents") or []:
        if content.get("contentKey") != "concept":
            continue
        text = "".join(str(chunk.get("text") or "") for chunk in content.get("chunks") or [])
        try:
            value = json.loads(text)
        except ValueError:
            return None
        if isinstance(value, dict) and isinstance(value.get("concept_id"), str) \
                and value["concept_id"].strip():
            return value
        return None
    return None


def _assert_same_concept(source_run: str, concept: dict) -> None:
    """**실린 컨셉과 원장이 같은 컨셉인가.** 이 검사가 카페 사고를 막는 그물이다.

    기존 그물(`test_market_research.py:188`)은 `CONCEPTS` 표 세 줄만 대조하므로 **실린 컨셉이
    오는 새 길은 그 검사를 통째로 비껴간다.** 여기서 안 막으면 「관측은 미용실, 잣대는 내
    사업안」이 되고 읽는 사람은 구분하지 못한다 — 그런 원장이 실제로 넷 있었다.

    못 읽는 원장도 **통과시키지 않는다.** 대조할 수 없다는 것은 짝이 맞다는 뜻이 아니다.
    """
    declared = concept["concept_id"].strip()
    try:
        recorded = ((_read_result(source_run).get("input") or {}).get("concept") or {}).get("concept_id")
    except (OSError, ValueError):
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                    f"원장 {source_run} 을 읽을 수 없어 컨셉 대조를 못 한다") from None
    if not recorded:
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                    f"원장 {source_run} 에 concept_id 가 없어 컨셉 대조를 못 한다")
    if recorded != declared:
        raise _fail("INVALID_REQUEST", "FIELD_CONSTRAINT_VIOLATION",
                    f"실린 컨셉과 원장이 다른 컨셉이다 — 컨셉 {declared}, 원장 {source_run} 은 {recorded}")


def _concept_of_run(source_run: str) -> dict | None:
    """원장이 **값으로** 들고 있는 컨셉. 되짚기가 실패하는 자리를 메운다.

    ⚠ `_concept_path_of` 는 `data/` 에서 같은 `concept_id` 를 가진 파일을 찾는데,
    제품 사업안은 요청에 실려 들어올 뿐 **디스크에 쓰인 적이 없다** — 구조적으로 못 찾는다.
    원장의 `input.concept` 이 바로 그 컨셉이므로 그것을 그대로 쓴다.
    """
    try:
        result = _read_result(source_run)
    except (OSError, ValueError):
        return None
    concept = (result.get("input") or {}).get("concept")
    if isinstance(concept, dict) and isinstance(concept.get("concept_id"), str):
        return concept
    return None


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
def _recollect(raw) -> dict:
    """재수집 지시를 **값으로** 읽는다. 없거나 모양이 아니면 빈 dict — 조용히 기본 경로다.

    받는 칸(전부 선택):
        slots      : 쉼표로 슬롯 id. 이것만 새로 수집해 복원한 원장에 **합친다**
        slotsFrom  : `source`(기본) | `current` — 사람 칸을 현재 값으로 덮을지
        from       : `a4`(기본) | `extract` — 어디서부터 복원할지

    **전체 재수집을 기본으로 두지 않는다.** 검색은 실행마다 흔들려서(회수율 0.2↔0.5)
    전부 다시 돌리면 **이미 확보한 확인됨이 사라질 수 있다**(`run.py --collect-slots` 주석).
    재수집하지 않은 슬롯은 원본 결과를 그대로 쓰므로 기존 확인됨이 구조적으로 보존된다.
    """
    if not isinstance(raw, dict):
        return {}
    slots = str(raw.get("slots") or "").strip()
    frm = str(raw.get("from") or "a4").strip()
    sfrom = str(raw.get("slotsFrom") or "source").strip()
    if frm not in ("a4", "extract") or sfrom not in ("source", "current"):
        return {}
    return {"slots": slots, "from": frm, "slotsFrom": sfrom}


def _full(source_run: str, concept_path: str, concept_id: str,
          run_id: str, budget: Budget, rescore: bool,
          collect: bool = False, as_of: str = "",
          recollect: dict | None = None) -> dict:
    import cards as CARDS                                          # noqa: PLC0415
    import scorecard as SCORECARD                                  # noqa: PLC0415
    import verdict as VERDICT                                      # noqa: PLC0415

    ledger = Run()

    if collect:
        # **원장이 없다 — 만든다.** 이 갈래가 붙기 전에는 견본 원장 셋 위에서만 돌았다.
        # 재수집 지시가 있으면 같은 갈래를 타되 **일부 슬롯만** 새로 사서 합친다.
        _collect(ledger, budget, concept_path, source_run, as_of or _now()[:10],
                 recollect or {})
    else:
        # 저장된 수집 위에서 재채점한다. 「안 돌았다」를 값으로 남긴다 —
        # 안 돈 것을 안 돌았다고 적는 것이 「조용한 실패」를 막는 유일한 방법이다.
        for name in ("harness", "dryrun", "collect"):
            ledger.stage(name)
            ledger.degrade(name, "NOT_WIRED",
                           "저장된 수집(--from a4) 위에서 돈다 — 이 단계는 이번 실행에서 돌지 않았다")

    verdict = _timed(ledger, "verdict", lambda: VERDICT.build(source_run, concept_path))
    cards_doc = _timed(ledger, "cards", lambda: CARDS.build(source_run, concept_path))
    score = _timed(ledger, "scorecard",
                   lambda: SCORECARD.build(source_run, concept_path, verdict=verdict))

    # ⚠ **절 체인을 카드 조립보다 먼저** 돌린다 — 승격 카드가 `evidence[]` 에 합류해야
    #   화면의 채널·원가·규제 과목이 차고, 9절이 인용한 수를 검산할 자리가 생긴다.
    #   슬롯 카드 15장과 절 사실 132건은 **겹치지 않는 두 목록**이다(판 ㊸ 0단계 실측).
    절 = _sections(ledger, budget, source_run, concept_path, run_id, rescore)

    # ⚠ **승격 카드는 `evidence[]` 에만 넣는다.** `serialize.market()` 은 카드에서 가격·
    #   시장 값을 뽑는데(`_price` 가 카드를 훑는다), 절 사실 132건을 같이 넘기면 TAM·
    #   대표 단가가 **조용히 다른 값이 된다.** 배선이 판정을 바꾸면 그것은 배선이 아니다.
    cards = cards_doc.get("카드") or []
    evidence = serialize.evidence(cards + 절["cards"])
    evidence_ids = {item["id"] for item in evidence}

    result = _read_result(source_run)
    market = serialize.market(
        verdict, cards,
        ((result.get("report") or {}).get("not_found") or {}),
        result.get("coverage_caveat"), evidence_ids,
        # 슬롯 정의 — 「S2」를 사람이 읽는 문구로 옮기는 데 쓴다. 원장에 이미 있어 새 I/O 는 없다.
        slots=((result.get("input") or {}).get("slots") or []))

    summary = _summary(ledger, budget, source_run, concept_path, evidence_ids, rescore)

    # 절이 안 붙은 근거(슬롯 카드)에 절을 붙인다. **답이 나는 자리를 하나로** 만든다 —
    # 지금까지 프론트 `bucketEvidence` 가 같은 물음을 다시 풀었다.
    serialize.assign_sections(evidence, market)

    return serialize.envelope(
        runId=run_id, conceptId=concept_id,
        asOf=str(result.get("reference_date") or _now()[:10]),
        generatedAt=_now(), mode="FULL",
        stages=[stage.as_contract() for stage in ledger.stages],
        degradations=ledger.degradations,
        scorecard=serialize.scorecard(score, 절["counts"]),
        market=market, canvas=None, bm=None,
        evidence=evidence, summary=summary,
        notes=list(serialize.NOTES_FULL),
        judgment=절["judgment"], prescriptions=절["prescriptions"],
        synthesis=절["synthesis"], report=절["report"])


#: 절 체인이 문서 하나를 읽는 데 LLM 1회. 문서 수를 미리 모르니 **최소 소요**로 잰다.
#: 이 값보다 예산이 적으면 시작조차 안 한다 — 완주 못 할 지출은 시작이 낭비다.
_SECTIONS_MIN_CALLS = 30

#: 요약(SOFT)이 쓰는 최소 호출. **절 체인이 이만큼은 남겨 둔다.**
#: ⚠ 안 남기면 새 것을 얻고 **지금 사용자가 보고 있는 요약을 잃는다** — 절 체인이
#:   `_summary` 보다 앞에 있어 선착순이면 뒤가 굶는다.
_SUMMARY_RESERVE = 3

#: 9절 합성 1회. **이것도 예비분에서 빼야 한다** — 안 그러면 읽기가 예비분을 정확히
#: 3만 남기고, 합성이 그중 1을 먹어 요약이 `minimum=3` 에서 떨어진다.
#: 고치려던 병(「새 것을 얻고 요약을 잃는다」)이 **잘린 실행에서 그대로 재현**된다.
_SYNTH_CALLS = 1


def _sections(ledger: Run, budget: Budget, source_run: str, concept_path: str,
              run_id: str, rescore: bool) -> dict:
    """**절 체인.** 문서를 절 단위로 다시 읽고 → 게재를 판정하고 → 2·8·9절을 만든다.

    돌려주는 것은 `{"cards", "counts", "judgment", "prescriptions", "synthesis"}` 이고
    **어느 것도 없으면 그 자리는 `None` 이다** — 빈 목록으로 돌려주면 화면이 「할 말이
    없었다」로 그리고 그것은 거짓이다.

    ## 왜 SOFT 인가
    이 단계가 죽어도 시장 크기·성적표·캔버스는 그대로 나간다. 판을 죽일 이유가 없다.
    다만 **조용히 죽지는 않는다** — `degrade` 로 사유가 봉투까지 간다.

    ## ⚠ 비싼 자리
    `read_sections` 는 **문서 수만큼** LLM 을 부른다(실측 182건). 그래서 원장에 이미
    `sections.json` 이 있으면 **그것을 쓴다** — 같은 문서를 두 번 사지 않는다.
    """
    import runpath                                                 # noqa: PLC0415
    import judge_lines as JUDGE                                    # noqa: PLC0415
    import prescribe as PRESCRIBE                                  # noqa: PLC0415
    import promote_cards as PROMOTE                                # noqa: PLC0415
    import publish_gate as GATE                                    # noqa: PLC0415
    import read_sections as READ                                   # noqa: PLC0415
    import reask_sections as REASK                                 # noqa: PLC0415
    import synthesize as SYNTH                                     # noqa: PLC0415

    빈 = {"cards": [], "counts": None, "judgment": None,
          "prescriptions": None, "synthesis": None, "report": None}
    stage = ledger.stage("sections")
    began = time.monotonic()
    # ⚠ `concept_path` 는 **research2 뿌리 기준 상대 경로**다(`_concept_path_of`). 그대로
    #   열면 CWD 가 다른 곳에서 죽는다 — 다른 단계들은 research2 안에서 열려 안 드러났다.
    path = (concept_path if os.path.isabs(concept_path)
            else os.path.join(RESEARCH_HOME, concept_path))
    concept = json.load(io.open(path, encoding="utf-8"))

    cache = os.path.join(runpath.read_dir(source_run), "sections.json")
    if os.path.isfile(cache):
        sections = json.load(io.open(cache, encoding="utf-8"))
    elif rescore:
        ledger.degrade("sections", "MODE_RESCORE", "재채점 모드는 LLM 을 부르지 않는다")
        return 빈
    elif not budget.can_afford(_SECTIONS_MIN_CALLS + _SUMMARY_RESERVE + _SYNTH_CALLS):
        ledger.degrade("sections", "BUDGET_EXHAUSTED",
                       f"남은 예산 {budget.remaining()} < 최소 소요 {_SECTIONS_MIN_CALLS}"
                       f" + 요약 몫 {_SUMMARY_RESERVE} + 9절 {_SYNTH_CALLS}")
        return 빈
    else:
        # ⚠ **문서 상한을 반드시 준다.** 안 주면 원장 전량(실측 182건)을 부르고
        #    **그 뒤에** 청구한다 — 상한 90짜리 예산이 182를 쓰고 나서 알게 된다.
        여유 = budget.remaining() - _SUMMARY_RESERVE - _SYNTH_CALLS
        try:
            # ★ **`pdf_refetch=True` 를 «반드시» 넘긴다** (판 ㊺).
            #
            # 실측: 이 인자는 CLI 에만 있었고 **제품은 한 번도 안 넘겼다.** 그래서 제품은
            # 옛 추출기가 만든 본문을 그대로 쓰는데, 그 본문은 **2단 조판 PDF 가 줄 단위로
            # 뒤섞여** 있어 온전한 문장이 존재하지 않는다 — 인용 대조가 **구조적으로**
            # 통과할 수 없다. 실례: 「…2026년부터 단계적으로 **영양표시 비중은 즉석조리식품
            # 45.4%…** 를 의무화할 것을」 — 왼쪽 단과 오른쪽 단이 한 줄씩 번갈아 섞였다.
            #
            # `_refetch_pdfs` 는 PDF 를 다시 받아 **지금의** `pdf_text` 로 다시 뽑고
            # **LLM 을 0회 부른다.** 원장 112건 중 PDF 는 14건(12%)이다.
            # ⚠ 못 받은 문서는 옛 본문 그대로 두고 사유를 남긴다 — **조용히 실패하지 않는다.**
            sections, run = READ.build(source_run, f"{run_id}-sec", limit=여유,
                                       pdf_refetch=True)
            부른_횟수 = int(run.counters.get("llm.calls", 0))
            budget.charge(부른_횟수)
            # ⚠ **예산에만 청구하고 단계에는 안 적으면 원장이 거짓말한다.** 실측(유료 스모크
            #   2026-08-15): 문서 132건을 읽고도 봉투의 단계표는 `llmCalls: 0` 이었고,
            #   실행 전체가 137회를 부르고 **4회라고 보고했다** — 34배다.
            stage.llm_calls += 부른_횟수
            # ⚠ **잘림은 호출 수로 되짚지 않는다.** LLM 실패가 섞이면 `쓴 < 여유` 가 되어
            #    **잘렸는데 표시가 안 붙는다.** 잘림을 아는 것은 `READ.build` 안이다.
            안본 = int(sections.get("안_읽은_문서") or 0)
            if 안본:
                # **읽다 만 것을 다 읽은 것처럼 두지 않는다.** 절이 비면 사업가는
                # 「없다」로 읽는데, 실제로는 「예산이 끊겼다」다.
                ledger.degrade("sections", "SECTIONS_TRUNCATED",
                               f"예산 상한 {여유}건까지만 읽었다 — 원장의 문서 {안본}건을 안 봤다")

            # ── ★ 얇은 절을 **다시 묻는다** (판 ㊹ 1단계) ──────────────
            #
            # **왜 한 번 더 부르나** — `read_sections` 는 문서 하나에 「7절 중 무엇을
            # 채우나」를 **한 번** 묻는다. 판 ㊵·㊶ 실측: 그렇게 물으면 모델이 **한 주제만
            # 긁는다.** 같은 문서·같은 글자·같은 모델에서 질문만 절 단위로 쪼갰더니
            # 채널 4→31 · 원가 20→82 · 규제 9→29 · 가격 136→321 이 됐다.
            # **이것이 판 ㊹ 의 ② 병이고, 도구는 판 ㊶ 에 이미 만들어 놓고 여기서 안 불렀다.**
            #
            # ⚠ **호출 수가 «문서 × 절» 이다.** 남은 예산을 그대로 문서 상한으로 넘기면
            #   절 수배로 초과한다(실측 112문서 × 4절 = 448회). 그래서 절 수로 **나눠서** 준다.
            # ⚠ 실패해도 `read` 산출은 그대로 쓴다 — **더 얻으려다 있는 것을 잃지 않는다.**
            절수 = len(REASK.DEFAULT_SECTIONS)
            남은 = budget.remaining() - _SUMMARY_RESERVE - _SYNTH_CALLS
            문서상한 = max(0, 남은 // 절수)
            if 문서상한 <= 0:
                ledger.degrade("sections", "REASK_SKIPPED",
                               f"남은 예산 {남은} 으로는 절 {절수}개를 한 문서도 못 묻는다")
            else:
                try:
                    # ★ **읽기가 고친 본문을 그대로 물려준다** (판 ㊺).
                    #   `_refetch_pdfs` 는 메모리 안에서만 고치므로, 안 넘기면 재질문이
                    #   `_corpus` 를 새로 불러 **옛 2단 조판 본문을 다시 읽는다.**
                    #   실측 규모: 읽기 94회는 새 본문인데 **재질문 376회가 옛 본문** — 지출의 80%다.
                    #   게다가 재질문이 겨냥하는 넷이 **정부 PDF 비중이 가장 높은 절**이다.
                    더, run2 = REASK.build(source_run, f"{run_id}-reask",
                                          limit=문서상한,
                                          docs=sections.get("_문서목록"))
                    부른2 = int(run2.counters.get("llm.calls", 0))
                    budget.charge(부른2)
                    stage.llm_calls += 부른2
                    sections = REASK.merge(sections, 더)
                    # ⚠ **성공을 `degrade` 로 적지 않는다** — 그것은 봉투를 타고 화면에
                    #   경고로 나간다. 이 단계가 얼마나 얻었는지는 `merge` 가 `sections.json`
                    #   의 `합침` 칸에 남기고, 호출 수는 위 `stage.llm_calls` 가 남긴다.
                except Exception as error:          # noqa: BLE001 — 더 얻는 시도다
                    # **실패는 값이다.** 조용히 넘기면 「원래 이만큼이었다」로 읽힌다.
                    ledger.degrade("sections", "REASK_FAILED", str(error)[:200])

            # ⚠ **본문 전량을 원장에 쓰지 않는다.** `_문서목록` 은 재질문에 물려주려고
            #   메모리로만 나르는 것이라, 그대로 쓰면 `sections.json` 이 수 MB 로 불어난다.
            sections.pop("_문서목록", None)
            io.open(os.path.join(runpath.write_dir(source_run), "sections.json"),
                    "w", encoding="utf-8").write(
                json.dumps(sections, ensure_ascii=False, indent=1))
        except Exception as error:                  # noqa: BLE001 — SOFT 다
            stage.status = "FAILED"
            ledger.degrade("sections", "SECTIONS_READ_FAILED", str(error)[:200])
            return 빈

    # ── 여기부터 LLM 0회 ─────────────────────────────────────
    publish = GATE.build(sections, concept)
    counts: dict = {}
    for r in publish.get("문서별") or []:
        for it in r.get("items") or []:
            # ⚠ **성적표가 세는 것은 «절 머리»뿐이다** (판 ㊹ 3단계). 서랍(`밖`)까지 세면
            #   「해외 시장 값」·「상위 범주 값」이 그 절의 성적을 올린다 —
            #   판 ㊸ 의 「원가 확인됨인데 원가 사실 0건」과 같은 병을 새 이름으로 되살리는 것이다.
            if GATE.머리인가(it):
                counts[GATE.절(it)] = counts.get(GATE.절(it), 0) + 1
    judgments = JUDGE.build(publish, concept)
    # ★ **서랍은 표본만 싣는다** (판 ㊺). 절 머리는 한 건도 안 접는다.
    #   왜 접는지는 `promote_cards.서랍_상한` 에 실측과 함께 적었다 — 요약하면
    #   봉투 상한 2MiB 를 넘기면 **실행 전체가 죽어** 이미 지불한 수집을 통째로 잃는다.
    생략: dict = {}
    카드 = PROMOTE.build(publish, 서랍상한=PROMOTE.서랍_상한, 생략=생략)

    # ★ 판 ㊺ — **절마다 「먼저 볼 것」을 고른다** (절당 LLM 1회).
    #   왜 필요한지는 `tools/pick_lead.py` 머리말에 실측과 함께 적었다 — 요약하면
    #   이 엔진에 **고르는 자리가 없어서** 줄 세우기를 「출처의 권위」가 대신했고,
    #   그래서 「출생아수 254,457명」이 「가정간편식 6조 8천억」을 이겼다.
    #   ⚠ **버리지 않는다. 순서만 바꾼다.** 실패하면 낱말표 순서를 그대로 쓴다.
    if not rescore and budget.can_afford(2):
        import pick_lead as PICK                                    # noqa: PLC0415
        try:
            카드, 부름, 고른 = PICK.apply(카드, concept, run_id=f"{run_id}-pick")
            budget.charge(부름)
            stage.llm_calls += 부름
            if 고른:
                print("먼저 볼 것 고름 — " + " · ".join(f"{k} {len(v)}건"
                                                 for k, v in sorted(고른.items())))
        except Exception as error:                  # noqa: BLE001 — SOFT 다
            ledger.degrade("sections", "PICK_FAILED", str(error)[:200])
    if 생략:
        # **몇 건 중 몇 건인지 말하지 않으면 표본이 전량인 척한다.** 경계 표시다 — 지우지 않는다.
        ledger.degrade("sections", "DRAWER_SAMPLED",
                       "참고 근거(서랍)는 절마다 "
                       f"{PROMOTE.서랍_상한}건까지만 실었어요 — "
                       + " · ".join(f"{절} {v['전체']}건 중 {v['실음']}건"
                                    for 절, v in sorted(생략.items()))
                       + ". 접힌 것도 원장에는 그대로 있어요")
    out = {"cards": 카드, "counts": counts,
           "judgment": serialize.judgment(judgments),
           "prescriptions": serialize.prescriptions(
               PRESCRIBE.build(publish, concept, judgments)),
           "synthesis": None, "report": None}

    # ⚠ **여기까지 왔으면 이 단계는 돌았다.** 이 두 줄이 없으면 초기값 `SKIPPED` 가 그대로
    #   남아, 문서 132건을 읽고 절을 다 채운 실행이 **「안 돌았다」로 보고된다**(실측).
    #   실패 경로만 `FAILED` 를 적고 성공 경로가 아무것도 안 적던 것이 원인이다.
    stage.status = "OK"
    stage.seconds = int(time.monotonic() - began)

    # ── ★ 판 ㊻ — **사람이 읽는 보고서 글**을 쓴다 (SOFT) ──────
    #   고르기 다음이 자리다. 절마다 「먼저 볼 것」이 정해진 뒤라야 글도 그 순서로 쓴다.
    out["report"] = _report(ledger, budget, stage, source_run, concept, run_id, rescore,
                            카드=카드)
    stage.seconds = int(time.monotonic() - began)

    # ── 9절만 유료다(LLM 1회). 여기가 막혀도 앞의 것은 다 살아 있다 ──
    if rescore or not budget.can_afford(1):
        ledger.degrade("sections", "SYNTHESIS_SKIPPED",
                       "9절 합성은 LLM 1회다 — 예산이 없거나 재채점 모드라 건너뛴다")
        return out
    try:
        out["synthesis"] = serialize.synthesis(
            SYNTH.build(publish, concept, judgments, run_id=f"{run_id}-synth"))
        budget.charge(1)
        stage.llm_calls += 1
    except Exception as error:                      # noqa: BLE001 — SOFT 다
        ledger.degrade("sections", "SYNTHESIS_FAILED", str(error)[:200])
    stage.seconds = int(time.monotonic() - began)
    return out


#: 보고서 글은 **재료가 있는 절마다 LLM 1회**(최대 7 — `write_report.SECTIONS`)에
#: **꼬리(8·9절) 1회**를 더한다. 이만큼 못 대면 시작하지 않는다 — 반쯤 쓰고 멈추면
#: 돈만 나가고 글은 안 남는다.
_REPORT_MIN_CALLS = 8


def _report(ledger: Run, budget: Budget, stage: Stage, source_run: str,
            concept: dict, run_id: str, rescore: bool,
            카드: list[dict] | None = None) -> dict | None:
    """**사람이 읽는 보고서 글.** 절 체인이 모은 사실 위에 절마다 문단·표를 쓴다.

    ## 왜 SOFT 인가
    글이 없어도 값·등급·경계·근거는 그대로 나간다. 화면은 `report` 가 `null` 이면
    지금까지처럼 표만 그린다 — **판을 죽일 이유가 없다.**

    ## ⚠ 호출 수를 반드시 청구한다
    `write_report.build` 는 실패해도 **쓴 호출 수를 돌려준다.** 그것을 예산과 단계에 같이
    더하지 않으면 원장이 거짓말한다 — 실측(2026-08-15): 137회를 쓰고 4회라고 보고했다.
    """
    if rescore:
        # 판정층·재채점은 LLM 0회가 규율이다.
        ledger.degrade("sections", "REPORT_SKIPPED", "재채점 모드는 LLM 을 부르지 않는다")
        return None
    if not budget.can_afford(_REPORT_MIN_CALLS + _SYNTH_CALLS):
        ledger.degrade("sections", "REPORT_SKIPPED",
                       f"남은 예산 {budget.remaining()} < 최소 소요 {_REPORT_MIN_CALLS}"
                       f" + 9절 {_SYNTH_CALLS} — 보고서 글은 건너뛰고 사실만 낸다")
        return None

    import write_report as WRITE                                    # noqa: PLC0415
    # ★ **게재를 지난 카드를 넘긴다**(판 ㊻). 안 넘기면 `write_report` 가 게재를 안 보는
    #   A1 재료로 물러서고, 그러면 §1 이 서랍·주제 밖까지 실은 132행 표가 된다(실측).
    doc, 부름, 사유 = WRITE.build(source_run, concept, run_id=f"{run_id}-report", 카드=카드)
    budget.charge(부름)
    stage.llm_calls += 부름
    if 사유:
        ledger.degrade("sections", "REPORT_FAILED", 사유[:200])
        return None
    return serialize.report(doc)


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


# ══════════════════════════════════════════════════════════════
# 수집 — harness → dryrun → collect
# ══════════════════════════════════════════════════════════════
#: 하네스 한 판의 LLM 상한. 규칙은 `harness/vocab.json.재시도` 에 있고 여기는 예산일 뿐이다.
HARNESS_CALLS = 3
#: 수집 한 판의 실측 상한. 실측 근거는 A3 슬롯별 발췌 + 교차확인이다.
COLLECT_CALLS = 80


def _collect(ledger: Run, budget: Budget, concept_path: str, run_id: str, as_of: str,
             recollect: dict | None = None) -> None:
    """**새 원장을 만든다.** 예전에 이 자리는 `SKIPPED` + `NOT_WIRED` 였다.

    세 단계를 순서대로 돌리고, **어디서 멈췄든 그 사실을 값으로 남긴다.**

        harness   슬롯·식 설계 (LLM ≤3)          — 미통과면 여기서 멈춘다
        dryrun    stat_code 가 서는지 (**무료**)  — 안 서면 유료 수집을 태우지 않는다
        collect   A1~C 수집 (LLM ≈80 · 유료)

    ⚠ **드라이런 게이트를 건너뛰지 않는다.** kosis 슬롯이 있는데 stat_code 가 하나도 안
      서면 수집은 빈손으로 끝난다 — 그 빈손이 「자료 부재」로 읽히는 것이 이 파이프라인이
      없애려는 실패 그 자체다. 무료로 알 수 있는 것을 유료로 알아내지 않는다.

    ⚠ 실패는 전부 `_Hard` 다. 원장이 없으면 판정도 카드도 거짓이 된다.
    """
    import sys as _sys                                              # noqa: PLC0415
    for _dir in (os.path.join(RESEARCH_HOME, "harness"),
                 os.path.join(RESEARCH_HOME, "adapters"),
                 os.path.join(RESEARCH_HOME, "blocks")):
        if _dir not in _sys.path:
            _sys.path.insert(0, _dir)
    import run as ENGINE                                            # noqa: PLC0415
    import slot_dryrun as DRYRUN                                    # noqa: PLC0415
    import slot_harness as HARNESS                                  # noqa: PLC0415

    # ⚠ **경로를 절대로 만든다.** 하네스는 CLI 관례대로 `data/slots_<tag>.json` 같은
    #   ROOT 상대 경로를 돌려주는데, `run.py` 는 그것을 **CWD 기준**으로 연다
    #   (`json.load(io.open(a.slots))`). CLI 는 research2 에서 돌아 맞아떨어지지만
    #   함수로 부르면 CWD 가 다르다 — 컨테이너는 `/app` 이라 **프로덕션에서도 터진다.**
    #   2026-08-11 실측: 수집이 시작하자마자 죽고 원장 디렉터리만 빈 채로 남았다.
    def _abs(path: str) -> str:
        return path if os.path.isabs(path) else os.path.join(RESEARCH_HOME, path)

    # ── harness ──────────────────────────────────────────────
    if not budget.can_afford(HARNESS_CALLS + COLLECT_CALLS):
        ledger.stage("harness")
        ledger.degrade("harness", "BUDGET_EXHAUSTED",
                       f"수집 한 판은 LLM {HARNESS_CALLS + COLLECT_CALLS}회가 필요한데 "
                       f"남은 예산은 {budget.remaining()}회다 — 완주 못 할 지출은 시작하지 않는다")
        raise _Hard("예산이 수집 한 판에 못 미친다")

    design = _timed(ledger, "harness", lambda: HARNESS.run_harness(
        HARNESS.HarnessOptions(concept=concept_path, tag=run_id, as_of=int(as_of[:4]))))
    budget.charge(HARNESS_CALLS)
    ledger.stages[-1].llm_calls = len(design.get("report", {}).get("시도_기록") or []) or 1
    if not design.get("passed"):
        # 하네스는 fail-open 이라 **값으로** 돌아온다 — 그 값을 그대로 옮긴다.
        failed = [c["name"] for c in (design.get("report", {}).get("checks") or [])
                  if not c.get("passed")]
        ledger.degrade("harness", "HARNESS_GATE_FAILED",
                       "슬롯 설계가 게이트를 못 넘었다 — 미통과 검사: " + ", ".join(failed))
        raise _Hard("하네스 게이트 미통과 — 스냅샷이 없어 수집할 슬롯이 없다")

    snapshot = {key: _abs(value) for key, value in design["snapshot"].items()}

    # ── dryrun (무료) ────────────────────────────────────────
    seen = _timed(ledger, "dryrun", lambda: DRYRUN.dryrun(
        DRYRUN.DryrunOptions(tag=run_id, slots=snapshot["slots"])))
    resolved = seen.get("stat_code_해결")
    if resolved and resolved.get("대상") and not resolved.get("해결"):
        ledger.degrade("dryrun", "STAT_CODE_UNRESOLVED",
                       f"kosis 슬롯 {resolved['대상']}개 중 stat_code 가 선 것이 0개다 — "
                       "유료 수집을 태우지 않는다. 슬롯의 subject·metric 을 고쳐야 한다")
        raise _Hard("드라이런 미통과 — stat_code 가 하나도 서지 않는다")
    if not resolved:
        # 키가 없어 대조 자체를 못 했다. **막지는 않되 조용히 넘기지 않는다.**
        ledger.degrade("dryrun", "STAT_CODE_UNCHECKED",
                       "KOSIS_API_KEY 가 없어 stat_code 실재 대조를 못 했다 — "
                       "수집이 빈손이면 자료 부재가 아니라 이 때문일 수 있다")

    # ── collect (유료) ───────────────────────────────────────
    # 재수집 지시가 있으면 **복원 + 부분 수집**이다. 새 설계에서 바뀐 슬롯만 새로 사고
    # 나머지는 원본 결과를 그대로 쓴다 — 그래야 이미 확보한 확인됨이 안 사라진다.
    # 지시가 없으면 아래 네 칸은 기본값이라 **종전 호출과 한 글자도 다르지 않다.**
    rc = recollect or {}
    _timed(ledger, "collect", lambda: ENGINE.collect(ENGINE.CollectOptions(
        id=run_id, concept=_abs(concept_path), as_of=as_of,
        slots=snapshot["slots"], formulas=snapshot["formulas"],
        from_stage=rc.get("from", ""), source_run=(run_id if rc else ""),
        collect_slots=rc.get("slots", ""), slots_from=rc.get("slotsFrom", "source"))))
    if rc:
        # **무엇을 다시 샀는지 값으로 남긴다.** 안 남기면 「왜 이 슬롯만 새 값인가」를
        # 나중에 코드로 추론해야 하고, 그건 기록이 아니다.
        ledger.degrade("collect", "RECOLLECTED",
                       f"재수집 — 슬롯 {rc.get('slots') or '(전체)'} 만 새로 샀다 "
                       f"(복원 {rc.get('from')} · 사람칸 {rc.get('slotsFrom')}). "
                       "나머지 슬롯은 원본 수집 결과를 그대로 쓴다")
    budget.charge(COLLECT_CALLS)


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
    import scorecard as SCORECARD                                  # noqa: PLC0415

    from ..validation import citation, gate, mapping               # noqa: PLC0415
    from .bm.flow import run_bm_pipeline_flow                      # noqa: PLC0415
    from .bm.normalize import create_bm_analysis_input             # noqa: PLC0415

    ledger = Run()
    material = await asyncio.to_thread(_bm_material, ledger, source_run,
                                       concept_path, concept_id,
                                       plan_material or {}, plan_constraints or {})
    cards, market_join, constraints, verdict, promoted = material

    # ⚠ **근거는 합집합, 판정은 슬롯 카드만.** 아래 `mapping.apply` 와 이 줄에만 승격 카드를
    #   넣는다 — `scorecard`(`SCORECARD.build`)와 `verdict` 는 원장을 직접 다시 읽으므로
    #   여기서 넘기는 목록과 무관하고, 그래야 슬롯 판정이 안 흔들린다(판 ㊸ 규율).
    근거_카드 = list(cards) + list(promoted or [])
    evidence = serialize.evidence(근거_카드)

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
    # ⚠ 게이트는 **직렬화 뒤**에 돈다. `canvas_cells()` 안의 `_stamp_user_plan()` 이 근거를
    #   인용하지 않은 칸을 PLAN 으로 내리므로, 그 전에 재면 사용자 계획을 관측으로 착각한다.
    # ⚠ **강등이 먼저다.** 근거 없이 「확인됨」이라고 쓴 칸을 UNVERIFIED 로 내린 뒤에
    #   직렬화한다. 순서를 바꾸면 화면이 근거 0건인 칸 옆에 「확인됨」을 그대로 단다.
    # ⚠ **매핑이 맨 앞이다.** 근거 id·출처 라벨·상태는 기계가 확정한다(LLM 0회). 이 자리가
    #   `serialize.canvas_cells()` 보다 뒤로 가면 칸의 경계가 id 에서 파생되지 않아
    #   `assert_caveats_reached`(그리고 자바 `requireCaveats`)가 결과를 통째로 거부한다.
    #   순서: mapping → citation.enforce(강등만) → _stamp_user_plan(계획칸) → gate.
    analysis, corrections = citation.enforce(
        mapping.apply(out["bm_analysis"], 근거_카드, verdict))
    if corrections:
        ledger.degrade("bm", "UNCITED_CLAIM",
                       f"근거 없이 확인을 주장한 {len(corrections)}칸을 미확인으로 내렸다: "
                       + ", ".join(f"{item.cell}({item.was})" for item in corrections))
    cells = serialize.canvas_cells(
        analysis.canvas, evidence,
        _user_planned_cells(plan_material, plan_constraints))
    # ⚠ **성적표를 BM 모드에서도 낸다**(계획서 1-2). LLM 0회다 — 저장된 원장을 다시 셀 뿐이다.
    #   이것이 없으면 게이트가 「이 칸의 근거가 애초에 수집되긴 했는가」를 모르고, 그러면
    #   사유의 갈래(`cause`)가 전부 `UNMAPPED` 이 되어 A급(재수집)과 B급(인용 누락)을
    #   구분하지 못한다. 갈래를 못 가르면 **컨셉을 고쳐 A급을 통과시키는 길**이 열린다.
    score = _timed(ledger, "scorecard",
                   lambda: SCORECARD.build(source_run, concept_path, verdict=verdict))
    scorecard = serialize.scorecard(score)
    gate_reasons = gate.evaluate(cells, scorecard)
    decision = gate.apply_decision(out["final_result"].decision.value, gate_reasons)
    return serialize.envelope(
        runId=run_id, conceptId=concept_id,
        asOf=str(result.get("reference_date") or _now()[:10]),
        generatedAt=_now(), mode="BM",
        stages=[s.as_contract() for s in ledger.stages],
        degradations=ledger.degradations,
        scorecard=scorecard, market=None,
        canvas={"cells": cells},
        # ⚠ 판정 블록도 **교정본(`analysis`)** 에서 나온다. 원본 `out["bm_analysis"]` 를
        #   넘기면 캔버스(매핑·강등 뒤)와 출처가 갈린다 — 지금은 이 함수가
        #   `market_fit_status`·`consistency_status` 만 읽어 무해하지만, 캔버스를 읽는 날
        #   조용히 두 갈래가 된다.
        bm=serialize.bm(out["final_result"], analysis, decision, gate_reasons),
        evidence=evidence, summary=None,
        notes=list(serialize.NOTES_BM),
        # BM 모드는 절 체인을 돌리지 않는다. **빼는 게 아니라 `null` 이다** —
        # 봉투를 `exact()` 한 번으로 못박으려면 칸은 늘 있어야 한다.
        judgment=None, prescriptions=None, synthesis=None)


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
    promoted = _timed(ledger, "promote", lambda: _promoted_cards(ledger, source_run,
                                                                 concept_path))
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
                                  cards_doc, concept, source_run, concept_id,
                                  promoted=promoted)

    market_join = _timed(ledger, "bm_adapter", adapt)
    # ⚠ `verdict` 를 같이 돌려준다 — `validation.mapping` 이 칸 상태를 도장에서 파생한다.
    #   반환 폭이 바뀌면 호출부(`_bm`)의 언패킹도 **같이** 고쳐야 한다.
    return (cards_doc.get("카드") or [], market_join,
            held.get("constraints") or {}, verdict, promoted)


def _promoted_cards(ledger: Run, source_run: str, concept_path: str) -> list:
    """원장에 이미 있는 `sections.json` → **승격 카드**. LLM 0회 · 네트워크 0회.

    <b>왜 여기서 다시 만드나.</b> 절 사실 승격(`promote_cards`)은 `_sections()` 안에 있고
    그것은 FULL 전용이다. 그래서 BM 걸음이 만드는 `market_join_data.evidence_list` 에는
    **슬롯 카드 15장**만 들어갔고, 모델은 그 밖의 id 를 인용할 수 없다
    (`bm/analyze.py::validate_market_evidence_ids` 가 전부 지운다). 봉투의 `evidence` 는
    `validation.runner._merge` 가 합집합으로 채워 **화면에는 143장이 보이는데**, 정작
    캔버스가 인용한 것은 0건이던 까닭이 이것이다.

    비싼 것은 문서를 읽는 일(`read_sections`)이고 그것은 이미 사서 `sections.json` 에
    저장돼 있다. 여기서 하는 것은 **저장된 것을 다시 세는 일**뿐이다.

    ⚠ **실패는 값이다.** 못 만들면 빈 목록을 돌려주고 사유를 격하로 남긴다 — 그러면 판
    ㊸ 이전과 똑같이 동작한다(되돌아갈 자리).
    """
    import runpath                                                 # noqa: PLC0415
    import promote_cards as PROMOTE                                # noqa: PLC0415
    import publish_gate as GATE                                    # noqa: PLC0415

    path = os.path.join(runpath.read_dir(source_run), "sections.json")
    if not os.path.isfile(path):
        # 옛 원장이거나 절 체인이 안 돈 실행이다. **결함이 아니다** — 그렇게 적는다.
        ledger.degrade("promote", "NO_SECTIONS",
                       "이 원장에는 절 조사 산출(sections.json)이 없다 — 슬롯 카드만 쓴다")
        return []
    try:
        sections = json.load(io.open(path, encoding="utf-8"))
        # ⚠ `concept_path` 는 research2 뿌리 기준 **상대 경로**일 수도 절대 경로일 수도
        #   있다(`_concept_path_of`). `_sections()` 가 같은 갈래를 이미 두고 있다.
        concept_full = (concept_path if os.path.isabs(concept_path)
                        else os.path.join(RESEARCH_HOME, concept_path))
        concept = json.load(io.open(concept_full, encoding="utf-8"))
        # ⚠ **봉투 경로와 «같은» 상한을 쓴다.** 한쪽만 접으면 BM 이 보는 근거와 화면이
        #   보는 근거가 갈리고, 접지 않은 쪽은 `context_length_exceeded` 로 죽는다
        #   (2026-08-15 실측: BM 이 정확히 그렇게 죽었다).
        return PROMOTE.build(GATE.build(sections, concept), concept,
                             서랍상한=PROMOTE.서랍_상한) or []
    except Exception as error:                      # noqa: BLE001 — 더 얻는 시도다
        ledger.degrade("promote", "PROMOTE_FAILED", str(error)[:200])
        return []
