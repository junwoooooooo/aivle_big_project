# -*- coding: utf-8 -*-
"""칸별 종합 요약 — **3번째 LLM 지점 `SUMMARY`** (판 ㉛).

    python service/summary.py <run_id> --concept data/concept_x.json --json

⚠ **절대 규칙 1 의 첫 확장이다.** 지금까지 LLM 은 A 블록(`SEARCH`·`EXTRACT`)에만 있었다.
확장을 안전하게 만드는 것은 호출을 줄이는 것이 아니라 **«무엇을 만들 수 없는가»를 못박는 것**이다:

    입력   근거 카드 목록**만**. 원장·본문·URL 에 접근하지 않는다
    출력   문장 + 그 문장이 가리키는 카드_id[]
    검사   문장↔카드 숫자 대조 · 해석 어휘 차단 · 카드 id 실재
    금지   숫자 · 등급 · 판정 · 카드 · 경계 문장의 삭제

**fail-closed**: 검사를 통과 못 하면 재시도 상한 안에서 다시 부르고, 소진되면
**요약 없이 카드만** 낸다. 요약은 있으면 좋은 것이지 값의 근거가 아니다 —
값과 등급은 이미 카드가 들고 있다.

지점 등재는 `rules/summary.v1.json llm_지점` 에 하네스·발췌와 **같은 형식**으로 있다.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "tools"))

import cards as CARDS                                              # 같은 서비스 층
import summary_check as CHECK                                      # 검사(LLM 0)

PROMPT = """너는 시장조사 결과를 **사실 그대로 요약**한다.

아래는 근거 카드다. **카드에 있는 것만** 쓸 수 있다.

{cards}

규칙 — 어기면 기계 검사가 잡아내고 요약은 버려진다:
1. **카드에 없는 숫자를 쓰지 마라.** 모든 수는 카드의 값에서 와야 한다.
2. **판단하지 마라.** 「유망하다」·「경쟁력 있다」·「충분하다」 같은 평가어 금지.
   너는 무엇이 관측됐는지만 적는다.
3. 계산값을 쓸 때는 **등급과 가정을 같이** 적어라(카드의 `등급`·`가정`).
4. 문장마다 **어느 카드에서 왔는지** id 를 붙여라.
5. **경계는 반드시 복사한다.** 카드에 `경계`·`경계_proxy`·`상한_울타리` 가 있으면
   **그 문구를 문장 안에 그대로 넣어라**(요약·의역 금지, 괄호로 붙여도 된다).
   예: "네이버 매출은 12조원이다(전사 매출 — 시장 매출 아님)."
   이것은 선택이 아니다 — 빠지면 기계 검사가 요약 전체를 버린다.
6. **그 수가 무엇인지는 `계량`·`주제` 가 말한다.** `칸` 이름을 계량으로 착각하지 마라 —
   예컨대 칸이 「GROWTH」라도 `계량` 이 「거래액」이면 그 수는 **거래액**이지 성장률이 아니다.

칸별로 1~2문장. JSON 으로만 답하라:
{{"요약": [{{"칸": "고객 세그먼트", "문장": "...", "카드_id": ["C-F001"]}}]}}
"""


#: 모델은 발췌(`tools/read_sections.py`)와 같은 것을 쓴다 — 지점마다 모델이 갈리면
#: 비용·품질 비교가 안 된다. 발췌가 판 ㊺ 에 루나로 갔고 여기가 안 따라가 **그 규칙이
#: 한동안 깨져 있었다**(판 ㊾ 에서 맞춤).
SUMMARY_MODEL = "gpt-5.6-luna"
#: **온도를 고정한다** (판 ㉜ ①). 요약은 창작이 아니라 카드 옮겨 적기이므로 흔들릴 이유가 없고,
#  흔들리면 「검사를 통과할 때까지 다시 부르기」가 **재시도가 아니라 뽑기**가 된다.
#  ⚠ 0 이 아니라 0.2 인 이유: 완전 고정은 한 번 막히면 **세 번 다 같은 문장**이 나와
#  재시도가 무의미해진다(판 ㉛ 에서 A 가 3회 연속 같은 자리에서 죽었다).
#
#  ⚠⚠ **추론 모델에는 이 값이 안 나간다** — `runlog.call_options` 가 뺀다(400 이라서).
#  그러면 위 「완전 고정」 걱정은 저절로 사라지지만(추론이 판마다 갈린다), **의도한 0.2 가
#  아니라 기본값 1.0 으로 도는 것**임을 알고 있어야 한다. 재시도가 뽑기가 되지 않게
#  막는 것은 이제 온도가 아니라 `summary_check` 의 검사다.
SUMMARY_TEMPERATURE = 0.2


def _call(prompt: str) -> tuple:
    """LLM 1회. **사용량을 돌려주되 원장에는 쓰지 않는다**(유리벽: 원장 쓰기 0).

    비용 기록은 이 층의 **산출물 안에** 남는다 — 원장을 건드리지 않으면서도
    「얼마 썼나」가 값으로 남아야 비용 표가 성립한다.
    """
    sys.path.insert(0, os.path.join(ROOT, "adapters"))
    from base import load_env_key                                  # noqa: E402
    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI                                      # noqa: E402
    from runlog import call_options                                # noqa: E402
    # ⚠ 온도는 `call_options` 가 정한다 — 추론 모델이면 빼야 400 이 안 난다.
    #   위 `SUMMARY_TEMPERATURE` 는 **추론 모델이 아닐 때만** 실제로 나간다.
    options = call_options(SUMMARY_MODEL)
    if "temperature" in options:
        options["temperature"] = SUMMARY_TEMPERATURE
    r = OpenAI().responses.create(model=SUMMARY_MODEL, input=prompt, **options)
    u = getattr(r, "usage", None)
    return (r.output_text or ""), {"in": getattr(u, "input_tokens", 0) or 0,
                                   "out": getattr(u, "output_tokens", 0) or 0}


def summarize(run: str, concept: str, max_retry: int = 3) -> dict:
    doc = CARDS.build(run, concept)
    cs = doc["카드"]
    if not cs:
        return {**doc, "요약": [], "_요약_없음": "카드 0장 — 요약할 관측이 없다"}

    rules = CHECK.rules()
    slim = [{k: v for k, v in c.items()
             if k in ("카드_id", "종류", "칸", "계량", "주제", "기간", "값", "단위",
                      "등급", "가정", "경계", "경계_proxy", "상한_울타리", "연도", "식")}
            for c in cs]

    attempts, usage = [], {"calls": 0, "in": 0, "out": 0}
    for n in range(1, max_retry + 1):
        txt, u = _call(PROMPT.format(cards=json.dumps(slim, ensure_ascii=False, indent=1)))
        usage["calls"] += 1
        usage["in"] += u["in"]
        usage["out"] += u["out"]
        try:
            j = txt[txt.index("{"):txt.rindex("}") + 1]
            got = json.loads(j).get("요약") or []
        except Exception as e:
            attempts.append({"시도": n, "결과": f"파싱 실패: {e}"})
            continue
        bad = CHECK.check(cs, got, rules)
        attempts.append({"시도": n, "문장": len(got), "위반": bad})
        if not bad:
            return {**doc, "요약": got, "_시도_기록": attempts, "_사용량": usage,
                    "_LLM_지점": "SUMMARY (rules/summary.v1.json llm_지점)",
                    "_검사": "문장↔카드 대조 통과 · 위반 0"}
    # ── fail-closed — **요약 없이 카드만** ─────────────────────────
    #   요약이 없다고 값이 없어지지 않는다. 값·등급·경계는 카드가 이미 들고 있다.
    #   여기서 「검사를 느슨하게 해서 통과시키기」를 하면 층 전체가 무의미해진다.
    return {**doc, "요약": [], "_시도_기록": attempts, "_사용량": usage,
            "_LLM_지점": "SUMMARY (rules/summary.v1.json llm_지점)",
            "_요약_없음": f"검사 미통과 {max_retry}회 — 요약을 버리고 카드만 낸다(fail-closed)"}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("run")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--out", default="")
    a = ap.parse_args()
    d = summarize(a.run, a.concept)
    if a.out:
        io.open(a.out, "w", encoding="utf-8").write(json.dumps(d, ensure_ascii=False, indent=1))
    if a.json:
        print(json.dumps(d, ensure_ascii=False, indent=1))
        return 0
    print(f"[{a.run}] 카드 {len(d['카드'])}장 · 요약 {len(d.get('요약') or [])}문장")
    for s in (d.get("요약") or []):
        print(f"  · [{s.get('칸')}] {s.get('문장')}\n      ← {s.get('카드_id')}")
    if d.get("_요약_없음"):
        print("  ⚠", d["_요약_없음"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
