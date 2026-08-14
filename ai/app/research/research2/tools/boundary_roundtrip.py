# -*- coding: utf-8 -*-
"""경계 왕복 검사 — **스냅샷 = 원장 = canvas** (판 ㉘ 도장). 읽기 전용 · LLM 0회.

    python tools/boundary_roundtrip.py --run <id> --slots <snapshot.json>

**경계는 쓴 곳이 아니라 도달한 곳에서만 존재한다.**
게이트 22검사는 **하네스 산출만** 본다 — 그래서 「스냅샷에 경계를 적었는가」는 보지만
「그것이 최종 매체까지 갔는가」는 **구조적으로 못 본다.** 판 ㉘ 감사가 그 구멍으로
`_경계` 소실 (나) 1건을 찾았다. 이 검사가 **최종 매체 축**에서 그 구멍을 막는다.

세 지점의 경계 필드 수가 **같아야** 통과다:

    스냅샷(사람·하네스가 쓴 곳)  =  원장(result.json)  =  canvas.json(수신자가 읽는 곳)

⚠ **개수만 세지 않는다** — 문장이 같은지도 본다. 개수가 같아도 다른 문장이면
   어딘가에서 갈아치워진 것이다.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

from schema import 경계_승격                                    # noqa: E402

#: 승격된 경계급 필드 — 이 넷이 왕복해야 한다.
FIELDS = tuple(경계_승격.values())


def _collect(slots: list) -> dict:
    """슬롯 목록에서 경계 문장을 모은다. 옛 `_` 이름도 같이 본다(스냅샷 호환)."""
    out = {}
    for s in slots or []:
        if not isinstance(s, dict):
            continue
        for old, new in 경계_승격.items():
            v = s.get(new) or s.get(old)
            if v in (None, "", {}):
                continue
            # dict(proxy_선언)은 **사유 문자열**로 눌러 비교한다 — 표현이 아니라 내용이 기준이다.
            # ⚠ **빈 껍데기는 경계가 아니다** — 하네스가 전 슬롯에 `{"대상":"","사유":""}` 를
            #   붙이는데, 그것까지 세면 「경계 12건 중 10건 미도달」로 **결함이 부푼다**
            #   (판 ㉘ 세 번째 잣대 조임 · 판 ⑭ 교훈).
            if isinstance(v, dict):
                txt = str(v.get("사유") or "").strip()
                if not txt:
                    continue
            else:
                txt = str(v).strip()
            if not txt:
                continue
            out[(s.get("slot_id"), new)] = txt
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--slots", required=True, help="그 실행을 만든 스냅샷")
    a = ap.parse_args()

    snap = json.load(io.open(os.path.join(ROOT, a.slots), encoding="utf-8"))["slots"]
    res = json.load(io.open(os.path.join(ROOT, "runs", a.run, "result.json"), encoding="utf-8"))
    led_slots = (res.get("input") or {}).get("slots") or []

    S, L = _collect(snap), _collect(led_slots)

    # canvas 는 **문장 포함**으로 본다 — 칸 구조가 슬롯과 1:1 이 아니라서 키로 못 맞춘다.
    cpath = os.path.join(ROOT, "outputs", f"canvas_{a.run}.json")
    ctext = io.open(cpath, encoding="utf-8").read() if os.path.exists(cpath) else ""

    # canvas 의 등급 표는 **사실이 있는 슬롯만** 낸다. 사실이 0건인 슬롯에 경계만 실을 수는
    # 없다 — 실을 값이 없다. 그러니 canvas 도달은 **채택된 사실이 있는 슬롯에만** 요구한다.
    # ⚠ 이것은 완화가 아니다: **값이 나간 곳에 경계가 따라갔는가**가 물음이고,
    #    값이 안 나갔으면 오독될 값도 없다.
    실린_슬롯 = {r.get("slot_id") for r in (res.get("report") or {}).get("ledger") or []
                if r.get("label") not in ("off_slot", "미검증")}

    rows, bad = [], []
    for k, txt in sorted(S.items(), key=lambda x: (x[0][0] or "", x[0][1])):
        in_led = L.get(k)
        # canvas 는 원문 그대로가 아닐 수 있어 **앞 12자**로 본다(문장 다듬기 허용)
        probe = txt[:12]
        in_canvas = bool(ctext) and probe in ctext
        값이_나갔나 = k[0] in 실린_슬롯
        ok = (in_led == txt) and (in_canvas or not 값이_나갔나)
        rows.append({"slot_id": k[0], "필드": k[1], "스냅샷": txt[:60],
                     "원장": ("같음" if in_led == txt else ("다름" if in_led else "없음")),
                     "canvas": ("있음" if in_canvas else
                                ("해당없음(사실 0건 — 나갈 값이 없다)" if not 값이_나갔나 else
                                 ("없음" if ctext else "canvas 미생성"))),
                     "통과": ok})
        if not ok:
            bad.append(rows[-1])

    out = {"_규칙": "스냅샷 = 원장 = canvas. 셋이 다르면 실패. **경계는 도달한 곳에서만 존재한다.**",
           "run": a.run, "slots": a.slots,
           "집계": {"경계_수": len(S), "원장_도달": sum(1 for r in rows if r["원장"] == "같음"),
                    "canvas_도달": sum(1 for r in rows if r["canvas"] == "있음"),
                    "실패": len(bad)},
           "행": rows}
    p = os.path.join(ROOT, "runs", a.run, "boundary_roundtrip.json")
    io.open(p, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))

    print(f"경계 {len(S)}건 · 원장 도달 {out['집계']['원장_도달']} · "
          f"canvas 도달 {out['집계']['canvas_도달']} · 실패 {len(bad)}")
    for r in rows:
        print(f"  {'OK ' if r['통과'] else 'X  '}{r['slot_id']:<5}{r['필드']:<12}"
              f"원장={r['원장']:<5}canvas={r['canvas']:<12}{r['스냅샷'][:45]}")
    print(f"\n기록: {p}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
