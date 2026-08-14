# -*- coding: utf-8 -*-
"""단위 배수 전수 감사 — **수리보다 먼저** (판 ⑱ ①). 읽기 전용 · LLM 0회.

    python tools/unit_audit.py

물음: **배수가 등재되지 않은 단위**가 붙은 사실이 있었나, 그리고 그것이 **통과했나**.

    (가) 격리됨      — 무해. 수리 후 재채점 대상
    (나) 통과해 값에 실림 — **사고.** 100만 배 틀린 값이 이미 나갔다는 뜻
    (다) 해당 없음

⚠ **(나) 가 1건이라도 있으면 수리보다 그 사실이 급하다** — 이미 나간 출력을 회수해야 한다.

판정 기준: `unit_raw` 에 **배수 접두어**(백만·십억·백억 …)가 들어 있는데
`units.unit_scale_in_name` 에 그 단위가 **없으면** 배수가 안 먹은 것이다.
"""
from __future__ import annotations

import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

#: 한국어 배수 접두어 — 이것이 단위 문자열에 있으면 **배수가 있어야 한다**
_PREFIX = re.compile(r"(백만|천만|십억|백억|천억|십조|백조|만|억|조|천)")


def main():
    units = json.load(io.open(os.path.join(ROOT, "rules", "units.v1.json"), encoding="utf-8"))
    registered = {k for k in (units.get("unit_scale_in_name") or {})
                  if not k.startswith("_")}
    # ⚠ **엔진과 같은 눈으로 본다.** 감사가 정규화를 안 보면 「억 원」이 미등재로 잡히는데
    #   파서는 정규화해서 잘 먹는다 — **같은 표를 두 곳이 다르게 읽는** 그 병이다
    #   (판 ⑫·⑬·⑯·⑰ 계보). 감사 도구도 규칙 파일의 정규화를 그대로 쓴다.
    _nz = units.get("unit_scale_normalize") or {}
    _strip = (_nz.get("strip_chars") or [" "]) if _nz.get("enabled") else []

    def _known(u: str) -> bool:
        if u in registered:
            return True
        v = u
        for ch in _strip:
            v = v.replace(ch, "")
        return v in registered

    runs_dir = os.path.join(ROOT, "runs")
    seen_units: dict = {}          # unit_raw → 등장 횟수
    rows = []

    for rid in sorted(os.listdir(runs_dir)):
        d = os.path.join(runs_dir, rid)
        jl = os.path.join(d, "run.jsonl")
        if not os.path.isfile(jl):
            continue
        # 원장 라벨 — 그 사실이 통과했는가
        label: dict = {}
        try:
            res = json.load(io.open(os.path.join(d, "result.json"), encoding="utf-8"))
            for r in (res.get("report") or {}).get("ledger") or []:
                label[r.get("fact_id")] = r.get("label")
        except Exception:
            pass

        for line in io.open(jl, encoding="utf-8"):
            if not line.strip():
                continue
            try:
                x = json.loads(line)
            except Exception:
                continue
            if x.get("node") != "a3_finding":
                continue
            for it in (x["payload"].get("findings") or []):
                u = (it.get("unit_raw") or "").strip()
                if not u:
                    continue
                seen_units[u] = seen_units.get(u, 0) + 1
                if not _PREFIX.search(u) or _known(u):
                    continue          # 배수가 없거나 등재돼 있다 → (다)
                rows.append({"run": rid, "slot_id": x["payload"].get("slot_id"),
                             "unit_raw": u, "number_raw": it.get("number_raw"),
                             "quote": (it.get("quote") or "")[:80]})

    # ── 라벨 대조 — **fact_id 로 잇는다.** ─────────────────────────
    # ⚠ 1차 잣대는 **슬롯 단위**로 라벨을 봤고 「그 슬롯에 통과한 사실이 하나라도 있으면 (나)」
    #   로 쳤다 — 그 통과한 사실이 **이 사실인지 모르는 채로**다(판 ⑭ 의 교훈: 잣대를 의심하라).
    #   지금은 **배수가 빠진 그 값 자체**가 원장에 어떤 라벨로 앉았는지를 본다:
    #   `value_num` 이 「배수를 안 먹은 숫자」와 같으면 그 사실이 바로 그것이다.
    _digits = re.compile(r"[\d,.]+")

    def _bare(nraw: str):
        """배수를 **안** 먹었을 때 나오는 숫자. 「350」+「억 원」 → 350.0"""
        m = _digits.search(str(nraw or ""))
        try:
            return float(m.group(0).replace(",", "")) if m else None
        except ValueError:
            return None

    facts_by_run: dict = {}
    for rid in {r["run"] for r in rows}:
        fb = {}
        jl = os.path.join(runs_dir, rid, "run.jsonl")
        for line in io.open(jl, encoding="utf-8"):
            if not line.strip():
                continue
            try:
                x = json.loads(line)
            except Exception:
                continue
            if x.get("node") == "a4_facts":
                p_ = x["payload"]
                fb.setdefault(p_.get("slot_id"), []).append(p_)
        facts_by_run[rid] = fb

    for r in rows:
        try:
            res = json.load(io.open(os.path.join(runs_dir, r["run"], "result.json"),
                                    encoding="utf-8"))
            lab = {x.get("fact_id"): x.get("label")
                   for x in (res.get("report") or {}).get("ledger") or []}
        except Exception:
            lab = {}
        bare = _bare(r["number_raw"])
        hit = [f for f in facts_by_run.get(r["run"], {}).get(r["slot_id"], [])
               if bare is not None and f.get("value_num") == bare]
        r["이은_사실"] = [f.get("fact_id") for f in hit]
        labs = [lab.get(f.get("fact_id")) for f in hit]
        r["사실_라벨"] = labs
        # **격리 라벨이면 무해.** 그 밖(확인됨·출처약함·미확인)이면 값에 실린 것이다.
        passed = [l for l in labs if l and l not in ("off_slot", "미검증")]
        r["분류"] = "나" if passed else ("가" if hit else "다(사실 미연결)")

    out = {
        "_규칙": "배수 접두어가 있는데 `unit_scale_in_name` 에 없는 단위 = 배수 미적용. "
                "(나)는 **그 값이 심사를 통과했다**는 뜻이고 사고다.",
        "등재된_배수_단위": sorted(registered),
        "본_적_있는_단위_전수": dict(sorted(seen_units.items(), key=lambda kv: -kv[1])),
        "미등재_배수_단위": sorted({r["unit_raw"] for r in rows}),
        "집계": {k: sum(1 for r in rows if r["분류"] == k)
                 for k in ("가", "나", "다(사실 미연결)")},
        "행": rows,
    }
    p = os.path.join(runs_dir, "unit_audit.json")
    io.open(p, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))

    print(f"등재된 배수 단위 : {sorted(registered)}")
    print(f"본 적 있는 단위  : {len(seen_units)}종")
    print(f"미등재 배수 단위 : {out['미등재_배수_단위']}")
    print(f"집계             : {out['집계']}  (나 = 사고)")
    for r in rows[:12]:
        print(f"  [{r['분류']}] {r['run']:<16}{r['slot_id']:<5}{r['unit_raw']:<8}"
              f"{str(r['number_raw'])[:14]:<15}사실={r['이은_사실']} 라벨={r['사실_라벨']}")
    print(f"\n기록: {p}")
    return 1 if out["집계"]["나"] else 0


if __name__ == "__main__":
    sys.exit(main())
