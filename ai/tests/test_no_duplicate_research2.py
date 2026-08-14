# -*- coding: utf-8 -*-
"""엔진 사본이 둘이 되는 것을 막는다 (판 ㉝ 이식). 파일 검사만 — import·네트워크 0.

    python ai/tests/test_no_duplicate_research2.py

왜 있는가: `시장조사/research2` 를 `ai/app/research/research2` 로 **옮겼다**(복사가 아니라).
이동은 사본을 안 만들지만, 다음 사람이 「연구용으로 하나 더」 하고 복사하는 것은 막지 못한다.

이 프로젝트는 **같은 물음을 두 곳이 각자 풀어** 갈라진 사고를 여러 번 겪었다 —
CLAUDE.md 사본 · 규칙 핀 분열(v5 vs v8) · 라우팅 분열 · 별칭 분열 · 표_계열 분열 · 프롬프트-게이트 분열.
그때마다 **한쪽만 고쳐지고 어느 쪽이 참인지 알 수 없게** 됐다.

그래서 「사본 금지」를 산문이 아니라 **검사**로 둔다.
"""
from __future__ import annotations

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))          # …/main

#: 엔진의 지문. 이 이름이 저장소에 둘 이상이면 사본이 생긴 것이다.
#: (이름이 흔하지 않고, 각 층에서 하나씩 골랐다)
FINGERPRINTS = [
    "slot_harness.py",      # 하네스
    "bm_adapter.py",        # 서비스 층
    "a_desk.py",            # 엔진 블록
    "fillaxis.py",          # 잎 모듈
    "runlog.py",            # 계측
]

#: 세지 않는 곳. 산출물·캐시·의존성.
SKIP = {"__pycache__", ".git", "node_modules", "runs", "outputs",
        ".venv", "venv", "build", "dist", ".gradle", "target"}

EXPECTED_HOME = os.path.join("ai", "app", "research", "research2")


def main() -> int:
    found: dict[str, list[str]] = {n: [] for n in FINGERPRINTS}
    for root, dirs, files in os.walk(REPO):
        dirs[:] = [d for d in dirs if d not in SKIP]
        for fn in files:
            if fn in found:
                found[fn].append(os.path.relpath(os.path.join(root, fn), REPO))

    ok, bad = 0, []
    for name, paths in found.items():
        if len(paths) == 1:
            ok += 1
            print(f"  OK  {name:<18} 1개  {paths[0]}")
        else:
            bad.append(f"{name}: {len(paths)}개 — {paths}")
            print(f"  X   {name:<18} {len(paths)}개  {paths}")

    # 있어야 할 자리에 있는가 (0개여도 실패다 — 지워졌거나 이름이 바뀐 것)
    home = os.path.join(REPO, EXPECTED_HOME)
    if os.path.isdir(home):
        ok += 1
        print(f"  OK  엔진 위치        {EXPECTED_HOME}")
    else:
        bad.append(f"엔진이 {EXPECTED_HOME} 에 없다")
        print(f"  X   엔진 위치        {EXPECTED_HOME} 없음")

    # 옛 자리에 코드가 다시 생기지 않았는가
    old = os.path.join(REPO, "시장조사", "research2")
    if not os.path.exists(old):
        ok += 1
        print("  OK  옛 자리 비어 있음  시장조사/research2")
    else:
        bad.append("시장조사/research2 가 다시 생겼다 — 사본이다")
        print("  X   옛 자리에 research2 가 있다")

    print(f"\n===== {ok} 통과 / {len(bad)} 실패")
    for b in bad:
        print(" 실패:", b)
    if bad:
        print("\n⚠ 엔진 사본이 둘이 되면 한쪽만 고쳐지고 어느 쪽이 참인지 알 수 없게 된다.")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
