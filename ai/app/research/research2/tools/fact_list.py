# -*- coding: utf-8 -*-
"""원장이 담은 **사실을 전부 늘어놓는다.** LLM 0회 · 읽기 전용 · 계산 0회.

    python tools/fact_list.py p39-readA               # 사람이 읽는 표
    python tools/fact_list.py p39-readA --json        # 기계용

**왜 필요한가** — 판 ㊳ 3단계의 주지표가 **재현율**이다(`expected.md` 부록 AP).
손으로 108건을 읽어 만든 보고서의 사실 58건 중 몇 건을 원장이 담았는지 세려면
원장 쪽 목록이 먼저 있어야 한다. 성적표(`scorecard.py`)는 **과목이 찼는가**를 보지
**무슨 사실이 들어왔는가**를 보지 않아 이 물음에 답하지 못한다.

⚠ 이 파일은 아무것도 판정하지 않는다. 세는 것은 사람이다 — 자동으로 매칭하면
「비슷한 숫자」를 같은 사실로 접어 재현율이 조용히 부풀어 오른다.
"""
from __future__ import annotations

import argparse, io, json, os, sys
from urllib.parse import urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

import runpath


def _rows(run_id: str) -> list:
    """`a4_facts`(값) 와 `a4_ledger`(라벨·등급·채택) 를 fact_id 로 잇는다."""
    base = runpath.find(run_id)
    facts, led = {}, {}
    with io.open(os.path.join(base, "run.jsonl"), encoding="utf-8") as fh:
        for line in fh:
            try:
                r = json.loads(line)
            except Exception:
                continue
            p = r.get("payload") or {}
            fid = p.get("fact_id")
            if not fid:
                continue
            if r.get("node") == "a4_facts":
                facts[fid] = p
            elif r.get("node") == "a4_ledger":
                led[fid] = p
    out = []
    for fid, f in facts.items():
        g = led.get(fid) or {}
        out.append({
            "fact_id": fid,
            "slot_id": f.get("slot_id") or "",
            "value": f.get("value_num"),
            "unit": f.get("unit_norm") or "",
            "year": f.get("year"),
            "등급": g.get("등급") or "",
            "라벨": g.get("label") or "",
            "채택": bool(g.get("채택")),
            "출처": (urlparse(f.get("url") or "").netloc or ""),
            "인용": (f.get("quote") or "").replace("\n", " ")[:70],
            "url": f.get("url") or "",
        })
    return sorted(out, key=lambda r: (r["slot_id"], -(r["value"] or 0)))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()

    rows = _rows(a.run_id)
    if a.json:
        print(json.dumps(rows, ensure_ascii=False, indent=1))
        return 0

    print(f"\n원장 {a.run_id} — 사실 {len(rows)}건 "
          f"(채택 {sum(1 for r in rows if r['채택'])}건)\n")
    print(f"{'슬롯':<5}{'값':>22} {'단위':<6}{'연도':<6}{'등급':<7}{'채':<3}{'출처':<24}인용")
    print("-" * 118)
    for r in rows:
        v = f"{r['value']:,.0f}" if isinstance(r["value"], (int, float)) else "—"
        print(f"{r['slot_id']:<5}{v:>22} {r['unit']:<6}{str(r['year'] or '—'):<6}"
              f"{r['등급']:<7}{'o' if r['채택'] else 'x':<3}{r['출처'][:23]:<24}{r['인용']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
