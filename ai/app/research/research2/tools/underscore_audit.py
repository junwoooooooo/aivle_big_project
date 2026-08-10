# -*- coding: utf-8 -*-
"""`_` 접두 키 소실 전수 감사 — **수리보다 먼저** (판 ㉘ ①). 읽기 전용 · LLM 0회.

    python tools/underscore_audit.py

물음: **스냅샷에 있었는데 원장에 없는 `_` 접두 키**가 무엇이고, 그것이 **출력에 실렸나**.

    (가) 소실됐지만 출력에 안 실렸다   — 무해. 승격 후 재채점 대상
    (나) **소실된 채 출력이 나갔다**   — **사고 · 차단 보고**
    (다) 해당 없음

⚠ **(나) 가 1건이라도 있으면 수리보다 그 사실이 급하다** — 경계 없는 값이 이미 나갔다는 뜻이다.

왜 이 감사가 필요한가: 판 ㉗ 이 `_proxy_선언` 소실을 **우연히** 발견했다(D 3칸을 확인하다가).
**우연히 찾은 결함은 그 하나만 있다는 보장이 없다.** 판 ⑱ 단위 감사와 같은 순서를 밟는다.
"""
from __future__ import annotations

import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

#: 경계·선언 성격의 키 — 소실되면 **정직성이 사라진다**. 나머지 `_` 키(이력·주석)와 다르다.
_경계급 = ("proxy", "경계", "울타리", "다리", "한계", "동결", "잠정")


def main():
    snap_keys: dict = {}          # 스냅샷에서 본 `_` 키 → 어느 파일에서
    for d in (os.path.join(ROOT, "data"), os.path.join(ROOT, "runs", "harness")):
        for root, _dirs, files in os.walk(d):
            for fn in files:
                if not fn.endswith(".json"):
                    continue
                try:
                    obj = json.load(io.open(os.path.join(root, fn), encoding="utf-8"))
                except Exception:
                    continue
                for s in (obj.get("slots") or []) if isinstance(obj, dict) else []:
                    if not isinstance(s, dict):
                        continue
                    for k in s:
                        if k.startswith("_"):
                            snap_keys.setdefault(k, set()).add(fn)

    #: 원장에 실제로 살아남은 `_` 키
    led_keys: dict = {}
    runs = os.path.join(ROOT, "runs")
    for rid in sorted(os.listdir(runs)):
        p = os.path.join(runs, rid, "result.json")
        if not os.path.isfile(p):
            continue
        try:
            res = json.load(io.open(p, encoding="utf-8"))
        except Exception:
            continue
        for s in (res.get("input") or {}).get("slots") or []:
            for k in s:
                if k.startswith("_"):
                    led_keys.setdefault(k, set()).add(rid)

    소실 = sorted(set(snap_keys) - set(led_keys))
    rows = []
    for k in 소실:
        급 = any(w in k for w in _경계급)
        # **(나) 판정** — 그 키가 붙은 슬롯의 값이 출력(canvas·보고서)에 실렸는가.
        # 경계급 키만 본다: 이력·주석이 안 넘어간 것은 설계대로다.
        # ⚠ **1차 잣대는 틀렸다** — 「경계급 키가 있고 canvas 가 있는 원장」을 전부 (나)로 세어
        #   계열 A 미용실 원장(`_proxy_선언` 을 쓸 이유가 없다)까지 잡았다. 판 ⑭ 의 교훈 그대로
        #   **느슨한 잣대는 결함을 부풀린다.** 물어야 할 것은 「그 키를 **실제로 담은 슬롯**의
        #   값이 출력에 실렸는가」다 — 그러려면 **그 슬롯 id 를 원장에서 찾아** 대조해야 한다.
        나 = []
        if 급:
            # 그 키를 담은 슬롯 id 를 스냅샷에서 모은다
            ids = set()
            for d in (os.path.join(ROOT, "data"), os.path.join(ROOT, "runs", "harness")):
                for root, _dd, files in os.walk(d):
                    for fn in files:
                        if not fn.endswith(".json"):
                            continue
                        try:
                            obj = json.load(io.open(os.path.join(root, fn), encoding="utf-8"))
                        except Exception:
                            continue
                        for s in (obj.get("slots") or []) if isinstance(obj, dict) else []:
                            if isinstance(s, dict) and k in s and s.get(k) not in (None, "", {}):
                                ids.add((fn, s.get("slot_id")))
            snap_ids = {sid for _f, sid in ids}
            for rid in sorted(os.listdir(runs)):
                p = os.path.join(runs, rid, "result.json")
                if not os.path.isfile(p):
                    continue
                try:
                    res = json.load(io.open(p, encoding="utf-8"))
                except Exception:
                    continue
                # **그 원장이 그 스냅샷으로 돌았는가** — 슬롯 id 만으로는 S1 이 어디에나 있어
                # 또 부푼다. 계열이 proxy 를 쓰는 컨셉인지까지 본다.
                cpt = (res.get("input") or {}).get("concept") or {}
                series = ((cpt.get("_계열") or {}).get("계열") or "")
                if 급 and "proxy" in k and series not in ("D",):
                    continue
                conf = [x for x in (res.get("report") or {}).get("ledger") or []
                        if x.get("label") == "확인됨" and x.get("slot_id") in snap_ids]
                out = os.path.join(ROOT, "outputs", f"canvas_{rid}.json")
                if conf and os.path.exists(out):
                    나.append(rid)
        rows.append({"키": k, "경계급": 급, "스냅샷_파일수": len(snap_keys[k]),
                     "분류": ("나" if 나 else ("가" if 급 else "다(주석·이력 — 설계대로)")),
                     "출력_나간_원장": 나})

    out = {"_규칙": "스냅샷에 있었는데 원장에 없는 `_` 키. **경계급**(proxy·경계·울타리·다리…)만 "
                   "(가)/(나)로 가른다 — 이력·주석이 안 넘어간 것은 설계대로다.",
           "스냅샷에서_본_키": sorted(snap_keys), "원장에_살아남은_키": sorted(led_keys),
           "소실": 소실,
           "집계": {k: sum(1 for r in rows if r["분류"].startswith(k))
                    for k in ("가", "나", "다")},
           "행": rows}
    p = os.path.join(runs, "underscore_audit.json")
    io.open(p, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))

    print(f"스냅샷 `_` 키   : {len(snap_keys)}종")
    print(f"원장 생존       : {sorted(led_keys)}")
    print(f"소실            : {소실}")
    print(f"집계            : {out['집계']}   (나 = 사고)")
    for r in rows:
        print(f"  [{r['분류']:<20}] {r['키']:<18} 경계급={r['경계급']} "
              f"출력={r['출력_나간_원장'][:3]}")
    print(f"\n기록: {p}")
    return 1 if out["집계"]["나"] else 0


if __name__ == "__main__":
    sys.exit(main())
