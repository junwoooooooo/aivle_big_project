# -*- coding: utf-8 -*-
"""설계 대조 도구 검증 — **LLM 0회 · 유료 0원.**

    python tests/test_design_score.py

**재는 자를 무엇으로 재는가.** 판 ㉛ 9회차가 `data/slots_hmr-pin0*.json` 로 남아 있고
성적표 결과가 문서에 값으로 있다(`docs/CONCEPT_TO_RESEARCH_HANDOFF.md` §15.8).
즉 **공짜 라벨 데이터**다 — 도구가 그 이력과 어긋나면 도구가 틀린 것이다.

⚠ **성적표를 재현하는 것이 목표가 아니다.** `pin-06` 은 성적표 6/6 이면서 오답이었고
   (§15.8.1), `pin-07` 은 4/6 이면서 정직했다. 이 도구가 그 둘을 **성적표와 반대로**
   매기는 것이 옳다 — 아래 「반대로 매긴다」 검사가 그것을 못 박는다.
"""
from __future__ import annotations
import io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "tools"))

from design_score import ScoreOptions, score                        # noqa: E402

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
        print(f"  OK  {name}")
    else:
        fail.append(f"{name} {detail}")
        print(f"  X   {name} {detail}")


REF = "data/slots_hmr-pin09.json"
CONCEPT = "data/concept_hmr-solo.json"


def 재기(snapshot: str, tag: str) -> dict:
    return score(ScoreOptions(tag=f"t-{tag}", slots=f"data/slots_{snapshot}.json",
                              ref=REF, concept=CONCEPT))


def 위반(r: dict, 축: str) -> list:
    v = r["축"][축]
    return v.get("위반") or v.get("미달") or []


# pin-01 은 별도 파일이 없다 — §15.8 표의 「(고정만)」이 `paid31b` 를 그대로 고정한 판이다.
p01 = 재기("paid31b-hmr", "p01")
p05 = 재기("hmr-pin05", "p05")
p06 = 재기("hmr-pin06", "p06")
p07 = 재기("hmr-pin07", "p07")
p09 = 재기("hmr-pin09", "p09")

print("\n── ① 기준 대조 축은 자기 자신을 만점으로 잰다 ──")
# **기준 대조 축만이다.** 이 넷은 «pin-09 와 얼마나 같은가» 를 재므로 자기 자신에게
# 만점이 아니면 축이 틀린 것이다. 다섯째 축은 성격이 다르다 — 아래 ⑧ 참조.
_REF축 = ["서식지_분산", "must_contain_규율", "발행_가능성", "계열_가격어휘"]
check("pin-09 는 **기준 대조 네 축**에서 만점",
      all(p09["축"][k]["점수"] == 1.0 for k in _REF축),
      str({k: p09["축"][k]["점수"] for k in _REF축}))
check("pin-09 의 구조 일치는 1.0", p09["구조"]["_평균"] == 1.0, str(p09["구조"]["_평균"]))

print("\n── ② 거짓 6/6 을 잡는다 (§15.8.1) ──")
# pin-06 의 S13 은 subject 「1인 가구」 · must_contain 「문제」였고, 그래서 「70대 이상
# 1인 가구 우울증상유병률 8.9%」가 수요 칸을 채웠다. 인구만 맞고 종류가 다르다.
mc6 = 위반(p06, "must_contain_규율")
check("pin-06 이 must_contain_규율에서 감점된다", p06["축"]["must_contain_규율"]["점수"] < 1.0,
      str(p06["축"]["must_contain_규율"]["점수"]))
check("잡힌 것이 바로 그 S13 이다",
      any(w["slot_id"] == "S13" and w["must_contain"] == ["문제"] for w in mc6),
      json.dumps(mc6, ensure_ascii=False)[:200])

print("\n── ③ 성적표와 **반대로** 매긴다 ──")
# 성적표: pin-06 6/6 > pin-07 4/6.  설계 품질: pin-07 이 위다 — 가드를 종류로 조여서
# 오답을 막았고, 그래서 깨진 것이 정직한 결과였다. 문턱은 개수를 세지 종류를 안 본다.
check("pin-07(4/6) 의 설계 점수가 pin-06(거짓 6/6) 보다 높다",
      p07["잰_축_평균"] > p06["잰_축_평균"],
      f"pin07={p07['잰_축_평균']} pin06={p06['잰_축_평균']}")

print("\n── ④ 서식지 분산이 회차 순서를 따른다 (§15.8) ──")
# pin-01~04 칸마다 표적 하나(4/6) → pin-05 분산 시작(5/6) → pin-09 (6/6).
s01, s05, s09 = (r["축"]["서식지_분산"]["점수"] for r in (p01, p05, p09))
check("pin-01 < pin-05 < pin-09", s01 < s05 < s09, f"{s01} < {s05} < {s09}")

print("\n── ⑤ pin-05 의 함정 — 분산인 줄 알았는데 중복이었다 ──")
# S13·S17 이 subject 가 둘 다 「1인 가구」라 `plan_query` 가 **같은 검색어**를 던졌다.
# 슬롯은 늘었는데(14→17) PAIN 의 «서로 다른 subject» 는 1 그대로다.
check("pin-05 의 PAIN 서로_다른_subject 가 1 이다",
      p05["축"]["서식지_분산"]["설계"].get("PAIN") == 1,
      str(p05["축"]["서식지_분산"]["설계"]))
check("슬롯은 늘었다 (14 → 17) — 개수로는 안 보인다",
      p01["슬롯수"]["설계"] == 14 and p05["슬롯수"]["설계"] == 17,
      f"{p01['슬롯수']['설계']} → {p05['슬롯수']['설계']}")

print("\n── ⑥ 발행 가능성 · 계열 가격 어휘 (§15.9 이유 ②③) ──")
발 = 위반(p01, "발행_가능성")
계 = 위반(p01, "계열_가격어휘")
check("pin-01 의 「프레시지 월 구독료」가 발행_가능성에서 잡힌다",
      any(w["subject"] == "프레시지" for w in 발), json.dumps(발, ensure_ascii=False)[:200])
check("같은 슬롯이 계열_가격어휘에서도 잡힌다 (계열 C 에 서비스 낱말)",
      any(w["metric"] == "월 구독료" for w in 계), json.dumps(계, ensure_ascii=False)[:200])
check("pin-09 는 두 축 모두 위반 0",
      not 위반(p09, "발행_가능성") and not 위반(p09, "계열_가격어휘"))

print("\n── ⑧ 다섯째 축은 **기준을 안 믿는다** (판 ㉜) ──")
# 이 축이 왜 뒤늦게 생겼나: 앞의 네 축을 `pin-01~09` **라벨에서만** 뽑았는데, 아홉 판 전부
# TAM·GROWTH·COMP 의 밴드를 사람이 옳게 적어 **신호가 0이었다.** 그래서 재는 자가
# 「가장 크게 죽이는 원인」에 구조적으로 눈이 없었고, 판 ㉜ 유료 실측에서 대가를 치렀다 —
# 6슬롯·성적표 4과목·blocker 1개가 한 원인으로 죽었다.
check("pin-09 가 다섯째 축에서 **감점된다** (기준의 흠을 안 따라간다)",
      p09["축"]["value_range_자릿수"]["점수"] < 1.0,
      str(p09["축"]["value_range_자릿수"]["점수"]))
check("걸린 것이 거래액 슬롯이다 (S1 은 [1e9, 5e10], 전형은 1e11~1e14)",
      all(w["metric"] == "거래액" for w in 위반(p09, "value_range_자릿수")),
      json.dumps(위반(p09, "value_range_자릿수"), ensure_ascii=False)[:150])
# **절대 축**이라 `--ref` 를 무엇으로 주든 답이 같아야 한다. 기준 대조 축과 성격이 다르다.
_self = score(ScoreOptions(tag="t-selfref", slots=f"data/slots_{'hmr-pin09'}.json",
                           ref="data/slots_hmr-pin05.json", concept=CONCEPT))
check("기준을 바꿔도 다섯째 축 점수는 같다 (절대 축이다)",
      _self["축"]["value_range_자릿수"]["점수"]
      == p09["축"]["value_range_자릿수"]["점수"])

print("\n── ⑨ 축을 정하기 전에 원장부터 센다 ──")
# 라벨이 안 흔든 축은 라벨에서 못 나온다. 그런데 **무엇이 실제로 죽였는지는 원장에
# 값으로 남아 있다** — 판 ㉜ 에서 이것을 먼저 세었으면 첫날 잡았다.
_withruns = score(ScoreOptions(tag="t-runs", slots=REF, ref=REF, concept=CONCEPT,
                               runs="p32-auto01"))
_사유 = _withruns["지난_실행_off_slot"]["사유별"]
check("지난 실행의 off_slot 사유가 세어진다", bool(_사유), str(_사유))
check("판 ㉜ 을 죽인 두 원인이 1·2등으로 나온다 (값범위·단위)",
      {"값범위 밖", "단위 불일치"} <= set(_사유), str(list(_사유)))
check("--runs 를 안 주면 **안 쟀다고 적는다** (조용히 비우지 않는다)",
      "_안_잰_이유" in p09["지난_실행_off_slot"])

print("\n── ⑦ 알고 남긴 한계 ──")
# pin-07·pin-08 은 네 축 만점인데 성적표는 4/6·5/6 이었다. 두 판의 차이는
# 「1인 가구 결식」 → 「1인 가구 아침 결식」 같은 **표적의 적절함**이고, 그것은 결정적
# 검사로 볼 수 없다. **이 도구는 필요조건을 재지 충분조건을 재지 않는다.**
check("pin-07 은 **기준 대조 네 축** 만점이다 — 그런데 성적표는 4/6 이었다",
      all(p07["축"][k]["점수"] == 1.0 for k in _REF축),
      str({k: p07["축"][k]["점수"] for k in _REF축}))
# 다섯째 축은 그 pin-07 도 잡는다 — **절대 축을 더한 값이 이것이다.** 라벨 대조만으로는
# 만점이던 판본이 「그래도 이 밴드로는 값이 격리된다」는 말을 듣는다.
check("그 pin-07 도 다섯째 축에서는 걸린다 (절대 축을 더한 값)",
      p07["축"]["value_range_자릿수"]["점수"] < 1.0,
      str(p07["축"]["value_range_자릿수"]["점수"]))

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
