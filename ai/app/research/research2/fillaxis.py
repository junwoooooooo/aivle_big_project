# -*- coding: utf-8 -*-
"""채움 축 토글 — **어느 축으로 채우는가를 한 곳에서 정한다** (판 ㉙ S6). LLM 0회.

기준 v2 는 채움과 등급을 **직교 두 축**으로 갈랐다:

    옛 축   `label == "확인됨"`   — 점수 5점. 채움과 등급을 겸했다
    새 축   `채택 == True`        — 4요건(관측·url·조회일·인용대조). 등급은 따로 표기한다

전환은 **한꺼번에 하지 않는다.** 소비자마다 `rules/fill.v2.json 소비자_배선` 에 축을 적고
하나씩 옮기며 변경별로 잰다(「계측된 개정」 규약 ③). 이 모듈이 그 토글을 읽는 **유일한 곳**이다.

⚠ **유리벽 밖(service/·harness/)에서도 import 할 수 있어야 한다.** 그래서 이 파일은
  엔진 모듈을 하나도 import 하지 않는 **잎 모듈**이다(`pdf_text.py` 와 같은 자리).
  같은 물음을 엔진과 서비스가 각자 풀면 두 번 갈라진다 — 실측 6회.
"""
from __future__ import annotations

import io
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
RULES = os.path.join(HERE, "rules")

_CACHE: dict = {}


def load_fill() -> dict:
    if "fill" not in _CACHE:
        pins = json.load(io.open(os.path.join(RULES, "rule_pins.json"), encoding="utf-8"))
        _CACHE["fill"] = json.load(
            io.open(os.path.join(RULES, pins["pins"]["fill"]), encoding="utf-8"))
    return _CACHE["fill"]


def axis(consumer: str) -> str:
    """그 소비자가 읽을 축. **미등록이면 멈춘다** — 조용히 기본값을 고르지 않는다.

    미등록을 「옛 축」으로 봐주면 새 소비자가 등록 없이 태어나고, 그러면
    `tools/grade_audit.py` 가 세는 「남은 전환 대상」이 실제보다 적게 보인다.
    """
    배선 = (load_fill().get("소비자_배선") or {}).get("배선") or {}
    if consumer not in 배선:
        raise KeyError(f"fill.v2.소비자_배선 에 '{consumer}' 가 없다 — "
                       f"축을 적기 전에는 이 소비자를 돌리지 않는다")
    return 배선[consumer]


def _get(row, key):
    """dict(서비스 층)과 dataclass(엔진 층)를 같은 방식으로 읽는다."""
    return row.get(key) if isinstance(row, dict) else getattr(row, key, None)


def filled(row, consumer: str) -> bool:
    """이 줄이 그 소비자에게 **채움 재료인가**.

    ⚠ 등급을 묻는 것이 아니다. 「확실한가」가 아니라 「채워도 되는가」다.
    """
    a = axis(consumer)
    if a == "비판정":
        raise ValueError(f"{consumer} 는 비판정 자리다 — 채움 판정에 쓰지 않는다")
    if a == "새":
        return bool(_get(row, "채택"))
    return _get(row, "label") == "확인됨"


def both(row) -> tuple[bool, bool]:
    """(옛, 새) 를 같이 돌려준다 — before/after 표를 만드는 자리에서 쓴다."""
    return _get(row, "label") == "확인됨", bool(_get(row, "채택"))
