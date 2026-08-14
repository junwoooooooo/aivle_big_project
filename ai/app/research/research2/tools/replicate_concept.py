# -*- coding: utf-8 -*-
"""Market Seed 스냅샷 → `concept.json` — **`ResearchConceptFactory.java` 를 그대로 옮긴 것.**

    python tools/replicate_concept.py --seed <스냅샷.json> --concept-id <원장이름표> \
           --constraints <bm constraint_json> --seeds <_경쟁_씨앗.json> --out data/concept_x.json

**왜 있는가 — 유료 수집 앞에서 설계를 무료로 재기 위해서다.**
제품 경로는 `pipeline._collect()` 가 harness → dryrun → collect 를 잇달아 돌리고
중간에 끊을 자리가 없다. 그런데 설계가 나쁜 채로 collect(LLM ≈80회)를 태우면 성적표가
나빠도 그 원인이 「설계」인지 「자료 부재」인지 못 가른다. 그래서 백엔드가 만들 컨셉을
여기서 **똑같이** 만들어 harness(LLM ≤3) 까지만 돌려 본다.

⚠ **이 파일은 Java 의 사본이다.** `ResearchConceptFactory` 가 바뀌면 여기도 바뀌어야 하고,
   갈리면 「관문에서 잰 설계」와 「실제로 돌 설계」가 달라진다 — 그때 이 도구는 거짓말을 한다.
   대응은 `--verify` 로 실제 원장의 `input.concept` 와 대조하는 것이다(수집 뒤에 쓴다).
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
from decimal import Decimal

#: `TwinSurveyStimulusDraftService` 의 세 상수를 그대로 옮긴다.
PLAIN_KRW = re.compile(r"(\d[\d,]*)\s*원")
SCALED_UNIT = re.compile(r"\d\s*[만억조]")
PRICE_MAX = 100_000_000
SAFE_LABEL = re.compile(r"[A-Za-z0-9._-]{1,64}$")
CONSTRAINT_KEYS = ("budget_krw", "months", "team")

SERIES = "C"
SERIES_WHY = ("시장 거래액 × 점유율로 TAM 을 세운다 — 대기업 신사업은 고객이 개인이고 제품을 "
              "사는 구조라, 사업체 수는 공급자를 세는 축이지 제품 시장의 크기가 아니다")


def price_krw(text: str | None) -> int | None:
    """원 단위 정수, 또는 확실히 못 읽으면 None. 「3만원」을 3 으로 읽지 않는다."""
    if text is None or SCALED_UNIT.search(text):
        return None
    found = PLAIN_KRW.search(text)
    if not found:
        return None
    try:
        value = int(found.group(1).replace(",", ""))
    except ValueError:
        return None
    return None if value < 0 or value > PRICE_MAX else value


def _text(node) -> str:
    if node is None:
        return ""
    if isinstance(node, str):
        return node.strip()
    if isinstance(node, (int, float)):
        return str(node).strip()
    return ""


def _merge(*nodes) -> list[str]:
    """문자열이면 한 칸, 배열이면 그대로. 빈 값은 떨어뜨린다."""
    out: list[str] = []
    for node in nodes:
        if node is None:
            continue
        if isinstance(node, list):
            out += [v for v in (_text(i) for i in node) if v]
        else:
            value = _text(node)
            if value:
                out.append(value)
    return out


def build(concept_id: str, snapshot: dict, constraints: dict | None,
          competitor_seeds: dict | None) -> dict:
    concept = snapshot.get("selectedConcept") or {}
    identity = concept.get("identity") or {}
    solution = concept.get("solution") or {}
    operation = concept.get("operation") or {}
    hyp = snapshot.get("finalHypotheses") or {}

    name = _text(identity.get("conceptName"))
    if not concept_id or not SAFE_LABEL.match(concept_id) or not name:
        raise SystemExit("컨셉 이름이 없거나 식별자를 원장 이름으로 쓸 수 없다")
    region = _text((hyp.get("targetRegion") or {}).get("value"))

    target_users = _text(identity.get("targetUsers"))
    # 지역 문구가 이미 들어 있으면 덧붙이지 않는다 — 두 번 나온다.
    target = (target_users if not region or region in target_users
              else (region if not target_users else f"{target_users} ({region})"))

    price_text = _text((hyp.get("price") or {}).get("value"))
    price = price_krw(price_text)

    root: dict = {
        "concept_id": concept_id,
        "name": name,
        "problem": _text(solution.get("problemScenario")),
        "target": target,
        "solution": _text(solution.get("solutionMechanism")),
    }
    if region:
        root["region"] = region
    root["hypotheses"] = []                      # 규칙 6 — 반드시 빈 배열
    root["price_hypothesis_krw"] = price
    root["constraint"] = {k: int(v) for k, v in (constraints or {}).items()
                          if k in CONSTRAINT_KEYS and isinstance(v, int)}

    root["_계열"] = {"계열": SERIES, "왜": SERIES_WHY,
                   "_고정_사유": "계열을 판별하지 않고 C 로 고정한다(제품 결정). "
                               "A 에서 C 로 바꾼 이유는 「식품이라서」가 아니라 "
                               "「채울 수 있어서」다 — T2 는 자리가 다섯이고 T7 은 둘이다. "
                               "⚠ 거래액(GMV) ≠ 매출이다."}
    root["_다듬기5"] = {
        "3_핵심_가치": _text(identity.get("coreValue")),
        "4_업종_분류": {"명칭": _text(identity.get("industryCategory")),
                    "_확인_필요": "KSIC 코드는 확정되지 않았다 — 코드는 드라이런에서 "
                               "stat_code 실재 대조로 확정한다(추측 금지)"}}

    revenue: dict = {"수익_방식": _text((hyp.get("revenueModel") or {}).get("value")),
                     "제안값_krw_월": price, "_확정_가격_원문": price_text}
    if price is None:
        revenue["_왜_숫자가_없나"] = "확정 가격이 원 단위 정수로 깨끗하게 읽히지 않았다"
    channels_text = _text((hyp.get("channels") or {}).get("value"))
    channel: dict = {"제안값": _merge((hyp.get("channels") or {}).get("value"))}
    if channels_text:
        channel["주_채널_가정"] = channels_text
    share = ((hyp.get("preMarketSomShare") or {}).get("value")) or {}
    percent = share.get("targetSharePercent")
    horizon = share.get("horizonYears")
    som: dict = {"가정_침투율": (float(Decimal(str(percent)) / Decimal(100))
                            if isinstance(percent, (int, float)) else None)}
    if isinstance(horizon, int):
        som["가정_기간"] = f"출시 {horizon}년차"
    som["_가정"] = [s.strip() for s in (share.get("assumptions") or [])
                  if isinstance(s, str) and s.strip()]
    som["_지어낸_값_표시"] = ("침투율은 관측 근거가 없는 순수 가정이다(사업안의 AI 제안값). "
                        "근거가 아니라 계산 입력으로만 쓴다")
    root["_hypotheses_v2"] = {
        "6_수익_가격": revenue, "7_채널": channel,
        "8_차별점": {"비교축": [],
                 "_확정_차별점_원문": _text((hyp.get("differentiators") or {}).get("value")),
                 "_왜_비었나": "축 이름을 추측해 넣으면 판정이 지어낸 축을 검증하게 된다"},
        "9_SOM_초기점유": som}

    plan: dict = {}
    rev = _text((hyp.get("revenueModel") or {}).get("value"))
    if rev:
        plan["revenue_model"] = rev
    for key, values in (("channel", _merge((hyp.get("channels") or {}).get("value"))),
                        ("differentiation", _merge((hyp.get("differentiators") or {}).get("value"))),
                        ("key_activities", _merge(operation.get("operatingModel"),
                                                  operation.get("transactionFlow"))),
                        ("key_resources", _merge(operation.get("platformRole"),
                                                 solution.get("featureSet"))),
                        ("key_partners", _merge(operation.get("partnerModel"),
                                                operation.get("partnerRequirements")))):
        if values:
            plan[key] = values
    plan["_출처"] = "사업안(concept portfolio v2)의 확정 가설과 운영 서술에서 파생했다 — 관측이 아니라 서술이다"
    root["_bm_plan"] = plan

    # ⚠ 비어 있으면 **칸 자체를 만들지 않는다** — 빈 블록을 실으면 하네스가 「씨앗이 있다」로
    #    읽고 corp_name 을 요구해 모델이 없는 회사를 지어낸다.
    if competitor_seeds:
        root["_경쟁_씨앗"] = competitor_seeds
    return root


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed", required=True, help="market_analysis_seed_snapshots.snapshot_json")
    ap.add_argument("--concept-id", required=True, help="원장 이름표(portfolio_concept_id)")
    ap.add_argument("--constraints", default="", help="bm_plan_preparations.constraint_json")
    ap.add_argument("--seeds", default="", help="_경쟁_씨앗 블록 JSON")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    load = lambda p: json.load(io.open(p, encoding="utf-8"))       # noqa: E731
    built = build(a.concept_id, load(a.seed),
                  load(a.constraints) if a.constraints else None,
                  load(a.seeds) if a.seeds else None)
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    io.open(a.out, "w", encoding="utf-8").write(json.dumps(built, ensure_ascii=False, indent=1))
    print(f"{a.out} — 최상위 비-언더스코어 키 "
          f"{[k for k in built if not k.startswith('_')]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
