# -*- coding: utf-8 -*-
"""판 ㉜ — BM 어댑터 검증. **LLM 0회 · 수집 0회 · 원장 쓰기 0회.**

    python tests/test_step14.py
"""
from __future__ import annotations
import io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "service"), os.path.join(ROOT, "tools")):
    sys.path.insert(0, p)

import bm_adapter as A                                              # noqa: E402

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
        print(f"  OK  {name}")
    else:
        fail.append(f"{name} {detail}")
        print(f"  X   {name} {detail}")


print("\n[1] 상수·계약 형태")
# ⚠ 판 ㉜ 은 «우리가 이해한 계약»으로 검증해 통과했고 그것은 **자문자답**이었다.
#    판 ㉜-b 에서 실제 노트북을 받아 대조하니 모양이 달랐다. 이제 실물을 따른다.
check("price_base 라벨이 MEDIAN_PROVISIONAL", A.PRICE_BASE_LABEL == "MEDIAN_PROVISIONAL")
f = A.MarketJoinData.model_fields
for k in ("concept_id", "concept_snapshot", "market_size", "growth_rate",
          "competitor_analysis", "price_analysis", "demand_evidence",
          "market_size_calculation", "missing_items", "evidence_list"):
    check(f"  MarketJoinData.{k}", k in f)
check("market_size 는 extra 금지", A.MarketSizeData.model_config.get("extra") == "forbid")
check("price_analysis.price_base 는 float (문자열 라벨 자리가 아니다)",
      A.PriceAnalysisData.model_fields["price_base"].annotation == (float | None))
check("⑦행이 정식 입력이고 **list** 다",
      "missing_items" in f and "list" in str(f["missing_items"].annotation))

print("\n[2] 어댑터는 값을 만들지 않는다 — LLM 0 · 원장 쓰기 0")
src = io.open(os.path.join(ROOT, "service", "bm_adapter.py"), encoding="utf-8").read()
body = "\n".join(l for l in src.splitlines() if not l.strip().startswith("#"))
check("LLM 호출 0", not re.findall(r"openai|OpenAI|responses\.create", body))
check("원장 쓰기 0", "run.jsonl" not in body and "result.json" not in body)
eng = re.findall(r"^\s*(?:from|import)\s+(a_desk|a_design|b_estimate|c_chain|blocks|run)\b",
                 body, re.M)
check("엔진 계산 모듈 import 0", not eng, str(eng))

print("\n[3] 경계는 **전부** 모인다 — 하나라도 빠지면 도달하지 않은 것이다")
c = {"카드_id": "C-1", "종류": "관측", "경계": "전사 매출 — 시장 매출 아님.",
     "상한_울타리": True, "경계_proxy": "⚠ 대리 관측(proxy) — 사업체를 센 것이다.",
     "proxy_선언": {"대상": "사업체 수", "사유": "장비 제공자로 대리한다"}}
cav = A._caveats(c)
check("경계 3종 + proxy 선언이 모두 실린다", len(cav) >= 3, str(len(cav)))
check("  경계 본문", any("시장 매출 아님" in x for x in cav))
check("  proxy 경계", any("대리 관측" in x for x in cav))
check("  proxy 선언(대상·사유)", any("proxy 선언" in x and "사유" in x for x in cav))
check("경계 없는 카드는 빈 목록", A._caveats({"카드_id": "C-2"}) == [])

print("\n[4] 견본 2건 — 그쪽 진입점 형태로 검증")
for run, con, cid in (("beauty-13b", "data/concept_beauty-noshow.json", "beauty-noshow"),
                      ("pet-treat-18", "data/concept_pet-treat.json", "pet-treat")):
    if not os.path.exists(os.path.join(ROOT, "runs", run, "result.json")):
        continue
    m = A.build(run, con, cid)
    A.MarketJoinData.model_validate(m.model_dump())
    check(f"{run} model_validate 통과", True)
    check(f"  {run} concept_id echo", m.concept_id == cid, m.concept_id)
    check(f"  {run} price_base 성격이 계산 절에 실린다",
          m.market_size_calculation.get("price_base_kind") == A.PRICE_BASE_LABEL)
    check(f"  {run} evidence ≥1", len(m.evidence_list) >= 1, str(len(m.evidence_list)))
    check(f"  {run} 모든 근거에 등급", all(e.get("grade") for e in m.evidence_list))
    # ⚠ **키는 `id` 다.** `card_id` 로 두면 노트북 validate_market_evidence_ids 에서 전멸한다.
    check(f"  {run} evidence 키가 `id`", all("id" in e for e in m.evidence_list))
    # 계산 근거는 **약한 고리**를 달고 온다 — 등급 없는 계산값이 나가면 안 된다(판 ㉙ 이월분)
    calc = [e for e in m.evidence_list if e.get("kind") == "계산"]
    check(f"  {run} 계산 근거에 식", all(e.get("formula") for e in calc) if calc else True)
    check(f"  {run} ⑦행 비어 있지 않다", bool(m.missing_items))

print("\n[5] concept_id 는 **echo** 다 — 우리가 만들지 않는다")
check("build 가 concept_id 를 인자로 받는다",
      "concept_id" in A.build.__code__.co_varnames)
m2 = A.build("pet-treat-18", "data/concept_pet-treat.json", "임의-외부-id-999")
check("받은 값을 그대로 돌려준다", m2.concept_id == "임의-외부-id-999", m2.concept_id)

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for x in fail:
    print(" 실패:", x)
sys.exit(1 if fail else 0)
