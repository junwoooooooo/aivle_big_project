# -*- coding: utf-8 -*-
"""블록 B — 이중 경로 추정과 대조. **LLM 호출 0회.** (절대규칙 1)

    B1 substitute  Ledger + Formula  → EstimateInput[]   어느 변수를 무엇으로 채웠나
    B2 estimate    EstimateInput[]   → Estimate          범위 + 민감도 + 반증조건
    B3 reconcile   Estimate[]        → Reconciliation    두 경로 대조

세 가지를 지킨다:
  · `as_of` 를 인자로 받는다. 오늘 날짜를 읽지 않는다 (수용기준 3).
  · **thin 은 `assumption_count` 로 흡수한다.** 표시만 하고 계산에 반영 안 하면
    2점짜리가 SOM 계산에 들어간다.
  · `diverged` 면 `adopted` 를 비운다. 두 경로가 3배 차이 난다는 사실 자체가 보고서 내용이다.
"""
from __future__ import annotations

import re

import fillaxis as _fx

from schema import (Coverage, Estimate, EstimateInput, Formula, Ledger,
                    Reconciliation, Slot)

# 식 템플릿 → 계산 방식. 자유 서술을 허용하지 않으므로 여기서 전부 다룬다.
TEMPLATE_OPS = {
    "T1": "mul",   # 상위시장규모 × 세그먼트비중
    "T2": "mul",   # 사업체수 × 침투율 × 단가
    "T3": "div",   # 상위N사매출합 ÷ 추정점유율
    "T4": "mul",   # 거래건수 × 건당금액
    "T5": "pick",  # 직접조회 — 첫 변수 값을 그대로
    "T7": "mul",   # 시장거래액 × 추정점유율 (계열 C)
}
# ⚠ 이 dict 자체가 **코드 상수**다 — 규약 ① 의 소급 적용 대상이고 백로그 33 에 있다.
#   `_apply` 가 모르는 템플릿을 "mul" 로 처리하는 것도 fail-open 이라 같이 봐야 한다.
#   T7 을 여기 **명시**한 이유가 그것이다 — 우연히 맞는 것과 맞게 적은 것은 다르다.


# ══════════════════════════════════════════════════════════════
# as_of — 전 블록 공유. period 해석에 오늘 날짜가 끼어들지 않게 한다.
# ══════════════════════════════════════════════════════════════
_YEAR = re.compile(r"(?:19|20)\d{2}")


def resolve_period(period: str, as_of_year: int) -> tuple:
    """'2023' → (2023, 2023) · '최근 3년' → (as_of-2, as_of) · 해석 불가 → (None, None)

    **오늘 날짜가 아니라 as_of 를 쓴다.** 같은 원장이면 언제 돌려도 같은 구간이 나온다.
    """
    p = (period or "").strip()
    m = _YEAR.search(p)
    if m:
        y = int(m.group(0))
        return y, y
    m2 = re.search(r"최근\s*(\d+)\s*년", p)
    if m2:
        n = int(m2.group(1))
        return as_of_year - n + 1, as_of_year
    return None, None


# ══════════════════════════════════════════════════════════════
# B1 — substitute : 원장에서 값을 꺼내고, 없으면 가정으로 채우되 **표시한다**
# ══════════════════════════════════════════════════════════════
def substitute(formula: Formula, ledger: Ledger, coverage: dict[str, Coverage],
               slots: dict[str, Slot], assumptions: dict, rules: dict) -> list[EstimateInput]:
    """var_id → EstimateInput.

    coverage 는 slot_id → Coverage. **'보강필요'·'공백' 슬롯의 값은 쓰지 않는다**
    (rules.scoring.coverage.thin_effect_in_B).
    """
    policy = rules["scoring"]["coverage"]["thin_effect_in_B"]
    # ── 변수 → 슬롯 조인은 **계층**이다. 완화가 아니다.
    #   ① formula_id 가 같은 슬롯에서 var_id 로 찾는다 (좁고 안전)
    #   ② 없으면 **전체 슬롯에서 var_id 로** 찾는다 (슬롯 하나가 식 하나에만 붙는 제약을 푼다)
    #   ③ 둘 다 실패하면 가정으로 가고, 가정도 없으면 시끄럽게 멈춘다
    #   **어느 키로 붙었는지 basis 에 남긴다** — 조용한 완화는 조용한 제약만큼 위험하다.
    #   var_id 오타가 엉뚱한 슬롯에 붙어도 아무도 모르면 그것이 다음 조인 버그가 된다.
    #   버그 A~D 의 근본 원인이 **과잉 제약 + 조용한 실패의 조합**이었다:
    #   A `_slots_of` 가 못 맞추면 전부 통과(fail-open) · B `_price_band` 가 유형·단위를 안 봄 ·
    #   C overlay 키 `(claim_type, metric)` 이 유일하지 않음 · D `formula_id` 가 덮이지 않음.
    scoped = {s.var_id: s for s in slots.values() if s.formula_id == formula.formula_id}
    anyslot = {s.var_id: s for s in slots.values()}
    out: list[EstimateInput] = []

    for v in formula.vars:
        slot = scoped.get(v.var_id)
        join = "formula_id+var_id" if slot else ""
        if not slot:
            slot = anyslot.get(v.var_id)
            join = "var_id (식 밖 슬롯)" if slot else "없음"
        cov = coverage.get(slot.slot_id) if slot else None
        usable = bool(cov) and policy[_policy_key(cov)]["compute"]

        best = None
        if usable and slot:
            rows = [r for r in ledger.by_slot(slot.slot_id)
                    if _fx.filled(r, "b_estimate.substitute")]
            rows.sort(key=lambda r: (-r.score, -r.cross, r.fact_id))
            for r in rows:
                fact = ledger.facts.get(r.fact_id)
                if fact and fact.value_num is not None:
                    best = (r, fact)
                    break

        if best:
            r, fact = best
            # ★ 단위 검사만 한다. **변환하지 않는다.** (규칙이 확정되기 전에 굳히면
            #    '10%' 와 '0.10' 이 조용히 100배 어긋난 채 흘러간다)
            mismatch = _unit_mismatch(slot.unit if slot else v.unit, fact.unit_norm, rules)
            out.append(EstimateInput(
                var_id=v.var_id, from_fact=r.fact_id, confirmed=True,
                basis=(f"unit_mismatch: {mismatch} / " if mismatch else "")
                      + f"슬롯 {slot.slot_id} · 조인 {join}"))
        else:
            # ⚠ `var_role` 은 자유 문자열이라 이름이 어긋나면 조용히 가정을 못 찾는다.
            #   허용 목록을 규칙에 선언하고 **미등재는 계산 불가로 멈춘다**(fail-closed) —
            #   "이름 문제"와 "근거 부재"를 구분해 사유에 남긴다.
            vr_cfg = ((rules.get("assumptions") or {}).get("var_roles") or {})
            allowed = set(vr_cfg.get("allowed") or [])
            if allowed and v.var_role not in allowed:
                out.append(EstimateInput(
                    var_id=v.var_id, from_fact=None, confirmed=False,
                    basis=f"미등재 var_role '{v.var_role}' — 계산 불가 "
                          f"(rules.assumptions.var_roles.allowed 에 없다)"))
                continue
            a = assumptions.get(v.var_role) or assumptions.get(v.var_id)
            if a is None:
                out.append(EstimateInput(var_id=v.var_id, from_fact=None, confirmed=False,
                                         basis="가정값 없음 — 계산 불가"))
            else:
                mismatch = _unit_mismatch(slot.unit if slot else v.unit, a.get("unit"), rules)
                out.append(EstimateInput(
                    var_id=v.var_id, assumption=float(a["value"]), confirmed=False,
                    basis=(f"unit_mismatch: {mismatch} / " if mismatch else "") + a["basis"]))
    return out


def _unit_mismatch(slot_unit: str, value_unit: str | None, rules: dict) -> str | None:
    """슬롯 단위와 값 단위가 다르면 사유 문자열, 같으면 None.

    **변환하지 않는다.** 조용한 실패보다 시끄러운 정지가 낫다.
    단위 변환 규칙은 A1 이 슬롯 단위를 확정한 뒤에 넣는다.
    """
    # ⚠ 옛 조기 반환(「어느 한쪽이 비면 판단하지 않는다」)을 제거했다 — 그것이 F 의 잔재였다.
    #   슬롯 단위가 비면 공용 함수가 True(검사 안 함)를 주고, **값 단위가 비면 비호환**이다.
    #   값 단위를 모르면 맞는지도 모르는 것이다(fail-closed).
    # **표는 `a_desk.units_compatible` 하나만 읽는다** — 두 소비자가 같은 함수를 부른다.
    # 따로 읽던 시절 정규화 범위와 기본값이 달라 판정이 갈렸다(조인 계보 F).
    from a_desk import units_compatible
    if units_compatible(slot_unit, value_unit, rules["units"]):
        return None
    return f"슬롯 '{slot_unit}' vs 값 '{value_unit}'"


def _policy_key(cov: Coverage) -> str:
    if cov.status != "충족":
        return cov.status
    return "충족_thin_true" if cov.thin else "충족_thin_false"


# ══════════════════════════════════════════════════════════════
# B2 — estimate : 값 대입 → 범위 · 민감도 · 반증조건
# ══════════════════════════════════════════════════════════════
def estimate(formula: Formula, inputs: list[EstimateInput], ledger: Ledger,
             coverage: dict[str, Coverage], slots: dict[str, Slot],
             assumptions: dict, rules: dict) -> Estimate:
    policy = rules["scoring"]["coverage"]["thin_effect_in_B"]
    var_to_slot = {s.var_id: s for s in slots.values() if s.formula_id == formula.formula_id}

    values: list[float] = []
    assumption_count = 0
    thin_notes: list[str] = []

    # ★ 시끄러운 정지 — 단위가 어긋난 입력이 하나라도 있으면 계산하지 않는다
    mism = [i for i in inputs if i.basis.startswith("unit_mismatch")]
    if mism:
        return Estimate(formula_id=formula.formula_id, target=formula.target,
                        path=formula.path, value=None, inputs=inputs,
                        assumption_count=0, status="unit_mismatch",
                        unit_note="; ".join(f"{i.var_id}: {i.basis}" for i in mism))

    for i in inputs:
        if i.from_fact:
            fact = ledger.facts[i.from_fact]
            values.append(float(fact.value_num))
            # ★ thin 흡수 — 표본이 얇으면 사실이어도 가정 하나로 센다
            slot = var_to_slot.get(i.var_id)
            cov = coverage.get(slot.slot_id) if slot else None
            if cov:
                delta = policy[_policy_key(cov)]["assumption_delta"] or 0
                if delta:
                    assumption_count += delta
                    thin_notes.append(
                        f"{i.var_id}: 표본 {cov.confirmed}건 < 기준 {cov.min_facts}건")
        elif i.assumption is not None:
            values.append(float(i.assumption))
            assumption_count += 1
        else:
            return Estimate(formula_id=formula.formula_id, target=formula.target,
                            path=formula.path, value=None, inputs=inputs,
                            assumption_count=assumption_count, status="insufficient",
                            falsified_if="", sensitivity=[])

    point = _apply(TEMPLATE_OPS.get(formula.template, "mul"), values)
    if point is None:
        return Estimate(formula_id=formula.formula_id, target=formula.target,
                        path=formula.path, value=None, inputs=inputs,
                        assumption_count=assumption_count, status="insufficient")

    # 범위 — 가정이 많을수록 넓어진다. 폭은 규칙 파일에서 온다(코드 상수 금지).
    # **가감이 아니라 배수다.** point*(1±0.5n) 으로 하면 가정 2개에서 하한이 0,
    # 3개에서 음수가 된다. 시장규모에 0이나 음수 하한은 값이 아니라 버그다.
    band = rules["scoring"]["estimate_band"]
    raw_factor = (1 + band["per_assumption"]) ** assumption_count
    factor = min(raw_factor, band["max_factor"])
    capped = raw_factor > band["max_factor"]      # 잘림도 정보다 — C블록이 이걸 보고 '추정 불가'
    lo, hi = point / factor, point * factor

    sens = _sensitivity(formula, inputs, values, assumptions, rules)
    falsified = _falsified_if(formula, inputs, assumptions)

    est = Estimate(formula_id=formula.formula_id, target=formula.target, path=formula.path,
                   value=[round(lo, 4), round(hi, 4)], inputs=inputs,
                   assumption_count=assumption_count, sensitivity=sens,
                   falsified_if=falsified, status="ok", range_capped=capped)
    if thin_notes:
        est.falsified_if = (falsified + " / " if falsified else "") + "; ".join(thin_notes)
    return est


def _apply(op: str, vals: list[float]) -> float | None:
    if not vals:
        return None
    if op == "pick":
        return vals[0]
    if op == "div":
        if len(vals) < 2 or vals[1] == 0:
            return None
        return vals[0] / vals[1]
    out = 1.0
    for v in vals:
        out *= v
    return out


def _sensitivity(formula, inputs, values, assumptions, rules) -> list[dict]:
    """가정으로 채운 변수를 범위로 흔들어 본다. 어느 변수가 결과를 지배하는지 본다."""
    out = []
    op = TEMPLATE_OPS.get(formula.template, "mul")
    for idx, i in enumerate(inputs):
        if i.assumption is None:
            continue
        a = assumptions.get(_role_of(formula, i.var_id)) or assumptions.get(i.var_id) or {}
        rng = a.get("range")
        if not rng:
            continue
        lo_vals = list(values); lo_vals[idx] = float(rng[0])
        hi_vals = list(values); hi_vals[idx] = float(rng[1])
        lo, hi = _apply(op, lo_vals), _apply(op, hi_vals)
        if lo is None or hi is None:
            continue
        out.append({"var_id": i.var_id, "range": [rng[0], rng[1]],
                    "result": [round(min(lo, hi), 4), round(max(lo, hi), 4)]})
    return out


def _role_of(formula: Formula, var_id: str) -> str:
    for v in formula.vars:
        if v.var_id == var_id:
            return v.var_role
    return var_id


def _falsified_if(formula: Formula, inputs, assumptions) -> str:
    parts = []
    for i in inputs:
        if i.assumption is None:
            continue
        role = _role_of(formula, i.var_id)
        a = assumptions.get(role) or assumptions.get(i.var_id) or {}
        if a.get("falsified_if"):
            parts.append(a["falsified_if"])
        else:
            parts.append(f"{role} 가정({i.assumption:g})이 틀리면 성립 안 함")
    return " / ".join(parts)


# ══════════════════════════════════════════════════════════════
# B3 — reconcile : 두 경로 대조. **갈리면 고르지 않는다.**
# ══════════════════════════════════════════════════════════════
def reconcile(target: str, topdown: Estimate | None, bottomup: Estimate | None,
              rules: dict) -> Reconciliation:
    td = topdown.value if topdown and topdown.status == "ok" else None
    bu = bottomup.value if bottomup and bottomup.status == "ok" else None

    if not td and not bu:
        # 둘 다 없으면 값이 없다. 이건 정말로 계산 불가다.
        return Reconciliation(target=target, topdown=td, bottomup=bu, overlap=None,
                              gap_ratio=None, status="insufficient",
                              suspect_var=_suspect(topdown, bottomup), adopted=None)
    if not td or not bu:
        # ── 경로가 하나뿐 — **채택하되 꼬리표를 단다.**
        #   버리면 사슬이 통째로 비어 R1~R3 가 영영 안 켜지고, 조용히 채택하면 삼각측량이
        #   사라진다. 문구는 `consistency.report_notes.single_path` 에 있다(절대규칙 7).
        one = td or bu
        return Reconciliation(target=target, topdown=td, bottomup=bu, overlap=None,
                              gap_ratio=None, status="single_path",
                              suspect_var=_suspect(topdown, bottomup), adopted=one)

    lo = max(td[0], bu[0])
    hi = min(td[1], bu[1])
    overlap = [lo, hi] if lo <= hi else None

    mid_td = (td[0] + td[1]) / 2
    mid_bu = (bu[0] + bu[1]) / 2
    gap = (max(mid_td, mid_bu) / min(mid_td, mid_bu)) if min(mid_td, mid_bu) > 0 else None

    cfg = rules["scoring"].get("reconcile", {"diverged_gap_ratio": 3.0})
    if overlap and abs(hi - lo) >= 0 and gap is not None and gap <= 1.5:
        status, adopted = "converged", [round(lo, 4), round(hi, 4)]
    elif overlap:
        status, adopted = "partial_overlap", [round(lo, 4), round(hi, 4)]
    else:
        status, adopted = "diverged", None      # ← 그럴듯한 쪽을 고르지 않는다

    if gap is not None and gap >= cfg["diverged_gap_ratio"]:
        status, adopted = "diverged", None

    return Reconciliation(target=target, topdown=td, bottomup=bu, overlap=overlap,
                          gap_ratio=round(gap, 3) if gap else None, status=status,
                          suspect_var=_suspect(topdown, bottomup), adopted=adopted)


def _suspect(*ests) -> str | None:
    """가정으로 채운 변수 중 민감도 폭이 가장 큰 것 — 재조사 힌트."""
    best, best_width = None, -1.0
    for e in ests:
        if not e:
            continue
        for s in e.sensitivity or []:
            r = s.get("result") or [0, 0]
            width = abs(r[1] - r[0])
            if width > best_width:
                best, best_width = s["var_id"], width
    return best


# ══════════════════════════════════════════════════════════════
# 블록 전체
# ══════════════════════════════════════════════════════════════
def run_block_b(formulas: list[Formula], ledger: Ledger, coverage: list[Coverage],
                slots: list[Slot], assumptions: dict, rules: dict,
                as_of_year: int, run=None) -> tuple:
    cmap = {c.slot_id: c for c in coverage}
    smap = {s.slot_id: s for s in slots}

    estimates: list[Estimate] = []
    for f in formulas:
        inputs = substitute(f, ledger, cmap, smap, assumptions, rules)
        estimates.append(estimate(f, inputs, ledger, cmap, smap, assumptions, rules))

    recs: list[Reconciliation] = []
    for target in sorted({f.target for f in formulas}):
        td = next((e for e in estimates if e.target == target and e.path == "topdown"), None)
        bu = next((e for e in estimates if e.target == target and e.path == "bottomup"), None)
        recs.append(reconcile(target, td, bu, rules))

    if run is not None:
        run.log_many("b2_estimate", estimates)
        run.log_many("b3_reconcile", recs)
    return estimates, recs
