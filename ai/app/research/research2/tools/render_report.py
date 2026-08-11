# -*- coding: utf-8 -*-
"""C3 보고서를 사람이 읽는 파일로 렌더한다. **LLM 0회 · 계산 0회.**

    python tools/render_report.py report1-01        # → runs/report1-01/report.md

이 파일은 **아무것도 계산하지 않는다.** `result.json` 의 `report` 를 그대로 옮길 뿐이다.
여기서 값을 만들거나 고치면 원장과 보고서가 갈라진다 — 그 순간 §6 의 존재 이유가 사라진다.
빈 섹션은 **빈 채로 낸다.** 채우면 그것이 곧 지어낸 값이다.
"""
from __future__ import annotations

import argparse, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

from schema import NOT_FOUND_KEYS

#: §7 키를 사람 말로. 키가 늘면 여기도 늘린다 — 안 늘리면 이름 없이 나간다.
KO = {
    "empty_slots": "공백 슬롯 (근거 0건)",
    "thin_slots": "빈약한 슬롯 (라벨은 붙었으나 표본 미달)",
    "unfilled_vars": "가정으로 채운 변수",
    "suspect_var": "재조사 1순위 변수",
    "off_slot": "격리된 근거 (슬롯 불일치)",
    "adapters": "어댑터 상태",
    "retry_hints": "재조사 힌트 (사람이 승인해 1회만)",
    "unknown_error_codes": "분류하지 못한 외부 응답",
    "contradictions": "모순 관측 (같은 대상·단위인데 값이 갈림)",
    "url_filtered": "열지 않고 거른 후보 (URL 필터)",
    "independent_topdown_blocked": "독립 Top-down 불가 — 사유 (‘더 찾아라’ 가 아니라 ‘찾아도 없다’)",
    "unit_mismatch": "단위 불일치로 멈춘 계산",
    "range_capped": "범위 상한에 부딪힌 추정",
    "skipped_checks": "선행 규칙 위반으로 건너뛴 검사",
}


def _rows(d, node):
    """같은 실행의 run.jsonl 에서 노드만 뽑는다. **계산이 아니라 조립**이다."""
    import os as _os
    p = _os.path.join(d, "run.jsonl")
    if not _os.path.exists(p):
        return []
    out = []
    for line in io.open(p, encoding="utf-8"):
        if line.strip():
            x = json.loads(line)
            if x["node"] == node:
                out.append(x)
    return out


def _fmt(v):
    if isinstance(v, float):
        return f"{v:,.10g}"
    return f"{v:,}" if isinstance(v, int) else str(v)


def render(run_id: str) -> str:
    d = os.path.join(ROOT, "runs", run_id)
    res = json.load(io.open(os.path.join(d, "result.json"), encoding="utf-8"))
    rep = res["report"]
    # 값·단위·연도는 `LedgerRow` 에 없고 `Fact` 에 있다. **계산이 아니라 조립**이라
    # 같은 실행의 run.jsonl 에서 fact_id 로 붙인다 — 없는 값을 만들지는 않는다.
    facts = {}
    jl = os.path.join(d, "run.jsonl")
    if os.path.exists(jl):
        for line in io.open(jl, encoding="utf-8"):
            if not line.strip():
                continue
            x = json.loads(line)
            if x["node"] == "a4_facts":
                facts[x["payload"]["fact_id"]] = x["payload"]
    ad = res.get("adapters") or {}
    L = []
    a = L.append

    a(f"# 시장조사 보고서 — {res.get('run_id')}")
    a("")
    a(f"- 기준일 `{res.get('reference_date')}` · 생성 `{res.get('finished_at')}`")
    a(f"- 컨셉: **{(res.get('input', {}).get('concept') or {}).get('name', '(없음)')}**")
    a(f"- 슬롯 {len(res.get('input', {}).get('slots') or [])}개 · "
      f"어댑터 {ad} · 규칙 `whitelist {res['rules']['whitelist']['version']}`")
    if res.get("coverage_caveat"):
        a(f"- ⚠ {res['coverage_caveat']}")
    a("")
    a("> 이 문서의 모든 숫자는 §6 원장의 행 하나에서 온다. **출처 없이 나타나는 숫자는 없다.**")
    a("> 비어 있는 섹션은 비어 있는 것이 결과다 — 채우지 않았다.")
    a("")

    a("## 1. 결론")
    a("")
    for c in rep.get("conclusion") or []:
        a(f"- {c}")
    if not rep.get("conclusion"):
        a("*(없음)*")
    # 핵심 숫자만 읽는 사람이 못 보면 없는 것과 같다 — 검증 상태를 §1 로 올린다.
    rcs = rep.get("reconciliations") or []
    weak = [r for r in rcs if r.get("adopted") and r.get("status") != "partial_overlap"]
    if weak:
        a("")
        a("**⚠ 이 보고서의 추정값은 전부 「검증 안 됨」 계열이다:**")
        for r in weak:
            lab = ("대조 없음(단일 경로)" if r.get("status") == "single_path"
                   else "수렴했으나 두 경로 비독립" if r.get("status") == "converged"
                   else r.get("status"))
            a(f"  - **{r.get('target')}** — {lab}")
    a("")

    a("## 2. 핵심 숫자")
    a("")
    hn = rep.get("headline_numbers") or []
    if hn:
        notes = ((res.get("rules", {}).get("consistency") or {}).get("report_notes") or {})
        TAG = {"single_path": "🟠 대조 없음", "converged": "🟡 수렴",
               "partial_overlap": "🟠 부분 겹침", "diverged": "🔴 갈림",
               "insufficient": "⚫ 계산 불가"}
        a("| 목표값 | 범위 | 중앙 | 배지 | 검증 상태 |")
        a("|---|---|---|---|---|")
        for h in hn:
            v = h.get("value")
            rng = (f"{_fmt(v[0])} ~ {_fmt(v[1])}" if isinstance(v, list) and len(v) == 2
                   else (h.get("why_no_value") or "값 없음"))
            mid = (f"{_fmt((v[0] * v[1]) ** 0.5)}" if isinstance(v, list) and len(v) == 2 else "")
            st = h.get("status")
            a(f"| {h.get('target')} | {rng} | {mid} | **{h.get('badge')}** | "
              f"{TAG.get(st, st)} |")
        a("")
        for st, key in (("single_path", "single_path"),
                        ("converged", "nonindependent_converged")):
            if any(h.get("status") == st for h in hn) and notes.get(key):
                a(f"- {TAG.get(st, st)} — {notes[key]}")
        # 밴드가 상한에 얼마나 가까운지. **아슬아슬함 자체가 독자 정보다.**
        band = (res.get("rules", {}).get("scoring") or {}).get("estimate_band") or {}
        per, mx = band.get("per_assumption", 0.5), band.get("max_factor", 8.0)
        worst = max([e.get("assumption_count", 0) for e in rep.get("how_computed") or []] or [0])
        f_now = (1 + per) ** worst
        if f_now <= mx < f_now * (1 + per):
            a(f"- ⚠ 현재 최대 가정 {worst}개 → 범위 배수 {f_now:.2f} (상한 {mx}). "
              f"**가정 1개만 더 늘면 상한을 넘어 '추정 불가 — 가정 과다' 로 전환된다.**")
        a("")
        a("중앙값은 범위의 기하평균이다(범위가 배수로 벌어지므로). **점추정이 아니라 범위가 결론이다.**")
    else:
        a("**없음.** 사슬 칸(TAM·SAM·SOM·1년차 매출…)이 하나도 채워지지 않았다.")
        a("고정 슬롯 모드에서는 A1 이 만드는 **식(Formula)이 없어** B블록이 추정을 만들지 못한다.")
    a("")

    # ── §2 병기 — **민감도 최대(suspect_var)와 불확실성 최대는 다른 개념이다.**
    #    문구는 코드가 아니라 `scoring.suspect_var.uncertainty_note` 에서 온다(절대규칙 7).
    #    불확실성은 가정 밴드의 상한/하한 비로 잰다 — 결정론이고 모델이 개입하지 않는다.
    sv = (res.get("rules", {}).get("scoring") or {}).get("suspect_var") or {}
    by_role = ((res.get("rules", {}).get("assumptions") or {}).get("by_role") or {})
    roles = {}
    for f in [x["payload"] for x in _rows(d, "a1_formula")]:
        for v in f.get("vars", []):
            roles[v["var_id"]] = v.get("var_role")
    used = {i["var_id"] for e in rep.get("reconciliations") or [] for i in []}
    used = set()
    for e in [x["payload"] for x in _rows(d, "b2_estimate")]:
        used |= {i["var_id"] for i in e.get("inputs", []) if not i.get("confirmed")}
    best = None
    for vid in used:
        rng = (by_role.get(roles.get(vid)) or {}).get("range")
        if rng and rng[0]:
            ratio = rng[1] / rng[0]
            if not best or ratio > best[1]:
                best = (vid, ratio, rng, roles.get(vid))
    if best and sv.get("uncertainty_note"):
        vid, ratio, rng, role = best
        a("")
        a("⚠ " + sv["uncertainty_note"].format(var=vid, role=role, lo=_fmt(rng[0]),
                                               hi=_fmt(rng[1]), ratio=f"{ratio:.2g}"))
    a("")

    a("## 3. 어떻게 계산했나")
    a("")
    hc = rep.get("how_computed") or []
    for e in hc:
        a(f"### {e.get('formula_id')} ({e.get('path')}) → {e.get('target')} · "
          f"가정 {e.get('assumption_count')}개 · {e.get('status')}")
        a("")
        a("| 변수 | 출처 | 값 | 근거 |")
        a("|---|---|---|---|")
        for i in e.get("inputs", []):
            src = (f"원장 {i['from_fact']}" if i.get("confirmed") else "**가정**")
            val = _fmt(i.get("assumption")) if i.get("assumption") is not None else ""
            a(f"| {i.get('var_id')} | {src} | {val} | {(i.get('basis') or '')[:110]} |")
        a("")
    if not hc:
        a("*(계산 없음 — §2 와 같은 이유)*")
    a("")

    a("## 4. 틀릴 수 있는 지점")
    a("")
    for f in rep.get("falsifiers") or []:
        a(f"- {f}")
    if not rep.get("falsifiers"):
        a("*(추정이 없어 반증 조건도 없다)*")
    a("")

    a("## 5. 두 경로 대조")
    a("")
    rc = rep.get("reconciliations") or []
    for r in rc:
        td, bu = r.get("topdown"), r.get("bottomup")
        a(f"### {r.get('target')} — **{r.get('status')}** (격차 {r.get('gap_ratio')}배)")
        a("")
        a(f"- Top-down: {_fmt(td[0]) + ' ~ ' + _fmt(td[1]) if td else '계산 불가'}")
        a(f"- Bottom-up: {_fmt(bu[0]) + ' ~ ' + _fmt(bu[1]) if bu else '계산 불가'}")
        a(f"- 채택: {_fmt(r['adopted'][0]) + ' ~ ' + _fmt(r['adopted'][1]) if r.get('adopted') else '**없음** — 그럴듯한 쪽을 고르지 않는다'}")
        a(f"- 재조사 1순위(민감도 최대): **{r.get('suspect_var')}**")
        if r.get("status") == "converged":
            a("")
            a("> ⚠ **수렴이 곧 삼각측량은 아니다.** 두 경로가 같은 사실을 공유하면 "
              "수렴은 당연하고 아무것도 검증하지 않는다. 위 §3 의 변수 출처를 보고 "
              "**두 경로가 독립인지 직접 확인하라.**")
        a("")
    if not rc:
        a("*(대조 없음 — Top-down·Bottom-up 식이 없다)*")
    a("")

    a("## 6. 근거 원장")
    a("")
    a("등급이 붙는 곳은 여기뿐이다. 모델은 이 표를 만들지 못한다.")
    a("")
    a("| 슬롯 | 라벨 | 점수 | 교차 | 출처 유형 | 값 | 단위 | 연도 | URL |")
    a("|---|---|---|---|---|---|---|---|---|")
    for r in rep.get("ledger") or []:
        f = facts.get(r.get("fact_id"), {})
        v = f.get("value_num")
        a(f"| {r.get('slot_id')} | {r.get('label')} | {r.get('score')} | {r.get('cross')} | "
          f"{r.get('kind')} | {_fmt(v) if v is not None else ''} | {f.get('unit_norm') or ''} | "
          f"{f.get('year') or ''} | {(r.get('url') or '')[:52]} |")
    a("")
    for r in rep.get("ledger") or []:
        notes = [x for x in (r.get("reasons") or []) if x]
        # conflict 는 reasons 에도 들어 있다 — 두 번 찍지 않는다
        if r.get("conflict") and r["conflict"] not in notes:
            notes.append(r["conflict"])
        notes = [("⚠ " + n if ("갈림" in n or "스케일" in n) else n) for n in notes]
        if r.get("off_slot_reason"):
            notes.append(f"격리: {r['off_slot_reason']}")
        if r.get("scope_note"):
            notes.append(f"⚠ {r['scope_note']}")
        if notes:
            a(f"- **{r.get('slot_id')}** ({r.get('fact_id')}): " + " · ".join(notes))
    a("")

    a("## 7. 못 찾은 것")
    a("")
    a("**이 섹션을 빼지 않는다.** 안 보여준 것이 무엇인지 먼저 내놓는 것이 신뢰를 만든다.")
    a("")
    nf = rep.get("not_found") or {}
    missing = [k for k in NOT_FOUND_KEYS if k not in nf]
    if missing:
        a(f"> ⚠ **키 누락 {missing}** — 침묵이 생긴 자리다. 규칙 위반이므로 그대로 표시한다.")
        a("")
    for k, v in nf.items():
        name = KO.get(k, k)
        if isinstance(v, list):
            a(f"### {name} — {len(v)}건")
            for x in v[:20]:
                a(f"- {json.dumps(x, ensure_ascii=False) if isinstance(x, (dict, list)) else x}")
            if not v:
                a("*(없음)*")
        elif isinstance(v, dict):
            a(f"### {name}")
            a(f"- {json.dumps(v, ensure_ascii=False)}")
        else:
            a(f"### {name}")
            a(f"- {v}")
        a("")
    return "\n".join(L)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id")
    a = ap.parse_args()
    out = os.path.join(ROOT, "runs", a.run_id, "report.md")
    io.open(out, "w", encoding="utf-8").write(render(a.run_id))
    print(f"→ {out}")


if __name__ == "__main__":
    main()
