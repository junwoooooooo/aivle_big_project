# -*- coding: utf-8 -*-
"""원장이 어디 있는가 — **한 곳에서만 답한다.**

원장 자리가 **둘**이다. 하나로 둘 수 없었던 이유가 있다.

    runs/            씨앗 원장. 컨테이너에서 **읽기 전용**으로 붙는다.
                     232MB 짜리 측정 기록이고 저장소에 없다(견본 3개만 예외).
                     `:ro` 는 「컨테이너가 측정 원장을 덮어쓸 수 없다」는 규율이다.
    runs-generated/  **수집이 만드는 새 원장.** 쓰기 가능해야 한다.

그래서 규칙은 하나다 — **읽기는 둘 다, 쓰기는 뒤에만.**

⚠ **왜 새 파일인가.** 원래 답은 `runlog.RUNS_DIR` 하나였는데, 원장을 읽는 쪽
   (`service/bm_scorer.py`·`service/bm_layer.py`)은 **유리벽 안이라 `runlog` 를 import
   할 수 없다**(`tests/test_verdict_canvas.py:52` 가 이름으로 막는다). 그래서 답을
   `os` 만 쓰는 **잎 모듈**로 내린다. 벽이 막는 것은 엔진 «계산»이지 경로 «상수»가 아니다 —
   이 파일은 아무것도 계산하지 않고 아무것도 읽지 않는다.

⚠ **읽기에서 `runs-generated/` 를 먼저 본다.** 같은 이름이 양쪽에 있으면 새 것이 이긴다.
   반대로 두면 사용자가 방금 만든 원장이 옛 씨앗에 조용히 가려진다.
"""
from __future__ import annotations

import os
import time

HERE = os.path.dirname(os.path.abspath(__file__))

#: 씨앗 원장. 컨테이너에서는 `:ro` 바인드다.
#: ⚠ 기본값을 바꾸지 않는다 — 검사들이 이 자리를 전제한다.
RUNS_DIR = os.environ.get("RESEARCH2_RUNS_DIR") or os.path.join(HERE, "runs")

#: 수집이 만드는 새 원장. **여기만 쓴다.**
GENERATED_RUNS_DIR = (os.environ.get("RESEARCH2_GENERATED_RUNS_DIR")
                      or os.path.join(HERE, "runs-generated"))

#: 읽을 때 보는 순서. 새 것이 먼저다.
SEARCH_ORDER = (GENERATED_RUNS_DIR, RUNS_DIR)


def _safe(run_id: str) -> str:
    """경로가 될 수 있는 글자만. `..` 이나 구분자가 들어오면 **여기서 멈춘다.**

    `pipeline._SAFE_RUN_ID` 가 이미 같은 일을 하지만, 이 파일을 부르는 길이 여럿이라
    한 곳이 빠뜨려도 경로가 새지 않게 둔다.
    """
    name = (run_id or "").strip()
    if not name or name in (".", "..") or os.path.sep in name or "/" in name or "\\" in name:
        raise ValueError(f"원장 이름으로 쓸 수 없다: {run_id!r}")
    return name


def find(run_id: str) -> str | None:
    """**읽기.** 있는 자리를 돌려준다. 어디에도 없으면 `None`."""
    name = _safe(run_id)
    for base in SEARCH_ORDER:
        candidate = os.path.join(base, name)
        if os.path.isdir(candidate):
            return candidate
    return None


def read_dir(run_id: str) -> str:
    """**읽기.** 없으면 `GENERATED_RUNS_DIR` 쪽 경로를 돌려준다.

    없는 것을 없다고 말하는 일은 부르는 쪽이 한다 — 여기서 예외를 던지면
    「없으면 이렇게」를 각자 다시 짜야 해서 답이 다시 흩어진다.
    """
    return find(run_id) or os.path.join(GENERATED_RUNS_DIR, _safe(run_id))


def exists(run_id: str) -> bool:
    return find(run_id) is not None


def complete(run_id: str) -> bool:
    """**재채점할 수 있는 원장인가.** 디렉터리가 아니라 `result.json` 을 본다.

    ⚠ `exists()` 로는 안 되는 이유(2026-08-12 실측). 제품 경로에서 수집이 7분째 돌던 중
    컨테이너가 새로 만들어져 실행이 죽었고, `run.jsonl`(52KB)만 남고 `result.json` 은
    안 남았다. 그 다음 실행은 `exists()` 가 True 라 **수집을 건너뛰고** 그 반쪽 원장을
    읽으려다 `FIELD_CONSTRAINT_VIOLATION` 으로 죽었다 — **그 사업안은 영영 수집을 못 하는
    상태로 굳었다.** 「원장이 있다」와 「원장을 읽을 수 있다」는 다른 물음이다.

    ⚠ `exists()` 를 고치지 않는다. `runner.py` 의 「runId 재사용」 방어는 **디렉터리**를
    물어야 한다 — `run.jsonl` 이 append-only 라 반쪽이든 아니든 겹치면 지표가 배로 보인다.
    """
    found = find(run_id)
    return found is not None and os.path.isfile(os.path.join(found, "result.json"))


def quarantine_partial(run_id: str) -> str | None:
    """반쪽 원장을 **비켜 놓는다.** 지우지 않는다 — 죽은 실행도 측정 기록이다.

    수집이 같은 디렉터리에 이어 쓰면 `run.jsonl` 이 append-only 라 **지표가 배로 보인다**
    (CLAUDE.md 의 「같은 --id 재실행 금지」가 그 말이다). 그래서 새 수집 전에 옮긴다.

    @return 옮긴 새 이름. 옮길 것이 없었으면 `None`.
    """
    found = find(run_id)
    if found is None or os.path.isfile(os.path.join(found, "result.json")):
        return None
    stamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime())
    moved = f"{found}.partial-{stamp}"
    os.rename(found, moved)
    return os.path.basename(moved)


def harness_bases() -> list[str]:
    """하네스 산출물이 있을 수 있는 자리들. 읽는 쪽은 **둘 다** 훑는다."""
    return [os.path.join(base, "harness") for base in SEARCH_ORDER]


def harness_read_dir(tag: str) -> str:
    """하네스 초안 **읽기.** 없으면 쓰기 자리의 경로를 돌려준다."""
    name = _safe(tag)
    for base in harness_bases():
        candidate = os.path.join(base, name)
        if os.path.isdir(candidate):
            return candidate
    return harness_write_dir(name)


def harness_write_dir(tag: str) -> str:
    """하네스 초안 **쓰기.** 원장과 같은 이유로 `runs-generated/` 다 — 씨앗 쪽은 `:ro` 라
    컨테이너에서 하네스가 그 자리에서 죽는다."""
    return os.path.join(GENERATED_RUNS_DIR, "harness", _safe(tag))


def write_dir(run_id: str) -> str:
    """**쓰기.** 항상 `runs-generated/` 다.

    ⚠ 씨앗 원장과 **같은 이름으로 쓰는 것을 막지 않는다.** 막을 수 없기 때문이다 —
      `:ro` 는 컨테이너 밖에서는 걸려 있지 않아서, 로컬 연구 세션이 같은 이름을 쓰면
      옛 원장이 읽기에서 가려진다. 이름은 부르는 쪽이 `selectionId` 처럼 겹치지 않는
      값으로 정한다.
    """
    return os.path.join(GENERATED_RUNS_DIR, _safe(run_id))
