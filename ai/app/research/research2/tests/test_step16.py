# -*- coding: utf-8 -*-
"""판 ㉛ B단계 — 발췌 입력을 **앞에서 자르지 않고 골라 담는다.** LLM 0회 · 네트워크 0회.

    python tests/test_step16.py

왜: `paid31a-hmr` 실측에서 본문 도달률이 **16.2%** 였다(230만 자 중 37만 자).
모자란 게 아니라 **앞 20,000자에 값이 없어서**다 — 요금표·통계표·시장 보고서의 숫자는
보통 중반 이후에 나온다. 상한을 올리면 토큰만 배로 들고 큰 문서에서는 비율이 그대로다.

잣대를 어디까지 공유하나 (중요):
  · **닻은 공유한다** — 계량 낱말·주제 낱말 위치. `tools/extract_triage.py` 가 쓰는 것과 같다.
  · **판정 조건은 공유하지 않는다** — triage 의 잣대는 「조건을 만족하는 값이 있는가」를
    **엄격히** 가리는 것이라(주제+단위+범위 전부 요구) 그대로 쓰면 표현이 다른 문서를
    통째로 버린다. 창은 **거르는 게 아니라 고르는 것**이므로 그 신호들을 **순위**로 쓴다.
"""
from __future__ import annotations
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import doc_window as W                                            # noqa: E402
from runlog import load_rules                                     # noqa: E402
from schema import Slot                                           # noqa: E402

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
        print(f"  OK  {name}")
    else:
        fail.append(f"{name} {detail}")
        print(f"  X   {name} {detail}")


RULES = load_rules()
CFG = (RULES["adapters"]["web"] or {}).get("extract_window") or {}

#: ⚠ **배송 기본값은 꺼짐**이다(측정했고 켤 근거가 안 나왔다 — 규칙 파일 `_왜_껐나`).
#:   그래서 동작 검사는 **명시적으로 켜서** 한다. 기본값을 그대로 쓰면 기본값이 바뀔 때마다
#:   검사의 뜻이 조용히 바뀐다 — 「끄면 꺼진다」만 확인하고 통과해 버린다.
def _rules(enabled: bool, radius: int | None = None) -> dict:
    w = {**CFG, "enabled": enabled}
    if radius:
        w["radius"] = radius
    return {**RULES, "adapters": {**RULES["adapters"],
                                  "web": {**RULES["adapters"]["web"], "extract_window": w}}}


ON = _rules(True)

SLOT = Slot(slot_id="S1", var_id="V1", formula_id="F", claim_type="TAM",
            subject="냉동 간편식", metric="거래액", period="2024", unit="원",
            region="대한민국", value_range=[1_000_000_000, 100_000_000_000_000])

#: 값이 **문서 뒤쪽**에 있는 본문. 앞자르기가 지는 판을 만든다.
NOISE = "가나다라마바사아자차카타파하 " * 900          # ≈ 13,500자
EARLIER = "냉동 간편식 시장 개관. 표는 아래에 있다. "      # 닻은 있으나 값이 없다
ANSWER = "2024년 냉동 간편식 거래액은 34,805,394백만원으로 집계됐다. "
#: 답은 **뒤쪽**(27,000자쯤)에 있고, 앞쪽에는 값 없는 닻이 하나 더 있다.
#: 창이 둘 나와야 「원문 순서대로 잇는가」를 잴 수 있다.
BIG = NOISE + EARLIER + NOISE + ANSWER + NOISE


print("\n[1] 규칙 — 값은 규칙 파일에서 온다 (절대규칙 7)")
check("extract_window 가 규칙 파일에 있다", bool(CFG), str(CFG)[:80])
check("  enabled 가 값이다", isinstance(CFG.get("enabled"), bool))
check("  radius 가 값이다", isinstance(CFG.get("radius"), int) and CFG["radius"] > 0)

print("\n[2] 짧은 문서는 **건드리지 않는다**")
short = "냉동 간편식 거래액은 1,234억원이다."
text, log = W.select(short, SLOT, ON, 20000)
check("원문 그대로", text == short)
check("  기록이 「자를 것이 없었다」라고 말한다", log.get("mode") == "whole", str(log))

print("\n[3] 긴 문서 — 뒤쪽에 있는 답을 **가져온다**")
text, log = W.select(BIG, SLOT, ON, 20000)
check("예산을 안 넘는다", len(text) <= 20000, len(text))
check("**답이 들어 있다**", "34,805,394" in text, log.get("mode"))
check("  창 방식으로 골랐다고 기록한다", log.get("mode") == "window", str(log))
check("  창 개수가 값으로 남는다", isinstance(log.get("windows"), int) and log["windows"] >= 1)
check("  버린 양이 값으로 남는다 (「우리가 버렸다」와 「자료가 없다」를 가른다)",
      isinstance(log.get("chars_dropped"), int) and log["chars_dropped"] > 0, str(log))

head = BIG[:20000]
check("⚠ 앞자르기였다면 못 찾았을 답이다 (이 검사가 무의미하지 않다는 증명)",
      "34,805,394" not in head)

print("\n[4] **예산을 다 쓴다** — 자리만 바꾸는 것이지 양을 줄이는 게 아니다")
# ⚠ 실측(win-on r=1500)에서 창 방식이 앞자르기보다 글자를 **20% 적게** 보냈다.
#   그러면 대조가 「좋은 자리 vs 나쁜 자리」가 아니라 「적은 입력 vs 많은 입력」이 된다 —
#   실제로 결과가 입력량을 따라 단조롭게 나빠졌다. 창은 **같은 예산**을 써야 한다.
for 예산 in (20000, 8000):
    t, lg = W.select(BIG, SLOT, ON, 예산)
    check(f"예산 {예산}: 상한을 안 넘는다", len(t) <= 예산, len(t))
    check(f"  예산 {예산}: 95% 이상 채운다 (앞자르기와 같은 양)",
          len(t) >= 예산 * 0.95, f"{len(t)}/{예산}")
    check(f"  예산 {예산}: 답은 여전히 들어 있다", "34,805,394" in t)

print("\n[5] 순서와 재현성")
t2, _ = W.select(BIG, SLOT, ON, 20000)
check("같은 입력이면 같은 출력 (실행마다 안 갈린다)", t2 == text)
check("창이 둘 이상 뽑혔다", log.get("windows", 0) >= 2, str(log))
check("창은 **원문 순서대로** 이어 붙인다 (앞 구간이 먼저)",
      text.find("표는 아래에 있다") < text.find("34,805,394"), str(log))
check("  건너뛴 자리를 표시한다 (앞뒤를 한 문장으로 잇지 않게)", "중략" in text)

print("\n[6] 닻이 없으면 **앞자르기로 돌아간다** — 조용히 빈손을 만들지 않는다")
쓸모없는 = "ZZZZ " * 6000
text3, log3 = W.select(쓸모없는, SLOT, ON, 1000)
check("빈손이 아니다", len(text3) == 1000, len(text3))
check("  폴백을 기록한다", log3.get("mode") == "head_fallback", str(log3))

print("\n[7] 끄면 예전 그대로 (되돌리기 가능)")
off = {**RULES, "adapters": {**RULES["adapters"],
                             "web": {**RULES["adapters"]["web"],
                                     "extract_window": {**CFG, "enabled": False}}}}
text4, log4 = W.select(BIG, SLOT, off, 20000)
check("앞에서 자른다", text4 == BIG[:20000])
check("  기록이 head 라고 말한다", log4.get("mode") == "head", str(log4))

print("\n[8] 잣대 — 닻은 triage 와 **같은 곳**을 본다")
check("계량 낱말이 닻이다", "거래액" in W.anchors_of(SLOT))
check("주제 낱말도 닻이다", any("냉동" in a or "간편식" in a for a in W.anchors_of(SLOT)))
check("한 글자 낱말은 닻이 아니다 (triage 와 같은 규율)",
      all(len(a) >= 2 for a in W.anchors_of(SLOT)), str(W.anchors_of(SLOT)))

print(f"\n{'='*54}\n통과 {ok} · 실패 {len(fail)}")
sys.exit(1 if fail else 0)
