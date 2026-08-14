# -*- coding: utf-8 -*-
"""A1 — 식 설계와 슬롯 생성. **LLM ✓ (A블록의 세 번째이자 마지막 LLM 표면)**

    Concept → Formula[] → Slot[]

두 가지를 지킨다:
  · **자유 서술 금지.** 식은 템플릿 T1~T5 에서 고르게 하고, 밖의 것은 버린다.
    어느 식을 골랐는지 로그에 남아야 "이 업종엔 이 식이 맞더라"가 축적된다.
  · **규칙 6.** `concept.research_view()` 만 넘긴다 — 팀·예산·기간과 사용자 가격 가설은
    수집 프롬프트에 들어가지 않는다.

`must_contain` / `must_not_contain` / `value_range` 는 **사람이 적는 칸**이다(F6).
A1 은 채우지 않는다. 사람이 적어둔 것이 없으면 그대로 두고, **그 사실을 세어** 보고서에 남긴다
— 가드가 없는 슬롯은 off_slot 4겹 중 3겹이 무력하다.
"""
from __future__ import annotations

import json
import re

import prompts
from a_desk import normalize_unit
from schema import FORMULA_TEMPLATES, Formula, FormulaVar, Slot, 경계_승격

MODEL = "gpt-4o-mini"

# "없음을 표현한 것"과 "값"을 구분한다.
# 목록은 rules/units.v1.json 의 nullish_tokens 한 곳에만 둔다 — 필드마다 따로 처리하면
# 새는 곳이 생긴다. (stat_code "None" 을 '기재함'으로 세어 **모델의 정직함을 코드가 지운** 사고)
_NULLISH_FALLBACK = frozenset({"", "-", "null", "none", "n/a", "na", "미상", "모름", "없음"})
_STAT_CODE = re.compile(r"^\d{3}/[A-Za-z0-9_]+$")     # orgId 3자리 / tblId


def _nullish(rules: dict | None) -> frozenset:
    try:
        return frozenset(t.lower() for t in rules["units"]["nullish_tokens"]["tokens"])
    except Exception:
        return _NULLISH_FALLBACK


def _clean(v, rules: dict | None = None):
    """'없음'을 뜻하는 온갖 표기를 None 으로 정규화한다."""
    if v is None:
        return None
    t = str(v).strip()
    return None if t.lower() in _nullish(rules) else t


def stat_code_state(code, rules: dict | None = None) -> str:
    """3단 판정 — 안 냄(정직) / 형식 틀림 / 형식 맞음.

    형식이 맞아도 **실재하는 통계표인지는 호출해 봐야 안다**(err 21).
    실제 결과는 run 로그에서 no_code / hallucinated_code / hit 로 다시 센다.
    """
    c = _clean(code, rules)
    if c is None:
        return "missing"
    return "given" if _STAT_CODE.match(c) else "malformed"


DEFAULT_TARGETS = ("TAM", "SAM", "COMP", "COMPARABLE", "PAIN", "PRICE")

_EXTRA = """

목표값: {targets}
각 목표값마다 path 가 "topdown" 인 식 1개, "bottomup" 인 식 1개를 만든다.

변수를 적을 때:
- 통계청 KOSIS 통계표로 얻을 수 있는 변수면 stat_code 에 "orgId/tblId" 를 적어라.
  **모르면 null 로 두어라 — 지어내지 마라.** 틀린 ID 는 조용히 빈손이 된다.
- 상장사 공시로 얻을 수 있으면 corp_name 에 회사 실명을 적어라.
- subject 에 지역명을 넣지 마라. 지역은 따로 다룬다.
"""


def design_formulas(concept, rules: dict, meter, targets=DEFAULT_TARGETS) -> tuple:
    """(formulas, rejected) — 템플릿 밖의 식은 버리고 사유를 남긴다."""
    tpl = "\n".join(f"  {k}: {v}" for k, v in FORMULA_TEMPLATES.items())
    view = concept.research_view() if hasattr(concept, "research_view") else dict(concept)
    body = prompts.render(prompts.FORMULA,
                          concept=json.dumps(view, ensure_ascii=False), templates=tpl)
    body += _EXTRA.replace("{targets}", ", ".join(targets))

    r = meter.create("a1_formula", model=MODEL, input=body)
    m = re.search(r"\{.*\}", r.output_text or "", re.S)
    try:
        data = json.loads(m.group(0)) if m else {}
    except Exception:
        data = {}

    formulas, rejected = [], []
    for f in data.get("formulas", []):
        fid = str(f.get("formula_id") or f"F{len(formulas) + 1}")
        if f.get("template") not in FORMULA_TEMPLATES:
            rejected.append({"formula_id": fid, "why": f"템플릿 밖: {f.get('template')}"})
            continue
        if f.get("path") not in ("topdown", "bottomup"):
            rejected.append({"formula_id": fid, "why": f"path 이상: {f.get('path')}"})
            continue
        vs = []
        for i, v in enumerate(f.get("vars") or []):
            vs.append(FormulaVar(
                var_id=str(v.get("var_id") or f"{fid}-V{i + 1}"),
                var_role=str(v.get("var_role") or v.get("role") or ""),
                subject=str(v.get("subject") or ""),
                metric=str(v.get("metric") or ""),
                period=str(v.get("period") or ""),
                unit=str(v.get("unit") or ""),
                subject_code=_clean(v.get("subject_code"), rules),
                stat_code=_clean(v.get("stat_code"), rules),
                corp_name=_clean(v.get("corp_name"), rules)))
        if not vs:
            rejected.append({"formula_id": fid, "why": "변수 없음"})
            continue
        formulas.append(Formula(formula_id=fid, target=f.get("target"),
                                path=f.get("path"), template=f["template"], vars=vs))
    return formulas, rejected


def slots_from_formulas(formulas: list, concept, human_slots: list | None = None,
                        rules: dict | None = None) -> tuple:
    """식의 변수 하나 = 슬롯 하나. (slots, unguarded_slot_ids)"""
    region = getattr(concept, "region", "대한민국")
    by_key = {}
    for h in (human_slots or []):
        by_key[(h.get("claim_type"), h.get("metric"))] = h

    guards = ((rules or {}).get("guards") or {}).get("by_claim_type", {})
    pct = ((rules or {}).get("guards") or {}).get("_percent", {})
    cur = ((rules or {}).get("guards") or {}).get("_currency", {})
    units = (rules or {}).get("units") or {"unit_norm": {}}

    slots, unguarded = [], []
    n = 0
    for f in formulas:
        for v in f.vars:
            n += 1
            h = by_key.get((f.target, v.metric)) or {}
            sid = f"S{n:02d}"
            # ⚠ 사람 칸 조인은 `(claim_type, metric)` 이고 **A1 이 쓰는 metric 은 자유 서술**이라
            #   거의 안 맞는다(full-01: 27개 중 25개). 못 맞추면 claim_type 기본 가드를 깔되
            #   **그 대체를 조용히 하지 않는다** — 무엇이 사람 것이고 무엇이 기본값인지 남긴다.
            #   (뒤의 `enforce_slot_rules` 자동채움이 must_contain 을 채우면 겉보기엔 가드가
            #    있어 보인다. full-03 에서 unguarded 21 인데 must_contain 슬롯이 23 이었다.)
            g = guards.get(f.target, {})
            if not h:
                unguarded.append({
                    "slot_id": sid, "claim_type": f.target, "metric": v.metric,
                    "unguarded": True,
                    "why": f"사람 칸 조인 실패 — (claim_type, metric)=({f.target}, {v.metric}) "
                           f"가 data/slots.json 에 없다",
                    "applied": {
                        "must_contain": "없음(맨몸)",
                        "must_not_contain": ("claim_type 기본 가드"
                                             if g.get("must_not_contain") else "없음"),
                        "value_range": ("단위 규칙 또는 claim_type 기본 가드"
                                        if g.get("value_range") else "없음"),
                    }})
            elif not h.get("must_contain"):
                unguarded.append({
                    "slot_id": sid, "claim_type": f.target, "metric": v.metric,
                    "unguarded": True,
                    "why": "사람 칸은 붙었으나 must_contain 이 비어 있다",
                    "applied": {"must_contain": "없음(맨몸)"}})
            # **단위를 먼저 정규화하고 가드를 고른다.** 원문 표기로 고르면 A1 이 '백분율' 이라
            # 쓸 때 `_percent` 를 못 맞춰 % 슬롯이 값범위 [0,100] 대신 [100,1e8] 을 받는다
            # (full-01 에서 실제로 났다). 정규화하면 off_slot 단위 겹과 가드가 같은 표를 본다.
            unit = normalize_unit(v.unit, units)[0] or v.unit
            vr = h.get("value_range") or g.get("value_range")
            if unit in (pct.get("units") or []):
                vr = pct.get("value_range") or vr        # 단위가 %면 단위가 이긴다
            elif unit in (cur.get("units") or []):
                # 단위가 원이면 화폐 범위. claim_type 기본 상한 1e8(1억)로는 상장사 매출
                # 3,147억이 값범위 밖으로 걸리고, 규모가 작은 오답만 살아남는다(full-04).
                vr = cur.get("value_range") or vr
            slots.append(Slot(
                slot_id=sid, var_id=v.var_id, formula_id=f.formula_id,
                claim_type=f.target, subject=v.subject, metric=v.metric,
                period=v.period or "2023", unit=unit, region=region,
                subject_code=v.subject_code or h.get("subject_code"),
                stat_code=v.stat_code, corp_name=v.corp_name,
                must_contain=h.get("must_contain") or [],
                must_not_contain=h.get("must_not_contain") or g.get("must_not_contain") or [],
                value_range=vr,
                accept=h.get("accept") or {"min_score": 5,
                                           "min_facts": 2, "min_confirmed": 1}))
    return slots, unguarded


# ══════════════════════════════════════════════════════════════
# A1 검증기 — LLM 0회 후처리. **형식만 맞으면 통과하던 것을 막는다.**
# ══════════════════════════════════════════════════════════════
_WORD = re.compile(r"[가-힣A-Za-z]+")


def topic_words(concept, rules: dict | None = None) -> list[str]:
    """concept 에서 검색 주제어를 뽑는다. **코드에 업종 상수를 박지 않는다.**"""
    cfg = ((rules or {}).get("slotcheck") or {}).get("topic_words") or {}
    fields = cfg.get("from_fields") or ["name"]
    stop = set(cfg.get("stopwords") or [])
    min_len, max_words = cfg.get("min_len", 2), cfg.get("max_words", 3)

    out = []
    for fld in fields:
        for w in _WORD.findall(str(getattr(concept, fld, "") or "")):
            if len(w) >= min_len and w not in stop and w not in out:
                out.append(w)
            if len(out) >= max_words:
                return out
    return out


def period_window(period, rules: dict, as_of_year: int) -> tuple:
    """명시된 기간 → (period_min, period_max). 기간 겹(off_slot 5겹째)이 읽는 창이다.

    사람이 적은 슬롯도 이 함수로 창을 받는다 — 안 그러면 A1 슬롯에만 기간 검사가 걸린다.
    """
    p = (rules.get("slotcheck") or {}).get("period") or {}
    sc = rules.get("scoring") or {}
    year = _year_of(period)
    tol = (sc.get("off_slot") or {}).get("period_tolerance_years")
    if year is None or tol is None:
        return None, None
    lo, hi = year - tol, year + tol
    # 하한을 신선 경계 아래로 내리지 않는다. 내리면 통과시킨 뒤 신선도로 감점하는
    # 슬롯이 생겨 지금 걷어내려는 천장이 그 슬롯들에만 되살아난다. 기준은 **as_of_year**
    # 다 — period 기준으로 읽으면 2024-3=2021 이 되어 이 규칙이 통째로 무효가 된다.
    if p.get("clamp_window_to_fresh") and sc.get("fresh_years") is not None:
        lo = max(lo, as_of_year - sc["fresh_years"])
    return lo, hi


def _year_of(period) -> int | None:
    m = re.search(r"(?:19|20)\d{2}", str(period or ""))
    return int(m.group(0)) if m else None


def _derive_period(metric: str, rules: dict, as_of_year: int) -> str:
    """절대연도를 박지 않고 as_of 에서 유도한다. **이 값이 검색 쿼리로 나간다.**"""
    p = (rules.get("slotcheck") or {}).get("period") or {}
    lagged = str(metric or "").strip() in (p.get("lagged_metrics") or [])
    return str(as_of_year - (p.get("lagged_offset", 2) if lagged
                             else p.get("default_offset", 1)))


# ══════════════════════════════════════════════════════════════
# 사람 칸 덮어쓰기 — **파생 실행에서 채점 규칙만 현재 값으로 바꾼다**
# ══════════════════════════════════════════════════════════════
HUMAN_FIELDS = ("must_contain", "must_not_contain", "value_range",
                # 판 ㉘ 승격 — **경계는 수집 조건이 아니라 표시**다. 무엇을 받아올지가
                # 아니라 **받은 것을 어떻게 읽지 말아야 하는지**를 말하므로, 사람 칸과
                # 같은 이유로 overlay 대상이다. 이게 없으면 옛 원장이 승격 후에도
                # **경계를 되찾지 못한다**(`nailrobot-02` 재채점 실측 — 원본 원장에
                # 이미 없어서 복원이 빈손이었다).
                "경계", "경계_출처", "proxy_선언", "proxy_사유", "경계_proxy")


def overlay_human_slots(slots: list, human_slots: list | None) -> tuple:
    """`data/slots.json` 의 **사람 칸만** 덮어쓴다. (slots, diff)

    왜 덮어써도 되는가 — 사람 칸은 **수집 조건이 아니라 채점 규칙**이다. `must_contain`
    을 넓히는 것은 이미 받아 둔 문서를 어떻게 볼지를 바꾸는 것이지 무엇을 받아올지를
    바꾸지 않는다. 수집이 같고 채점만 바뀌는 것은 **재채점의 정의 그대로**다.

    반대로 `subject`·`period`·`unit`·`stat_code` 는 **그 수집을 만든 조건**이라 절대
    건드리지 않는다. 바꾸면 원장이 자기를 만든 검색과 어긋나고, 그 실행은 무엇을 잰
    것도 아닌 게 된다.

    대응은 **`slot_id` 가 먼저다.** 파생 실행은 원본 `result.json` 에서 슬롯을 복원하므로
    `slot_id` 가 그대로 남아 있다. `slot_id` 가 없을 때만 `(claim_type, metric)` 으로
    떨어진다 — A1 이 만든 슬롯은 사람 슬롯과 id 가 다르기 때문이다.

    ⚠ **`(claim_type, metric)` 은 유일하지 않다.** 실명 경쟁사 슬롯 S8(쿠팡포스)·S9(토스플레이스)
    가 둘 다 `(COMP, 월 구독료)` 라 dict 에서 뒤엣것이 앞엣것을 덮었고, S8 이 **토스플레이스의
    `must_contain` 으로 채점**됐다(report1-01-fix 실측). 충돌은 조용히 넘기지 않고 경고한다.
    """
    by_id = {h["slot_id"]: h for h in (human_slots or []) if h.get("slot_id")}
    by_key, dup = {}, []
    for h in (human_slots or []):
        k = (h.get("claim_type"), h.get("metric"))
        if k in by_key:
            dup.append(k)
        by_key[k] = h
    diff = []
    if dup:
        # 조용히 덮으면 엉뚱한 슬롯의 가드로 채점된다 — 시끄럽게 알린다.
        print(f"    ⚠ 사람 슬롯에 (claim_type, metric) 중복 {sorted(set(dup))} — "
              f"slot_id 로 대응한다")
    for s in slots:
        h = by_id.get(s.slot_id) or by_key.get((s.claim_type, s.metric))
        if not h:
            continue
        changed = {}
        # `formula_id` 는 **B 의 조인 키**다(`substitute` 가 slot.formula_id == formula.formula_id
        # 로 변수를 잇는다). 검색에 쓰이지 않으므로 수집 조건이 아니고, 사람이 식을 다시 쓰면
        # 같이 움직여야 한다 — 안 옮기면 파생 실행에서 **변수가 조용히 가정으로 떨어진다**
        # (report2-01 실측: TD 의 V1 이 '가정값 없음 — 계산 불가' 로 죽어 대조가 insufficient).
        # subject·period·unit·stat_code 와는 성격이 다르다 — 그것들은 그 수집을 만든 조건이다.
        if h.get("formula_id") and h["formula_id"] != s.formula_id:
            changed["formula_id"] = {"from": s.formula_id, "to": h["formula_id"]}
            s.formula_id = h["formula_id"]
        # 옛 스냅샷은 경계급 키가 아직 `_` 접두다 — **승격 이름으로 이관해서** 본다.
        # 표는 `schema.경계_승격` **하나뿐**이고 `run.mk_slot` 이 같은 것을 쓴다(사본 금지).
        for old, new in 경계_승격.items():
            if h.get(old) not in (None, "", {}) and h.get(new) in (None, "", {}):
                h[new] = h[old]
        # 쓴 주체는 **필드가 아니라 기록으로** 가른다(도장 조건) — 주체별로 키를 가르면
        # 여섯 번째 분열이다. 코드가 붙인 `경계_proxy` 가 있으면 하네스, 없으면 사람 칸이다.
        if h.get("경계") and not h.get("경계_출처"):
            h["경계_출처"] = "하네스" if h.get("경계_proxy") or h.get("_경계_proxy") else "사람"
        for f in HUMAN_FIELDS:
            if f in h and h[f] != getattr(s, f):
                changed[f] = {"from": getattr(s, f), "to": h[f]}
                setattr(s, f, h[f])
        # accept 는 통째로 갈아끼우지 않는다 — min_facts 만이 사람이 조정하는 칸이고,
        # min_score·min_sources 까지 덮으면 채점 문턱이 조용히 따라 움직인다.
        mf = (h.get("accept") or {}).get("min_facts")
        if mf is not None and mf != (s.accept or {}).get("min_facts"):
            changed["accept.min_facts"] = {"from": (s.accept or {}).get("min_facts"), "to": mf}
            s.accept = {**(s.accept or {}), "min_facts": mf}
        if changed:
            diff.append({"slot_id": s.slot_id, "changed": changed})
    return slots, diff


def _with_aliases(topics: list, cfg: dict) -> list:
    """주제어 목록 뒤에 통계표 용어를 이어 붙인다. 순서는 주제어 먼저 — 잘려도 원래 말은 남는다."""
    alias = ((cfg.get("subject_terms") or {}).get("aliases") or {})
    out = []
    for t in topics:
        for w in [t, *alias.get(t, [])]:
            if w not in out:
                out.append(w)
    return out


def _discard_reason(slot, cfg: dict) -> dict | None:
    """'답이 나올 수 없는 슬롯' 이면 사유 코드를 낸다. 아니면 None.

    사유는 전부 `slotcheck.discard.reasons` 에 있고 코드째로 §7 까지 간다 — 무엇을
    왜 안 던졌는지가 산출물에 남아야 커버리지가 낮을 때 원인을 가릴 수 있다(절대규칙 5).
    """
    r = (cfg.get("discard") or {}).get("reasons") or {}

    if (r.get("empty_subject_or_metric") or {}).get("enabled") and \
            (not (slot.subject or "").strip() or not (slot.metric or "").strip()):
        return {"code": "empty_subject_or_metric", "why": "subject·metric 이 비어 검색할 수 없다"}

    mi = r.get("metric_not_indicative") or {}
    if mi.get("enabled") and (slot.metric or "").strip() in (mi.get("metrics") or []):
        return {"code": "metric_not_indicative",
                "why": f"metric '{slot.metric}' 은 무엇을 세는지 지시하지 않는다"}

    if (r.get("unit_missing") or {}).get("enabled") and not str(slot.unit or "").strip():
        return {"code": "unit_missing", "why": "단위가 없어 단위·값범위 겹이 돌지 않는다"}

    return None


def enforce_slot_rules(slots: list, concept, rules: dict, as_of_year: int) -> tuple:
    """A1 출력의 **내용**을 강제한다. (slots, discarded, fixes)

    보정은 값을 만들어내는 게 아니라 A1 이 비운 칸을 규칙으로 채우는 것이고,
    무엇을 고쳤는지는 전부 `fixes` 로 나가 `a1_audit` 에 남는다.
    """
    cfg = (rules.get("slotcheck") or {})
    topics = topic_words(concept, rules)
    kept, discarded, fixes = [], [], []

    for s in slots:
        why = _discard_reason(s, cfg)
        if why:
            discarded.append({"slot_id": s.slot_id, **why})
            continue

        before = s.period
        s.period = _derive_period(s.metric, rules, as_of_year)
        s.period_min, s.period_max = period_window(s.period, rules, as_of_year)
        if before != s.period:
            fixes.append({"slot_id": s.slot_id, "what": "period",
                          "from": before, "to": s.period,
                          "window": [s.period_min, s.period_max]})

        # subject 가 곧 검색 쿼리다. 주제어가 없으면 검색이 아무 데로나 간다.
        # 쿼리는 subject + metric 을 이어 붙여 만든다(`web.plan_query`). 그러니 주제어가
        # 있는지도 **둘을 합쳐서** 본다. subject 만 보면 metric 에 이미 '카페' 가 있는데
        # 앞에 또 붙여 "카페 상위시장규모 카페 및 커피숍" 이 된다 — 지도가 경고한
        # "서울 서울 커피전문점" 과 같은 중복이다.
        sub = (cfg.get("subject") or {})
        if sub.get("require_topic_word") and topics and \
                not any(t in f"{s.subject} {s.metric}" for t in topics):
            was = s.subject
            s.subject = (sub.get("prefix_format") or "{topic} {subject}").format(
                topic=topics[0], subject=s.subject)
            fixes.append({"slot_id": s.slot_id, "what": "subject", "from": was, "to": s.subject})

        # 하한선일 뿐이다 — subject 로 찾은 문서는 당연히 이 말을 갖고 있다.
        # 단, **남의 시장을 묻는 슬롯에는 우리 주제어를 요구하지 않는다.** 경쟁사 점유율에
        # '카페' 가 안 나오는 건 정상이다 — 요구하면 멀쩡한 자료를 우리가 버린다.
        mc = (cfg.get("must_contain") or {})
        if mc.get("fill_when_empty") and not s.must_contain and topics \
                and s.claim_type not in (mc.get("skip_claim_types") or []):
            # 주제어 뒤에 **통계표가 쓰는 말**을 이어 붙인다. must_contain 은 any 라 넓히는
            # 방향이고, '카페' 로만 요구하면 KOSIS 의 '커피전문점' 표가 통째로 격리된다
            # (full-03: usable 66건 중 '비알콜' 0건 — 실재하는 통계를 말이 안 맞아 놓쳤다).
            s.must_contain = _with_aliases(topics, cfg)[:mc.get("max_filled", 2)]
            fixes.append({"slot_id": s.slot_id, "what": "must_contain", "to": s.must_contain})

        # 틀린 코드는 있는 것보다 없는 게 낫다 — 어댑터가 검색으로 찾는다.
        if (cfg.get("stat_code") or {}).get("drop_malformed") and \
                stat_code_state(s.stat_code, rules) == "malformed":
            fixes.append({"slot_id": s.slot_id, "what": "stat_code",
                          "from": s.stat_code, "to": None})
            s.stat_code = None

        kept.append(s)
    return kept, discarded, fixes


# ══════════════════════════════════════════════════════════════
# A1 품질 계측 — 지표가 못 잡는 종류라 따로 센다
# ══════════════════════════════════════════════════════════════
def audit_slots(slots: list, formulas: list, rules: dict | None = None) -> dict:
    """A1 이 만든 슬롯을 기계로 훑는다. 눈으로 볼 것과 별개로 숫자를 남긴다."""
    stat_targets = {"TAM", "SAM"}          # 통계표로 얻는 게 자연스러운 목표값
    stat_slots = [s for s in slots if s.claim_type in stat_targets]

    by_state = {"missing": [], "malformed": [], "given": []}
    for s in stat_slots:
        by_state[stat_code_state(s.stat_code, rules)].append(s.slot_id)

    dup_region = [s.slot_id for s in slots if s.region and s.region in s.subject]
    narrow = [s.slot_id for s in slots if len(s.must_contain) >= 3]

    # 두 식이 정말 독립인가 — 같은 변수를 공유하면 삼각측량이 무의미하다
    shared = []
    by_target = {}
    for f in formulas:
        by_target.setdefault(f.target, []).append(f)
    for target, fs in by_target.items():
        td = next((x for x in fs if x.path == "topdown"), None)
        bu = next((x for x in fs if x.path == "bottomup"), None)
        if not td or not bu:
            continue
        key = lambda v: (v.subject.strip(), v.metric.strip())
        overlap = {key(v) for v in td.vars} & {key(v) for v in bu.vars}
        if overlap:
            shared.append({"target": target, "shared_vars": sorted("|".join(x) for x in overlap)})

    return {
        # ① stat_code 3단 — 안 냄 / 형식 틀림 / 형식 맞음.
        #    **안 낸 것**이 라우팅을 web 으로 흘려보내는 직접 원인이다.
        #    형식이 맞아도 실제 존재하는 통계표인지는 별개다 — 그건 호출해 봐야 안다(err 21).
        "stat_slots_total": len(stat_slots),
        "stat_code_missing": by_state["missing"],
        "stat_code_malformed": by_state["malformed"],
        "stat_code_given": by_state["given"],
        "stat_code_missing_ratio": (round(len(by_state["missing"]) / len(stat_slots), 3)
                                    if stat_slots else None),
        "stat_code_usable_ratio": (round(len(by_state["given"]) / len(stat_slots), 3)
                                   if stat_slots else None),
        # ② must_contain 과도 협소 신호
        "must_contain_3plus": narrow,
        "no_guard_slots": [s.slot_id for s in slots if not s.must_contain],
        # ③ 두 식의 독립성
        "shared_vars_between_paths": shared,
        # 기타 눈으로 볼 것들
        "subject_contains_region": dup_region,
        "targets_with_both_paths": sorted(t for t, fs in by_target.items()
                                          if {x.path for x in fs} == {"topdown", "bottomup"}),
        "templates_used": sorted({f.template for f in formulas}),
    }
