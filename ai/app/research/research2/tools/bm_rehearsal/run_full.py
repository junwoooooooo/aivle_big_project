# -*- coding: utf-8 -*-
"""BM 완주 1건 + **인용 대조표** (판 ㉜-b ②). 유료 — BM 판정 호출 1회.

    python tools/bm_rehearsal/run_full.py --run beauty-13b \
        --concept data/concept_beauty-noshow.json --concept-id beauty-noshow

무엇을 보는가 — 대조표 5항목(지시서):

  1. **추정 TAM 이 `VERIFIED` 로 둔갑하는가** ← 가장 중요하다. 둔갑하면 「낮은 등급의 높은 표기」
  2. `caveats` 가 최종 문장까지 살아 있는가
  3. `missing_items`(⑦행)가 **공백**으로 읽히는가 **0**으로 읽히는가
  4. 우리 `source_kind` 가 7종 필터와 어떻게 만나는가
  5. `card_id` 형식이 `market_evidence_ids` 검증을 통과하는가

⚠ **둔갑을 발견해도 우리가 고치지 않는다.** 그건 어댑터가 아니라 그쪽 프롬프트 문제이므로
   **관찰 보고로만** 전달한다(지시서 명시). 우리가 손대면 경계가 흐려진다.
"""
from __future__ import annotations

import argparse
import asyncio
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "service"))
sys.path.insert(0, os.path.join(ROOT, "adapters"))

from base import load_env_key                                      # noqa: E402
os.environ.setdefault("OPENAI_API_KEY", load_env_key("OPENAI_API_KEY") or "")

import nb_cells as NB                                              # noqa: E402
import nb_llm as L                                                 # noqa: E402
import bm_adapter as A                                             # noqa: E402


async def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--concept", required=True)
    ap.add_argument("--concept-id", required=True)
    ap.add_argument("--out", default="")
    a = ap.parse_args()

    m = A.build(a.run, a.concept, a.concept_id)
    mj = NB.MarketJoinData.model_validate(m.model_dump())
    bm_in = NB.create_bm_analysis_input(market_data=mj, legal_data=None,
                                        execution_constraints={})
    print(f"BM_MODEL={L.BM_MODEL} · evidence {len(mj.evidence_list)} · 호출 1회")
    try:
        flow = await L.run_bm_pipeline_flow(bm_in)
    except Exception as e:                                         # noqa: BLE001
        rec = {"run": a.run, "상태": "호출 실패", "BM_MODEL": L.BM_MODEL,
               "오류": f"{type(e).__name__}: {e}"[:500],
               "_뜻": ("완주 불가. 모델 접근이 없거나 이름이 다르다 — **우리 어댑터 문제가 아니다.** "
                     "①(LLM 직전까지)은 이미 완주했다.")}
        print(json.dumps(rec, ensure_ascii=False, indent=1))
        if a.out:
            io.open(a.out, "w", encoding="utf-8").write(json.dumps(rec, ensure_ascii=False, indent=1))
        return 2

    fin = flow["final_result"]
    ev = {e["id"]: e for e in mj.evidence_list}
    tam_id = "C-CALC-TAM"
    tam_grade = (ev.get(tam_id) or {}).get("grade")

    rows, cited = [], set()
    for it in fin.canvas:
        cited |= set(it.market_evidence_ids)
        rows.append({"칸": str(it.canvas_cell), "status": str(it.status),
                     "labels": it.source_labels,
                     "evidence": it.market_evidence_ids,
                     "content": it.content[:2],
                     "reason": it.reason[:120]})

    tam_cells = [r for r in rows if tam_id in r["evidence"]]
    둔갑 = [r for r in tam_cells if r["status"] == "VERIFIED"]

    all_cav = [c for e in mj.evidence_list for c in (e.get("caveats") or [])]
    joined = json.dumps(fin.model_dump(), ensure_ascii=False)
    cav_alive = [c for c in all_cav if c[:12] in joined]

    표 = {
        "1_추정TAM_둔갑": {"tam_등급": tam_grade, "인용한_칸": [r["칸"] for r in tam_cells],
                       "VERIFIED_로_둔갑": [r["칸"] for r in 둔갑],
                       "판정": "둔갑 없음" if not 둔갑 else "⚠ 둔갑 — 그쪽 프롬프트 문제(우리가 고치지 않는다)"},
        "2_경계_잔존": {"보낸_경계": len(all_cav), "최종_결과에_남음": len(cav_alive),
                    "사라진_것": [c[:60] for c in all_cav if c not in cav_alive]},
        "3_missing_items": {"보낸_건수": len(mj.missing_items),
                          "최종에_언급": sum(1 for x in mj.missing_items
                                        if str(x.get("item")) in joined)},
        "4_source_labels": {"쓰인_라벨": sorted({l for r in rows for l in r["labels"]}),
                          "허용_7종": sorted(NB.ALLOWED_CANVAS_SOURCE_LABELS)},
        "5_evidence_id": {"보낸_id": len(ev), "인용된_id": sorted(cited),
                        "형식_탈락": sorted(set(cited) - set(ev))},
    }
    out = {"run": a.run, "상태": "완주", "BM_MODEL": L.BM_MODEL,
           "decision": str(fin.decision), "confidence": fin.confidence,
           "canvas": rows, "대조표": 표}
    print(json.dumps(표, ensure_ascii=False, indent=1))
    if a.out:
        io.open(a.out, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))
        print(f"\n기록: {a.out}")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
