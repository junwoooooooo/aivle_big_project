# -*- coding: utf-8 -*-
"""BM 게이트 채점기 검증 — 서비스 층 1호. **LLM 0회 · 네트워크 0회.**

여기서 지키는 것은 채점 결과가 아니라 **경계**다:
  · 엔진(`blocks/`·`adapters/`)을 import 하지 않는다 — 한 방향 유리벽
  · 판정마다 원장 인용이 붙는다 (예외는 「선언」뿐이고 스스로 밝힌다)
  · 규칙이 참조하는 필드가 원장에 없으면 **판정_불가**(fail-closed)
  · 「미충족」과 「축_부재」를 구분한다 — 측정 안 함이 측정 실패로 둔갑하지 않는다

    python tests/test_bm_scorer.py
"""
from __future__ import annotations
import io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "service"))

import bm_scorer as S

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


RULES = json.load(io.open(os.path.join(ROOT, "rules", "bm_gate.v1.json"), encoding="utf-8"))

# ══════════════════════════════════════════════════════════════
print("[유리벽] 채점기는 엔진을 import 하지 않는다")
src = io.open(os.path.join(ROOT, "service", "bm_scorer.py"), encoding="utf-8").read()
code = "\n".join(l.split("#")[0] for l in src.split("\n") if not l.strip().startswith("#"))
for bad in ("import a_desk", "import b_estimate", "import c_chain", "import a_design",
            "import web", "import kosis", "import dart", "from a_desk", "from blocks"):
    check(f"'{bad}' 없음", bad not in code)
check("원장에 쓰지 않는다 (열기 모드 'w' 없음)", '"w"' not in code and "'w'" not in code)
check("임계치 상수를 코드에 두지 않는다 (규칙 파일에서만)",
      "min_slots" not in code.replace('axis["require"].get("min_slots", 1)', ""))

# ══════════════════════════════════════════════════════════════
print("\n[규칙 파일] 상태값과 선언 결함이 분리돼 있다")
check("states 에 축_부재·판정_불가 가 있다",
      {"충족", "미충족", "축_부재", "판정_불가"} <= set(RULES["states"]))
check("declared_defects 가 axes 와 별도 블록", "declared_defects" in RULES
      and all("declared" not in a for a in RULES["axes"]))
check("선언 결함에 만료조건이 있다",
      all(d.get("만료조건") for d in RULES["declared_defects"]))

# ══════════════════════════════════════════════════════════════
print("\n[답을 아는 문제] gate3-01 원장(재채점 audit-final)")
rep = S.score("audit-final")
st = {a["id"]: a["state"] for a in rep["axes"]}
check("경쟁 실명 = 충족 (R9 passed)", st["competition"] == "충족", st["competition"])
check("채널 = 축_부재 (미충족이 아니다)", st["channel"] == "축_부재", st["channel"])
_ch_why = [a for a in rep["axes"] if a["id"] == "channel"][0]["why"]
# 백로그 17 신설(2026-08-08) 후 사유가 바뀌었다. 「스키마에 CHANNEL 이 없다」는 거짓이 됐고,
# 이 원장이 축_부재인 진짜 이유는 **이 원장에 CHANNEL 슬롯이 0개**라는 것이다.
check("  근거가 «이 원장에 CHANNEL 슬롯이 0개» 를 가리킨다",
      "CHANNEL 슬롯이 0개" in _ch_why, _ch_why)
check("  «재지 않았다» 와 «못 채웠다» 를 구분해 적는다",
      "«못 채웠다»가 아니라 «재지 않았다»" in _ch_why, _ch_why)
check("가격 밴드 = 미충족", st["price_band"] == "미충족", st["price_band"])
price = [a for a in rep["axes"] if a["id"] == "price_band"][0]
check("  관측 사유가 있다 (R7 기반)", "R7" in price["why"], price["why"])
# ⚠ 실제 선언(버그 H · 조회_경로_결함)은 **만료돼 2026-08-08 삭제**됐다(beauty-02 에서
#   DART found≥1 확인). 그래서 여기서는 **합성 선언**으로 메커니즘만 검사한다 —
#   선언이 없어졌다고 「선언은 꼬리표를 달고 나온다」는 방어선까지 지우면 안 된다.
_synthetic = json.load(io.open(os.path.join(ROOT, "rules", "bm_gate.v1.json"), encoding="utf-8"))
_synthetic["declared_defects"] = [{"id": "SYN", "axis": "price_band",
                                   "사유코드": "합성_선언", "근거": "테스트용",
                                   "만료조건": "테스트 전용 — 규칙 파일에 넣지 않는다"}]
_rep2 = S.score("audit-final", rules=_synthetic)
_price2 = [a for a in _rep2["axes"] if a["id"] == "price_band"][0]
check("  선언 사유가 병기된다", any(d["사유코드"] == "합성_선언"
                              for d in _price2.get("declared") or []))
check("  선언은 스스로 「원장 관측 아님」을 밝힌다",
      all("원장 관측 아님" in d["꼬리표"] for d in _price2.get("declared") or []))
check("  현재 규칙에는 만료된 선언이 남아 있지 않다",
      not (json.load(io.open(os.path.join(ROOT, "rules", "bm_gate.v1.json"),
                             encoding="utf-8")).get("declared_defects") or []))

print("\n  판정마다 원장 인용이 붙는다 (충족 축은 반드시)")
for a in rep["axes"]:
    if a["state"] == "충족":
        check(f"  {a['name']} 인용 있음", bool(a["cites"]), str(a["cites"])[:60])

# ══════════════════════════════════════════════════════════════
print("\n[fail-closed] 규칙이 참조하는 필드가 없으면 판정_불가")
led = S.load_ledger("audit-final")
led_no_rule = dict(led, violations={})
a_r9 = [a for a in RULES["axes"] if a["id"] == "competition"][0]
r = S.score_axis(a_r9, led_no_rule, RULES)
check("규칙 판정이 없으면 판정_불가", r["state"] == "판정_불가", r["state"])
check("  조용한 기본값이 아니다", "없다" in r["why"], r["why"])

led_no_cov = dict(led, coverage={})
a_dem = [a for a in RULES["axes"] if a["id"] == "demand_size"][0]
r2 = S.score_axis(a_dem, led_no_cov, RULES)
check("coverage 가 없으면 판정_불가", r2["state"] == "판정_불가", r2["state"])

print("\n  슬롯이 아예 없는 축은 축_부재 (미충족 아님)")
led_no_slot = dict(led, slots=[s for s in led["slots"] if s.get("claim_type") != "PAIN"])
a_pain = [a for a in RULES["axes"] if a["id"] == "demand_pain"][0]
r3 = S.score_axis(a_pain, led_no_slot, RULES)
check("PAIN 슬롯이 없으면 미충족 (absent_state 미선언 축)",
      r3["state"] == "미충족", r3["state"])
check("  channel 은 absent_state 가 선언돼 축_부재",
      S.score_axis([a for a in RULES["axes"] if a["id"] == "channel"][0],
                   led, RULES)["state"] == "축_부재")

# ══════════════════════════════════════════════════════════════
print("\n[대조] 다른 원장은 다른 축이 충족된다 (게이트 표는 여러 실행의 종합이었다)")
rep2 = S.score("report3-04")
st2 = {a["id"]: a["state"] for a in rep2["axes"]}
check("report3-04: 수요·시장크기 충족", st2["demand_size"] == "충족", st2["demand_size"])
check("report3-04: 경쟁 실명 미충족", st2["competition"] == "미충족", st2["competition"])
check("→ 한 원장에 둘 다 있는 실행은 없다",
      st["competition"] == "충족" and st2["demand_size"] == "충족"
      and st["demand_size"] == "미충족" and st2["competition"] == "미충족")

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print("   -", f)
sys.exit(1 if fail else 0)
