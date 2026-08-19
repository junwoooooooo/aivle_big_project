# -*- coding: utf-8 -*-
"""슬롯 하네스 기계 검증 게이트 — LLM 출력을 **코드가** 검사한다. LLM 0회.

fail-closed: 하나라도 실패하면 스냅샷을 쓰지 않고 사람에게 보고한다.
검사 항목별 결과는 전부 값으로 남는다(실패는 값이다).

유리벽: `blocks/` import 0. 라우팅 판정은 엔진 코드를 부르지 않고
`rules/adapters.v1.json` 을 **읽어서** 같은 규칙을 적용한다 — 규칙이 정본이기 때문이다.
"""
from __future__ import annotations

import json
import os
import re
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

_STAT_CODE = re.compile(r"^\d{3}/[A-Za-z0-9_]+$")     # orgId 3자리 / tblId (a_design.py 와 동일)

FORBIDDEN_LLM_FIELDS = frozenset({
    "kind", "tier", "score", "conf", "confidence", "verdict", "label", "role", "relation",
})
# `var_role` 은 금지어가 아니다 — schema.FormulaVar 가 'role' 과 이름을 일부러 분리했다.
_FORBIDDEN_EXEMPT = frozenset({"var_role"})


def _load(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


# ══════════════════════════════════════════════════════════════
# 라우팅 — blocks/a_desk.py:305 route_sources 와 같은 순서. 규칙에서 읽는다.
# ══════════════════════════════════════════════════════════════
def route_of(slot: dict, adapters: dict) -> tuple[str, str]:
    if slot.get("stat_code"):
        return "kosis", "stat_code"
    if slot.get("corp_name"):
        return "dart", "corp_name"
    if slot.get("claim_type") in ("LEGAL", "PRICE"):
        return "web", f"{slot.get('claim_type')} 전용 경로"
    rm = ((adapters.get("kosis") or {}).get("route_metrics") or {})
    if rm.get("enabled"):
        metric = slot.get("metric") or ""
        hit = next((m for m in rm.get("match", []) if m in metric), None)
        if hit and any(x in metric for x in rm.get("exclude", [])):
            hit = None
        if hit:
            return "kosis", f"route_metric={hit}"
    return "web", "기본 경로"


# ══════════════════════════════════════════════════════════════
# 검사 항목
# ══════════════════════════════════════════════════════════════
def check_vocab(slots: list, vocab: dict) -> dict:
    """G1 — metric·unit·claim_type 이 통제 어휘 안인가. 자유 서술은 여기서 죽는다.

    단위는 계량마다 **하나로 고정**이다. 1차 초안에서 도입률이 `%` 와 `비율` 로 섞여
    나왔고, 그러면 같은 계량의 값끼리도 비교가 깨진다.
    """
    cat = vocab["metric"]["catalog"]
    allowed_ct = set(vocab["claim_type"]["enum"])
    bad = []
    for s in slots:
        met = s.get("metric")
        spec = cat.get(met)
        if spec is None:
            bad.append({"slot_id": s.get("slot_id"), "field": "metric", "value": met,
                        "why": "통제 어휘 밖"})
        elif s.get("unit") != spec["unit"]:
            bad.append({"slot_id": s.get("slot_id"), "field": "unit", "value": s.get("unit"),
                        "why": f"「{met}」의 단위는 {spec['unit']} 로 고정"})
        if s.get("claim_type") not in allowed_ct:
            bad.append({"slot_id": s.get("slot_id"), "field": "claim_type",
                        "value": s.get("claim_type"), "why": "통제 어휘 밖"})
    return {"name": "통제 어휘", "passed": not bad, "violations": bad,
            "checked": len(slots)}


def check_role_kind(formulas: list, vocab: dict, metric_cat: dict) -> dict:
    """G8 — `var_role` 의 자리와 계량 종류(kind)가 맞는가. **개수 자리에 비율 금지.**

    1차 초안의 `F_SAM` 이 T2(사업체수 × 침투율 × 단가)의 '사업체수' 자리에
    「종사자 1인 사업체 비율」을 넣었다. 게이트 9항목을 전부 통과했는데 SAM 은
    무의미한 수였다 — 자릿수가 아니라 **종류**가 틀린 값은 크기 필터로 안 걸린다.
    """
    roles = vocab["var_role"]["catalog"]
    bad = []
    for f in formulas:
        for v in f.get("vars", []):
            role, met = v.get("var_role"), v.get("metric")
            spec = roles.get(role)
            if spec is None:
                bad.append({"formula_id": f.get("formula_id"), "var_id": v.get("var_id"),
                            "var_role": role, "why": "var_role 통제 어휘 밖"})
                continue
            want = spec["kind"]
            got = (metric_cat.get(met) or {}).get("kind")
            if want != "any" and got and want != got:
                bad.append({"formula_id": f.get("formula_id"), "var_id": v.get("var_id"),
                            "var_role": role, "metric": met,
                            "why": f"{role} 자리는 {want} 인데 「{met}」은 {got}"})
            # 1:1 결속 — 종류가 맞아도 **뜻이 다른** 계량이 들어오는 자리를 막는다.
            # 「침투율」 자리에 「종사자 1인 사업체 비율」이 들어가 TAM/SAM 이
            # «1인 미용실 전체가 100% 구독» 을 계산했다(2026-08-08 실측).
            allow = spec.get("metrics")
            if allow and met not in allow:
                bad.append({"formula_id": f.get("formula_id"), "var_id": v.get("var_id"),
                            "var_role": role, "metric": met, "허용": allow,
                            "why": f"{role} 자리는 지정된 계량만 쓴다"})
    return {"name": "var_role↔계량 종류", "passed": not bad, "violations": bad}


def check_template_roles(formulas: list, vocab: dict, assumptions: dict | None = None) -> dict:
    """G9 — 템플릿이 요구하는 자리가 다 있는가.

    T2 에 `연환산` 이 없으면 월 매출을 연 매출로 읽는다(12배 축소). 1차 초안이 그랬고
    게이트는 통과했다 — 「빠진 변수」는 있는 값을 검사해서는 절대 안 보인다.

    ★ 판 ㊴ — **가정값이 «있는지»까지 본다.** 예전에는 `vocab.var_role._가정_역할`
    이라는 **이름 목록**만 봤다. 그 목록은 `rules/assumptions.v1.json` 의 사본이라
    규칙 파일에서 값을 지워도 게이트는 그대로 통과했고, 통과한 설계는 B 블록에서
    「가정값 없음 — 계산 불가」로 조용히 멈췄다(`blocks/b_estimate.py:127`).
    **문턱은 규칙 파일이다**(절대규칙 7) — 이름 사본이 아니라 값을 읽는다.
    """
    if assumptions is None:                       # 규칙 파일이 정본이다(절대 규칙 7)
        assumptions = _load(os.path.join(ROOT, "rules", "assumptions.v1.json"))
    req = vocab["template"]["required_roles"]
    assume_ok = set(vocab["var_role"]["_가정_역할"])
    # 값이 실제로 **있는** 역할만 「가정으로 채울 수 있는 자리」다.
    by_role = (assumptions.get("by_role") or {})
    have_value = {r for r, a in by_role.items()
                  if isinstance(a, dict) and a.get("value") is not None}
    # **초과 자리 검사** (판 ⑫ ①′). 목록이 있는 템플릿만 정확 일치를 요구한다 —
    # 모르는 템플릿(T1·T4·T5)에 걸면 멀쩡한 식을 죽인다. 값은 규칙 파일에.
    allow_cfg = vocab["template"].get("허용_자리") or {}
    allow_map = (allow_cfg.get("map") or {}) if allow_cfg.get("enabled") else {}
    bad = []
    for f in formulas:
        have = {v.get("var_role") for v in f.get("vars", [])}
        missing = [r for r in req.get(f.get("template"), []) if r not in have]
        if missing:
            bad.append({"formula_id": f.get("formula_id"), "template": f.get("template"),
                        "빠진_자리": missing})
        # ⚠ **빠진 것만 보면 합집합이 통과한다.** 판 ⑫ 실측: 모델이 T2 를 T7 으로
        #   교체하지 않고 **덧붙여** 자리가 7개가 됐는데 검사를 통과했다. 그리고 통과하면
        #   `b_estimate._apply("mul")` 가 변수를 **전부 곱해** 무의미한 TAM 을 낸다.
        allow = allow_map.get(f.get("template"))
        if allow:
            extra = sorted(r for r in have if r and r not in set(allow))
            if extra:
                bad.append({"formula_id": f.get("formula_id"), "template": f.get("template"),
                            "초과_자리": extra, "허용": list(allow),
                            "why": (allow_cfg.get("_위반_문구") or "").format(
                                template=f.get("template"), 허용=list(allow), 초과=extra)})
        for v in f.get("vars", []):
            if v.get("_observable") is not False:
                continue
            role = v.get("var_role")
            if role in have_value:
                continue
            # **두 사유를 가른다.** 이름이 어휘 밖인 것과 값이 없는 것은 고칠 자리가 다르다
            # — 앞은 식을 고치고 뒤는 관측을 붙이거나 규칙에 근거 있는 값을 넣는 일이다.
            why = ("관측 안 하는데 assumptions.by_role 에 없는 역할 — B 가 값을 못 채운다"
                   if role not in assume_ok else
                   f"관측 안 하는데 가정값이 없다 — rules/assumptions.v1.json::by_role.{role} "
                   f"에 value 가 없다(판 ㊴ 에서 남의 사업 숫자를 지웠다). "
                   f"이 자리는 **관측으로 채우거나 식에서 빼야** 한다 — B 가 값을 못 채운다")
            bad.append({"formula_id": f.get("formula_id"), "var_id": v.get("var_id"),
                        "var_role": role, "why": why})
    return {"name": "템플릿 필수 자리", "passed": not bad, "violations": bad}


def check_price_cell(slots: list, vocab: dict) -> dict:
    """G10 — 가격 계량은 수익원 칸 · claim_type PRICE.

    1차 초안이 「월 구독료」를 고객 세그먼트 칸에 TAM 으로 달았다. 칸-claim_type 정합은
    통과한다 — 짝이 맞았으니까. 맞는 짝을 **엉뚱한 칸에** 붙인 것이라 이 검사가 따로 필요하다.
    """
    price = set(vocab["metric"]["_가격_계량"])
    bad = [{"slot_id": s.get("slot_id"), "metric": s.get("metric"),
            "canvas_cell": s.get("_canvas_cell"), "claim_type": s.get("claim_type"),
            "why": "가격 계량은 수익원 칸 · PRICE 여야 한다"}
           for s in slots if s.get("metric") in price
           and (s.get("_canvas_cell") != "수익원" or s.get("claim_type") != "PRICE")]
    return {"name": "가격 계량의 칸", "passed": not bad, "violations": bad}


def check_period(slots: list, slotcheck: dict, as_of_year: int) -> dict:
    """G11 — period 가 `rules/slotcheck.v1.json` 의 기간 규칙과 맞는가.

    국가통계는 늦게 나온다(lagged_metrics → as_of-2), 나머지는 as_of-1. 1차 초안은
    사업체 수만 맞고 나머지가 전부 2023 이었다 — 검색이 통째로 그 해에 묶인다.
    """
    p = slotcheck.get("period") or {}
    lagged = set(p.get("lagged_metrics") or [])
    # **연 계열 예외** (판 ⑳ 도장) — `F_GROWTH` 슬롯만 여러 해를 허용한다.
    # 성장률은 **두 해를 이어야** 구간이 생기고, 한 해로 고정하면 **구조적으로 계산 불가**다
    # (판 ⑲ 실측 · 판 ④ 미용실은 사람이 손으로 우회했다 — 백로그 53).
    # ⚠ **좁게 연다**: 이 예외는 `formula_ids` 에 든 식의 슬롯에만 걸리고 나머지는 그대로다.
    gs = p.get("성장률_연계열") or {}
    g_fids = set(gs.get("formula_ids") or []) if gs.get("enabled") else set()
    g_ok = {str(as_of_year - o) for o in (gs.get("허용_오프셋") or [])}
    g_seen: dict = {}
    bad, rows = [], []
    for s in slots:
        met = s.get("metric") or ""
        if s.get("formula_id") in g_fids:
            g_seen.setdefault(s.get("formula_id"), set()).add(str(s.get("period")))
            rows.append({"slot_id": s.get("slot_id"), "metric": met,
                         "period": s.get("period"), "기대": f"연 계열 {sorted(g_ok)} 중"})
            if str(s.get("period")) not in g_ok:
                bad.append({"slot_id": s.get("slot_id"), "metric": met,
                            "period": s.get("period"), "기대": f"연 계열 {sorted(g_ok)} 중"})
            continue
        off = p.get("lagged_offset", 2) if any(m in met for m in lagged) \
            else p.get("default_offset", 1)
        want = str(as_of_year - off)
        rows.append({"slot_id": s.get("slot_id"), "metric": met,
                     "period": s.get("period"), "기대": want})
        if str(s.get("period")) != want:
            bad.append({"slot_id": s.get("slot_id"), "metric": met,
                        "period": s.get("period"), "기대": want})
    # **연 계열은 개수도 본다** — 한 해만 있으면 성장률이 안 선다. 그것을 여기서 말한다.
    for fid, yrs in (g_seen.items() if gs.get("최소_연도_수_강제") else ()):
        need = int(gs.get("최소_연도_수") or 2)
        if len(yrs) < need:
            bad.append({"formula_id": fid, "연도": sorted(yrs),
                        "why": f"연 계열이 {len(yrs)}개 — {need}개 이상이어야 구간이 선다"})
    return {"name": "period 기간 규칙", "passed": not bad, "violations": bad, "rows": rows}


def check_corp_exists(slots: list, corpcode: dict) -> dict:
    """G12 — `corp_name` 이 DART 공시 법인 사전에 있는가.

    ⚠ **이 검사가 보는 것은 「실재」이지 「적합」이 아니다.** 1차 초안에서 실제로
    뒤집힌 결과가 나왔다 — 엉뚱한 경쟁사(왓챠, OTT)는 사전에 **있어서 통과**하고,
    타당한 경쟁사(스포카)는 **비상장이라 사전에 없어서** 걸렸다. 그래서 이 검사의
    뜻은 "이 회사가 맞는 경쟁사다"가 아니라 **"이 이름으로는 공시를 못 딴다"** 이다.
    비상장 경쟁사는 corp_name 을 비우고 web 계량(가입 매장 수 등)으로 관측해야 한다.
    업종 적합성은 기계가 판정하지 않는다 — 사람 확인 칸으로 따로 남긴다.
    """
    bad, rows = [], []
    for s in slots:
        name = (s.get("corp_name") or "").strip()
        if not name:
            continue
        exists = name in corpcode
        rows.append({"slot_id": s.get("slot_id"), "corp_name": name,
                     "state": "공시법인" if exists else "사전에 없음",
                     "업종_적합성": "사람 확인 (기계 미판정)"})
        if not exists:
            bad.append({"slot_id": s.get("slot_id"), "corp_name": name,
                        "why": "DART 공시 법인이 아니다 — corp_name 을 비우고 web 계량으로 관측하라"})
    return {"name": "corp_name 공시 실재", "passed": not bad, "violations": bad, "rows": rows}


def check_routing(slots: list, adapters: dict) -> dict:
    """G2 — 모든 슬롯이 어디로 갈지 결정되는가. **web 기본 경로로 흘러가는 것을 센다.**

    라우팅은 구조상 항상 답이 나온다(else → web). 그래서 이 검사는 '갈 곳이 있는가'가
    아니라 **'의도한 곳으로 가는가'** 를 본다: kosis/dart 어휘를 쓴 슬롯이 실제로 그
    경로를 타는지 대조하고, 결과를 슬롯별로 기록한다.
    """
    rows, mismatch = [], []
    for s in slots:
        adapter, why = route_of(s, adapters)
        want = s.get("_기대_경로")
        rows.append({"slot_id": s.get("slot_id"), "metric": s.get("metric"),
                     "adapter": adapter, "why": why, "기대": want})
        if want and want != adapter:
            mismatch.append({"slot_id": s.get("slot_id"), "기대": want, "실제": adapter,
                             "why": why})
    return {"name": "metric 라우팅", "passed": not mismatch, "routes": rows,
            "violations": mismatch}


def check_formula_join(slots: list, formulas: list) -> dict:
    """G3 — `formula_id` 실재 + `var_id` 가 그 식의 변수인가.

    B 블록 조인은 `formula_id + var_id` 다(blocks/b_estimate.py:74). 둘 중 하나가
    어긋나면 **조용히** 가정으로 떨어지거나 엉뚱한 슬롯에 붙는다. 실측: 기존 카페
    스냅샷의 S7~S9 가 존재하지 않는 `F_COMP` 를 물고 있었고 아무도 몰랐다.
    """
    by_id = {f.get("formula_id"): f for f in formulas}
    bad = []
    for s in slots:
        fid, vid = s.get("formula_id"), s.get("var_id")
        f = by_id.get(fid)
        if f is None:
            bad.append({"slot_id": s.get("slot_id"), "formula_id": fid,
                        "why": "식이 존재하지 않음"})
            continue
        if vid not in {v.get("var_id") for v in f.get("vars", [])}:
            bad.append({"slot_id": s.get("slot_id"), "formula_id": fid, "var_id": vid,
                        "why": "그 식의 변수 목록에 없음 — B 조인이 식 밖 슬롯으로 떨어진다"})
    return {"name": "식 조인(formula_id·var_id)", "passed": not bad, "violations": bad,
            "식_개수": len(formulas)}


def check_coverage(slots: list, vocab: dict) -> dict:
    """G4 — 캔버스 커버리지. 2026-08-08 승인된 갈래:

    · 측정·판정 칸 4개 → 담당 슬롯 ≥ 1
    · 계획 칸 5개      → 슬롯 불필요. 원천 명시 기록
    · 어느 칸에도 안 붙는 슬롯 0
    """
    measured = vocab["canvas"]["측정판정"]["cells"]
    planned = vocab["canvas"]["계획"]["cells"]
    provisional = vocab.get("잠정", {})

    cells, missing = {}, []
    for cell in measured:
        owned = [s.get("slot_id") for s in slots if s.get("_canvas_cell") == cell]
        status = "충족" if owned else "미충족"
        if owned and cell in provisional:
            status = "잠정 충족"
        cells[cell] = {"성격": "측정·판정", "슬롯": owned, "상태": status}
        if cell in provisional:
            cells[cell]["_잠정"] = provisional[cell]["사유"]
        if not owned:
            missing.append(cell)

    for cell, origin in planned.items():
        cells[cell] = {"성격": "계획", "상태": "슬롯_불필요", "원천": origin}

    orphan = [s.get("slot_id") for s in slots
              if s.get("_canvas_cell") not in measured]
    return {"name": "캔버스 커버리지", "passed": not missing and not orphan,
            "cells": cells, "미충족_칸": missing, "고아_슬롯": orphan}


def check_cell_fit(slots: list, vocab: dict, concept: dict | None = None) -> dict:
    """G4b — `claim_type` 과 `canvas_cell` 이 서로 맞는가.

    커버리지 검사(G4)는 "칸이 채워졌는가"만 본다. 그래서 경쟁사 관측을 채널 칸에,
    성장률을 가치 제안 칸에 붙여도 **칸 수만 맞으면 통과**한다 — 실측(초안 2회차)에서
    실제로 그렇게 나왔다. 칸이 채워진 것과 **맞는 것이 채워진 것**은 다르다.
    """
    measured = vocab["canvas"]["측정판정"]["cells"]
    bad = []
    for s in slots:
        cell = s.get("_canvas_cell")
        spec = measured.get(cell)
        if not spec:
            continue
        # **식 전용 예외** (판 ⑳ 도장). **칸 전반을 여는 것이 아니라** 그 식에 속한
        # 슬롯에만 걸린다 — `F_GROWTH` 의 `GROWTH` 가 「고객 세그먼트」에 실리는 자리다.
        # 다른 claim_type 은 여전히 막힌다.
        allow = list(spec["claim_types"]) + list(
            (spec.get("claim_types_by_formula") or {}).get(s.get("formula_id"), []))
        if s.get("claim_type") not in allow:
            bad.append({"slot_id": s.get("slot_id"), "_canvas_cell": cell,
                        "claim_type": s.get("claim_type"),
                        "허용": allow, "why": "칸과 claim_type 불일치"})
    # ── 식 → claim_type **강제** (판 ㉖) ──────────────────────────────
    # 위 검사는 「칸」 기준이라 `F_GROWTH` 슬롯이 `TAM` 이어도 통과한다. 그런데 판정 층
    # `judge_growth` 는 **claim_type 으로** 성장률 축을 세므로, 그 어긋남이 있으면
    # **확인됨이 원장에 있는데도 성장률이 0건**이 된다(`ledger-02` 실측).
    # ⚠ 프롬프트 5-1 이 **이미** 같은 말을 하고 있다 — 완화가 아니라 **정합**이다.
    _f = (vocab.get("식_목록") or {}).get("claim_type_강제") or {}
    # ⚠ **E 는 백로그 46 이 먼저다**(도장 조건 ③). 해외 라우팅이 없는 상태에서 강제만 켜면
    #   맞춰야 할 축 자체가 틀린 채로 초안이 헛돈다.
    _series = ((concept or {}).get("_계열") or {}).get("계열")
    if _f.get("enabled") and _series not in (_f.get("제외_계열") or []):
        for s in slots:
            want = (_f.get("map") or {}).get(s.get("formula_id"))
            if want and s.get("claim_type") != want:
                bad.append({"slot_id": s.get("slot_id"),
                            "formula_id": s.get("formula_id"),
                            "claim_type": s.get("claim_type"), "허용": [want],
                            "why": (_f.get("사유_문구") or "").format(
                                fid=s.get("formula_id"), want=want, got=s.get("claim_type"))})
    return {"name": "칸-claim_type 정합", "passed": not bad, "violations": bad}


def check_dart_corp_metric(slots: list, vocab: dict) -> dict:
    """G4c — dart 어휘(매출액·영업이익)를 쓰면서 `corp_name` 이 없으면 web 으로 새고,
    업종 전체 매출을 회사 매출인 양 물어오게 된다. 「전사 매출 ≠ 시장 매출」의 그 사고다.
    """
    dart_metrics = {m for m, v in vocab["metric"]["catalog"].items() if v["route"] == "dart"}
    bad = [{"slot_id": s.get("slot_id"), "metric": s.get("metric"),
            "why": "dart 계량인데 corp_name 없음 — web 으로 샌다"}
           for s in slots if s.get("metric") in dart_metrics and not s.get("corp_name")]
    return {"name": "dart 계량의 corp_name", "passed": not bad, "violations": bad}


_PLACEHOLDER = [
    (re.compile(r"(^|[\s(])[A-Za-z](사|社)(\s|$|\))"), "A사 꼴"),
    (re.compile(r"^[A-Za-z][가-힣]"), "A미용 꼴 — 영문 한 글자 + 한글"),
    (re.compile(r"[○◯●□X]{2,}|\bOO\b|\bXX\b|ㅇㅇ"), "○○·OO·XX 꼴"),
    (re.compile(r"예시|가칭|샘플|placeholder|익명|무명"), "예시·가칭 표기"),
    (re.compile(r"^(우리|자사|본사)"), "우리 서비스 — 관측 대상이 아니다"),
]


def check_placeholder(slots: list) -> dict:
    """G13 — `subject` 가 자리표시자인가.

    2026-08-08 실측: corp_name 검사에 걸린 모델이 재시도에서 실명을 포기하고
    「A미용 예약 SaaS·B미용 예약 SaaS·C미용 예약 SaaS」로 바꿔 넣었다. subject 는
    자유 텍스트라 통제 어휘로는 못 막는다. **지어낸 이름보다 자리표시자가 더 나쁘다** —
    검색이 아무것도 못 찾고 그 사실이 슬롯 설계 탓인지 자료 부재 탓인지 구분되지 않는다.
    경쟁사 실명은 LLM 이 지을 것이 아니라 **사람 씨앗 + 엔진 발굴**에서 온다.
    """
    bad = []
    for s in slots:
        subj = (s.get("subject") or "").strip()
        for rx, why in _PLACEHOLDER:
            if rx.search(subj):
                bad.append({"slot_id": s.get("slot_id"), "subject": subj, "why": why})
                break
    return {"name": "자리표시자 subject", "passed": not bad, "violations": bad}


def check_fixed_metrics(slots: list, vocab: dict) -> dict:
    """G14 — 식별로 못박은 계량만 쓰는가 (F_DIFF·F_CHANNEL).

    차별점·채널은 「무엇으로 재는가」가 열려 있으면 모델이 계량을 발명한다.
    수치 대리지표로 한정하고, 기능 유무 축은 애초에 슬롯을 만들지 않는다.
    """
    fixed = {k: v for k, v in (vocab["metric"].get("_식별_계량") or {}).items()
             if not k.startswith("_")}
    bad = [{"slot_id": s.get("slot_id"), "formula_id": s.get("formula_id"),
            "metric": s.get("metric"), "허용": fixed[s["formula_id"]],
            "why": "이 식은 지정된 계량만 쓴다"}
           for s in slots
           if s.get("formula_id") in fixed and s.get("metric") not in fixed[s["formula_id"]]]
    return {"name": "식별 지정 계량", "passed": not bad, "violations": bad}


def check_value_range(slots: list, vocab: dict) -> dict:
    """G15 — `value_range` 의 상한이 하한보다 큰가.

    `[0, 0]` 은 값을 거르는 것이 아니라 **모든 값을 격리**한다. 무료 서비스를 그렇게
    적어 대체재 밴드가 구조적으로 안 만들어졌다(2026-08-08). 무료는 `[0, 소액 상한]`.
    """
    if not ((vocab.get("요구") or {}).get("value_range_최소폭") or {}).get("상한_초과_하한"):
        return {"name": "value_range 폭", "passed": True, "violations": [], "_비활성": True}
    bad = []
    for s in slots:
        vr = s.get("value_range")
        if not vr or len(vr) != 2:
            bad.append({"slot_id": s.get("slot_id"), "value_range": vr, "why": "[최소, 최대] 두 값이 아니다"})
        elif not vr[1] > vr[0]:
            bad.append({"slot_id": s.get("slot_id"), "value_range": vr,
                        "why": "상한이 하한보다 커야 한다 — 같으면 모든 값이 off_slot 이 된다"})
    return {"name": "value_range 폭", "passed": not bad, "violations": bad}


def check_reverse_corp(slots: list, vocab: dict) -> dict:
    """G16 — web 계량인데 `corp_name` 이 붙었는가 (역방향).

    `route_sources` 는 `corp_name` 이 있으면 **무조건 dart** 로 보낸다
    (blocks/a_desk.py:311). 「월 구독료」에 corp_name 을 달면 공시에 없는 계정을
    찾으러 가서 그대로 빈손이 된다 — 실측 1건. 앞선 검사는 반대 방향만 봤다.
    """
    cat = vocab["metric"]["catalog"]
    bad = [{"slot_id": s.get("slot_id"), "metric": s.get("metric"),
            "corp_name": s.get("corp_name"),
            "why": "web 계량에 corp_name 이 붙으면 dart 로 라우팅돼 빈손이 된다"}
           for s in slots
           if s.get("corp_name") and (cat.get(s.get("metric")) or {}).get("route") != "dart"]
    return {"name": "역방향 corp_name", "passed": not bad, "violations": bad}


def _has_seeds(concept: dict | None) -> bool:
    return bool(((concept or {}).get("_경쟁_씨앗") or {}).get("seeds"))


def check_dart_probe(slots: list, vocab: dict, concept: dict | None = None) -> dict:
    """G17 — dart 경로를 실제로 한 번 태우는 슬롯이 있는가 (결정 3).

    없으면 이번 판에서도 dart 가 한 번도 안 불려 「검증 대기」 꼬리표를 그대로
    끌고 간다. 대신 그 슬롯은 **경계 표시를 달고 있어야 한다** — 공시 매출은
    전사 매출이지 시장 매출이 아니다.
    """
    req = (vocab.get("요구") or {}).get("dart_검증_슬롯") or {}
    need = req.get("필요")
    # **조건부 요구** (백로그 39 수리). 씨앗이 없으면 정당한 corp_name 이 존재할 수 없으므로
    # 요구 자체를 끈다 — 채울 수 없는 칸을 강제하면 모델은 이름을 지어낸다(3/3 실측).
    if need == "씨앗_있을_때만":
        need = _has_seeds(concept)
    if not need:
        return {"name": "DART 경로 검증 슬롯", "passed": True, "violations": [],
                "_비활성": True,
                "note": ("씨앗 미제공 — corp_name 요구 해제. 경쟁 실명은 수집 결과에서 "
                         "나오면 잡고, 없으면 「경쟁 실명 미확보 — 씨앗 미제공」으로 표시한다")
                if not _has_seeds(concept) else "요구 없음"}
    probes = [s for s in slots if s.get("metric") == req.get("metric") and s.get("corp_name")]
    if not probes:
        return {"name": "DART 경로 검증 슬롯", "passed": False,
                "violations": [{"why": f"「{req.get('metric')}」 + corp_name 슬롯이 없다"}]}
    missing = [{"slot_id": s.get("slot_id"), "why": "경계 표시 없음"}
               for s in probes if not s.get("_경계")]
    return {"name": "DART 경로 검증 슬롯", "passed": not missing,
            "violations": missing,
            "rows": [{"slot_id": s.get("slot_id"), "corp_name": s.get("corp_name")}
                     for s in probes]}


def check_slot_keys(slots: list, vocab: dict) -> dict:
    """G18 — 슬롯 키가 엔진 `Slot` 계약 안인가.

    `run.py:243` 은 `_` 로 시작하지 않는 키를 **전부** `Slot(**s)` 에 넘긴다. 계약 밖
    이름이 하나라도 있으면 **A1 이전에 TypeError 로 죽는다** — 게이트를 19개 통과하고
    유료 실행에서 죽었다(2026-08-08, `canvas_cell`). 하네스의 부가 정보는 `_` 로 시작해야 한다.
    """
    ok = set((vocab.get("slot_schema") or {}).get("fields") or [])
    bad = [{"slot_id": s.get("slot_id"), "key": k,
            "why": "Slot 계약 밖 — `_` 를 앞에 붙이거나 빼라"}
           for s in slots for k in s if not k.startswith("_") and k not in ok]
    return {"name": "슬롯 키 계약", "passed": not bad, "violations": bad}


def check_extract_hints(slots: list, vocab: dict, concept: dict | None) -> dict:
    """G21 — `_추출_힌트` 가 있고 **컨셉에서 왔는가** (P2 배선 공사, 판 ⑥-0).

    판 ⑤ 실측: 통제 어휘에 미용실 낱말(`노쇼 피해 경험률`)이 박혀 있어 **다른 업종의 PAIN 은
    표현조차 못 했다.** 계량 이름을 업종 중립으로 바꾼 대신, 업종 표현은 이 힌트가 나른다.

    **핵심은 「있는가」가 아니라 「컨셉에서 왔는가」다.** 힌트를 자유롭게 열어 두면 모델이
    컨셉에 없는 업종 지식을 흘리고, 그러면 상수를 코드에서 빼서 **LLM 기억으로 옮긴 것**
    뿐이다. 그래서 최소 1개는 컨셉 본문에 **그대로 나오는 말**이어야 한다.

    전부를 요구하지는 않는다 — 정당한 동의어(「노쇼」↔「예약 부도」)까지 죽는다.
    """
    req = ((vocab.get("요구") or {}).get("추출_힌트") or {})
    need_types = set(req.get("필수_claim_type") or [])
    if not need_types:
        return {"name": "추출 힌트(컨셉 유래)", "passed": True, "violations": [], "_비활성": True}
    lo = int(req.get("최소_개수") or 0)
    ground_n = int(req.get("컨셉_유래_최소") or 0)
    text = ""
    if concept:
        text = " ".join(str(concept.get(k) or "")
                        for k in ("name", "problem", "target", "solution"))
    # 대조 방식은 규칙 파일이 정한다(규약 ①). 없으면 옛 동작(정확 부분문자열) — 규칙이
    # 없다고 조용히 느슨해지지 않는다.
    cmp_cfg = req.get("컨셉_유래_대조") or {}

    def _grounded(h: str) -> bool:
        """이 힌트가 **컨셉에서 왔는가.**

        옛 방식은 정확 부분문자열이었다. 한국어는 조사·어미가 붙어 어절이 그대로
        재등장하는 일이 드물어서, 그 방식은 사실상 「컨셉 문장을 통째로 베껴라」였다 —
        판 ⑧ 에서 `"성분 확인"` 이 「**성분을 일일이 확인**하지만」과 뜻이 같은데 탈락했다.

        어절 방식은 쪼개서 보되 **전부** 있어야 한다. 하나라도 없으면 유래가 아니다 —
        느슨하게 열면 이 검사의 목적(**상수를 LLM 기억으로 옮긴 것 잡기**)이 사라진다.
        """
        h = str(h).strip()
        if not h:
            return False
        if cmp_cfg.get("방식") != "어절":
            return h in text
        min_len = int(cmp_cfg.get("최소_어절_길이") or 2)
        # 1글자 어절은 **뺀다** — 아무 데나 맞아 검사를 무력화한다.
        toks = [t for t in h.split() if len(t) >= min_len]
        if not toks:
            # 어절이 전부 1글자면 유래를 판정할 수 없다 → 통과시키지 않는다.
            return False
        hit = [t for t in toks if t in text]
        return len(hit) == len(toks) if cmp_cfg.get("전부_일치", True) else bool(hit)

    bad, rows = [], []
    for s in slots:
        if s.get("claim_type") not in need_types:
            continue
        hints = [h for h in (s.get("_추출_힌트") or []) if str(h).strip()]
        grounded = [h for h in hints if _grounded(h)]
        rows.append({"slot_id": s.get("slot_id"), "힌트": hints, "컨셉_유래": grounded})
        if len(hints) < lo:
            bad.append({"slot_id": s.get("slot_id"), "힌트": hints,
                        "why": f"`추출_힌트` 가 {lo}개 미만 — 업종 표현을 나를 것이 없다"})
        elif not text:
            bad.append({"slot_id": s.get("slot_id"),
                        "why": "컨셉 텍스트를 못 받았다 — 유래를 확인할 수 없으므로 통과시키지 않는다"})
        elif len(grounded) < ground_n:
            bad.append({"slot_id": s.get("slot_id"), "힌트": hints,
                        "why": f"컨셉 본문에 그대로 나오는 힌트가 {ground_n}개 미만 — "
                               f"컨셉에 없는 업종 지식은 상수를 LLM 기억으로 옮긴 것뿐이다"})
    return {"name": "추출 힌트(컨셉 유래)", "passed": not bad, "violations": bad, "rows": rows}


def check_unit_subject(slots: list, series_rule: dict, concept: dict | None) -> dict:
    """G22 — **고객 단위 정합** (판 ⑥-1). 계열이 정한 고객 단위와 TAM·SAM 슬롯이
    실제로 세는 대상이 맞는가.

    ⚠ **무조건 탈락이 아니다.** 폴백 사다리는 「정확 구간이 없으면 인접 구간 + 경계」를
    정당한 길로 정해 두었다. 그러니 가르는 것은 «다른 것을 셌는가» 가 아니라
    **«다른 것을 세면서 그렇다고 말했는가»** 다 — 말했으면 통과, 안 했으면 탈락.

    판 ⑥ 실측: 계열 B 의 TAM 이 「소프트웨어 개발 및 공급업 사업체 수」를, E 가
    「화장품 제조업 사업체 수」를 셌고 **KOSIS 가 완전일치로 받아 줬다.** 형식이 하나도
    안 틀려서 게이트 21항목 중 볼 수 있는 검사가 0개였다.
    """
    name = "고객 단위 정합"
    series = ((concept or {}).get("_계열") or {}).get("계열")
    off = series_rule.get("미표기_계열") or {}
    if not series or series not in (series_rule.get("계열_고객_단위") or {}):
        # fail-open 이 아니다 — 「검사 안 함」을 **값으로 남긴다**(실패는 값이다).
        return {"name": name, "passed": True, "violations": [], "_비활성": True,
                "note": off.get("기록", "계열 미표기"), "계열": series}
    want = set((series_rule["계열_고객_단위"][series].get("허용") or []))
    cls_map = {k: v for k, v in (series_rule.get("unit_subject_class") or {}).items()
               if not k.startswith("_")}
    targets = set((series_rule.get("검사_대상") or {}).get("claim_type") or [])
    lo = int((series_rule.get("proxy_선언") or {}).get("최소_사유_길이") or 0)
    bad, rows = [], []
    for s in slots:
        if s.get("claim_type") not in targets:
            continue
        cls = cls_map.get(s.get("metric"))
        if cls is None:                      # 고객 단위와 무관한 계량(비율·단가 등)
            continue
        decl = s.get("_proxy_선언") or {}
        why, tgt = str(decl.get("사유") or "").strip(), str(decl.get("대상") or "").strip()
        declared = bool(tgt) and len(why) >= lo
        # ⚠ **proxy 는 고객 단위를 바꾸는 열쇠가 아니다** (판 ㉔). 같은 고객 단위 안에서
        # 인접 구간을 대신할 때 쓰는 것이고, 「개인을 세야 하는데 사업체를 세고 사유를
        # 적었다」는 사다리가 아니라 **계열을 갈아치운 것**이다. 계열 D 는 `want` 가
        # **빈 목록**(정의상 proxy)이라 이 제한을 받지 않는다 — 비어 있음이 곧 「항상 선언」.
        _lock = (series_rule.get("proxy_선언") or {}).get("고객_단위_대체_금지") or {}
        단위_잠금 = bool(_lock.get("enabled")) and bool(want)
        ok = cls in want or (declared and not 단위_잠금)
        row = {"slot_id": s.get("slot_id"), "metric": s.get("metric"),
               "센_것": cls, "계열": series, "허용": sorted(want),
               "proxy_선언": ({"대상": tgt, "사유": why} if declared else None), "통과": ok}
        if declared and cls not in want:
            # **경계 표시는 코드가 붙인다.** 모델이 적기를 기다리면 빠지는 판이 생기고,
            # 경계는 빠지면 안 되는 종류의 문장이다.
            row["경계"] = ((series_rule.get("proxy_선언") or {}).get("경계_문구") or "").format(
                센_것=cls, 고객_단위="·".join(sorted(want)) or "신시장(기존 시장 없음)", 사유=why)
            s["_경계_proxy"] = row["경계"]
        rows.append(row)
        if not ok:
            bad.append({"slot_id": s.get("slot_id"), "subject": s.get("subject"),
                        "metric": s.get("metric"), "센_것": cls,
                        "계열": series, "고객_단위": sorted(want) or ["(신시장 — 항상 선언)"],
                        "why": ((_lock.get("사유_문구") or "").format(
                            계열=series, 고객_단위="·".join(sorted(want)), 센_것=cls)
                            if (declared and 단위_잠금) else
                            f"계열 {series} 의 고객 단위가 아닌 것을 세는데 proxy 선언이 없다 "
                            f"— 조용한 오염이거나, 사다리라면 사유를 적어야 한다")})
    return {"name": name, "passed": not bad, "violations": bad, "rows": rows, "계열": series}


def check_subject_aliases(slots: list, vocab: dict) -> dict:
    """G23 — 표기 변종이 **같은 대상의 다른 이름**인가 (판 ㉛).

    별칭은 `must_contain` 을 통과시키는 다리다. 다리에 **다른 대상**을 얹으면 엉뚱한
    문서가 슬롯을 채운다 — 「완화가 아니라 다리」라는 판 ⑰ 의 전제가 그 자리에서 깨진다.
    코드는 「같은 대상인가」를 못 판단하므로(의미 판정은 코드가 못 한다 — 판 ⑭ 실측)
    **셀 수 있는 것만** 잰다: 개수 상한 · 빈 문자열 · subject 와 동일 · 자기들끼리 중복 ·
    금지 낱말(경쟁·유사·기타 같은 «다른 대상» 표지).
    """
    cfg = (vocab.get("subject_aliases") or {})
    if not cfg.get("enabled", True):
        return {"name": "표기 변종", "passed": True, "violations": []}
    cap = int(cfg.get("최대_개수") or 4)
    금지 = cfg.get("금지_낱말") or []
    bad = []
    for s_ in slots:
        al = s_.get("subject_aliases") or []
        subj = (s_.get("subject") or "").strip()
        sid = s_.get("slot_id")
        if len(al) > cap:
            bad.append({"slot_id": sid, "why": f"표기 변종 {len(al)}개 — 상한 {cap}"})
        seen = set()
        for a in al:
            a = str(a).strip()
            if not a:
                bad.append({"slot_id": sid, "why": "빈 표기 변종"})
            elif a == subj:
                bad.append({"slot_id": sid, "alias": a, "why": "subject 와 같다 — 다리가 아니다"})
            elif a in seen:
                bad.append({"slot_id": sid, "alias": a, "why": "중복"})
            elif any(w in a for w in 금지):
                bad.append({"slot_id": sid, "alias": a,
                            "why": "다른 대상 표지가 들어 있다 — 별칭이 아니라 다른 대상이다"})
            seen.add(a)
    return {"name": "표기 변종", "passed": not bad, "violations": bad}


def check_forbidden_fields(raw: dict) -> dict:
    """G5 — 절대 규칙 2. LLM 출력에 등급 칸이 있으면 그 자리에서 탈락."""
    found = []

    def walk(o, path=""):
        if isinstance(o, dict):
            for k, v in o.items():
                if k in FORBIDDEN_LLM_FIELDS and k not in _FORBIDDEN_EXEMPT:
                    found.append(f"{path}.{k}")
                walk(v, f"{path}.{k}")
        elif isinstance(o, list):
            for i, v in enumerate(o):
                walk(v, f"{path}[{i}]")

    walk(raw)
    return {"name": "금지 필드(절대 규칙 2)", "passed": not found, "violations": found}


def check_hypothesis_leak(slots: list, formulas: list, hypotheses: dict) -> dict:
    """G6 — 절대 규칙 6. 가설 값이 슬롯·식(=수집으로 나가는 것)에 새지 않았는가.

    가격 숫자가 슬롯에 박히면 그 값 근처 문서만 물어오고 그것으로 그 값을 '검증'하게 된다.
    """
    needles = []
    h = hypotheses or {}
    price = (h.get("6_수익_가격") or {}).get("제안값_krw_월")
    if price is not None:
        needles += [str(price), format(price, ",")]
    som = (h.get("9_SOM_초기점유") or {}).get("가정_침투율")
    if som is not None:
        needles.append(str(som))
    for ch in (h.get("7_채널") or {}).get("제안값", []) or []:
        needles.append(ch)

    blob = json.dumps({"slots": slots, "formulas": formulas}, ensure_ascii=False)
    hit = [n for n in needles if n and n in blob]
    return {"name": "가설 누출(절대 규칙 6)", "passed": not hit, "violations": hit,
            "검사한_값": len(needles)}


def check_stat_code(slots: list, adapters: dict, kosis_key: str | None) -> dict:
    """G7 — `stat_code` 실재 대조. **추측 금지, 못 찾으면 빈칸 + 보고.**

    LLM 에게는 '모르면 null' 을 지시하므로 대부분 미기재(정직)로 남는다.
    기재된 것만 형식 검사 후 KOSIS 에 실제로 물어본다. 키가 없으면 `not_configured`
    를 값으로 남긴다 — 가짜로 통과시키지 않는다.
    """
    rows = []
    for s in slots:
        code = (s.get("stat_code") or "").strip()
        if not code:
            rows.append({"slot_id": s.get("slot_id"), "state": "미기재", "note": "정직 — 빈칸 유지"})
            continue
        if not _STAT_CODE.match(code):
            rows.append({"slot_id": s.get("slot_id"), "state": "형식_틀림", "code": code})
            continue
        if not kosis_key:
            rows.append({"slot_id": s.get("slot_id"), "state": "not_configured",
                         "code": code, "note": "KOSIS_API_KEY 없음 — 대조 못 함"})
            continue
        rows.append({"slot_id": s.get("slot_id"), "code": code,
                     **_kosis_exists(code, adapters, kosis_key)})
    bad = [r for r in rows if r.get("state") in ("형식_틀림", "없음")]
    return {"name": "stat_code 실재 대조", "passed": not bad, "rows": rows,
            "violations": bad}


def _kosis_exists(code: str, adapters: dict, key: str) -> dict:
    org, tbl = code.split("/", 1)
    cfg = adapters.get("kosis") or {}
    q = {"method": "getMeta", "apiKey": key, "format": "json", "jsonVD": "Y",
         "orgId": org, "tblId": tbl, "type": "TBL"}
    url = (cfg.get("meta_base") or "https://kosis.kr/openapi/statisticsData.do") \
        + "?" + urllib.parse.urlencode(q)
    try:
        with urllib.request.urlopen(url, timeout=20) as r:
            body = r.read().decode("utf-8", "replace")
    except Exception as e:
        return {"state": "fetch_failed", "note": f"{type(e).__name__}: {str(e)[:80]}"}
    if '"err"' in body or "존재하지 않습니다" in body:
        return {"state": "없음", "note": body[:120]}
    return {"state": "실재", "note": body[:80]}


# ══════════════════════════════════════════════════════════════
# ── 권고 검사 ────────────────────────────────────────────────────────────
# **탈락시키지 않는다.** `passed` 는 항상 True 이고 위반은 `권고` 칸에 담긴다.
#
# 왜 권고인가 — 판 ⑧ 이 「템플릿 필수 자리」에서 재시도 3/3 을 소진하고 **스냅샷 없이**
# 죽었다. 게이트를 막는 검사는 만족 불가능해지는 순간 **수집 자체를 막는다.** 아래 둘은
# 「이렇게 하면 잘 되더라」이지 「이러지 않으면 틀렸다」가 아니다 — 컨셉에 따라 수요를
# 3갈래로 못 가르는 것이 정상인 경우가 있다.
#
# 그렇다고 조용히 두지도 않는다. `slot_harness` 가 이 칸을 **재시도 프롬프트로 되먹이고**
# best-of-N 가중에 낮은 무게로 센다 — 막지는 않되 나은 판본을 고르게는 한다.
def _권고(name: str, 권고: list, note: str = "") -> dict:
    return {"name": name, "passed": True, "권고_검사": True, "권고": 권고,
            "_규칙": note or "권고다 — 게이트를 막지 않는다. 되먹임과 best-of-N 에만 쓴다."}


def check_must_contain(slots: list) -> dict:
    """G24(권고) — `must_contain` 이 **자기 subject 의 낱말 하나**인가.

    `must_contain` 은 `any()` 다(`blocks/a_desk.py`) — 낱말을 늘리면 조여지는 게 아니라
    **느슨해진다.** 판 ㉛ 9회차 실측: `pin-09`(6/6)의 비지 않은 9칸은 전부 낱말이 하나이고
    그 낱말이 자기 subject 의 부분문자열이다. 반면 `pin-06` 은 subject="1인 가구" ·
    must_contain=["문제"] 로 성적표 6/6 을 냈는데 그 값이 「70대 이상 1인 가구
    우울증상유병률 8.9%」였다 — **인구만 맞고 문제의 종류가 다르다.**

    ⚠ **빈 `must_contain` 은 위반이 아니다.** `pin-09` 의 TAM·COMP 12칸이 비어 있고
      그것이 옳다(경로 보증이 있거나 낱말로 가를 것이 없는 자리다).
      「must_contain 없음」을 나무라는 것은 `tools/slot_dryrun.check_guards` 쪽 관점이고,
      이 검사는 **적었으면 제대로 적었는가**만 본다. 둘은 일부러 다른 것을 본다.
    """
    권고 = []
    for s in slots:
        mc = [w for w in (s.get("must_contain") or []) if str(w).strip()]
        if not mc:
            continue
        subj = str(s.get("subject") or "")
        why = []
        if len(mc) > 1:
            why.append(f"낱말 {len(mc)}개 — any() 라 늘리면 조여지는 게 아니라 느슨해진다")
        for w in mc:
            if " " in w:
                why.append(f"'{w}' 에 공백 — 원문 표기와 어긋나면 통과 불가능한 벽이 된다")
            elif w not in subj:
                why.append(f"'{w}' 가 자기 subject 「{subj}」에 없다 — "
                           "아무 문서에나 있는 말이면 종류가 다른 값이 문턱을 넘는다")
        if why:
            권고.append({"slot_id": s.get("slot_id"), "subject": subj,
                        "must_contain": mc, "why": why})
    return _권고("must_contain 규율(권고)", 권고)


def check_habitat_spread(slots: list, vocab: dict) -> dict:
    """G25(권고) — PAIN·PRICE 가 **서로 다른 subject** 로 흩어져 있는가.

    한 칸에 표적 하나면 그 서식지(값이 실리는 문서 종류)를 검색이 못 물어온 판은 칸이
    통째로 빈다. 판 ㉛: `pin-04`(칸마다 1개) 4/6 → `pin-05`(분산 시작) 5/6 →
    `pin-09`(PAIN 4 · PRICE 5) 6/6.

    ⚠ **개수가 아니라 서로 다른 subject 를 센다.** 검색어는 subject·metric·period·region
      으로 만들어지므로(`adapters/web.py`), `must_contain` 만 다르고 subject 가 같으면
      **같은 검색어를 두 번 던지는 것**이다. `pin-05` 가 실제로 그 함정에 빠져 슬롯은
      14→17 로 늘었는데 PAIN 의 서로 다른 subject 는 1 그대로였다.
    """
    req = ((vocab.get("요구") or {}).get("서식지_분산") or {})
    대상 = list(req.get("대상_claim_type") or [])
    문턱 = int(req.get("최소_서로_다른_subject") or 0)
    if not 대상 or not 문턱:
        return {**_권고("서식지 분산(권고)", []), "_비활성": "vocab.요구.서식지_분산 미설정"}
    본 = {}
    for s in slots:
        본.setdefault(s.get("claim_type"), set()).add(s.get("subject"))
    권고 = [{"claim_type": ct, "서로_다른_subject": len(본.get(ct) or ()), "문턱": 문턱,
            "subject": sorted(본.get(ct) or ()),
            "why": "표적이 한 서식지에 몰려 있다 — 그 서식지를 검색이 못 물어온 판은 "
                   "칸이 통째로 빈다. **분산은 subject 로 한다**(must_contain 만 바꾸면 "
                   "검색어가 같아 중복이다)"}
           for ct in 대상 if len(본.get(ct) or ()) < 문턱]
    return _권고("서식지 분산(권고)", 권고)


def check_publishability(slots: list, vocab: dict, adapters: dict,
                         concept: dict | None = None) -> dict:
    """G27(권고) — 수요·가격 칸이 **회사를 지목**하고 있는가. 그 값은 발행되지 않는다.

    ⚠ **이 검사는 `tools/design_score.py` 의 「발행_가능성」 축을 게이트로 옮긴 것이다.**
      그 축은 판 ㉜부터 있었는데 **게이트에는 없었다** — 그래서 하네스가 위반을 보지도,
      되먹이지도 못했다. 2026-08-12 실측(제품 경로 첫 유료 실행): PRICE 세 칸이 전부
      「비비고 냉동식품」·「오뚜기 냉동식품」·「풀무원 간편식」이었고 **셋 다 빈손**이라
      성적표 ④가격이 미확보로 났다. 같은 컨셉의 다른 시도는 「편의점 도시락」·「배달 음식」·
      「외식」으로 적었다 — **지시는 이미 프롬프트 7 에 있고 모델이 요동한 것**이다.
      그래서 처방이 「지시 정합」이 아니라 **재시도**다(`vocab.재시도.권고_재시도`).

    경쟁(COMP·COMPARABLE)·채널 칸은 씨앗 실명을 쓰는 것이 **규칙**이라 보지 않는다.
    dart 라우팅도 보지 않는다 — 공시는 회사 단위로 실제 발행된다.
    """
    req = ((vocab.get("요구") or {}).get("서식지_분산") or {})
    대상_ct = list(req.get("대상_claim_type") or [])
    if concept is None or not 대상_ct:
        return {**_권고("발행 가능성(권고)", []),
                "_비활성": "컨셉 미지정 또는 대상 claim_type 미설정 — 씨앗 이름을 모르면 "
                        "회사 지목을 못 가른다"}
    seeds = ((concept.get("_경쟁_씨앗") or {}).get("seeds") or [])
    이름 = sorted({str(x).strip() for s in seeds
                  for x in (s.get("이름"), s.get("운영사")) if str(x or "").strip()},
                 key=len, reverse=True)
    권고 = []
    for s in slots:
        if s.get("claim_type") not in 대상_ct:
            continue
        route, _why = route_of(s, adapters)
        if route == "dart":
            continue
        subj = str(s.get("subject") or "")
        맞은 = [n for n in 이름 if n in subj]
        if 맞은 or s.get("corp_name"):
            권고.append({"slot_id": s.get("slot_id"), "claim_type": s.get("claim_type"),
                        "subject": subj, "metric": s.get("metric"),
                        "지목": 맞은 or [s.get("corp_name")],
                        "why": "회사를 지목한 수요·가격 표적은 발행되지 않는다 "
                               "(실측: 「프레시지 월 구독료」 0건 · 「비비고 냉동식품 판매가」 "
                               "0건). 통계·보도에 실제로 실리는 **대체재·이용 행태**로 "
                               "물어라 — 편의점 도시락가·배달비·외식비·혼자 식사 비율 같은 자리다"})
    return _권고("발행 가능성(권고)", 권고)


def check_range_band(slots: list, guards: dict | None = None) -> dict:
    """G26(권고) — 슬롯의 `value_range` 가 그 계량의 **전형 밴드**와 겹치는가.

    판 ㉜ 실측: 하네스가 거래액 밴드를 `[1e8, 2e9]` 로 적었고 참값은 38.0조였다.
    **6슬롯·성적표 4과목·blocker 1개가 이 하나의 하류**였다. 수집이 값을 찾아놓고 버린 것이다.
    수집 층에는 구조 갈래를 넣었지만(`a_desk.off_slot_reason`), **설계 시점에 미리 보이는
    편이 싸다** — 여기서 걸리면 유료 수집을 태우기 전에 고칠 수 있다.

    ⚠ **합격 조건은 `data/slots_hmr-pin09.json` 이 걸리는 것이다.** 그 스냅샷은 성적표
      6/6 을 낸 기준 설계인데 S1 이 `[1e9, 5e10]` 이고 거래액 전형은 1e11~1e14 다 —
      **겹치지 않는다.** 그 6/6 의 ①시장크기는 자릿수 차이 2.88 로 문턱 3.0 을 **간신히**
      지나 서 있었다. 이 검사가 pin-09 를 통과시키면 **검사가 무른 것**이다.

    규칙 값은 `rules/guards.v1.json` 에 있다 — `blocks/` 와 이 하네스가 **같은 표**를 봐야
    「설계는 통과인데 수집이 버린다」가 안 생긴다.
    """
    if guards is None:
        guards = _load(os.path.join(ROOT, "rules", "guards.v1.json"))
    bands = ((guards.get("value_range") or {}).get("계량_전형_밴드") or {})
    if not bands:
        return {**_권고("value_range 전형 밴드(권고)", []),
                "_비활성": "guards.value_range.계량_전형_밴드 미설정"}
    권고 = []
    for s in slots:
        band = (bands.get(s.get("metric")) or {}).get("밴드")
        vr = s.get("value_range")
        if not band or not vr or len(vr) != 2:
            continue                       # 밴드 없는 계량은 판정하지 않는다
        lo, hi = vr
        if hi < band[0] or lo > band[1]:   # 겹치지 않는다
            권고.append({"slot_id": s.get("slot_id"), "metric": s.get("metric"),
                        "subject": s.get("subject"),
                        "value_range": [lo, hi], "전형_밴드": list(band),
                        "why": f"「{s.get('metric')}」의 전형 크기는 [{band[0]:g}, {band[1]:g}] "
                               f"인데 이 슬롯의 기대는 [{lo:g}, {hi:g}] 다 — 겹치지 않는다. "
                               "이대로 수집하면 **맞는 값이 격리된다**(판 ㉜ 에서 6슬롯이 그랬다)"})
    return _권고("value_range 전형 밴드(권고)", 권고)


def run_gate(raw: dict, slots: list, formulas: list, vocab: dict, adapters: dict,
             hypotheses: dict, kosis_key: str | None = None,
             slotcheck: dict | None = None, as_of_year: int | None = None,
             corpcode: dict | None = None, concept: dict | None = None,
             series_rule: dict | None = None) -> dict:
    cat = vocab["metric"]["catalog"]
    if series_rule is None:                       # 규칙은 파일이 정본이다(절대 규칙 7)
        series_rule = _load(os.path.join(ROOT, "rules", "series_unit.v1.json"))
    checks = [
        check_forbidden_fields(raw),
        check_vocab(slots, vocab),
        check_routing(slots, adapters),
        check_formula_join(slots, formulas),
        check_coverage(slots, vocab),
        check_cell_fit(slots, vocab, concept),
        check_dart_corp_metric(slots, vocab),
        check_hypothesis_leak(slots, formulas, hypotheses),
        check_stat_code(slots, adapters, kosis_key),
        check_role_kind(formulas, vocab, cat),
        check_template_roles(formulas, vocab),
        check_price_cell(slots, vocab),
        check_period(slots, slotcheck or {}, as_of_year or 0),
        check_corp_exists(slots, corpcode or {}),
        check_placeholder(slots),
        check_fixed_metrics(slots, vocab),
        check_value_range(slots, vocab),
        check_reverse_corp(slots, vocab),
        check_dart_probe(slots, vocab, concept),
        check_slot_keys(slots, vocab),
        check_extract_hints(slots, vocab, concept),
        check_unit_subject(slots, series_rule, concept),
        check_subject_aliases(slots, vocab),
        # ── 여기부터 권고. `passed` 는 항상 True 라 게이트 판정을 바꾸지 않는다 ──
        check_must_contain(slots),
        check_habitat_spread(slots, vocab),
        check_range_band(slots),
        check_publishability(slots, vocab, adapters, concept),
    ]
    return {"passed": all(c["passed"] for c in checks),
            "checks": checks, "rules_series_unit": series_rule,
            # ⚠ **`요약` 은 「통과/실패」 이분법으로 둔다.** `tools/harness_variance.py` 가
            #   «"통과" 가 아닌 것 = 미통과» 로 세기 때문에, 여기에 「권고 N건」을 섞으면
            #   그 도구의 «검사별_미통과_횟수» 에 권고가 조용히 실패로 합류한다.
            #   권고는 아래 별도 칸으로 낸다 — 다른 도구의 뜻을 바꾸지 않으면서 보이게.
            "요약": {c["name"]: ("통과" if c["passed"] else "실패") for c in checks},
            "권고_요약": {c["name"]: len(c.get("권고") or [])
                       for c in checks if c.get("권고_검사")},
            "권고_수": sum(len(c.get("권고") or []) for c in checks)}
