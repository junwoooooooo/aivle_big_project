# -*- coding: utf-8 -*-
"""web 어댑터 — `plan_query(✗) → search(✓) → fetch(✗) → extract(✓)`

LLM 은 search 와 extract 둘뿐이다. fetch 가 순수 HTTP 라야
"모델이 snippet 으로 추측했다"가 **구조적으로** 불가능해진다.

extract 는 **슬롯 단위로 문서를 묶어 1회** 호출한다 (문서마다 호출하면 비용이 10배).
어느 문서에서 나온 인용인지는 `doc_index` 로 지목하게 하고, 그 번호로 url 을 되돌린다.
"""
from __future__ import annotations

import json, re
from datetime import datetime

import requests
import trafilatura
from trafilatura.metadata import extract_metadata

import pdf_text
import prompts
from base import AdapterResult, load_env_key, make_document
from schema import Candidate, Document, Finding, FindingItem, Slot

NAME = "web"
SEARCH_MODEL = "gpt-5.4-nano"
EXTRACT_MODEL = "gpt-4o-mini"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0 Safari/537.36")

MAX_CANDIDATES = 6
MAX_DOCS_IN_PROMPT = 5
DOC_CHARS = 6000
MAX_ITEMS = 3


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
    for qi, _unused_query in enumerate(plan_query(slot)):
        # `from_query` 에는 **실제로 모델이 받은 것**만 적는다. plan_query 의 문자열을 적으면
        # 나가지도 않은 검색어가 원장에 남아 진단을 오염시킨다(실제로 그랬다).
        q = (f"[{variant}·표본{qi + 1}] {slot.subject} / {slot.metric} / 기간 {slot.period} / "
             f"지역 {slot.region} (검색어는 모델이 정함)")
        r = _call(meter, "a3_search", model=SEARCH_MODEL, tools=[{"type": "web_search"}],
                         input=prompts.render(
                             tpl, subject=slot.subject, metric=slot.metric,
                             period=slot.period, region=slot.region,
                             # 가설 D — 슬롯 유형마다 원출처가 다르다 (12-2)
                             claim_type_hint=prompts.claim_type_hint(slot.claim_type)))
        cits = _citations(r)
        if not cits:
            out.append(Candidate(slot_id=slot.slot_id, trace_id=f"{trace_prefix}-q{qi}-u0",
                                 url="", from_query=q, status="no_result"))
            continue
        for ui, c in enumerate(cits[:MAX_CANDIDATES]):
            out.append(Candidate(slot_id=slot.slot_id, trace_id=f"{trace_prefix}-q{qi}-u{ui}",
                                 url=c["url"], title=c["title"], from_query=q))
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
        if not ptext:
            return Document(**base, http_status="ok",
                            content_status=pdf_cfg.get("unreadable_status",
                                                       pdf_text.UNREADABLE),
                            http_code=r.status_code, error=why)
        status, tlen, digits = classify_content(ptext, rules["scoring"])
        return Document(**base, text=ptext, http_status="ok", content_status=status,
                        text_len=tlen, digit_count=digits, http_code=r.status_code)

    if "html" not in ctype:
        return Document(**base, http_status="not_html", content_status="empty",
                        http_code=r.status_code)

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
# extract — LLM ✓. **슬롯 단위 1회.** doc_index 로 url 을 되돌린다.
# ══════════════════════════════════════════════════════════════
JSON_OBJ = re.compile(r"\{.*\}", re.S)
_DIGIT = re.compile(r"\d+")


def _doc_index(item: dict, picked: list):
    """인용이 어느 문서에서 왔는지 정한다. **모델의 doc_index 를 믿되, 없으면 본문으로 찾는다.**

    처음엔 doc_index 가 int 가 아니면 통째로 버렸는데, 모델이 값을 제대로 뽑아 놓고도
    번호를 "0" 이나 "문서 0" 으로 쓰는 바람에 **성공한 발췌를 우리가 버리고 있었다**(실측).
    번호가 이상하면 인용문이 실제로 들어 있는 문서를 찾는다 — 딱 하나면 그것이다.
    """
    idx = item.get("doc_index")
    if isinstance(idx, bool):
        idx = None
    if isinstance(idx, str):
        m = _DIGIT.search(idx)
        idx = int(m.group(0)) if m else None
    if isinstance(idx, int) and 0 <= idx < len(picked):
        return idx
    if len(picked) == 1:
        return 0
    q = re.sub(r"\s+", "", str(item.get("quote") or ""))
    if len(q) >= 6:
        hits = [i for i, d in enumerate(picked)
                if q in re.sub(r"\s+", "", d.text or "")]
        if len(hits) == 1:
            return hits[0]
    return None


def extract(slot: Slot, docs: list[Document], meter, trace_id: str,
            rules: dict | None = None) -> Finding:
    usable = [d for d in docs if d.content_status == "usable" and d.text.strip()]
    if not usable:
        why = ", ".join(sorted({d.content_status if d.http_status == "ok" else d.http_status
                                for d in docs})) or "문서 없음"
        return Finding(slot_id=slot.slot_id, trace_id=trace_id, status="not_found",
                       note=f"쓸 만한 본문 없음 ({why})")

    # ── 상한과 순서 (판 ㉚ ②) ────────────────────────────────────────
    #   값·순서는 규칙 파일에서 온다(규약 ①). 예전엔 상한이 **코드 상수**였고
    #   자르는 순서가 **목록 순서**였다 — 그런데 직접 주입분은 목록 **맨 뒤**라
    #   **가장 먼저 잘렸다.** `--direct-urls` 의 존재 이유가 「이 문서를 반드시 보게 한다」인데
    #   상한이 그 보장을 **말없이** 깨고 있었다(판 ㉚ 실측: 주입 2건 전부 탈락).
    #   ⚠ 심사 완화가 아니다 — 문턱·점수·가드 무변경. **누가 심사대에 오르는가**의 순서다.
    wcfg = ((rules or {}).get("adapters", {}).get("web") or {})
    cap = int(wcfg.get("extract_max_docs") or MAX_DOCS_IN_PROMPT)
    order = wcfg.get("extract_priority") or []

    def _rank(d):
        ch = getattr(d, "channel", "") or "web"
        return order.index(ch) if ch in order else len(order)

    ranked = sorted(usable, key=_rank) if order else usable
    picked = ranked[:cap]
    cut = ranked[cap:]
    dropped = len(cut)
    r = _call(meter, "a3_extract", model=EXTRACT_MODEL,
                     input=prompts.render(
                         prompts.EXTRACT, subject=slot.subject, metric=slot.metric,
                         period=slot.period, region=slot.region, max_items=MAX_ITEMS,
                         documents=prompts.render_documents(
                             picked, int(wcfg.get("extract_doc_chars") or DOC_CHARS))))
    m = JSON_OBJ.search(r.output_text or "")
    try:
        data = json.loads(m.group(0)) if m else {}
    except Exception:
        data = {}

    # 잘린 것은 **개수가 아니라 trace_id 로** 남긴다(백로그 26). 개수만 적으면
    # 「어느 문서가 빠졌나」를 `usable[:5]` 로 역산해야 하고, 그건 기록이 아니라 추론이다.
    note = f"문서 {len(picked)}개 묶어 1회 호출" + (
        f" (상한 {cap} 으로 {dropped}개 제외: {[d.trace_id for d in cut]})" if dropped else "")
    if data.get("status") != "found" or not data.get("findings"):
        return Finding(slot_id=slot.slot_id, trace_id=trace_id, status="not_found",
                       note=f"{str(data.get('note', 'parse_or_absent'))[:120]} / {note}")

    items: list[FindingItem] = []
    dropped_no_doc = 0
    for f in data["findings"][:MAX_ITEMS]:
        idx = _doc_index(f, picked)
        if idx is None:
            dropped_no_doc += 1
            continue                      # 어느 문서에서 왔는지 못 정하면 대조가 불가능하다
        items.append(FindingItem(quote=str(f.get("quote", ""))[:600],
                                 number_raw=str(f.get("number_raw", "")),
                                 unit_raw=str(f.get("unit_raw", "")),
                                 url=picked[idx].url,
                                 context=str(f.get("context", ""))[:300]))
    if not items:
        return Finding(slot_id=slot.slot_id, trace_id=trace_id, status="not_found",
                       note=f"인용 {dropped_no_doc}건이 어느 문서인지 확정 불가 / {note}")
    return Finding(slot_id=slot.slot_id, trace_id=trace_id, status="found",
                   findings=items, note=note)


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
