# -*- coding: utf-8 -*-
"""근거 카드 — **LLM 0회.** 원장·판정 층의 값을 카드 한 장씩으로 옮긴다 (판 ㉛).

    python service/cards.py <run_id> --concept data/concept_x.json --json

왜 있는가: 요약층(3번째 LLM 지점)에 **원장을 통째로 주면 안 된다.** 모델이 본문·URL·점수를
보면 거기서 새 숫자를 만들 수 있고, 그러면 「카드 밖 숫자」를 검사로 가를 수가 없다.
카드는 **모델이 볼 수 있는 것의 전부**이며, 그래서 검사가 성립한다.

⚠ **이 파일은 값을 만들지 않는다.** 옮기기만 한다 — 단 하나의 예외가 계산값의 **등급**이고,
그것도 「약한 고리」라는 **규칙의 적용**이지 새 판정이 아니다(`rules/summary.v1.json 계산값_등급`).

한 방향 유리벽: 엔진 import 0 · 원장 쓰기 0 · **LLM 0**.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
sys.path.insert(0, ROOT)

import bm_scorer                                                   # 같은 서비스 층
import verdict as V                                                # 같은 서비스 층
import fillaxis as _fx                                             # 잎 모듈


def _rules() -> dict:
    pins = json.load(io.open(os.path.join(ROOT, "rules", "rule_pins.json"), encoding="utf-8"))
    out = {"summary": json.load(io.open(os.path.join(ROOT, "rules", "summary.v1.json"),
                                        encoding="utf-8"))}
    out["fill"] = json.load(io.open(os.path.join(ROOT, "rules", pins["pins"]["fill"]),
                                    encoding="utf-8"))
    return out


def _ladder(r: dict) -> list:
    return r["summary"]["카드"]["계산값_등급"]["사다리"]


def weakest(grades: list, ladder: list) -> str:
    """**약한 고리가 등급을 정한다.** 재료 중 가장 낮은 등급."""
    known = [g for g in grades if g in ladder]
    if not known:
        return ladder[0]
    return min(known, key=ladder.index)


def merge_bridge_caveats(card: dict, fact: dict) -> dict:
    """다리로 들어온 값의 **상한 울타리를 카드에 싣는다.**

    ⚠ 이 울타리는 **슬롯이 아니라 사실**에 붙어 있다 — 슬롯이 선언한 경계가 아니라
    **어댑터가 다른 이름의 집계로 치환한 결과**이기 때문이다. 위 반복문은 슬롯만 보므로
    여기가 끊겨 있었다: 판 ⑰ 조건 3 은 「경계가 canvas 까지 가야 한다」인데
    사다리 2단이 **판 ㉛A 에서 처음 발동**해(그 전엔 `expected.md` 에 「미도달」) 드러났다.

    경계는 **덮어쓰지 않고 더한다** — 슬롯 경계와 울타리 경계는 다른 사실이다.
    """
    bridges = [b for b in (fact.get("표기_다리") or []) if b.get("상한_울타리")]
    if not bridges:
        return card
    card["상한_울타리"] = True
    card["표기_다리"] = bridges            # 조용한 치환 금지 — 무엇을 무엇으로 바꿨는지
    있던 = card.get("경계")
    모음 = [있던] if isinstance(있던, str) and 있던.strip() else list(있던 or [])
    for b in bridges:
        for x in b.get("경계") or []:
            if x not in 모음:
                모음.append(x)
    card["경계"] = 모음
    return card


def build(run: str, concept: str) -> dict:
    r = _rules()
    ladder = _ladder(r)
    led = bm_scorer.load_ledger(run)
    v = V.build(run, concept)
    slots = {s["slot_id"]: s for s in led["slots"]}

    cards: list[dict] = []

    # ── ① 관측 카드 — 원장 행 하나 = 카드 하나 ──────────────────────
    #    **채택된 행만** 카드가 된다. 채택 못 한 행을 카드로 주면 모델이 그것을
    #    근거처럼 쓰게 되고, 「채우는가」와 「확실한가」를 갈라 놓은 판 ㉙ 이 무의미해진다.
    for row in led["ledger_rows"]:
        if not _fx.filled(row, "verdict._confirmed"):
            continue
        s = slots.get(row["slot_id"]) or {}
        f = (led.get("facts") or {}).get(row.get("fact_id")) or {}
        card = {
            "카드_id": f"C-{row['fact_id']}",
            "종류": "관측",
            "칸": s.get("_canvas_cell") or s.get("claim_type") or "",
            # **이 수가 무엇인가** — 판 ㉛ 실측으로 추가했다.
            #   처음엔 `칸`(= claim_type 폴백)만 줬더니 모델이 「GROWTH」를 읽고
            #   **거래액 2조를 「2024년 성장률」이라고 썼다.** 숫자 출처는 맞아서
            #   검사는 통과했다 — **검사는 출처를 보지 의미를 못 본다**(백로그 65 계열).
            #   고칠 곳은 검사가 아니라 **카드가 주는 정보**다.
            "계량": s.get("metric"),
            "주제": s.get("subject"),
            "기간": s.get("period"),
            "값": f.get("value_num"),
            "단위": f.get("unit_norm") or s.get("unit"),
            "등급": row.get("등급"),
            "등급_근거": row.get("등급_근거"),
            "채택": bool(row.get("채택")),
            "출처_url": row.get("url"),
            "kind": row.get("kind"),
            "조회일": row.get("retrieved_at"),
            "인용": (f.get("quote") or "")[:120],
            "연도": f.get("year"),
            "슬롯": row["slot_id"],
        }
        # 경계는 **값과 같은 자리에** 실어 나른다 — 판 ㉘ 이 값비싸게 배운 것.
        for k in ("경계", "경계_proxy", "proxy_선언", "상한_울타리"):
            if s.get(k):
                card[k] = s[k]
        merge_bridge_caveats(card, f)
        cards.append(card)

    by_id = {c["카드_id"]: c for c in cards}

    # ── ② 계산 카드 — **여기서 계산값 등급을 닫는다** ────────────────
    #    판 ㉙ 의 `grade_monotone` M2 는 검사 대상이 0 이었다. 위반이 없어서가 아니라
    #    **계산값에 등급 표기가 아예 없었기 때문**이다(그 도구가 경고로 그렇게 말한다).
    #    요약층이 등급 없는 숫자를 문장으로 만들면 낮은 등급의 높은 표기가 **문장 형태로**
    #    재발하므로, 요약을 만들기 **전에** 닫는다.
    m = v.get("시장_추정") or {}
    for key, name in (("TAM_추정", "TAM"), ("SAM_추정", "SAM"), ("성장률_추정", "성장률")):
        est = m.get(key)
        if not isinstance(est, dict) or est.get("값") is None:
            continue
        mats, mat_ids = [], []
        for g in (est.get("근거") or []):
            cid = f"C-{g.get('fact_id')}"
            mat_ids.append(cid)
            if cid in by_id:
                mats.append(by_id[cid]["등급"])
        가정 = list(est.get("가정") or [])
        # ── 무엇이 «고리»인가 (판 ㉛ 실측으로 좁혔다) ────────────────────
        #   처음엔 「가정 목록이 비어 있지 않으면 추정」으로 짰다. **값을 열어 보니 과했다.**
        #     TAM  가정 = 「추정점유율 0.3 는 가정이다」        → **지어낸 입력값**
        #     성장률 가정 = 「두 해를 직선으로 이었다」·「과거다」  → **해석 경계**
        #   둘을 같은 무게로 세면 **관측 2건의 산술이 지어낸 0.3 과 동급**으로 깎인다.
        #   기계로 가르는 기준은 산문이 아니라 **입력이 관측으로 뒷받침되는가**다:
        #     입력 칸 수 > 재료 카드 수  →  뒷받침 없는 입력이 있다  →  그것이 고리다.
        #   ⚠ 등급을 **올리는** 방향의 변경이라 특히 조심해 좁혔다 —
        #     뒷받침 없는 입력이 **하나라도** 있으면 추정이다(fail-closed 유지).
        #   ⚠ 경계 문장은 **그대로 카드에 실린다**. 등급 계산에 안 쓸 뿐 사라지지 않는다(§4).
        #   계보: 「약한 고리가 등급을 정한다」의 정밀화이고, 기존 성적표가 이미 성장률을
        #        gov_stat 확정으로 다뤄 왔다(판 ㉓ · CLAUDE.md §7).
        미관측_입력 = max(0, len(est.get("입력") or {}) - len(mat_ids))
        if 미관측_입력:
            mats.append("추정")
        등급 = weakest(mats, ladder)
        cards.append({
            "카드_id": f"C-CALC-{name}",
            "종류": "계산",
            "칸": "고객 세그먼트" if name != "성장률" else "고객 세그먼트",
            "값": est.get("값"),
            "단위": "원" if name in ("TAM", "SAM") else "%",
            "값_퍼센트": est.get("값_퍼센트"),
            "등급": 등급,
            "등급_근거": (f"약한 고리: {sorted(set(mats), key=ladder.index)} → {등급}"
                       + (f" · 뒷받침 없는 입력 {미관측_입력}개" if 미관측_입력 else
                          " · 입력 전부 관측")),
            "미관측_입력_수": 미관측_입력,
            "채택": True,
            "식": est.get("식"),
            "입력": est.get("입력"),
            "재료_카드_id": mat_ids,
            "가정": 가정,
            "약한_고리": 등급,
            "대조_기반": est.get("대조_기반"),
        })

    return {"run_id": run, "concept": v.get("concept"),
            "_규칙": r["summary"]["_한_줄"],
            "_카드는_무엇인가": ("요약층(LLM)이 볼 수 있는 것의 **전부**다. 카드 밖 숫자가 "
                          "문장에 나오면 그것이 지어내기이고 검사가 막는다."),
            "카드": cards}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("run")
    ap.add_argument("--concept", required=True)
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    d = build(a.run, a.concept)
    if a.json:
        print(json.dumps(d, ensure_ascii=False, indent=1))
        return 0
    print(f"[{a.run}] 카드 {len(d['카드'])}장")
    for c in d["카드"]:
        print(f"  {c['카드_id']:<16}{c['종류']:<5}{str(c['등급']):<8}{str(c['값'])[:18]:<20}"
              f"{(c.get('출처_url') or c.get('식') or '')[:52]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
