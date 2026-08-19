# -*- coding: utf-8 -*-
"""**절마다 따로 묻기** — 문서 × 절 각각에 「이 절에 관한 것만」을 묻는다. (판 ㊶)

    # 시범 — 문서 10건 × 4절 (≈70원)
    python tools/reask_sections.py 0c54ffb5-... --id p41-pilot --limit 10 \
           --sections PRICE,CHANNEL,UNIT_ECONOMICS,REGULATION

    # 전 구간 (≈950원)
    python tools/reask_sections.py 0c54ffb5-... --id p41-full \
           --sections PRICE,CHANNEL,UNIT_ECONOMICS,REGULATION

    python tools/reask_sections.py ... --dry-run     # 호출 수·비용만 (LLM 0회)

**왜 이렇게 묻나** — 판 ㊵ 실측. 같은 문서·같은 60,000자·같은 모델에서 질문만 바꿨다:

| 질문의 폭 | 결과 |
|---|---|
| 슬롯 1개 (제품 방식) | 문서 141건 중 **120건 `not_found`** |
| 절 7개 메뉴 + 「전부 뽑아라」 | 17건 — **전부 매출액 · 전부 `MARKET_SIZE`** |
| 통짜 12만 자 | 3건 (더 나쁨) |
| **절 1개** | **가격 표 전 품목 ×3개년 — 6,513원 포함** |

**너무 좁으면 못 찾고, 너무 넓으면 한 주제만 긁는다.**

⚠ **폐기된 색인이 아니다.** 색인은 「일부 문서만 열자」였고 이것은 「**모든** 문서를
절마다 다시 묻자」다. **버리는 곳이 없다.**

⚠ **컨셉이 프롬프트에 안 들어간다** (절대규칙 6 — 수집에 가격 가설을 넣으면 자기확인 회로).

산출은 `read_sections.py` 의 `sections.json` **과 같은 모양**이다. 그래야
`publish_gate.py` · `render_sections.py` · `checklist.py` 가 그대로 먹는다.
같은 문서가 절마다 한 번씩 나오므로 `문서별` 은 **(문서 × 절)** 단위다.
"""
from __future__ import annotations

import argparse, concurrent.futures as cf, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, HERE, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import prompts
from base import load_env_key
from runlog import Meter, Run, load_rules
import read_sections as RS
from focus_probe import FOCUS, LABEL

#: ⚠ **`read_sections` 것을 그대로 쓴다. 여기 다시 적지 않는다.**
#: 이 저장소는 「같은 물음을 두 곳이 각자 푼다」로 **여섯 번** 갈렸다. 발췌 모델은
#: 읽기와 재질문이 **반드시 같아야** 「모델이 이겼나 질문이 이겼나」를 가릴 수 있다.
MODEL = RS.MODEL
#: 줄 수도 **읽기 것을 그대로 쓴다**(판 ㊺ 에서 6 → 12). 두 곳에 적으면 갈린다 —
#: 한쪽만 올리면 「어느 단계가 빨라졌나」를 못 가리고, 429 를 봐도 범인을 못 짚는다.
WORKERS = RS.WORKERS
JSON_OBJ = re.compile(r"\{.*\}", re.S)
FIELDS = ("quote", "number_raw", "unit_raw", "year", "subject", "table_context")

#: 단가도 모델을 따라간다. **박아 두면 모델을 바꾼 날 원장이 거짓 원가를 기록한다.**
#: (옛 실측: `p39-secFULL` 112회 · in 949,451 · out 47,793 → 238원 @ mini)
_IN, _OUT, _KRW = RS.PRICE_IN, RS.PRICE_OUT, 1390
_TOK_PER_CHAR = 0.610      # p40-focus 실측 (60,000자 → 36,626 토큰, 프롬프트 포함)
_OUT_PER_CALL = 1667 * 0.6  # p40-focus 실측을 보수적으로


#: 출력 상한을 **명시한다.** 판 ㊶ 2차 시범 실측: 오뚜기 문서의 `PRICE` 응답이
#: `{"status":"found","facts":[{…` 중간에서 **잘렸고**, 통째로 파싱 실패해 **11건이 0건이 됐다.**
#: 상한을 안 주면 모델이 스스로 짧게 끊고, 그 사실이 어디에도 안 남는다.
#:
#: ⚠ **판 ㊺ 에서 4배로 올렸다 — 추론 모델은 «생각한 토큰»도 이 상한에서 깎는다.**
#:   `gpt-5.6-luna` 는 `reasoning.effort` 를 가진 추론 모델이고, OpenAI 문서가
#:   「추론 토큰은 출력 토큰으로 친다」고 명시한다. 상한이 빠듯하면 **생각하다 끝나
#:   본문이 빈 채로 «성공»한다** — 실패보다 나쁜 자리다(조용하다).
#: ⚠ **읽기와 같은 값을 쓴다** — 두 곳에 적으면 갈린다.
MAX_OUT = RS.MAX_OUT
_OBJ = re.compile(r"\{[^{}]*\}")


def _parse(raw: str) -> tuple:
    """(data, 잘렸나). **잘려도 건질 것은 건진다** — 규칙 5(실패는 값이다)의 연장이다.

    통째로 버리면 「모델이 못 뽑았다」와 「우리가 못 읽었다」가 구별되지 않는다.
    """
    m = JSON_OBJ.search(raw)
    if m:
        try:
            return json.loads(m.group(0)), False
        except Exception:
            pass
    facts = []
    for om in _OBJ.finditer(raw):          # 완성된 객체만 건진다
        try:
            o = json.loads(om.group(0))
        except Exception:
            continue
        if isinstance(o, dict) and o.get("quote"):
            facts.append(o)
    if facts:
        return {"status": "found", "facts": facts}, True
    return {}, bool(raw.strip())


def _one(d: dict, code: str, meter, cap: int) -> dict:
    """문서 하나 × 절 하나. **예외를 올리지 않는다**(규칙 5 — 실패는 값이다)."""
    body = d["text"][:cap]
    # ⚠ **`조회일` 을 빠뜨리면 여기서 뽑은 사실이 화면에 한 건도 못 간다.**
    #   `promote_cards.py:62` 가 조회일 없는 행을 **「retrieved_at 없음」으로 승격 거부**한다.
    #   실측(판 ㊹ 1단계 배선 시범): 이 한 칸이 없어 사실 **24건이 통째로** 버려졌다 —
    #   추출은 성공하고 인용 대조도 통과했는데 **조용히** 사라졌다.
    #   `_corpus` 가 원장 `a3_document.retrieved_at` 에서 이미 걷어 놨다. **지어내지 않고 옮긴다.**
    out = {"trace_id": d["trace_id"], "url": d["url"], "글자": d["글자"],
           "조회일": d.get("조회일"),
           "보낸_글자": len(body), "별칭": d["별칭"], "물은_절": code}
    try:
        r = meter.create("a3_reask", model=MODEL, max_output_tokens=MAX_OUT,
                         input=prompts.render(
                             FOCUS, label=LABEL[code],
                             document=f"[문서] {d['title'] or d['url']}\n{body}"))
    except Exception as e:
        return {**out, "status": "llm_failed",
                "note": f"{type(e).__name__}: {str(e)[:160]}", "items": []}

    raw = getattr(r, "output_text", "") or ""
    data, 잘림 = _parse(raw)
    out["잘림"] = 잘림
    if (data.get("status") or "") != "found":
        # ⚠ **원문을 남긴다.** 판 ㊶ 1차 시범에서 `not_found` 7건이 나왔는데 사유가 전부
        # 「모델이 형식을 안 지켰다」였고, 그것이 **진짜 없어서인지 파싱 실패인지 가를 수
        # 없었다.** 사유를 못 가르면 다음 판이 엉뚱한 데를 판다(규칙 5 — 실패는 값이다).
        return {**out, "status": "not_found",
                "note": str(data.get("note") or "")[:200],
                "왜": ("모델이 not_found 를 냈다" if data.get("status") == "not_found"
                      else "JSON 을 못 읽었다"),
                "원문": raw[:2000], "items": []}

    hay = RS._norm(body)
    items = []
    for f in data.get("facts") or []:
        if not isinstance(f, dict):
            continue
        it = {k: str(f.get(k) or "") for k in FIELDS}
        # **절은 물어본 절이다.** 모델에게 절을 고르게 하지 않는다 — 그것이 판 ㊵ 의 병이었다.
        it["section"] = code
        it["quote_verified"] = bool(it["quote"]) and RS._norm(it["quote"]) in hay
        if not it["quote_verified"]:
            # **본문에서 진짜 문장을 되찾는다** (판 ㊹ 4단계 · 정본은 `RS.재정박`).
            다시 = RS.재정박(it.get("number_raw") or "", it.get("subject") or "", body,
                              it.get("unit_raw") or "")
            if 다시 and RS._norm(다시) in hay:
                it["quote"], it["quote_verified"] = 다시, True
                it["인용_되찾음"] = True
        it["section_valid"] = True
        it["채택"] = it["quote_verified"]
        it["탈락_사유"] = "" if it["채택"] else "인용이 본문에 없다"
        items.append(it)
    return {**out, "status": "found", "note": "", "items": items}


#: 제품 경로가 다시 묻는 절. **이미 넘치는 절은 넣지 않는다.** CLI 기본값과 같은 값이고
#: **여기가 정본**이다.
#:
#: ★ **판 ㊺ — 넷에서 하나로 줄였다.** 넷은 판 ㊶ 에서 `gpt-4o-mini` 로 잰 목록인데,
#: 발췌가 `gpt-5.6-luna` 가 된 뒤 **읽기 한 번만으로 이미 다 찬다.** 같은 원장
#: (`0c54ffb5…`, 문서 112건)을 읽기만/합침 두 벌로 갈라 재채점한 실측 — `tools/reask_ab.py`:
#:
#:     과목        읽기만   +재질문    상태
#:     채널          22건     88건    FILLED → FILLED
#:     원가·수익성    17건    133건    FILLED → FILLED
#:     규제           8건     64건    FILLED → FILLED
#:
#: 근거는 2,666 → 5,640 으로 **2배**가 됐는데 **성적표 상태·가격 판단(2갈래)·처방(3건)이
#: 하나도 안 바뀌었다.** 늘어난 것은 대부분 서랍행이다(가격 1,174 `밖` · 원가 869 `밖`).
#: 재질문 384회는 **결론을 하나도 안 바꾸고** 벽시계의 60%·지출의 70%를 먹고 있었다.
#:
#: ⚠ **왜 규제만 남기나.** 읽기만으로 채널 22·원가 17 인데 **규제는 8건**이고 문턱은 3이다.
#:   여유가 가장 얇아서, 다른 사업안에서 2건으로 떨어지면 그 절이 통째로 `MISSING` 이 된다.
#:   이 하나가 그 보험이고, 값은 112회(≈3분)다.
#: ⚠ **측정은 사업안 «한 개»(냉동 간편식) 위에서 했다.** 아예 끄는 판단은 다른 사업안에서
#:   한 번 더 재고 내린다 — 그것이 남은 숙제 A 다.
DEFAULT_SECTIONS = ("REGULATION",)


def build(source_run: str, run_id: str, *, sections=DEFAULT_SECTIONS,
          cap: int = 60000, limit: int = 0, pdf_refetch: bool = False,
          docs: list | None = None) -> tuple[dict, "Run"]:
    """**import 해서 쓰는 자리.** `read_sections.build` 와 같은 모양을 돌려준다.

    돌려주는 것: `(sections.json 과 같은 dict, Run)`.
    `Run.counters["llm.calls"]` 로 부른 횟수를 알 수 있다 — 제품이 예산에 청구하려면 필요하다.

    ⚠ **`limit` 은 «문서» 수다. 호출 수는 `limit × len(sections)` 이다.**
      제품 쪽에서 남은 예산을 그대로 넘기면 **절 수배로 초과한다.** 실측: 112문서 × 4절 = 448회.
    ⚠ **CLI 와 두 구현이 되면 안 된다** — `main()` 이 이 함수를 부른다.

    ## ★ `docs` — **읽기가 고친 본문을 그대로 물려받는 자리** (판 ㊺)

    `_refetch_pdfs` 는 **메모리 안에서만** 본문을 고친다(원장에 다시 쓰지 않는다).
    그래서 이 함수가 `RS._corpus` 를 **새로** 부르면 **옛 2단 조판 본문을 다시 읽는다.**

    ⚠ 실측 규모: 읽기 94회는 고친 본문, **재질문 376회는 옛 본문**이었다 — **지출의 80%다.**
      게다가 재질문이 겨냥하는 넷(`PRICE`·`CHANNEL`·`UNIT_ECONOMICS`·`REGULATION`)이
      **정부 PDF 비중이 가장 높은 절**이라, `pdf_refetch` 를 넣은 이유가 그대로 살아 있는
      자리에 안 들어간 꼴이었다.

    → 부르는 쪽이 **이미 고친 목록**을 넘기면 그것을 쓴다. 안 넘기면 `pdf_refetch` 로 여기서 고친다.
    """
    codes = [str(s).strip().upper() for s in sections if str(s).strip()]
    bad = [c for c in codes if c not in LABEL]
    if bad:
        raise ValueError(f"모르는 절 코드: {bad} (가능: {', '.join(LABEL)})")

    if docs is None:
        docs = RS._corpus(source_run)
        if pdf_refetch:
            docs = RS._refetch_pdfs(docs)
    if limit:
        docs = docs[:limit]
    return _run(docs, codes, source_run, run_id, cap)


def _run(docs: list, codes: list, source_run: str, run_id: str, cap: int,
         진행=None) -> tuple[dict, "Run"]:
    """문서×절을 실제로 부르는 **한 곳.** `build()` 와 `main()` 이 **둘 다 여기로** 온다.

    ⚠ 두 벌로 갈리면 「CLI 로는 되는데 제품에서는 다르다」가 생긴다 —
      이 저장소가 「같은 물음을 두 곳이 각자 푼다」로 여섯 번 겪은 것이다.
    """
    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI                                      # noqa: PLC0415
    run = Run(run_id, rules=load_rules())
    meter = Meter(OpenAI(), run)

    작업 = [(d, c) for d in docs for c in codes]
    res: list = [None] * len(작업)
    with cf.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futs = {pool.submit(_one, d, c, meter, cap): i for i, (d, c) in enumerate(작업)}
        done = 0
        for fu in cf.as_completed(futs):
            res[futs[fu]] = fu.result()
            done += 1
            if 진행 and done % 20 == 0:
                진행(done, len(작업))

    items = [it for r in res for it in r["items"]]
    ok = [it for it in items if it["채택"]]
    per: dict = {}
    for it in ok:
        per[it["section"]] = per.get(it["section"], 0) + 1

    out = {"source_run": source_run, "run_id": run_id, "cap": cap,
           "문서": len(docs), "물은_절": codes,
           "보낸_글자": sum(r["보낸_글자"] for r in res),
           "상태": {s: sum(1 for r in res if r["status"] == s)
                  for s in sorted({r["status"] for r in res})},
           "인용_총": len(items), "인용_채택": len(ok), "절별": per, "문서별": res}
    return out, run


def merge(base: dict, extra: dict) -> dict:
    """`read` 산출에 `reask` 산출을 **합친다.** 어느 쪽도 버리지 않는다.

    ⚠ **같은 문서가 양쪽에 나온다.** `read` 는 문서 1건당 1행, `reask` 는 (문서 × 절)당 1행이라
      `문서별` 을 그냥 이으면 문서 수가 부풀어 보인다 — 그래서 **`문서` 는 고유 trace_id 로 센다.**
    ⚠ **중복 사실을 여기서 지우지 않는다.** 같은 값이 양쪽에서 나오면 그것은 **교차 근거**이고,
      접기 단계에서 접을 일이지 읽기 단계에서 버릴 일이 아니다
      (「버리는 자리는 질문과 게재뿐」).
    """
    if not extra:
        return base
    행 = list(base.get("문서별") or []) + list(extra.get("문서별") or [])
    사실 = [it for r in 행 for it in (r.get("items") or [])]
    ok = [it for it in 사실 if it.get("채택")]
    per: dict = {}
    for it in ok:
        per[it["section"]] = per.get(it["section"], 0) + 1
    return {**base,
            "문서": len({r.get("trace_id") for r in 행}),
            "물은_절": list(extra.get("물은_절") or []),
            "보낸_글자": int(base.get("보낸_글자") or 0) + int(extra.get("보낸_글자") or 0),
            "인용_총": len(사실), "인용_채택": len(ok), "절별": per, "문서별": 행,
            "합침": {"read": int(base.get("인용_채택") or 0),
                   "reask": int(extra.get("인용_채택") or 0)}}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("source_run")
    ap.add_argument("--id", required=True, help="새 실행 id (원장을 덮지 않는다)")
    ap.add_argument("--sections", default="PRICE,CHANNEL,UNIT_ECONOMICS,REGULATION",
                    help="다시 물을 절. **이미 넘치는 절은 넣지 않는다**")
    ap.add_argument("--cap", type=int, default=60000)
    ap.add_argument("--limit", type=int, default=0, help="문서 N건만 (시범용)")
    ap.add_argument("--urls", default="",
                    help="URL 조각 쉼표 목록. **시범 전용** — 지표가 실재하는 문서를 겨눈다. "
                         "⚠ 본 실행에 쓰면 그것이 색인이다(폐기된 길). 시범은 측정이지 산출이 아니다")
    ap.add_argument("--expect", default="", help="산출에 이 문자열이 있는지 찍는다 (사전 등록 지표)")
    ap.add_argument("--dry-run", dest="dry", action="store_true",
                    help="호출 수·예상 비용만. **LLM 0회 · 0원**")
    a = ap.parse_args()

    codes = [s.strip().upper() for s in a.sections.split(",") if s.strip()]
    bad = [c for c in codes if c not in LABEL]
    if bad:
        print(f"모르는 절 코드: {bad}  (가능: {', '.join(LABEL)})")
        return 1

    docs = RS._corpus(a.source_run)
    if a.urls:
        want = [u.strip() for u in a.urls.split(",") if u.strip()]
        docs = [d for d in docs if any(u in (d["url"] or "") for u in want)]
        print(f"⚠ --urls 로 {len(docs)}건만 골랐다 (시범 전용)")
    if a.limit:
        docs = docs[:a.limit]
    보낼 = sum(min(d["글자"], a.cap) for d in docs)
    호출 = len(docs) * len(codes)
    tok_in = 보낼 * _TOK_PER_CHAR * len(codes)
    tok_out = _OUT_PER_CALL * 호출
    원 = (tok_in / 1e6 * _IN + tok_out / 1e6 * _OUT) * _KRW
    print(f"문서 {len(docs)}건 × 절 {len(codes)}개 = **{호출}회** · "
          f"보낼 글자 {보낼 * len(codes):,} · 예상 **{원:,.0f}원**")
    print(f"  절: {' · '.join(codes)}")
    if a.dry:
        print("\n--dry-run — 여기서 멈춘다 (LLM 0회 · 0원)")
        return 0

    out, run = _run(docs, codes, a.source_run, a.id, a.cap,
                    진행=lambda n, t: print(f"  … {n}/{t}"))
    res = out["문서별"]
    items = [it for r in res for it in r["items"]]
    ok = [it for it in items if it["채택"]]
    per = out["절별"]
    path = os.path.join(run.dir, "sections.json")
    io.open(path, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))

    print(f"\n문서×절 상태 {out['상태']}")
    print(f"사실 {len(items)}건 · 인용 대조 통과 {len(ok)} (떨어짐 {len(items) - len(ok)})")
    print("절별 통과 " + " · ".join(f"{k} {v}" for k, v in sorted(per.items())))
    if a.expect:
        있 = any(a.expect in (it["number_raw"] + it["quote"]) for it in ok)
        print(f"\n**사전 등록 지표 «{a.expect}» — {'나왔다' if 있 else '안 나왔다'}**")
    print(f"기록: {path}")
    m = run.counters
    돈 = m.get("llm.tokens_in", 0) / 1e6 * _IN + m.get("llm.tokens_out", 0) / 1e6 * _OUT
    print(f"LLM {m.get('llm.calls', 0):.0f}회 · in {m.get('llm.tokens_in', 0):,.0f} "
          f"out {m.get('llm.tokens_out', 0):,.0f} · ≈{돈 * _KRW:,.0f}원")
    return 0


if __name__ == "__main__":
    sys.exit(main())
