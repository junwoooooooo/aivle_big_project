# -*- coding: utf-8 -*-
"""BM 층 검증 — 서비스 층 2호. **LLM 0회 · 네트워크 0회.**

여기서 지키는 것은 문장이 아니라 **규율**이다:
  · 엔진 import 0 · 원장 쓰기 0 · **LLM 호출 0** (narrative 는 조립이지 생성이 아니다)
  · 등급을 매기지 않는다 (종합 점수·적합/부적합 없음)
  · 값 칸마다 근거(trace_id / 성적표 인용 / 공백 선언)
  · **선언 사유가 출력 어디에 나오든 꼬리표(선언·근거·만료조건)가 동반된다** ((다) 판정)
  · 채널 한계 문구는 **뒷문장까지** 나온다 — 침묵이 승인으로 읽히지 않게
  · **원장 하나 → 문서 하나.** 종합하지 않는다

    python tests/test_bm_layer.py
"""
from __future__ import annotations
import io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "service"))

import bm_layer as L

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


SRC = io.open(os.path.join(ROOT, "service", "bm_layer.py"), encoding="utf-8").read()
CODE = "\n".join(l.split("#")[0] for l in SRC.split("\n") if not l.strip().startswith("#"))

# ══════════════════════════════════════════════════════════════
print("[유리벽] 엔진 import 0 · 원장 쓰기 0 · LLM 호출 0")
for bad in ("import a_desk", "import b_estimate", "import c_chain", "import a_design",
            "import web", "import kosis", "import dart", "from blocks"):
    check(f"'{bad}' 없음", bad not in CODE)
for bad in ("openai", "OpenAI", "responses.create", "Meter", "prompts"):
    check(f"LLM 흔적 '{bad}' 없음", bad not in CODE)
check("원장에 쓰지 않는다", '"w"' not in CODE and "'w'" not in CODE)

print("\n[등급 금지] 종합 점수·적합/부적합을 만들지 않는다")
doc = L.build("audit-final")
flat = json.dumps(doc, ensure_ascii=False)
for bad in ('"total_score"', '"verdict"', '"grade"', '"적합"', '"부적합"'):
    check(f"{bad} 없음", bad not in flat)
check("narrative 꼬리표가 '조립' 이다 (거짓 '생성' 아님)",
      "조립" in doc["narrative"]["evidence"] and "생성" not in doc["narrative"]["evidence"],
      doc["narrative"]["evidence"])

# ══════════════════════════════════════════════════════════════
print("\n[audit-final] 기대값")
comp = {r["name"] + "/" + str(r["label"]): r for r in doc["competition"]["rows"]}
c5 = [r for r in doc["competition"]["rows"] if r["label"] == "확인됨"]
check("경쟁: 코케비즈 확인됨 5점 official_page", bool(c5)
      and c5[0]["name"] == "코케비즈" and c5[0]["score"] == 5
      and c5[0]["kind"] == "official_page", str(c5[:1]))
check("  근거에 fact_id 가 붙는다", bool(c5) and c5[0]["evidence"].startswith("원장 F"))
check("수익 구조: headline 이 비어 공백 선언", not doc["revenue_structure"]["values"]
      and len(doc["revenue_structure"]["gaps"]) >= 5)
check("  공백 칸의 근거 유형이 '공백 선언'",
      all(g["evidence"] == "공백 선언" for g in doc["revenue_structure"]["gaps"]))

print("\n[선언 꼬리표] 출력 어디에 나오든 꼬리표가 동반된다 ((다) 판정)")
txt = L.render(doc)
for m in re.finditer(r"조회_경로_결함", txt):
    seg = txt[m.start():m.start() + 400]
    check("  '조회_경로_결함' 등장 시 꼬리표 동반",
          "선언(원장 관측 아님)" in seg and "만료" in seg, seg[:80])
check("선언이 JSON 출력에도 꼬리표째 실린다",
      all("선언(원장 관측 아님)" in d["꼬리표"]
          for g in doc["gaps"] for d in g.get("declared") or []))
# 선언이 **있을 때** 요약에서 꼬리표가 안 떨어지는지가 검사 대상이다. 실제 선언은
# 만료 삭제됐으므로(2026-08-08), 선언이 하나도 없으면 이 검사는 성립하지 않는다.
_declared = [d for g in doc["gaps"] for d in (g.get("declared") or [])]
if _declared:
    check("  narrative 요약에서도 꼬리표가 안 떨어진다",
          "선언(원장 관측 아님)" in doc["narrative"]["text"]
          and "만료조건" in doc["narrative"]["text"])
else:
    check("  선언 0건 — 만료 삭제 확인 (검사 대상 없음)", True)

print("\n[채널 한계] 뒷문장까지 나온다")
check("한계 선언 앞문장", "채널 축을 다루지 않는다" in doc["narrative"]["text"])
check("  **뒷문장**(침묵이 승인으로 읽히지 않게)",
      "채널 없이 BM 이 성립한다는 뜻이 아니다" in doc["narrative"]["text"])

print("\n[단일 원장] 종합하지 않는다")
check("문서에 단일 원장 기준 명시", "단일 원장" in doc["_scope"] and "audit-final" in doc["_scope"])
check("narrative 첫 줄도 단일 원장을 밝힌다",
      "단일 원장" in doc["narrative"]["text"].split("\n")[0])
check("build 는 run_id 하나만 받는다",
      L.build.__code__.co_argcount == 1, str(L.build.__code__.co_varnames[:2]))

# ══════════════════════════════════════════════════════════════
print("\n[report3-04] 기대값 — 값이 있는 원장")
doc2 = L.build("report3-04")
vals = {r["key"]: r for r in doc2["revenue_structure"]["values"]}
for k, mid in (("TAM", 3_832_272_000), ("SAM", 729_504_000),
               ("SOM", 218_851_200), ("revenue_y1", 10_942_560)):
    check(f"  {k} 중앙(기하) ≈ {mid:,}", k in vals
          and abs(vals[k]["mid_geometric"] - mid) < max(1, mid * 1e-6),
          str(vals.get(k, {}).get("mid_geometric")))
check("single_path 꼬리표가 값 옆에 동반",
      all(v["verification_note"] for v in vals.values()
          if v["verification"] == "single_path"))
check("converged 에도 독립성 확인 문구가 붙는다",
      "독립성" in vals["SAM"]["verification_note"], vals["SAM"]["verification_note"])
check("경쟁 축은 공백 선언 (R9 violated)", doc2["competition"]["gap"] is not None)
check("  그래도 실명 관측은 남는다", len(doc2["competition"]["rows"]) >= 2)
check("채널 한계 문구는 이 문서에도 있다",
      "채널 없이 BM 이 성립한다는 뜻이 아니다" in doc2["narrative"]["text"])

print("\n[피벗 조건문] 조건만 낸다 — 고르지 않는다")
piv = doc["pivot_conditions"]
check("조건문이 if/then 구조", all("if" in p and "then" in p for p in piv))
check("  '권장'·'추천' 같은 선택 언어가 없다",
      not any(re.search(r"권장|추천|해야 한다|하는 것이 좋", p["then"]) for p in piv),
      str([p["then"] for p in piv])[:90])
check("  축_부재 축은 '성립/불성립을 말할 수 없다' 로 나온다",
      any("말할 수 없다" in p["then"] for p in piv))

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print("   -", f)
sys.exit(1 if fail else 0)
