# -*- coding: utf-8 -*-
"""하네스 **구조 일치율** 측정 (판 ⑦ H1). **LLM 0회 · 읽기 전용 · 스냅샷 무변경.**

    python tools/harness_agreement.py

무엇을 재는가 — **두 가지를 따로 잰다. 섞으면 안 된다.**

  (가) **골든 대조**   : 승인된 스냅샷(골든) vs 하네스가 새로 만든 초안
  (나) **재생성 안정성**: 같은 컨셉의 초안 vs 초안 (골든이 없어도 잴 수 있다)

(가)는 「하네스가 사람이 승인한 것과 같은 것을 만드는가」이고,
(나)는 「하네스가 돌릴 때마다 같은 것을 만드는가」다. **(나)가 높아도 (가)가 낮으면
자동 전환하면 안 된다** — 매번 똑같이 틀릴 수 있기 때문이다.

**문구 완전일치를 재지 않는다.** subject 서술은 실행마다 흔들리는 것이 정상이고, 그것으로
재면 일치율이 늘 0 근처가 되어 아무것도 못 정한다. 대신 **구조**를 본다:
게이트 통과 여부 · 슬롯 수 · claim_type 구성 · 캔버스 칸 커버리지 · 계량 구성 · 식 구성.

⚠ **이 도구는 저장된 초안만 읽는다.** 새로 생성하지 않으므로 **LLM 비용이 0**이고,
**기존 승인 스냅샷을 건드리지 않는다**(재생성 금지 원칙).
"""
from __future__ import annotations

import collections
import importlib.util
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "harness"))


def _load(p):
    return json.load(io.open(p, encoding="utf-8"))


def _harness():
    spec = importlib.util.spec_from_file_location(
        "slot_harness", os.path.join(ROOT, "harness", "slot_harness.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def signature(slots: list, passed) -> dict:
    """**구조 서명** — 문구가 아니라 뼈대. 여기 없는 것(subject 서술·must_contain 낱말)은
    실행마다 흔들리는 것이 정상이라 일부러 뺐다."""
    return {
        "게이트": bool(passed),
        "슬롯수": len(slots),
        "claim_type": collections.Counter(s.get("claim_type") for s in slots),
        "캔버스칸": collections.Counter(s.get("_canvas_cell") for s in slots),
        "계량": collections.Counter(s.get("metric") for s in slots),
        "식": collections.Counter(s.get("formula_id") for s in slots),
    }


def _bag_agree(a: collections.Counter, b: collections.Counter) -> float:
    """다중집합 일치도 — 겹치는 개수 / 많은 쪽 개수. 개수까지 본다(슬롯이 2개인지 3개인지가 뜻이 다르다)."""
    tot = sum((a | b).values())
    return (sum((a & b).values()) / tot) if tot else 1.0


def agree(x: dict, y: dict) -> dict:
    """항목별 일치도와 그 평균. **항목을 균등 가중**한다 — 어느 하나를 무겁게 두려면
    근거가 있어야 하는데 지금은 없다."""
    parts = {
        "게이트": 1.0 if x["게이트"] == y["게이트"] else 0.0,
        "슬롯수": (min(x["슬롯수"], y["슬롯수"]) / max(x["슬롯수"], y["슬롯수"])
                if max(x["슬롯수"], y["슬롯수"]) else 1.0),
        "claim_type": _bag_agree(x["claim_type"], y["claim_type"]),
        "캔버스칸": _bag_agree(x["캔버스칸"], y["캔버스칸"]),
        "계량": _bag_agree(x["계량"], y["계량"]),
        "식": _bag_agree(x["식"], y["식"]),
    }
    parts["_평균"] = round(sum(parts.values()) / len(parts), 4)
    return {k: (round(v, 4) if isinstance(v, float) else v) for k, v in parts.items()}


def drafts_of(tag: str, vocab: dict, sh) -> list:
    """저장된 초안 → (라벨, 서명). 게이트 통과 여부는 그 판의 `gate.json` 에서 온다."""
    d = os.path.join(ROOT, "runs", "harness", tag)
    gj = os.path.join(d, "gate.json")
    passed = _load(gj).get("passed") if os.path.exists(gj) else None
    out = []
    for n in range(1, 9):
        p = os.path.join(d, f"llm_raw_{n}.json")
        if not os.path.exists(p):
            continue
        slots, _f, _n = sh.wire(_load(p)["data"], vocab)
        # 마지막 초안만 게이트 결과를 안다 — 중간 시도는 «미통과»가 확정이다(통과했으면 멈췄다).
        last = not os.path.exists(os.path.join(d, f"llm_raw_{n + 1}.json"))
        out.append((f"{tag}#{n}", signature(slots, passed if last else False)))
    return out


def main():
    vocab = _load(os.path.join(ROOT, "harness", "vocab.json"))
    sh = _harness()
    hdir = os.path.join(ROOT, "runs", "harness")
    tags = sorted(t for t in os.listdir(hdir)
                  if os.path.isdir(os.path.join(hdir, t)))

    # ── 컨셉 단위로 묶는다. 태그가 여럿이어도 같은 컨셉이면 한 묶음 ──
    concept_of = {
        "beauty-noshow": "미용실", "beauty-p2check": "미용실", "beauty-p2check-g61": "미용실",
        "pilates-member": "필라테스", "pilates-p2check": "필라테스",
        "pilates-p2check-g61": "필라테스",
        "household-ledger": "가계부(B)", "household-ledger-g61": "가계부(B)",
        "pet-treat": "반려간식(C)", "pet-treat-g61": "반려간식(C)",
        "nailrobot-rental": "네일로봇(D)", "nailrobot-rental-g61": "네일로봇(D)",
        "kbeauty-sea": "K뷰티(E)", "kbeauty-sea-g61": "K뷰티(E)",
        "edge-delivery": "양면(경계)", "edge-govbot": "B2G(경계)",
        "edge-adcommunity": "광고(경계)",
    }
    #: 골든 = **사람이 승인한 스냅샷**. 하네스가 자동으로 쓴 것은 골든이 아니다.
    goldens = {"미용실": "data/slots_beauty-noshow.json"}

    by_concept: dict = collections.defaultdict(list)
    for t in tags:
        for lab, sig in drafts_of(t, vocab, sh):
            by_concept[concept_of.get(t, t)].append((lab, sig))

    rep = {"_규칙": "구조 일치율. LLM 0회 · 스냅샷 무변경. 문구가 아니라 뼈대를 본다.",
           "골든_대조": [], "재생성_안정성": [], "분모": {}}

    # (가) 골든 대조
    for cname, path in goldens.items():
        g = _load(os.path.join(ROOT, path))
        gsig = signature(g.get("slots") or [], True)     # 승인됐다 = 통과다
        for lab, sig in by_concept.get(cname, []):
            rep["골든_대조"].append({"컨셉": cname, "골든": path, "초안": lab,
                                  **agree(gsig, sig)})

    # (나) 재생성 안정성 — 같은 컨셉의 **마지막 초안끼리**(중간 시도는 폐기된 것이다)
    for cname, items in by_concept.items():
        finals = [(l, s) for l, s in items if s["게이트"]] or items[-1:]
        # 통과본이 둘 이상이면 그것끼리, 아니면 각 태그의 마지막끼리 본다
        last_by_tag: dict = {}
        for l, s in items:
            last_by_tag[l.split("#")[0]] = (l, s)
        vals = list(last_by_tag.values())
        for i in range(len(vals)):
            for j in range(i + 1, len(vals)):
                rep["재생성_안정성"].append({"컨셉": cname, "A": vals[i][0], "B": vals[j][0],
                                        **agree(vals[i][1], vals[j][1])})
        rep["분모"][cname] = {"태그": len(last_by_tag), "초안": len(items),
                             "골든": cname in goldens}

    def avg(rows):
        return round(sum(r["_평균"] for r in rows) / len(rows), 4) if rows else None

    rep["요약"] = {
        "골든_대조_평균": avg(rep["골든_대조"]),
        "골든_대조_분모": {"컨셉": len(goldens), "초안": len(rep["골든_대조"])},
        "재생성_안정성_평균": avg(rep["재생성_안정성"]),
        "재생성_안정성_분모": {"컨셉": len(by_concept), "쌍": len(rep["재생성_안정성"])},
    }
    out = os.path.join(ROOT, "runs", "harness-agreement")
    os.makedirs(out, exist_ok=True)
    io.open(os.path.join(out, "agreement.json"), "w", encoding="utf-8").write(
        json.dumps(rep, ensure_ascii=False, indent=1, default=dict))

    print("== 골든 대조 (승인 스냅샷 vs 초안) ==")
    for r in rep["골든_대조"]:
        print(f"  {r['초안']:<26} {r['_평균']}  "
              f"게이트{r['게이트']} 슬롯{r['슬롯수']} 계량{r['계량']} 칸{r['캔버스칸']}")
    print("\n== 재생성 안정성 (초안 vs 초안) ==")
    for r in rep["재생성_안정성"]:
        print(f"  {r['컨셉']:<12} {r['A']:<24} {r['B']:<24} {r['_평균']}")
    print("\n== 요약 ==")
    print(json.dumps(rep["요약"], ensure_ascii=False, indent=1))
    print(f"\n산출: {os.path.join(out, 'agreement.json')}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
