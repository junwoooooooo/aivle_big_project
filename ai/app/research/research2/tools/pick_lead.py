# -*- coding: utf-8 -*-
"""**절마다 「먼저 볼 것」을 고른다.** 절 1개당 LLM 1회. (판 ㊺)

왜 있나
-------
이 엔진에는 **고르는 자리가 없었다.** 사실을 뽑고(A), 실을지 말지 판정하고(게재),
카드로 만들지만, **「이 절에서 사업가가 먼저 봐야 할 것」을 정하는 곳이 한 군데도 없다.**
그래서 줄 세우기를 등급(출처의 권위)이 대신했고, 그 결과가 이것이다 —

    1절 「시장 크기」 1등   출생아수 254,457명            (KOSIS → 등급 «확정»)
    그 절에 같이 있던 것   가정간편식 판매액 6조 8천억     (농경연 PDF → 등급 «추정»)

값도 출처도 인용도 **전부 참**이고, 모든 검사를 통과한다. 다만 **답이 아니다.**
이 시스템은 「이 수가 진짜인가」만 재고 「이 수가 이 질문에 답하는가」를 재지 않았다.

낱말표(`rules/publish.v1.json` 의 `절_표지`)로 대신해 봤고 **두더지 잡기였다** —
「비중」을 넣으면 채널 1등이 「1인가구 취업가구 비중」이 되고, 「이상」을 넣으면 규제 1등이
「토요일 10분 이상 책 읽은 비율」이 된다. 그리고 낱말을 맞춰도 **사업안이 바뀌면 처음부터**다.

목표 보고서(`docs/market-research-redesign/TARGET_REPORT.md`)가 나은 이유가 바로 이것이다 —
**사람(모델)이 문서를 읽고 골랐다.** 그 자리를 되돌려 놓는다.

무엇을 하지 «않나»
------------------
⚠ **버리지 않는다.** 고른 것을 앞으로 옮길 뿐이고, 나머지는 뒤에 그대로 남는다.
⚠ **등급·판정·경계를 만들지 않는다**(절대규칙 2). 순서만 돌려준다.
⚠ **후보에 없는 id 는 무시한다** — 모델이 지어낸 id 로 순서를 바꾸지 않는다.
⚠ 실패하면 **낱말표 순서를 그대로 쓴다.** 더 얻으려다 있는 것을 잃지 않는다.
"""
from __future__ import annotations

import argparse, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    if p not in sys.path:
        sys.path.insert(0, p)

from runlog import Meter, Run, call_options                        # noqa: E402

MODEL = "gpt-5.6-luna"
MAX_OUT = 8192

#: 절마다 앞세울 개수. 목표 보고서가 절을 여는 «큰 수 카드»가 셋이고, 표 머리가 그 뒤다.
LEAD = 6

#: 후보가 이보다 적으면 **부르지 않는다** — 고를 것이 없는데 돈을 쓰지 않는다.
MIN_CANDIDATES = 8

#: 한 번에 보여 줄 후보 상한. 넘으면 앞에서부터 자른다(낱말표가 이미 줄 세워 놨다).
MAX_CANDIDATES = 60

절_물음 = {
    "MARKET_SIZE": "이 시장은 얼마나 큰가 — 규모와 성장",
    "PRICE": "우리 가격이 어디에 서는가 — 비교 대상의 실제 가격",
    "COMPETITOR": "그 자리에 누가 있는가 — 경쟁자의 규모·점유·실적",
    "CHANNEL": "어디서 팔리는가 — 채널별 비중과 진입 조건",
    "DEMAND": "우리 고객이 실재하는가 — 수요의 크기와 이유",
    "UNIT_ECONOMICS": "한 개 팔면 얼마가 남는가 — 마진·원가",
    "REGULATION": "팔기 전에 무엇을 지켜야 하는가 — 기준과 의무",
}

_SYS = """너는 시장조사 보고서의 편집자다. 절마다 실린 사실 후보를 보고
**사업가가 그 절에서 «먼저» 봐야 할 것**을 고른다.

고르는 기준 — 순서대로
1. **그 절의 물음에 정면으로 답하는가.** 곁가지는 뒤로 민다
2. **이 사업의 시장에 대한 값인가.** 다른 산업·다른 업종의 수는 고르지 않는다
3. 같은 계량이 여러 해에 걸쳐 있으면 **연도 비교가 되게 둘까지** 함께 고른다
4. 출처의 권위보다 **내용의 적합성**이 앞선다 — 「확정」이어도 곁가지면 뒤다

⚠ 값을 만들거나 고치지 않는다. 등급·판정을 매기지 않는다. **후보의 id 만 고른다.**
⚠ 후보에 없는 id 를 쓰지 않는다.

출력은 JSON 하나:
{"고른것": [{"id": "sec-0012", "왜": "간편식 시장 자체의 판매액이다"}]}"""


def _후보(카드: list) -> list:
    out = []
    for c in 카드:
        out.append({
            "id": c.get("카드_id"),
            "계량": str(c.get("계량") or c.get("주제") or "")[:70],
            "값": str(c.get("_원문값") or "")[:24],
            "연도": c.get("기간"),
            "발행사": c.get("_발행사"),
            "등급": c.get("등급"),
        })
    return out


def _고르기(절: str, 후보: list, concept: dict, meter, run_id: str) -> list:
    사업 = " · ".join(str(concept.get(k) or "") for k in ("concept_name", "market", "problem")
                    if concept.get(k))
    물음 = 절_물음.get(절, 절)
    user = json.dumps({
        "사업안": 사업 or "(설명 없음)",
        "절": 절, "이 절의 물음": 물음,
        "몇 건 고르나": LEAD,
        "후보": 후보,
    }, ensure_ascii=False)
    r = meter.create("a6_pick", model=MODEL, **call_options(MODEL, MAX_OUT),
                     input=[{"role": "system", "content": _SYS},
                            {"role": "user", "content": user}])
    txt = getattr(r, "output_text", "") or ""
    i, j = txt.find("{"), txt.rfind("}")
    if i < 0 or j <= i:
        return []
    got = json.loads(txt[i:j + 1]).get("고른것") or []
    return [str(x.get("id")) for x in got if isinstance(x, dict) and x.get("id")]


def apply(카드: list, concept: dict, *, run_id: str = "pick", 진행=None) -> tuple:
    """절마다 앞세울 것을 골라 **순서만** 바꾼다.

    @return (새 카드 목록, 부른 횟수, 절별 고른 id)
    """
    from openai import OpenAI                                      # noqa: PLC0415
    run = Run(run_id)
    meter = Meter(OpenAI(), run)

    절별: dict = {}
    for c in 카드:
        # 서랍은 고르기 대상이 아니다 — 이미 「참고」로 접히는 것들이다.
        if c.get("_갈래") != "밖":
            절별.setdefault(c.get("_절"), []).append(c)

    고른: dict = {}
    부름 = 0
    for 절, 목록 in 절별.items():
        if len(목록) < MIN_CANDIDATES:
            continue
        try:
            ids = _고르기(절, _후보(목록[:MAX_CANDIDATES]), concept, meter, run_id)
            부름 += 1
        except Exception as error:                  # noqa: BLE001 — SOFT 다
            print(f"  고르기 실패 {절} — {type(error).__name__}: {str(error)[:80]}")
            continue
        있는 = {c.get("카드_id") for c in 목록}
        고른[절] = [i for i in ids if i in 있는][:LEAD]   # 지어낸 id 는 버린다
        if 진행:
            진행(절, len(고른[절]))

    if not 고른:
        return 카드, 부름, 고른

    # **앞으로 옮기기만 한다.** 나머지는 원래 차례 그대로 뒤에 남는다.
    순위 = {}
    for 절, ids in 고른.items():
        for n, i in enumerate(ids):
            순위[i] = n
    자리 = {id(c): n for n, c in enumerate(카드)}
    카드 = sorted(카드, key=lambda c: (순위.get(c.get("카드_id"), 10_000), 자리[id(c)]))
    return 카드, 부름, 고른


def main() -> int:
    ap = argparse.ArgumentParser(description="절마다 «먼저 볼 것»을 고른다 (절당 LLM 1회)")
    ap.add_argument("promoted", help="promote_cards 산출 json")
    ap.add_argument("--concept", required=True)
    a = ap.parse_args()
    카드 = (json.load(io.open(a.promoted, encoding="utf-8")).get("카드") or [])
    concept = json.load(io.open(a.concept, encoding="utf-8"))
    새, 부름, 고른 = apply(카드, concept, run_id="pick-cli",
                        진행=lambda s, n: print(f"  {s} → {n}건"))
    print(f"\n호출 {부름}회 · 절 {len(고른)}개")
    for 절, ids in 고른.items():
        머리 = [c for c in 새 if c.get("카드_id") in ids][:3]
        print(f"\n[{절}]")
        for c in 머리:
            print(f"   {str(c.get('계량'))[:34]:36}{c.get('_원문값')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
