# -*- coding: utf-8 -*-
"""판 ㉛ — 근거 카드 + 요약층(3번째 LLM 지점) 검증. **LLM 0회 · 수집 0회.**

    python tests/test_step13.py
"""
from __future__ import annotations
import io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "service"), os.path.join(ROOT, "tools")):
    sys.path.insert(0, p)

import cards as C                                                   # noqa: E402
import summary_check as SC                                          # noqa: E402

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
        print(f"  OK  {name}")
    else:
        fail.append(f"{name} {detail}")
        print(f"  X   {name} {detail}")


print("\n[1] LLM 지점 등재 — 지점 없는 호출은 무인 검증 밖이다")
R = SC.rules()
pts = {p["이름"]: p for p in R["llm_지점"]["지점"]}
check("지점 3개 등재 (SEARCH·EXTRACT·SUMMARY)",
      set(pts) == {"SEARCH", "EXTRACT", "SUMMARY"}, str(sorted(pts)))
for n, p in pts.items():
    # 하네스·발췌와 **같은 형식**이어야 한다 — 형식이 다르면 대조가 안 된다
    for k in ("이름", "블록", "위치", "입력", "출력", "검사", "못_만드는_것"):
        check(f"  {n}.{k}", bool(p.get(k)))
s = pts["SUMMARY"]
check("SUMMARY 는 숫자·등급·판정을 못 만든다",
      {"숫자", "등급", "판정"} <= set(s["못_만드는_것"]), str(s["못_만드는_것"]))
check("SUMMARY 입력은 카드뿐 (원장·본문 접근 없음)", "카드" in s["입력"])

print("\n[2] 요약층 유리벽 — 원장 쓰기 0 · 엔진 계산 모듈 import 0")
src = io.open(os.path.join(ROOT, "service", "summary.py"), encoding="utf-8").read()
body = "\n".join(l for l in src.splitlines() if not l.strip().startswith("#"))
eng = re.findall(r"^\s*(?:from|import)\s+(a_design|a_desk|b_estimate|c_chain|blocks|run)\b",
                 body, re.M)
check("엔진 계산 모듈 import 0", not eng, str(eng))
# `--out` 은 산출물이지 원장이 아니다. 원장 디렉터리에 쓰는 코드가 없어야 한다.
check("원장(run.jsonl·result.json) 쓰기 0",
      "run.jsonl" not in body and "result.json" not in body)
csrc = io.open(os.path.join(ROOT, "service", "cards.py"), encoding="utf-8").read()
check("카드 생성은 LLM 0회",
      not re.findall(r"openai|OpenAI|responses\.create", csrc))

print("\n[3] 계산값 등급 — **약한 고리가 등급을 정한다** (판 ㉙ 이월분 마감)")
ladder = R["카드"]["계산값_등급"]["사다리"]
check("사다리 4단", ladder == ["근거 없음", "추정", "실무 신뢰", "확정"], str(ladder))
check("확정 + 추정 → 추정", C.weakest(["확정", "추정"], ladder) == "추정")
check("확정 + 실무 신뢰 → 실무 신뢰", C.weakest(["확정", "실무 신뢰"], ladder) == "실무 신뢰")
check("확정만 → 확정", C.weakest(["확정", "확정"], ladder) == "확정")
check("빈 재료 → 최하단 (fail-closed)", C.weakest([], ladder) == "근거 없음")

print("\n[4] 오염 반례 — **막혀야 하는 것이 막히는가** (통과 사례만으론 증명이 안 된다)")
for g in SC.GOLDEN:
    bad = SC.check(g.get("카드") or SC.GOLDEN_CARDS, [g["문장"]], R)
    got = "통과" if not bad else "막힘 · " + bad[0]["검사"]
    check(f"  {g['id']} → {g['기대']}", got == g["기대"], f"실측 {got}")
ids = {g["id"] for g in SC.GOLDEN}
check("반례에 «카드 밖 숫자» 가 있다", "poison_number_not_in_card" in ids)
check("반례에 «해석 문장» 이 있다", "poison_interpretation" in ids)
check("반례에 «경계 누락» 이 있다", "poison_boundary_dropped" in ids)

print("\n[5] 검사끼리 모순 없음 — 경계를 베끼라 해 놓고 그 안의 수를 막지 않는다")
card = {"카드_id": "C-X", "종류": "관측", "값": 65.0, "단위": "%",
        "등급": "확정", "경계": "외식업 214곳 표본이다 — 미용업 직접 통계가 아니다."}
sent = [{"문장": "노쇼 피해 경험률은 65%다(외식업 214곳 표본이다 — 미용업 직접 통계가 아니다.).",
         "카드_id": ["C-X"]}]
check("경계를 그대로 실은 문장은 통과", not SC.check([card], sent, R),
      str(SC.check([card], sent, R)))

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
