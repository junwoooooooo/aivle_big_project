# -*- coding: utf-8 -*-
"""실패 어휘가 백엔드 화이트리스트 안인지 대조한다. 파일 읽기만 — import·네트워크 0.

    python ai/tests/test_failure_vocabulary.py

왜 있는가: 판 ㉝ 착수 실측 — `ai/app/research/runner.py` 가 내던 실패 사유 **9개가
`InternalAiExecutionClient.ERROR_REASONS` 에 하나도 없었고**, `MODEL_EXECUTION_FAILED` 는
**코드조차 없었다**. 그 상태에서는 실패가 나도 원인이 뭉개져 디버깅이 불가능하다.

게다가 `parseFailure` 는 `RETRYABLE_REASONS.contains(reason) != retryable` 이면
**응답 자체를 무효 처리**한다 — retryable 값이 한 칸만 어긋나도 조용히 다른 실패가 된다.

두 파일이 각자 목록을 들고 있으므로 **한쪽만 고쳐지는 것**이 이 검사가 막는 일이다.
"""
from __future__ import annotations

import io
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
JAVA = os.path.join(REPO, "backend", "src", "main", "java", "com", "aivle", "backend",
                    "taskrun", "integration", "InternalAiExecutionClient.java")
RUNNER = os.path.join(HERE, "..", "app", "research", "runner.py")


def java_vocabulary() -> tuple[dict, set]:
    """`ERROR_REASONS`(코드→사유들) 와 `RETRYABLE_REASONS` 를 소스에서 읽는다."""
    src = io.open(JAVA, encoding="utf-8").read()
    block = src[src.index("ERROR_REASONS"):src.index("RETRYABLE_REASONS")]
    reasons: dict[str, set] = {}
    for m in re.finditer(r'Map\.entry\(\s*"([A-Z_]+)"\s*,\s*Set\.of\((.*?)\)\)', block, re.S):
        reasons[m.group(1)] = set(re.findall(r'"([A-Z_]+)"', m.group(2)))
    tail = src[src.index("RETRYABLE_REASONS"):]
    retryable = set(re.findall(r'"([A-Z_]+)"', tail[:tail.index(");")]))
    return reasons, retryable


def python_vocabulary() -> dict:
    """`runner._ALLOWED` 를 소스에서 읽는다 — import 하면 무거운 의존이 딸려온다."""
    src = io.open(RUNNER, encoding="utf-8").read()
    block = src[src.index("_ALLOWED = {"):]
    block = block[:block.index("\n}")]
    out = {}
    for m in re.finditer(r'\(\s*"([A-Z_]+)"\s*,\s*"([A-Z_]+)"\s*\)\s*:\s*\((\d+),\s*(True|False)\)', block):
        out[(m.group(1), m.group(2))] = (int(m.group(3)), m.group(4) == "True")
    return out


def main() -> int:
    reasons, retryable = java_vocabulary()
    allowed = python_vocabulary()
    ok, bad = 0, []

    if not reasons or not allowed:
        print("X   목록을 못 읽었다 — 소스 모양이 바뀌었을 수 있다")
        return 1

    for (code, reason), (_status, py_retryable) in sorted(allowed.items()):
        if code not in reasons:
            bad.append(f"코드 미등록: {code}")
            print(f"  X   {code}/{reason}  ← INTERNAL_CODES 에 없다")
            continue
        if reason not in reasons[code]:
            bad.append(f"사유 미등록: {code}/{reason}")
            print(f"  X   {code}/{reason}  ← ERROR_REASONS[{code}] 에 없다")
            continue
        java_retryable = reason in retryable
        if java_retryable != py_retryable:
            bad.append(f"retryable 불일치: {reason} py={py_retryable} java={java_retryable}")
            print(f"  X   {code}/{reason}  retryable py={py_retryable} java={java_retryable}")
            continue
        ok += 1
        print(f"  OK  {code}/{reason:<36} retryable={py_retryable}")

    print(f"\n===== {ok} 통과 / {len(bad)} 실패")
    for b in bad:
        print(" 실패:", b)
    if bad:
        print("\n⚠ 화이트리스트 밖 사유는 원인을 잃는다. retryable 불일치는 응답을 통째로 무효화한다.")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
