# -*- coding: utf-8 -*-
"""**문서에서 9절에 해당하는 «문단»을 원문 그대로 떼어 온다.** (판 ㊺ 2단계)

    python tools/read_passages.py 0c54ffb5-b7bf-46b0-adc2-be284fed6acb --id p45-pass-01 --docs S8-q1-u2,S2-q1-u2
    python tools/read_passages.py <run> --id <id> --limit 10 --dry

## 왜 이것을 만드나 — 지금 문의 모양이 병이다

지금 재료를 만드는 문(`prompts.EXTRACT_SECTIONS`)이 요구하는 것은
**「수 + 단위 + 주어 + 인용, 일곱 칸 중 하나」**다. 그 문을 못 지나는 것이 셋이다.

1. **수가 안 붙은 사실.** 「개별급속냉동(IQF)으로…」는 수가 없어 들어올 문이 없다.
   실측(판 ㊺): 그 문서를 **다섯 번** 물었고 매번 답했는데 IQF 는 **0회** 나왔다
2. **한정어.** 「**만두류를 제외한** 냉동간편식 +46.5%」에서 그 한정어는
   `subject` 한 칸에서 죽는다. 그러면 다음 단계가 **다른 것을 같은 것으로 착각한다**
3. **빈손을 허락 안 한다.** 「수치를 전부 뽑아라」는 게임회사 증권신고서에도 20개를
   요구한다. 실측: 보고서 2절에 넥슨·크래프톤 매출, PA6 화학소재 가격, 자립준비청년
   정착금이 **41행**으로 실렸다 — 문이 빈손을 안 받아서 생긴 것이다

**문단째로 뜨면 셋이 한꺼번에 풀린다.** 그리고 인용 대조가 **결정적(LLM 0회)** 이 된다 —
원문 부분문자열이므로 대조가 참·거짓으로 딱 갈린다.

## ⚠ 이 도구가 «해결하지 않는» 것

- **2단 조판 PDF.** 원문 자체가 행 교차로 부서진 문서는 **부서진 채로 충실히** 실려 온다.
  대조는 통과하는데 사람이 못 읽는다. 그 길은 `read_sections._refetch_pdfs` 쪽이다
- **화면 근거 카드.** 문단은 「값 하나 + 단위 + 연도」가 아니라서 카드가 못 된다.
  수 뽑기는 **없애는 것이 아니라 뒤로 미루는 것**이다 — 좁아진 재료에서 다시 뽑는다

## 산출

`{"문서별": [{"trace_id", "url", "조회일", "절": {CODE: [{"문단", "그대로"}]}}]}`
`그대로` 는 **본문 부분문자열인가**를 기계로 잰 값이다. 거짓이면 그 문단은 **버리지 않고
표시만 한다** — 탈락률 자체가 이 판의 측정값이다.
"""
from __future__ import annotations

import argparse, concurrent.futures as cf, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "adapters"), HERE):
    if p not in sys.path:
        sys.path.insert(0, p)

import prompts
import runpath
from base import load_env_key
from read_sections import _corpus, _norm
from runlog import Meter, Run, call_options, load_rules
import read_sections as RS

MODEL = RS.MODEL
"""⚠ **추출과 같은 모델을 쓴다.** 다르면 「문 모양이 이겼는지 모델이 이겼는지」를 못 가른다.
판 ㊹ 이 집필 모델 교락으로 한 번 헤맨 자리다.

그래서 판 ㊾ 부터 값을 **베끼지 않고 `read_sections` 를 그대로 가리킨다** — 베껴 두면
저쪽만 바뀌었을 때 이 규칙이 조용히 깨진다(실제로 `service/summary.py` 가 그렇게 깨져 있었다)."""

WORKERS = 6
DOC_CAP = 60000
MAX_OUT = 8000
#: 한 절에서 뜰 문단 수 상한. **문서당이 아니라 절당**이다 — 빈손이 정답인 절이 대부분이라
#: 문서당 상한은 뜻이 없다(실측: 지금 문의 문서당 산출 평균이 4.5개다).
MAX_PER_SECTION = 6

SECTIONS = [
    ("MARKET_SIZE", "시장·카테고리의 규모와 성장률"),
    ("PRICE", "가격·판매단가·요금"),
    ("COMPETITOR", "누가 얼마나 팔고 있나 · 설비·공법·제품 로드맵"),
    ("CHANNEL", "어느 경로로 팔리나 · 수수료·입점 조건"),
    ("DEMAND", "사람들이 얼마나·왜 쓰나, 그리고 왜 안 쓰나"),
    ("UNIT_ECONOMICS", "원가·마진·수익성"),
    ("REGULATION", "지켜야 할 기준·인허가·의무"),
]

PROMPT = """아래는 시장조사로 수집한 문서 **한 건**이다.

이 문서에서 **아래 일곱 절에 해당하는 «문단»을 원문 그대로 떼어 와라.**

{sections}

{document}

규칙:
1. **한 글자도 바꾸지 마라.** 요약·환언·줄임 금지. 문서에 있는 글자를 **그대로 복사**한다.
   다음 단계가 이 문단이 문서 안에 실재하는지 **기계로** 대조하고, 다듬으면 그 자리에서 탈락한다.
1-1. ⚠ **본문이 부서져 보여도 고쳐 쓰지 마라.** 이 말뭉치의 PDF 는 2단 조판이 줄 단위로
   뒤섞여 있는 것이 있다. 읽기 힘들다고 **문장을 재조립하면 대조에서 탈락한다.**
   부서진 그대로 뜨고, 그 사실은 다음 단계가 따로 다룬다.
2. **뜻이 서는 만큼 뜬다.** 수 하나만 떼지 말고 **그 수가 무엇의 수인지 알 수 있는 만큼** 뜬다.
   예) 「만두류를 제외한 냉동간편식(냉동 후라이, 냉동핫도그 등)의 시장 규모는 동일 기간에
   4,812억 원에서 7,050억 원으로 46.5% 증가하였음」 — **「만두류를 제외한」을 빼면 다른 뜻이 된다.**
   길면 **문장 2~4개** 범위로 끊되, 끊은 구간은 **이어진 한 덩어리**여야 한다.
   ⚠ 떨어진 두 부분을 «…»로 이어 붙이면 대조에서 탈락한다.
2-1. ★ **줄글이 아니어도 뜬다.** 이 말뭉치의 상당수가 **표·개조식(◇ ● - 로 시작하는 항목)·
   사업보고서**다. 그런 문서에서는 **표의 «행 한 줄»**이나 **개조식 «항목 한 줄»**을 그대로 뜬다.
   앞뒤의 표 제목·열 이름 줄까지 붙여 뜨면 더 좋다.
   ⚠ **「줄글 문단이 없다」는 빈손의 이유가 아니다.** 실측(판 ㊺ 1차): 이 규칙이 없어서
   오뚜기 사업보고서·통계청 보도자료에서 **한 줄도 못 떴다.**
3. ★ **수가 없어도 뜬다.** 설비·공법·인증·의무화·규격·계약 조건처럼 **사업 결정에 조건을
   거는 사실**은 수가 없어도 값어치가 있다.
   예) 「개별급속냉동(IQF)으로 더 편리하게 즐기는 … 출시」는 경쟁사의 설비를 말한다.
   ⚠ 다만 **홍보 문구·수상 이력·회사 연혁·전망 의견**은 아니다. 「무엇을 갖췄나·무엇을
   지켜야 하나」이지 「우리는 훌륭하다」가 아니다.
4. ★★ **빈손이 정답일 때가 있다.** 이 문서가 그 절과 상관없으면 그 절은 **빈 배열**로 둔다.
   억지로 채우지 마라. ⚠ 이 규칙을 어기면 게임회사 증권신고서에서 「시장 크기」가 나온다.
   실제로 그랬다.
   **다만 빈손은 「이 문서가 다른 산업·다른 주제의 것일 때」다.** 우리 산업의 문서인데
   모양이 표거나 개조식이라서 비우는 것은 **틀렸다**(2-1 참조).
   그리고 **일곱 절이 전부 비는 것은 드물다** — 비우기 전에 2-1 을 한 번 더 읽어라.
5. 한 절에 최대 {max_per} 문단. 그 절에서 **가장 결정에 가까운 것**부터.
6. 절이 애매하면 **가장 가까운 하나**에만 넣는다. 같은 문단을 여러 절에 중복해 넣지 마라.

JSON 만 출력한다. 키는 절 코드, 값은 문단 문자열의 배열이다:
{skeleton}
"""


def _skeleton() -> str:
    """⚠ **빈 배열을 본보기로 주지 않는다.**

    1·2차 실측(판 ㊺): 틀을 `{"MARKET_SIZE": [], …}` 로 줬더니 10문서에서 문단 20 → 8개,
    출력 토큰 **1,015개**(상한 8,000)까지 떨어졌다. 규칙을 아무리 고쳐도 안 움직였다 —
    **모델이 규칙보다 «틀의 모양»을 따라간다.** 채워진 예를 준다.
    """
    return ('{"MARKET_SIZE": ["<이 절에 해당하는 원문 한 덩어리를 그대로>", '
            '"<또 있으면 또>"], "PRICE": ["<원문 그대로>"], '
            '"COMPETITOR": ["<원문 그대로>"], "CHANNEL": ["<원문 그대로>"], '
            '"DEMAND": ["<원문 그대로>"], "UNIT_ECONOMICS": ["<원문 그대로>"], '
            '"REGULATION": ["<원문 그대로>"]}')


def _sections_menu() -> str:
    return "\n".join(f"- {c} — {ask}" for c, ask in SECTIONS)


class _Doc:
    def __init__(self, d):
        self.url, self.title, self.text = d["url"], d["title"], d["text"]


def _one(d: dict, meter, cap: int) -> dict:
    """문서 하나. **예외를 올리지 않는다** — 하나가 죽어 전체가 죽으면 안 된다."""
    body = d["text"][:cap]
    out = {"trace_id": d["trace_id"], "url": d["url"], "글자": d["글자"],
           "조회일": d.get("조회일"), "보낸_글자": len(body), "별칭": d["별칭"]}
    try:
        r = meter.create("a3_passages", model=MODEL, **call_options(MODEL, MAX_OUT),
                         input=prompts.render(
                             PROMPT, sections=_sections_menu(),
                             max_per=MAX_PER_SECTION, skeleton=_skeleton(),
                             document=prompts.render_document(_Doc(d), cap)))
    except Exception as e:
        return {**out, "status": "llm_failed",
                "note": f"{type(e).__name__}: {str(e)[:160]}", "절": {}}

    txt = (getattr(r, "output_text", "") or "").strip()
    m = re.search(r"\{.*\}", txt, re.S)
    try:
        data = json.loads(m.group(0)) if m else {}
    except Exception:
        data = {}
    if not isinstance(data, dict):
        return {**out, "status": "bad_json", "note": txt[:200], "절": {}}

    hay = _norm(body)
    절: dict = {}
    총 = 참 = 0
    for code, _ in SECTIONS:
        vals = data.get(code) or []
        if not isinstance(vals, list):
            continue
        got = []
        for v in vals[:MAX_PER_SECTION]:
            s = str(v or "").strip()
            if len(s) < 10:
                continue
            # ⚠ **버리지 않고 «표시»한다.** 탈락률 자체가 이 판의 측정값이다 —
            #   조용히 버리면 「베끼기가 잘 됐다」와 「못 베낀 것을 감췄다」가 구분이 안 된다.
            그대로 = _norm(s) in hay
            got.append({"문단": s, "그대로": 그대로, "글자": len(s)})
            총 += 1
            참 += 1 if 그대로 else 0
        if got:
            절[code] = got
    return {**out, "status": "found" if 총 else "not_found",
            "note": "", "절": 절, "문단_총": 총, "문단_그대로": 참}


def build(source_run: str, run_id: str, *, cap: int = DOC_CAP, limit: int = 0,
          docs_only: tuple = ()) -> tuple:
    docs = _corpus(source_run)
    if docs_only:
        want = set(docs_only)
        docs = [d for d in docs if d["trace_id"] in want or want & set(d["별칭"])]
    if limit:
        docs = docs[:limit]
    보낼 = sum(min(d["글자"], cap) for d in docs)
    print(f"문서 {len(docs)}건 · 보낼 글자 {보낼:,} · 상한 {cap:,}자/문서")

    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI
    run = Run(run_id, rules=load_rules())
    meter = Meter(OpenAI(), run)

    res: list = [None] * len(docs)
    with cf.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futs = {pool.submit(_one, d, meter, cap): i for i, d in enumerate(docs)}
        for fu in cf.as_completed(futs):
            res[futs[fu]] = fu.result()

    총 = sum(r.get("문단_총") or 0 for r in res)
    참 = sum(r.get("문단_그대로") or 0 for r in res)
    글자 = sum(p["글자"] for r in res for v in (r.get("절") or {}).values() for p in v)
    절별: dict = {}
    for r in res:
        for c, v in (r.get("절") or {}).items():
            절별[c] = 절별.get(c, 0) + len(v)
    return {"source_run": source_run, "run_id": run_id, "cap": cap,
            "문서": len(docs), "보낸_글자": sum(r["보낸_글자"] for r in res),
            "상태": {s: sum(1 for r in res if r["status"] == s)
                   for s in sorted({r["status"] for r in res})},
            "문단_총": 총, "문단_그대로": 참,
            "그대로_비율": round(참 / 총, 3) if 총 else 0.0,
            "발췌_글자": 글자, "절별": 절별, "문서별": res}, run


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("source_run")
    ap.add_argument("--id", required=True)
    ap.add_argument("--cap", type=int, default=DOC_CAP)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--docs", default="", help="쉼표로 trace_id 지정")
    ap.add_argument("--dry", action="store_true")
    a = ap.parse_args()

    only = tuple(x.strip() for x in a.docs.split(",") if x.strip())
    if a.dry:
        docs = _corpus(a.source_run)
        if only:
            want = set(only)
            docs = [d for d in docs if d["trace_id"] in want or want & set(d["별칭"])]
        if a.limit:
            docs = docs[:a.limit]
        print(json.dumps({"문서": len(docs),
                          "보낼_글자": sum(min(d["글자"], a.cap) for d in docs),
                          "목록": [(d["trace_id"], d["글자"]) for d in docs]},
                         ensure_ascii=False, indent=1))
        return 0

    out, run = build(a.source_run, a.id, cap=a.cap, limit=a.limit, docs_only=only)
    path = os.path.join(runpath.GENERATED_RUNS_DIR, a.id + "-passages.json")
    io.open(path, "w", encoding="utf-8").write(
        json.dumps(out, ensure_ascii=False, indent=1))
    m = run.counters
    print(json.dumps({k: out[k] for k in
                      ("문서", "상태", "문단_총", "문단_그대로", "그대로_비율",
                       "발췌_글자", "절별")}, ensure_ascii=False, indent=1))
    print(f"LLM {m.get('llm.calls', 0):.0f}회 · in {m.get('llm.tokens_in', 0):,.0f} "
          f"· out {m.get('llm.tokens_out', 0):,.0f}\n→ {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
