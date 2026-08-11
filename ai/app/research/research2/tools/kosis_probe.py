# -*- coding: utf-8 -*-
"""판 ④ 0단계 — **표를 먼저 연다.** KOSIS 에 실제로 무엇이 있는지 대조한다.

    python tools/kosis_probe.py

**LLM 0회 · 읽기 전용 · 원장 쓰기 0.** 어댑터(`adapters/kosis.py`)의 함수를 **그대로** 쓴다 —
여기서 따로 구현하면 「탐색기에서는 보였는데 수집기에서는 안 보인다」가 생긴다.

왜 이 단계가 있는가: 백로그 18(중기부 소상공인실태조사)은 **자료가 있다고 가정하고**
설계를 다 세웠다가, 원문을 열어 보니 산업 대분류까지만 보고해서 통째로 기각됐다.
확인 비용은 쿼리 몇 번이다. **추측으로 채우지 않는다.**

확인 4가지:
  (a) 두발 미용업 × **종사자규모** 교차표가 있고 「1인」 항목이 있는가
  (b) 같은 표에 **시도(서울)** 축이 있는가
  (c) **2023·2024** 연도가 수록됐는가
  (d) 각 표의 `orgId/tblId` (= 슬롯에 기입할 `stat_code`)
"""
from __future__ import annotations

import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "adapters"), os.path.join(ROOT, "blocks")):
    sys.path.insert(0, p)

import kosis                                   # noqa: E402  — 어댑터를 그대로 쓴다
from base import get_json, load_env_key        # noqa: E402
from runlog import load_rules                  # noqa: E402
from schema import Slot                        # noqa: E402

OUT_DIR = os.path.join(ROOT, "runs", "kosis-probe-04")

#: 종사자규모 축·항목을 알아보는 말들. **판정에 쓰지 않고 표시에만 쓴다** —
#  코드가 「1인」을 못 알아봐도 사람이 덤프를 보고 판단할 수 있어야 한다.
SIZE_AXIS_WORDS = ("종사자규모", "종사자 규모", "규모별", "사업체규모", "종사자수규모")
ONE_WORDS = ("1인", "1 인", "1~4", "1-4", "1명")
SIDO_WORDS = ("시도", "행정구역", "지역", "시·도")


def probe_slot(label: str, subject: str, metric: str, region: str, period: str,
               rules: dict, key: str) -> dict:
    """슬롯 하나를 어댑터의 눈으로 본다. 채택하지 않고 **보이는 것을 적는다.**"""
    s = Slot(slot_id="PROBE", var_id="V", formula_id="F", claim_type="TAM", unit="개",
             subject=subject, metric=metric, period=period, region=region)
    out = {"label": label, "subject": subject, "metric": metric, "region": region,
           "period": period}
    cands = kosis.search_tables(s, rules, key)
    out["후보수"] = len(cands)
    out["후보"] = [{"stat_code": f"{c['org_id']}/{c['tbl_id']}", "name": c["name"],
                    "overlap": c["overlap"], "region_fit": c["region_fit"],
                    "period_fit": c["period_fit"], "from_query": c["from_query"]}
                   for c in cands[:8]]
    code, why, _ = kosis.resolve_stat_code(s, rules, key)
    out["resolve_stat_code"] = code
    out["resolve_why"] = why
    return out


def dump_meta(org_id: str, tbl_id: str, rules: dict, key: str) -> dict:
    """표 하나의 축과 항목을 전부 적는다. **판정하지 않는다 — 사람이 본다.**"""
    res = {"stat_code": f"{org_id}/{tbl_id}"}
    axes, items = kosis.table_meta(org_id, tbl_id, rules, key)
    if not axes:
        res["error"] = "메타 조회 실패(type=ITM)"
    else:
        res["ITM_축"] = [{"obj_id": a, "obj_nm": items[a]["obj_nm"],
                          "항목수": len(items[a]["items"]),
                          "항목": [i["name"] for i in items[a]["items"][:40]]}
                         for a in axes]
    # 분류축(OBJ)은 ITM 과 다른 호출이다 — 종사자규모·시도는 보통 여기 있다.
    cfg = rules["adapters"]["kosis"]
    data, err, detail = get_json(cfg["meta_base"],
                                 {"method": "getMeta", "apiKey": key, "orgId": org_id,
                                  "tblId": tbl_id, "type": "OBJ", "format": "json",
                                  "jsonVD": "Y"}, rules)
    if err or not isinstance(data, list):
        res["OBJ_오류"] = f"{err}: {str(detail)[:200]}"
        return res
    by_axis: dict = {}
    for row in data:
        oid = row.get("OBJ_ID")
        a = by_axis.setdefault(oid, {"obj_id": oid, "obj_nm": row.get("OBJ_NM"), "항목": []})
        a["항목"].append({"id": str(row.get("ITM_ID") or row.get("OBJ_ITM_ID") or ""),
                         "name": row.get("ITM_NM") or row.get("OBJ_ITM_NM") or ""})
    res["OBJ_축"] = []
    for a in by_axis.values():
        names = [x["name"] for x in a["항목"]]
        res["OBJ_축"].append({
            "obj_id": a["obj_id"], "obj_nm": a["obj_nm"], "항목수": len(names),
            "항목_앞40": names[:40],
            "_종사자규모축?": any(w in str(a["obj_nm"] or "") for w in SIZE_AXIS_WORDS),
            "_시도축?": any(w in str(a["obj_nm"] or "") for w in SIDO_WORDS),
            "_1인항목": [n for n in names if any(w in n.replace(" ", "") for w in ONE_WORDS)][:10],
            "_서울항목": [n for n in names if "서울" in n][:5],
        })
    return res


def main():
    key = load_env_key("KOSIS_API_KEY")
    if not key:
        print("KOSIS_API_KEY 없음 — 가짜로 채우지 않는다. 여기서 멈춘다.")
        return 2
    rules = load_rules()
    os.makedirs(OUT_DIR, exist_ok=True)

    report = {"_규칙": "0단계 실측. 판정하지 않고 보이는 것을 적는다. LLM 0회.",
              "as_of": "2026-08-08", "슬롯탐색": [], "표덤프": []}

    probes = [
        ("S18 1인 사업체 수(전국)", "두발 미용업", "종사자 1인 사업체 수", "대한민국", "2024"),
        ("S19 1인 사업체 수(서울)", "두발 미용업", "종사자 1인 사업체 수", "서울특별시", "2024"),
        ("S20/S21 사업체 수(전국)", "두발 미용업", "사업체 수", "대한민국", "2024"),
        ("S5 사업체 수(서울)", "두발 미용업", "사업체 수", "서울특별시", "2024"),
    ]
    seen_tables = {}
    for label, subj, met, reg, per in probes:
        print(f"[탐색] {label} …")
        r = probe_slot(label, subj, met, reg, per, rules, key)
        report["슬롯탐색"].append(r)
        for c in r["후보"][:4]:
            org, tbl = c["stat_code"].split("/", 1)
            seen_tables[(org, tbl)] = c["name"]
        print(f"    후보 {r['후보수']}개 · resolve={r['resolve_stat_code']} · {str(r['resolve_why'])[:110]}")

    for (org, tbl), name in list(seen_tables.items())[:10]:
        print(f"[메타] {org}/{tbl} {name[:50]} …")
        d = dump_meta(org, tbl, rules, key)
        d["표이름"] = name
        report["표덤프"].append(d)

    io.open(os.path.join(OUT_DIR, "probe_raw.json"), "w", encoding="utf-8").write(
        json.dumps(report, ensure_ascii=False, indent=1))
    print(f"\n원자료: {os.path.join(OUT_DIR, 'probe_raw.json')}")
    print(f"표 {len(report['표덤프'])}개 덤프. 다음: 사람이 읽고 kosis_probe.md 를 쓴다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
