# -*- coding: utf-8 -*-
"""블록 A — 데스크 리서치. **이 파일의 A4 는 LLM 을 부르지 않는다.**

지금 들어 있는 것: 결정론 유틸 + A4 `normalize_and_grade`
나중에 들어올 것: A1(식 설계) · A2(라우팅) · A3(수집) — 단계 5~7

A4 순서 (§4 그대로):
    ① quote_verified  ② 숫자 파싱  ③ 단위 정규화  ④ 연도  ⑤ dedup_key
    ⑥ match_key  ⑦ off_slot 4겹  ⑧ 등급  ⑨ 교차확인  ⑩ 커버리지 점검

⑩ 이 맨 뒤인 이유: `confirmed` 개수는 등급이 다 끝나야 확정된다.
"""
from __future__ import annotations

import fillaxis as _fx

import re
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from schema import (QUARANTINE_LABELS, Coverage, Document, Fact, Finding,
                    Ledger, LedgerRow, Route, Slot)

# ══════════════════════════════════════════════════════════════
# 유틸 ① — URL 정규화 / dedup_key
# ══════════════════════════════════════════════════════════════
_TRACKING = ("utm_", "gclid", "fbclid", "mc_", "igshid", "ref_src")


def canonical_url(url: str) -> str:
    """추적 파라미터 제거 + 끝 슬래시 통일. 같은 페이지가 두 건으로 세어지는 것을 막는다(F3)."""
    if not url:
        return ""
    try:
        p = urlsplit(url.strip())
        if p.scheme.lower() not in ("http", "https") or not p.netloc:
            return ""
        q = [(k, v) for k, v in parse_qsl(p.query, keep_blank_values=True)
             if not k.lower().startswith(_TRACKING)]
        host = p.netloc.lower()
        if host.startswith("www."):
            host = host[4:]
        return urlunsplit((p.scheme.lower(), host, p.path.rstrip("/") or "/",
                           urlencode(sorted(q), doseq=True), ""))
    except Exception:
        return ""


def dedup_key(url: str) -> str:
    c = canonical_url(url)
    if not c:
        return ""
    p = urlsplit(c)
    return f"{p.netloc}{p.path}" + (f"?{p.query}" if p.query else "")


def domain_of(url: str) -> str:
    return urlsplit(canonical_url(url) or url).netloc.lower()


# ══════════════════════════════════════════════════════════════
# 유틸 ② — 숫자 파싱. **값을 만들어내지 않는다. 실패는 None.**
# ══════════════════════════════════════════════════════════════
_NUM = re.compile(r"-?\d[\d,]*(?:\.\d+)?")


def parse_number(number_raw: str, unit_raw: str, units_rules: dict) -> tuple:
    """'407만 3천' + '개' → (4073000.0, '개', False)

    반환: (value_num, unit_norm, approx). 파싱 불가면 (None, None, False).
    한국어 단위는 **겹쳐 나온다**('15조 5000억') — 큰 단위부터 훑어 누적해야 한다.
    """
    raw = (number_raw or "").strip()
    if not raw:
        return None, None, False

    mult = units_rules["ko_multipliers"]
    approx_tokens = units_rules["prefix_modifiers"]["approx"]
    approx = any(t in raw for t in approx_tokens) or any(t in (unit_raw or "") for t in approx_tokens)

    # 숫자·단위·소수점만 남긴다 (약, 여, 공백, 원화기호 등 제거)
    s = raw.replace(",", "").replace(" ", "")
    for t in approx_tokens:
        s = s.replace(t, "")
    s = s.replace("₩", "").replace("$", "").replace("＄", "")

    total, matched, rest = 0.0, False, s
    for tok in sorted(mult, key=lambda k: -mult[k]):        # 조 → 억 → 만 → 천
        if tok in rest:
            head, _, rest = rest.partition(tok)
            head_num = _NUM.search(head)
            if not head_num:
                return None, None, False
            try:
                total += float(head_num.group(0).replace(",", "")) * mult[tok]
            except ValueError:
                return None, None, False
            matched = True

    if matched:
        tail = _NUM.search(rest)
        if tail:
            try:
                total += float(tail.group(0).replace(",", ""))
            except ValueError:
                pass
        value = total
    else:
        m = _NUM.search(s)
        if not m:
            return None, None, False
        try:
            value = float(m.group(0).replace(",", ""))
        except ValueError:
            return None, None, False

    unit_norm, scale = normalize_unit(unit_raw, units_rules)
    if scale == "unknown":
        # **모르는 배수는 값을 만들지 않는다.** 1배로 두면 조용히 틀린 값이 흐른다 —
        # `resolve_axes` 의 「합계 대체 금지」와 같은 원칙(조용한 대체는 지어내기).
        return None, unit_norm, approx
    # '120' + '억원' 처럼 단위에 배수가 들어 있으면 곱한다.
    # 단, 숫자 쪽에서 이미 한국어 단위를 먹었으면(matched) 이중 계상 금지.
    if scale and not matched:
        value *= scale
    return value, unit_norm, approx


def normalize_unit(unit_raw: str, units_rules: dict) -> tuple:
    """'억원' → ('원', 100000000). 모르는 단위는 원문 그대로 두고 배수 없음.

    ⚠ **배수 접두어가 있는데 표에 없으면 `scale` 이 `"unknown"` 이다**(판 ⑱).
    조용히 1배로 두면 **100만 배 틀린 값이 흐른다** — 판 ⑰ 실측(「백만원」).
    """
    u = (unit_raw or "").strip()
    if not u:
        return None, None
    tbl = units_rules.get("unit_scale_in_name", {})
    scale = tbl.get(u)
    if scale is None:
        # **공백 하나가 배수를 죽인다** — 「억 원」 vs 「억원」. 대조 직전에만 지운다.
        nz = units_rules.get("unit_scale_normalize") or {}
        if nz.get("enabled"):
            squeezed = u
            for ch in (nz.get("strip_chars") or [" "]):
                squeezed = squeezed.replace(ch, "")
            scale = tbl.get(squeezed)
    if scale is None:
        uk = units_rules.get("unknown_multiplier") or {}
        if uk.get("enabled"):
            # ⓐ 아는 접두어인데 표에 없다
            hit = any(p in u for p in (uk.get("prefixes") or []))
            # ⓑ **화이트리스트 뒤집기** — 「…원」인데 순수 「원」도 아니고 표에도 없다.
            #    접두어 목록만 보면 「십경원」처럼 **모르는 표기가 1배로 통과**한다(판 ⑲ 실측).
            for suf in (uk.get("화폐_접미") or []):
                if u.endswith(suf) and u != suf:
                    hit = True
            if hit:
                scale = "unknown"      # **명시 실패** — 조용한 1배 금지
    norm = units_rules["unit_norm"].get(u)
    if norm is None:
        for k, v in units_rules["unit_norm"].items():       # '여명' 같은 변형
            if k and k in u:
                norm = v
                break
    return (norm or u), scale


def units_compatible(slot_unit: str, fact_unit: str | None, units_rules: dict) -> bool:
    """**`units.compatible` 표를 읽는 유일한 함수.** 두 소비자(off_slot·B블록)가 이것만 부른다.

    한 표를 두 함수가 따로 읽던 시절, 정규화 범위와 기본값이 달라 판정이 갈렸다
    (조인 계보 F). 실측: 관측된 단위 쌍 25개 중 **갈리는 것은 「슬롯 단위 있음 + 값 단위 미상」
    한 쌍**이었다 — off_slot 은 비호환(격리), B 는 「판단 안 함」으로 통과시켰다.
    → **fail-closed 로 통일한다.** 값 단위를 모르면 맞는지도 모르는 것이다.
      (영향 0건: 단위 미상 사실 20건은 전부 이미 off_slot 이라 B 에 도달하지 않는다)

    슬롯이 단위를 안 적었으면 **검사 자체를 안 한다**(없는 기준으로 벌하지 않는다).
    """
    if not slot_unit:
        return True
    if not fact_unit:
        return False                      # 값 단위 미상 = 비호환 (fail-closed)
    su, _ = normalize_unit(slot_unit, units_rules)
    fu, _ = normalize_unit(fact_unit, units_rules)
    table = units_rules.get("compatible", {})
    return fu == su or fu in table.get(su, [su]) or fact_unit in table.get(su, [su])


# ══════════════════════════════════════════════════════════════
# 유틸 ③ — 연도. 실패하면 None. **추측하지 않는다.**
# ══════════════════════════════════════════════════════════════
#: ⚠ 숫자 경계가 **필수**다. 경계가 없으면 값 `20264`(서울 커피전문점 사업체 수) 안의
#  '2026' 을 사실 연도로 집는다 — route12-02 에서 실제로 그랬고, 멀쩡한 확인됨이 기간 겹에
#  격리됐다. 조용히 틀리는 종류다: match_key 도 같이 갈려 교차확인이 안 붙는다.
_YEAR = re.compile(r"(?<!\d)(?:19|20)\d{2}(?!\d)")


def parse_year(*texts: str) -> int | None:
    for t in texts:
        m = _YEAR.search(str(t or ""))
        if m:
            y = int(m.group(0))
            if 1990 <= y <= 2100:
                return y
    return None


# ══════════════════════════════════════════════════════════════
# 유틸 ③-2 — 두 값이 **같은 값인가**. 교차확인에서만 쓴다 (백로그 7).
#   완전 일치는 과잉이다: "95,000개 정도"(반올림 표기)와 "9만5,337개"(정확값)는 같은 사실이다.
#   무제한 허용은 구멍 그대로다: 68.0 과 757,000 이 서로를 보증하던 자리다.
#   기준은 코드가 아니라 `scoring.cross` 에 있다(절대규칙 7).
# ══════════════════════════════════════════════════════════════
def _sig_digits(v: float) -> int:
    """유효숫자 자릿수 — 후행 0 을 뺀 자릿수. 95000 → 2 · 95337 → 5 · 350 → 2."""
    s = f"{abs(v):.10g}".replace("-", "").replace(".", "").lstrip("0").rstrip("0")
    return len(s) or 1


def _round_sig(v: float, d: int) -> float:
    if v == 0:
        return 0.0
    import math
    e = math.floor(math.log10(abs(v)))
    return round(v, -(e - d + 1))


def same_value(a: float, b: float, cross_rules: dict) -> bool:
    """**유효숫자 일치 그리고 상대차 상한** — 두 조건은 상보적이라 둘 다 만족해야 한다.

    유효숫자만 쓰면 1자리에서 100,000 과 149,000 이 같아지고(33%),
    상대차만 쓰면 105,000 과 110,000(4.5%)이 붙는다 — 그건 서로 다른 요금제다.
    """
    if a is None or b is None:
        return False
    if a == b:
        return True
    cfg = (cross_rules or {}).get("value_match") or {}
    lo, hi = min(abs(a), abs(b)), max(abs(a), abs(b))
    if hi == 0:
        return True
    if (hi - lo) / hi > cfg.get("max_rel_diff", 0.05):
        return False
    if not cfg.get("significant_digits", True):
        return True
    d = max(1, min(_sig_digits(a), _sig_digits(b)))
    return _round_sig(a, d) == _round_sig(b, d)


def is_scale_suspect(a: float, b: float, cross_rules: dict) -> bool:
    """정확히 10^n 배 — 같은 값이 아니라 **단위 오독 의심**이다. 조용히 변환하지 않는다."""
    cfg = (cross_rules or {}).get("scale_suspect") or {}
    if not cfg.get("enabled") or not a or not b:
        return False
    lo, hi = min(abs(a), abs(b)), max(abs(a), abs(b))
    if lo == 0:
        return False
    r = hi / lo
    return any(abs(r - 10 ** n) < 10 ** n * 1e-9 for n in cfg.get("powers", []))


# ══════════════════════════════════════════════════════════════
# 유틸 ④ — content_status. HTTP 200 과 '쓸 만한 본문'은 다르다 (F9).
#          A3 fetch 가 부르지만 결정론이라 여기 둔다.
# ══════════════════════════════════════════════════════════════
#: UTF-8 로 인코딩된 한글 바이트를 latin-1 로 읽은 흔적. 'ì' 'ë' 뒤에 이어지는 후속 바이트.
#: 악센트 문자 하나(é)는 뒤가 ASCII 라 걸리지 않는다 — 프랑스어 본문을 깨진 것으로 보지 않는다.
_MOJIBAKE = re.compile("[À-ÿ][-¿]")


def mojibake_ratio(text: str) -> float:
    t = text or ""
    return (len(_MOJIBAKE.findall(t)) * 2 / len(t)) if t else 0.0


def is_mojibake(text: str, scoring_rules: dict) -> bool:
    """인코딩이 깨진 본문인가. LLM 0회, 순수 결정론."""
    cfg = (scoring_rules.get("content_status") or {}).get("mojibake")
    if not cfg:
        return False
    return mojibake_ratio(text) >= cfg["min_ratio"]


def classify_content(text: str, scoring_rules: dict) -> tuple:
    cfg = scoring_rules["content_status"]
    t = text or ""
    digits = sum(c.isdigit() for c in t)
    if len(t.strip()) <= cfg["empty"]["max_text_len"]:
        return "empty", len(t), digits
    # 길이도 숫자도 충분해서 usable 로 새어 들어간다 — 길이 검사 앞에 둔다
    if is_mojibake(t, scoring_rules):
        return "mojibake", len(t), digits
    for kw in cfg["paywall"]["keywords"]:
        if kw in t:
            return "paywall", len(t), digits
    # 길이·숫자로는 못 잡는 껍데기 — 616자에 숫자 26개인 '로딩중입니다' 통계표가 있었다
    if is_loading_shell(t, scoring_rules):
        return "js_shell", len(t), digits
    if len(t) < cfg["js_shell"]["max_text_len"] and digits < cfg["js_shell"]["max_digit_count"]:
        return "js_shell", len(t), digits
    return "usable", len(t), digits


def is_loading_shell(text: str, scoring_rules: dict) -> bool:
    """로딩 안내만 있고 값이 없는 껍데기인가. **맨 앞에 있을 때만** 친다.

    키워드만 보면 오분류한다 — 진짜 페이지(3,182자)에도 스크립트 잔재로 'loading' 이
    2,780번째 자리에 있었다. 껍데기는 맨 앞에서 로딩을 알리므로 위치로 가른다.
    실측 분리: 껍데기 8건 pos 0~104 · 진짜 문서 pos 2,780 (저장 문서 294건 전수).
    """
    cfg = (scoring_rules["content_status"].get("js_shell") or {})
    head = cfg.get("loading_head_chars")
    kws = cfg.get("loading_keywords") or []
    if not head or not kws:
        return False
    front = (text or "")[:head]
    return any(k in front for k in kws)


# ══════════════════════════════════════════════════════════════
# A2 — route_sources : 어디로 보낼지 규칙으로 정한다. LLM 0회.
#      **전부 웹검색으로 보내지 않는 것이 커버리지의 핵심이다.**
# ══════════════════════════════════════════════════════════════
#  ⚠ `claim_type` 이 TAM/SAM 이면 코드 없이도 kosis 로 보내 봤다가 **되돌렸다**(full-02).
#    10슬롯이 전부 not_found 였고 `run.py` 의 수집 루프에 **폴백이 없어** 그대로 죽었다.
#    삼켜진 것이 '카페 침투율' '카페 단가' 처럼 **통계표에 있을 리 없는 값**이었다 —
#    claim_type 만으로 통계 경로를 정한 것이 거칠었다. 다시 넣으려면 (1) metric 기준으로
#    좁히고 (2) not_found 면 web 으로 떨어지는 폴백을 **함께** 넣어야 한다.
#    → 작업 12-1 이 그 두 조건을 채운 재도입이다. 목록도 폴백 조건도 코드가 아니라
#      `adapters.kosis.route_metrics` 에 있다 (절대규칙 7).
def _route_metrics(rules: dict) -> dict:
    cfg = ((rules.get("adapters") or {}).get("kosis") or {}).get("route_metrics") or {}
    return cfg if cfg.get("enabled") else {}


def route_sources(slots: list[Slot], rules: dict) -> list[Route]:
    rm = _route_metrics(rules)
    out = []
    for s in slots:
        if s.stat_code:
            out.append(Route(s.slot_id, "kosis", f"stat_code={s.stat_code}"))
        elif s.corp_name:
            out.append(Route(s.slot_id, "dart", f"corp_name={s.corp_name}"))
        elif s.claim_type == "LEGAL":
            out.append(Route(s.slot_id, "web", "LEGAL — 법령정보센터 한정"))
        elif s.claim_type == "PRICE":
            out.append(Route(s.slot_id, "web", "PRICE — 공식 도메인 우선"))
        else:
            # metric 이 국가통계가 발행하는 계량이면 검색 대신 통계 API 로.
            # **기본 경로(web)로 갈 것만 가로챈다** — LEGAL·PRICE·dart 는 건드리지 않는다.
            metric = s.metric or ""
            hit = next((m for m in rm.get("match", []) if m in metric), None)
            if hit and any(x in metric for x in rm.get("exclude", [])):
                hit = None      # 「가입 매장 수」는 통계가 아니라 그 회사의 고객 수다
            if hit:
                out.append(Route(s.slot_id, "kosis", f"route_metric={hit} (검색 대신 통계 API)",
                                 fallback_to=rm.get("fallback_to", "")))
            else:
                out.append(Route(s.slot_id, "web", "기본 경로"))
    return out


def should_fallback(route: Route, finding: Finding, rules: dict) -> bool:
    """kosis 가 **못 찾은 것**일 때만 web 으로 떨어뜨린다.

    키 없음(`not_configured`)·인증 실패·네트워크 실패는 폴백하지 않는다. web 성공으로 덮으면
    보고서 §7 의 "통계 API 미사용 — 커버리지 하한" 고지가 사라져 나중에 커버리지가 낮은
    이유를 "검색이 나빴나 / API 를 안 썼나"로 가를 수 없다 (규칙 5).
    """
    if not route.fallback_to:
        return False
    return finding.status in (_route_metrics(rules).get("fallback_on") or [])


# ══════════════════════════════════════════════════════════════
# A4 ①~⑥ — normalize
# ══════════════════════════════════════════════════════════════
def _squash(s: str) -> str:
    return re.sub(r"\s+", "", s or "")


def _strip_noise(text: str, yf: dict) -> str:
    """연도 탐색 **전에** 전화번호·사업자등록번호 구간을 지운다.

    거르는 게 아니라 **안 보이게** 한다 — 실측(mss 노쇼 PDF): 4자리 매치 9건 중 7건이
    전화번호였다(`033-243-1950`→1950). 창 안에 남는 유일한 후보가 보도시점이라,
    그대로 두면 body_scan 이 「발행일로 메우지 않는다」를 본문 경로로 우회한다.
    """
    cfg = (yf or {}).get("exclude_spans") or {}
    if not cfg.get("enabled"):
        return str(text or "")
    out = str(text or "")
    for pat in cfg.get("patterns") or []:
        out = re.sub(pat, " ", out)
    return out


def _years_of(text: str, yf: dict) -> list:
    """본문에 나타난 연도를 **등장 순서대로**. 4자리 + 2자리 축약(’25) 둘 다.

    2자리를 안 보면 한국 공문서에서는 사실상 눈을 감는 것이다 — mss 실태조사 PDF 의
    연도 정보 10건이 전부 `‘25.11.24.`·`’22년` 형식이라 4자리 정규식이 하나도 못 잡았다.
    """
    t = _strip_noise(text, yf)
    hits = [(m.start(), int(m.group(0))) for m in _YEAR.finditer(t)]
    td = (yf or {}).get("two_digit") or {}
    if td.get("enabled") and td.get("pattern"):
        cent = int(td.get("century") or 2000)
        hits += [(m.start(), cent + int(m.group(1)))
                 for m in re.finditer(td["pattern"], t)]
    return [y for _, y in sorted(hits) if 1990 <= y <= 2100]


def _window_of(slot: Slot, yf: dict) -> tuple:
    """주움 창. **판정 창과 같은 값을 쓴다** — 이게 이 수리의 핵심이다.

    예전에는 주움이 코드 하드코딩 ±3, 판정이 규칙 ±2 + fresh clamp 였다. 그래서 A4 가
    스스로 주운 연도로 스스로를 격리했다(beauty-05 S16: 「’22년 이후」의 2022).
    슬롯에 저장된 `period_min`/`period_max` 는 **판정이 읽는 바로 그 값**이라
    구조적으로 어긋날 수 없다.
    """
    pw = (yf or {}).get("pick_window") or {}
    if pw.get("use_slot_window") and slot.period_min is not None and slot.period_max is not None:
        return slot.period_min, slot.period_max
    want = parse_year(slot.period)
    if not want:
        return None, None
    span = int(pw.get("fallback_span", 3))
    return want - span, want + span


def _years_in_period(text: str, slot: Slot, yf: dict | None = None) -> set:
    """창 안의 연도 **전부**. 하나로 좁히는 판단은 부르는 쪽이 한다."""
    lo, hi = _window_of(slot, yf or {})
    if lo is None:
        return set()
    return {y for y in _years_of(text, yf or {}) if lo <= y <= hi}


def _year_in_period(text: str, slot: Slot, yf: dict | None = None) -> int | None:
    """문맥에서 **슬롯 기간 창 안**의 연도만 취한다 (등장 순서 첫 매치).

    아무 연도나 주우면 기사 날짜·저작권 표기가 사실 연도로 둔갑한다.
    슬롯이 기간을 안 적었으면 문맥에서 뽑지 않는다 — 비우는 게 낫다.
    **창 밖 연도는 「줍고 격리」가 아니라 「안 줍는다」**. 결과는 year=None + 감점 1 이고,
    그것이 「모른다」의 정직한 표현이다.
    """
    lo, hi = _window_of(slot, yf or {})
    if lo is None:
        return None
    for y in _years_of(text, yf or {}):
        if lo <= y <= hi:
            return y
    return None


def normalize(findings: list[Finding], docs: dict[str, Document],
              slots: dict[str, Slot], rules: dict) -> list[Fact]:
    """Finding[] → Fact[]. LLM 0회.

    docs 는 trace_id → Document. 인용문 대조에 원문이 필요하다.
    """
    units = rules["units"]
    facts: list[Fact] = []
    seen: set[tuple] = set()
    n = 0

    # 인용은 서로 다른 문서에서 나올 수 있다 (extract 가 슬롯 단위로 여러 문서를 묶어 보므로).
    # trace_id 하나로만 본문을 찾으면 quote_verified 가 엉뚱한 문서와 대조된다.
    by_url = {canonical_url(d.url): d for d in docs.values() if d.url}

    for f in findings:
        if f.status != "found":
            continue                       # 실패는 원장이 아니라 §7 로 간다 (규칙 5)
        slot = slots[f.slot_id]

        for item in f.findings:
            doc = by_url.get(canonical_url(item.url)) or docs.get(f.trace_id)
            body = _squash(doc.text if doc else "")
            n += 1
            # ① 인용문이 본문에 실재하는가 — F7 의 직접 방어선
            quote_ok = bool(_squash(item.quote)) and _squash(item.quote) in body
            # ②③ 숫자·단위
            value, unit, _approx = parse_number(item.number_raw, item.unit_raw, units)
            # ④ 연도 — **사실의 시점**만 본다. 발행일로 메우지 않는다.
            #    ① 인용문 안의 연도 ② 문맥 안에서 슬롯 기간에 드는 연도 ③ 없으면 None
            #    (발행일을 사실 연도로 쓰면 같은 사실이 기사 날짜 때문에 갈라진다 — F4 가 오작동한다)
            yf = rules["scoring"]["year_fields"]
            order = yf["order"]                               # ①인용문 ②문맥 ③null
            # 인용문은 창을 안 건다(짧고 그 사실을 직접 말하는 자리) — 다만 잡음 구간과
            # 2자리 표기는 여기서도 같은 규칙을 쓴다.
            _q = _years_of(item.quote, yf)
            year, year_source = (_q[0] if _q else None), order[0]
            if year is None:
                year, year_source = _year_in_period(item.context, slot, yf), order[1]
            if year is None:
                # ③ **본문**에서 슬롯 기간 창 안의 연도. context 가 비었을 때만 온다.
                #    발행일 fallback 과 다르다 — 발행일은 「문서가 나온 때」고 이건
                #    「본문이 말하는 때」다. 추측이 아니라 **관측의 위치를 넓힌 것**이다.
                #    창 안에 둘 이상이면 **고르지 않는다** — 고르는 순간 조용한 추측이다.
                bs = yf.get("body_scan") or {}
                if bs.get("enabled") and doc is not None:
                    cands = _years_in_period(doc.text, slot, yf)
                    if len(cands) == 1 or (cands and not bs.get("require_unique", True)):
                        year, year_source = min(cands), order[2]
            if year is None:
                year_source = order[-1]
            published_year = parse_year(doc.published_at_raw if doc else "")
            url = canonical_url(item.url or (doc.url if doc else ""))
            # ⑤ dedup — 같은 페이지의 같은 값은 1건 (F3)
            dk = dedup_key(url)
            key = (f.slot_id, dk, value, unit)
            if key in seen:
                continue
            seen.add(key)

            facts.append(Fact(
                fact_id=f"F{len(facts) + 1:03d}",
                slot_id=f.slot_id, var_id=slot.var_id, trace_id=f.trace_id,
                url=url, quote=item.quote,
                value_num=value, unit_norm=unit, year=year, year_source=year_source,
                dedup_key=dk,
                match_key=slot.match_key(year),        # ⑥ 코드로 만든다 (F4)
                quote_verified=quote_ok,
                # 계산해 놓고 안 싣던 값(버그 G). **참고용이고 match_key 에는 안 들어간다** —
                # 발행일로 사실 연도를 메우지 않는다는 규칙은 그대로다.
                published_year=published_year,
                content_status=(doc.content_status if doc else "empty"),
                channel=(doc.channel if doc else "web"),
                # 조회 시점을 사실까지 실어 나른다. **문서에 없으면 None 이고 채우지 않는다** —
                # 지금 시각을 넣으면 옛 실행을 복원할 때마다 「방금 본 값」으로 둔갑한다.
                retrieved_at=(doc.retrieved_at if doc else None),
                account_id=getattr(item, "account_id", "") or "",
                sj_div=getattr(item, "sj_div", "") or "",
                scope=getattr(item, "scope", "") or "",
            ))
    return facts


# ══════════════════════════════════════════════════════════════
# A4 ⑦ — off_slot 5겹. 버리지 않고 격리한다.
#         (4겹으로 시작했으나 '슬롯 2023 vs 사실 2026' 이 통과해 기간 겹을 추가했다)
# ══════════════════════════════════════════════════════════════
def mask_false_friends(hay: str, rules: dict) -> str:
    """주제어가 **다른 말의 일부**로 걸리는 것을 지운다. `must_contain` 대조에만 쓴다.

    must_contain 은 문서 전문을 보므로 '카페24' 한 번이면 전자상거래 호스팅사 요금제
    페이지가 카페 슬롯을 통과한다 — full-03 에서 usable 66건 중 12건이 그것이었다.
    도메인을 막지 않는 이유는 cafe24.com 이 COMP 슬롯에서는 정당한 출처일 수 있어서다.

    `must_not_contain` 에는 쓰지 않는다 — 가려낸 말이 금지어면 금지가 조용히 풀린다.
    """
    ff = (((rules.get("slotcheck") or {}).get("topic_words") or {}).get("false_friends") or {})
    for key, terms in ff.items():
        if key.startswith("_"):
            continue
        for t in terms:
            hay = hay.replace(t, " ")
    return hay


def _account_mismatch(fact: Fact, slot: Slot, rules: dict) -> str | None:
    """off_slot 6겹째 — 공시 계정이 슬롯이 묻는 계정인가.

    **계정 정체가 없으면 판정하지 않는다.** web 경로의 사실은 `account_id` 가 비어 있고,
    없는 기준으로 벌하지 않는다(기간 겹과 같은 원칙).
    """
    if not fact.account_id:
        return None
    rule = None
    for r in (((rules.get("adapters") or {}).get("dart") or {})
              .get("accounts") or {}).get("by_metric") or []:
        if any(w in (slot.metric or "") for w in r.get("match") or []):
            rule = r
            break
    if rule is None:
        return None                     # 슬롯이 계정을 묻는 슬롯이 아니다
    if fact.account_id in (rule.get("account_ids") or []) and \
            fact.sj_div in (rule.get("sj_div") or []):
        return None
    return (f"계정 불일치: 슬롯 '{slot.metric}' 이 묻는 계정이 아님 "
            f"({fact.sj_div}/{fact.account_id})")


def _표기_다리(word: str, subject: str, hay: str, rules: dict) -> str | None:
    """슬롯 어휘 ↔ 통계 어휘 **다리** (판 ⑰). 통과시킨 별칭을 돌려준다.

    **완화가 아니라 다리다.** 「애완용품」이 「반려동물 용품」의 통계 표기임을 이미 아는
    자리(`adapters.kosis.resolve.subject_별칭`)가 있는데 **심사 층만 그것을 몰랐다** —
    표 찾기·축 고르기는 같은 표를 쓴다(판 ⑯).

    ⚠ **단일 원천.** 이 함수는 그 표를 **그대로 참조**하고 사본을 만들지 않는다.
    판 ⑫(키 분열)·⑬(라우팅 분열)·⑯(축 분열)이 전부 **「같은 물음을 두 곳이 각자 푼」**
    사고였다 — **세 번째 복제가 네 번째 갈림을 만든다.**

    ⚠ **넓히는 것이지 여는 것이 아니다.** 표에 없는 표기는 **여전히 차단**된다.
    """
    al = (((rules.get("adapters") or {}).get("kosis") or {})
          .get("resolve") or {}).get("subject_별칭") or {}
    if not al.get("enabled"):
        return None
    # ⚠ **키의 단위가 다르다.** 별칭 표의 키는 **subject 전체 문구**(「반려동물 용품」)이고
    #   `must_contain` 은 **낱말 조각**(「반려동물」)이다. 그래서 `word` 로 직접 찾으면 안 맞는다.
    #   찾는 것은 **슬롯 subject 의 별칭**이고, `word` 는 그 subject 를 가리키는 낱말인지만 본다.
    for key, aliases in (al.get("map") or {}).items():
        if key not in (subject or ""):
            continue                       # 이 슬롯의 subject 를 가리키는 별칭이 아니다
        if (word or "") not in (subject or ""):
            continue                       # ⚠ subject 낱말이 아닌 가드는 **그대로 살린다**
        for alias in aliases:
            if alias and alias in hay:
                return alias
    return None


def off_slot_reason(fact: Fact, slot: Slot, doc: Document | None, rules: dict) -> str | None:
    hay = f"{fact.quote} {doc.text if doc else ''}"
    if slot.must_contain:
        masked = mask_false_friends(hay, rules)
        if not any(w in masked for w in slot.must_contain):
            # 직접 표기가 없다 → **다리를 본다.** 통과시키면 **어느 별칭이 통과시켰는지
            # 사실에 값으로 남긴다**(조용한 치환 금지 — `axis_default` 와 같은 원칙).
            bridged = [(w, a) for w in slot.must_contain
                       if (a := _표기_다리(w, slot.subject, masked, rules))]
            if not bridged:
                return f"must_contain 없음: {slot.must_contain}"
            # 조건 3 — **상위 카테고리면 울타리를 값으로 강제한다.** 다리로 들어온 값이
            # 상위 집계면 하위인 척 쓰이면 안 된다(사다리 2단 · 울타리 없는 2단 금지).
            cap = (((rules.get("adapters") or {}).get("kosis") or {}).get("resolve") or {})                 .get("subject_별칭", {}).get("상위_카테고리", {}).get("map", {})
            fact.표기_다리 = [
                {"슬롯_표기": w, "통계_표기": a,
                 "상한_울타리": bool(cap.get(a, {}).get("상한_울타리")),
                 "경계": list(cap.get(a, {}).get("경계") or [])}
                for w, a in bridged]
    hit = [w for w in slot.must_not_contain if w in hay]
    if hit:
        return f"must_not_contain 포함: {hit}"
    if not units_compatible(slot.unit, fact.unit_norm, rules["units"]):
        return f"단위 불일치: 슬롯 '{slot.unit}' vs 사실 '{fact.unit_norm}'"
    # 6겹 — 계정. 어댑터가 계정 정체를 줬을 때만 본다.
    # **`value_range` 보다 앞이어야 한다.** full-04 에서 카페24 재무상태표 191행 중
    # 자산총계(415조)·유동자산(263조)이 값범위 밖으로 걸리고, 하필 규모가 작은
    # `당기법인세자산 2,456,770원` 하나만 범위 안이라 「경쟁사의 매출」 확인됨 5점이 됐다.
    # 값범위는 크기만 보므로 **틀린 계정을 크기로 거르면 작은 오답이 살아남는다.**
    acc = _account_mismatch(fact, slot, rules)
    if acc:
        return acc
    if slot.value_range and fact.value_num is not None:
        lo, hi = slot.value_range
        if not (lo <= fact.value_num <= hi):
            # **자릿수 그물** (판 ⑲). 기대가 관측을 검열하지 못하게 하되, 단위·축 오류는
            # 그대로 잡는다 — 「조금 벗어남」과 「10^4 배 벗어남」은 **종류가 다른 사건**이다.
            cfg = ((rules.get("guards") or {}).get("value_range") or {})
            if cfg.get("enabled"):
                import math
                v = abs(fact.value_num)
                near = min((abs(x) for x in (lo, hi) if x), default=0) or 1
                far = max(abs(lo), abs(hi)) or 1
                ref = near if v < abs(lo) else far
                diff = abs(math.log10(v) - math.log10(abs(ref))) if v > 0 else 99.0
                cap = float(cfg.get("차단_자릿수_차이", 3))
                if diff > cap:
                    return (cfg.get("차단_사유") or "값범위 밖").format(
                        diff=diff, cap=cap, v=fact.value_num, lo=lo, hi=hi)
                # **통과시키되 조용히 넘기지 않는다** — 플래그를 사실에 값으로 남긴다.
                fact.기대_밖 = {"사유": (cfg.get("플래그_사유") or "").format(
                                   lo=lo, hi=hi, diff=diff, cap=cap),
                               "자릿수_차이": round(diff, 2),
                               "값": fact.value_num, "기대": [lo, hi]}
            else:
                return f"값범위 밖: {fact.value_num:g} ∉ [{lo:g}, {hi:g}]"
    # 5겹 — 기간. 슬롯이 2023 을 물었는데 2026 값이 오면 다른 사실이다.
    # 창은 **A1 이 계산해 슬롯에 저장한 것**을 읽는다(`period_min`/`period_max`). 여기서
    # ±tol 로 다시 계산하면 하한이 신선 경계 아래로 내려가 '통과시킨 뒤 감점' 이 되고,
    # 무엇보다 창이 기록에 남지 않아 왜 잘렸는지 나중에 못 따진다.
    # 창이 없는 슬롯(사람이 적은 것 등)은 판정하지 않는다 — 없는 기준으로 벌하지 않는다.
    if fact.year and slot.period_min is not None and slot.period_max is not None:
        if not (slot.period_min <= fact.year <= slot.period_max):
            return (f"기간 불일치: 슬롯 {slot.period} vs 사실 {fact.year} "
                    f"(창 {slot.period_min}~{slot.period_max})")
    return None


# ══════════════════════════════════════════════════════════════
# A4 ⑧⑨ — 등급과 교차확인. **등급이 붙는 유일한 곳.**
# ══════════════════════════════════════════════════════════════
def kind_of(url: str, whitelist: dict) -> tuple:
    host = urlsplit(canonical_url(url) or url).netloc.lower().split(":")[0]
    for kind, domains in whitelist["kinds"].items():
        for dom in domains:
            if host == dom or host.endswith("." + dom):
                return kind, f"whitelist:{dom}"
    for suf, kind in whitelist["suffix_downgrade"].items():
        if suf.startswith("_"):
            continue
        if host == suf or host.endswith("." + suf):
            return kind, f"downgrade:*.{suf}"
    return whitelist["default_kind"], "default:unlisted"


def grade(facts: list[Fact], slots: dict[str, Slot], docs: dict[str, Document],
          rules: dict, reference_year: int) -> Ledger:
    sc, wl = rules["scoring"], rules["whitelist"]

    # ⑨ 교차확인 준비 — match_key 가 같고 **화자가 다를 때만** 센다 (F4)
    #
    # ⚠ 도메인이 다르다고 화자가 다른 것이 아니다. 자기발표(회사 공식 페이지)와 그 회사가
    #   낸 보도자료 배포본은 **도메인이 둘이지만 말하는 사람은 하나**다. 그대로 두면
    #   official_page(4) + press_release 조합이 +1 을 받아 **혼자 5점(확인됨)** 에 닿는다.
    #   → 자기발표 유형은 **한 화자로 접는다.**
    #   URL 로 '어느 회사가 발표했는지' 는 알 수 없다(배포 대행 도메인에는 그 정보가 없다).
    #   그래서 회사별로 접지 않고 **자기발표 전체를 한 칸**으로 둔다 — 모르는 것은
    #   보수적으로. 독립 발행자(통계기관·언론)와의 교차는 그대로 살아 있다.
    # normalize 와 같은 색인. 두 곳이 다른 방식으로 문서를 찾으면 그 자체가 조인 버그다.
    by_url = {canonical_url(d.url): d for d in docs.values() if d.url}

    xc = sc.get("cross") or {}
    self_kinds = set(xc.get("self_published_kinds") or [])
    bucket = xc.get("self_published_bucket") or "self_published"

    # 묶는 키에 **단위를 더한다**(1겹). `match_key` 자체는 건드리지 않는다 — 비교 축이
    # 바뀌면 과거 원장과 문자열이 안 맞는다(백로그 9). 단위는 여기서만 본다.
    groups: dict[tuple, list] = {}
    for f in facts:
        if f.quote_verified and f.value_num is not None:
            # 단위는 **슬롯 단위로 접는다** — `units_compatible` 이 호환이라고 한 것은
            # 교차 묶음에서도 같은 그룹이어야 한다. 실측(gate3-01): '매장'·'곳' 이
            # off_slot 에서는 통과했는데 묶음에서는 갈려 화자가 3 → 2 로 줄었다.
            # **같은 규칙 표를 두 소비자가 다르게 읽는 것 — 조인 버그 계보 F.**
            u = f.unit_norm
            sl = slots.get(f.slot_id)
            if sl and units_compatible(sl.unit, f.unit_norm, rules["units"]):
                u = normalize_unit(sl.unit, rules["units"])[0] or sl.unit
            key = (f.match_key, u) if xc.get("require_same_unit") else (f.match_key,)
            groups.setdefault(key, []).append(f)

    # ── 원 출처 귀속 (②-b-A) ────────────────────────────────────
    #   도메인으로 화자를 세면 **같은 보도자료를 받아쓴 복제 기사들이 서로를 «독립 발행자»로
    #   보증한다.** 실측(beauty-06a): 중기부 노쇼 실태조사의 65% 를 ajunews 와 sedaily 가
    #   서로 교차 확인해 각각 +1 을 받았다 — 둘은 같은 조사 한 건이다.
    #   ⚠ 이것은 가점을 깎는 변경이 아니라 **없던 독립성을 만들어내던 착시의 수리**다.
    #      임계·base_score·caps 는 하나도 안 건드린다. 바꾸는 것은 «몇 명이 말했는가»뿐이다.
    sig_rules = rules.get("source_signature") or {}
    sig_cross = sig_rules.get("cross") or {}
    sig_on = bool(sig_cross.get("enabled"))
    sig_need = bool(sig_cross.get("require_signature"))

    def _signature_of(fact) -> str | None:
        """본문에 「발행주체 + 조사명」이 **둘 다** 있으면 그 원 출처 id. LLM 0회.

        한쪽만으로는 귀속하지 않는다 — 부처 이름은 아무 기사에나 나온다.
        """
        d = by_url.get(canonical_url(fact.url))
        body = (d.text if d else "") or ""
        if not body:
            return None
        for sig in sig_rules.get("signatures") or []:
            if (any(w in body for w in sig.get("발행주체") or [])
                    and any(w in body for w in sig.get("조사명") or [])):
                return f"sig:{sig['id']}"
        return None

    def _speaker(fact) -> str | None:
        """화자 id. **None 이면 화자로 세지 않는다**(fail-closed).

        「모르면 독립으로 쳐준다」는 fail-open 이고 그게 지금의 착시를 만든 구조다.
        모르면 세지 않는다 — 자기 사실의 점수를 잃지는 않지만 남에게 가점을 주지도 못한다.
        """
        if sig_on:
            s = _signature_of(fact)
            if s:
                return s
            if sig_need:
                return None
        k, _ = kind_of(fact.url, wl)
        return bucket if k in self_kinds else domain_of(fact.url)

    #: fact_id → (독립 화자 수, conflict 설명)
    cross: dict[str, tuple] = {}
    for key, members in groups.items():
        for f in members:
            same, diff, scaled = [], [], []
            for g in members:
                if same_value(f.value_num, g.value_num, xc):
                    same.append(g)
                else:
                    (scaled if is_scale_suspect(f.value_num, g.value_num, xc) else diff).append(g)
            note = ""
            if scaled:
                r = max(abs(f.value_num), abs(scaled[0].value_num)) / max(
                    min(abs(f.value_num), abs(scaled[0].value_num)), 1e-12)
                note = (f"스케일 의심 — 같은 단위({key[-1]})에 {r:,.0f}배 차이"
                        f"({f.value_num:,.10g} ↔ {scaled[0].value_num:,.10g}). 단위 오독일 수 있다")
            elif diff:
                vals = sorted({g.value_num for g in members})
                note = (f"값 갈림 — 같은 대상·단위({key[-1]})에 값 {len(vals)}종: "
                        + " · ".join(f"{v:,.10g}" for v in vals[:5]))
            # None 은 «화자를 특정 못 함» 이라 세지 않는다 (fail-closed).
            cross[f.fact_id] = ({s for s in (_speaker(g) for g in same) if s}, note)

    # ── 기준 v2 직교 축 계산 — **이 한 곳에서만** (판 ㉙ S1) ──────────────
    #   소비자가 재계산하지 않는다. 같은 물음을 두 곳이 각자 풀면 두 번 갈라진다 —
    #   판 ⑫ 키 · ⑬ 라우팅 · ⑯ 별칭 · ㉒ 프롬프트-게이트 · ㉕ 표_계열 · ㉙ 핀, 실측 6회.
    fill = rules.get("fill") or {}

    def _v2(f, kind, by, n_cross, 격리사유: str | None = None) -> dict:
        """`채택`·`채택_불가_사유`·`등급`·`등급_근거`·`retrieved_at` 을 만든다.

        **등급은 점수를 경유하지 않는다** — `등급표[kind]` 에서 직접 온다. 점수를 경유하면
        `base_score` 를 올리는 순간 등급·표기·계측 3축으로 동시에 번진다(= 낮은 등급의
        높은 표기 = 금지선).
        """
        사유: list[str] = []
        if 격리사유:
            # 격리는 4요건보다 앞선다 — 슬롯과 안 맞는 값은 애초에 이 칸의 재료가 아니다.
            사유.append(격리사유)
        if f.value_num is None and not (f.quote or ""):
            사유.append("관측 없음")
        if not f.url:
            사유.append("url 없음")
        if not f.retrieved_at:
            # 백필 금지 — 만들어 넣지 않고 채택 불가로 두고 재수집 힌트에 올린다.
            사유.append("retrieved_at 없음")
        if not f.quote_verified:
            사유.append("인용 대조 실패")

        불가 = fill.get("채택_불가_부류") or {}
        미등재 = (kind == 불가.get("기본_미등재_kind")
                  and by == 불가.get("기본_미등재_kind_by"))
        if kind in (불가.get("kinds") or {}) and not 미등재:
            # 추적 불가·비관측은 **등급 문제가 아니라 채택 요건 미달**이다(결정 ②).
            사유.append(불가["kinds"][kind])
        # ── 등재 거부 도메인 (판 ㉙ S1 — 백로그 30 의 절반을 닫는다) ──────────
        #   `whitelist.rejected` 는 「읽는 코드 0」이었다. 즉 **거부했다와 아직 안 봤다가
        #   동작상 같았다.** 판 ㉙ 이 미등재를 여는 판이므로 그 상태를 두면 v.daum.net
        #   같은 **원출처 불명 재게시**가 개방의 문으로 같이 들어온다.
        #   ⚠ **점수·등급·kind_of 는 건드리지 않는다** — 새 축(채택)에서만 막는다.
        #     기존 원장의 label·score 가 흔들리지 않아야 개방 효과를 분리해 잴 수 있다.
        #   계보: 「추적 불가는 등급 문제가 아니라 채택 요건 미달」(결정 ②)의 적용 확장.
        if 불가.get("등재_거부_적용"):
            dom = domain_of(f.url)
            for rj in (wl.get("rejected") or []):
                if dom == rj.get("domain"):
                    사유.append(f"{불가['등재_거부_사유']} — {rj.get('why', '')[:60]}")
                    break

        표 = fill.get("등급표") or {}
        등급 = 표.get("_기본") or "추정"
        for lv in ("확정", "실무 신뢰", "추정"):
            if kind in (표.get(lv) or []):
                등급, = (lv,)
                break
        근거 = f"등급표:{kind}"

        up = fill.get("등급_상향") or {}
        사다리 = up.get("사다리") or []
        if (up.get("독립_화자_최소") and n_cross >= up["독립_화자_최소"]
                and 등급 in 사다리 and not 사유):
            i = min(사다리.index(등급) + int(up.get("최대_단계") or 1), len(사다리) - 1)
            if 사다리[i] != 등급:
                등급, 근거 = 사다리[i], f"상향:독립화자 {n_cross} ({근거})"
        return {"채택": not 사유, "채택_불가_사유": 사유,
                "등급": 등급, "등급_근거": 근거, "retrieved_at": f.retrieved_at}

    ledger = Ledger()
    for f in facts:
        slot = slots[f.slot_id]
        kind, by = kind_of(f.url, wl)
        reasons: list[str] = []
        caps: list[int] = []

        # ── 격리 ① 인용문이 본문에 없으면 애초에 근거가 아니다 (F7).
        #    점수를 매기지 않는다 — 'score 6 인데 미검증' 같은 상태를 원장에 남기지 않기 위해.
        if not f.quote_verified:
            ledger.rows.append(LedgerRow(
                fact_id=f.fact_id, slot_id=f.slot_id, url=f.url, kind=kind, kind_by=by,
                score=0, label="미검증", cross=0,
                reasons=["인용문이 본문에 없음 — 근거로 쓸 수 없다"],
                off_slot_reason=None, **_v2(f, kind, by, 0)))
            ledger.facts[f.fact_id] = f
            continue

        # ── 격리 ② 슬롯과 안 맞으면 격리 (4겹)
        # ⚠ 문서 조회는 **normalize 와 같은 순서**여야 한다 — URL 우선, trace_id 폴백.
        #   trace_id 로만 찾던 시절, 인용이 서로 다른 문서에서 오면 **첫 문서로 대조**됐다
        #   (조인 버그 E). `web.collect` 가 finding.trace_id 를 첫 문서로 덮어 대부분 가려졌고,
        #   가려진 자리에서는 사유가 틀린 채로 격리됐다(gate2-01 S7: 진짜 사인은 단위 불일치인데
        #   must_contain 으로 보고됐다). **두 곳이 같은 방식으로 찾아야 한다.**
        off = off_slot_reason(f, slot, by_url.get(canonical_url(f.url))
                              or docs.get(f.trace_id), rules)
        if off:
            ledger.rows.append(LedgerRow(
                fact_id=f.fact_id, slot_id=f.slot_id, url=f.url, kind=kind, kind_by=by,
                score=0, label="off_slot", cross=0, reasons=["슬롯 불일치 — 격리 보관"],
                off_slot_reason=off, **_v2(f, kind, by, 0, 격리사유="슬롯 불일치 — 격리 보관")))
            ledger.facts[f.fact_id] = f
            continue

        score = sc["base_score"].get(kind, 1)

        if f.content_status != "usable":
            caps.append(sc["caps"]["no_usable_body"])
            reasons.append(f"본문 미확보({f.content_status}) — 상한 {sc['caps']['no_usable_body']}")
        if f.year is None:
            # 연도 미표기 예외 — **자기 요금 페이지처럼 연도가 없는 것이 정상인 자리**에만.
            # 조건·목록·문구는 전부 규칙 파일에 있다(절대규칙 7). 상한은 안 올린다.
            exm = sc.get("missing_year_exemption") or {}
            ok = (exm.get("enabled")
                  and kind in (exm.get("kinds") or [])
                  and slot.claim_type in (exm.get("claim_types") or []))
            if ok and exm.get("require_retrieved_at") and not f.retrieved_at:
                # 예외의 **대가**를 못 냈다 — 조용히 봐주지 않고 사유를 남기고 감점한다.
                ok = False
                reasons.append(exm.get("reason_denied") or "연도 미표기 면제 불가")
            if ok:
                reasons.append((exm.get("reason") or "연도 미표기 면제")
                               .replace("{retrieved_at}", str(f.retrieved_at)))
            else:
                score -= sc["missing_year_penalty"]
                reasons.append("필수 요소 누락: year")
        else:
            age = reference_year - f.year
            if age > sc["fresh_years"]:
                score -= sc["stale_penalty"]
                reasons.append(f"신선도: {f.year}년 자료 ({age}년 경과)")

        speakers, conflict = cross.get(f.fact_id, (set(), ""))
        n_cross = len(speakers)
        if n_cross >= sc["cross_min_sources"]:
            score += sc["cross_bonus"]
            reasons.append(f"교차 확인 +{sc['cross_bonus']} (독립 발행자 {n_cross})")
        if f.year is None:
            # 연도 미상은 **unknown 버킷**이라 연도 있는 사실과 교차하지 않는다.
            # 사유를 원장에 남긴다 — 점수가 왜 안 올랐는지가 보이지 않으면 조용한 폴백과 같다.
            reasons.append("연도 미상 — match_key unknown 버킷(연도 있는 사실과 교차하지 않는다)")
        if conflict:
            # 모순은 확인 실패가 아니라 **그 자체가 조사 결과**다 (B3 의 diverged 와 같은 철학).
            # 가점만 죽이고 조용히 넘어가면 "출처마다 값이 다르다"는 발견이 사라진다.
            reasons.append(conflict)

        allowed = _allowed_kinds(slot)
        if allowed and kind not in allowed:
            caps.append(sc["caps"]["kind_not_allowed"])
            reasons.append(f"허용 외 출처 유형({kind}) — 상한 {sc['caps']['kind_not_allowed']}")

        if caps:
            score = min(score, min(caps))
        score = max(int(score), 0)

        th = sc["label_thresholds"]
        label = "확인됨" if score >= th["확인됨"] else ("출처약함" if score >= th["출처약함"] else "미확인")
        if slot.accept.get("min_score") and score < slot.accept["min_score"] and label == "확인됨":
            label = "출처약함"          # 슬롯이 더 엄격하면 슬롯을 따른다

        ledger.rows.append(LedgerRow(
            fact_id=f.fact_id, slot_id=f.slot_id, url=f.url, kind=kind, kind_by=by,
            score=score, label=label, cross=n_cross, reasons=reasons, conflict=conflict,
            # 값의 범위 꼬리표는 **점수와 무관하게** 값 옆에 붙어 간다. 3,147억이
            # '시장 매출' 이 아니라 '카페24 전사 매출' 이라는 사실은 등급이 아니라
            # 그 숫자의 정의다 — 지우면 읽는 사람이 시장규모로 오해한다.
            scope=f.scope or "", scope_note=_scope_note(f.scope, sc),
            **_v2(f, kind, by, n_cross)))
        ledger.facts[f.fact_id] = f
    return ledger


def _scope_note(scope: str, scoring_rules: dict) -> str:
    """꼬리표 문구는 **규칙 파일에서** 온다(절대규칙 7). 코드에 경계 문구를 박지 않는다."""
    if not scope:
        return ""
    return (scoring_rules.get("scope_labels") or {}).get(scope, "")


def _allowed_kinds(slot: Slot) -> list[str]:
    """슬롯이 허용하는 출처 유형. accept.allowed_kinds 가 있으면 그것, 없으면 제한 없음."""
    return list(slot.accept.get("allowed_kinds") or [])


# ══════════════════════════════════════════════════════════════
# A4 ⑩ — 커버리지 점검. **여기가 맨 뒤인 이유: confirmed 는 등급이 끝나야 확정된다.**
#         '충족'과 '충분'은 다른 얘기다 — min_facts 미달이면 thin 으로 표시한다.
# ══════════════════════════════════════════════════════════════
def check_coverage(ledger: Ledger, slots: list[Slot], rules: dict) -> list[Coverage]:
    default_min = rules["scoring"]["coverage"]["default_min_facts"]
    out = []
    for slot in slots:
        rows = [r for r in ledger.by_slot(slot.slot_id) if r.label not in QUARANTINE_LABELS]
        ok = [r for r in rows if _fx.filled(r, "a_desk.check_coverage")]
        min_confirmed = int(slot.accept.get("min_confirmed", 1))
        min_facts = int(slot.accept.get("min_facts", default_min))

        if len(ok) >= min_confirmed:
            status = "충족"
        elif rows:
            status = "보강필요"
        else:
            status = "공백"

        # ── 단일 원천 연계열 예외 (판 ㉕, 사용자 도장 · **좁게**) ─────────
        # 공식 통계의 연 계열은 **한 슬롯 = 한 표 · 한 해 = 한 셀**이라 독립 2건이
        # **구조적으로 나올 수 없다.** 채택을 거부하는 대신 **딱지를 값으로 붙인다** —
        # 정직성은 채택 거부가 아니라 표기로 지킨다(§0). ⚠ **`gov_stat` 5점 한정**:
        # 낮은 등급 원천으로 번지면 「교차 없이 채워짐」이 일반화된다.
        _sx = (rules["scoring"]["coverage"].get("단일_원천_연계열") or {})
        단일_원천 = None
        # ⚠ **1건일 때만이다.** 도장 문구가 「**단일** 원천」이고, confirmed=2 가 3 에
        #   미달하는 경우까지 면제하면 그건 「교차가 부족하다」를 덮는 것이지
        #   「교차가 구조적으로 불가하다」가 아니다 (테스트 `test_step2` 가 잡았다).
        if _sx.get("enabled") and len(ok) == 1 and len(ok) < min_facts:
            _c = _sx.get("적용_조건") or {}
            if ok and all((r.score or 0) >= int(_c.get("최소_점수", 5))
                          and r.kind in (_c.get("허용_kind") or []) for r in ok):
                min_facts = int(_sx.get("완화_min_facts", 1))
                단일_원천 = _sx.get("딱지")

        cov = Coverage(slot_id=slot.slot_id, status=status,
                       confirmed=len(ok), total=len(rows),
                       evidence_ids=[r.fact_id for r in ok] or [r.fact_id for r in rows],
                       min_facts=min_facts)
        if 단일_원천:
            # **해소가 아니라 값으로 유지한다** — 수신 모듈은 교차확인 여부를 알 권리가 있다.
            cov.단일_원천 = 단일_원천
        if status == "공백":
            cov.retry_hint = f"{slot.slot_id}: 수집 0건 — 검색·fetch 로그를 볼 것"
        out.append(cov)
    return out


# ══════════════════════════════════════════════════════════════
# A4 전체
# ══════════════════════════════════════════════════════════════
def normalize_and_grade(findings: list[Finding], docs: dict[str, Document],
                        slots: list[Slot], rules: dict, reference_year: int,
                        run=None) -> tuple:
    smap = {s.slot_id: s for s in slots}
    facts = normalize(findings, docs, smap, rules)
    ledger = grade(facts, smap, docs, rules, reference_year)
    coverage = check_coverage(ledger, slots, rules)
    if run is not None:
        run.log_many("a4_facts", facts)
        run.log_many("a4_ledger", ledger.rows)
        run.log_many("a4_coverage", coverage)
        _log_diagnostics(facts, ledger, slots, run)
    return ledger, coverage


#: off_slot 사유 문자열 → 어느 겹인지. `off_slot_reason` 이 만드는 접두사와 1:1 이다.
_OFF_SLOT_LAYERS = ("must_contain 없음", "must_not_contain 포함", "단위 불일치",
                    "값범위 밖", "기간 불일치")


def _log_diagnostics(facts, ledger, slots, run) -> None:
    """지표를 읽을 수 있게 하는 계측. 판정에는 쓰지 않는다.

    **왜 필요한가** — 슬롯의 기간을 옮기면 확인됨이 안 늘 수 있고, 그때 원인이 둘로 갈린다:
    (a) 필터는 열렸는데 신선한 자료가 애초에 안 잡힌다 → A3 검색 문제
    (b) 필터가 여전히 자른다                         → 슬롯 정의 문제
    연도 분포와 '기간 겹으로 잘린 건수'가 없으면 이 둘을 구분할 수 없다.
    """
    run.count("a4_slots.total", len(slots))

    for f in facts:                              # 연도 분포 — **출처를 나눠 담는다**
        run.count(f"a4_year.{f.year if f.year is not None else 'none'}")
        run.count(f"a4_year_source.{f.year_source or 'none'}")

    # off_slot 5겹 중 어디서 잘렸나 — **claim_type 과 함께 센다.**
    # 같은 '겹'이라도 성격이 다르다. COMPARABLE 은 정의상 남의 시장을 보는 슬롯이라
    # must_contain 에 우리 주제어를 요구하는 것 자체가 설계 결함일 수 있다(S15 실측).
    # 겹만 세면 "정상 격리"와 "설계 결함"이 한 칸에 섞여 다음 표적을 못 고른다.
    ctype = {s.slot_id: s.claim_type for s in slots}
    for r in ledger.rows:
        if not r.off_slot_reason:
            continue
        layer = next((k for k in _OFF_SLOT_LAYERS if r.off_slot_reason.startswith(k)), "기타")
        run.count(f"a4_off_slot.{layer}")
        run.count(f"a4_off_slot_by_type.{layer}|{ctype.get(r.slot_id, '?')}")
