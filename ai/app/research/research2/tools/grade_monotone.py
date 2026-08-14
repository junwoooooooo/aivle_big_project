# -*- coding: utf-8 -*-
"""등급 단조성 — **낮은 등급의 높은 표기 0** (판 ㉙ S3). LLM 0회 · 원장 쓰기 0회.

    python tools/grade_monotone.py --run pet-treat-14 --concept data/concept_pet-treat.json

`boundary_roundtrip.py` 의 자매다. 그쪽은 **경계가 최종 매체까지 도달했는가**를 보고,
이쪽은 **도달한 값의 등급이 재료보다 높지 않은가**를 본다. 둘 다 「쓴 곳」이 아니라
「도달한 곳」에서 잰다 — 판 ㉘ 이 값비싸게 배운 것이다.

두 겹으로 잰다:

  **M1 행 단조** — 한 줄의 표기 등급은 `등급표[kind]` 를 넘을 수 없다.
      넘으려면 상향 근거(독립 화자 ≥2)가 **값으로** 있어야 한다. 없으면 실패(fail-closed).
  **M2 칸 단조** — 계산값(TAM·성장률·밴드)의 표기 등급은 **재료 중 가장 낮은 등급**을
      넘을 수 없다. **약한 고리가 등급을 정한다.** gov_stat 거래액 × 가정 점유율의
      결과가 「확정」으로 표기되면 그것이 곧 금지선 위반이다.

⚠ 이 도구는 **아무것도 고치지 않는다.** 세고 실패시킬 뿐이다.
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

#: 낮은 쪽이 앞. 비교는 이 순서로만 한다.
LADDER = ["근거 없음", "추정", "실무 신뢰", "확정"]


def rank(g: str) -> int:
    return LADDER.index(g) if g in LADDER else 0


def _fill() -> dict:
    pins = json.load(io.open(os.path.join(ROOT, "rules", "rule_pins.json"), encoding="utf-8"))
    return json.load(io.open(os.path.join(ROOT, "rules", pins["pins"]["fill"]), encoding="utf-8"))


def grade_of_kind(kind: str, fill: dict) -> str:
    표 = fill["등급표"]
    for lv in ("확정", "실무 신뢰", "추정"):
        if kind in (표.get(lv) or []):
            return lv
    return 표.get("_기본") or "추정"


def load_canvas(run: str, concept: str) -> dict:
    out = subprocess.run([sys.executable, "-m", "service.canvas", run,
                          "--concept", concept, "--json"],
                         cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        raise SystemExit(f"canvas 생성 실패:\n{out.stderr[-1500:]}")
    return json.loads(out.stdout)


def ledger_rows(run: str) -> dict:
    rows = {}
    p = os.path.join(ROOT, "runs", run, "run.jsonl")
    for ln in io.open(p, encoding="utf-8"):
        o = json.loads(ln)
        if o.get("node") == "a4_ledger":
            pl = o["payload"]
            for r in (pl.get("rows") or ([pl] if "label" in pl else [])):
                rows[r["fact_id"]] = r
    return rows


def walk(o, path=""):
    """칸 안의 모든 dict 를 경로와 함께 흘려보낸다."""
    if isinstance(o, dict):
        yield path, o
        for k, v in o.items():
            yield from walk(v, f"{path}/{k}")
    elif isinstance(o, list):
        for i, v in enumerate(o):
            yield from walk(v, f"{path}[{i}]")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--concept", required=True)
    a = ap.parse_args()

    fill, cv, led = _fill(), load_canvas(a.run, a.concept), ledger_rows(a.run)
    up = fill.get("등급_상향") or {}
    need = int(up.get("독립_화자_최소") or 2)

    fails, m1, m2 = [], 0, 0

    # ── M1 행 단조 ────────────────────────────────────────────
    for path, node in walk(cv.get("칸") or {}):
        if not (isinstance(node, dict) and node.get("등급") and node.get("kind")):
            continue
        m1 += 1
        ceiling = grade_of_kind(node["kind"], fill)
        if rank(node["등급"]) <= rank(ceiling):
            continue
        row = led.get(node.get("fact_id")) or {}
        if row.get("cross", 0) >= need and rank(node["등급"]) <= rank(ceiling) + 1:
            continue                      # 상향 근거가 값으로 있다
        fails.append(f"M1 {path} — 표기 '{node['등급']}' > 재료 '{ceiling}' "
                     f"(kind={node['kind']} fact={node.get('fact_id')} "
                     f"cross={row.get('cross')})")

    # ── M2 칸 단조 — 계산값은 **약한 고리**를 따른다 ─────────────
    for path, node in walk(cv.get("칸") or {}):
        if not isinstance(node, dict):
            continue
        표기 = node.get("등급") or node.get("표기_등급")
        근거 = node.get("근거")
        if not (표기 and isinstance(근거, list) and 근거):
            continue
        재료 = [grade_of_kind(g.get("kind") or "", fill) for g in 근거 if isinstance(g, dict)]
        # 가정이 섞이면 그 자체가 가장 약한 고리다 — 관측이 아니다.
        if node.get("assumption_count") or node.get("가정"):
            재료.append("추정")
        if not 재료:
            continue
        m2 += 1
        약한 = min(재료, key=rank)
        if rank(표기) > rank(약한):
            fails.append(f"M2 {path} — 표기 '{표기}' > 약한 고리 '{약한}' (재료 {재료})")

    print(f"[{a.run}] M1 행 {m1}개 · M2 계산칸 {m2}개 검사")
    if m2 == 0:
        # **조용한 통과를 만들지 않는다.** 검사 대상이 0인 것과 위반이 0인 것은 다르다 —
        # 판 ㉙ 착수 시점에 계산값(TAM·성장률·밴드)에는 **등급 표기 자체가 없었다.**
        # 「약한 고리가 등급을 정한다」는 문서에는 있었지만 **값으로는 없던 것**이고,
        # 그 상태에서 M2 가 「통과」라고만 말하면 판 ㉘ 의 경계 소실과 같은 모양이 된다.
        print("  ⚠ M2 검사 대상 0 — 계산값에 등급 표기가 **없다**. "
              "위반이 없는 것이 아니라 **잴 것이 없는 것**이다(판 ㉙ S3 실측).")
    if fails:
        print("실패:")
        for f in fails:
            print("  X  " + f)
        return 1
    print("통과 — 낮은 등급의 높은 표기 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
