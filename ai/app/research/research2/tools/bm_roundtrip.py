# -*- coding: utf-8 -*-
"""BM 어댑터 왕복 — **변환 후에도 등급·경계·카드가 전손실 0인가** (판 ㉜). LLM 0회.

    python tools/bm_roundtrip.py --run beauty-13b --concept data/concept_beauty-noshow.json \
        --concept-id beauty-noshow

`boundary_roundtrip`(스냅샷=원장=canvas)의 **한 구간 뒤**다. 그쪽이 「경계가 canvas 까지
도달했나」를 보고, 이쪽은 「**그 다음 수신자에게도 도달했나**」를 본다.

판 ㉘ 이 배운 것을 이 자리에 그대로 적용한다:
**경계는 쓴 곳이 아니라 도달한 곳에서만 존재한다.** 어댑터가 자리를 안 만들면 조용히 빠진다.

세는 것:
  ① **카드 수** — 카드 N장이 evidence N건이 됐는가 (조용한 누락 0)
  ② **등급** — 카드마다 등급이 그대로 옮겨졌는가 (**바뀌면 실패** — 어댑터는 재판정하지 않는다)
  ③ **경계** — 카드가 든 경계 문장이 `caveats` 에 전부 있는가
  ④ **⑦행** — `missing_items` 가 canvas 의 `못_찾은_것` 과 키 단위로 같은가
  ⑤ **concept_id echo** · **price_base 상수**
"""
from __future__ import annotations

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "service")):
    sys.path.insert(0, p)

import bm_adapter as A                                             # noqa: E402
import cards as CARDS                                              # noqa: E402


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--concept", required=True)
    ap.add_argument("--concept-id", required=True)
    a = ap.parse_args()

    cd = CARDS.build(a.run, a.concept)
    m = A.build(a.run, a.concept, a.concept_id)
    cv = A._canvas(a.run, a.concept)

    cards = {c["카드_id"]: c for c in cd["카드"]}
    ev = {e.card_id: e for e in m.evidence_list}
    bad = []

    # ① 카드 수
    if len(cards) != len(ev):
        bad.append(f"① 카드 {len(cards)}장 → evidence {len(ev)}건 (누락 "
                   f"{sorted(set(cards) - set(ev))})")

    for cid, c in cards.items():
        e = ev.get(cid)
        if e is None:
            continue
        # ② 등급 — **어댑터는 재판정하지 않는다**
        if (c.get("등급") or "근거 없음") != e.grade:
            bad.append(f"② {cid} 등급 '{c.get('등급')}' → '{e.grade}' — 어댑터가 등급을 바꿨다")
        # ③ 경계 — 하나라도 빠지면 실패
        want = A._caveats(c)
        for t in want:
            if t not in e.caveats:
                bad.append(f"③ {cid} 경계 누락 — 「{t[:44]}…」")
        # 값·출처도 같이 본다 — 등급만 맞고 값이 틀리면 더 나쁘다
        if c.get("값") is not None and e.value != c.get("값"):
            bad.append(f"③' {cid} 값 {c.get('값')} → {e.value}")

    # ④ ⑦행
    mi, nf = set(m.missing_items), set(cv.get("못_찾은_것") or {})
    if mi != nf:
        bad.append(f"④ missing_items 키 불일치 — 빠짐 {sorted(nf - mi)} 더함 {sorted(mi - nf)}")

    # ⑤ 상수·echo
    if m.price_base != A.PRICE_BASE:
        bad.append(f"⑤ price_base {m.price_base} ≠ {A.PRICE_BASE}")
    if m.concept_id != a.concept_id:
        bad.append(f"⑤ concept_id echo 실패 {m.concept_id} ≠ {a.concept_id}")

    # 그쪽 진입점 형태 검증
    try:
        A.BMAnalysisInput.model_validate({"market": m.model_dump()})
        valid = "통과"
    except Exception as e:                                        # noqa: BLE001
        valid = f"실패 {e}"
        bad.append(f"pydantic {valid}")

    n_cav = sum(len(e.caveats) for e in m.evidence_list)
    print(f"[{a.run}] 카드 {len(cards)} → evidence {len(ev)} · 경계 {n_cav}문장 · "
          f"missing {len(mi)}종 · concept_id={m.concept_id} · price_base={m.price_base}")
    print(f"  pydantic(BMAnalysisInput.model_validate): {valid}")
    if bad:
        print("실패:")
        for b in bad:
            print("  X  " + b)
        return 1
    print("  통과 — 전손실 0 (카드·등급·경계·값·⑦행·상수·echo)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
