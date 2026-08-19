# -*- coding: utf-8 -*-
"""**체크리스트의 사실이 원장 코퍼스 안에 글자로 실재하는가.** LLM 0회 · 읽기 전용.

    python tools/corpus_probe.py 0c54ffb5-...  --probes data/recall_probes.json

**왜 필요한가** — 재현율이 낮을 때 원인이 둘인데 처방이 정반대다.

    ① 원장에 글자가 **있는데** 못 건졌다  → 고칠 곳은 **읽기**(발췌 프롬프트·상한)
    ② 원장에 글자가 **없다**              → 고칠 곳은 **검색**. 읽기를 아무리 고쳐도 못 낸다

②는 그 판 재현율의 **천장**이다. 천장을 모른 채 읽기를 고치면 「잘하고도 실패 판정」이 난다.

⚠ **「문서에 있다」는 「사실로 건졌다」가 아니다.** 이 도구는 발췌·심사를 통과했는지
모른다. 천장을 재는 것이지 성적을 매기는 것이 아니다.
⚠ 짧은 수(`13.5`)는 우연히 걸린다. 그런 항목은 `힘: weak` 로 표시되고 **따로 센다.**
"""
from __future__ import annotations

import argparse, io, json, os, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

import runpath


def _bodies(run_id: str) -> dict:
    """trace_id → 본문 문자열. 원시 JSON 응답도 문자열로 편다(API 경로 문서)."""
    p = os.path.join(runpath.find(run_id), "a3_bodies.json")
    raw = json.load(io.open(p, encoding="utf-8"))
    return {k: (v if isinstance(v, str) else json.dumps(v, ensure_ascii=False))
            for k, v in raw.items()}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id")
    ap.add_argument("--probes", default=os.path.join(ROOT, "data", "recall_probes.json"))
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()

    bodies = _bodies(a.run_id)
    items = json.load(io.open(a.probes, encoding="utf-8"))["items"]

    rows = []
    for it in items:
        hits = []
        for key in it["열쇠말"]:
            for tid, text in bodies.items():
                if key in text:
                    hits.append((key, tid))
                    break
        rows.append({**it, "있음": bool(hits), "맞은_열쇠말": [h[0] for h in hits],
                     "문서": sorted({h[1] for h in hits})})

    if a.json:
        print(json.dumps(rows, ensure_ascii=False, indent=1))
        return 0

    print(f"\n원장 {a.run_id} — 문서 {len(bodies)}건 위에서 체크리스트 {len(rows)}건 조회\n")
    cur = None
    for r in rows:
        if r["절"] != cur:
            cur = r["절"]
            print(f"\n[{cur}]")
        mark = "O" if r["있음"] else "X"
        weak = " (약)" if r["힘"] == "weak" else ""
        doc = (r["문서"][0] if r["문서"] else "")
        print(f"  {mark} {r['id']:<5}{r['무엇'][:44]:<46}{weak:<5}{doc[:26]}")

    # ── 합계. **약한 열쇠말은 따로 센다** — 섞으면 천장이 부풀어 보인다 ──
    tot = collections.Counter()
    for r in rows:
        tot[(r["힘"], r["있음"])] += 1
    s_o, s_x = tot[("strong", True)], tot[("strong", False)]
    w_o, w_x = tot[("weak", True)], tot[("weak", False)]
    print(f"\n{'':-<78}")
    print(f"  강한 열쇠말  있음 {s_o} · 없음 {s_x}   ← 천장 판정은 이 줄로 한다")
    print(f"  약한 열쇠말  있음 {w_o} · 없음 {w_x}   ← 우연히 걸릴 수 있다")
    n = len(rows)
    print(f"  전체         있음 {s_o + w_o}/{n} = {round(100 * (s_o + w_o) / n, 1)}%"
          f"  (강한 것만: {s_o}/{s_o + s_x} = {round(100 * s_o / max(s_o + s_x, 1), 1)}%)")
    print("\n  ⚠ 「있음」은 문서에 글자가 있다는 뜻이다. 사실로 건졌다는 뜻이 아니다.")

    print("\n  [절별]")
    per = collections.defaultdict(lambda: [0, 0])
    for r in rows:
        per[r["절"]][0 if r["있음"] else 1] += 1
    for k, (o, x) in per.items():
        print(f"    {k:<8}있음 {o:<3}없음 {x}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
