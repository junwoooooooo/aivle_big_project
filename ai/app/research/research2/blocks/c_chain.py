# -*- coding: utf-8 -*-
"""블록 C — 논리 사슬과 보고서. **LLM 호출 0회.** (절대규칙 1)

    C1 build_chain        Reconciliation[] + Ledger + 사용자입력 → ChainCell[]
    C2 check_consistency  ChainCell[] + rules/consistency.v1.json → Violation[]
    C3 render_report      전부 → Report (7개 섹션)

세 가지를 지킨다:
  · **출처 없이 나타나는 숫자가 없다.** 모든 칸은 computed / ledger / user_input 중 하나에서 온다.
  · **규칙에는 순서가 있다.** blocker 가 깨지면 그 아래 의존 규칙은 `skipped` — 무의미한 위반을
    줄줄이 내지 않는다. "1번이 깨져서 3번은 검사 안 함"이 훨씬 읽기 쉽다.
  · **§7 '못 찾은 것'을 빼지 않는다.** 격리·미검증·단위불일치·잘림이 전부 여기로 모인다.
"""
from __future__ import annotations

import fillaxis as _fx

from schema import (NOT_FOUND_KEYS, QUARANTINE_LABELS, ChainCell, Coverage, Estimate,
                    Ledger, Reconciliation, Report, Slot, Violation, to_dict)


# ══════════════════════════════════════════════════════════════
# C1 — build_chain : 출처 없는 숫자가 없게 만든다
# ══════════════════════════════════════════════════════════════
def build_chain(recs: list[Reconciliation], ledger: Ledger, user_input: dict,
                rules: dict, slots=None) -> dict[str, ChainCell]:
    cells: dict[str, ChainCell] = {}
    cell_names = rules["consistency"]["chain_cells"]

    # ① 추정에서 온 칸 (adopted 가 있을 때만 — diverged 면 값이 없다)
    for r in recs:
        if r.target in cell_names:
            v = _mid(r.adopted)
            cells[r.target] = ChainCell(
                key=r.target, value=v,
                source="computed" if v is not None else "missing",
                origin=f"B3 {r.status}" + (f" (gap {r.gap_ratio})" if r.gap_ratio else ""))

    # ② 사용자 입력에서 온 칸
    for k, v in (user_input or {}).items():
        if k in cell_names:
            cells[k] = ChainCell(key=k, value=_num(v), source="user_input", origin=f"입력.{k}")

    # ③ 원장에서 직접 온 칸 (가격 밴드 등)
    band = _price_band(ledger, slots, rules)
    if band:
        cells["alt_price_band"] = ChainCell(key="alt_price_band", value=None,
                                            source="ledger", origin=f"ALT/PRICE 슬롯 {band}")

    # ④ 파생 계산 — SOM 이 없고 SAM·점유율이 있으면 계산할 수 있지만, **하지 않는다.**
    #    추정은 B블록의 일이다. 여기서 새 숫자를 만들면 출처가 흐려진다.

    for name in cell_names:
        cells.setdefault(name, ChainCell(key=name, value=None, source="missing",
                                         origin="채워지지 않음"))
    return cells


def _mid(rng):
    """범위의 중앙. **기하평균이다 — 산술평균이 아니다.**

    범위는 `lo=point/f, hi=point*f` 로 **배수**로 만든다(`estimate_band`). 그렇게 만든 밴드의
    중앙은 기하평균이고, 산술평균을 쓰면 **곱셈 항등이 깨진다** — report3-03 에서
    `가격 × 고객수 ≈ 1년차매출`(R3)이 밴드 폭 차이 때문에 52.5% 어긋나 위반이 났다
    (기하평균이면 0.00%). **오차의 형태는 모델을 따라간다**(B블록 원칙).

    0 이나 음수가 섞이면 기하평균이 정의되지 않는다 → 산술로 되돌린다.
    조용히 NaN 이 흐르는 것이 최악이다.
    """
    if not rng:
        return None
    lo, hi = rng[0], rng[1]
    if lo > 0 and hi > 0:
        return (lo * hi) ** 0.5
    return (lo + hi) / 2


def _num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def _price_band(ledger: Ledger, slots, rules: dict) -> list | None:
    """대체재 가격 밴드. **`claim_type` 이 PRICE·COMP 이고 단위가 화폐인 사실만.**

    옛 구현은 `확인됨` 인 **모든** 사실의 min/max 를 잡았다. report1-01 에서 그 결과가
    `[20,264 ~ 106,452]` — 커피전문점 **사업체 수(개)** 였고, R7(가격 비교 가능)이 그
    개수 밴드를 보고 통과했다. **단위를 안 보는 밴드는 밴드가 아니다.**
    화폐 단위 목록은 `guards._currency.units` 를 그대로 쓴다(새 상수를 만들지 않는다).
    """
    money = set((((rules or {}).get("guards") or {}).get("_currency") or {})
                .get("units") or ["원", "KRW"])
    # 채택 기준은 **규칙 파일**이 정한다(규약 ①). 블록이 없으면 옛 동작(확인됨만)으로 둔다 —
    # 규칙이 없다고 조용히 넓히지 않는다.
    cfg = ((rules or {}).get("consistency") or {}).get("price_band") or {}
    ctypes = tuple(cfg.get("claim_types") or ("PRICE", "COMP"))
    accepts = cfg.get("accept") or [{"id": "confirmed", "enabled": True, "labels": ["확인됨"]}]
    need = int(cfg.get("min_facts") or 2)

    def takes(row) -> bool:
        """이 원장 행이 밴드 재료가 되는가. 규칙의 `accept` 갈래 중 하나라도 맞으면 채택."""
        for a in accepts:
            if not a.get("enabled"):
                continue
            if a.get("labels") and row.label not in a["labels"]:
                continue
            if a.get("kinds") and row.kind not in a["kinds"]:
                continue
            if a.get("min_score") is not None and row.score < a["min_score"]:
                continue
            if a.get("require_quote_verified") and ledger.facts[row.fact_id].quote_verified \
                    is not True:
                continue
            return True
        return False

    by_slot = {s.slot_id: s for s in (slots or [])}
    vals = []
    for fid, f in ledger.facts.items():
        sl = by_slot.get(f.slot_id)
        if not sl or sl.claim_type not in ctypes:
            continue
        if f.value_num is None or (cfg.get("require_currency_unit", True)
                                   and f.unit_norm not in money):
            continue
        if any(r.fact_id == fid and takes(r) for r in ledger.rows):
            vals.append(f.value_num)
    return [min(vals), max(vals)] if len(vals) >= need else None


# ══════════════════════════════════════════════════════════════
# C2 — check_consistency : 규칙은 데이터. 코드는 해석기일 뿐이다.
# ══════════════════════════════════════════════════════════════
def check_consistency(cells: dict[str, ChainCell], ledger: Ledger,
                      coverage: list[Coverage], rules: dict, slots=None) -> list[Violation]:
    out: list[Violation] = []
    failed_blockers: set[str] = set()
    skipped_ids: set[str] = set()

    for spec in rules["consistency"]["rules"]:
        rid = spec["id"]

        # ── 의존 규칙 먼저 — 위에서 blocker 가 깨졌으면 검사하지 않는다.
        #    **전이된다**: R2 가 skipped 면 R2 에 의존하는 R3 도 전제가 미확인이다.
        blocked_by = [d for d in spec.get("depends_on", [])
                      if d in failed_blockers or d in skipped_ids]
        if blocked_by:
            out.append(Violation(rule_id=rid, name=spec["name"], severity=spec["severity"],
                                 passed=True, status="skipped", skipped_by=blocked_by[0],
                                 detail=f"{blocked_by[0]} 위반/미검사로 검사하지 않음"))
            skipped_ids.add(rid)
            continue

        passed, detail, used = _apply_check(spec, cells, ledger, coverage, rules, slots)
        if passed is None:            # 재료가 없어 판단 불가
            out.append(Violation(rule_id=rid, name=spec["name"], severity=spec["severity"],
                                 passed=True, status="not_applicable", detail=detail, cells=used))
            continue

        v = Violation(rule_id=rid, name=spec["name"], severity=spec["severity"],
                      passed=bool(passed), status="passed" if passed else "violated",
                      detail=detail, cells=used)
        if not passed:
            if rid in rules["consistency"]["retry_hint"]["emit_for"]:
                v.retry_hint = f"{rid}: {spec['message']} — 사람이 승인하면 1회 재조사"
            if spec["severity"] == "blocker":
                failed_blockers.add(rid)
        out.append(v)
    return out


def _val(cells, key):
    c = cells.get(key)
    return c.value if c else None


def _apply_check(spec, cells, ledger, coverage, rules, slots=None):
    """반환 (passed|None, detail, used_cells). None 은 '재료 없음 = 판단 불가'."""
    ch = spec["check"]
    t = ch["type"]

    if t == "ordered":
        keys = ch["cells"]
        vals = [_val(cells, k) for k in keys]
        if any(v is None for v in vals):
            return None, f"값 없음: {[k for k, v in zip(keys, vals) if v is None]}", keys
        ok = all(vals[i] <= vals[i + 1] for i in range(len(vals) - 1))
        return ok, " ≤ ".join(f"{k}({v:,.0f})" for k, v in zip(keys, vals)), keys

    if t == "lte":
        a, b = _val(cells, ch["left"]), _val(cells, ch["right"])
        if a is None or b is None:
            return None, "값 없음", [ch["left"], ch["right"]]
        return a <= b, f"{ch['left']}({a:,.0f}) ≤ {ch['right']}({b:,.0f})", [ch["left"], ch["right"]]

    if t in ("product_approx", "product_lte"):
        fs = [_val(cells, k) for k in ch["factors"]]
        if any(v is None for v in fs):
            return None, "값 없음", ch["factors"]
        prod = 1.0
        for v in fs:
            prod *= v
        if t == "product_lte":
            r = _val(cells, ch["right"])
            if r is None:
                return None, "값 없음", ch["factors"] + [ch["right"]]
            return prod <= r, f"곱 {prod:,.0f} ≤ {ch['right']}({r:,.0f})", ch["factors"] + [ch["right"]]
        eq = _val(cells, ch["equals"])
        if eq is None or eq == 0:
            return None, "값 없음", ch["factors"] + [ch["equals"]]
        # ── 단위 정렬. **환산은 명시적으로만 하고, 규칙에 없으면 멈춘다.**
        #   report3-02 에서 R3 가 price(원/월) × 고객수 를 revenue_y1(원/연) 과 그냥 비교해
        #   차이 96% 로 '위반' 했다 — 데이터가 아니라 **사슬에 단위 선언이 없어서** 생긴
        #   가짜 위반이었다. 틀린 곱을 만드느니 시끄럽게 멈춘다(B블록과 같은 철학).
        note = ""
        ua = ch.get("unit_align")
        if ua:
            units = rules["consistency"].get("chain_cell_units") or {}
            conv = rules["consistency"].get("unit_conversions") or {}
            u_from = units.get(ua["cell"])
            u_to = units.get(ch["equals"] if ua.get("to") == "equals" else ua.get("to"))
            if u_from and u_to and u_from != u_to:
                k = f"{u_from}→{u_to}"
                if k not in conv:
                    return None, (f"단위 불일치: {ua['cell']}({u_from}) vs "
                                  f"{ch['equals']}({u_to}) — 환산 규칙 `{k}` 이 없어 **판정 불가**"),                         ch["factors"] + [ch["equals"]]
                v0 = _val(cells, ua["cell"])
                prod = prod / v0 * (v0 * conv[k]) if v0 else prod
                note = (f"{ua['cell']} {v0:,.0f}{u_from} → {v0 * conv[k]:,.0f}{u_to} 로 "
                        f"환산 후 비교 · ")
        diff = abs(prod - eq) / abs(eq)
        return diff <= ch["tolerance"], \
            note + f"곱 {prod:,.0f} vs {ch['equals']} {eq:,.0f} (차이 {diff:.1%})", \
            ch["factors"] + [ch["equals"]]

    if t == "ratio_gte":
        a, b = _val(cells, ch["left"]), _val(cells, ch["right"])
        if a is None or b in (None, 0):
            return None, "값 없음", [ch["left"], ch["right"]]
        return (a / b) >= ch["min"], f"{ch['left']}/{ch['right']} = {a / b:.2f}", [ch["left"], ch["right"]]

    if t == "in_band_or_comparable":
        price = _val(cells, ch["value"])
        band_cell = cells.get(ch["band"])
        if price is None or band_cell is None or band_cell.source == "missing":
            # **거짓 통과 금지.** 밴드가 없으면 판정하지 않는다 — 없는 기준으로 통과도
            # 위반도 만들지 않는다. 밴드가 왜 비었는지는 §7 이 말한다.
            return None, ("대체재 가격 밴드가 없다(PRICE·COMP 슬롯의 화폐 단위 확인됨 "
                          "사실이 2건 미만) — 판정 불가"), [ch["value"], ch["band"]]
        return True, f"비교 가능: {band_cell.origin}", [ch["value"], ch["band"]]

    if t == "subject_match":
        cell = cells.get(ch["cell"])
        target = cells.get(ch["against"])
        if not cell or cell.source == "missing" or not target or target.source == "missing":
            return None, "타겟 정의 또는 대상 칸이 비어 있음", [ch["cell"], ch["against"]]
        same = (cell.origin or "").split("|")[0] == (target.origin or "").split("|")[0]
        return same, f"{cell.origin} vs {target.origin}", [ch["cell"], ch["against"]]

    if t == "min_confirmed":
        want_type = ch["claim_type"]
        if slots is None:
            return None, ("슬롯 목록을 못 받아 claim_type 을 확인할 수 없다 — "
                          "**판정 불가**(fail-closed)"), []
        ids = _slots_of(slots, want_type)
        if not ids:
            return None, (f"{want_type} 슬롯이 조사 설계에 **하나도 없다** — 판정 불가. "
                          f"이 축을 재려면 슬롯을 먼저 만들어야 한다"), []
        n = sum(1 for c in coverage if c.status == "충족" and c.slot_id in ids)
        return n >= ch["min"], f"{want_type} 슬롯 {sorted(ids)} 중 충족 {n}개", sorted(ids)

    if t == "all_cells_sourced":
        bad = [k for k, c in cells.items() if c.value is not None and c.source == "missing"]
        return not bad, f"출처 없는 값: {bad}" if bad else "모든 값에 출처 있음", bad

    if t == "no_duplicate_metric":
        by_key: dict[str, set] = {}
        # **가격은 모순이 아니라 밴드다** (판 ㉜). 대체재 가격이 여러 값인 것은 정상이고
        # 이 저장소도 그렇게 다룬다 — R7 의 이름이 「우리 가격이 **대체재 밴드**와 비교
        # 가능」이다. 이 축까지 「두 값이면 모순」으로 보면 **가격 칸을 제대로 채울수록
        # blocker 가 뜬다.** ⚠ 원래 새던 자리다: pin-09(성적표 6/6)의 실행도 이 규칙을
        # 위반했는데 **성적표가 체인 위반을 안 보여줘서** 아무도 못 봤다.
        # TAM·SAM 은 그대로 둔다 — 시장 규모가 38조이면서 11조일 수는 없다.
        밴드_ct = set(ch.get("밴드_claim_type") or [])
        밴드_슬롯 = {s.slot_id for s in (slots or []) if s.claim_type in 밴드_ct}
        밴드_본것: dict[str, set] = {}
        # ⚠ `next(..., None)` 로 조용히 넘어가지 않는다 — 원장에 행이 없는 사실이 있으면
        #   그건 조립이 깨진 것이므로 **사유로 남긴다**(#16, 표시만·판정 로직 변경 없음).
        orphan = []
        for fid, f in ledger.facts.items():
            row = next((r for r in ledger.rows if r.fact_id == fid), None)
            if row is None:
                orphan.append(fid)
                continue
            if _fx.filled(row, "c_chain._apply_check") and f.value_num is not None:
                # 밴드 축은 **판정에서 빼되 기록에는 남긴다** — 조용한 통과 금지.
                where = 밴드_본것 if f.slot_id in 밴드_슬롯 else by_key
                where.setdefault(f.match_key, set()).add(round(f.value_num, 4))
        dup = {k: sorted(v) for k, v in by_key.items() if len(v) > 1 and max(v) > 1.1 * min(v)}
        band = {k: sorted(v) for k, v in 밴드_본것.items() if len(v) > 1}
        note = (f" · ⚠ 원장 행이 없는 사실(누락) {orphan}" if orphan else "")
        note += (f" · 밴드(모순 아님) {band}" if band else "")
        return not dup, (f"같은 지표 두 값: {dup}" if dup else "중복 없음") + note, []

    if t == "legal_resolved":
        pend = [c.slot_id for c in coverage if c.status != "충족" and c.slot_id.startswith("L")]
        return not pend, f"미해소 법률 슬롯: {pend}" if pend else "해당 없음", []

    return None, f"알 수 없는 검사 유형: {t}", []


def _slots_of(slots, claim_type: str) -> set:
    """그 claim_type 인 슬롯 id. **못 맞추면 빈 집합이다 — 전부로 되돌리지 않는다.**

    옛 구현은 `slot_id.startswith(claim_type[:1])` 로 골랐다. `"COMP"[:1] == "C"` 인데
    slot_id 는 `S1`…`S9` 라 **아무것도 안 맞았고**, 뒤의 `or` 가 전 슬롯으로 되돌려
    R9(경쟁사 ≥1곳 실명, blocker)가 **TAM·SAM 슬롯을 세고 통과**했다(report1-01 실측).
    DART `accounts` 의 fail-open 과 같은 병이다 — **빈 매칭은 전부 통과가 아니라 판정 불가.**
    """
    return {s.slot_id for s in (slots or []) if s.claim_type == claim_type}


# ══════════════════════════════════════════════════════════════
# C3 — render_report : §7 을 빼지 않는다
# ══════════════════════════════════════════════════════════════
def render_report(cells: dict[str, ChainCell], violations: list[Violation],
                  estimates: list[Estimate], recs: list[Reconciliation],
                  ledger: Ledger, coverage: list[Coverage], slots: list[Slot],
                  adapters: dict, coverage_caveat: str | None, rules: dict,
                  unknown_codes: list | None = None,
                  url_filtered: list | None = None,
                  extract_capped: list | None = None,
                  fetch_empty: list | None = None) -> Report:
    blockers = [v for v in violations if v.status == "violated" and v.severity == "blocker"]
    warns = [v for v in violations if v.status == "violated" and v.severity == "warn"]
    skipped = [v for v in violations if v.status == "skipped"]

    # 1. 결론 3문장
    filled = sum(1 for c in coverage if c.status == "충족")
    conclusion = [
        f"핵심 차단 사유 {len(blockers)}건, 경고 {len(warns)}건. "
        f"({len(skipped)}건은 상위 위반 때문에 검사하지 않음)",
        f"슬롯 {len(coverage)}개 중 {filled}개 충족, "
        f"{sum(1 for c in coverage if c.thin)}개는 표본 부족(thin), "
        f"{sum(1 for c in coverage if c.status == '공백')}개 공백.",
        ("채택 가능한 값이 없다 — 두 경로가 갈렸다." if all(r.adopted is None for r in recs)
         else "아래 핵심 숫자는 두 경로가 겹친 구간만 채택했다."),
    ]
    if coverage_caveat:
        conclusion.append(coverage_caveat)

    # 2. 핵심 숫자 3개 + 신뢰도 배지
    headline = []
    for r in recs:
        est = next((e for e in estimates if e.target == r.target and e.status == "ok"), None)
        # 추정이 하나도 'ok' 가 아니면 **왜 없는지**를 적는다 — 조용한 None 금지(#16).
        est_missing = None if est else (
            "누락(target=" + str(r.target) + "): "
            + (", ".join(sorted({e.status for e in estimates if e.target == r.target}))
               or "해당 target 의 추정 자체가 없다"))
        capped = any(e.range_capped for e in estimates if e.target == r.target)
        headline.append({
            "target": r.target,
            "value": None if capped else r.adopted,
            "est_missing": est_missing,
            "badge": ("추정 불가 — 가정 과다(범위 상한 도달)" if capped
                      else (est.badge if est else "추정 불가")),
            "status": r.status,
            "why_no_value": ("두 경로 격차 — 채택값 없음" if r.status == "diverged"
                             else ("가정 과다" if capped else None)),
        })

    # 3. 어떻게 계산했나
    how = [{"formula_id": e.formula_id, "target": e.target, "path": e.path,
            "assumption_count": e.assumption_count, "status": e.status,
            "inputs": [to_dict(i) for i in e.inputs]} for e in estimates]

    # 4. 틀릴 수 있는 지점
    falsifiers = [e.falsified_if for e in estimates if e.falsified_if]

    # 5. 두 경로 대조
    recon = [to_dict(r) for r in recs]

    # 6. 근거 원장 (격리 포함 — 숨기지 않는다). scope 꼬리표는 LedgerRow 에 실려 온다.
    led = [to_dict(r) for r in ledger.rows]

    # 값의 범위가 다른 근거는 **결론 위쪽에서** 한 번 더 말한다. 원장 깊숙이만 있으면
    # 핵심 숫자만 읽는 사람에게는 없는 것과 같다 — 전사 매출을 시장규모로 오해한다.
    scoped = [r for r in ledger.rows if r.scope and r.label not in QUARANTINE_LABELS]
    for note in sorted({r.scope_note for r in scoped if r.scope_note}):
        conclusion.append(f"⚠ {note}")

    # 7. 못 찾은 것 — NOT_FOUND_KEYS 전부. 값이 비어도 키는 남긴다.
    off = [r for r in ledger.rows if r.label == "off_slot"]
    unver = [r for r in ledger.rows if r.label == "미검증"]
    not_found = {
        "empty_slots": [c.slot_id for c in coverage if c.status == "공백"],
        "thin_slots": [{"slot_id": c.slot_id, "confirmed": c.confirmed, "min_facts": c.min_facts}
                       for c in coverage if c.thin],
        "unfilled_vars": [i["var_id"] for e in how for i in e["inputs"]
                          if not i.get("from_fact")],
        "suspect_var": [r.suspect_var for r in recs if r.suspect_var],
        "off_slot": {"count": len(off),
                     "by_reason": _count_by(off, lambda r: (r.off_slot_reason or "").split(":")[0]),
                     "unverified_quote": len(unver)},
        "adapters": {k: v for k, v in (adapters or {}).items()},
        "retry_hints": ([c.retry_hint for c in coverage if c.retry_hint] +
                        [v.retry_hint for v in violations if v.retry_hint]),
        # B블록이 시끄럽게 멈춘 자리 — 조용히 사라지면 안 된다
        "unit_mismatch": [{"formula_id": e.formula_id, "note": e.unit_note}
                          for e in estimates if e.status == "unit_mismatch"],
        "range_capped": [e.formula_id for e in estimates if e.range_capped],
        "skipped_checks": [{"rule_id": v.rule_id, "skipped_by": v.skipped_by} for v in skipped],
        # 분류 못 한 외부 코드 — 규칙 파일에 추가할 대상 목록
        "unknown_error_codes": list(unknown_codes or []),
        # 같은 대상·단위인데 값이 갈린 것. **가점을 죽인 채 조용히 넘어가면 안 된다** —
        # "출처마다 값이 다르다"는 확인 실패가 아니라 그 자체가 조사 결과다(B3 의 diverged).
        "contradictions": [{"slot_id": r.slot_id, "fact_id": r.fact_id,
                            "url": r.url, "note": r.conflict}
                           for r in ledger.rows if getattr(r, "conflict", "")],
        # A3 에서 **열지도 않고** 거른 후보. 무엇을 안 봤는지 밝히지 않으면
        # 커버리지가 낮을 때 "못 찾은 것"과 "우리가 안 연 것"을 구분할 수 없다 (규칙 5).
        "url_filtered": list(url_filtered or []),
        # 물어봤는데 없는 것이 아니라 **묻지도 않은 것**이다. 섞으면 §7 이 거짓이 된다.
        "extract_capped": list(extract_capped or []),
        "fetch_empty": list(fetch_empty or []),
        # **'더 찾아라' 가 아니라 '찾아도 없다'.** retry_hints 와 성격이 달라 칸을 나눈다.
        # 문구는 규칙 파일에서 온다(절대규칙 7).
        # 판 ⑯ ② — **값으로 싣는다.** 규칙 파일이 목록을 주면 그대로, 없으면 빈 칸.
        # ⚠ 빈 칸이어도 **키는 남긴다**(「미측정」과 「0건」을 가르는 자리 — 판 ⑪ ① 계보).
        "자료_부재_확정": list(
            ((rules.get("assumptions") or {}).get("자료_부재_확정") or {}).get(
                (ledger and getattr(ledger, "run_id", "")) or "", [])
            or ((rules.get("assumptions") or {}).get("자료_부재_확정") or {}).get("_공용", [])),
        "independent_topdown_blocked": list(
            ((rules.get("consistency") or {}).get("report_notes") or {})
            .get("independent_topdown_blocked") or []),
    }
    for k in NOT_FOUND_KEYS:              # 하나라도 빠지면 침묵이 생긴다
        not_found.setdefault(k, [])

    return Report(conclusion=conclusion, headline_numbers=headline, how_computed=how,
                  falsifiers=falsifiers, reconciliations=recon, ledger=led, not_found=not_found)


def _count_by(rows, keyfn) -> dict:
    out: dict = {}
    for r in rows:
        k = keyfn(r) or "(사유 없음)"
        out[k] = out.get(k, 0) + 1
    return dict(sorted(out.items(), key=lambda kv: -kv[1]))


# ══════════════════════════════════════════════════════════════
# 블록 전체
# ══════════════════════════════════════════════════════════════
def run_block_c(recs, ledger, coverage, slots, estimates, user_input, rules,
                adapters=None, coverage_caveat=None, run=None, unknown_codes=None,
                url_filtered=None, extract_capped=None, fetch_empty=None):
    cells = build_chain(recs, ledger, user_input, rules, slots)
    violations = check_consistency(cells, ledger, coverage, rules, slots)
    report = render_report(cells, violations, estimates, recs, ledger, coverage, slots,
                           adapters or {}, coverage_caveat, rules, unknown_codes,
                           url_filtered, extract_capped, fetch_empty)
    if run is not None:
        run.log_many("c1_chain", list(cells.values()))
        run.log_many("c2_violations", violations)
        run.log("c3_report", to_dict(report))
    return cells, violations, report
