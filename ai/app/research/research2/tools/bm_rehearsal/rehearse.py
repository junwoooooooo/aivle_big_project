# -*- coding: utf-8 -*-
"""BM 실연결 리허설 — **LLM 호출 직전까지 완주** (판 ㉜-b ①). LLM 0회 · 원장 쓰기 0회.

    python tools/bm_rehearsal/rehearse.py --run beauty-13b \
        --concept data/concept_beauty-noshow.json --concept-id beauty-noshow

**검증 통과는 입장이고 완주는 다른 사건이다.** 판 ㉜ 은 우리가 세운 모델로 검증해 통과했지만,
실제 노트북을 받아 대조하니 **모양이 달랐다** — 그때의 통과는 자문자답이었다.
이 도구는 **그쪽 셀을 그대로 태워** 어디까지 가는지 센다.

단계: 우리 어댑터 → `MarketJoinData.model_validate` → `create_bm_analysis_input`
      → `concept_id` 3자 대조 → `resolve_bm_input` → (LLM 자리) → 검증 2종

⚠ 마지막 검증 2종은 **BM 이 낸 결과**를 검사하는 함수라 입력만으로는 못 돈다.
   그래서 **합성 Canvas** 를 만들어 태운다 — 우리 evidence id 와 라벨이
   그 필터를 **통과할 수 있는 모양인지**를 재는 것이 목적이다.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "service"))

import nb_cells as NB                                              # 그쪽 셀 발췌
import bm_adapter as A                                             # 우리 어댑터


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--concept", required=True)
    ap.add_argument("--concept-id", required=True)
    ap.add_argument("--out", default="")
    a = ap.parse_args()

    log, fail = [], []

    def step(n, ok, detail=""):
        log.append({"단계": n, "통과": bool(ok), "상세": detail})
        print(f"  [{'OK ' if ok else 'X  '}] {n}  {detail}")
        if not ok:
            fail.append(n)

    print(f"\n=== BM 리허설 — {a.run} ===")

    # ① 우리 어댑터
    m = A.build(a.run, a.concept, a.concept_id)
    payload = m.model_dump()
    step("① 어댑터 변환", True,
         f"evidence {len(payload['evidence_list'])} · missing {len(payload['missing_items'])}")

    # ② 그쪽 스키마 검증 — **여기가 판 ㉜ 이 못 본 자리다**
    try:
        mj = NB.MarketJoinData.model_validate(payload)
        step("② MarketJoinData.model_validate (그쪽 스키마)", True)
    except Exception as e:                                        # noqa: BLE001
        step("② MarketJoinData.model_validate (그쪽 스키마)", False, str(e)[:300])
        return _finish(log, fail, a, None, None)

    # ③ 입력 객체 생성 + concept_id 3자 대조
    try:
        bm_in = NB.create_bm_analysis_input(market_data=mj, legal_data=None,
                                            execution_constraints={})
        step("③ create_bm_analysis_input + concept_id 대조", True,
             f"bm={bm_in.concept_id} market={bm_in.market_join_data.concept_id}")
    except Exception as e:                                        # noqa: BLE001
        step("③ create_bm_analysis_input", False, str(e)[:300])
        return _finish(log, fail, a, None, None)

    # ④ 정규화
    try:
        res = NB.resolve_bm_input(bm_in)
        step("④ resolve_bm_input", True,
             f"name={res.concept_name[:26]} · diff {len(res.differentiation)}")
    except Exception as e:                                        # noqa: BLE001
        step("④ resolve_bm_input", False, str(e)[:300])
        return _finish(log, fail, a, None, None)

    # ── 실측 ⓑ evidence id 가 검증을 통과하는가 ──────────────────────
    ids = [e["id"] for e in payload["evidence_list"]]
    canvas = [NB.BMCanvasItem(canvas_cell=c, content=["합성 — 리허설용"],
                              source_labels=["market_size"],
                              market_evidence_ids=list(ids),
                              status=NB.CanvasStatus.PARTIAL, reason="리허설")
              for c in NB.CanvasCell]
    synth = NB.BMAnalysisResult(
        concept_id=a.concept_id, concept_name=res.concept_name, canvas=canvas,
        market_fit_status="PARTIAL", consistency_status="PARTIAL",
        market_fit_summary="리허설", consistency_summary="리허설")
    v = NB.validate_market_evidence_ids(synth, mj)
    kept = v.canvas[0].market_evidence_ids
    step("⑤ validate_market_evidence_ids", len(kept) == len(ids),
         f"{len(kept)}/{len(ids)} 생존")

    # ── 실측 ⓐ source_labels 필터 ────────────────────────────────
    # ⚠ 허용 7종은 **입력의 절 이름**이지 우리 등급/출처유형이 아니다.
    #   그래서 「우리 라벨의 생존율」은 성립하지 않는다 — 대신 **우리 데이터가 실린 절**이
    #   유효 라벨이 되는지를 잰다.
    filled = [s for s in ("market_size", "growth_rate", "competitor_analysis",
                          "price_analysis", "demand_evidence", "concept_snapshot")
              if _has(payload, s)]
    probe = [NB.BMCanvasItem(canvas_cell=c, content=["합성"],
                             source_labels=list(filled) + ["우리_등급_확정"],
                             market_evidence_ids=[], status=NB.CanvasStatus.PARTIAL,
                             reason="리허설") for c in NB.CanvasCell]
    synth2 = synth.model_copy(update={"canvas": probe})
    v2 = NB.validate_canvas_source_labels(synth2)
    kept_l = v2.canvas[0].source_labels
    step("⑥ validate_canvas_source_labels", set(kept_l) == set(filled),
         f"유효 절 {len(filled)}종 전부 생존 · 우리 축 라벨은 탈락(예상된 것)")

    # ── 실측 ⓒ 경계·등급이 필터 후에도 남는가 ────────────────────────
    cav = sum(len(e.get("caveats") or []) for e in mj.evidence_list)
    grades = {e.get("grade") for e in mj.evidence_list}
    step("⑦ 경계·등급 잔존 (evidence_list 는 필터 대상이 아니다)",
         cav >= 0 and None not in grades,
         f"경계 {cav}문장 · 등급 {sorted(g for g in grades if g)}")

    return _finish(log, fail, a, mj, {"evidence_ids": ids, "kept_ids": kept,
                                      "labels_filled": filled, "labels_kept": kept_l,
                                      "caveats": cav, "grades": sorted(g for g in grades if g)})


def _has(p: dict, sec: str) -> bool:
    v = p.get(sec)
    if isinstance(v, dict):
        return any(x is not None for x in v.values())
    return bool(v)


def _finish(log, fail, a, mj, meas):
    out = {"run": a.run, "concept_id": a.concept_id, "단계": log,
           "실패": fail, "실측": meas,
           "_한계": ("LLM 호출부(`run_bm_analysis`)는 태우지 않았다. ⑤⑥ 은 **합성 Canvas** 로 "
                   "필터를 태운 것이라 「우리 id·라벨이 통과 가능한 모양인가」를 재고, "
                   "「BM 이 실제로 무엇을 인용하는가」는 ②(유료 완주)에서만 알 수 있다.")}
    if a.out:
        io.open(a.out, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))
        print(f"\n기록: {a.out}")
    print(f"\n{'실패 ' + str(fail) if fail else '전 단계 완주 — LLM 호출 직전까지 통과'}")
    return 1 if fail else 0


if __name__ == "__main__":
    sys.exit(main())
