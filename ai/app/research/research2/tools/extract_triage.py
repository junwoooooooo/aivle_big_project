# -*- coding: utf-8 -*-
"""발췌 실패 3분류 — **저장 문서만 읽는다.** LLM 0회 · 수집 0회 (판 ⑭ ①).

    python tools/extract_triage.py --run pet-treat-03 --skip S1,S3

무엇을 가르나:
    (a) 문서에 값이 **실제로 있다** — 발췌가 못 읽었다
    (b) 문서에 값이 **없다** — 회수된 문서가 질의와 불일치
    (c) 값도 없고 불일치도 아니다 — 자료 실제 부재 **후보**

**사람 눈으로 「있어 보인다」를 쓰지 않는다.** 슬롯이 실제로 요구하는 것
(`must_contain` · 계량 낱말 · 단위 · `value_range`)을 **코드로** 대조하고,
맞은 자리의 **문자 오프셋과 앞뒤 문맥**을 근거로 첨부한다.
**대조에 쓴 잣대를 산출물에 같이 적는다** — 잣대가 안 보이면 분류를 검증할 수 없다.

⚠ (c) 를 「부재 확정」이라 부르지 않는다. **이 표본은 회수된 문서뿐**이고, 웹 전체가 아니다.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)

import runpath                                          # noqa: E402

#: 숫자 — 천단위 쉼표·소수점·퍼센트 허용
_NUM = re.compile(r"\d[\d,]*(?:\.\d+)?")


def _nums(text: str):
    for m in _NUM.finditer(text):
        raw = m.group(0)
        try:
            yield m.start(), raw, float(raw.replace(",", ""))
        except ValueError:
            continue


def triage(slot: dict, docs: list[tuple[str, str]]) -> dict:
    """슬롯 하나. docs = [(trace_id, 본문)]."""
    mc = [w for w in (slot.get("must_contain") or []) if w]
    vr = slot.get("value_range") or []
    unit = slot.get("unit") or ""
    # 계량 낱말 — 「사업체 수」처럼 띄어쓴 계량은 어절로도 찾는다(본문 표기가 흔들린다)
    met_words = [w for w in re.split(r"\s+", slot.get("metric") or "") if len(w) >= 2]
    # ⚠ **주제도 같은 창 안에 있어야 한다.** 1차 잣대는 계량 낱말만 봤고, 그 결과
    #   S10 이 「감사관 페이지의 3918」로, S9 가 「펫로스 애도 기간 16.3%」로 (a) 판정됐다 —
    #   **잣대가 느슨하면 「발췌가 못 읽었다」가 부풀고, 발췌가 옳게 거절한 것을 결함으로 만든다.**
    #   가장 **긴**(=가장 구체적인) subject 낱말을 요구한다. 「시장」·「용품」 같은 흔한
    #   2자 낱말로는 주제를 못 가른다(실측: 「온라인 시장의 점유율」이 걸렸다).
    subj_words = sorted((w for w in re.split(r"[\s·/]+", slot.get("subject") or "")
                         if len(w) >= 2), key=len, reverse=True)
    subj_key = subj_words[0] if subj_words else ""

    hits, mc_hit, num_in_range = [], set(), []
    for tid, text in docs:
        for w in mc:
            if w in text:
                mc_hit.add(w)
        # 계량 낱말 근처에 범위 안 숫자가 있는가 — 「값이 실제로 있다」의 코드 정의
        for w in met_words:
            for m in re.finditer(re.escape(w), text):
                lo, hi = max(0, m.start() - 260), min(len(text), m.start() + 260)
                window = text[lo:hi]
                for off, raw, val in _nums(window):
                    if vr and not (vr[0] <= val <= vr[1]):
                        continue
                    if unit and unit not in window:
                        continue
                    if subj_key and subj_key not in window:
                        continue          # 주제가 없으면 그 숫자는 이 슬롯의 값이 아니다
                    hits.append({"trace_id": tid, "계량낱말": w, "값": raw,
                                 "오프셋": m.start() + off - (m.start() - lo),
                                 "문맥": re.sub(r"\s+", " ", window)[:150]})
                    num_in_range.append(val)
                    break
                if hits and hits[-1]["trace_id"] == tid:
                    break

    if hits:
        cls, why = "a", "문서에 슬롯 조건을 만족하는 값이 실재한다 — 발췌가 못 읽었다"
    elif mc and not mc_hit:
        cls, why = "b", (f"`must_contain` {mc} 중 본문에 나타난 것이 0개 — "
                         f"회수된 문서가 이 슬롯의 주제가 아니다")
    elif not any(w in t for _, t in docs for w in (met_words or ["\0"])):
        cls, why = "b", "계량 낱말이 어느 문서에도 없다 — 질의-문서 불일치"
    else:
        cls, why = "c", ("계량 낱말은 있으나 슬롯 조건(단위·value_range)을 만족하는 값이 "
                         "없다 — 자료 실제 부재 **후보**")
    return {"slot_id": slot["slot_id"], "claim_type": slot.get("claim_type"),
            "metric": slot.get("metric"), "subject": slot.get("subject"),
            "분류": cls, "why": why,
            "_잣대": {"must_contain": mc, "value_range": vr, "unit": unit,
                     "계량낱말": met_words,
                     "주제_열쇠": subj_key,
                     "_규칙": ("계량 낱말 ±260자 창 안에 **주제 열쇠 + 단위 + value_range 안의 숫자**가 "
                              "모두 있을 것. 주제를 안 보면 남의 숫자가 걸린다(1차 잣대의 실패)")},
            "문서수": len(docs), "must_contain_적중": sorted(mc_hit),
            "근거": hits[:3]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--skip", default="", help="무효 표본 slot_id (쉼표)")
    a = ap.parse_args()
    skip = {x.strip() for x in a.skip.split(",") if x.strip()}

    # ⚠ 원장은 **두 자리**에 있다 — 씨앗 `runs/` 와 수집이 만든 `runs-generated/`.
    #   여기가 `runs/` 만 보고 있어서 새로 수집한 판을 「없다」고 했다(`tavily_intake`·
    #   `scorecard` 에서 이미 한 번씩 고친 것과 같은 병). 답은 `runpath` 한 곳이다.
    d = runpath.read_dir(a.run)
    res = json.load(io.open(os.path.join(d, "result.json"), encoding="utf-8"))
    bodies = json.load(io.open(os.path.join(d, "a3_bodies.json"), encoding="utf-8"))
    slots = {s["slot_id"]: s for s in res["input"]["slots"]}

    by_slot: dict = {}
    found_ok = set()
    for line in io.open(os.path.join(d, "run.jsonl"), encoding="utf-8"):
        if not line.strip():
            continue
        x = json.loads(line)
        if x["node"] == "a3_document":
            p = x["payload"]
            by_slot.setdefault(p["slot_id"], []).append(
                (p["trace_id"], bodies.get(p["trace_id"], "")))
        elif x["node"] == "a3_finding" and x["payload"]["status"] == "found":
            found_ok.add(x["payload"]["slot_id"])

    rows = []
    for sid, slot in slots.items():
        if sid in found_ok or sid in skip:
            continue
        rows.append(triage(slot, by_slot.get(sid, [])))

    out = {"_규칙": ("발췌 실패 3분류. **저장 문서만** 읽는다(LLM 0 · 수집 0). "
                   "(c) 는 **부재 후보**이지 확정이 아니다 — 표본은 회수된 문서뿐이다."),
           "run": a.run, "제외": sorted(skip), "판정_대상": len(rows),
           "분류_집계": {k: sum(1 for r in rows if r["분류"] == k) for k in "abc"},
           "행": rows}
    p = os.path.join(d, "extract_triage.json")
    io.open(p, "w", encoding="utf-8").write(json.dumps(out, ensure_ascii=False, indent=1))

    print(f"판정 대상 {len(rows)}건 (제외 {sorted(skip)}) · 집계 {out['분류_집계']}\n")
    for r in rows:
        print(f"  [{r['분류']}] {r['slot_id']:<4} {r['metric']:<12} 문서{r['문서수']:>2} · {r['why'][:70]}")
        for h in r["근거"][:1]:
            print(f"        근거 {h['trace_id']} @{h['오프셋']} 값={h['값']} · …{h['문맥'][:90]}…")
    print(f"\n기록: {p}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
