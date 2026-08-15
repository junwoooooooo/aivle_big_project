# -*- coding: utf-8 -*-
"""57 진단 — **같은 조건 반복에서 무엇이 흔들리는가** (판 ⑪ ⑤).

    python tools/harness_variance.py --concept data/concept_pet-treat.json \
        --tag p11-var --runs 3 --token-budget 70000

**조건을 하나도 바꾸지 않는다.** 같은 컨셉·같은 어휘·같은 모델로 반복해서, 시도마다
어느 검사가 죽는지를 표로 쌓는다. 판 ⑩ 실측에서 `p10` 은 `var_role↔계량 종류` 로,
`p10b` 는 `템플릿 필수 자리` 로 죽었다 — **실패가 자리를 옮긴다**는 것이 관측이었고,
그것이 「하네스 불안정」(백로그 57)의 내용이다.

예산은 **사이클 수와 토큰 둘 다** 본다. 먼저 닿는 쪽에서 멈춘다 — 예산 도달은
**미달이 아니라 사전등록된 종료 조건**이다(부록 M).

산출: `<runs-generated>/harness/<tag>-분산.json` — 판정하지 않고 보이는 것을 적는다.

⚠ **원장 자리를 손으로 적지 않는다** (판 ㉜ 수리). 이 도구는 `runs/harness/...` 를
   **리터럴로** 들고 있었는데, 그동안 하네스 산출이 `runs-generated/harness/` 로 옮겨갔다
   (씨앗 `runs/` 는 컨테이너에서 `:ro` 라 하네스가 그 자리에서 죽는다).
   그래서 `gate.json` 을 **한 번도 못 찾고** 반복마다 「gate.json 없음」만 쌓았을 것이다 —
   유료 설계를 3회 돌리고 표는 비는 모양이다. 자리를 묻는 답은 `runpath` 하나뿐이다.
"""
from __future__ import annotations

import argparse
import collections
import io
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
import runpath                                                       # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--concept", required=True)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--runs", type=int, default=3)
    ap.add_argument("--token-budget", type=int, default=70000)
    ap.add_argument("--as-of", default="2026")
    a = ap.parse_args()

    env = dict(os.environ)
    env["PYTHONIOENCODING"] = "utf-8"
    rows, tokens, cycles, stopped = [], 0, 0, ""

    for i in range(1, a.runs + 1):
        tag = f"{a.tag}-{i}"
        print(f"\n=== 반복 {i}/{a.runs} · 누적 토큰 {tokens:,} / {a.token_budget:,} ===",
              flush=True)
        subprocess.run(
            [sys.executable, os.path.join(ROOT, "harness", "slot_harness.py"),
             "--concept", os.path.join(ROOT, a.concept), "--tag", tag, "--as-of", a.as_of],
            cwd=ROOT, env=env)
        gp = os.path.join(runpath.harness_read_dir(tag), "gate.json")
        if not os.path.exists(gp):
            rows.append({"반복": i, "오류": "gate.json 없음"})
            continue
        g = json.load(io.open(gp, encoding="utf-8"))
        for att in g.get("시도_기록") or []:
            u = att.get("usage") or {}
            t = (u.get("tokens_in") or 0) + (u.get("tokens_out") or 0)
            tokens += t
            cycles += 1
            rows.append({
                "반복": i, "시도": att["시도"], "토큰": t, "통과": att["통과"],
                "미통과": sorted(k for k, v in (att.get("요약") or {}).items() if v != "통과"),
            })
        # 예산은 **반복 사이에서만** 본다 — 실행 도중에 끊으면 그 실행이 반쪽으로 남는다.
        if cycles >= 9 or tokens >= a.token_budget:
            stopped = f"예산 도달 — 사이클 {cycles}/9 · 토큰 {tokens:,}/{a.token_budget:,}"
            print(f"\n{stopped}", flush=True)
            break

    # ── 분산 표 — 어느 검사가 몇 번 죽었나 ────────────────────
    cnt = collections.Counter(c for r in rows for c in r.get("미통과", []))
    n_att = sum(1 for r in rows if "시도" in r)
    out = {
        "_규칙": "판정하지 않고 보이는 것을 적는다. 같은 조건 반복 · 변경 0.",
        "tag": a.tag, "concept": os.path.basename(a.concept),
        "반복_수": len({r["반복"] for r in rows}), "시도_수": n_att,
        "토큰_합": tokens, "예산": a.token_budget, "종료": stopped or "반복 소진",
        "전체_통과_시도": sum(1 for r in rows if r.get("통과")),
        "검사별_미통과_횟수": dict(cnt.most_common()),
        "검사별_미통과_비율": {k: round(v / n_att, 3) for k, v in cnt.most_common()} if n_att else {},
        "시도별": rows,
    }
    # 쓰기는 항상 `runs-generated/` 다 — 씨앗 `runs/` 는 `:ro` 라 여기서 죽는다.
    out_dir = os.path.join(runpath.GENERATED_RUNS_DIR, "harness")
    os.makedirs(out_dir, exist_ok=True)
    p = os.path.join(out_dir, f"{a.tag}-분산.json")
    io.open(p, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))
    print(f"\n기록: {p}")
    print(f"시도 {n_att} · 전체 통과 {out['전체_통과_시도']} · 토큰 {tokens:,}")
    for k, v in cnt.most_common():
        print(f"  {v}/{n_att}  {k}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
