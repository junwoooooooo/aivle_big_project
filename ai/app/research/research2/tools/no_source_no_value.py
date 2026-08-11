# -*- coding: utf-8 -*-
"""출처 없는 값 0 — **관측 0 에서 값이 태어나지 않았는가** (판 ㉙ S3). LLM 0회 · 원장 쓰기 0회.

    python tools/no_source_no_value.py --run pet-treat-14 --concept data/concept_pet-treat.json

금지선 제1항이 「관측 0 에서 값 생성 금지」다. 그 금지선은 지금까지 **산문으로만** 있었다 —
`canvas.audit()` 6검사는 조립을 보고, `boundary_roundtrip` 은 경계를 보고, 아무도
「이 숫자가 어디서 왔는가」를 **전수로** 되짚지 않았다.

규칙: **값이 있는 모든 칸은 `fact_id ≥ 1` 또는 `가정 ≥ 1` 로 되짚어져야 한다.**
둘 다 없으면 그 숫자는 **어디서도 오지 않았다** — 그것이 지어내기다.

⚠ **가정도 출처다.** 단 「가정에서 왔다」고 **적혀 있을 때만** 그렇다. 적히지 않은 가정은
   출처가 아니라 그냥 없는 것이다(§5 「경계 표시를 지우지 않는다」의 이 층 적용).
"""
from __future__ import annotations

import argparse
import io
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

#: 값으로 세는 키. 이 키에 숫자가 있으면 되짚기 대상이다.
VALUE_KEYS = ("값", "value", "값_퍼센트", "TAM", "SAM", "SOM")
#: 되짚기 증거로 인정하는 키.
SOURCE_KEYS = ("근거", "fact_id", "evidence_ids", "출처")
ASSUM_KEYS = ("가정", "assumption_count", "원천")


def load_canvas(run: str, concept: str) -> dict:
    out = subprocess.run([sys.executable, "-m", "service.canvas", run,
                          "--concept", concept, "--json"],
                         cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        raise SystemExit(f"canvas 생성 실패:\n{out.stderr[-1500:]}")
    return json.loads(out.stdout)


def walk(o, path=""):
    if isinstance(o, dict):
        yield path, o
        for k, v in o.items():
            yield from walk(v, f"{path}/{k}")
    elif isinstance(o, list):
        for i, v in enumerate(o):
            yield from walk(v, f"{path}[{i}]")


def traceable(node: dict) -> tuple[bool, str]:
    for k in SOURCE_KEYS:
        v = node.get(k)
        if (isinstance(v, list) and v) or (isinstance(v, str) and v):
            return True, f"{k}={len(v) if isinstance(v, list) else v}"
    for k in ASSUM_KEYS:
        v = node.get(k)
        if (isinstance(v, list) and v) or (isinstance(v, int) and v) or (isinstance(v, str) and v):
            return True, f"{k}={v if not isinstance(v, list) else len(v)}"
    return False, ""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--concept", required=True)
    a = ap.parse_args()

    cv = load_canvas(a.run, a.concept)
    checked, fails, ok_rows = 0, [], []
    for path, node in walk(cv.get("칸") or {}):
        if not isinstance(node, dict):
            continue
        vals = [k for k in VALUE_KEYS
                if isinstance(node.get(k), (int, float)) and node.get(k) is not None]
        if not vals:
            continue
        checked += 1
        ok, why = traceable(node)
        if ok:
            ok_rows.append(f"{path} ({', '.join(vals)}) ← {why}")
        else:
            fails.append(f"{path} — 값 {[(k, node[k]) for k in vals]} 인데 "
                         f"근거도 가정도 없다. **이 숫자는 어디서도 오지 않았다**")

    print(f"[{a.run}] 값 있는 칸 {checked}개 되짚기")
    for r in ok_rows:
        print("  OK  " + r)
    if fails:
        print("실패:")
        for f in fails:
            print("  X  " + f)
        return 1
    print("통과 — 출처 없는 값 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
