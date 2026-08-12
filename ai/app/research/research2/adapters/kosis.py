# -*- coding: utf-8 -*-
"""KOSIS 어댑터 — 통계표 값을 직접 받는다. **LLM 0회.**

    Slot(stat_code="orgId/tblId") → (Finding, Document)

`stat_code` 는 단계 5 에서 **사람이 손으로** 넣는다.
A1 이 올바른 통계표 ID 를 뽑을 수 있는지는 별개 문제이고 단계 7 에서 따로 확인한다.
여기서 섞으면 나중에 "어댑터가 틀린 건지 A1 이 잘못 뽑은 건지" 구분되지 않는다.
"""
from __future__ import annotations

import json
import re

from base import AdapterResult, fail, get_json, load_env_key, make_document, make_finding
from schema import FindingItem, Slot

NAME = "kosis"


# ══════════════════════════════════════════════════════════════
# 통계표 ID 찾기 — **LLM 0회.** 모델은 '무엇을 찾는지'만 내고 ID 는 여기서 찾는다.
# (모델이 못하는 일을 안 시키는 게 프롬프트를 고치는 것보다 확실하다)
# ══════════════════════════════════════════════════════════════
_TOKEN = re.compile(r"[가-힣A-Za-z]{2,}")


def _tokens(*texts) -> set:
    out = set()
    for t in texts:
        out |= set(_TOKEN.findall(str(t or "")))
    return out


# 표 이름에 수록 기간이 적혀 있다: "…사업체수, 종사자수(’06~ )" / "(’20~ )".
# 슬롯 연도를 담지 못하는 표를 먼저 열면 err 30(데이터 없음)만 반복한다.
_NAME_PERIOD = re.compile(r"[’'(](\d{2})\s*~")


def _covers_year(name: str, year: int | None) -> int:
    """0 = 그 연도를 담을 만함(좋음), 1 = 아님. 이름에 기간 표기가 없으면 0(중립)."""
    if not year:
        return 0
    m = _NAME_PERIOD.search(name or "")
    if not m:
        return 0
    start = 2000 + int(m.group(1))
    return 0 if year >= start else 1


_SIDO = ("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원",
         "충청북도", "충청남도", "충북", "충남", "전북", "전라남도", "전남", "경상북도",
         "경북", "경상남도", "경남", "제주")


def _slot_year(slot: Slot):
    m = re.search(r"(?:19|20)\d{2}", slot.period or "")
    return int(m.group(0)) if m else None


def _queries(slot: Slot) -> list:
    """검색어 여러 개. 하나로는 시도별 표만 돌아온다(실측)."""
    subj, met, reg = (slot.subject or ""), (slot.metric or ""), (slot.region or "")
    national = (not reg) or reg in ("대한민국", "전국")
    qs = [f"{subj} {met}".strip()]
    qs.append(f"{'시도' if national else reg} 산업 {met}".strip())
    qs.append(met)
    seen, out = set(), []
    for q in qs:
        if q and q not in seen:
            seen.add(q)
            out.append(q)
    return out


def search_tables(slot: Slot, rules: dict, key: str) -> list:
    """통계표 후보를 모아 정렬한다. 채택은 하지 않는다 — 채택은 메타 확인으로."""
    cfg = rules["adapters"]["kosis"]
    rc = cfg["resolve"]
    national = (slot.region or "") in ("", "대한민국", "전국")
    want = _tokens(slot.subject, slot.metric)

    seen, cands = set(), []
    for q in _queries(slot):
        data, err, _ = get_json(cfg["search_base"],
                                {"method": "getList", "apiKey": key, "searchNm": q,
                                 "format": "json", "jsonVD": "Y"}, rules)
        if err or not isinstance(data, list):
            continue
        for row in data[:rc["max_candidates"]]:
            tbl = str(row.get("TBL_ID") or "")
            if tbl in seen:
                continue
            seen.add(tbl)
            name = row.get("TBL_NM") or ""
            # 표 이름에 박힌 시도명 — 전국 슬롯이면 감점, 해당 지역 슬롯이면 가점
            sido_in_name = next((x for x in _SIDO if x in name), None)
            if national:
                region_fit = 1 if sido_in_name else 0        # 0 = 좋음
            else:
                region_fit = 0 if (sido_in_name and sido_in_name in slot.region) else 1
            cands.append({"org_id": str(row.get("ORG_ID") or ""), "tbl_id": tbl, "name": name,
                          "overlap": sum(1 for w in want if w in name),
                          "region_fit": region_fit, "from_query": q,
                          "period_fit": _covers_year(name, _slot_year(slot))})
    # 결정론 정렬 — 지역 적합 → 이름 겹침 → 통계청 우선 → TBL_ID 사전순
    # 기간을 담는 표 → 지역 적합 → 이름 겹침 → 통계청 → TBL_ID (전부 결정론)
    cands.sort(key=lambda c: (c["period_fit"], c["region_fit"], -c["overlap"],
                              0 if c["org_id"] in rc["prefer_org_ids"] else 1, c["tbl_id"]))
    return cands


def _norm(x: str) -> str:
    return (x or "").replace(" ", "").replace("·", "").strip()


def _find_item(items: dict, want_phrase: str) -> tuple:
    """항목 이름을 **구 단위**로 찾는다. (축 id, 항목 id, 항목명, 강도)
    강도: 2=완전일치, 1=포함. 토큰 조각만으로는 잡지 않는다 — '커피' 로 '커피가공업' 을 잡으면 안 된다."""
    w = _norm(want_phrase)
    if not w:
        return None, None, None, 0
    best = (None, None, None, 0)
    for oid, ax in items.items():
        for it in ax["items"]:
            n = _norm(it["name"])
            if n == w:
                return oid, it["id"], it["name"], 2
            if len(w) >= 3 and (w in n or n in w) and best[3] < 1:
                best = (oid, it["id"], it["name"], 1)
    return best


def resolve_stat_code(slot: Slot, rules: dict, key: str) -> tuple:
    """(stat_code, why, candidates).

    이름 겹침은 후보 **정렬**에만 쓰고, 채택은 **메타 확인**으로 한다:
      ① 찾는 항목이 구 단위로 있는가  ② 요청 지역(또는 전국)이 지역 축에 있는가
    """
    rc = rules["adapters"]["kosis"]["resolve"]
    # 표 **검색**도 subject 로 돈다 — 「개인 금융 관리」로는 인구 표가 후보에 들어오지도
    # 않는다. 등재된 표 계열은 **0단계 프로브로 실재를 확인한 표를 직접** 후보로 놓는다.
    _pre = []
    _tsx = rc.get("표_계열") or {}
    if _tsx.get("enabled"):
        for _nm, _sp in (_tsx.get("계열") or {}).items():
            if _nm.startswith("_") or (slot.metric or "") not in (_sp.get("match_metrics") or []):
                continue
            for _code in (_sp.get("tables") or []):
                _o, _t = _code.split("/", 1)
                _pre.append({"org_id": _o, "tbl_id": _t, "name": f"{_nm} 등재표 {_code}"})
            break
    cands = _pre + search_tables(slot, rules, key)
    if not cands:
        return None, "검색 결과 없음", []

    # ── 표 계열 분기 (판 ㉕, 백로그 44) ──────────────────────────────
    # 표마다 **무엇이 축인가**가 다르다. 사업체 표는 업종이 항목 축이라 `subject` 로 찾히지만
    # **인구 표에는 subject 축이 아예 없다**(0단계 프로브 실측: ITEM × 행정구역 × 5세별).
    # 그 계열은 subject 대신 **ITEM 축의 이름**으로 맞춘다. ⚠ **경로 추가지 교체가 아니다** —
    # 등재된 계량에만 걸리고 나머지는 전부 기존 subject 경로로 간다.
    _ts = rc.get("표_계열") or {}
    찾을_이름, 표계열 = slot.subject, None
    if _ts.get("enabled"):
        for nm, spec in (_ts.get("계열") or {}).items():
            if nm.startswith("_"):
                continue
            if (slot.metric or "") in (spec.get("match_metrics") or []):
                찾을_이름, 표계열 = spec.get("항목_이름") or slot.subject, nm
                break

    tried, verified = [], []
    for c in cands[:rc["verify_top_n"]]:
        axes, items = table_meta(c["org_id"], c["tbl_id"], rules, key)
        if not axes:
            tried.append(f"{c['tbl_id']}:메타없음")
            continue
        _, _, hit_name, strength = _find_item(items, 찾을_이름)
        if 표계열 and strength >= 2:
            # **해석 근거를 값으로 남긴다** — 어느 계열 경로로 무엇을 맞췄는지.
            hit_name = f"{hit_name} (표 계열 '{표계열}' · subject 축 없음)"
        if strength < 2:
            # 세는 대상이 **「항목」 축**에 있는 표(인구·거래액)를 위한 별칭 조회.
            # 느슨한 포함 비교가 아니라 **규칙에 적힌 짝만** 본다 — 못 맞추면 그대로 멈춘다.
            for alias in ((rc.get("subject_별칭") or {}).get("map") or {}).get(
                    (slot.subject or "").strip(), [])                     if (rc.get("subject_별칭") or {}).get("enabled") else []:
                _, _, hit_name, strength = _find_item(items, alias)
                if strength >= 2:
                    hit_name = f"{hit_name} (별칭 '{slot.subject}')"
                    break
        if strength < 2:
            tried.append(f"{c['tbl_id']}:항목{'유사' if strength else '없음'}")
            continue
        # 지역 축이 요청 지역을 담고 있는가 (없으면 다른 표를 먼저 본다)
        region_ok = True
        for oid, ax in items.items():
            nm = ax["obj_nm"] or ""
            if "지역" in nm or "행정" in nm or "시도" in nm:
                names = [_norm(i["name"]) for i in ax["items"]]
                region_ok = any(_norm(r) in names for r in
                                ([slot.region] + rc["region_fallbacks"]))
                break
        code = f"{c['org_id']}/{c['tbl_id']}"
        why = f"항목 '{hit_name}' 완전일치 · 표 '{c['name'][:24]}'"
        if region_ok:
            verified.append((code, why + " · 지역 축 확인"))
            if len(verified) >= rc.get("verify_keep", 4):
                break
            continue
        # 지역이 안 맞으면 **채택하지 않는다.** 인천 숫자를 전국 값으로 내보내는 것보다 공백이 낫다.
        tried.append(f"{c['tbl_id']}:지역불일치")

    if verified:
        return verified[0][0], verified[0][1], verified
    return None, f"'{slot.subject}' 를 담은 표를 못 찾음: {tried[:5]}", []



# ══════════════════════════════════════════════════════════════
# 분류 축 좁히기 — **LLM 0회.** 항목 이름으로 코드를 역검색한다.
#   A1 이 낸 subject_code(KSIC) 를 믿지 않는다. stat_code 가 0/10 이었으므로 KSIC 도 못 믿는다.
#   KOSIS 산업 항목 ID 는 'I56221' 처럼 KSIC 에 대분류 문자가 붙는다 — 그대로 쓰면 안 맞는다.
# ══════════════════════════════════════════════════════════════
_META_CACHE: dict = {}


def table_meta(org_id: str, tbl_id: str, rules: dict, key: str):
    """(축 순서, 축별 항목들). 축 순서는 objL1..objLn 에 그대로 대응한다."""
    ck = f"{org_id}/{tbl_id}"
    if ck in _META_CACHE:
        return _META_CACHE[ck]
    cfg = rules["adapters"]["kosis"]
    data, err, detail = get_json(cfg["meta_base"],
                                 {"method": "getMeta", "apiKey": key, "orgId": org_id,
                                  "tblId": tbl_id, "type": "ITM", "format": "json",
                                  "jsonVD": "Y"}, rules)
    if err or not isinstance(data, list):
        return None, None
    axes, items = [], {}
    for row in data:
        oid = row.get("OBJ_ID")
        if oid not in items:
            items[oid] = {"obj_id": oid, "obj_nm": row.get("OBJ_NM"), "items": []}
            axes.append(oid)
        items[oid]["items"].append({"id": str(row.get("ITM_ID")), "name": row.get("ITM_NM") or ""})
    _META_CACHE[ck] = (axes, items)
    return axes, items


def _pick(items: list, wants: set, fallback_names=(), unique_contains=True) -> tuple:
    """항목 이름으로 코드를 고른다. **(id, why, tier)** — tier ∈ exact|contains|fallback|none.

    부르는 쪽이 **어느 계층에서 걸렸는지 알아야** 폴백을 기록하고 주 축에서 거부할 수 있다.
    포함 매칭은 후보가 **둘 이상이면 고르지 않는다** — 「커피」가 「커피 전문점」과
    「커피가공업」에 동시에 걸리면 아무거나 집는 것이 곧 오집이다.
    """
    for w in sorted(wants, key=len, reverse=True):
        for it in items:
            if it["name"].replace(" ", "") == w.replace(" ", ""):
                return it["id"], f"이름 일치: {it['name']}", "exact"
    for w in sorted(wants, key=len, reverse=True):
        cand = [it for it in items if w in it["name"].replace(" ", "")]
        if len(cand) > 1 and unique_contains:
            return None, f"이름 포함 후보 {len(cand)}개로 모호: {[c['name'] for c in cand[:3]]}", "none"
        if len(cand) == 1:
            return cand[0]["id"], f"이름 포함: {cand[0]['name']}", "contains"
    for fb in fallback_names:
        for it in items:
            if it["name"].replace(" ", "") == fb:
                return it["id"], f"기본값: {it['name']}", "fallback"
    return None, "해당 항목 없음", "none"


def resolve_axes(slot: Slot, org_id: str, tbl_id: str, rules: dict, key: str) -> tuple:
    """(itmId, {objL1: code, ...}, why, 표기_치환). 축을 **하나도 빠짐없이** 채운다 — 빠지면 err 20.

    네 번째 값이 **치환 기록**이다. 별칭으로 다른 이름의 항목을 집었으면 그 사실을
    **여기서** 값으로 낸다 — 치환이 일어난 자리이기 때문이다. 예전에는 `why` 문자열
    안에만 있었고(「(별칭 'X')」), 문자열은 셀 수도 조건으로 쓸 수도 없었다.
    """
    axes, items = table_meta(org_id, tbl_id, rules, key)
    if not axes:
        return None, None, "메타 조회 실패", []

    cfg = rules["adapters"]["kosis"]["resolve"]
    # 구(phrase) 를 먼저 본다 — 토큰만 보면 '커피' 로 '커피가공업' 을 잡는다
    want_subject = {slot.subject or ""}
    # **별칭을 축 선택에도 쓴다** (판 ⑯ 수리). 이 표는 `resolve_stat_code`(표 찾기)에만
    # 쓰이고 여기(축 고르기)에는 안 쓰였다 — 그래서 **드라이런은 「애완용품」을 맞추고
    # 수집은 못 맞춰 「합계」로 폴백**했다(`pet-treat-04` 274조). **같은 물음을 두 곳이
    # 각자 풀면 두 번 갈라진다** — 판 ⑫ 키 분열·판 ⑬ 라우팅 분열과 같은 계보다.
    _al = cfg.get("subject_별칭") or {}
    aliases: list = []
    if _al.get("enabled"):
        aliases = list((_al.get("map") or {}).get((slot.subject or "").strip(), []))
        want_subject |= set(aliases)
    표기_치환: list = []
    want_metric = {slot.metric or ""} | set(_TOKEN.findall(slot.metric or ""))
    # **표 계열의 항목 이름도 축 선택에 쓴다** (판 ㉕ — 판 ⑯ 계보 **다섯 번째**).
    # 표 찾기(`resolve_stat_code`)에는 `표_계열` 을 줬는데 여기엔 안 줘서, 인구 표에서
    # 「인구」가 **총인구수·남자인구수·여자인구수 셋 다에 포함**돼 「모호」로 죽었다
    # (`ledger-01` 실측 · 확인됨 0). **같은 물음을 두 곳이 각자 풀면 두 번 갈라진다.**
    _ts = cfg.get("표_계열") or {}
    if _ts.get("enabled"):
        for _nm, _sp in (_ts.get("계열") or {}).items():
            if _nm.startswith("_") or (slot.metric or "") not in (_sp.get("match_metrics") or []):
                continue
            # 정확 이름 **하나**로 좁힌다 — 토큰을 남겨 두면 모호가 그대로 재발한다.
            want_metric = {_sp.get("항목_이름") or slot.metric}
            break
    want_region = {slot.region or ""}

    uc = cfg.get("contains_match_unique", True)
    main_kw = cfg.get("main_axis_keywords") or []
    itm_id, itm_why = None, ""
    objs, why, defaults = {}, [], {}
    n = 0
    for oid in axes:
        axis = items[oid]
        nm = axis["obj_nm"] or ""
        if oid == "ITEM" or nm == "항목":
            # 항목은 **주 축**이다 — item_fallbacks 는 비어 있고, 못 고르면 실패한다.
            itm_id, itm_why, _t = _pick(axis["items"], want_metric,
                                        cfg.get("item_fallbacks", []), uc)
            if itm_id is None:
                return None, None, f"항목 축 실패({itm_why}) — 폴백하지 않는다", []
            continue
        n += 1
        is_region = ("지역" in nm or "행정" in nm or "시도" in nm)
        is_main = any(k in nm for k in main_kw)
        # **주 축은 폴백 없이 fail-closed.** 못 고르면 다른 집계를 가져오는 것이라
        # '맞은 것처럼 보이는 오답' 이 된다.
        fb = () if is_main else (cfg["region_fallbacks"] if is_region else cfg["axis_fallbacks"])
        code, w, tier = _pick(axis["items"], want_region if is_region else want_subject, fb, uc)
        if code is None:
            # **`ALL` 로 채우지 않는다.** 축을 못 고르면 수집 실패다(옛 fail-open 제거).
            return None, None, f"'{nm}' 축 실패({w}) — ALL 로 채우지 않는다", []
        # **치환을 여기서 값으로 잡는다.** 집은 항목 이름이 슬롯 표기가 아니라 별칭이면,
        # 우리는 **다른 이름의 집계를 가져온 것**이다. 그 사실이 사실(Fact)까지 가야
        # 상위 카테고리 울타리를 붙일 수 있다 — `must_contain` 이 비어 있어도.
        if not is_region and aliases:
            picked = next((i["name"] for i in axis["items"] if i["id"] == code), "")
            if _norm(picked) != _norm(slot.subject or "") and \
                    any(_norm(picked) == _norm(a) for a in aliases):
                표기_치환.append({"슬롯_표기": slot.subject, "통계_표기": picked})
        objs[f"objL{n}"] = code
        why.append(f"{nm}={code}({w})")
        if tier == "fallback":
            # 폴백은 **선택 행위**다. 동작은 그대로 두되 선택을 기록한다.
            defaults[nm] = w.replace("기본값: ", "").strip()
    ax = ("항목=" + str(itm_id) + f"({itm_why}); " + "; ".join(why))
    if defaults:
        ax += " | axis_default=" + json.dumps(defaults, ensure_ascii=False)
    return itm_id, objs, ax, 표기_치환


def collect(slot: Slot, rules: dict, trace_id: str | None = None,
            key: str | None = None) -> AdapterResult:
    tid = trace_id or f"{slot.slot_id}-kosis"
    cfg = rules["adapters"]["kosis"]

    key = load_env_key("KOSIS_API_KEY") if key is None else key   # "" 는 '키 없음'이다
    if not key:
        return fail(slot, tid, "no_key", rules, "KOSIS_API_KEY 없음", channel="kosis_api")

    # A1 이 낸 stat_code 는 형식조차 틀리는 일이 잦다(실측). 없거나 이상하면 **검색으로 찾는다.**
    plan = []                                  # [(stat_code, 어떻게 얻었는지)]
    if slot.stat_code and re.match(r"^\d{3}/\S+$", str(slot.stat_code)):
        plan.append((str(slot.stat_code), "a1"))
    found, why, verified = resolve_stat_code(slot, rules, key)
    if isinstance(verified, list):
        for v in verified:
            if isinstance(v, tuple):
                plan.append((v[0], f"search({v[1]})"))
    if not plan:
        return fail(slot, tid, "bad_stat_code", rules,
                    f"stat_code 없음/형식오류({slot.stat_code!r}) 이고 검색도 실패: {why}",
                    channel="kosis_api")

    last_err, axis_fails = "", []
    for code, resolved_by in plan:
        org_id, tbl_id = str(code).split("/", 1)
        out = _fetch_table(slot, org_id, tbl_id, rules, key, tid, resolved_by)
        if isinstance(out, tuple) and out and out[0] == "_axis_failed":
            axis_fails.append(f"{code}: {out[1]}")
            continue
        if out is not None:
            return out
        last_err = f"{code}: 해당 기간 데이터 없음"
    if axis_fails and not last_err:
        # **축 실패는 축 실패라고 말한다.** 「합계」로 대신 채우지 않는다 —
        # 그것은 fail-open 이 아니라 **대체에 의한 지어내기**다(세그먼트 자리에 전체 집계).
        return fail(slot, tid, "axis_resolution_failed", rules,
                    f"축 해석 실패 {len(axis_fails)}건 — " + " / ".join(axis_fails[:3]),
                    channel="kosis_api")
    return fail(slot, tid, "empty_result", rules,
                f"검증 통과 표 {len(plan)}개 모두 {slot.period} 데이터 없음 ({last_err})",
                channel="kosis_api")


def _fetch_table(slot: Slot, org_id: str, tbl_id: str, rules: dict, key: str,
                 tid: str, resolved_by: str):
    """표 하나를 실제로 조회한다. 그 기간 데이터가 없으면 None (다음 후보로 넘어가라는 뜻)."""
    cfg = rules["adapters"]["kosis"]

    # 축을 **전부** 채운다. 하나라도 빠지면 err 20(필수요청변수 누락), 다 ALL 이면 err 31(셀 초과).
    itm_id, objs, axis_why, 표기_치환 = resolve_axes(slot, org_id, tbl_id, rules, key)
    if objs is None:
        # 축을 못 고르면 **이 표는 못 쓴다.** 옛 코드는 ALL 로 채워 다른 집계를 가져왔다.
        # ⚠ **사유를 뭉개지 않는다**(판 ⑯). 예전에는 그냥 `None` 을 돌려 다음 후보로 넘어갔고,
        #   전부 실패하면 `empty_result`(「그 기간 데이터 없음」)로 보고됐다 —
        #   **축 실패와 데이터 부재는 다른 사건**이고 틀린 사유는 다음 판을 오도한다.
        return ("_axis_failed", axis_why)
    params = dict(cfg["defaults"])
    params.update({"method": "getList", "apiKey": key, "orgId": org_id, "tblId": tbl_id,
                   "itmId": itm_id or "ALL"})
    params.update(objs)
    # 슬롯 기간을 요청에 반영한다. 안 하면 최근 N개가 오고, 그게 슬롯 기간과 어긋나면
    # A4 의 off_slot 4겹(기간 불일치)에 걸려 애써 가져온 값이 격리된다.
    yr = re.search(r"(?:19|20)\d{2}", slot.period or "")
    if yr:
        params.pop("newEstPrdCnt", None)
        params.update({"startPrdDe": yr.group(0), "endPrdDe": yr.group(0)})

    # ── 출처 URL 은 **표가 아니라 우리가 읽은 계열**을 가리켜야 한다 ──────────────
    # 예전에는 `orgId/tblId` 만 넣어서, **같은 표를 보는 슬롯들이 URL 을 공유**했다.
    # A4 의 `normalize` 는 문서를 **URL 로** 찾으므로(버그 E 의 비대칭) 같은 URL 을 가진
    # 문서는 하나만 남고, 나머지 슬롯의 인용은 **남의 본문과 대조돼 `quote_verified=False`**
    # 가 된다. 판 ④ 드라이런 실측: S5(서울)·S20(2023)·S21(2024)이 한 표를 보자
    # **값은 셋 다 정확한데 둘이 미검증**으로 떨어졌다.
    # 축·기간을 URL 에 넣으면 계열마다 달라지고, KOSIS statHtml 링크로도 여전히 유효하다.
    src_url = cfg["source_url_template"].format(orgId=org_id, tblId=tbl_id)
    axis_q = "&".join(f"{k}={v}" for k, v in sorted(objs.items()) if v)
    if itm_id:
        axis_q = f"itmId={itm_id}" + (f"&{axis_q}" if axis_q else "")
    if yr:
        axis_q += f"&prdDe={yr.group(0)}"
    if axis_q:
        src_url = f"{src_url}&{axis_q}"

    data, err, detail = get_json(cfg["base"], params, rules)
    if err:
        kind = "auth_failed" if "401" in detail or "403" in detail else err
        return fail(slot, tid, kind, rules, detail, url=src_url, channel="kosis_api")

    # KOSIS 는 오류도 200 으로 준다 — dict 로 오면 오류 메시지다
    if isinstance(data, dict):
        code = str(data.get("err") or "")
        if code in cfg["error_codes"].get("no_data", []):
            return None                     # 이 표에는 그 기간 데이터가 없다 → 다음 후보로
        codes = cfg["error_codes"]
        if code in codes["auth"]:
            kind = "auth_failed"
        elif code in codes["not_found"]:
            # ⚠ err 21 은 '통계표 없음'과 '잘못된 요청 변수' **둘 다**에 쓰인다(실측).
            #    코드만 보면 우리 코드 버그가 '조사 결과'로 둔갑한다 — 메시지로 갈라야 한다.
            msg = str(data.get("errMsg") or "")
            kind = "bad_request" if any(k in msg for k in codes["bad_request_msgs"]) \
                else "bad_stat_code"
        elif code in codes.get("bad_request", []):
            kind = "bad_request"        # 우리 요청이 틀렸다 — 조용히 넘기면 원인을 못 찾는다
        else:
            # 모르는 코드는 넘어가지 않는다. 조용한 실패가 제일 비싸다.
            kind = "unknown_code"
        return fail(slot, tid, kind, rules, f"code={code} {str(data)[:160]}",
                    url=src_url, channel="kosis_api")
    if not isinstance(data, list) or not data:
        return fail(slot, tid, "empty_result", rules, "빈 배열", url=src_url, channel="kosis_api")

    text = json.dumps(data, ensure_ascii=False, indent=1)
    items: list[FindingItem] = []
    for row in data[:20]:
        val = row.get("DT")
        if val in (None, "", "-"):
            continue
        # 인용문은 **응답 안에 실제로 있는 문자열**이어야 한다 (quote_verified 가 웹과 같은 의미를 갖게)
        quote = json.dumps(row, ensure_ascii=False)
        if quote not in text:
            quote = f'"DT": "{val}"'
        items.append(FindingItem(
            quote=quote,
            number_raw=str(val),
            unit_raw=str(row.get("UNIT_NM") or ""),
            url=src_url,
            # year 를 여기서 정하지 않는다 — 웹 경로와 같은 파서(a_desk.parse_year)가
            # context 에서 뽑는다. 두 경로가 다른 year 를 만들면 교차확인이 조용히 깨진다.
            context=f"{row.get('TBL_NM', '')} {row.get('ITM_NM', '')} "
                    f"{row.get('C1_NM', '')} {row.get('PRD_DE', '')}"))

    if not items:
        return fail(slot, tid, "empty_result", rules, "값(DT) 이 전부 비어 있음",
                    url=src_url, channel="kosis_api")

    doc = make_document(slot, tid, src_url, text, "kosis_api",
                        published_at=str(data[0].get("PRD_DE") or "") or None)
    f = make_finding(slot, tid, items)
    f.note = f"stat_code={org_id}/{tbl_id} ({resolved_by}) | {axis_why}"
    f.표기_치환 = 표기_치환          # 조용한 치환 금지 — A4 가 여기서 울타리를 읽는다
    # **대상을 확정한 것은 여기다.** 슬롯이 `stat_code` 를 비워 뒀어도 우리는 통계표를
    # 지목해 받아 왔다 — 그 사실을 값으로 내려보내야 A4 가 낱말 대조를 면제할 수 있다.
    f.경로_보증 = {"경로_칸": "stat_code", "값": f"{org_id}/{tbl_id}", "어떻게": resolved_by}
    return AdapterResult(f, doc, adapter_state="ok")
