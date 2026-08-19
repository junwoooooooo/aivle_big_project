# -*- coding: utf-8 -*-
"""web 어댑터 — `plan_query(✗) → search(✓) → fetch(✗) → extract(✓)`

LLM 은 search 와 extract 둘뿐이다. fetch 가 순수 HTTP 라야
"모델이 snippet 으로 추측했다"가 **구조적으로** 불가능해진다.

extract 는 **문서 하나당 1회** 호출한다(`rules.adapters.web.extract_mode = per_doc`).
판 ㉛ 전까지는 슬롯 단위로 문서를 묶어 1회였다 — 비용은 쌌지만 모델의 「없습니다」 한
마디로 문서 5건이 통째로 죽었고 어느 문서를 읽었는지 원장에 남지 않았다(실측: 발췌에
들어간 44건이 인용 1건). 문서별로 물으면 인용의 소속도 자명해져 `doc_index` 역추적이
필요 없다.
"""
from __future__ import annotations

import concurrent.futures as cf
import dataclasses
import json, re
from datetime import datetime

import requests
import trafilatura
from trafilatura.metadata import extract_metadata

import doc_window
import pdf_text
import prompts
from base import AdapterResult, load_env_key, make_document
from schema import Candidate, Document, Finding, FindingItem, Slot

NAME = "web"
SEARCH_MODEL = "gpt-5.4-nano"
#: 수집 단계의 발췌. 판 ㊾ 에서 `gpt-4o-mini` → `gpt-5.6-luna` — 절 발췌
#: (`tools/read_sections.py`)가 판 ㊺ 에 먼저 옮겨간 것과 같은 이유다.
#: ⚠ 여기는 온도를 안 넘긴다(원래 안 넘겼다). 그래서 상수 한 줄이면 끝이다.
EXTRACT_MODEL = "gpt-5.6-luna"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0 Safari/537.36")

# 폴백값 — **정본은 `rules/adapters.v1.json web`** 이다(규약 ①). 여기 값은 규칙을
# 못 받은 호출(옛 테스트 등)에서만 쓰인다. 둘이 갈리면 규칙 쪽이 이긴다.
MAX_CANDIDATES = 12
MAX_DOCS_IN_PROMPT = 12
DOC_CHARS = 20000
MAX_ITEMS = 8
#: 검색 표본의 동시 실행 수. 표본은 서로 독립이라 순차로 돌 이유가 없다.
#: 정본은 `rules.adapters.web.search_workers` 이고 여기 값은 폴백이다.
SEARCH_WORKERS = 6

#: 발췌를 문서별로 부를 때의 동시 실행 수. `run.py:MAX_WORKERS` 와 같은 수를 쓴다 —
#: 슬롯 병렬 안에서 다시 병렬이라 곱해진다는 것을 알고 두는 값이다.
EXTRACT_WORKERS = 5


def _wcfg(rules: dict | None) -> dict:
    return ((rules or {}).get("adapters", {}).get("web") or {})


# ══════════════════════════════════════════════════════════════
# plan_query — LLM ✗. subject 와 region 을 **중복시키지 않는다.**
#              이전 버전에서 "서울 서울 커피전문점 사업체 수" 가 실제로 나갔다.
#
# ⚠ **이 문자열은 모델에게 가지 않는다.** `search()` 는 프롬프트를 subject·metric·period·
#   region 으로 렌더할 뿐이고, 실제 검색어는 모델의 web_search 도구가 스스로 정한다.
#   지금 이 함수가 하는 일은 **반복 횟수(2회)를 정하는 것뿐**이다.
#   과거에 이 문자열이 `Candidate.from_query` 에 그대로 실려 "쿼리에 연도가 박혀 나간다" 는
#   진단을 낳았다 — 로그가 만든 착시였다. 실제 연도 경로는 프롬프트의 `대상 기간` 줄이다.
#   쿼리를 진짜로 프롬프트에 넣는 안(=(가))은 12-2 판정 후 별건이다. 지금 같이 바꾸면
#   프롬프트 수정과 쿼리 주입 중 무엇이 먹혔는지 못 가린다(full-02 의 실패).
# ══════════════════════════════════════════════════════════════
def plan_query(slot: Slot) -> list[str]:
    region = "" if (slot.region and slot.region in slot.subject) else (slot.region or "")
    forms = [
        " ".join(x for x in (region, slot.subject, slot.metric, slot.period) if x),
        " ".join(x for x in (slot.subject, slot.metric, "통계", slot.period) if x),
    ]
    seen, out = set(), []
    for q in forms:
        q = re.sub(r"\s+", " ", q).strip()
        if q and q not in seen:
            seen.add(q)
            out.append(q)
    return out


# ══════════════════════════════════════════════════════════════
# search — LLM ✓. URL 과 제목만 건진다. 검색 요약은 버린다.
# ══════════════════════════════════════════════════════════════
def _citations(resp) -> list[dict]:
    seen, out = set(), []

    def walk(o):
        if isinstance(o, dict):
            if o.get("type") == "url_citation":
                u = (o.get("url") or "").strip()
                if u and u not in seen:
                    seen.add(u)
                    out.append({"url": u, "title": o.get("title") or ""})
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)

    walk(json.loads(resp.model_dump_json()))
    return out


class LLMFailure(Exception):
    """LLM 호출 실패를 값으로 바꾸기 위한 신호. kind 는 llm_quota | llm_error."""

    def __init__(self, kind: str, detail: str):
        super().__init__(detail)
        self.kind, self.detail = kind, detail


def _call(meter, node: str, **kw):
    """LLM 예외를 밖으로 흘리지 않는다 — 워커 하나가 실행 전체를 죽이면 안 된다(규칙 5)."""
    try:
        return meter.create(node, **kw)
    except Exception as e:
        name = type(e).__name__
        detail = f"{name}: {str(e)[:160]}"
        kind = "llm_quota" if ("RateLimit" in name or "quota" in str(e).lower()
                               or "credit" in str(e).lower()) else "llm_error"
        raise LLMFailure(kind, detail) from None


def search(slot: Slot, meter, trace_prefix: str, rules: dict | None = None) -> list[Candidate]:
    # 문안은 **규칙이 정한다**(rules.adapters.web.search_prompt). 기본은 v1 이고,
    # 미채택인 v12-2 는 명시적으로 골라야 쓰인다. 규칙은 result.json 에 값째로 복사되므로
    # 어느 실행이 어느 문안으로 수집했는지가 비교 축으로 자동으로 남는다.
    variant = ((rules or {}).get("adapters", {}).get("web") or {}).get("search_prompt")
    variant, tpl = prompts.search_prompt(variant)
    out: list[Candidate] = []
    # **표본 수.** `plan_query` 가 돌려주는 것은 검색어가 아니라 «몇 번 뽑을까»다 —
    # 문자열은 모델의 web_search 가 스스로 정하므로(그 함수 주석 참조) 이 반복은
    # **같은 프롬프트에서 독립 표본을 N번 뽑는 것**이다.
    #
    # 판 ㊱ 실측이 이 값을 규칙으로 뺀 이유: 정답 문서(통계청 사회조사)가 4표본 중 1번
    # 나타났다(q≈0.25). 두 판의 질의 겹침은 3% — 뽑기마다 다른 데를 뒤진다. 그러면
    # 슬롯 적중률은 1-(1-q)^N 이라 **N 을 늘리는 것이 가장 직접적인 레버**다.
    #   N=2 → 44%   N=4 → 68%   N=6 → 82%   N=8 → 90%
    # ⚠ 기본은 **2** 다 — 안 적으면 지금까지의 모든 원장과 같은 조건으로 돈다.
    _forms = plan_query(slot)
    _n = max(1, int(_wcfg(rules).get("search_samples") or len(_forms)))
    cap_c = int(_wcfg(rules).get("max_candidates") or MAX_CANDIDATES)

    def _one(qi: int) -> tuple:
        """표본 하나. **값으로 돌려준다** — 예외는 밖에서 모아 판정한다."""
        # `from_query` 에는 **실제로 모델이 받은 것**만 적는다. plan_query 의 문자열을 적으면
        # 나가지도 않은 검색어가 원장에 남아 진단을 오염시킨다(실제로 그랬다).
        q = (f"[{variant}·표본{qi + 1}] {slot.subject} / {slot.metric} / 기간 {slot.period} / "
             f"지역 {slot.region} (검색어는 모델이 정함)")
        # tag 는 API 로 안 나간다 — 모델이 실제로 던진 검색어를 `a3_web_query` 노드로
        # 남기기 위한 머리다(판 ㉟ ②). `from_query` 는 우리가 지어낸 설명 문자열이고,
        # 진짜 질의는 응답 안에만 있다.
        r = _call(meter, "a3_search",
                  tag={"slot_id": slot.slot_id,
                       "trace_id": f"{trace_prefix}-q{qi}",
                       "variant": variant, "from_query": q},
                  model=SEARCH_MODEL, tools=[{"type": "web_search"}],
                  input=prompts.render(
                      tpl, subject=slot.subject, metric=slot.metric,
                      period=slot.period, region=slot.region,
                      # 가설 D — 슬롯 유형마다 원출처가 다르다 (12-2)
                      # 힌트도 **문안 버전을 따른다** — 안 그러면 v12-2 가
                      # 가리키는 글이 조용히 달라진다(원장엔 이름만 남는다).
                      claim_type_hint=prompts.claim_type_hint(slot.claim_type, variant)))
        cits = _citations(r)
        if not cits:
            return q, [Candidate(slot_id=slot.slot_id, trace_id=f"{trace_prefix}-q{qi}-u0",
                                 url="", from_query=q, status="no_result")]
        # 상한은 규칙에서. 예전엔 코드 상수 6 이었고 **7번째부터는 원장에 흔적조차
        # 없었다** — `url_filter` 는 걸린 것도 status='filtered' 로 남기는데 이 상한은
        # 아무것도 안 남긴다. 값을 규칙으로 올린 뒤에도 그 성질은 그대로다(백로그 26).
        return q, [Candidate(slot_id=slot.slot_id, trace_id=f"{trace_prefix}-q{qi}-u{ui}",
                             url=c["url"], title=c["title"], from_query=q)
                   for ui, c in enumerate(cits[:cap_c])]

    # **표본은 서로 독립이다 — 순차로 돌 이유가 없다.** 판 ㊱ 에서 N 을 2→6 으로 올리자
    # 이 루프가 벽시계의 지배항이 됐다(판당 ≈10분). 슬롯(run.MAX_WORKERS)과 발췌
    # (EXTRACT_WORKERS)는 이미 병렬인데 여기만 순차였다.
    # ⚠ 슬롯 병렬 **안에서** 다시 병렬이라 동시 실행이 곱해진다 — 알고 두는 값이고,
    #   그래서 상한을 규칙에서 읽는다(429 를 만나면 여기를 조인다).
    results: list = [None] * _n
    if _n == 1:
        results[0] = _one(0)
    else:
        w = min(int(_wcfg(rules).get("search_workers") or SEARCH_WORKERS), _n)
        with cf.ThreadPoolExecutor(max_workers=w) as pool:
            futs = {pool.submit(_one, i): i for i in range(_n)}
            errs = []
            for fu in cf.as_completed(futs):
                try:
                    results[futs[fu]] = fu.result()
                except LLMFailure as e:
                    errs.append(e)
            # 순차판은 **첫 실패에서 즉시 멈췄다.** 병렬은 이미 다 뜬 뒤이므로, 하나라도
            # 실패했으면 같은 예외를 올려 `collect()` 가 어댑터 상태로 접게 한다 —
            # 실패를 값으로 바꾸는 자리는 여기가 아니라 거기다(규칙 5).
            if errs:
                raise errs[0]
    # **순서를 보존한다.** trace_id 에 qi 가 박혀 있어 완료 순서로 담으면 원장의
    # 표본 번호와 목록 순서가 어긋나고, 상한 절단이 «먼저 끝난 표본» 편을 든다.
    for _q, cands in results:
        out.extend(cands)
    return out


# ══════════════════════════════════════════════════════════════
# url_filter — LLM ✗. **fetch 앞에서** 연다. 내용이 아니라 URL 만 본다 (12-4).
#   12-3 에서 프롬프트 배제 목록이 껍데기율을 절반으로 줄였지만(z=-2.80) 도메인 다양성까지
#   깎았다. 확실한 것만 결정론으로 옮기면 그 개선을 LLM 분산 없이 공짜로 가져온다.
#   폐기한 패턴과 사유는 rules 에 남아 있다 — `/bbs/` 는 정부 보도자료를 죽인다.
# ══════════════════════════════════════════════════════════════
def url_filter(cands: list, rules: dict) -> tuple:
    """(통과, 걸림). 걸린 것은 **버리지 않고** status='filtered' + 사유를 달아 돌려준다."""
    cfg = ((rules.get("adapters") or {}).get("web") or {}).get("url_filter") or {}
    if not cfg.get("enabled"):
        return list(cands), []
    keep, drop = [], []
    for c in cands:
        if not c.url:
            keep.append(c)
            continue
        hit = [p for p in cfg.get("patterns", []) if re.search(p["re"], c.url, re.I)]
        if hit:
            c.status = "filtered"
            c.filter_reason = " · ".join(p["id"] for p in hit)
            drop.append(c)
        else:
            keep.append(c)
    return keep, drop


# ══════════════════════════════════════════════════════════════
# fetch — LLM ✗. 순수 HTTP. content_status 는 실측 기준값으로 정한다.
# ══════════════════════════════════════════════════════════════
def fetch(cand: Candidate, rules: dict) -> Document:
    from a_desk import classify_content

    # `retrieved_at` 은 **모든 분기**에 실린다 — 실패 문서도 「언제 시도했는지」를 남긴다.
    base = dict(slot_id=cand.slot_id, trace_id=cand.trace_id, url=cand.url, channel="web",
                retrieved_at=datetime.now().isoformat(timespec="seconds"))
    if not cand.url:
        return Document(**base, http_status="error", content_status="empty", error="no_result")
    try:
        r = requests.get(cand.url, headers={"User-Agent": UA},
                         timeout=rules["adapters"]["retry"]["timeout_sec"])
    except requests.Timeout:
        return Document(**base, http_status="timeout", content_status="empty", error="timeout")
    except Exception as e:
        return Document(**base, http_status="blocked", content_status="empty",
                        error=type(e).__name__)
    if r.status_code >= 400:
        return Document(**base, http_status="blocked", content_status="empty",
                        http_code=r.status_code)
    # ── PDF 분기. **html 검사보다 먼저** 온다 ──────────────────
    # 예전에는 여기서 통째로 `empty` 였다. 그래서 「빈 페이지」와 「PDF 라 못 읽음」이
    # 원장에서 같은 값이었고, mss 노쇼 보도자료처럼 **수치가 첨부에만 있는** 문서를
    # 「내용이 없는 문서」로 오독했다(백로그 23·24). 실패는 값이다(절대규칙 5).
    ctype = r.headers.get("content-type", "").lower()
    pdf_cfg = (rules["scoring"].get("content_status") or {}).get("pdf") or {}
    if pdf_cfg and pdf_text.is_pdf(r.content, ctype, pdf_cfg):
        ptext, why = pdf_text.extract(r.content, pdf_cfg)
        # `is_pdf` 는 **두 갈래 모두**에 붙인다 (판 ㉟ ②-b). 죽은 쪽에만 붙이면
        # 해석기가 들어온 뒤 「되살아난 PDF」를 usable 더미에서 세지 못한다.
        if not ptext:
            return Document(**base, http_status="ok", is_pdf=True,
                            content_status=pdf_cfg.get("unreadable_status",
                                                       pdf_text.UNREADABLE),
                            http_code=r.status_code, error=why)
        status, tlen, digits = classify_content(ptext, rules["scoring"])
        return Document(**base, text=ptext, http_status="ok", content_status=status,
                        text_len=tlen, digit_count=digits, http_code=r.status_code,
                        is_pdf=True)

    if "html" not in ctype:
        return Document(**base, http_status="not_html", content_status="empty",
                        http_code=r.status_code)

    # ── 인코딩 (판 ㉛) ────────────────────────────────────────────
    #   `requests` 는 Content-Type 에 charset 이 없으면 **ISO-8859-1 로 가정한다**
    #   (HTTP 1.1 유산). 한국어 UTF-8 페이지가 그 자리에서 깨지고, 깨진 본문은
    #   `classify_content` 가 `mojibake` 로 격리한다 — 즉 **우리가 깨뜨린 것을
    #   문서 탓으로 기록해 왔다.** 판 ㉛ 실측: `smoke-collect-01` 의 mojibake 9건이
    #   전부 이 경로였고, 그중 `steppay.kr/pricing` 은 자릿수 984개짜리 요금표였다.
    #   ⚠ 서버가 charset 을 **밝혔으면 그 말을 믿는다** — 밝힌 값을 우리가 덮으면
    #     추측이 선언을 이기게 되고, 그건 다른 종류의 조용한 오독이다.
    if "charset=" not in ctype:
        r.encoding = r.apparent_encoding or r.encoding

    text = trafilatura.extract(r.text) or ""
    status, tlen, digits = classify_content(text, rules["scoring"])
    published = None
    try:
        md = extract_metadata(r.text)
        published = str(md.date) if md and md.date else None
    except Exception:
        pass
    return Document(**base, text=text, published_at_raw=published, http_status="ok",
                    content_status=status, text_len=tlen, digit_count=digits,
                    has_table="<table" in r.text.lower(), http_code=r.status_code)


# ══════════════════════════════════════════════════════════════
# extract — LLM ✓. **문서당 1회** (`extract_mode = per_doc`).
#   ⚠ 예전의 `_doc_index`(인용 → 문서 역추적)는 **지웠다.** 문서가 하나라 소속이
#     자명해져 쓸 자리가 없어졌다. 그 함수는 「모델이 doc_index 를 '문서 0' 이라고
#     써서 성공한 발췌를 우리가 버리던」 사고의 수습이었는데, 사고의 원인 자체가
#     묶음 호출이었다 — 원인을 없애면 수습도 없앤다.
# ══════════════════════════════════════════════════════════════
JSON_OBJ = re.compile(r"\{.*\}", re.S)


def _extract_one(slot: Slot, doc: Document, meter, chars: int, max_items: int,
                 rules: dict | None = None) -> dict:
    """문서 **하나**에 묻는다. 반환은 값이다 — 예외를 올리지 않는다.

    `LLMFailure` 도 값으로 접어 돌려준다. 문서 하나가 죽었다고 슬롯 전체를 죽이면
    「그 문서를 못 읽었다」가 「그 슬롯은 자료가 없다」로 둔갑한다 — 이 파이프라인이
    없애려는 실패다. 다만 **전부 죽으면** 부르는 쪽이 그것을 알아채고 어댑터 상태로
    올린다(키가 죽은 것을 자료 부재로 보고하면 안 된다).
    """
    # **앞에서 자르지 않고 골라 담는다** (판 ㉛B). 어디를 골랐는지는 값으로 남는다 —
    # 「우리가 버렸다」와 「자료가 없다」가 구별되지 않으면 §7 이 거짓이 된다.
    본문, 창_기록 = doc_window.select(doc.text or "", slot, rules or {}, chars)
    try:
        r = _call(meter, "a3_extract", model=EXTRACT_MODEL,
                  input=prompts.render(
                      prompts.EXTRACT, subject=slot.subject, metric=slot.metric,
                      period=slot.period, region=slot.region, max_items=max_items,
                      documents=prompts.render_documents(
                          [dataclasses.replace(doc, text=본문)], len(본문))))
    except LLMFailure as e:
        return {"trace_id": doc.trace_id, "url": doc.url, "status": "llm_failed",
                "note": f"{e.kind}: {e.detail}", "items": [], "_failure": e,
                "창": 창_기록}
    m = JSON_OBJ.search(r.output_text or "")
    try:
        data = json.loads(m.group(0)) if m else {}
    except Exception:
        data = {}
    if data.get("status") != "found" or not data.get("findings"):
        return {"trace_id": doc.trace_id, "url": doc.url, "status": "not_found",
                "note": str(data.get("note", "parse_or_absent"))[:120], "items": [],
                "창": 창_기록}
    # ⚠ **`_doc_index` 가 필요 없다.** 문서가 하나라 인용의 소속이 자명하다.
    #   묶음 시절에는 quote 로 문서를 역추적해야 했고, 못 정하면 그 인용을 버렸다
    #   (조인 버그 E 의 뿌리 · 옛 탈락 지점 D5). 그 탈락 지점이 구조적으로 사라진다.
    items = [FindingItem(quote=str(f.get("quote", ""))[:600],
                         number_raw=str(f.get("number_raw", "")),
                         unit_raw=str(f.get("unit_raw", "")),
                         url=doc.url,
                         context=str(f.get("context", ""))[:300])
             for f in data["findings"][:max_items]]
    return {"trace_id": doc.trace_id, "url": doc.url, "status": "found",
            "note": "", "items": items, "창": 창_기록}


def extract(slot: Slot, docs: list[Document], meter, trace_id: str,
            rules: dict | None = None) -> Finding:
    """문서마다 따로 묻는다 (`extract_mode=per_doc`).

    판 ㉛ 실측이 바꾼 자리다 — 묶음(문서 5개를 한 프롬프트에 넣고 1회)일 때
    발췌에 들어간 44건이 인용 1건을 냈다(2.3%). 모델의 「없습니다」 한 마디로
    5건이 통째로 죽었고, **어느 문서를 실제로 읽었는지 원장에 남지 않았다.**
    """
    wcfg = _wcfg(rules)
    usable = [d for d in docs if d.content_status == "usable" and d.text.strip()]
    if not usable:
        why = ", ".join(sorted({d.content_status if d.http_status == "ok" else d.http_status
                                for d in docs})) or "문서 없음"
        return Finding(slot_id=slot.slot_id, trace_id=trace_id, status="not_found",
                       note=f"쓸 만한 본문 없음 ({why})",
                       extract_log={"picked": [], "cut": [], "docs_usable": 0,
                                    "docs_sent": 0, "chars_total": 0, "chars_sent": 0,
                                    "mode": wcfg.get("extract_mode") or "per_doc",
                                    "model": EXTRACT_MODEL, "per_doc": []})

    # ── 상한과 순서 (판 ㉚ ②) ────────────────────────────────────────
    #   값·순서는 규칙 파일에서 온다(규약 ①). 예전엔 상한이 **코드 상수**였고
    #   자르는 순서가 **목록 순서**였다 — 그런데 직접 주입분은 목록 **맨 뒤**라
    #   **가장 먼저 잘렸다.** `--direct-urls` 의 존재 이유가 「이 문서를 반드시 보게 한다」인데
    #   상한이 그 보장을 **말없이** 깨고 있었다(판 ㉚ 실측: 주입 2건 전부 탈락).
    #   ⚠ 심사 완화가 아니다 — 문턱·점수·가드 무변경. **누가 심사대에 오르는가**의 순서다.
    cap = int(wcfg.get("extract_max_docs") or MAX_DOCS_IN_PROMPT)
    chars = int(wcfg.get("extract_doc_chars") or DOC_CHARS)
    max_items = int(wcfg.get("extract_max_items") or MAX_ITEMS)
    order = wcfg.get("extract_priority") or []

    def _rank(d):
        ch = getattr(d, "channel", "") or "web"
        return order.index(ch) if ch in order else len(order)

    ranked = sorted(usable, key=_rank) if order else usable
    picked, cut = ranked[:cap], ranked[cap:]

    # 문서별 호출. 슬롯 병렬 안에서 다시 병렬이라 동시 실행이 곱해진다 — 알고 두는 값이다.
    results: list[dict] = [None] * len(picked)                      # 순서를 보존한다
    if len(picked) == 1:
        results[0] = _extract_one(slot, picked[0], meter, chars, max_items, rules)
    else:
        with cf.ThreadPoolExecutor(max_workers=min(EXTRACT_WORKERS, len(picked))) as pool:
            futs = {pool.submit(_extract_one, slot, d, meter, chars, max_items, rules): i
                    for i, d in enumerate(picked)}
            for fu in cf.as_completed(futs):
                results[futs[fu]] = fu.result()

    # **전부 LLM 실패면 값으로 접지 않고 올린다.** 키가 죽은 것을 「자료 부재」로
    # 보고하면 §7 이 거짓이 된다 — 부르는 쪽이 어댑터 상태로 올려야 한다(규칙 5).
    failed = [r for r in results if r["status"] == "llm_failed"]
    if failed and len(failed) == len(results):
        raise failed[0]["_failure"]

    log = {
        "picked": [d.trace_id for d in picked],
        "cut": [d.trace_id for d in cut],
        "docs_usable": len(usable), "docs_sent": len(picked),
        "chars_total": sum(len(d.text or "") for d in picked),
        # ⚠ **실제로 보낸 글자 수를 센다.** 예전에는 `min(len, chars)` 로 **추정**했는데,
        #   창 고르기가 들어오면 그 추정이 틀린다 — 도달률이 거짓이 된다(판 ㉛B).
        "chars_sent": sum(int((r.get("창") or {}).get("chars_sent") or 0) for r in results),
        "mode": wcfg.get("extract_mode") or "per_doc",
        # 입력을 어떻게 골랐나 — 창 방식별 문서 수. `funnel.py` 가 이것으로 도달률을 가른다.
        "window_modes": {m: sum(1 for r in results if (r.get("창") or {}).get("mode") == m)
                         for m in sorted({(r.get("창") or {}).get("mode") or "?"
                                          for r in results})},
        "model": EXTRACT_MODEL, "calls": len(picked),
        "per_doc": [{k: v for k, v in r.items() if k in ("trace_id", "url", "status", "note")}
                    | {"items": len(r["items"]), "창": r.get("창") or {}} for r in results],
    }
    items = [it for r in results for it in r["items"]]
    #: 사람이 읽는 한 줄. **세는 것은 `extract_log` 가 하고 여기는 요약만 한다** —
    #  같은 사실을 문자열과 값 두 곳에 두면 갈라진다(백로그 26 이 그 사고였다).
    note = (f"문서 {len(picked)}개 각각 1회 호출 "
            f"(found {sum(1 for r in results if r['status'] == 'found')}"
            f" · 실패 {len(failed)})"
            + (f" · 상한 {cap} 으로 {len(cut)}개 제외" if cut else ""))
    if not items:
        return Finding(slot_id=slot.slot_id, trace_id=trace_id, status="not_found",
                       note=note, extract_log=log)
    return Finding(slot_id=slot.slot_id, trace_id=trace_id, status="found",
                   findings=items, note=note, extract_log=log)


# ══════════════════════════════════════════════════════════════
# 어댑터 전체 — 다른 어댑터와 같은 인터페이스
# ══════════════════════════════════════════════════════════════
def collect(slot: Slot, rules: dict, meter, trace_prefix: str | None = None) -> tuple:
    """(Finding, {trace_id: Document}, [Candidate], adapter_state)

    LLM 이 죽어도 **값으로** 돌려준다. 예외를 올리면 워커 하나가 실행 전체를 죽인다.
    """
    tp = trace_prefix or slot.slot_id
    try:
        cands = search(slot, meter, tp, rules)
    except LLMFailure as e:
        fm = rules["adapters"]["failure_map"][e.kind]
        return (Finding(slot_id=slot.slot_id, trace_id=f"{tp}-search",
                        status=fm["finding_status"], note=f"{e.kind}: {e.detail}"),
                {}, [], fm["adapter_state"])
    cands, filtered = url_filter(cands, rules)
    docs = [fetch(c, rules) for c in cands]
    cands = cands + filtered          # 기록에는 **걸린 것도 함께** 남긴다 (규칙 5)
    dmap = {d.trace_id: d for d in docs}
    # extract 는 슬롯 하나에 1회. 인용의 trace_id 는 그 문서의 것을 물려받는다.
    try:
        finding = extract(slot, docs, meter, trace_id=f"{tp}-extract", rules=rules)
    except LLMFailure as e:
        fm = rules["adapters"]["failure_map"][e.kind]
        return (Finding(slot_id=slot.slot_id, trace_id=f"{tp}-extract",
                        status=fm["finding_status"], note=f"{e.kind}: {e.detail}"),
                dmap, cands, fm["adapter_state"])
    # ⚠ 예전에는 여기서 `finding.trace_id` 를 **첫 인용의 문서**로 덮어 A4 가 본문을 찾게 했다.
    #   인용이 여러 문서에서 오면 두 번째부터는 첫 문서로 대조되는 조인 버그 E 의 원인이었다.
    #   지금은 `normalize` 도 `grade` 도 **URL 로** 문서를 찾으므로 이 hack 이 필요 없다.
    return finding, dmap, cands, "ok"
