# -*- coding: utf-8 -*-
"""단계 18 검증 — 판 ㊲ G5 「값 부재 인용」 겹.

**이 파일이 지키려는 것 한 줄: 채택된 값은 그 인용문 안에서 읽혀야 한다.**

판 ㊱ 이 「연속 3판 6/6」을 냈는데 그 판의 채택 인용 중 2건이 값 없는 인용이었다
(S14 75.9% ← 「배달비 부담 역시 과하다고 느끼고 있었다」, S13 54.0%). `quote_verified`
는 인용문이 문서에 실재하는지만 보고 **값이 인용문 안에 있는지는 아무도 안 봤다.**

검사는 세 갈래다:
  ① 겹이 목표를 맞히는가            — 숫자 0개 인용이 집힌다
  ② 겹이 과조임이 아닌가            — pin-09 의 ⑤(41.7%) · 한국어 배수 · 쉼표 · API 채널
  ③ **겹이 남의 자리를 뺏지 않는가** — 새 겹은 맨 뒤다(판 ㊱ 에서 17검사가 뒤집혔다)
"""
from __future__ import annotations
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "blocks"))
sys.path.insert(0, os.path.join(ROOT, "tools"))

import a_desk as A4                                              # noqa: E402
from runlog import load_rules                                    # noqa: E402
from schema import Document, Fact, Slot                          # noqa: E402

BASE = load_rules()
ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


def rules(**off):
    """규칙 사본. **문턱은 코드가 아니라 규칙 값이다** — 시험도 규칙을 갈아 끼워 잰다."""
    import copy
    r = copy.deepcopy(BASE)
    r["scoring"]["off_slot"]["값_부재_인용"].update(off)
    return r


def slot(sid="S1", ctype="PAIN", unit="%", **kw):
    kw.setdefault("must_contain", [])
    return Slot(slot_id=sid, var_id="V1", formula_id="F1", claim_type=ctype,
                subject="1인 가구", metric="혼자 식사 비율", period="2024", unit=unit, **kw)


def fact(quote, value, unit="%", channel="web", year=None, **kw):
    url = "https://kostat.go.kr/x"
    return Fact(fact_id="F1", slot_id="S1", var_id="V1", trace_id="S1-t", url=url,
                quote=quote, value_num=value, unit_norm=unit, year=year,
                dedup_key=url, match_key="S1|혼자 식사 비율|2024", quote_verified=True,
                content_status="usable", channel=channel, **kw)


def doc(text="본문"):
    return Document(slot_id="S1", trace_id="S1-t", url="https://kostat.go.kr/x",
                    text=text, content_status="usable", text_len=len(text))


def 사유(f, s=None, r=None):
    return A4.off_slot_reason(f, s or slot(), doc(f.quote), r or rules())


print("\n① 목표를 맞히는가")

# 판 ㊱ 실측 그대로. 「배달비 부담 역시 과하다고 느끼고 있었다」에 숫자가 0개인데
# 75.9% 가 채택됐다 — 값의 출처가 인용문이 아니면 확인됨 등급이 추적 불가능해진다.
r = 사유(fact("배달비 부담 역시 과하다고 느끼고 있었다.", 75.9))
check("숫자 0개 인용이 집힌다", (r or "").startswith("값 부재 인용"), str(r))

# S13 형 — 지표도 값도 없는 서술.
r = 사유(fact("이들은 주거비를 제외하면 식품 구매와 외식비에 지출을 집중하는 경향을 보였다.", 54.0))
check("값도 지표도 없는 인용이 집힌다", (r or "").startswith("값 부재 인용"), str(r))

# 인용문에 숫자는 있는데 **다른 숫자**인 경우. 무서술 겹은 통과시킨다(서술이 길다).
r = 사유(fact("응답자의 26.9%가 점심을 혼자 먹었다고 답했다.", 41.7))
check("인용문에 없는 값은 집힌다(다른 수만 있음)", (r or "").startswith("값 부재 인용"), str(r))


print("\n② 과조임이 아닌가")

# **이것이 기준선이다.** pin-09 의 ⑤를 정직하게 채운 그 인용 — 죽이면 겹을 되돌린다.
r = 사유(fact("혼자 식사한 비율은 아침 식사한 사람 중 41.7%(2.9%p), 점심은 26.9%로 상승", 41.7))
check("pin-09 의 ⑤ 41.7% 가 산다", r is None, str(r))

# API 채널 면제. `"DT": "38041110"` 은 **백만원 단위 칸**이고 value_num 은 38041110000000 —
# 면제하지 않으면 p36-n6-01 의 정상 채움 6건이 통째로 오탐이다(실측).
r = 사유(fact('"DT": "38041110"', 38041110000000.0, unit="원", channel="kosis_api"),
        slot(unit="원"))
check("KOSIS 백만원 칸이 오탐이 아니다", r is None, str(r))
r = 사유(fact('"thstrm_amount": "27342589100000"', 27342589100000.0, unit="원",
             channel="dart_api"), slot(unit="원"))
check("DART thstrm_amount 가 오탐이 아니다", r is None, str(r))

# 한국어 배수. `str(value_num) in quote` 로 보면 여기가 통째로 무너진다.
r = 사유(fact("1만 원 초과 주문은 배달비를 받지 않는다", 10000.0, unit="원"), slot(unit="원"))
check("「1만 원」이 10000 으로 읽힌다", r is None, str(r))
r = 사유(fact("지난해 온라인 쇼핑 거래액은 38조 원이었다", 38000000000000.0, unit="원"),
        slot(unit="원"))
check("「38조」가 3.8e13 으로 읽힌다", r is None, str(r))
r = 사유(fact("올해 시장 규모는 15조 5000억 원으로 집계됐다", 15500000000000.0, unit="원"),
        slot(unit="원"))
check("「15조 5000억」이 겹쳐 읽힌다", r is None, str(r))

# 표기 차이 — 쉼표.
r = 사유(fact("도시락 한 개 값은 1,200원이다", 1200.0, unit="원"), slot(unit="원"))
check("쉼표 표기가 읽힌다", r is None, str(r))

# 없는 기준으로 벌하지 않는다 — 다른 네 겹과 같은 원칙.
r = 사유(fact("비율이 상승했다고 밝혔다.", None))
check("value_num 이 없으면 판정하지 않는다", r is None, str(r))

# 측정 조건은 규칙 값이다 — 한 번에 하나만 켜서 잰다.
r = 사유(fact("배달비 부담 역시 과하다고 느끼고 있었다.", 75.9), None, rules(enabled=False))
check("enabled=false 면 겹이 죽는다", r is None, str(r))


print("\n③ 남의 자리를 뺏지 않는가 — **새 겹은 맨 뒤다**")

# 판 ㊱ 실측: 새 겹을 앞에 뒀더니 기존 겹의 사유를 가로채 검사 17개가 뒤집혔다.
# 아래 셋은 **값도 인용문에 없다** — 그래도 먼저 걸린 사유가 남아야 한다.
s = slot(must_contain=["혼밥"])
r = 사유(fact("배달비 부담이 크다고 답했다.", 75.9), s)
check("must_contain 실패가 먼저다", (r or "").startswith("must_contain 없음"), str(r))

s = slot(must_not_contain=["소상공인"])
r = 사유(fact("소상공인 응답자들은 부담이 크다고 답했다.", 75.9), s)
check("must_not_contain 이 먼저다", (r or "").startswith("must_not_contain 포함"), str(r))

s = slot(unit="원")
r = 사유(fact("부담이 크다고 답했다.", 75.9, unit="%"), s)
check("단위 불일치가 먼저다", (r or "").startswith("단위 불일치"), str(r))

s = slot(period_min=2023, period_max=2025)
r = 사유(fact("부담이 크다고 답했다.", 75.9, year=2018), s)
check("기간 불일치가 먼저다", (r or "").startswith("기간 불일치"), str(r))

# 무서술 겹(넷째)도 다섯째보다 앞이다 — 「95%」는 값이 인용문에 **있다**.
r = 사유(fact("95%", 95.0))
check("무서술 인용이 먼저다", (r or "").startswith("무서술 인용"), str(r))

# 사유 세기에 등록됐는가. 안 하면 「기타」로 뭉개져 진단이 사라진다(§27.3).
check("_OFF_SLOT_LAYERS 에 등록됐다", "값 부재 인용" in A4._OFF_SLOT_LAYERS,
      str(A4._OFF_SLOT_LAYERS))
check("겹이 맨 뒤로 등록됐다", A4._OFF_SLOT_LAYERS[-1] == "값 부재 인용",
      str(A4._OFF_SLOT_LAYERS[-1]))


print("\n④ 재는 자와 자르는 자가 같은 산식을 쓴다")

import quote_audit                                               # noqa: E402
check("감사기가 엔진 함수를 그대로 쓴다",
      quote_audit.값이_인용문에_있는가 is A4.값이_인용문에_있는가)

U = BASE["units"]
_f = {"quote": "배달비 부담 역시 과하다고 느끼고 있었다.", "value_num": 75.9, "channel": "web"}
check("감사기가 값 부재를 집는다",
      any(x["검사"] == "값_부재_인용"
          for x in quote_audit.검사(_f, {"claim_type": "PAIN"}, {}, {}, U)))
_k = {"quote": '"DT": "38041110"', "value_num": 38041110000000.0, "channel": "kosis_api"}
check("감사기가 KOSIS 를 면제한다",
      not any(x["검사"] == "값_부재_인용"
              for x in quote_audit.검사(_k, {"claim_type": "SIZE"}, {}, {}, U)))
# 옛 원장은 `rules.units` 가 없다 — **0 이 아니라 미측정**이어야 한다.
check("units 가 없으면 다섯째 눈을 안 돌린다",
      not any(x["검사"] == "값_부재_인용"
              for x in quote_audit.검사(_f, {"claim_type": "PAIN"}, {}, {}, None)))


print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
