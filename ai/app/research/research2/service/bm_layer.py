# -*- coding: utf-8 -*-
"""BM 층 — **서비스 층 2호.** LLM 0회 · 네트워크 0회 · 원장 읽기 전용.

    python service/bm_layer.py <run_id>
    python service/bm_layer.py <run_id> --json

요구 근거: `문서/요구사항 정의서.xlsx` **BAF-09-07**
  ○ 사업계획서를 기반으로 비즈니스 모델의 **적합성을 분석**
  ○ **수익 구조와 경쟁력**을 평가하여 분석 결과 제공
백로그 6 의 세 원칙 중 이 층에 해당하는 것: **적합성은 검사와 서술을 분리한다.**

이 파일이 지키는 것 (채점기와 같은 유리벽):

  · **엔진을 import 하지 않는다**(`blocks/`·`adapters/`). 채점기와 원장·규칙만 읽는다.
  · **원장에 쓰지 않는다.**
  · **LLM 을 부르지 않는다.** `narrative` 는 **결정론 템플릿 조립**이다 —
    꼬리표도 「생성」이 아니라 **「조립(템플릿 v1)」** 이다. 거짓 꼬리표를 달지 않는다.
    (절대규칙 1 「LLM 은 A블록에만」은 범위 수정 없이 그대로 성립한다)
  · **등급을 매기지 않는다.** 종합 점수도 「적합/부적합」도 내지 않는다.
    결론은 ①축별 사실 요약 ②공백 목록 ③피벗 **조건문**(if-then) 셋뿐이다.
    **조건을 나열하는 것은 관측, 조건을 고르는 것은 판단** — 고르는 일은 피벗 층이 한다.
  · **모든 값 칸에 근거가 붙는다** — `trace_id` / 성적표 인용 / 공백 선언 중 하나.
  · **선언 사유(declared_defects)는 요약에서도 꼬리표를 잃지 않는다.**
  · **원장 하나 → BM 문서 하나.** 종합하지 않는다(백로그 6.5 해소 전까지).
"""
from __future__ import annotations

import argparse, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
# 채움 축 토글 — **잎 모듈**이라 유리벽을 넘지 않는다(엔진 import 0).
sys.path.insert(0, ROOT)
import fillaxis as _fx                              # noqa: E402
# 원장 위치 해결자 — `os` 밖에 아무것도 import 하지 않는 잎 모듈이다(유리벽 유지).
import runpath as _runpath                          # noqa: E402
sys.path.insert(0, HERE)

import bm_scorer                                    # 같은 서비스 층 (엔진 아님)

TEMPLATE_VERSION = "템플릿 v1"

#: (나) 판정 — **뒷문장까지 필수다.** 침묵이 승인으로 읽히는 것을 막는다.
#  분기와 무관하게 **항상** 붙는다. 경계 표시라 앞문장과 따로 둔다.
CHANNEL_TAIL = "채널 없이 BM 이 성립한다는 뜻이 아니다."


def channel_limit(card: dict) -> str:
    """채널 한계 문구. **성적표의 채널 축 상태에서 파생한다.**

    옛 구현은 「축_부재 — 측정된 적 없음, 백로그 11」을 고정 문자열로 박아 두었다.
    CHANNEL claim_type 이 신설된 뒤(백로그 17, 2026-08-08)에는 축이 미충족으로 나올 수
    있는데, 그때도 「다루지 않는다」고 말하면 **성적표와 문서가 어긋난다** — 「재지 않았다」와
    「재려다 못 채웠다」를 섞지 말라는 규칙의 위반이고, 조용한 거짓이다.

    뒷문장은 **어느 갈래에서도 떨어지지 않는다.**
    """
    # 채널 축을 못 찾으면 **한계를 선언하는 쪽**으로 떨어진다(아래 축_부재 갈래).
    # 못 찾았다고 「충족」이나 「미충족」으로 넘어가면 그게 fail-open 이다.
    ax = next((a for a in card.get("axes") or [] if a.get("id") == "channel"), None)
    state = (ax or {}).get("state")
    if state == "충족":
        return ("이 BM 문서의 채널 축은 충족이다 — 관측 근거는 성적표 인용에 있다. "
                + CHANNEL_TAIL)
    if state in (None, "축_부재"):
        return ("이 BM 문서는 채널 축을 다루지 않는다(축_부재 — 측정된 적 없음). "
                + CHANNEL_TAIL)
    return (f"이 BM 문서의 채널 축은 «{state}» 다 — 재려다 못 채운 것이지 "
            f"재지 않은 것이 아니다. " + CHANNEL_TAIL)


def _load_report(run_id: str) -> dict:
    # 원장 자리는 둘이다 — `runpath` 가 유일한 답이다(`bm_scorer.load_ledger` 와 같은 결).
    p = os.path.join(_runpath.read_dir(run_id), "result.json")
    res = json.load(io.open(p, encoding="utf-8"))
    return res


def _load_concept() -> dict:
    p = os.path.join(ROOT, "data", "concept.json")
    if not os.path.exists(p):
        return {}
    return json.load(io.open(p, encoding="utf-8"))


def _fmt(v):
    if v is None:
        return "—"
    if isinstance(v, float) and v == int(v):
        v = int(v)
    return f"{v:,}" if isinstance(v, (int, float)) else str(v)


# ══════════════════════════════════════════════════════════════
# 칸 하나씩 — 값에는 반드시 근거가 붙는다
# ══════════════════════════════════════════════════════════════
def build_revenue_structure(res: dict, concept: dict) -> dict:
    """수익 구조 — **값의 나열과 검증 상태.** 평가하지 않는다."""
    rep = res.get("report") or {}
    hn = rep.get("headline_numbers") or []
    rows, gaps = [], []
    for h in hn:
        v = h.get("value")
        if v is None:
            gaps.append({"key": h.get("target"),
                         "evidence": "공백 선언",
                         "why": h.get("why_no_value") or f"{h.get('status')} — 채택값 없음"})
            continue
        rows.append({
            "key": h.get("target"),
            "range": [v[0], v[1]],
            "mid_geometric": (v[0] * v[1]) ** 0.5,
            "badge": h.get("badge"),
            "verification": h.get("status"),
            # 검증 상태 꼬리표는 **값 옆에 붙어 간다** — §1 로 올렸던 것과 같은 이유
            "verification_note": {
                "single_path": "대조 없음 — 교차검증되지 않은 단일 경로",
                "converged": "수렴했으나 두 경로의 독립성은 §3(어떻게 계산했나)에서 확인할 것",
            }.get(h.get("status"), ""),
            "evidence": "성적표 인용 없음 · 원장 report.headline_numbers",
        })
    price = (concept or {}).get("price_hypothesis_krw")
    pricing = {"key": "가격 가설", "value": price,
               "evidence": "사용자 입력(concept.json) — **관측이 아니다**",
               "note": "수집 프롬프트에는 들어가지 않는다(절대규칙 6)"} if price else None
    return {"values": rows, "gaps": gaps, "pricing_hypothesis": pricing}


def build_competition(res: dict) -> dict:
    """경쟁력 — **실명·수치·출처 등급의 나열.** 우열을 매기지 않는다."""
    rep = res.get("report") or {}
    slots = {s["slot_id"]: s for s in (res.get("input", {}).get("slots") or [])}
    rows = []
    for r in rep.get("ledger") or []:
        s = slots.get(r.get("slot_id")) or {}
        if s.get("claim_type") != "COMP":
            continue
        rows.append({
            "name": s.get("subject"), "metric": s.get("metric"),
            "label": r.get("label"), "score": r.get("score"),
            "kind": r.get("kind"), "cross": r.get("cross"),
            "fact_id": r.get("fact_id"), "url": r.get("url"),
            "evidence": f"원장 {r.get('fact_id')}",
        })
    return {"rows": rows,
            "gap": None if any(_fx.filled(x, "bm_layer.build_competition")
                               for x in rows) else
            {"evidence": "공백 선언",
             "why": "확인됨인 경쟁사가 없다 — 실명은 관측됐으나 교차확인 미달"}}


def build_gaps(card: dict, res: dict) -> list:
    """공백 목록 — **「축_부재」와 「미충족」을 구분해서** 적는다."""
    out = []
    for a in card["axes"]:
        if a["state"] == "충족":
            continue
        out.append({"axis": a["name"], "state": a["state"], "why": a["why"],
                    "evidence": "성적표 인용",
                    # 선언 사유는 여기서도 꼬리표를 통째로 들고 간다 ((다) 판정)
                    "declared": a.get("declared") or []})
    nf = (res.get("report") or {}).get("not_found") or {}
    for k in ("independent_topdown_blocked", "empty_slots"):
        v = nf.get(k)
        if not v:
            continue
        # **조용히 자르지 않는다.** 예전에는 `v[0][:160]` 이라 리스트 5원소 중 1개만 나갔고,
        # 하필 "0단계 실측:" 콜론에서 끊겨 «실측 결과가 없다» 로 읽혔다(검수 2026-08-07).
        # 공백을 선언하는 칸이 공백을 감추면 그게 제일 나쁘다.
        items = [str(x) for x in v] if isinstance(v, list) else [str(v)]
        out.append({"axis": f"§7 {k}", "state": "공백",
                    "why": f"원장 원소 {len(items)}개 — 전부 아래에 옮긴다(요약·절단 없음)",
                    "items": items,
                    "evidence": "원장 report.not_found." + k, "declared": []})
    return out


def build_pivot_conditions(card: dict, res: dict) -> list:
    """피벗 **조건문**만 낸다 — if-then. **고르지 않는다**(고르는 일은 피벗 층)."""
    out = []
    for a in card["axes"]:
        if a["state"] == "충족":
            continue
        if a["state"] == "축_부재":
            out.append({"if": f"{a['name']} 축을 측정하기로 결정하면",
                        "then": "슬롯을 먼저 만들어야 한다 — 지금은 성립/불성립을 말할 수 없다",
                        "evidence": "성적표 인용", "blocking": a["why"]})
            continue
        out.append({"if": f"{a['name']} 이 충족으로 바뀌면",
                    "then": f"이 BM 문서의 「{a['name']}」 칸이 공백에서 값으로 바뀐다",
                    "evidence": "성적표 인용", "blocking": a["why"]})
    return out


def build_narrative(card: dict, rev: dict, comp: dict, gaps: list, run_id: str) -> dict:
    """**조립**이다. LLM 을 부르지 않는다 — 계산된 값을 정해진 문형에 끼운다."""
    L = [f"이 문서는 `{run_id}` **단일 원장** 기준이다. 여러 실행을 종합하지 않았다."]
    ok = [a["name"] for a in card["axes"] if a["state"] == "충족"]
    absent = [a["name"] for a in card["axes"] if a["state"] == "축_부재"]
    # **비율로 쓰지 않는다.** 「1/5」로 읽히면 재지 않은 축이 분모에 들어간다.
    # 예전 문형 "충족 축: A / 전체 5축" 은 충족이 1개일 때만 우연히 맞았다(검수 2026-08-07).
    line = (f"충족 축 {len(ok)}개({', '.join(ok) if ok else '없음'})"
            f" · 전체 {len(card['axes'])}축")
    if absent:
        line += (f"(축_부재 {len(absent)} 포함)"
                 " — 비율로 읽지 않는다. 재지 않은 축은 분모가 될 수 없다")
    L.append(line + ".")
    if rev["values"]:
        L.append("수익 구조 값 " + str(len(rev["values"])) + "건 — "
                 + " · ".join(f"{r['key']} {_fmt(round(r['mid_geometric']))}"
                              f"({r['verification']})" for r in rev["values"]) + ".")
    else:
        L.append("수익 구조: **값 없음.** " +
                 "; ".join(f"{g['key']} — {g['why']}" for g in rev["gaps"]) + ".")
    if comp["rows"]:
        L.append("경쟁: " + " · ".join(
            f"{r['name']} {r['label']}({r['score']}점)" for r in comp["rows"]) + ".")
    if comp["gap"]:
        L.append("경쟁 축은 공백이다 — " + comp["gap"]["why"] + ".")
    for g in gaps:
        for d in g.get("declared") or []:
            # (다) 판정 — 요약에서도 꼬리표(선언·근거·만료조건)를 떨어뜨리지 않는다
            L.append(f"{g['axis']} 의 사유 「{d['사유코드']}」는 {d['꼬리표']} · "
                     f"만료조건: {d['만료조건']}")
    L.append(channel_limit(card))
    L.append("이 문서는 **등급을 매기지 않는다.** 축별 사실·공백·조건문만 낸다.")
    return {"text": "\n".join(L), "evidence": f"조립({TEMPLATE_VERSION})"}


def build(run_id: str) -> dict:
    card = bm_scorer.score(run_id)
    res = _load_report(run_id)
    concept = _load_concept()
    rev = build_revenue_structure(res, concept)
    comp = build_competition(res)
    gaps = build_gaps(card, res)
    piv = build_pivot_conditions(card, res)
    return {
        "run_id": run_id,
        "_scope": f"이 문서는 run_id `{run_id}` **단일 원장** 기준이다 (종합 아님).",
        "requirement": "BAF-09-07 — 사업계획서 기반 BM 적합성 · 수익 구조와 경쟁력",
        "gate_summary": {"rules_version": card["rules_version"],
                         "요약": card["요약"], "axes": card["axes"]},
        "revenue_structure": rev,
        "competition": comp,
        "gaps": gaps,
        "pivot_conditions": piv,
        "narrative": build_narrative(card, rev, comp, gaps, run_id),
    }


def render(doc: dict) -> str:
    L = [f"# BM 분석 — {doc['run_id']}", "", f"> {doc['_scope']}",
         f"> 요구 근거: {doc['requirement']}", "",
         "## 1. 게이트 요약", "", "| 축 | 상태 | 사유 |", "|---|---|---|"]
    for a in doc["gate_summary"]["axes"]:
        st = a["state"] + (f" · {a['qualifier']}" if a.get("qualifier") else "")
        L.append(f"| {a['name']} | **{st}** | {a['why']} |")
    L += ["", "## 2. 수익 구조", ""]
    rev = doc["revenue_structure"]
    if rev["values"]:
        L += ["| 항목 | 범위 | 중앙(기하) | 배지 | 검증 상태 |", "|---|---|---|---|---|"]
        for r in rev["values"]:
            L.append(f"| {r['key']} | {_fmt(round(r['range'][0]))} ~ {_fmt(round(r['range'][1]))} "
                     f"| {_fmt(round(r['mid_geometric']))} | {r['badge']} | "
                     f"{r['verification']} — {r['verification_note']} |")
    for g in rev["gaps"]:
        L.append(f"- **{g['key']}: 공백** — {g['why']} ({g['evidence']})")
    if rev["pricing_hypothesis"]:
        p = rev["pricing_hypothesis"]
        L.append(f"- 가격 가설 {_fmt(p['value'])}원 — {p['evidence']} · {p['note']}")
    L += ["", "## 3. 경쟁력", ""]
    if doc["competition"]["rows"]:
        L += ["| 경쟁사 | 지표 | 라벨 | 점수 | 출처 유형 | 근거 |", "|---|---|---|---|---|---|"]
        for r in doc["competition"]["rows"]:
            L.append(f"| {r['name']} | {r['metric']} | {r['label']} | {r['score']} | "
                     f"{r['kind']} | {r['evidence']} |")
    if doc["competition"]["gap"]:
        L.append(f"- **공백** — {doc['competition']['gap']['why']}")
    L += ["", "## 4. 공백", ""]
    for g in doc["gaps"]:
        L.append(f"- **{g['axis']}** ({g['state']}) — {g['why']}")
        for it in g.get("items") or []:          # 원장 원소 = 불릿 1개. 1:1 이다
            L.append(f"  - {it}")
        for d in g.get("declared") or []:
            L.append(f"  - **{d['사유코드']}** — {d['꼬리표']} · 만료: {d['만료조건']}")
    L += ["", "## 5. 피벗 조건문 (조건만 — 고르지 않는다)", ""]
    for p in doc["pivot_conditions"]:
        L.append(f"- **IF** {p['if']} → **THEN** {p['then']}  \n  (막는 것: {p['blocking']})")
    L += ["", f"## 6. 요약 — {doc['narrative']['evidence']}", "",
          doc["narrative"]["text"]]
    return "\n".join(L)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    doc = build(a.run_id)
    print(json.dumps(doc, ensure_ascii=False, indent=2) if a.json else render(doc))


if __name__ == "__main__":
    main()
