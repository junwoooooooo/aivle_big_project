# -*- coding: utf-8 -*-
"""계열×층 매트릭스 — **다섯 계열의 잔여 문을 한 장으로** (판 ⑲ 2부). LLM 0회 · 수집 0회.

    python tools/series_matrix.py

각 계열의 **TAM 슬롯**에 정답 합성 사실을 **원문 층부터** 넣어 어느 층에서 막히는지 센다.
⚠ **주입은 파이프라인 최상류에서** — 이미 파싱된 값을 넣으면 단위층을 건너뛰어
**계기가 진단을 속인다**(판 ⑲ 실측, CLAUDE.md §4 등재).

검사하는 층:

    단위(parse_number) → off_slot 4겹(must_contain·단위·value_range·기간) → 등급
    + **period 축** — 「연 계열을 몇 개 연도로 담는가」. 슬롯이 한 해만 담으면
      **성장률이 구조적으로 불가능**하다(판 ⑲ C 실측). B·D·E 에도 같은 문이 있다.

슬롯 출처: 승인 스냅샷이 있으면 그것, 없으면 **하네스 저장 초안**(게이트 미통과분도 본다 —
통과해야만 볼 수 있으면 「왜 막혔나」의 절반을 못 본다).
"""
from __future__ import annotations

import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "harness")):
    sys.path.insert(0, p)

import a_desk as A4                                              # noqa: E402
from runlog import load_rules                                    # noqa: E402
from schema import Document, Fact, Slot                          # noqa: E402

#: 계열별 대상 — (계열, 컨셉, 슬롯 출처, 정답 원문(숫자, 단위), 본문 표기)
#:   정답값은 **그 계열에서 실제로 관측 가능한 참값 후보**를 쓴다(프로브·드라이런 실측 씨앗).
CASES = [
    ("A", "data/concept_beauty-noshow.json", ("snapshot", "data/slots_beauty-noshow.json"),
     ("115310", "개"), "두발 미용업"),
    ("B", "data/concept_household-ledger.json", ("harness", "household-ledger"),
     ("51712619", "명"), "총인구수"),
    ("C", "data/concept_pet-treat.json", ("snapshot", "data/slots_p12-gate3.json"),
     ("2792575", "백만원"), "애완용품"),
    ("D", "data/concept_nailrobot-rental.json", ("harness", "nailrobot-rental"),
     ("115310", "개"), "두발 미용업"),
    ("E", "data/concept_kbeauty-sea.json", ("harness", "kbeauty-sea"),
     ("2792575", "백만원"), "화장품"),
]


def load_slots(kind: str, ref: str, vocab, concept):
    """승인 스냅샷 또는 하네스 저장 초안에서 슬롯을 얻는다."""
    if kind == "snapshot":
        raw = json.load(io.open(os.path.join(ROOT, ref), encoding="utf-8"))["slots"]
        return [Slot(**{k: v for k, v in s.items() if not k.startswith("_")}) for s in raw]
    import slot_harness as H
    d = os.path.join(ROOT, "runs", "harness", ref)
    for i in (3, 2, 1):
        p = os.path.join(d, f"llm_raw_{i}.json")
        if os.path.exists(p):
            raw = json.load(io.open(p, encoding="utf-8"))
            slots, _f, _n = H.wire(raw["data"], vocab, concept)
            return [Slot(**{k: v for k, v in s.items() if not k.startswith("_")})
                    for s in slots]
    return []


def probe(slot: Slot, num: str, unit: str, body_nm: str, rules: dict, yr: int) -> dict:
    body = (f'{{"C1_NM": "{body_nm}", "ITM_NM": "{slot.metric}", '
            f'"DT": "{num}", "UNIT_NM": "{unit}", "PRD_DE": "{yr}"}}')
    doc = Document(slot_id=slot.slot_id, trace_id="g", url="https://kosis.kr/g",
                   text=body, content_status="usable")
    v, un, _ = A4.parse_number(num, unit, rules["units"])
    if v is None:
        return {"막힌_층": "단위", "사유": f"{num}+{unit} → None"}
    f = Fact(fact_id="F900", slot_id=slot.slot_id, var_id=slot.var_id, trace_id="g",
             url=doc.url, quote=f'"DT": "{num}"', value_num=v, unit_norm=un, year=yr,
             dedup_key="g", match_key="g", quote_verified=True,
             content_status="usable", channel="kosis_api")
    off = A4.off_slot_reason(f, slot, doc, rules)
    if off:
        # 사유 앞머리로 어느 겹인지 가른다 — 「무엇이 막았나」가 매트릭스의 칸이다
        층 = ("must_contain" if "must_contain" in off else
              "단위 불일치" if "단위" in off else
              "value_range" if "값범위" in off else
              "기간" if "기간" in off or "period" in off else "off_slot 기타")
        return {"막힌_층": 층, "사유": off[:90]}
    led = A4.grade([f], {slot.slot_id: slot}, {"g": doc}, rules, yr + 1)
    row = led.rows[0]
    if row.label != "확인됨":
        return {"막힌_층": "등급", "사유": f"label={row.label} score={row.score}",
                "reasons": row.reasons[:2]}
    return {"막힌_층": None, "사유": f"확인됨 {row.score} {row.kind}",
            "기대_밖": bool(getattr(f, "기대_밖", None)),
            "표기_다리": bool(getattr(f, "표기_다리", None))}


def main():
    rules = load_rules()
    vocab = json.load(io.open(os.path.join(ROOT, "harness", "vocab.json"), encoding="utf-8"))
    out = []
    for series, cpath, (kind, ref), (num, unit), body_nm in CASES:
        concept = json.load(io.open(os.path.join(ROOT, cpath), encoding="utf-8"))
        try:
            slots = load_slots(kind, ref, vocab, concept)
        except Exception as e:
            out.append({"계열": series, "오류": f"{type(e).__name__}: {e}"[:120]})
            continue
        tam = [s for s in slots if s.claim_type in ("TAM", "SAM")]
        if not tam:
            out.append({"계열": series, "오류": "TAM·SAM 슬롯 없음", "슬롯수": len(slots)})
            continue
        s0 = tam[0]
        r = probe(s0, num, unit, body_nm, rules, 2024)
        # **period 축** — 연 계열을 몇 개 연도로 담는가. 한 해뿐이면 성장률 구조적 불가.
        years = {str(s.period) for s in slots if s.claim_type in ("TAM", "SAM", "GROWTH")}
        growth = [s for s in slots if s.claim_type == "GROWTH"]
        out.append({"계열": series, "슬롯원천": f"{kind}:{ref}", "슬롯수": len(slots),
                    "TAM슬롯": s0.slot_id, "metric": s0.metric, "subject": s0.subject,
                    "막힌_층": r["막힌_층"], "사유": r.get("사유"),
                    "period_축": {"연도_집합": sorted(years), "GROWTH_슬롯": len(growth),
                                 "성장률_가능": len(years) >= 2 or len(growth) >= 2}})

    p = os.path.join(ROOT, "runs", "series_matrix.json")
    io.open(p, "w", encoding="utf-8").write(json.dumps(
        {"_규칙": "정답을 **원문 층부터** 넣어 어느 층에서 막히는지 센다. "
                 "`막힌_층=None` 이면 그 계열의 TAM 경로는 열려 있다.",
         "행": out}, ensure_ascii=False, indent=1))

    print(f"{'계열':<4}{'슬롯':<5}{'막힌 층':<16}{'성장률':<7}사유")
    for r in out:
        if "오류" in r:
            print(f"{r['계열']:<4}{'-':<5}{'ERR':<16}{'-':<7}{r['오류']}")
            continue
        pa = r["period_축"]
        print(f"{r['계열']:<4}{r['슬롯수']:<5}{str(r['막힌_층'] or '통과'):<16}"
              f"{('가능' if pa['성장률_가능'] else '불가'):<7}{str(r['사유'])[:70]}")
    print(f"\n기록: {p}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
