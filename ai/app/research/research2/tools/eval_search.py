# -*- coding: utf-8 -*-
"""작업 12-2 판정기 — A3 `search` 단계만 잰다. **LLM 0회 · 네트워크 0회.**

기준은 `expected.md` 에 있고 이 파일은 그것을 계산할 뿐이다. 임계치를 여기서 바꾸지 마라.

    python tools/eval_search.py --control fixed-01 route12-01 route12-02
    python tools/eval_search.py --control fixed-01 route12-01 route12-02 \
                                --treat search12-01 search12-02 search12-03

`found`·`확인됨` 은 **표시만 한다.** 발췌 이후가 섞인 값이라 검색 성적이 아니다(expected.md §0).
"""
from __future__ import annotations

import argparse, collections, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import a_desk as A
from runlog import load_rules

SLOTS = ("S3", "S4", "S5", "S6")
#: expected.md **부록 A**(v4 재보정). 여기서 고치지 마라 — 고치려면 expected.md 에 먼저 append 한다.
#  v3 기준(0.25/0.15)은 폐기됐다: 화이트리스트 등재만으로 대조군이 0.091 → 0.473 이 됐다.
GATE = {"M1": 0.73, "M2": 0.65, "M3": 0.35, "M4": 3.0, "M1_보류하한": 0.60}
#: 12-3 주지표 — 「독립 발행자 비율」(expected.md 부록 C-2 의 산수에서 도출).
#  문턱은 **12-3 새 대조군 값만 보고** 등록한다. 등록 전에는 None 이고 판정하지 않는다.
#  등록: expected.md 부록 F (2026-08-06T16:47:18 — 처치군을 열기 전에 대조군만 보고 박았다)
GATE_M5 = 0.45
GATE_M5_보류하한 = 0.32
#: 회귀 감시는 **대조군 상대**다. 절대값(12-2 의 M3 ≤ 0.35)을 쓰면 이번 대조군 자신이
#  M3 = 0.500 으로 위반한다 — 대조군이 못 넘는 문턱으로 처치군을 재면 측정이 아니다.
REGRESSION_REL = {"M2_최대하락": 0.10, "M3_최대상승": 0.10, "M4_최소비율": 0.8}
#: expected.md §1 — 화이트리스트 동결. 버전이 다르면 지표가 소급해 달라진다.
#  ⚠ **두 버전을 헷갈리지 마라.** 실행 당시 `result.json` 에 복사된 버전과, 지금 이 분석이
#    쓰는 `rules/` 의 버전은 다를 수 있다. 지표를 만드는 것은 **후자**다. 둘 다 찍는다.
PINNED_WHITELIST = "v5-2026-08-06"


def _domain(url: str) -> str:
    c = A.canonical_url(url)
    return c.split("/")[2] if c.count("/") >= 2 else c


def _rows(run: str) -> list[dict]:
    path = os.path.join(ROOT, "runs", run, "run.jsonl")
    return [json.loads(l) for l in io.open(path, encoding="utf-8") if l.strip()]


def collect_arm(runs: list[str], rules: dict, human: dict) -> dict:
    """실행 여러 개를 한 팔로 합친다. 문서 단위 지표는 문서를 그대로 쌓는다."""
    wl, base = rules["whitelist"], rules["scoring"]["base_score"]
    self_kinds = set((rules["scoring"].get("cross") or {}).get("self_published_kinds") or [])
    per_slot = collections.defaultdict(lambda: collections.Counter())
    tot = collections.Counter()
    slot_runs, slot_runs_hit, dom_sum = 0, 0, 0
    seen_versions = set()

    for run in runs:
        rows = _rows(run)
        bodies = json.load(io.open(os.path.join(ROOT, "runs", run, "a3_bodies.json"),
                                   encoding="utf-8"))
        res = json.load(io.open(os.path.join(ROOT, "runs", run, "result.json"),
                                encoding="utf-8"))
        seen_versions.add(((res.get("rules") or {}).get("whitelist") or {}).get("version"))
        for sid in SLOTS:
            docs = [x["payload"] for x in rows
                    if x["node"] == "a3_document" and x["payload"]["slot_id"] == sid]
            mc = human[sid]["must_contain"]
            usable = [d for d in docs if d["content_status"] == "usable"]
            hi = [d for d in docs if base.get(A.kind_of(d["url"], wl)[0], 0) >= 4]
            hi5 = [d for d in docs if base.get(A.kind_of(d["url"], wl)[0], 0) >= 5]
            # M5 — 독립 발행자. 자기발표도 미등재도 아닌 것.
            # 화자 가드 이후 cross 는 **여기서만** 나오므로 확인됨의 병목이 정확히 이것이다.
            ind = [d for d in docs
                   if A.kind_of(d["url"], wl)[0] not in self_kinds
                   and A.kind_of(d["url"], wl)[0] != wl.get("default_kind", "aggregate")]
            hit = [d for d in usable
                   if any(w in (bodies.get(d["trace_id"]) or "") for w in mc)]
            doms = {_domain(d["url"]) for d in docs if d["url"]}

            c = per_slot[sid]
            c["docs"] += len(docs); c["usable"] += len(usable)
            c["hi"] += len(hi); c["hi5"] += len(hi5); c["hit"] += len(hit)
            c["ind"] += len(ind)
            c["doms"] += len(doms); c["slot_runs"] += 1
            slot_runs += 1
            slot_runs_hit += 1 if hi else 0
            dom_sum += len(doms)
        for k in ("docs", "usable", "hi", "hi5", "hit"):
            tot[k] += sum(per_slot[s][k] for s in SLOTS) - tot.get("_" + k, 0)
            tot["_" + k] = sum(per_slot[s][k] for s in SLOTS)
        tot["found"] += sum(1 for x in rows if x["node"] == "a3_finding"
                            and x["payload"]["slot_id"] in SLOTS
                            and x["payload"]["status"] == "found")
        tot["confirmed"] += sum(1 for x in rows if x["node"] == "a4_ledger"
                                and x["payload"]["slot_id"] in SLOTS
                                and x["payload"]["label"] == "확인됨")

    # 누적은 per_slot 이 정본이다 (위 tot 누산은 found/confirmed 용)
    agg = collections.Counter()
    for s in SLOTS:
        for k in ("docs", "usable", "hi", "hi5", "hit", "ind"):
            agg[k] += per_slot[s][k]
    return {"runs": runs, "per_slot": per_slot, "agg": agg, "slot_runs": slot_runs,
            "slot_runs_hit": slot_runs_hit, "dom_sum": dom_sum,
            "found": tot["found"], "confirmed": tot["confirmed"],
            "whitelist_versions": sorted(v for v in seen_versions if v),
            # 지표를 만드는 것은 **지금 rules/ 에 있는 버전**이다 (사후 재계산이므로)
            "analysis_version": rules["whitelist"].get("version", "?"),
            # 주지표가 읽는 규칙 — 라운드 중 바뀌면 측정이 무효다 (expected.md 부록 B)
            "cross_frozen": sorted(self_kinds)}


def metrics(arm: dict) -> dict:
    a, n = arm["agg"], arm["agg"]["docs"]
    return {
        "M1": a["hi"] / n if n else 0.0,
        "M1a": a["hi5"] / n if n else 0.0,
        "M2": a["hit"] / a["usable"] if a["usable"] else 0.0,
        "M3": 1 - a["usable"] / n if n else 0.0,
        "M4": arm["dom_sum"] / arm["slot_runs"] if arm["slot_runs"] else 0.0,
        "M5": a["ind"] / n if n else 0.0,
    }


def verdict(m: dict) -> str:
    """expected.md §4 그대로. 회귀 감시가 하나라도 무너지면 기각이다."""
    if m["M2"] < GATE["M2"] or m["M3"] > GATE["M3"] or m["M4"] < GATE["M4"]:
        return "기각 (회귀 감시 위반 — 발행자를 사면서 주제·본문·도메인 다양성을 팔았다)"
    if m["M1"] >= GATE["M1"]:
        return "채택"
    if m["M1"] >= GATE["M1_보류하한"]:
        return "보류 (방향은 맞으나 표본 부족 — 처치군 3회 추가 후 재판정)"
    return "기각"


def show(name: str, arm: dict):
    m = metrics(arm)
    a = arm["agg"]
    print(f"\n[{name}]  실행 {', '.join(arm['runs'])}")
    print(f"  분석에 쓴 화이트리스트 {arm['analysis_version']}"
          + ("  (동결 버전과 일치)" if arm["analysis_version"] == PINNED_WHITELIST
             else f"   ⚠ 동결 버전({PINNED_WHITELIST})과 다르다 — 판정하지 마라")
          + f"   · 실행 당시 기록 {arm['whitelist_versions']}")
    print(f"  문서 {a['docs']} · usable {a['usable']} · kind>=4 {a['hi']} · "
          f"kind=5 {a['hi5']} · 주제적중 {a['hit']}")
    print(f"  **M5(주지표) {m['M5']:.3f} ({a['ind']}/{a['docs']})** — 독립 발행자 "
          f"(자기발표 {sorted(arm['cross_frozen'])} · 미등재 제외)")
    print(f"  M1 {m['M1']:.3f} ({a['hi']}/{a['docs']})   M1a {m['M1a']:.3f}   "
          f"M2 {m['M2']:.3f} ({a['hit']}/{a['usable']})   "
          f"M3 {m['M3']:.3f}   M4 {m['M4']:.2f}")
    print(f"  보조: kind>=4 를 1건 이상 물어온 슬롯-실행 {arm['slot_runs_hit']}/{arm['slot_runs']}"
          f"   (단독 판정 금지)")
    print(f"  참고(판정 미사용): found {arm['found']} · 확인됨 {arm['confirmed']}")
    print("  슬롯별 — 총량만 보지 않는다 (S3·S4 는 공개 출처 부재 가능)")
    for s in SLOTS:
        c = arm["per_slot"][s]
        print(f"    {s}  문서 {c['docs']:3d}  독립 {c['ind']:2d}  "
              f"M5 {(c['ind'] / c['docs'] if c['docs'] else 0):.3f}  "
              f"kind>=4 {c['hi']:2d}  usable {c['usable']:2d}  주제적중 {c['hit']:2d}")
    return m


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--control", nargs="+", required=True)
    ap.add_argument("--treat", nargs="*", default=[])
    ap.add_argument("--min-docs", type=int, default=55,
                    help="expected.md §2 의 정지 규칙 — 분모로 건다(결과를 보고 멈추지 않는다)")
    a = ap.parse_args()

    rules = load_rules()
    human = {s["slot_id"]: s for s in
             json.load(io.open(os.path.join(ROOT, "data", "slots.json"),
                               encoding="utf-8"))["slots"]}

    ctl = collect_arm(a.control, rules, human)
    show("대조군", ctl)
    if not a.treat:
        print("\n처치군 없음 — 기준선만 냈다. expected.md 의 실측값과 대조하라.")
        return

    trt = collect_arm(a.treat, rules, human)
    m = show("처치군", trt)
    n = trt["agg"]["docs"]
    print(f"\n정지 규칙: 처치군 문서 {n}/{a.min_docs}"
          + ("  → 충족" if n >= a.min_docs else "  → **미달. 판정하지 말고 1회 더 돌린다**"))
    if n < a.min_docs:
        return
    mc = metrics(ctl)
    if GATE_M5 is None:
        print("\n판정: **보류 — 주지표(M5) 문턱이 아직 등록되지 않았다.**"
              " expected.md 에 대조군 값만 보고 먼저 박아라(부록 C-2).")
        print(f"  (기술 통계) M5 {mc['M5']:.3f} → {m['M5']:.3f}")
        return
    if mc["M5"] >= GATE_M5:
        print(f"\n판정: **불가 — 주지표 포화**. 대조군 M5 {mc['M5']:.3f} 가 이미 채택선 이상이다.")
        return
    ok5 = (m["M5"] >= GATE_M5)
    lim = {"M2": mc["M2"] - REGRESSION_REL["M2_최대하락"],
           "M3": mc["M3"] + REGRESSION_REL["M3_최대상승"],
           "M4": mc["M4"] * REGRESSION_REL["M4_최소비율"]}
    broke = ([f"M2 {m['M2']:.3f} < {lim['M2']:.3f}"] if m["M2"] < lim["M2"] else []) \
        + ([f"M3 {m['M3']:.3f} > {lim['M3']:.3f}"] if m["M3"] > lim["M3"] else []) \
        + ([f"M4 {m['M4']:.2f} < {lim['M4']:.2f}"] if m["M4"] < lim["M4"] else [])
    v = (f"기각 (회귀 감시 위반 — {' · '.join(broke)})" if broke else
         "채택" if ok5 else
         "보류 (표본 추가 — 처치군 3회 더)" if m["M5"] >= GATE_M5_보류하한 else "기각")
    print(f"\n판정: {v}")
    print(f"  M5 {mc['M5']:.3f} → {m['M5']:.3f}  (채택선 {GATE_M5} · 보류하한 {GATE_M5_보류하한})")
    print(f"  회귀 감시(대조군 상대)  M2 {mc['M2']:.3f}→{m['M2']:.3f} (≥{lim['M2']:.3f}) · "
          f"M3 {mc['M3']:.3f}→{m['M3']:.3f} (≤{lim['M3']:.3f}) · "
          f"M4 {mc['M4']:.2f}→{m['M4']:.2f} (≥{lim['M4']:.2f})")
    print(f"  기록만: M1 {mc['M1']:.3f}→{m['M1']:.3f} · M1a {mc['M1a']:.3f}→{m['M1a']:.3f}")
    return
    # ── 아래는 12-2 의 M1 판정부 (기록으로 남긴다) ──
    # ⚠ 안전장치 — **대조군이 이미 문턱을 넘으면 그 문턱은 아무것도 가르지 못한다.**
    #   v5 에서 실제로 그랬다(대조군 M1 0.855 ≥ 0.73). 이때 나오는 '채택'은 측정이 아니라
    #   낡은 문턱의 기계적 산물이다. 판정을 내지 말고 멈춘다 (expected.md 부록 C).
    if mc["M1"] >= GATE["M1"]:
        print(f"\n판정: **불가 — 주지표 포화**. 대조군 M1 {mc['M1']:.3f} 가 이미 채택선"
              f" {GATE['M1']} 이상이다. 문턱을 다시 박기 전에는 판정하지 마라.")
        print(f"  (기술 통계) M1 {mc['M1']:.3f} → {m['M1']:.3f}   M1a {mc['M1a']:.3f} → "
              f"{m['M1a']:.3f}   M2 {mc['M2']:.3f} → {m['M2']:.3f}   "
              f"M3 {mc['M3']:.3f} → {m['M3']:.3f}   M4 {mc['M4']:.2f} → {m['M4']:.2f}")
        return
    print(f"\n판정: {verdict(m)}")
    print(f"  M1 {mc['M1']:.3f} → {m['M1']:.3f}   M2 {mc['M2']:.3f} → {m['M2']:.3f}   "
          f"M3 {mc['M3']:.3f} → {m['M3']:.3f}   M4 {mc['M4']:.2f} → {m['M4']:.2f}")


if __name__ == "__main__":
    main()
