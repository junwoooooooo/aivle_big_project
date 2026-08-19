# -*- coding: utf-8 -*-
"""**통째 읽기** — 문서마다 「이 문서가 9절 중 무엇을 채우나」를 묻는다. (판 ㊳ 3단계 소판 B)

    python tools/read_sections.py 0c54ffb5-... --id p39-secA
    python tools/read_sections.py 0c54ffb5-... --id smoke --limit 5     # 싸게 확인

**왜 슬롯 경로를 안 쓰나** — 지금 발췌(`web.extract`)는 문서 하나에 **슬롯 하나의 질문**을
던진다. 판 ㊳ 실측: 문서 141건 중 120건이 `not_found`, 읽는 양을 2.1배로 올려도 그 비율이
안 변했고, 값이 몰린 큰 문서 4건은 글자를 3배로 줘도 인용 **0 → 0** 이었다. 그 문서 안에
찾는 수가 **실재함은 `corpus_probe.py` 로 확인됐다.** 남은 설명은 질문 방식뿐이다.

**이 도구는 기존 파이프라인을 건드리지 않는다.** 원장을 읽어 `sections.json` 을 따로 낳는다.
계약·봉투·성적표는 4단계에서 정한다 — 3단계는 **무엇이 나오는지 먼저 본다.**

⚠ **인용 대조를 여기서 한다.** 절 사실이 슬롯 게이트를 우회하면 판 ㉞~㊲ 에서 쌓은
정밀도 장치가 새 경로에 하나도 안 걸린다. 그래서 `quote_verified` 를 이 안에서 매기고,
**떨어진 것도 값으로 남긴다**(규칙 5 — 실패는 값이다).
"""
from __future__ import annotations

import argparse, concurrent.futures as cf, hashlib, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import prompts
import runpath
from base import load_env_key
from runlog import Meter, Run, load_rules

#: 발췌 모델. **`reask_sections` 가 이 값을 그대로 쓴다** — 두 곳에 적으면 갈린다.
#:
#: 판 ㊺ 에서 `gpt-4o-mini` → `gpt-5.6-luna`. 같은 문서 10건·같은 프롬프트·읽기 1회 실측:
#:
#:     모델            뽑음   절 머리   채우는 절   입력$/1M   출력$/1M
#:     gpt-4o-mini      71      30       2절        0.15      0.60
#:     gpt-4o           95      56       2절        2.50     10.00
#:     gpt-5.6-terra   342     123       6절        2.00     12.00
#:     gpt-5.6-luna    350     121       5절        0.20      1.20   ← 채택
#:
#: `mini` 는 「총매출 3조 6,745억」 같은 **큰 수만 긁고 세부 항목을 건너뛴다.**
#: `luna` 는 `terra` 성능에 값이 1/10 이고, **절 머리 1건당 원가는 `mini` 보다도 낮다.**
#: ⚠ 추론 모델이다 — `temperature` 를 **넘기지 않는다**(넘기면 400). 여기는 원래 안 넘긴다.
MODEL = "gpt-5.6-luna"

#: 1M 토큰당 달러. **모델과 «반드시» 같이 움직인다** — 안 그러면 실행이 스스로 보고하는
#: 원가가 거짓이 되고, `expected.md` 원장이 그 거짓 숫자로 채워진다(판 ㊺ 에서 잡은 자리).
PRICE_IN, PRICE_OUT = 0.20, 1.20

#: 추론 모델의 **「답하기 전에 얼마나 생각할까」** 손잡이. 온도 자리를 대신한다.
#: 받는 값: `none` · `low` · `medium`(기본) · `high` · `xhigh`. 빈 문자열이면 안 넘긴다.
#:
#: ⚠ **발췌에서는 «많이 생각하는 것»이 해로울 수 있다.** 다른 모듈(분류/코드북) 실측에서
#:   `high` 는 추론 토큰을 40배 쓰고 이름표를 **더 뭉갰다**(6→3). 발췌는 **세부를 많이
#:   남겨야** 하는 일이라 정반대 방향이다 — 뭉치면 그만큼 사실이 사라진다.
#:   그래서 **재고 정한다.** 값은 `tools/effort_probe` 결과를 여기 적는다.
EFFORT = ""

#: 출력 상한. **추론 토큰이 여기서 같이 깎인다** — 빠듯하면 생각하다 끝나 본문이 빈 채로
#: 「성공」한다. `reask_sections` 가 이 값을 그대로 쓴다(두 곳에 적으면 갈린다).
MAX_OUT = 65536


def _옵션(effort: str = "") -> dict:
    """모델이 받는 손잡이만 골라 넘긴다. **없으면 안 넘긴다** — 400 으로 죽는다."""
    e = (effort or EFFORT or "").strip()
    return {"reasoning": {"effort": e}} if e else {}

#: 동시에 세우는 줄 수. **벽시계를 정하는 것은 모델이 아니라 이 수다** — 호출 수를
#: 이것으로 나눈 만큼이 걸린다(판 ㊺ 실측: 읽기 112회 ÷ 6 = 약 6분).
#:
#: ★ **판 ㊺ — 6 → 12.** 6 은 이유가 적힌 적 없는 맨 상수였다. 올려도 되는 근거:
#:   `OpenAI()` 기본 클라이언트가 429 를 **지수 백오프로 2회 재시도**하고, 그래도 실패한
#:   문서는 `_one` 이 `llm_failed` 로 **값으로** 남기지 판을 죽이지 않는다.
#: ⚠ **비용은 한 푼도 안 변한다** — 호출 수가 같다. 바뀌는 것은 벽시계뿐이다.
#: ⚠ 429 가 실제로 늘면 원장의 `llm.*.error` 가 센다. **거기가 이 수를 되돌릴 자리다.**
WORKERS = 12
JSON_OBJ = re.compile(r"\{.*\}", re.S)
#: 인용 대조용 정규화 — 공백과 **문장부호까지만** 접는다.
#: 그 이상(숫자만 맞으면 통과 따위)으로 관대해지면 「대조했다」가 거짓이 된다.
_WS = re.compile(r"\s+")


def _punct() -> str:
    """관용할 문장부호. 값은 `rules/publish.v1.json` 에서 온다 (규약 ①).

    **왜 관대해졌나**: 체크리스트 1-6 「냉동간편식 1조 1,666억」의 본문은
    「…26.2% 증가함」(줄바꿈)이고 모델 인용은 「…26.2% 증가함.」이었다. **마침표 하나로 죽었다.**
    실측: 이 관용으로 18건이 되살아나고, 그중에 이번 판의 왕관 사실이 들어 있다.
    """
    p = os.path.join(ROOT, "rules", "publish.v1.json")
    try:
        return ((json.load(io.open(p, encoding="utf-8")).get("인용_관용") or {})
                .get("문장부호") or "")
    except Exception:
        return ""


_PUNCT = str.maketrans("", "", _punct())


def _norm(s: str) -> str:
    return _WS.sub("", s or "").translate(_PUNCT)


#: 되찾은 인용의 앞뒤 폭. 문장 하나가 담기고 문단은 안 담기는 크기다.
_앞, _뒤 = 160, 90
_문장끝 = "。.!?●■◆\n\t"


def 재정박(num_raw: str, subject: str, body: str, unit_raw: str = "") -> str | None:
    """모델이 쓴 인용이 본문에 없을 때, **본문에서 그 수가 있는 진짜 문장을 되찾는다.**

    ## 왜 필요한가 — 인용 대조가 «가장 좋은 문서»를 골라 죽이고 있었다

    실측(판 ㊹ 4단계, `p41-merged`): 이 판의 왕관 사실 **「간편식 국내 판매액 6조 8천억」**과
    목표 보고서 6절 머릿값 **「식품 제조업 영업이익률 4% 미만」**이 **둘 다 「인용이 본문에
    없다」로 떨어져 있었다.** 그런데 본문에는 있다 —

        본문:   「…국내 판매액은 2025년 6조 8천억 규모로 예상됨, 프리미엄화와 냉동 제품 출…」
        모델:   「2025년 간편식의 국내 판매액 규모를 … 약 6조 8천억 원 규모로 추정됨.」

    정부 PDF 는 레이아웃이 본문에 섞여 들어와(「환경변화와 식품시장 전망 < 기후 환경 >」이
    문장 한가운데 박힌다) **글자 그대로 인용하는 것이 불가능**하다. 모델은 그것을 읽기 좋게
    고쳐 썼고, 대조는 그것을 거짓으로 판정했다. **재료가 좋을수록 더 죽는다.**

    ## ⚠ 이것은 관문을 «느슨하게» 하는 것이 아니다

    모델이 쓴 문장을 봐주는 게 아니라 **버리고 본문 문장으로 갈아 끼운다.** 결과적으로
    인용은 **정의상 본문 그대로**가 된다 — 지금보다 엄격해진다.

    두 겹으로 오정박을 막는다:
      ① 그 수가 본문에 **한 번만** 나오거나,
      ② 되찾은 창 안에 **주어의 낱말이 겹칠 때**만 인정한다.
    둘 다 아니면 **되찾지 않는다** — 아무 자리에나 정박하면 값의 정체가 바뀐다.
    """
    n = (num_raw or "").split("~")[0].strip()
    u = (unit_raw or "").strip()
    if not n or not body:
        return None

    # ⚠ **짧은 수는 «수+단위»로만 찾는다.** 실측(판 ㊹ 4단계 1차): `num="4"` 가 본문
    #   아무 데나 걸려 인용이 **「경」 한 글자**가 됐다. `4%` 로 찾으면 그 일이 없다.
    #   `4` 는 어느 문서에나 수백 번 나오고, 그런 수는 «어디에 있었나»가 정보가 아니다.
    후보 = [f"{n}{u}", f"{n} {u}"] if u else []
    후보 += [n] if len(n.replace(",", "")) >= 2 else []
    자리, 쓴 = [], ""
    for cand in 후보:
        cand = cand.strip()
        자리 = [m.start() for m in re.finditer(re.escape(cand), body)]
        if not 자리 and "," in cand:                # `1,140,941` ↔ `1140941`
            cand = cand.replace(",", "")
            자리 = [m.start() for m in re.finditer(re.escape(cand), body)]
        if 자리:
            쓴 = cand
            break
    if not 자리:
        return None

    말 = {w for w in re.findall(r"[가-힣A-Za-z]{2,}", subject or "") if len(w) >= 2}
    for i in 자리:
        창 = body[max(0, i - _앞): i + _뒤]
        if not (len(자리) == 1 or (말 and any(w in 창 for w in 말))):
            continue
        # 문장 경계로 다듬는다 — 앞은 마지막 끝맺음 뒤부터, 뒤는 첫 끝맺음까지
        앞부분, 뒷부분 = 창[:_앞], 창[_앞:]
        for ch in _문장끝:
            k = 앞부분.rfind(ch)
            if k >= 0:
                앞부분 = 앞부분[k + 1:]
        끝 = min([j for j in (뒷부분.find(c) for c in _문장끝) if j >= 0] or [len(뒷부분)])
        got = " ".join((앞부분 + 뒷부분[:끝 + 1]).split()).strip()
        # ⚠ **다듬다가 빈 껍데기가 되면 버린다.** 문장 부호가 수 바로 앞뒤에 있으면
        #   창이 통째로 깎여 한두 글자가 남는다 — 그런 인용은 근거가 아니다.
        if len(got) >= 12 and 쓴 in got:
            return got
    return None


def _corpus(source_run: str) -> list[dict]:
    """원장의 문서를 **내용 기준으로 중복 제거**해 돌려준다.

    같은 URL 이 슬롯마다 다른 `trace_id` 로 저장돼 있어 `trace_id` 로 세면 문서가 부풀고,
    같은 문서를 여러 번 읽어 돈만 는다.
    """
    base = runpath.find(source_run)
    raw = json.load(io.open(os.path.join(base, "a3_bodies.json"), encoding="utf-8"))
    meta = {}
    with io.open(os.path.join(base, "run.jsonl"), encoding="utf-8") as fh:
        for line in fh:
            try:
                r = json.loads(line)
            except Exception:
                continue
            # ⚠ **조회일은 `a3_document` 에만 있다** (실측: a3_candidate 178건 전부 없음,
            #    a3_document 182건 전부 있음). 승격 카드가 조회일 없이 「확정」으로 앉는 것을
            #    막으려면 여기서 같이 걷어야 한다 — **지어내지 않고 되찾는다.**
            if r.get("node") in ("a3_candidate", "a3_document"):
                p = r.get("payload") or {}
                if p.get("trace_id"):
                    old = meta.get(p["trace_id"]) or ("", "", None)
                    meta[p["trace_id"]] = (p.get("url") or old[0], p.get("title") or old[1],
                                           p.get("retrieved_at") or old[2])
    seen: dict[str, dict] = {}
    for tid, v in raw.items():
        text = v if isinstance(v, str) else json.dumps(v, ensure_ascii=False)
        if not text.strip():
            continue
        h = hashlib.md5(text.encode("utf-8")).hexdigest()
        if h in seen:
            seen[h]["별칭"].append(tid)
            continue
        url, title, got = meta.get(tid, ("", "", None))
        seen[h] = {"trace_id": tid, "url": url, "title": title, "조회일": got,
                   "text": text, "글자": len(text), "별칭": []}
    return sorted(seen.values(), key=lambda d: -d["글자"])


def _refetch_pdfs(docs: list) -> list:
    """PDF 를 다시 받아 **지금의** `pdf_text` 로 본문을 다시 뽑는다. LLM 0회.

    원장의 본문은 옛 추출기(`pg.extract_text()`)가 만든 것이라 **다단 조판이 줄 단위로
    뒤섞여** 있다 — 그 문서에서는 온전한 문장이 존재하지 않아 인용 대조가 구조적으로
    통과할 수 없다(판 ㊳ 실측). **실패는 값이다** — 못 받은 문서는 옛 본문 그대로 두고
    그 사실을 남긴다.
    """
    import requests
    import pdf_text
    cfg = pdf_text.load_pdf_cfg()
    ua = {"User-Agent": "Mozilla/5.0"}
    out = []
    for d in docs:
        u = d["url"] or ""
        if "pdf" not in u.lower():
            out.append({**d, "재추출": "PDF 아님"})
            continue
        try:
            r = requests.get(u, timeout=40, headers=ua)
            if r.status_code != 200 or not pdf_text.is_pdf(r.content, "", cfg):
                out.append({**d, "재추출": f"못 받음(HTTP {r.status_code})"})
                continue
            text, why = pdf_text.extract(r.content, cfg)
            if not text.strip():
                out.append({**d, "재추출": f"본문 없음({why})"})
                continue
            out.append({**d, "text": text, "글자": len(text),
                        "재추출": f"다시 뽑음 {d['글자']:,}→{len(text):,}자"})
        except Exception as e:
            out.append({**d, "재추출": f"실패({type(e).__name__})"})
    for d in out:
        print(f"  [{d.get('재추출')}] {(d['url'] or '')[:78]}")
    return out


class _Doc:
    """`prompts.render_document` 가 보는 최소 모양."""

    def __init__(self, d):
        self.url, self.title, self.text = d["url"], d["title"], d["text"]


def _read_one(d: dict, meter, cap: int, max_items: int) -> dict:
    """문서 하나. **예외를 올리지 않는다** — 하나가 죽어 전체가 죽으면 안 된다(규칙 5)."""
    body = d["text"][:cap]
    out = {"trace_id": d["trace_id"], "url": d["url"], "글자": d["글자"],
           "조회일": d.get("조회일"), "보낸_글자": len(body), "별칭": d["별칭"]}
    try:
        # ⚠ **출력 상한을 «명시»한다** (판 ㊺). 안 주면 모델이 스스로 끊고 그 사실이
        #   어디에도 안 남는다 — 그리고 추론 모델은 **생각한 토큰도 이 상한에서 깎아서**
        #   생각하다 끝나면 **본문이 빈 채로 「성공」한다.** 실패보다 나쁜 자리다(조용하다).
        #   `reask_sections.MAX_OUT` 과 같은 값이다.
        r = meter.create("a3_sections", model=MODEL, max_output_tokens=MAX_OUT, **_옵션(),
                         input=prompts.render(
                             prompts.EXTRACT_SECTIONS,
                             sections=prompts._SECTION_MENU,
                             max_items=max_items,
                             document=prompts.render_document(_Doc(d), cap)))
    except Exception as e:
        return {**out, "status": "llm_failed",
                "note": f"{type(e).__name__}: {str(e)[:160]}", "items": []}

    m = JSON_OBJ.search(getattr(r, "output_text", "") or "")
    try:
        data = json.loads(m.group(0)) if m else {}
    except Exception:
        data = {}
    if (data.get("status") or "") != "found":
        return {**out, "status": "not_found",
                "note": str(data.get("note") or "모델이 형식을 안 지켰다")[:200], "items": []}

    hay = _norm(body)
    items, codes = [], set(prompts.SECTION_CODES)
    for f in (data.get("findings") or [])[:max_items]:
        if not isinstance(f, dict):
            continue
        q = str(f.get("quote") or "")
        sec = str(f.get("section") or "").strip().upper()
        it = {k: str(f.get(k) or "") for k in prompts.EXTRACT_ITEM_SECTION_FIELDS}
        it["section"] = sec
        # ── 두 겹. **떨어뜨리되 지우지 않는다** ──────────────────────
        it["quote_verified"] = bool(q) and _norm(q) in hay
        if not it["quote_verified"]:
            # **본문에서 진짜 문장을 되찾는다** (판 ㊹ 4단계). 되찾으면 인용은
            # 정의상 본문 그대로가 된다 — 관문이 느슨해지는 게 아니라 엄격해진다.
            다시 = 재정박(it.get("number_raw") or "", it.get("subject") or "", body,
                        it.get("unit_raw") or "")
            if 다시 and _norm(다시) in hay:
                it["quote"], it["quote_verified"] = 다시, True
                it["인용_되찾음"] = True
        it["section_valid"] = sec in codes
        it["채택"] = it["quote_verified"] and it["section_valid"]
        it["탈락_사유"] = ("" if it["채택"] else
                        ("인용이 본문에 없다" if not it["quote_verified"]
                         else f"절 코드가 아니다({sec or '빈칸'})"))
        items.append(it)
    return {**out, "status": "found", "note": "", "items": items}


#: 문서 하나에서 받을 사실 수 상한. **모델과 한 몸이다.**
#: 판 ㊺ 전에는 20 이었고 그것이 옳았다 — `mini` 실측 최댓값이 **19**라 상한에 **닿은 적이 없다**.
#: 그런데 `luna`·`terra` 는 문서 10건 시범에서 **60(그때 상한)에 닿았다.** 모델을 올리면
#: 「상한은 병이 아니다」가 뒤집힌다. ⚠ 모델을 되돌리면 이 값도 같이 되돌린다.
MAX_ITEMS = 60


def build(source_run: str, run_id: str, *, cap: int = 60000, max_items: int = MAX_ITEMS,
          limit: int = 0, only_pdf: bool = False, pdf_refetch: bool = False) -> tuple:
    """**절 단위로 문서를 읽는다.** LLM 을 문서 수만큼 부른다 — 이 체인에서 가장 비싸다.

    판 ㊸ 1단계에서 `main()` 밖으로 꺼냈다. 돌려주는 것은 `(sections.json 내용, Run)` 이고,
    `Run` 은 부르는 쪽이 **비용을 셀 수 있게** 같이 준다 — 유료 호출을 하고도 얼마 썼는지
    모르는 자리를 만들지 않는다.
    """
    docs = _corpus(source_run)
    if only_pdf:
        docs = [d for d in docs if "pdf" in (d["url"] or "").lower()]
    if pdf_refetch:
        docs = _refetch_pdfs(docs)
    # **몇 건을 안 봤는지 여기서 센다.** 부르는 쪽이 호출 수로 되짚으면 LLM 실패와
    # 구분이 안 된다 — 잘림을 아는 것은 자른 자리다.
    안본 = max(0, len(docs) - limit) if limit else 0
    if limit:
        docs = docs[:limit]
    보낼 = sum(min(d["글자"], cap) for d in docs)
    print(f"문서 {len(docs)}건 · 보낼 글자 {보낼:,} · 상한 {cap:,}자/문서")

    os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")
    from openai import OpenAI
    run = Run(run_id, rules=load_rules())
    meter = Meter(OpenAI(), run)

    results: list = [None] * len(docs)
    with cf.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futs = {pool.submit(_read_one, d, meter, cap, max_items): i
                for i, d in enumerate(docs)}
        done = 0
        for fu in cf.as_completed(futs):
            results[futs[fu]] = fu.result()
            done += 1
            if done % 20 == 0:
                print(f"  … {done}/{len(docs)}")

    items = [it for r in results for it in r["items"]]
    ok = [it for it in items if it["채택"]]
    per_sec: dict = {}
    for it in ok:
        per_sec[it["section"]] = per_sec.get(it["section"], 0) + 1

    return {"source_run": source_run, "run_id": run_id, "cap": cap,
            "문서": len(docs), "안_읽은_문서": 안본, "보낸_글자": sum(r["보낸_글자"] for r in results),
            "상태": {s: sum(1 for r in results if r["status"] == s)
                   for s in sorted({r["status"] for r in results})},
            "인용_총": len(items), "인용_채택": len(ok), "절별": per_sec,
            # ★ **고친 본문을 그대로 물려준다** (판 ㊺). `_refetch_pdfs` 는 메모리 안에서만
            #   고치므로, 재질문이 `_corpus` 를 새로 부르면 **옛 2단 조판 본문을 다시 읽는다.**
            #   실측: 그 상태로는 읽기 94회만 새 본문이고 **재질문 376회가 옛 본문** — 지출의 80%다.
            #   ⚠ **원장에 쓰기 전에 «반드시» 빼라** — 본문 전량이라 `sections.json` 이 수 MB 로
            #     불어난다. 빼는 자리는 `pipeline._sections` 의 저장 직전 한 곳이다.
            "_문서목록": docs,
            "문서별": results}, run


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("source_run")
    ap.add_argument("--id", required=True, help="새 실행 id (원장을 덮지 않는다)")
    ap.add_argument("--cap", type=int, default=60000, help="문서당 보낼 글자 상한")
    ap.add_argument("--max-items", dest="max_items", type=int, default=MAX_ITEMS)
    ap.add_argument("--limit", type=int, default=0, help="문서 N건만 (싼 확인용)")
    ap.add_argument("--only-pdf", dest="only_pdf", action="store_true",
                    help="PDF 문서만. `--pdf-refetch` 효과를 재는 용도")
    ap.add_argument("--pdf-refetch", dest="pdf_refetch", action="store_true",
                    help="PDF 를 다시 받아 **지금의** pdf_text 로 본문을 다시 뽑는다(LLM 0회). "
                         "원장에 저장된 본문은 옛 추출기가 만든 것이라 다단 조판이 뒤섞여 있다")
    a = ap.parse_args()

    out, run = build(a.source_run, a.id, cap=a.cap, max_items=a.max_items,
                     limit=a.limit, only_pdf=a.only_pdf, pdf_refetch=a.pdf_refetch)
    items, ok, per_sec = out["인용_총"], out["인용_채택"], out["절별"]
    path = os.path.join(run.dir, "sections.json")
    io.open(path, "w", encoding="utf-8").write(
        json.dumps(out, ensure_ascii=False, indent=1))

    print(f"\n문서 상태 {out['상태']}")
    print(f"인용 {items}건 · 대조 통과 {ok}건 (떨어짐 {items - ok})")
    print("절별 " + " · ".join(f"{k} {v}" for k, v in sorted(per_sec.items())) or "절별 없음")
    print(f"기록: {path}")
    run.finish() if hasattr(run, "finish") else None
    m = run.counters
    print(f"LLM {m.get('llm.calls', 0):.0f}회 · 토큰 in {m.get('llm.tokens_in', 0):,.0f} "
          f"out {m.get('llm.tokens_out', 0):,.0f} · "
          f"≈ ${m.get('llm.tokens_in', 0) / 1e6 * PRICE_IN + m.get('llm.tokens_out', 0) / 1e6 * PRICE_OUT:.3f}"
          f" ({MODEL})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
