# -*- coding: utf-8 -*-
"""산출물 성적표 — **7과목을 코드가 잰다** (판 ㉙ S7). LLM 0회 · 원장 쓰기 0회.

    python tools/scorecard.py --run pet-treat-14 --concept data/concept_pet-treat.json

왜 있는가: 보고 규약은 「완료 보고는 산출물 성적표로 시작한다」인데, 그 표는 지금까지
**판마다 사람이 손으로** 채웠다. 손으로 채우는 표는 두 가지로 틀린다 —
판마다 잣대가 흔들리고(판 ⑭ 「잣대를 조일 때마다 답이 바뀌었다」), 디스크와 어긋난다
(판 ⑳ 「스냅샷 조회를 원장 사실로 착각」 · 판 ㉙ 착수 시 「3·3·1 은 어느 실행에도 없다」).

**문턱은 `rules/fill.v2.json 항목` 에서만 온다** — 여기 코드에 숫자를 박으면 규약 ① 위반이고,
다음 판이 문턱을 고쳐도 표가 안 따라온다.

⚠ **상태 판정 기준은 「사유가 잘 붙었는가」가 아니라 「사용자가 쓸 값이 있는가」다.**
   「미생성 + 사유」로 9칸이 다 찬 문서는 **산출물 성적으로는 전부 미확보**다(§4 보고 규약).
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
sys.path.insert(0, ROOT)
import runpath                                           # noqa: E402
sys.path.insert(0, ROOT)

import fillaxis as _fx                                             # noqa: E402


def _run(mod: list, *args) -> dict:
    """자식 파이썬을 **UTF-8 로 띄운다.**

    ⚠ Windows 지뢰(판 ㉛ 실측). `-X utf8` 은 **부모에만** 걸리고 자식은 물려받지 않는다.
    그래서 자식의 표준출력이 CP949 가 되고, 판정 JSON 은 한글이라 그 자리에서
    `UnicodeEncodeError` 로 죽는다(rc=1). 게다가 부모가 stderr 를 utf-8 로 읽으려다
    reader 스레드까지 죽어 **`out.stderr` 가 `None` 이 되고**, 그러면 사유를 적으려던
    `raise` 문이 `TypeError` 로 바뀐다 — **실패가 엉뚱한 예외로 둔갑한다.**
    `-X utf8` 을 자식에게 넘기고, 디코딩 실패는 값을 잃되 보고는 되게 `replace` 로 둔다.
    """
    out = subprocess.run([sys.executable, "-X", "utf8"] + mod + list(args),
                         cwd=ROOT, capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    if out.returncode != 0:
        raise SystemExit(f"{mod} 실패:\n{(out.stderr or '(stderr 없음)')[-1200:]}")
    return json.loads(out.stdout)


def ledger_rows(run: str) -> list:
    rows = []
    for ln in io.open(os.path.join(runpath.read_dir(run), "run.jsonl"), encoding="utf-8"):
        o = json.loads(ln)
        if o.get("node") == "a4_ledger":
            p = o["payload"]
            rows += p.get("rows") or ([p] if "label" in p else [])
    return rows


def fact_values(run: str) -> dict:
    """`{fact_id: value_num}`. **원장 `a4_facts` 노드에서 읽는다.**

    ⚠ 예전에는 `result.json["facts"]` 를 봤는데 **그 키는 존재한 적이 없다.** 그래서
    정량 필터가 항상 빈 dict 를 조회했고, 아무것도 안 잡히니 `... or True` 로 무력화됐다
    — 필터가 상시 참이라 「정량」이 곧 「전체 PAIN」이었다(판 ㉛ 실측).
    """
    out = {}
    for ln in io.open(os.path.join(runpath.read_dir(run), "run.jsonl"), encoding="utf-8"):
        o = json.loads(ln)
        if o.get("node") == "a4_facts":
            p = o["payload"]
            out[p.get("fact_id")] = p.get("value_num")
    return out


def slots_of(run: str) -> dict:
    res = json.load(io.open(os.path.join(runpath.read_dir(run), "result.json"), encoding="utf-8"))
    return {s["slot_id"]: s for s in (res.get("input") or {}).get("slots") or []}, res


def 상태(n: int, 채워짐: int, 부분: int = 0) -> str:
    if n >= 채워짐:
        return "채워짐"
    return "부분" if (부분 and n >= 부분) else "미확보"


def build(run: str, concept: str, verdict: dict | None = None) -> dict:
    """`verdict` 를 주면 **서브프로세스를 띄우지 않는다**(`bm_adapter.build_from` 과 같은 수).

    서버(async 핸들러)는 이미 판정을 메모리에 들고 있고, 거기서 인터프리터를 또 띄우면
    `SystemExit`(= `BaseException`)을 못 잡는다. **판정을 두 번 계산하지도 않는다** —
    같은 물음을 두 곳이 각자 풀면 갈라진다(실측 6회).
    """
    fill = _fx.load_fill()
    항목 = fill["항목"]
    slots, res = slots_of(run)
    rows = ledger_rows(run)
    facts = fact_values(run)
    v = verdict if verdict is not None else _run(
        ["service/verdict.py", run, "--concept", concept, "--json"])
    m = v.get("시장_추정") or {}

    #: **성적표는 판정 층이 보는 것을 그대로 비춘다** — 자기 축을 따로 갖지 않는다.
    #  여기에 `r["채택"]` 을 박아 두면 토글을 옛 축으로 되돌려도 표가 안 움직여
    #  before/after 가 **아무것도 못 재게 된다**(판 ㉙ 에서 실제로 한 번 그랬다).
    #  같은 물음을 두 곳이 각자 풀면 두 번 갈라진다 — 실측 6회.
    적 = [r for r in rows if _fx.filled(r, "verdict._confirmed")]

    def by_claim(*ct):
        return [r for r in 적 if (slots.get(r["slot_id"]) or {}).get("claim_type") in ct]

    out = {}

    # ① 시장 크기 — TAM 밑동 관측
    base = by_claim("TAM", "SAM")
    out["1_시장크기"] = {"n": len(base), "상태": 상태(len(base), 항목["1_시장크기"]["채워짐"]),
                      "값": (m.get("TAM_추정") or {}).get("값"),
                      "등급": sorted({r.get("등급") for r in base}) or None}

    # ② 성장률 — 2년치 · 직접률 · proxy 선언 중 하나
    g = m.get("성장률_추정") or {}
    갈래 = ("2년치" if len(g.get("입력") or {}) >= 2 else
           ("직접률" if g.get("값") is not None else None))
    #: 문턱은 **규칙 파일에서만** 온다(파일 머리 규약). 예전엔 이 칸과 `6_계산` 두 곳만
    #  코드 안 조건문이라 규칙을 고쳐도 표가 안 따라왔다.
    갈래_ok = 항목["2_성장률"].get("채워짐_갈래") or 항목["2_성장률"].get("갈래") or []
    out["2_성장률"] = {"갈래": 갈래, "값_퍼센트": g.get("값_퍼센트"),
                    "상태": "채워짐" if (갈래 in 갈래_ok and g.get("값") is not None)
                          else "미확보"}

    # ③ 경쟁사 — 실명 + URL 몇 곳
    comp = by_claim("COMP", "COMPARABLE")
    도메인 = sorted({(r.get("url") or "").split("/")[2] for r in comp if r.get("url")})
    out["3_경쟁사"] = {"n_url": len(도메인), "도메인": 도메인,
                    "상태": 상태(len(도메인), 항목["3_경쟁사"]["채워짐"], 항목["3_경쟁사"]["부분"])}

    # ④ 가격 — 표시가격 몇 건이면 밴드
    price = by_claim("PRICE", "ALT")
    out["4_가격"] = {"n": len(price),
                   "상태": 상태(len(price), 항목["4_가격"]["채워짐"], 항목["4_가격"]["부분"])}

    # ⑤ 수요 — 정량 1건 or 정성 2건
    pain = by_claim("PAIN")
    # 정량 = **수치가 있는** 관측. 정성 = 나머지(인용은 있으나 값이 없는 것).
    # 둘의 문턱이 다르다 — 규칙 파일이 「정량 1건 or 정성 2건」이라고 적어 두었고,
    # 여태 그 or 의 오른쪽은 도달 불가였다.
    정량 = [r for r in pain if facts.get(r["fact_id"]) is not None]
    정성 = [r for r in pain if facts.get(r["fact_id"]) is None]
    out["5_수요"] = {"n": len(pain), "n_정량": len(정량), "n_정성": len(정성),
                   "최고_등급": (sorted({r.get("등급") for r in pain},
                                    key=lambda x: ["추정", "실무 신뢰", "확정"].index(x)
                                    if x in ("추정", "실무 신뢰", "확정") else -1)[-1]
                              if pain else None),
                   "상태": ("채워짐"
                          if (len(정량) >= 항목["5_수요"]["정량_채워짐"]
                              or len(정성) >= 항목["5_수요"]["정성_채워짐"])
                          else ("부분" if pain else "미확보"))}

    # ⑥ 계산 — TAM 산출 + 가정 명시
    # ⚠ 가정은 **요인 표에서 센다**(`assumption_count`). 문장 수를 세면 한 요인이 세
    # 문장이던 시절의 수(6)와 실제 가정 수(4)가 갈라진다 — 실제로 갈라져 있었다.
    # `가정` 문장 폴백은 요인이 없는 옛 판정 출력용이다.
    tam = m.get("TAM_추정") or {}
    가정수 = tam.get("assumption_count")
    if 가정수 is None:
        가정수 = len(tam.get("가정") or [])
    가정_필수 = bool(항목["6_계산"].get("가정_명시_필수"))
    out["6_계산"] = {"TAM": tam.get("값"), "가정수": 가정수,
                   "상태": ("미확보" if tam.get("값") is None
                          else ("채워짐" if (가정수 or not 가정_필수) else "부분"))}

    # ⑦ 못 찾은 것 — **항상**
    nf = (res.get("report") or {}).get("not_found") or {}
    out["7_못찾은것"] = {"건수": {k: v for k, v in nf.items() if v}, "상태": "—"}
    return {"run": run, "채택_행": len(적), "전체_행": len(rows), "과목": out}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--concept", required=True)
    ap.add_argument("--label", default="")
    a = ap.parse_args()
    r = build(a.run, a.concept)
    q = r["과목"]
    tally = {"채워짐": 0, "부분": 0, "미확보": 0}
    print(f"\n[{a.label or a.run}] 채택 {r['채택_행']}/{r['전체_행']}행")
    for k in ("1_시장크기", "2_성장률", "3_경쟁사", "4_가격", "5_수요", "6_계산", "7_못찾은것"):
        st = q[k]["상태"]
        tally[st] = tally.get(st, 0) + (1 if st in tally else 0)
        detail = {x: y for x, y in q[k].items() if x != "상태"}
        print(f"  {k:<12}{st:<6}{json.dumps(detail, ensure_ascii=False)[:110]}")
    print(f"  → 6과목 중 채워짐 {tally['채워짐']} · 부분 {tally['부분']} · 미확보 {tally['미확보']}"
          f"  (⑦은 상태 없음 — 항상 낸다)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
