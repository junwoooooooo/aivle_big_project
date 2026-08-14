# -*- coding: utf-8 -*-
"""채택된 사실의 **인용문**을 검사한다 — LLM 0회 · 원장 쓰기 0회.

    python -X utf8 tools/quote_audit.py --run pain-full-02
    python -X utf8 tools/quote_audit.py --run pin-09 --json

왜 있는가: 판 ㉟ 4단계가 성적표 **2/2 6/6** 을 냈는데, 채택된 인용을 손으로 읽으니
넷 다 슬롯이 묻는 것이 아니었다(입점업체 중개수수료 4.5% · 숫자뿐인 「95%」 ·
전 세계 예약 증가율 19% · 2018년 문서에 찍힌 `year 2025`).

**그 검사가 사람 눈 안에만 있었다.** 눈은 판마다 잣대가 흔들리고 다음 판은 또 손으로
센다 — 판 ㉟ ①의 `실행_능력` 한 칸이 유료 4판을 되돌린 것과 **같은 종류의 부재**다.
그래서 눈을 코드로 옮긴다.

⚠ **이 도구는 아무것도 격리하지 않는다. 세기만 한다.**
   자르는 것은 `rules/` 의 몫이다. 재는 자와 자르는 자가 같으면 자기 채점이 된다 —
   `design_score` 가 성적표와 일부러 갈리는 것과 같은 이유다.

⚠ **필요조건이지 충분조건이 아니다.** 다섯 검사를 다 통과한 오답이 있다.
"""
from __future__ import annotations

import argparse
import collections
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "blocks"))
import runpath                                           # noqa: E402
# ⚠ **산식은 한 곳이다.** 재는 자와 자르는 자는 갈라 두되(이 도구는 세기만 한다),
#   「값이 인용문에서 읽히는가」의 계산까지 복제하면 감사기와 규칙이 갈린다 —
#   면제 설정을 사본으로 뒀다가 「규칙이 통과시킨 것을 감사기가 계속 집는」 실패를
#   이미 한 번 겪었다(§27.3). 그래서 판정 함수는 엔진 것을 그대로 부른다.
from a_desk import 값이_인용문에_있는가                       # noqa: E402

#: 인용문에 이 낱말이 있으면 **응답자가 사업자 쪽**이다. 소비자 경험률 슬롯에서
#: 세 판 연속 같은 함정에 걸렸다 — 4.5%(입점업체 중개수수료) · 69.3%(배달앱 이용사업자
#: n=300) · 28.3%(소상공인 만족도). 셋 다 수치·단위는 맞고 **모집단만 달랐다.**
사업자_낱말 = ("사업자", "점주", "입점업체", "소상공인", "가맹점", "자영업자",
            "외식업체", "판매자", "셀러")

#: 슬롯 region 이 국내인데 인용문이 이것을 말하면 다른 것을 재고 있다.
해외_낱말 = ("전 세계", "전세계", "글로벌", "해외", "세계 각국", "OECD")

#: 인용문에서 숫자·기호를 걷어낸 뒤 남는 글자 수의 하한.
#: **보정 기준은 pin-09 다.** 처음 10 으로 뒀더니 「이용경험률 85%」(5자)까지 집었는데,
#: 그 인용은 **무엇을 잰 값인지 말하고 있다** — 집으면 안 된다. 4 로 내리면 갈린다:
#:   살린다  「이용경험률 85%」5자 · 「혼자 식사한 비율은 아침 식사한 사람 중 41.7%…」
#:   집는다  「95%」0자 · 「13.7%」0자 · 「20%」0자 · 「약 20%」1자 · 「3,400」0자
#: 즉 **무엇을 잰 값인지 말하는 인용은 살고, 숫자만 있는 것은 집힌다.**
서술_최소자 = 4

#: 구조화 응답 채널. **인용문이 산문이 아니다** — `"DT": "38041110"` 은 KOSIS 표의
#: 칸이지 사람이 쓴 문장이 아니고, 그 값의 정체는 `stat_code`·`itmId` 가 보증한다.
#: 여기를 면제하지 않으면 pin-09 의 정상 채움 4건이 통째로 오탐이 된다(실측).
서술_면제_채널 = ("kosis_api", "dart_api")

_숫자기호 = re.compile(r"[\d０-９.,%％·~\-–—()\[\]{}<>/:;·、，。\s'\"“”‘’]+")


def _load(run: str) -> tuple:
    d = runpath.read_dir(run)
    p = os.path.join(d, "run.jsonl")
    if not os.path.isfile(p):
        raise SystemExit(f"원장이 없다: {p}")
    facts, ledger = {}, {}
    for ln in io.open(p, encoding="utf-8"):
        if not ln.strip():
            continue
        o = json.loads(ln)
        pl = o.get("payload") or {}
        if o.get("node") == "a4_facts":
            facts[pl.get("fact_id")] = pl
        elif o.get("node") == "a4_ledger":
            for r in (pl.get("rows") or ([pl] if "label" in pl else [])):
                ledger[r.get("fact_id")] = r
    res = json.load(io.open(os.path.join(d, "result.json"), encoding="utf-8"))
    specs = {s["slot_id"]: s for s in (res.get("input") or {}).get("slots") or []}
    # ⚠ **면제는 원장의 규칙에서 읽는다.** 여기 사본을 두면 재는 자와 자르는 자가 갈리고,
    #   그 갈라짐이 이 저장소가 반복해 겪은 실패다(엔진이 쓰는 것 vs 이미지가 설치하는 것).
    #   실측: PRICE 면제를 규칙에만 넣었더니 감사기가 규칙이 통과시킨 사실을 계속 집었다.
    #   옛 원장(가드 이전)에는 이 칸이 없으므로 모듈 기본값으로 떨어진다.
    off = ((res.get("rules") or {}).get("scoring") or {}).get("off_slot") or {}
    # ⚠ `units` 도 원장에서 읽는다. **없으면 다섯째 눈을 돌리지 않는다** — 파서 표가
    #   없는데 기본표로 메우면 「그때의 잣대」가 아니라 「지금의 잣대」로 옛 원장을 재게 된다.
    #   칸이 없으면 0 이 아니라 미측정이다(판 ㉟ ①과 같은 원칙).
    units = (res.get("rules") or {}).get("units") or {}
    return (facts, ledger, specs,
            (off.get("무서술_인용") or {}), (off.get("값_부재_인용") or {}), units)


def 검사(f: dict, slot: dict, bare_cfg: dict | None = None,
       nv_cfg: dict | None = None, units_rules: dict | None = None) -> list[dict]:
    """사실 하나에 대한 지적 목록. **판정이 아니라 관측이다.**"""
    out = []
    q = str(f.get("quote") or "")

    # ① 불가능 연도 — 2018년에 발행된 문서가 2025년 통계를 실을 수는 없다.
    #    ⚠ **단순 불일치는 잡지 않는다.** `schema.py:329` 가 「2025년 기사가 2023년
    #    통계를 인용」을 정상으로 규정한다. 잡는 것은 **반대 방향**뿐이다.
    #    published_year 가 없으면 판정하지 않는다 — 없는 기준으로 벌하지 않는다.
    y, py = f.get("year"), f.get("published_year")
    if isinstance(y, int) and isinstance(py, int) and y > py:
        out.append({"검사": "불가능_연도", "상세": f"year {y} > published_year {py}"})

    # ② 모집단 — 값도 단위도 맞는데 **누구에게 물었는가**가 다르다.
    hit = [w for w in 사업자_낱말 if w in q]
    if hit:
        out.append({"검사": "모집단", "상세": f"인용문에 {hit}"})

    # ③ 무서술 인용 — 숫자만 있는 인용은 근거가 아니다(절대규칙 4).
    #    API 채널은 면제한다 — 거기 '인용문'은 표의 칸이지 문장이 아니다.
    cfg = bare_cfg or {}
    면제채널 = cfg.get("면제_채널") or list(서술_면제_채널)
    면제타입 = cfg.get("면제_claim_type") or []
    최소자 = int(cfg.get("최소자") or 서술_최소자)
    bare = _숫자기호.sub("", q)
    if (f.get("channel") not in 면제채널
            and str(slot.get("claim_type") or "") not in 면제타입
            and len(bare) < 최소자):
        out.append({"검사": "무서술_인용", "상세": f"숫자·기호를 뺀 글자 {len(bare)}자 < {최소자}"})

    # ④ 지역 이탈 — 슬롯이 국내를 묻는데 인용이 세계를 말한다.
    reg = str(slot.get("region") or "")
    if reg and ("한국" in reg or "대한민국" in reg):
        h2 = [w for w in 해외_낱말 if w in q]
        if h2:
            out.append({"검사": "지역_이탈", "상세": f"슬롯 region '{reg}' vs 인용문 {h2}"})

    # ⑤ 값 부재 인용 (판 ㊲ G5) — 채택된 값이 **그 인용문 안에서 읽히는가**.
    #    ⚠ 문자열 대조가 아니다. 판정은 엔진 함수가 한다(산식 한 곳).
    #    ⚠ API 채널 면제. 「"DT": "38041110"」(백만원 칸) vs value_num 38041110000000 —
    #      면제하지 않으면 정상 채움 6건이 오탐이다(실측).
    nv = nv_cfg or {}
    v = f.get("value_num")
    if (units_rules and v is not None
            and f.get("channel") not in (nv.get("면제_채널") or list(서술_면제_채널))):
        if not 값이_인용문에_있는가(q, float(v), units_rules,
                            float(nv.get("상대_허용오차") or 0.005)):
            out.append({"검사": "값_부재_인용", "상세": f"{float(v):g} 가 인용문에서 읽히지 않는다"})
    return out


def build(run: str, claim_type: str = "") -> dict:
    facts, ledger, specs, bare_cfg, nv_cfg, units = _load(run)
    rows, 통계 = [], collections.Counter()
    검사한 = 0
    for fid, f in facts.items():
        led = ledger.get(fid) or {}
        # **채택된 것만 본다.** 격리된 것은 이미 값으로 남아 있고, 산출물에 안 간다.
        if not led.get("채택"):
            continue
        slot = specs.get(f.get("slot_id")) or {}
        if claim_type and str(slot.get("claim_type") or "").upper() != claim_type.upper():
            continue
        검사한 += 1
        지적 = 검사(f, slot, bare_cfg, nv_cfg, units)
        for x in 지적:
            통계[x["검사"]] += 1
        if 지적:
            rows.append({"fact_id": fid, "slot_id": f.get("slot_id"),
                         "claim_type": slot.get("claim_type"),
                         "label": led.get("label"), "등급": led.get("등급"),
                         "value": f.get("value_num"), "unit": f.get("unit_norm"),
                         "url": f.get("url"), "quote": (f.get("quote") or "")[:200],
                         "지적": 지적})
    return {"run": run, "_claim_type": claim_type or None,
            "채택_검사": 검사한, "지적_사실": len(rows),
            # 옛 원장은 `rules.units` 가 없어 다섯째 눈이 안 돈다 — **0 이 아니라 미측정**이다.
            "값_부재_인용_가능": bool(units),
            "검사별": dict(통계), "행": rows}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--claim-type", dest="claim_type", default="")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    r = build(a.run, a.claim_type)

    if a.json:
        print(json.dumps(r, ensure_ascii=False, indent=2))
        return 1 if r["지적_사실"] else 0

    범위 = f" · claim_type={r['_claim_type']} 만" if r["_claim_type"] else ""
    print(f"\n[{r['run']}] 인용 검사  채택 {r['채택_검사']}건 검사{범위}")
    if not r["행"]:
        print("  지적 0건")
        print("  ⚠ 필요조건이지 충분조건이 아니다 — 다섯 검사를 통과한 오답이 있다.\n")
        return 0
    print(f"  지적 {r['지적_사실']}건  {r['검사별']}\n")
    for x in r["행"]:
        print(f"  {x['fact_id']} {x['slot_id']}[{x['claim_type']}] "
              f"{x['value']}{x['unit'] or ''} · {x['label']}/{x['등급']}")
        for d in x["지적"]:
            print(f"      ✗ {d['검사']}: {d['상세']}")
        print(f"      「{x['quote'][:120]}」")
        print(f"      {str(x['url'])[:100]}")
    print()
    return 1


if __name__ == "__main__":
    sys.exit(main())
