# -*- coding: utf-8 -*-
"""⛔ **반증됐다. 쓰지 마라.** (판 ㊵ 실측 · 2026-08-14)

    같은 문서·같은 모델에서 **질문의 폭만** 바꿔 쟀다:
      절 7개 메뉴 + 「전부 뽑아라」 → 17건 (전부 매출액)
      **통짜 12만 자(이 도구)**    → **3건. 더 나쁘다**
      절 하나씩 「가격만」          → 가격 표 전 품목 ×3개년 (6,513원 포함)

    원인은 「읽는 단위」가 아니라 **「질문의 폭」**이었다. 대신 `tools/reask_sections.py`
    를 쓴다. 이 파일은 **무엇을 이미 죽여 봤는지의 기록**으로만 남긴다 —
    지우면 다음 판이 같은 길을 다시 판다.

**통짜 읽기** — 문서를 덩어리로 묶어 **절 구분 없이** 읽고, 절은 **집필 단계에서 처음 만든다.**
(판 ㊵ 항목 3·4 · `docs/market-research-redesign/PLAN.md`)

    # 시범 — 덩어리 2개, 가격 절 하나 (≈52원)
    python tools/write_sections.py 0c54ffb5-... --id p40-pilot --chunks 2 --sections PRICE \
           --concept data/concept_hmr-product.json

    # 본 실행 — 전 덩어리, 7절
    python tools/write_sections.py 0c54ffb5-... --id p40-full --concept data/concept_hmr-product.json

**왜 문서를 하나씩 안 읽나** — 원본 보고서는 문서 30건을 한 책상에 펼쳐 놓고 「보고서를 써라」
고 시켜 만들어졌다. 지금 파이프라인은 문서 1건씩 열고 「9절 중 뭘 채우나」만 묻는다. 그래서
「8,900원은 6,513원의 1.37배」 같은 문장이 **원리적으로 못 나온다.**

**두 층을 절대 섞지 않는다.**

    [추출층] 덩어리마다 1회 — 수치·사실을 **개수 제한 없이 전부** 뽑기만 한다.
             ⚠ **절을 배정하지 않는다. 문단을 쓰지 않는다.**
             절을 덩어리에게 배정시키면 1패스가 「매출액이니까 MARKET_SIZE」로 뭉갠
             그 판단이 그대로 재발한다. 절은 집필 단계에서 처음 생긴다.
    [집필층] 절마다 1회 — 살아남은 사실 + **컨셉값**을 받아 문단·표·해석을 쓴다.

⚠ **컨셉값은 집필층에만 들어간다.** 절대규칙 6(가격 가설을 **수집** 프롬프트에 넣지 않는다
— 자기확인 회로)과 스치므로, 추출층 프롬프트에는 컨셉이 한 글자도 들어가지 않는다.

⚠ **문서 경계에서만 자른다.** 문서를 중간에서 자르면 표가 회사명·표머리와 분리돼
「무엇의 수인가」가 틀어진다.

⚠ **덩어리는 120,000자.** 180,000자는 창을 넘는다(입력 11.6만 + 출력 1.6만 > 12.8만 토큰).

인용 대조는 `read_sections._norm` 을 그대로 쓴다 — **한 잣대를 두 곳에서 만들지 않는다.**
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

#: 발췌와 같은 모델을 쓴다. 판 ㊾ 부터 값을 **베끼지 않고 가리킨다** — 베껴 두면
#: 저쪽만 바뀌었을 때 이 규칙이 조용히 깨진다.
MODEL = RS.MODEL
WORKERS = 4
JSON_OBJ = re.compile(r"\{.*\}", re.S)

FACT_FIELDS = ("quote", "number_raw", "unit_raw", "year", "subject", "table_context")

EXTRACT_CHUNK = """아래는 시장조사를 위해 수집한 문서 **여러 건**이다. 한 책상에 펼쳐 놓았다.

{documents}

이 재료에서 **시장조사에 쓸 수치·사실을 전부** 뽑아라.

규칙:
1. **개수 제한이 없다.** 이 재료가 말하는 것 중 시장조사에 쓸 만한 것을 **남김없이** 뽑아라.
   ⚠ 「이건 상관없겠지」 하고 버리지 마라 — 적합한지는 다음 단계가 기계로 정한다.
   네가 버리면 그 판단은 아무 데도 기록되지 않는다.
2. **절(카테고리)을 배정하지 마라. 문단을 쓰지 마라.** 이 단계는 **뽑기만** 한다.
   어느 절에 실릴지는 다음 단계가 정한다. 여기서 정하면 그 판단이 굳어 버린다.
3. quote 는 문서에 있는 문장을 **한 글자도 바꾸지 말고** 그대로. 다음 단계가 이 문장이
   문서에 실재하는지 기계로 대조하고, 다듬으면 그 자리에서 탈락한다.
   **숫자를 품은 짧은 구간만** 옮겨라. 표에서 뽑았다면 그 **행 한 줄**이면 충분하다.
4. number_raw · unit_raw 도 원문 표기 그대로. 계산·환산·반올림 금지.
   예) "30만 명" → number_raw "30만", unit_raw "명"
   **범위는 쪼개지 마라.** "2,400~3,400원" 은 그대로 하나로 낸다.
   숫자가 없는 사실(규제 의무·업계 관행·광고 문구)도 값어치가 있다. 그때는
   number_raw 와 unit_raw 를 빈 문자열로 두고 quote 와 subject 만 채워라.
5. **subject 는 「이 수가 무엇의 수인가」를 문서의 말 그대로 적는다.** 가장 중요한 칸이다.
   예) "26개 사업장에서 제조 및 판매를 하고 있습니다" → subject 는 **"제조·판매 사업장"**.
       "가맹점" 이나 "가입 매장" 으로 **바꾸지 마라.**
   ⚠ 이 칸을 뭉뚱그리면 다음 단계가 **다른 것을 같은 것으로 착각한다.** 길어도 좋다.
6. year 는 그 수치가 **가리키는 해**다(문서를 쓴 해가 아니다). 모르면 빈 문자열.
7. table_context 는 표에서 뽑았을 때 **표 제목과 행·열 이름**을 적는다. 표가 아니면 빈 문자열.
8. doc 은 그 사실이 나온 문서의 번호([문서 3] 이면 "3")를 적는다.
9. **없는 것을 지어내지 마라.** 문서에 없는 수치를 만들거나 다른 값으로 대신 넣지 마라.

JSON 만 출력:
{{"status":"found","facts":[{{"quote":"…","number_raw":"…","unit_raw":"…","year":"…","subject":"…","table_context":"…","doc":"…"}}]}}
"""

WRITE_SECTION = """너는 시장조사 보고서의 **「{label}」 절 하나**를 쓴다.

## 이 사업의 컨셉

{concept}

## 쓸 수 있는 사실 — **이것 말고는 아무것도 쓸 수 없다**

{facts}

## 할 일

위 사실 중 **이 절에 실릴 것을 골라** 문단과 표를 쓴다.

규칙:
1. **모든 문장·표에 `cites` 를 단다.** 위 사실의 id 를 적는다. 근거 없는 문장은 쓰지 마라.
   컨셉값(가격·예산 등)과 사실을 **비교하는 문장**은 그 사실의 id 를 단다.
2. **비교하고 판단하라.** 사실을 나열만 하면 이 단계를 할 이유가 없다.
   컨셉값이 사실 대비 어디에 서는지, 무엇이 사업안을 지지하고 무엇이 흔드는지 써라.
   ⚠ 계산은 위 사실에 있는 수로만 한다. 새 수를 만들지 마라.
3. **표를 아끼지 마라.** 여러 행으로 비교되는 것(가격대·채널 비중·연도별)은 표가 낫다.
4. 이 절에 실릴 사실이 정말 없으면 `body` 를 빈 배열로 두고 `note` 에 왜인지 한 줄.
   **빈 것도 정답이다** — 지어내는 것보다 낫다.

JSON 만 출력:
{{"section":"{code}","body":[
  {{"kind":"문장","text":"…","cites":["f1","f7"]}},
  {{"kind":"표","title":"…","columns":["…","…"],"rows":[["…","…"]],"cites":["f3"]}}
],"note":""}}
"""

LABEL = {"MARKET_SIZE": "시장 크기", "PRICE": "가격", "COMPETITOR": "경쟁",
         "CHANNEL": "채널 — 어디서 팔리나", "DEMAND": "수요",
         "UNIT_ECONOMICS": "원가와 수익성", "REGULATION": "규제"}


def chunks_of(docs: list, cap: int, size: int) -> list:
    """**문서 경계에서만** 자른다. 문서 하나가 상한을 넘으면 그 문서가 혼자 한 덩어리다."""
    out, cur, n = [], [], 0
    for d in docs:
        s = min(d["글자"], cap)
        if cur and n + s > size:
            out.append(cur)
            cur, n = [], 0
        cur.append(d)
        n += s
    if cur:
        out.append(cur)
    return out


def _render(chunk: list, cap: int) -> str:
    return "\n\n".join(
        f"[문서 {i}] {d['title'] or d['url'] or '(제목 없음)'}\n{d['text'][:cap]}"
        for i, d in enumerate(chunk, 1))


def _extract_one(ci: int, chunk: list, meter, cap: int) -> dict:
    """덩어리 하나. **예외를 올리지 않는다**(규칙 5 — 실패는 값이다)."""
    보낸 = sum(min(d["글자"], cap) for d in chunk)
    out = {"덩어리": ci, "문서": len(chunk), "보낸_글자": 보낸,
           "trace_ids": [d["trace_id"] for d in chunk]}
    try:
        r = meter.create("a3_chunk", model=MODEL,
                         input=prompts.render(EXTRACT_CHUNK, documents=_render(chunk, cap)))
    except Exception as e:
        return {**out, "status": "llm_failed",
                "note": f"{type(e).__name__}: {str(e)[:160]}", "facts": []}
    m = JSON_OBJ.search(getattr(r, "output_text", "") or "")
    try:
        data = json.loads(m.group(0)) if m else {}
    except Exception:
        data = {}
    if (data.get("status") or "") != "found":
        return {**out, "status": "not_found",
                "note": str(data.get("note") or "모델이 형식을 안 지켰다")[:200], "facts": []}

    # 인용 대조 — 덩어리 전체를 건초더미로 본다(어느 문서에서 왔는지는 doc 칸이 말한다)
    hay = RS._norm("\n".join(d["text"][:cap] for d in chunk))
    facts = []
    for f in data.get("facts") or []:
        if not isinstance(f, dict):
            continue
        it = {k: str(f.get(k) or "") for k in FACT_FIELDS}
        it["doc"] = str(f.get("doc") or "")
        it["quote_verified"] = bool(it["quote"]) and RS._norm(it["quote"]) in hay
        it["section_valid"] = True        # 이 층은 절을 배정하지 않는다
        it["채택"] = it["quote_verified"]
        it["탈락_사유"] = "" if it["채택"] else "인용이 본문에 없다"
        facts.append(it)
    return {**out, "status": "found", "note": "", "facts": facts}


def _concept_text(path: str) -> str:
    """집필층에만 들어간다. **`_` 접두 키는 뺀다** — `run.py:load_concept` 과 같은 잣대."""
    c = json.load(io.open(path, encoding="utf-8"))
    keep = {k: v for k, v in c.items() if not k.startswith("_")}
    return json.dumps(keep, ensure_ascii=False, indent=1)


def _write_one(code: str, facts: list, concept: str, meter) -> dict:
    블록 = "\n".join(
        f"  {it['id']}. {it['number_raw']}{it['unit_raw']} — {it['subject']}"
        f"{(' (' + it['year'] + ')') if it['year'] else ''}"
        f"{(' [' + it['table_context'][:80] + ']') if it['table_context'] else ''}"
        f"\n      인용: {it['quote'][:200]}"
        for it in facts)
    try:
        r = meter.create("a3_write", model=MODEL,
                         input=prompts.render(WRITE_SECTION, label=LABEL.get(code, code),
                                              code=code, concept=concept, facts=블록))
    except Exception as e:
        return {"section": code, "status": "llm_failed",
                "note": f"{type(e).__name__}: {str(e)[:160]}", "body": []}
    m = JSON_OBJ.search(getattr(r, "output_text", "") or "")
    try:
        data = json.loads(m.group(0)) if m else {}
    except Exception:
        data = {}
    body, ids = [], {it["id"] for it in facts}
    for b in data.get("body") or []:
        if not isinstance(b, dict):
            continue
        cites = [c for c in (b.get("cites") or []) if c in ids]
        b["cites"], b["환각_cites"] = cites, [c for c in (b.get("cites") or []) if c not in ids]
        b["근거_없음"] = not cites          # 지우지 않고 표시만 — 규칙 5
        body.append(b)
    return {"section": code, "status": "found", "note": str(data.get("note") or ""),
            "body": body}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("source_run")
    ap.add_argument("--id", required=True, help="새 실행 id (원장을 덮지 않는다)")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--cap", type=int, default=60000, help="문서당 보낼 글자 상한")
    ap.add_argument("--chunk-chars", dest="size", type=int, default=120000,
                    help="덩어리 크기. 180,000 은 창을 넘는다")
    ap.add_argument("--chunks", type=int, default=0, help="앞에서 N 덩어리만 (시범용)")
    ap.add_argument("--sections", default=",".join(prompts.SECTION_CODES),
                    help="집필할 절. 시범은 PRICE 하나")
    ap.add_argument("--dry-run", dest="dry", action="store_true",
                    help="덩어리 구성과 보낼 글자만 보고 멈춘다. **LLM 0회**")
    a = ap.parse_args()

    docs = RS._corpus(a.source_run)
    ch = chunks_of(docs, a.cap, a.size)
    if a.chunks:
        ch = ch[:a.chunks]
    보낼 = sum(min(d["글자"], a.cap) for c in ch for d in c)
    codes = [s.strip().upper() for s in a.sections.split(",") if s.strip()]
    print(f"문서 {sum(len(c) for c in ch)}건 · 덩어리 {len(ch)}개 · 보낼 글자 {보낼:,} "
          f"(상한 {a.cap:,}/문서 · 덩어리 {a.size:,})")
    for i, c in enumerate(ch, 1):
        print(f"  덩어리 {i}: {len(c)}문서 {sum(min(d['글자'], a.cap) for d in c):,}자")
    print(f"집필할 절: {' · '.join(codes)}")
    print(f"LLM 예상 {len(ch)} + {len(codes)} 회")
    if a.dry:
        print("\n--dry-run — 여기서 멈춘다 (LLM 0회 · 0원)")
        return 0

    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI
    run = Run(a.id, rules=load_rules())
    meter = Meter(OpenAI(), run)

    # ── 추출층 ────────────────────────────────────────────────
    res: list = [None] * len(ch)
    with cf.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futs = {pool.submit(_extract_one, i + 1, c, meter, a.cap): i for i, c in enumerate(ch)}
        for fu in cf.as_completed(futs):
            res[futs[fu]] = fu.result()
    facts = []
    for r in res:
        for f in r["facts"]:
            f["덩어리"] = r["덩어리"]
            f["id"] = f"f{len(facts) + 1}"
            facts.append(f)
    ok = [f for f in facts if f["채택"]]
    print(f"\n추출층 — 사실 {len(facts)}건 · 인용 대조 통과 {len(ok)} "
          f"(떨어짐 {len(facts) - len(ok)}) · 값 없는 것 {sum(1 for f in ok if not f['number_raw'])}")
    for r in res:
        print(f"  덩어리 {r['덩어리']}: {r['status']} · {len(r['facts'])}건 {r.get('note','')[:60]}")

    # ── 집필층 ────────────────────────────────────────────────
    concept = _concept_text(a.concept)
    sections = [_write_one(c, ok, concept, meter) for c in codes]
    for s in sections:
        n = len(s["body"])
        print(f"\n집필 {s['section']} — {s['status']} · {n}칸 "
              f"(근거 없음 {sum(1 for b in s['body'] if b['근거_없음'])}"
              f" · 환각 cite {sum(len(b['환각_cites']) for b in s['body'])})")
        for b in s["body"][:6]:
            if b.get("kind") == "표":
                print(f"    [표] {b.get('title')}  {len(b.get('rows') or [])}행  {b['cites']}")
            else:
                print(f"    {str(b.get('text'))[:150]}  {b['cites']}")
        if s["note"]:
            print(f"    note: {s['note'][:150]}")

    out = {"source_run": a.source_run, "run_id": a.id, "cap": a.cap, "덩어리_크기": a.size,
           "덩어리": len(ch), "보낸_글자": 보낼, "덩어리별": res,
           "사실": facts, "절": sections}
    path = os.path.join(run.dir, "written.json")
    io.open(path, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))
    print(f"\n기록: {path}")
    m = run.counters
    돈 = m.get("llm.tokens_in", 0) / 1e6 * 0.15 + m.get("llm.tokens_out", 0) / 1e6 * 0.60
    print(f"LLM {m.get('llm.calls', 0):.0f}회 · 토큰 in {m.get('llm.tokens_in', 0):,.0f} "
          f"out {m.get('llm.tokens_out', 0):,.0f} · ≈ ${돈:.3f} (≈{돈 * 1390:.0f}원)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
