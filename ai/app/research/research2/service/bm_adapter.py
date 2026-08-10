# -*- coding: utf-8 -*-
"""BM 어댑터 — `canvas.json` + 근거 카드 → **`MarketJoinData`** (판 ㉜ · ㉜-b 개정). LLM 0회.

    python service/bm_adapter.py <run_id> --concept data/concept_x.json \
        --concept-id beauty-noshow --out outputs/marketjoin_<run_id>.json

⚠ **판 ㉜-b 에서 통째로 다시 썼다.** 판 ㉜ 은 상대 스키마가 저장소에 없어서 **우리가 이해한 계약**을
pydantic 으로 세우고 그것으로 검증했다. 그 뒤 실제 노트북(`bm_pipeline_v1_final_actual_input.ipynb`)을
받아 대조하니 **모양이 상당히 달랐다.** 그때의 「검증 통과」는 **자기 모델에 대한 자문자답**이었다.

    판 ㉜ (우리 추정)              실제 계약
    ─────────────────────────────────────────────────────────────
    evidence_list[].card_id       evidence_list[].**id**  ← 이게 다르면 `market_evidence_ids`
                                                            검증에서 **전부 탈락**한다
    missing_items: dict           missing_items: **list[dict]**
    price_base: "MEDIAN_..."(str) price_analysis.price_base: **float**
    (없음)                        concept_snapshot · competitor_analysis ·
                                  demand_evidence · market_size_calculation **필수**
    (자유)                        market_size·growth_rate·price_analysis 는 **extra="forbid"**

**정본은 노트북이다.** 이 파일은 그 스키마를 그대로 따른다 — 어긋나면 **이 파일만** 고친다.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import subprocess
import sys

from pydantic import BaseModel, Field

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, ROOT)

import cards as CARDS                                              # 같은 서비스 층

#: 가격 대표값의 **성격 표시**. 실제 계약의 `price_analysis.price_base` 는 **float** 라
#: 이 문자열을 거기 넣을 수 없다(`extra="forbid"`). 그래서 **값은 float 로 넣고,
#: 「그 값이 무엇인지」는 `market_size_calculation` 과 evidence 의 `grade_reason` 에 싣는다.**
#: ⚠ 이름만 옮기고 뜻을 잃으면 잠정 대표값이 확정 단가로 읽힌다.
PRICE_BASE_LABEL = "MEDIAN_PROVISIONAL"

#: 그쪽이 허용하는 Canvas 출처 라벨 7종(노트북 `ALLOWED_CANVAS_SOURCE_LABELS`).
#: ⚠ **우리 `source_kind`(gov_stat 등)와 다른 축이다.** 이것은 «입력의 어느 절에서 왔나»이고
#:   우리 등급은 «얼마나 확실한가»다. 둘을 같은 것으로 읽으면 대조표가 거짓이 된다.
ALLOWED_SOURCE_LABELS = ("concept_snapshot", "market_size", "growth_rate",
                         "competitor_analysis", "price_analysis",
                         "demand_evidence", "execution_constraints")


# ══════════════════════════════════════════════════════════════
# 계약의 사본 — **노트북 셀 6 을 그대로 옮긴 것**. 값을 바꾸지 않는다.
# ══════════════════════════════════════════════════════════════
class ConceptSnapshot(BaseModel):
    concept_name: str | None = None
    target_customer: str | None = None
    problem: str | None = None
    solution: str | None = None
    core_value: str | None = None
    differentiation: list[str] = Field(default_factory=list)
    revenue_model: str | None = None
    channel: str | list[str] | None = None
    model_config = {"extra": "allow"}


class MarketSizeData(BaseModel):
    tam: float | None = None
    sam: float | None = None
    som: float | None = None
    unit: str | None = None
    model_config = {"extra": "forbid"}


class GrowthRateData(BaseModel):
    value: float | None = None
    unit: str | None = None
    model_config = {"extra": "forbid"}


class PriceAnalysisData(BaseModel):
    price_min: float | None = None
    price_base: float | None = None
    price_max: float | None = None
    currency: str | None = None
    model_config = {"extra": "forbid"}


class MarketJoinData(BaseModel):
    concept_id: str
    concept_snapshot: ConceptSnapshot
    market_size: MarketSizeData
    growth_rate: GrowthRateData
    competitor_analysis: list[dict]
    price_analysis: PriceAnalysisData
    demand_evidence: list[dict]
    market_size_calculation: dict
    missing_items: list[dict] = Field(default_factory=list)
    evidence_list: list[dict] = Field(default_factory=list)


# ══════════════════════════════════════════════════════════════
def _canvas(run: str, concept: str) -> dict:
    out = subprocess.run([sys.executable, "-m", "service.canvas", run,
                          "--concept", concept, "--json"],
                         cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        raise SystemExit(f"canvas 생성 실패:\n{out.stderr[-1200:]}")
    return json.loads(out.stdout)


def _verdict(run: str, concept: str) -> dict:
    out = subprocess.run([sys.executable, "service/verdict.py", run,
                          "--concept", concept, "--json"],
                         cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        raise SystemExit(f"verdict 실패:\n{out.stderr[-1200:]}")
    return json.loads(out.stdout)


CAVEAT_KEYS = ("경계", "경계_proxy", "상한_울타리")


def _caveats(c: dict) -> list:
    """카드가 든 경계를 **전부** 모은다. 하나라도 빠지면 도달하지 않은 것이다(§4)."""
    out = []
    for k in CAVEAT_KEYS:
        v = c.get(k)
        if not v:
            continue
        for t in (v if isinstance(v, list) else [v]):
            if str(t).strip():
                out.append(str(t))
    pd = c.get("proxy_선언")
    if isinstance(pd, dict) and (pd.get("사유") or pd.get("대상")):
        out.append(f"proxy 선언 — 대상 {pd.get('대상')} · 사유 {pd.get('사유')}")
    return out


def _evidence(c: dict) -> dict:
    """카드 → `evidence_list` 항목.

    ⚠ **키 이름은 `id` 다.** 노트북 `validate_market_evidence_ids` 가 `item["id"]` 를 읽는다 —
      `card_id` 로 두면 허용 id 집합이 **비어** 모든 `market_evidence_ids` 가 조용히 탈락한다.
      판 ㉜ 이 정확히 그 상태였다(우리 모델로만 검증했으므로 안 보였다).
    """
    return {
        "id": c["카드_id"],
        "kind": c["종류"],
        "metric": c.get("계량"), "subject": c.get("주제"), "period": c.get("기간"),
        "value": c.get("값"), "unit": c.get("단위"),
        # ↓ 우리 축. 그쪽 `source_labels` 와 **다른 축**이다(등급 vs 입력 절).
        "grade": c.get("등급"), "grade_reason": c.get("등급_근거"),
        "source_url": c.get("출처_url"), "source_kind": c.get("kind"),
        "retrieved_at": c.get("조회일"), "quote": c.get("인용") or None,
        "caveats": _caveats(c),
        "formula": c.get("식"), "inputs": c.get("입력"),
        "material_ids": list(c.get("재료_카드_id") or []),
        "assumptions": list(c.get("가정") or []),
    }


def build(run: str, concept: str, concept_id: str) -> MarketJoinData:
    """CLI 진입점용 껍데기 — 재료를 모아 `build_from` 에 넘긴다.

    ⚠ `_canvas`·`_verdict` 는 **서브프로세스**라 인터프리터를 2회 더 띄우고
      `SystemExit` 을 부를 수 있다. 서버(async 핸들러)에서는 그것을 못 잡는다 —
      그래서 in-process 경로(`build_from`)를 갈라 두었다(판 ㉝).
    """
    return build_from(_canvas(run, concept), _verdict(run, concept),
                      CARDS.build(run, concept),
                      json.load(io.open(os.path.join(ROOT, concept), encoding="utf-8")),
                      run, concept_id)


def build_from(cv: dict, vd: dict, cd: dict, con: dict,
               run: str, concept_id: str) -> MarketJoinData:
    """**서브프로세스 0 · 파일 읽기 0.** 이미 만들어진 재료만 받아 조립한다.

    서버는 이 경로를 쓴다 — 파이프라인이 canvas·verdict·cards 를 이미 메모리에 들고 있다.
    """
    cards = cd["카드"]
    by_ct = {}
    for c in cards:
        by_ct.setdefault(c.get("칸") or "", []).append(c)

    ev = [_evidence(c) for c in cards]
    m = vd.get("시장_추정") or {}
    tam, sam = m.get("TAM_추정") or {}, m.get("SAM_추정") or {}
    gr = m.get("성장률_추정") or {}

    # ── 경쟁·수요·가격 — **카드에서만** 온다(값을 새로 만들지 않는다) ──────
    comp = [_evidence(c) for c in cards
            if (c.get("칸") in ("COMP", "COMPARABLE") or c.get("계량") in ("매출액", "가입 매장 수"))]
    demand = [_evidence(c) for c in cards
              if c.get("칸") == "PAIN" or c.get("계량") == "문제 경험률"]
    prices = sorted(float(c["값"]) for c in cards
                    if c.get("칸") == "PRICE" and isinstance(c.get("값"), (int, float)))
    pa = PriceAnalysisData(currency="KRW")
    if prices:
        mid = prices[len(prices) // 2] if len(prices) % 2 else \
            (prices[len(prices) // 2 - 1] + prices[len(prices) // 2]) / 2
        pa = PriceAnalysisData(price_min=prices[0], price_base=mid,
                               price_max=prices[-1], currency="KRW")

    # ⑦행 — 실제 계약은 **list[dict]** 다. dict 로 주면 `model_validate` 가 죽는다.
    missing = [{"item": k, "detail": v} for k, v in (cv.get("못_찾은_것") or {}).items() if v]

    calc = {
        "tam": tam.get("식"), "tam_inputs": tam.get("입력"),
        "tam_grade": next((e["grade"] for e in ev if e["id"] == "C-CALC-TAM"), None),
        "tam_assumptions": tam.get("가정") or [],
        "sam": sam.get("식") if isinstance(sam, dict) else None,
        "growth": gr.get("식"), "growth_grade":
            next((e["grade"] for e in ev if e["id"] == "C-CALC-성장률"), None),
        # `price_base` 의 성격은 **여기에** 싣는다 — `price_analysis` 는 extra 를 금지한다.
        "price_base_kind": PRICE_BASE_LABEL,
        "price_base_note": ("잠정 대표값(관측 표시가격의 중앙값)이다. **확정 단가가 아니다.** "
                            "관측 건수가 적으면 중앙값은 대표성이 없다."),
        "_등급_읽는_법": ("계산값 등급은 **약한 고리**를 따른다 — 가정이 섞이면 `추정` 이다. "
                     "`VERIFIED`·확정으로 승격하면 「낮은 등급의 높은 표기」가 된다."),
        "_경계": ("evidence_list[].caveats 를 떨어뜨리지 마라 — 값과 함께 옮겨야 하는 문장이다."),
    }

    return MarketJoinData(
        concept_id=concept_id,
        concept_snapshot=ConceptSnapshot(
            concept_name=con.get("name"), target_customer=con.get("target"),
            problem=con.get("problem"), solution=con.get("solution"),
            core_value=con.get("solution"),
            differentiation=[str(h) for h in (con.get("hypotheses") or [])][:6],
            revenue_model=None, channel=None),
        market_size=MarketSizeData(tam=tam.get("값"),
                                   sam=sam.get("값") if isinstance(sam, dict) else None,
                                   som=None, unit="KRW"),
        growth_rate=GrowthRateData(value=gr.get("값_퍼센트"), unit="%/년"),
        competitor_analysis=comp,
        price_analysis=pa,
        demand_evidence=demand,
        market_size_calculation=calc,
        missing_items=missing,
        evidence_list=ev,
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("run")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--concept-id", required=True,
                    help="입구에서 받은 concept_id — **echo 한다**")
    ap.add_argument("--out", default="")
    a = ap.parse_args()
    m = build(a.run, a.concept, a.concept_id)
    js = json.dumps(m.model_dump(), ensure_ascii=False, indent=1)
    if a.out:
        io.open(a.out, "w", encoding="utf-8").write(js)
    print(f"[{a.run}] concept_id={m.concept_id} · evidence {len(m.evidence_list)} "
          f"· 경계 {sum(len(e['caveats']) for e in m.evidence_list)}문장 "
          f"· 경쟁 {len(m.competitor_analysis)} · 수요 {len(m.demand_evidence)} "
          f"· missing {len(m.missing_items)}건 · price_base={m.price_analysis.price_base}")
    if a.out:
        print(a.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
