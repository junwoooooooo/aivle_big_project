# -*- coding: utf-8 -*-
"""BM 게이트 채점기 — **서비스 층 1호.** LLM 0회 · 네트워크 0회 · 원장 읽기 전용.

    python service/bm_scorer.py <run_id>
    python service/bm_scorer.py <run_id> --json

한 방향 유리벽 — 이 파일이 지키는 것:

  · **엔진을 import 하지 않는다.** `blocks/`·`adapters/` 를 부르지 않는다. 원장(`runs/<id>/`)과
    규칙(`rules/bm_gate.v1.json`)만 읽는다. 엔진이 바뀌어도 여기는 안 깨지고, 여기가 뭘 해도
    엔진은 모른다.
  · **원장에 쓰지 않는다.** 읽기만 한다.
  · **점수는 규칙 파일이 정한다.** 이 파일에 임계치 상수를 두지 않는다(절대규칙 7).
  · **모든 판정에 원장 인용을 붙인다.** `trace_id`·`fact_id`·`rule_id` 중 하나 이상.
    인용 없는 판정은 만들지 않는다 — 유일한 예외가 `declared_defects` 이고, 그건 출력이
    스스로 「선언(원장 관측 아님)」이라고 밝힌다.
  · **공백은 공백이라 쓴다.** 등급을 지어내지 않는다.
  · **규칙이 원장에 없는 필드를 참조하면 `판정_불가`** 로 남긴다(fail-closed · 조용한 기본값 금지).
"""
from __future__ import annotations

import argparse, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
# 채움 축 토글 — **잎 모듈**이라 유리벽을 넘지 않는다(엔진 import 0).
sys.path.insert(0, ROOT)
import fillaxis as _fx                              # noqa: E402
# 원장 위치 해결자. **잎 모듈이라 유리벽을 넘지 않는다** — `os` 밖에 아무것도 import 하지
# 않고 아무것도 계산하지 않는다(경로 상수뿐). 벽이 막는 것은 엔진 계산이다.
import runpath as _runpath                          # noqa: E402
RULES = os.path.join(ROOT, "rules", "bm_gate.v1.json")


# ══════════════════════════════════════════════════════════════
# 원장 읽기 — 이 파일이 원장을 만지는 유일한 곳
# ══════════════════════════════════════════════════════════════
def load_ledger(run_id: str) -> dict:
    # 원장 자리가 둘이다(씨앗 `runs/` 는 `:ro`, 수집이 만든 것은 `runs-generated/`).
    # 답은 `runpath` 한 곳에 있다 — 여기서 다시 조립하면 읽는 자리마다 답이 갈린다.
    d = _runpath.read_dir(run_id)
    res_p, jl_p = os.path.join(d, "result.json"), os.path.join(d, "run.jsonl")
    if not os.path.exists(res_p):
        raise FileNotFoundError(f"원장이 없다: {res_p}")
    res = json.load(io.open(res_p, encoding="utf-8"))
    rows = []
    if os.path.exists(jl_p):
        rows = [json.loads(l) for l in io.open(jl_p, encoding="utf-8") if l.strip()]

    def node(n):
        return [x["payload"] for x in rows if x["node"] == n]

    return {
        "run_id": res.get("run_id", run_id),
        # 기준일 — 신선도·시차 판정이 **오늘**이 아니라 실행 기준일에서 나와야
        # 「같은 원장 → 같은 결과」가 깨지지 않는다(`rules/scoring.reference_date`).
        "reference_date": res.get("reference_date"),
        "slots": res.get("input", {}).get("slots") or [],
        "coverage": {c["slot_id"]: c for c in node("a4_coverage")},
        "violations": {v["rule_id"]: v for v in node("c2_violations")},
        "ledger_rows": node("a4_ledger"),
        "facts": {f["fact_id"]: f for f in node("a4_facts")},
        "report": res.get("report") or {},
    }


def _cites(led: dict, slot_ids: list, only_confirmed: bool = True) -> list:
    """그 슬롯들의 근거를 원장에서 끌어온다. **판정마다 인용을 붙이기 위한 것.**"""
    out = []
    for r in led["ledger_rows"]:
        if r["slot_id"] not in slot_ids:
            continue
        if only_confirmed and not _fx.filled(r, "bm_scorer._cites"):
            continue
        f = led["facts"].get(r.get("fact_id")) or {}
        out.append({"slot_id": r["slot_id"], "fact_id": r.get("fact_id"),
                    "trace_id": f.get("trace_id"), "label": r.get("label"),
                    "score": r.get("score"), "url": (r.get("url") or "")[:70]})
    return out


# ══════════════════════════════════════════════════════════════
# 축 하나 채점 — 상태는 규칙 파일의 states 에서만 나온다
# ══════════════════════════════════════════════════════════════
def score_axis(axis: dict, led: dict, rules: dict) -> dict:
    out = {"id": axis["id"], "name": axis["name"], "cites": [], "notes": []}
    src = axis.get("source")

    if src == "coverage":
        want = set(axis.get("claim_types") or [])
        slot_ids = [s["slot_id"] for s in led["slots"] if s.get("claim_type") in want]
        if not slot_ids:
            # **재지 않은 것과 못 채운 것을 구분한다.**
            out["state"] = axis.get("absent_state") or "미충족"
            out["why"] = axis.get("absent_note") or f"{sorted(want)} 슬롯이 하나도 없다"
            return out
        missing = [sid for sid in slot_ids if sid not in led["coverage"]]
        if missing:
            out["state"] = "판정_불가"      # fail-closed — 조용한 기본값 금지
            out["why"] = f"원장에 coverage 가 없는 슬롯: {missing}"
            return out
        need = axis["require"]["coverage_status"]
        hit = [sid for sid in slot_ids if led["coverage"][sid].get("status") == need]
        out["cites"] = _cites(led, hit)
        if len(hit) >= axis["require"].get("min_slots", 1):
            thin = [sid for sid in hit if led["coverage"][sid].get("thin")]
            out["state"] = "충족"
            out["why"] = f"{sorted(want)} 슬롯 {sorted(hit)} 이 '{need}'"
            if thin and axis.get("qualifier_thin"):
                out["qualifier"] = axis["qualifier_thin"]
                out["notes"].append(f"표본 얇음: {sorted(thin)} (min_facts 미달)")
        else:
            out["state"] = "미충족"
            out["why"] = axis.get("reason_if_unmet", "")
            out["notes"].append("슬롯별 상태: " + json.dumps(
                {sid: led["coverage"][sid].get("status") for sid in slot_ids},
                ensure_ascii=False))
        return out

    if src == "consistency_rule":
        rid = axis["rule_id"]
        v = led["violations"].get(rid)
        if v is None:
            out["state"] = "판정_불가"      # fail-closed
            out["why"] = f"원장에 규칙 {rid} 의 판정이 없다"
            return out
        want = set(axis.get("cite_claim_types") or [])
        sids = [s["slot_id"] for s in led["slots"] if s.get("claim_type") in want]
        out["cites"] = [{"rule_id": rid, "status": v.get("status"),
                         "detail": (v.get("detail") or "")[:110]}] + _cites(led, sids)
        if v.get("status") == axis["require"]["violation_status"]:
            out["state"] = "충족"
            out["why"] = f"{rid} {v.get('status')}"
        elif v.get("status") in ("violated", "not_applicable", "skipped"):
            out["state"] = "미충족"
            out["why"] = f"{rid} {v.get('status')} — " + axis.get("reason_if_unmet", "")
        else:
            out["state"] = "판정_불가"
            out["why"] = f"{rid} 상태를 해석할 수 없다: {v.get('status')}"
        return out

    out["state"] = "판정_불가"
    out["why"] = f"알 수 없는 source: {src}"
    return out


def score(run_id: str, rules: dict | None = None) -> dict:
    rules = rules or json.load(io.open(RULES, encoding="utf-8"))
    led = load_ledger(run_id)
    axes = [score_axis(a, led, rules) for a in rules["axes"]]
    by_id = {a["id"]: a for a in axes}

    # ── 선언 결함 — **관측이 아니다.** 출력이 스스로 그렇게 밝힌다.
    for df in rules.get("declared_defects") or []:
        tgt = by_id.get(df.get("axis"))
        if not tgt:
            continue
        tgt.setdefault("declared", []).append({
            "사유코드": df.get("사유코드"), "id": df.get("id"),
            "꼬리표": "선언(원장 관측 아님) · 근거: " + str(df.get("근거")),
            "만료조건": df.get("만료조건"),
        })
    return {"run_id": led["run_id"], "rules_version": rules["version"], "axes": axes,
            # 수집 조건이 다른 원장을 한 표에 두지 않는다(백로그 6.5). 규칙에 적힌 주의를
            # 성적표에 실어 보낸다 — 비교 금지는 읽는 사람에게 보여야 효력이 있다.
            **({"비교_주의": rules["비교_주의"]} if rules.get("비교_주의") else {}),
            "요약": {s: sum(1 for a in axes if a["state"] == s)
                    for s in rules["states"]}}


def render(rep: dict) -> str:
    # 수집 조건이 다른 원장을 한 표에 두지 않는다(백로그 6.5). 규칙에 적힌 주의를
    # **성적표 머리에** 그대로 얹는다 — 비교 금지는 읽는 사람에게 보여야 효력이 있다.
    L = [f"# BM 게이트 채점 — {rep['run_id']}", "",
         f"규칙 `{rep['rules_version']}` · 요약 {rep['요약']}", "",
         "> 판정은 **원장에서만** 나온다. 인용 없는 판정은 없다 —",
         "> 유일한 예외인 「선언」 항목은 스스로 그렇게 밝힌다.", ""]         + ([f"> ⚠ {rep['비교_주의']}", ""] if rep.get("비교_주의") else []) + [
         "| 축 | 상태 | 사유 | 인용 |", "|---|---|---|---|"]
    for a in rep["axes"]:
        st = a["state"] + (f" · {a['qualifier']}" if a.get("qualifier") else "")
        cite = "; ".join(
            (c.get("rule_id") or f"{c.get('slot_id')}/{c.get('fact_id')}/{c.get('trace_id')}")
            for c in a["cites"][:3]) or "—"
        L.append(f"| {a['name']} | **{st}** | {a['why']} | {cite} |")
    L.append("")
    for a in rep["axes"]:
        for n in a.get("notes") or []:
            L.append(f"- {a['name']}: {n}")
        for d in a.get("declared") or []:
            L.append(f"- {a['name']}: **{d['사유코드']}** — {d['꼬리표']} · 만료: {d['만료조건']}")
    return "\n".join(L)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    rep = score(a.run_id)
    print(json.dumps(rep, ensure_ascii=False, indent=2) if a.json else render(rep))


if __name__ == "__main__":
    main()
