# -*- coding: utf-8 -*-
"""캔버스 조립 층 — **서비스 층 5호. 나르기만 한다.**

    python service/canvas.py <run_id> --concept data/concept_beauty-noshow.json --json

유리벽: 엔진 import 0 · 원장 쓰기 0 · LLM 0회.

**이 층은 값을 만들지 않는다.** 각 칸은 셋 중 하나에서만 온다:
  ① 엔진 관측(원장·성적표·보고서) ② 판정 층 도장 ③ 컨셉·입력 제약
어느 칸에서 왔는지를 `원천` 에 적고, 꼬리표(`badge`·`assumption_count`·사유)를
**문자열 그대로** 옮긴다. 요약·재작성 금지.

**판단문을 쓰지 않는다.** 「달성 가능」·「난이도」·「A가 낫다」는 이 층의 어휘가 아니다.
칸에 들어가는 것은 `[내용 + 원천 + 상태]` 이거나 `[공백 + 사유]` 뿐이다.

「못 찾음으로 끝나는 칸 0」은 **관측 실패를 숨기라는 뜻이 아니다.** 관측이 비면
가정이 명시된 추정값으로 채우되 `계산`·`가정` 을 같이 나른다 — 조용히 지어내는 것이
아니라 시끄럽게 계산하는 것이다. 내부 §7 꼬리표는 `못_찾은_것` 칸에 그대로 남는다.
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

import bm_scorer                                    # 같은 서비스 층
import verdict as V

CELLS_PLAN = {
    "고객 관계": ("solution", "컨셉 서술"),
    "핵심 자원": ("solution", "컨셉 서술"),
    "핵심 활동": ("solution", "컨셉 서술"),
    "핵심 파트너": ("_경쟁_씨앗", "컨셉 서술(경쟁·제휴 씨앗)"),
    "비용 구조": ("constraint", "입력 제약(예산·팀·기간)"),
}


def _user_docs(led: dict) -> dict | None:
    """사용자 제공 문서의 **출처와 경계 표시**를 최종 매핑까지 나른다.

    채택된 사실이 0건이어도 «넣었는데 안 붙었다»는 사실 자체가 값이다 — 안 실으면
    「사용자 문서를 안 넣었다」와 구분되지 않는다.
    """
    import glob
    out = []
    for p in sorted(glob.glob(os.path.join(ROOT, "runs", "userdocs-*", "intake_report.json"))):
        r = json.load(io.open(p, encoding="utf-8"))
        out.append({"채널": r.get("channel"), "문서수": len(r.get("docs") or []),
                    "_경계": r.get("_경계"),
                    "슬롯": sorted({d["slot_id"] for d in r.get("docs") or []})})
    if not out:
        return None
    adopted = [c for c in V._confirmed(led, {"PAIN"})]
    return {"적재": out, "채택된_사실": adopted or None,
            "주의": ("적재는 됐으나 채택된 사실이 0건이다 — 「넣지 않았다」와 다르다"
                   if not adopted else None)}


def _trust_rules() -> dict:
    return json.load(io.open(os.path.join(ROOT, "rules", "trust_labels.v1.json"),
                             encoding="utf-8"))


def _grade(row: dict, tr: dict) -> str:
    """원장 한 줄 → 표기 등급. **캔버스는 등급을 만들지 않는다 — 읽어 나른다.**

    판 ㉙ S5 부터 등급의 단일 원천은 **원장 행의 `등급` 필드**(`blocks/a_desk.grade` 가
    `fill.v2.등급표` 로 계산)다. 예전에는 여기서 **점수로 다시 매겼고**, 그것이
    「같은 물음을 두 곳이 각자 푼다」의 일곱 번째가 될 자리였다.

    ⚠ 인용 대조에 실패했거나 격리된 줄은 **관측으로 치지 않는다** — 등급 이전에 재료가 아니다.
    """
    if row.get("label") in ("미검증", "off_slot"):
        return "근거 없음"
    g = row.get("등급")
    if g:
        return g

    # ── 옛 원장 폴백 — 판 ㉙ 이전 원장에는 `등급` 필드가 없다 ──────────────
    #   ⚠ 새 원장에서 이 경로를 타면 `_등급_출처` 가 깨진 것이다.
    sc = row.get("score") or 0
    for lv in tr["levels"]:
        c = lv["조건"]
        if c.get("else") or c.get("from_assumption") or c.get("from_observation"):
            continue
        if sc < c.get("min_score", 0):
            continue
        if "max_score" in c and sc > c["max_score"]:
            continue
        if c.get("require_quote_verified") and row.get("label") == "미검증":
            continue
        return lv["표기"]
    return "근거 없음"


def _trust(led: dict, claim_types: set) -> list | None:
    """그 칸에 걸린 사실들의 등급 표. **점수·출처유형을 반드시 병기한다** —
    등급명만 떼어 읽으면 「실무 신뢰」가 「확정」으로 오독된다(trust_labels `_표기_규칙`).
    """
    tr = _trust_rules()
    slots = {s["slot_id"]: s for s in led["slots"]}      # bm_scorer.load_ledger 의 키
    out = []
    for r in led["report"]["ledger"]:
        s = slots.get(r["slot_id"]) or {}
        if s.get("claim_type") not in claim_types:
            continue
        if r.get("label") in ("off_slot",):
            continue
        row = {"slot_id": r["slot_id"], "fact_id": r["fact_id"],
               "등급": _grade(r, tr), "score": r.get("score"),
               "kind": r.get("kind"), "원장_label": r.get("label"),
               "url": r.get("url")}
        # ── 두 축을 **같은 자리에** 싣는다 (판 ㉙ S5) ────────────────────
        #   `등급` 은 「얼마나 확실한가」이고 `채택` 은 「채워도 되는가」다. 다른 물음이다.
        #   등급만 실으면 읽는 쪽이 「확정인데 왜 안 쓰였지」를 알 수 없고, 그 침묵이
        #   판 ㉘ 의 경계 소실과 같은 모양이 된다 — **경계는 도달한 곳에서만 존재한다.**
        if "채택" in r:
            row["채택"] = r["채택"]
            if r.get("채택_불가_사유"):
                row["채택_불가_사유"] = r["채택_불가_사유"]
            if r.get("등급_근거"):
                row["등급_근거"] = r["등급_근거"]
            row["retrieved_at"] = r.get("retrieved_at")
        # ── 경계를 **값과 같은 자리에** 싣는다 (판 ㉘ 승격) ──────────────
        # 별도 칸에 모아 두면 값만 읽는 수신자에게 안 간다. 판 ㉘ 감사가 찾은 사고가
        # 정확히 그것이다 — 「전사 매출 — 시장 매출 아님」이 **지워진 게 아니라
        # 애초에 최종 매체까지 가는 길이 없었다.**
        # **경계는 쓴 곳이 아니라 도달한 곳에서만 존재한다.**
        for k in ("경계", "경계_proxy"):
            if s.get(k):
                row[k] = s[k]
        if s.get("경계_출처"):
            row["경계_출처"] = s["경계_출처"]
        if s.get("proxy_선언"):
            row["proxy_선언"] = s["proxy_선언"]
        out.append(row)
    return out or None


def _cell(내용, 원천, 상태, **kw):
    c = {"내용": 내용, "원천": 원천, "상태": 상태}
    c.update(kw)
    return c


def _blank(사유, 원천):
    return {"내용": None, "원천": 원천, "상태": "공백", "사유": 사유}


def build(run_id: str, concept_path: str) -> dict:
    led = bm_scorer.load_ledger(run_id)
    concept = V.load_concept(concept_path)
    ver = V.build(run_id, concept_path)
    rep = led["report"]
    head = {x.get("target"): x for x in (rep.get("headline_numbers") or [])}
    cells = {}

    # ── 고객 세그먼트 — 측정(시장 크기 + 성장률) ──────────────
    size = []
    for t in ("TAM", "SAM", "SOM"):
        x = head.get(t)
        if x:
            size.append({"target": t, "value": x.get("value"), "badge": x.get("badge"),
                         "status": x.get("status"), "why_no_value": x.get("why_no_value")})
    # 성장률은 **판정 층 계산**을 그대로 나른다(관측은 엔진, 계산은 판정 층).
    # 예전에는 「TAM/SAM 확인됨 중 단위가 % 인 것」을 성장률로 삼았는데, 그 조건은
    # **아무 % 관측이나 성장률로 만든다** — 세그먼트비중이 %로 관측되면 그것도 걸린다.
    mk = ver["시장_추정"]
    growth = mk.get("성장률_추정") or None
    if growth and growth.get("값") is None:
        growth = None
    tam = mk.get("TAM_추정")
    cells["고객 세그먼트"] = _cell(
        {"대상": concept.get("target"), "시장_크기_엔진": size,
         "시장_크기_추정": {"TAM": tam, "SAM": mk.get("SAM_추정")},
         "성장률": growth or None,
         "성장률_대조_기반": (growth or {}).get("대조_기반"),
         "시장_추정_대조_기반": (tam or {}).get("대조_기반")},
        f"엔진 §2 핵심 숫자 + 판정 층 가정 승격 (run={led['run_id']})",
        "측정 + 추정",
        꼬리표=[f"{s['target']}={s['badge']}/{s['status']}" for s in size],
        계산=(tam or {}).get("식"), 가정=(tam or {}).get("가정"),
        신뢰_등급=_trust(led, {"TAM", "SAM"}),
        SAM_사유=mk.get("SAM_사유"),
        _보존=mk.get("_보존"),
        성장률_표시=(growth or {}).get("표시"),
        성장률_사유=None if growth else ((mk.get("성장률_추정") or {}).get("사유")
                                     or "성장률 확인됨 0건 — 축_부재"))

    # ── 가치 제안 — 측정(수요 근거·경쟁) + 판정(차별점) ───────
    pain = V._confirmed(led, {"PAIN"})
    comp = V._confirmed(led, {"COMP"})
    cells["가치 제안"] = _cell(
        {"핵심_가치": (concept.get("_다듬기5") or {}).get("3_핵심_가치"),
         "수요_근거": pain or None,
         "경쟁": comp or None,
         "차별점_축별": ver["판정"]["8_차별점"]["축"],
         "사용자_제공_문서": _user_docs(led)},
        "엔진 §6 원장(PAIN·COMP) + 판정 층", "측정 + 판정",
        신뢰_등급=_trust(led, {"PAIN", "COMP", "COMPARABLE"}),
        수요_근거_사유=None if pain else "PAIN 확인됨 0건 — 사용자 문서 어댑터 대상",
        꼬리표=[a["도장"] for a in ver["판정"]["8_차별점"]["축"]])

    # ── 채널 — 판정. **축_부재는 두 문장으로 유지한다** ────────
    # 관측이 0건이면 판정 층이 가정 승격 추정을 싣는다. 그때 **경계 두 문장과 가정 목록을
    # 값과 함께** 나른다 — 값만 옮기고 경계를 떨어뜨리면 해외 벤치마크가 국내 관측으로 읽힌다.
    ch = ver["판정"]["7_채널"]
    est = ch.get("추정") or {}
    obs = ch.get("근거") or None
    cells["채널"] = _cell(
        {"가설": ch.get("가설값"), "추정": ch.get("추정"), "관측": obs},
        "② 인풋 가설(7번) + 판정 층", ch["도장"], 사유=ch.get("why"),
        경계=est.get("경계"),
        계산=est.get("식"), 가정=est.get("가정"),
        assumption_count=est.get("assumption_count"),
        # 관측이 없으면 등급은 **추정**이다 — 「확정」·「실무 신뢰」와 섞지 않는다.
        # 그 둘은 관측의 이름이고 이건 계산의 이름이다.
        신뢰_등급=_trust(led, {"CHANNEL"}) or ([{"등급": est["등급"], "원천": "가정 승격",
                                               "assumption_count": est.get("assumption_count"),
                                               "_주의": "관측이 아니다"}] if est else None))

    # ── 수익원 — 측정(가격) + 판정 ────────────────────────────
    pr = ver["판정"]["6_수익_가격"]
    som = ver["판정"]["9_SOM_초기점유"]
    cells["수익원"] = _cell(
        {"수익_방식": (concept.get("_hypotheses_v2") or {}).get("6_수익_가격", {}).get("수익_방식"),
         "가격_가설": pr.get("가설값"), "대체재_밴드": pr.get("밴드"),
         # **밴드 옆에 대조 기반을 같이 나른다.** 값만 옮기면 「3건」이 「3화자」로 읽힌다
         # (판 ⑩: 3건 전부 gongbiz.kr 한 곳이었다).
         "대체재_밴드_대조_기반": pr.get("대조_기반"),
         "매출_추정": som.get("추정")},
        "엔진 §6 원장(PRICE) + §3 계산 + 판정 층", pr["도장"],
        신뢰_등급=_trust(led, {"PRICE", "ALT"}),
        사유=pr.get("why"),
        계산=(som.get("추정") or {}).get("식"),
        가정=(som.get("추정") or {}).get("가정"))

    # ── 계획 칸 5개 — 슬롯 없이 컨셉·제약에서 온다 ─────────────
    for name, (key, src) in CELLS_PLAN.items():
        val = concept.get(key)
        if key == "constraint" and not val:
            cells[name] = _blank("입력 제약이 비어 있다 — 사람이 채우는 칸", src)
        else:
            cells[name] = _cell(val, src, "계획")

    out = {"run_id": led["run_id"], "concept": concept.get("name"),
           "_규칙": "나르기만 한다. 판단문 없음. 상태 어휘는 엔진·판정 층의 것을 그대로 쓴다.",
           "칸": cells,
           "못_찾은_것": rep.get("not_found"),          # §7 그대로. 키 하나도 빼지 않는다
           "틀릴_수_있는_지점": rep.get("falsifiers"),   # §4 — 떨어뜨리면 근거 없이 자신 있는 문서가 된다
           "결론_머리말": rep.get("conclusion")}         # §1
    return out


# ══════════════════════════════════════════════════════════════
# 검사 — 조립 결과가 규칙을 지켰는지 **코드가 본다**
# ══════════════════════════════════════════════════════════════
JUDGMENT_WORDS = ("달성 가능", "달성가능", "난이도", "유망", "매력적", "우수", "낫다",
                  "권장", "추천", "적합하다", "부적합")


def build_from_failure(failure: dict, concept: dict) -> dict:
    """**하네스가 실패했을 때의 canvas** (판 ⑦ H3, `rules/failopen.v1.json`).

    원장이 없다 — 슬롯이 안 만들어졌으니 수집도 없었고 수집이 없으니 원장도 없다.
    그래도 **문서는 나온다.** §0 「어떤 입력에도 출력은 나온다. 실패·부재·한계는 **출력 안의
    표시**로 존재한다」의 가장 밑바닥 경우다.

    ⚠ **성공 canvas 와 겉모습이 같으면 그것이 곧 조용한 실패다.** 그래서 머리말이 첫 줄에
    붙고, 측정 칸의 상태는 상태 어휘 4종이 아니라 **「미생성」**이다 — 「재려다 못 채웠다」와
    **「잴 것을 못 만들었다」**를 섞지 않는다.
    """
    mark = failure.get("canvas_표시") or {}
    상태 = mark.get("상태") or "미생성"
    사유 = mark.get("사유") or "슬롯 생성이 게이트를 통과하지 못했다"
    영향 = set(failure.get("영향_칸") or [])
    cells = {}
    for name in ("고객 세그먼트", "가치 제안", "채널", "수익원"):
        c = _blank(사유 if name in 영향 else
                   "하네스 실패로 이 판에서는 슬롯이 생성되지 않았다", "하네스(실패)")
        c["상태"] = 상태
        c["_영향_직접"] = name in 영향
        cells[name] = c
    for name, (key, src) in CELLS_PLAN.items():
        v = concept.get(key)
        cells[name] = _cell(v, src, "계획") if v else _blank("컨셉에 없음", src)
    return {
        "run_id": None, "concept": concept.get("name"),
        "_규칙": "나르기만 한다. 판단문 없음. 상태 어휘는 엔진·판정 층의 것을 그대로 쓴다.",
        "_하네스_실패": failure,
        "결론_머리말": [mark.get("머리말") or "하네스 실패 — 관측 없이 만들어진 문서다.",
                    f"실패 검사: {[x.get('검사') for x in (failure.get('실패_검사') or [])]}",
                    f"재시도 {failure.get('시도_횟수')}/{failure.get('상한')} 소진."],
        "칸": cells,
        # §7·§4 는 **빈 dict 가 아니라 사유를 담아** 보존한다 — 키만 남기면
        # 「못 찾은 것이 없다」로 읽힌다.
        "못_찾은_것": {"_사유": "수집이 시작되지 않았다 — 슬롯이 생성되지 않아 원장이 없다",
                   "harness_failure": failure.get("실패_검사")},
        "틀릴_수_있는_지점": ["슬롯이 생성되지 않아 **어떤 관측도 시도되지 않았다**",
                       "이 문서의 모든 측정 칸은 값이 없는 것이 아니라 **잴 것이 없었다**"],
    }


def audit(doc: dict) -> dict:
    cells = doc["칸"]
    blob = json.dumps(doc, ensure_ascii=False)
    checks = [
        {"name": "9칸이 다 있다", "passed": len(cells) == 9, "detail": sorted(cells)},
        {"name": "모든 칸에 내용+원천+상태", "passed": all(
            c.get("원천") and c.get("상태") and (c.get("내용") is not None or c.get("사유"))
            for c in cells.values())},
        {"name": "판단문 없음", "passed": not [w for w in JUDGMENT_WORDS if w in blob],
         "detail": [w for w in JUDGMENT_WORDS if w in blob]},
        {"name": "추정 칸에 계산+가정", "passed": all(
            (c.get("계산") and c.get("가정")) or not (c.get("내용") or {}).get("매출_추정")
            for c in cells.values() if isinstance(c.get("내용"), dict))},
        {"name": "§7 못 찾은 것 보존", "passed": doc.get("못_찾은_것") is not None},
        {"name": "§4 틀릴 수 있는 지점 보존", "passed": doc.get("틀릴_수_있는_지점") is not None},
    ]
    return {"passed": all(c["passed"] for c in checks), "checks": checks}


def render(doc: dict) -> str:
    L = [f"# BM 캔버스 매핑 — {doc['concept']} ({doc['run_id']})", "",
         "> 나르기만 한 문서다. 판단문·점수·등급 없음.", "",
         "| 칸 | 상태 | 원천 |", "|---|---|---|"]
    for k, c in doc["칸"].items():
        L.append(f"| {k} | **{c['상태']}** | {c['원천']} |")
    L += ["", "## 못 찾은 것 (§7 그대로)", "```",
          json.dumps(doc["못_찾은_것"], ensure_ascii=False, indent=1)[:1200], "```"]
    return "\n".join(L)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id")
    ap.add_argument("--concept", default="data/concept_beauty-noshow.json")
    ap.add_argument("--json", action="store_true")
    a = ap.parse_args()
    doc = build(a.run_id, a.concept)
    rep = audit(doc)
    print(json.dumps(doc, ensure_ascii=False, indent=2) if a.json else render(doc))
    if not rep["passed"]:
        print("\n조립 검사 실패:", json.dumps(
            [c for c in rep["checks"] if not c["passed"]], ensure_ascii=False))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
