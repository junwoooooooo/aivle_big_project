# -*- coding: utf-8 -*-
"""DART 어댑터 — 공시 재무 항목을 직접 받는다. **LLM 0회.**

    Slot(corp_name="...") → corp_code 조회 → 재무제표 → (Finding, Document)

corp_code 목록(corpCode.xml)은 zip 으로 한 번 받아 캐시한다.
회사명이 목록에 없으면 **not_found** 다 — 시스템 오류가 아니라 조사 결과다.
"""
from __future__ import annotations

import io, json, os, re, zipfile

import requests

from base import AdapterResult, fail, get_json, load_env_key, make_document, make_finding
from schema import FindingItem, Slot


def _year_of(period: str):
    """`slot.period` 에서 사업연도. **버그 H 수정(2026-08-07) — 정의가 없어 조회 성공
    경로가 NameError 로 즉사했다.** 못 읽으면 None → 호출부가 `bad_stat_code` 로 멈춘다.
    자릿수 경계는 `a_desk._YEAR` 와 같은 이유다(값 안의 '2026' 을 집지 않는다)."""
    m = re.search(r"(?<!\d)(?:19|20)\d{2}(?!\d)", period or "")
    return int(m.group(0)) if m else None

NAME = "dart"
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_cache_corpcode.json")


def _corp_index(key: str, rules: dict) -> tuple:
    """회사명 → corp_code. (index, err_kind, detail)"""
    if os.path.exists(CACHE):
        try:
            return json.load(io.open(CACHE, encoding="utf-8")), None, ""
        except Exception:
            pass
    cfg = rules["adapters"]["dart"]
    try:
        r = requests.get(cfg["corp_code_zip"], params={"crtfc_key": key},
                         timeout=rules["adapters"]["retry"]["timeout_sec"])
    except requests.Timeout:
        return None, "timeout", "corpCode.xml"
    except Exception as e:
        return None, "http_error", type(e).__name__
    if r.status_code >= 400:
        return None, "http_error", f"HTTP {r.status_code}"
    if r.content[:2] != b"PK":                      # zip 이 아니면 인증 실패 XML 이다
        body = r.content.decode("utf-8", "ignore")[:200]
        return None, ("auth_failed" if "013" in body or "인증" in body else "parse_error"), body
    try:
        with zipfile.ZipFile(io.BytesIO(r.content)) as z:
            xml = z.read(z.namelist()[0]).decode("utf-8")
    except Exception as e:
        return None, "parse_error", type(e).__name__

    idx = {}
    for m in re.finditer(r"<list>(.*?)</list>", xml, re.S):
        blk = m.group(1)
        name = re.search(r"<corp_name>(.*?)</corp_name>", blk, re.S)
        code = re.search(r"<corp_code>(.*?)</corp_code>", blk, re.S)
        stock = re.search(r"<stock_code>(.*?)</stock_code>", blk, re.S)
        if name and code:
            n = name.group(1).strip()
            # 상장사를 우선한다 (stock_code 가 있는 쪽)
            if n not in idx or (stock and stock.group(1).strip()):
                idx[n] = code.group(1).strip()
    io.open(CACHE, "w", encoding="utf-8").write(json.dumps(idx, ensure_ascii=False))
    return idx, None, ""


def collect(slot: Slot, rules: dict, trace_id: str | None = None,
            key: str | None = None, year: int | None = None) -> AdapterResult:
    tid = trace_id or f"{slot.slot_id}-dart"
    cfg = rules["adapters"]["dart"]

    key = load_env_key("DART_API_KEY") if key is None else key   # "" 는 '키 없음'이다
    if not key:
        return fail(slot, tid, "no_key", rules, "DART_API_KEY 없음", channel="dart_api")
    if not slot.corp_name:
        return fail(slot, tid, "bad_stat_code", rules, "corp_name 없음", channel="dart_api")

    idx, err, detail = _corp_index(key, rules)
    if err:
        return fail(slot, tid, err, rules, detail, channel="dart_api")

    corp_code = idx.get(slot.corp_name)
    if not corp_code:
        # ⚠ 「미등록」과 「표기 불일치」는 **다른 사실이다.** 예전엔 둘 다 empty_result 로
        #   뭉뚱그려 "그 회사는 공시 안 함" 처럼 읽혔다. **매칭 규칙은 완화하지 않고**
        #   (완전일치 유지) 사유만 가른다 — 이름이 비슷한 후보가 있으면 표기 문제일 수 있다.
        want = (slot.corp_name or "").replace(" ", "")
        near = [n for n in idx if want and (want in n.replace(" ", "")
                                            or n.replace(" ", "") in want)][:5]
        if near:
            detail = (f"corp_name '{slot.corp_name}' 완전일치 없음 — **미조회(표기 불일치 가능)**. "
                      f"비슷한 등록명: {near}")
        else:
            detail = (f"corp_name '{slot.corp_name}' — **조회됨·미등록**(DART 목록에 "
                      f"비슷한 이름조차 없다)")
        return fail(slot, tid, "empty_result", rules, detail, channel="dart_api")

    src_url = cfg["source_url_template"].format(corp_code=corp_code)
    y = year or _year_of(slot.period or "")
    if not y:
        return fail(slot, tid, "bad_stat_code", rules, f"period 에서 연도 해석 불가: {slot.period!r}",
                    url=src_url, channel="dart_api")

    data, err, detail = get_json(
        f"{cfg['base']}/fnlttSinglAcntAll.json",
        {"crtfc_key": key, "corp_code": corp_code, "bsns_year": str(y),
         "reprt_code": cfg["report_code"], "fs_div": "CFS"}, rules)
    if err:
        return fail(slot, tid, err, rules, detail, url=src_url, channel="dart_api")

    status = str((data or {}).get("status", ""))
    if status in ("010", "011", "012", "020", "021"):       # 인증·한도 계열
        return fail(slot, tid, "auth_failed", rules, f"status {status} {data.get('message')}",
                    url=src_url, channel="dart_api")
    if status == "013":                       # 조회된 데이터 없음 → 조사 결과다
        return fail(slot, tid, "empty_result", rules,
                    f"status 013 {(data or {}).get('message')}",
                    url=src_url, channel="dart_api")
    if status != "000":
        # 분류표에 없는 status — 모르는 상태는 보수적으로 멈춘다
        return fail(slot, tid, "unknown_code", rules,
                    f"status={status} {(data or {}).get('message')}",
                    url=src_url, channel="dart_api")

    rows = data.get("list") or []
    if not rows:
        return fail(slot, tid, "empty_result", rules, "list 비어 있음",
                    url=src_url, channel="dart_api")

    rule = _account_rule(slot.metric, cfg)
    if rule is None:
        # **표가 못 맞추면 조용히 전부 통과가 아니라 멈춘다.** 예전에는 여기서 빈 집합이
        # 되어 가드가 풀리고 재무상태표 191행이 다 통과했다(full-04: 당기법인세자산이
        # 「경쟁사의 매출」 확인됨 5점). 규칙의 구멍은 조사 결과가 아니라 우리 버그다.
        return fail(slot, tid, "unmapped_metric", rules,
                    f"metric '{slot.metric}' 이 dart.accounts.by_metric 에 없음",
                    url=src_url, channel="dart_api")

    text = json.dumps(rows, ensure_ascii=False, indent=1)
    items: list[FindingItem] = []
    spare: list[FindingItem] = []          # 계정이 안 맞는 것 — 버리지 않고 격리용으로 남긴다

    def _item(row, aid, sj, nm, amount):
        quote = json.dumps(row, ensure_ascii=False)
        if quote not in text:
            quote = f'"thstrm_amount": "{row.get("thstrm_amount")}"'
        return FindingItem(
            quote=quote, number_raw=amount, unit_raw="원", url=src_url,
            account_id=aid, sj_div=sj,
            # DART 가 주는 것은 **회사의** 재무제표다. 시장 안의 매출이 아니라
            # 전사 매출이므로 꼬리표를 붙여 보낸다 — 원장·보고서까지 살아간다.
            scope="company_total",
            context=f"{slot.corp_name} {nm} {row.get('bsns_de') or y}년 "
                    f"{row.get('thstrm_nm') or ''}")

    for row in rows:
        nm = (row.get("account_nm") or "").strip()
        aid = (row.get("account_id") or "").strip()
        sj = (row.get("sj_div") or "").strip()
        amount = (row.get("thstrm_amount") or "").replace(",", "")
        if not amount or not re.match(r"^-?\d+$", amount):
            continue
        # 계정은 **account_id 로** 가른다. 한글 계정명은 회사마다 다르다(카페24='영업수익')
        if aid in rule["account_ids"] and sj in rule["sj_div"]:
            items.append(_item(row, aid, sj, nm, amount))
            if len(items) >= 5:
                break
        elif len(spare) < cfg["accounts"].get("max_mismatch_items", 3):
            spare.append(_item(row, aid, sj, nm, amount))

    # 맞는 계정이 하나도 없으면 **빈손으로 넘기지 않는다.** 안 맞는 것을 몇 건 실어 보내
    # A4 가 account_mismatch 로 격리하게 한다 — 실패는 값이다(절대규칙 5).
    if not items:
        items = spare

    if not items:
        return fail(slot, tid, "empty_result", rules,
                    f"'{slot.metric}' 에 해당하는 계정을 찾지 못함", url=src_url, channel="dart_api")

    doc = make_document(slot, tid, src_url, text, "dart_api", published_at=f"{y}-12-31")
    return AdapterResult(make_finding(slot, tid, items), doc, adapter_state="ok")


def _account_rule(metric: str, cfg: dict) -> dict | None:
    """slot.metric → 계정 규칙. **규칙의 낱말이 metric 안에 들어 있으면** 그것이다.

    방향이 중요하다. 반대로(표의 키 == metric) 하면 A1 이 쓰는 자유 서술
    metric('경쟁사의 매출')에 절대 안 걸린다 — 이번 구멍의 직접 원인이었다.
    못 맞추면 None 을 내고 호출부가 **멈춘다.** 빈 집합을 내면 가드가 풀린다.
    """
    for r in (cfg.get("accounts") or {}).get("by_metric") or []:
        if any(w in (metric or "") for w in r.get("match") or []):
            return r
    return None
