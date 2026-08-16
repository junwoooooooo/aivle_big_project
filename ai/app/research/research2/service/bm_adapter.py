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

#: 캔버스 «계획 5칸»의 재료가 실리는 자리 — 컨셉의 `_bm_plan` → `concept_snapshot`.
#:
#: 앞 넷은 `ConceptSnapshot` 의 **명명 필드**이고, 뒤 넷은 `extra="allow"` 로 얹는
#: **확장 필드**다. 확장이 정상 경로인 이유:
#:   · `bm/prompt.py` 는 노트북에서 기계 추출한 담당자 계약이라 우리가 못 고친다.
#:   · 그런데 그 프롬프트는 `concept_snapshot` 의 **필드명을 열거하지 않는다** —
#:     「concept_snapshot 또는 execution_constraints에 명시된 활동만 정리한다」뿐이다.
#:   · `bm/analyze.py` 가 `ResolvedBMInput` 을 통째로 dump 하고 거기 `market_join_data`
#:     가 들어 있어, 확장 필드는 그대로 모델에 도달한다.
#: 그래서 계약 사본(`bm/contracts.py`)을 건드리지 않고 계획 칸을 채울 수 있다.
#: ⚠ 표를 두 군데 두지 않는다. 키를 늘릴 일이 생기면 **여기만** 고친다.
PLAN_FIELDS = ("revenue_model", "channel", "differentiation",
               "key_activities", "key_resources", "key_partners",
               "customer_relationship")


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
    # ⚠ `-X utf8` 은 **장식이 아니다.** 없으면 자식이 한글 JSON 을 CP949 로 뱉다 죽고,
    #   부모의 stderr 디코딩까지 터져 `out.stderr` 가 **None** 이 된다 — 그러면 실패가
    #   `TypeError: 'NoneType' object is not subscriptable` 로 둔갑해 원인을 못 찾는다.
    #   `tools/scorecard.py::_run` 에서 이미 한 번 고친 병이고, 여기 사본이 남아
    #   `test_step14` §4 를 막고 있었다(베낀 조회는 갈라진다).
    out = subprocess.run([sys.executable, "-X", "utf8", "-m", "service.canvas", run,
                          "--concept", concept, "--json"],
                         cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        raise SystemExit(f"canvas 생성 실패:\n{out.stderr[-1200:]}")
    return json.loads(out.stdout)


def _verdict(run: str, concept: str) -> dict:
    out = subprocess.run([sys.executable, "-X", "utf8", "service/verdict.py", run,
                          "--concept", concept, "--json"],
                         cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        raise SystemExit(f"verdict 실패:\n{out.stderr[-1200:]}")
    return json.loads(out.stdout)


CAVEAT_KEYS = ("경계", "경계_proxy", "상한_울타리")

#: `상한_울타리` 는 bool 표식이라 문장이 필요하다. 정본 문장은 계약층
#: (`ai/app/research/serialize.py::_CEILING_SENTENCE`) 과 **같은 말**이어야 한다 —
#: 캔버스와 payload 가 같은 울타리를 다르게 말하면 사람이 둘을 다른 사실로 읽는다.
CEILING_SENTENCE = "⚠ 상한 울타리 — 이 값은 **상한으로만** 읽어야 한다(상위 집계를 밑동으로 썼다)."


def _caveats(c: dict) -> list:
    """카드가 든 경계를 **전부** 모은다. 하나라도 빠지면 도달하지 않은 것이다(§4)."""
    out = []
    for k in CAVEAT_KEYS:
        v = c.get(k)
        if not v:
            continue
        if k == "상한_울타리":
            # bool 표식이다 — 그대로 넣으면 캔버스에 `"True"` 가 실린다(판 ㉛A 실측).
            out.append(CEILING_SENTENCE)
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


#: 컨셉 생성 산출물(`ConceptCandidateResult`) → 계획 칸 재료. 정본 표는
#: `docs/architecture/AS_BUILT_ARCHITECTURE.md` 에 있고 **여기가 그 표의 구현**이다.
#: 값이 여럿인 칸은 이어 붙이지 않고 **목록으로** 넘긴다 — 한 문장으로 뭉치면 모델이
#: 그것을 한 항목으로 읽는다.
_CONCEPT_TO_PLAN = {
    "revenue_model": ("revenueModel",),
    "channel": ("channels",),
    "differentiation": ("differentiators",),
    "key_activities": ("operatingModel", "transactionFlow"),
    "key_resources": ("platformRole", "featureSet"),
    "key_partners": ("partnerModel", "partnerRequirements"),
    # ⚠ `customer_relationship` 은 **의도적으로 비운다.** 컨셉 스키마에 대응 필드가 없고,
    #   `solutionMechanism` 에서 유추하면 `bm/prompt.py` §5(「입력에 명시된 것만」)를
    #   어긴다. 필드 신설이 정답이고 그건 별건이다 — 그때까지 이 칸은 정직하게 빈다.
}


#: 사용자가 BM 앞 화면에서 채운 칸. **컨셉 계약이 주지 않는 것들**이다 —
#: 입구계약서 §1 의 선택 필드에 활동·자원·파트너·고객 관계가 없다.
USER_PLAN_KEY = "_user_bm_plan"


def plan_material_of(con: dict) -> dict:
    """계획 5칸의 재료. **사용자 입력 > 견본 `_bm_plan` > 컨셉 파생** 순이다.

    견본 컨셉 파일은 `_bm_plan` 을 손으로 들고 있지만 그것은 **계약 밖의 스텁**이고,
    컨셉 생성 담당자에게 요구하는 값이 아니다. 그래서 제품에서는 이 넷을 화면이 받는다.

    ⚠ **사용자가 이긴다.** 예전에는 `_bm_plan` 이 최우선이라, 견본 컨셉에서 사용자가 같은
      칸을 채워도 **조용히 무시**됐다. 사람이 방금 쓴 것이 파일의 스텁에 지는 것은
      「입력을 받았다」는 화면의 약속을 깨는 일이다.

    ⚠ **지어내지 않는다.** 원 필드가 없으면 그 칸은 비운 채로 둔다 — 빈 칸은 결함이
      아니라 「입력에 없다」는 사실이고, 그 사실이 화면에 그대로 서야 한다.
    """
    plan = dict(con.get("_bm_plan") or {})
    for key, value in (con.get(USER_PLAN_KEY) or {}).items():
        if value:
            plan[key] = value
    for key, sources in _CONCEPT_TO_PLAN.items():
        if plan.get(key):
            continue                                  # 이미 들고 있으면 그대로 둔다
        merged = []
        for name in sources:
            value = con.get(name)
            if isinstance(value, (list, tuple)):
                merged.extend(str(x).strip() for x in value if str(x).strip())
            elif isinstance(value, str) and value.strip():
                merged.append(value.strip())
        if merged:
            # 단일 문자열 칸은 목록이 아니라 문장 하나여야 한다(계약의 타입이 그렇다).
            plan[key] = merged[0] if key in ("revenue_model",) else merged
    return plan


def _snapshot(con: dict) -> ConceptSnapshot:
    """컨셉 → `concept_snapshot`. **계획 5칸이 여기서 재료를 받는다.**

    ⚠ `differentiation` 을 `con["hypotheses"]` 에서 가져오지 않는다. 그 필드는 절대 규칙 6
      때문에 **비어 있어야 하고**(`Concept.research_view()` 가 수집 프롬프트로 그대로
      넘긴다), 실제로 비어 있다. 거기서 읽으면 차별점이 **항상 `[]`** 다 — 조용히.
      실내용은 컨셉의 `_bm_plan` 에 있고, `_` 키라 수집에는 넘어가지 않는다.
    """
    plan = plan_material_of(con)
    fine = con.get("_다듬기5") or {}
    extra = {k: plan[k] for k in PLAN_FIELDS[3:] if plan.get(k)}
    return ConceptSnapshot(
        concept_name=con.get("name"), target_customer=con.get("target"),
        problem=con.get("problem"), solution=con.get("solution"),
        # 예전엔 solution 을 그대로 복사했다 — 같은 문장이 두 칸에 있으면 둘 다 신호가 없다.
        core_value=fine.get("3_핵심_가치") or con.get("solution"),
        differentiation=[str(x) for x in (plan.get("differentiation") or [])][:6],
        revenue_model=plan.get("revenue_model"),
        channel=plan.get("channel"),
        **extra)


def execution_constraints_of(con: dict) -> dict:
    """컨셉의 `constraint` → BM 입력의 `execution_constraints`. **비용 구조 칸의 유일한 원천.**

    없으면 `{}` 다 — 지어내지 않는다. 그때 모델은 프롬프트 §8 대로 `content=[]` 를 낸다.
    ⚠ 값은 정수로 유지한다(CLAUDE.md §5-2: task input 에 부동소수점 금지).
    """
    raw = con.get("constraint")
    return dict(raw) if isinstance(raw, dict) else {}


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
        concept_snapshot=_snapshot(con),
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
