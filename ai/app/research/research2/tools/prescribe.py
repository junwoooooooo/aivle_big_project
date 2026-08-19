# -*- coding: utf-8 -*-
"""**8절 처방** — 「무엇을 못 구했나 / 왜 필요한가 / 어디서 구하나」. LLM 0회 · 0원. (판 ㊷ 5단계)

    python tools/prescribe.py runs-generated/p42-gate/publish.json \
           --concept data/concept_hmr-product.json

원본 사람 보고서 8절은 3열이고 **「어디서」가 처방**이다. 「못 구했다」로 끝내면 사업가는
거기서 멈추지만, 「식품공전 즉석섭취·편의식품류 규격을 보라」까지 적히면 10분에 끝난다.

**무엇을 못 구했는지는 원장이 이미 안다** — 셈으로 나오는 것을 모델에게 묻지 않는다.
갈래와 문구만 `rules/prescribe.v1.json` 에 있고, 어느 절이 걸리는지는 여기서 센다.

성공 판정 ③ **「인터뷰에서 무엇을 물을까」** 는 이 표의 `INTERVIEW` 줄이 답한다.
"""
from __future__ import annotations

import argparse, io, json, os, sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE):
    sys.path.insert(0, p)

import publish_gate as PG          # ⚠ 절 배정 규칙의 정본은 PG.절() 하나다

SECTION_말 = {
    "MARKET_SIZE": "시장 크기", "PRICE": "가격의 자리", "COMPETITOR": "경쟁 지형",
    "CHANNEL": "채널", "DEMAND": "수요", "UNIT_ECONOMICS": "원가·수익성", "REGULATION": "규제",
}


def _센다(d: dict) -> tuple:
    """절별 (실린 수, 탈락 사유 Counter)."""
    실림, 사유 = Counter(), {}
    for r in d["문서별"]:
        for it in r.get("items", []):
            if not it.get("게재"):
                continue
            sec = PG.절(it)
            if PG.머리인가(it):
                실림[sec] += 1
            else:
                사유.setdefault(it["section"], Counter())[it["게재_사유"].split("(")[0]] += 1
    return 실림, 사유


def build(d: dict, c: dict, J: dict | None = None) -> list:
    """8절 처방 **줄 목록**을 낸다. 판 ㊸ 1단계에서 `main()` 밖으로 꺼냈다.

    `J` 는 `judge_lines.build()` 의 산출이다. CLI 는 옆의 `judgments.json` 을 읽어 넣고,
    제품 경로는 방금 만든 것을 그대로 넘긴다 — **파일을 거치지 않는다.**
    """
    P = json.load(io.open(os.path.join(ROOT, "rules", "prescribe.v1.json"), encoding="utf-8"))
    실림, 사유 = _센다(d)
    문턱 = P["빈약_문턱"]

    행 = []
    for code, 말 in SECTION_말.items():
        n = 실림.get(code, 0)
        if n >= 문턱:
            continue
        s = P["절별_처방"][code]
        갈 = P["갈래"][s["갈래"]]
        # **왜 비었는지를 원장에서 말한다.** 「못 구했다」와 「구했는데 값이 없다」는 다른 병이다.
        c2 = 사유.get(code, Counter())
        무값 = c2.get("값이 없다", 0) + c2.get("값 자리에 숫자가 없다", 0)
        전체 = sum(c2.values())
        진단 = (f"이름은 {전체}건 잡혔는데 **그중 {무값}건이 값 자리에 숫자가 없다** — "
                f"지켜야 할 기준치가 하나도 안 잡혔다"
                if 전체 and 무값 / 전체 >= 0.5 else
                f"검토 {전체}건 중 실린 것 {n}건" if 전체 else "후보 자체가 안 잡혔다")
        행.append({"절": code, "절말": 말, "실림": n, "진단": 진단,
                   "갈래": s["갈래"], "갈래말": 갈["말"], "왜": 갈["왜"], "어디서": s["어디서"]})

    # ── 반쪽만 찬 절 — **채워졌다고 다 답한 것이 아니다** ──────
    # 채널 절은 「어디서 팔리나」에 답하지만 사업가의 물음은 「**어디부터 열까**」다.
    # 비중은 잡혔는데 **입점 조건·수수료가 없으면 결정을 못 한다.** 실린 수가 문턱을
    # 넘었다고 이 절이 처방 목록에서 사라지면, 못 채운 반쪽이 보이지 않는다.
    조건어 = ("수수료", "입점", "계약", "마진", "매대", "리베이트")
    if 실림.get("CHANNEL", 0) >= 문턱:
        있 = [it for r in d["문서별"] for it in r.get("items", [])
              if PG.머리인가(it)
              and any(w in str(it.get("subject") or "") for w in 조건어)]
        진단 = ("채널별 비중은 잡혔으나 **입점 조건·수수료가 없다** — 「어디서 팔리나」에는 "
                "답하고 「**어디부터 열까**」에는 못 답한다")
        if 있:
            진단 += f". 다만 {len(있)}건은 확보돼 있다(예: {str(있[0].get('subject'))[:30]})"
        s = P["절별_처방"]["CHANNEL"]
        행.append({"절": "CHANNEL", "절말": "채널", "실림": None, "진단": 진단,
                   "갈래": s["갈래"], "갈래말": P["갈래"][s["갈래"]]["말"],
                   "왜": P["갈래"][s["갈래"]]["왜"], "어디서": s["어디서"]})

    # ── 판단이 침묵한 자리도 처방 대상이다 ──
    if J:
        for g in (J.get("가격") or {}).get("갈래", []):
            if not g.get("문장"):
                행.append({"절": "PRICE", "절말": "가격의 자리", "실림": None,
                           "진단": g.get("왜_못_쓰나", ""), "갈래": "REACHABLE",
                           "갈래말": P["갈래"]["REACHABLE"]["말"],
                           "왜": P["갈래"]["REACHABLE"]["왜"],
                           "어디서": "같은 절에 실린 사실 중 짝이 되는 값을 찾거나, 그 값 하나만 다시 묻는다"})
        if (J.get("가격") or {}).get("결론"):
            행.append({"절": "PRICE", "절말": "가격의 자리", "실림": None,
                       "진단": "가격 지형은 잡혔으나 **어느 쪽으로 팔지**는 값으로 안 갈린다",
                       "갈래": "INTERVIEW", "갈래말": P["갈래"]["INTERVIEW"]["말"],
                       "왜": P["갈래"]["INTERVIEW"]["왜"],
                       "어디서": "「이 값이면 편의점 도시락 대신 살 것인가 · 배달 대신 살 것인가」를 묻는다"})

    # ── 컨셉이 스스로 「지어낸 값」이라 표시한 것 ──
    h = (c.get("_hypotheses_v2") or {})
    som = h.get("9_SOM_초기점유") or {}
    if som.get("_지어낸_값_표시"):
        행.append({"절": "DEMAND", "절말": "수요", "실림": None,
                   "진단": f"침투율 {som.get('가정_침투율')} 은 **컨셉이 스스로 「관측 근거가 없는 "
                          f"순수 가정」이라 적어 둔 값**이다. 이 조사는 그것을 확인하지 못했다",
                   "갈래": "INTERVIEW", "갈래말": P["갈래"]["INTERVIEW"]["말"],
                   "왜": P["갈래"]["INTERVIEW"]["왜"],
                   "어디서": "구매 의향을 직접 묻는다. ⚠ 의향은 실제 구매가 아니다 — 언급 수로만 읽는다"})

    return 행


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("publish")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--out", default="")
    a = ap.parse_args()

    d = json.load(io.open(a.publish, encoding="utf-8"))
    c = json.load(io.open(a.concept, encoding="utf-8"))
    P = json.load(io.open(os.path.join(ROOT, "rules", "prescribe.v1.json"), encoding="utf-8"))
    문턱 = P["빈약_문턱"]

    jp = os.path.join(os.path.dirname(a.publish), "judgments.json")
    J = json.load(io.open(jp, encoding="utf-8")) if os.path.exists(jp) else None
    행 = build(d, c, J)

    print(f"8절 처방 — {len(행)}줄 (실린 것이 {문턱}건 미만인 절 + 판단이 침묵한 자리)\n")
    for x in 행:
        n = "—" if x["실림"] is None else f"{x['실림']}건"
        print(f"■ {x['절말']} ({n})  →  **{x['갈래말']}**")
        print(f"   무엇: {x['진단']}")
        print(f"   왜  : {x['왜']}")
        print(f"   어디서: {x['어디서']}\n")

    갈 = Counter(x["갈래"] for x in 행)
    print("갈래별:", " · ".join(f"{P['갈래'][k]['말']} {v}" for k, v in 갈.most_common()))
    질문 = [x for x in 행 if x["갈래"] == "INTERVIEW"]
    print(f"\n→ **시장 인터뷰가 받을 질문 {len(질문)}개** (성공 판정 ③)")
    for x in 질문:
        print(f"   · {x['어디서']}")

    out = a.out or os.path.join(os.path.dirname(a.publish), "prescribe.json")
    io.open(out, "w", encoding="utf-8").write(json.dumps({"행": 행}, ensure_ascii=False, indent=1))
    print(f"\n기록: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
