# -*- coding: utf-8 -*-
"""**한 문서에 한 절만 묻는다** — 「한 호출은 한 주제만 긁는다」 가설의 최소 시험. (판 ㊵)

    python tools/focus_probe.py 0c54ffb5-... --url 000725 --section PRICE --id p40-focus

판 ㊵ 실측: 오뚜기 사업보고서 한 건에 **절 메뉴 일곱을 다 보여 주고** 「전부 뽑아라」고 했더니
17건이 나왔는데 **전부 매출액이고 전부 `MARKET_SIZE`** 였다. 같은 문서 안의 가격 표
(냉동식품 6,513원)·매출처별 채널 비중 5행에 손도 안 댔다.

그래서 묻는다 — **절을 하나만 지정하면 그 표를 보는가.**
LLM **1회**. 이 한 번이 이 판의 원인 진단을 확정하거나 뒤집는다.

⚠ **이것은 폐기된 「색인」이 아니다.** 색인은 문서에 딱지를 붙여 **일부 문서만 열자**는
것이었고, 이것은 **모든 문서를 절마다 다시 묻자**는 것이다. 버리는 곳이 없다.
⚠ 컨셉값은 넣지 않는다 (절대규칙 6 — 수집 프롬프트에 가격 가설 금지).
"""
from __future__ import annotations

import argparse, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import prompts
from base import load_env_key
from runlog import Meter, Run, load_rules
import read_sections as RS

#: 판 ㊾ 에서 `gpt-4o-mini` → `gpt-5.6-luna`. 온도를 안 넘기므로 상수 한 줄이면 끝이다.
MODEL = "gpt-5.6-luna"
JSON_OBJ = re.compile(r"\{.*\}", re.S)
FIELDS = ("quote", "number_raw", "unit_raw", "year", "subject", "table_context")

FOCUS = """아래는 시장조사를 위해 수집한 문서 **한 건**이다.

{document}

이 문서에서 **「{label}」에 관한 수치·사실만** 뽑아라. 다른 것은 뽑지 마라.

규칙:
1. **개수 제한이 없다.** 이 문서가 「{label}」에 대해 말하는 것을 **남김없이** 뽑아라.
   ⚠ 문서 앞쪽만 보고 끝내지 마라. **문서 전체를 훑어라.** 뒤쪽 표에 있을 수 있다.
2. **표를 특히 눈여겨보라.** 표 한 행이 그대로 하나의 사실이다.
3. quote 는 문서에 있는 문장·표 행을 **한 글자도 바꾸지 말고** 그대로. 기계로 대조한다.
4. number_raw · unit_raw 는 원문 표기 그대로. 계산·환산·반올림 금지. 범위는 쪼개지 마라.
   ⚠ **한 표 행에 값이 둘 이상이면 각각을 따로 낸다.**
   예) `| 대형마트 | 1,140,941 | 31.05% |` → **금액 항목 하나 + 비중 항목 하나**.
   금액만 내고 비중을 버리면 「어디서 팔리나」의 답이 사라진다.
5. **subject 는 「이 수가 무엇의 수인가」를 문서의 말 그대로.** 길어도 좋다.
6. table_context 에는 **표 제목과 행·열 이름**을 적는다.
7. 이 문서에 「{label}」에 대한 것이 정말 없으면 not_found 로 하고 한 줄. 빈 결과도 정답이다.

JSON 만 출력:
{{"status":"found","facts":[{{"quote":"…","number_raw":"…","unit_raw":"…","year":"…","subject":"…","table_context":"…"}}]}}
"""

#: ⚠ **라벨에 예시를 반드시 붙인다.** 판 ㊶ 1차 시범 실측: 예시가 붙은 `PRICE` 는 17건을
#: 뽑았고, 예시 없는 `UNIT_ECONOMICS`(「한 개 팔면 얼마 남는가」)·`REGULATION`
#: (「팔기 전에 지켜야 할 것」)은 **둘 다 0건**이었다. 그런데 같은 문서 60,000자 안에
#: 가동률 표(pos 41,506 — 99.00%·98.97%·99.77%·97.15%)와 HACCP(pos 5,175)이 **실재했다.**
#: 모델은 추상어를 자기 문서의 말과 잇지 못한다. **문서에 실제로 쓰이는 낱말을 준다.**
LABEL = {
    "MARKET_SIZE": "시장 크기 — 이 시장이 얼마나 큰가 "
                   "(시장 규모·판매액·거래액·출하액·성장률·점유율)",
    "PRICE": "가격 — 제품이 얼마에 팔리는가 (판매단가·가격대·가격 변동·물가지수)",
    "COMPETITOR": "경쟁 — 어느 회사가 무엇을 얼마나 하는가 "
                  "(회사별 매출·점유율·제품 수·브랜드·설비·신제품)",
    "CHANNEL": "채널 — 어디서 팔리는가 "
               "(매출처별 판매비중·유통 경로·대형마트/편의점/온라인/대리점 비중·"
               "입점 조건·수수료·물류·배송)",
    "DEMAND": "수요 — 누가 왜 사는가 (구매 이유·이용 빈도·소비 지출·가구 통계·불만·선호)",
    "UNIT_ECONOMICS": "원가와 수익성 — 한 개 팔면 얼마 남는가 "
                      "(영업이익률·순이익률·매출원가·원재료 가격·매입 단가·"
                      "생산능력·가동률·물류비·수수료율·손익)",
    "REGULATION": "규제와 기준 — 팔기 전에 지켜야 할 것 "
                  "(인증·HACCP·표시 의무·영양표시·위생 기준·유통 온도·미생물 규격·"
                  "포장재 기준·식품공전·법령·허가)",
}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("source_run")
    ap.add_argument("--url", required=True, help="문서 URL 의 일부 (부분 일치)")
    ap.add_argument("--section", required=True, choices=list(LABEL))
    ap.add_argument("--id", required=True)
    ap.add_argument("--cap", type=int, default=60000)
    ap.add_argument("--expect", default="", help="이 문자열이 산출에 있는지 찍는다 (사전 등록한 지표)")
    a = ap.parse_args()

    docs = [d for d in RS._corpus(a.source_run) if a.url in (d["url"] or "")]
    if not docs:
        print(f"URL 에 «{a.url}» 이 든 문서가 없다")
        return 1
    d = docs[0]
    body = d["text"][:a.cap]
    print(f"문서 {d['글자']:,}자 → 보낼 {len(body):,}자 · 절 {a.section}\n{d['url'][:80]}")

    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI
    run = Run(a.id, rules=load_rules())
    meter = Meter(OpenAI(), run)
    r = meter.create("a3_focus", model=MODEL,
                     input=prompts.render(FOCUS, label=LABEL[a.section],
                                          document=f"[문서] {d['title'] or d['url']}\n{body}"))
    m = JSON_OBJ.search(getattr(r, "output_text", "") or "")
    data = json.loads(m.group(0)) if m else {}
    hay = RS._norm(body)
    facts = []
    for f in data.get("facts") or []:
        if not isinstance(f, dict):
            continue
        it = {k: str(f.get(k) or "") for k in FIELDS}
        it["quote_verified"] = bool(it["quote"]) and RS._norm(it["quote"]) in hay
        facts.append(it)

    ok = sum(1 for f in facts if f["quote_verified"])
    print(f"\n상태 {data.get('status')} · 사실 {len(facts)}건 · 인용 대조 통과 {ok}")
    for f in facts:
        print(f"  {f['number_raw']!r:<16}{f['unit_raw']!r:<10}{f['subject'][:46]:<48}"
              f"{'O' if f['quote_verified'] else 'X'}  tbl={f['table_context'][:30]!r}")
    if a.expect:
        있 = any(a.expect in (f["number_raw"] + f["quote"]) for f in facts)
        print(f"\n**사전 등록 지표 «{a.expect}» — {'나왔다' if 있 else '안 나왔다'}**")

    out = {"source_run": a.source_run, "url": d["url"], "section": a.section,
           "보낸_글자": len(body), "facts": facts}
    path = os.path.join(run.dir, "focus.json")
    io.open(path, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))
    print(f"기록: {path}")
    c = run.counters
    돈 = c.get("llm.tokens_in", 0) / 1e6 * 0.15 + c.get("llm.tokens_out", 0) / 1e6 * 0.60
    print(f"LLM {c.get('llm.calls', 0):.0f}회 · in {c.get('llm.tokens_in', 0):,.0f} "
          f"out {c.get('llm.tokens_out', 0):,.0f} · ≈{돈 * 1390:.0f}원")
    return 0


if __name__ == "__main__":
    sys.exit(main())
