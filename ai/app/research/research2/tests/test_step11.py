# -*- coding: utf-8 -*-
"""단계 11 검증 — 판 ⑩ 의 두 개정.

    ②-a  scoring.missing_year_exemption   자기 요금 페이지 연도 감점 예외
    ②-b  consistency.price_band           가격 밴드 산출물별 임계 분리

**이 파일이 지키려는 것은 「개정이 먹는가」가 아니라 「개정이 새지 않는가」다.**
완화는 언제나 인접한 자리로 샌다 — 그래서 통과 케이스 1개당 **좁힘 케이스 여러 개**를 둔다.
"""
from __future__ import annotations
import copy, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "blocks"))

import a_desk as A4
import c_chain as C
from runlog import load_rules
from schema import Document, Fact, Ledger, LedgerRow, Slot

# ── 판 ㉙ 픽스처 현실화 — **기대값을 바꾸는 것이 아니라 빠져 있던 현실을 채운다** ──────
#   실제 수집은 **모든 문서에 조회일을 찍는다**(`adapters/base.py:63,103` · `adapters/web.py:169`).
#   픽스처만 그것을 빼먹고 있었고, 기준 v2 의 새 축(`채택`)은 조회일을 4요건 중 하나로 본다.
#   즉 조회일 없는 픽스처는 **실제 수집이 만들 수 없는 행**이다.
#   ⚠ 여기서 채우는 것은 **입력의 현실성**뿐이다. 어떤 `check()` 의 기대값도 손대지 않는다 —
#     기대값이 바뀌어야 통과하는 상황이 오면 그것은 픽스처 문제가 아니라 **회귀 신호**다.
_RA_FIXTURE = "2026-08-09T00:00:00"
_Document_real = Document


def Document(*a, **k):
    k.setdefault("retrieved_at", _RA_FIXTURE)
    return _Document_real(*a, **k)

_Fact_real = Fact


def Fact(*a, **k):
    k.setdefault("retrieved_at", _RA_FIXTURE)
    return _Fact_real(*a, **k)


BASE = load_rules()
ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


def rules_with(exempt=None, band_official=None):
    """규칙을 **값으로 복사해** 손본다. 전역 규칙 파일을 건드리지 않는다."""
    r = copy.deepcopy(BASE)
    if exempt is not None:
        r["scoring"]["missing_year_exemption"]["enabled"] = exempt
    if band_official is not None:
        for a in r["consistency"]["price_band"]["accept"]:
            if a["id"] == "official_price_page":
                a["enabled"] = band_official
    return r


def slot(sid="S1", ctype="PRICE"):
    return Slot(slot_id=sid, var_id="V1", formula_id="F1", claim_type=ctype,
                subject="예약 관리 SaaS", metric="월 구독료", period="2026", unit="원",
                period_min=2023, period_max=2027)


URL = "https://gongbiz.kr/landing-page/payment-plan"
QUOTE = "월 19,800원"
BODY = "요금제 안내 — 스탠다드 월 19,800원 부터"


def fact(fid="F001", sid="S1", url=URL, value=19800.0, retrieved="2026-08-09T12:00:00",
         qv=True):
    return Fact(fact_id=fid, slot_id=sid, var_id="V1", trace_id=f"{sid}-t", url=url,
                quote=QUOTE, value_num=value, unit_norm="원", year=None,
                dedup_key=url, match_key=f"{sid}|월 구독료|unknown", quote_verified=qv,
                content_status="usable", channel="direct_url", retrieved_at=retrieved)


def doc(url=URL):
    return Document(slot_id="S1", trace_id="S1-t", url=url, text=BODY,
                    content_status="usable", text_len=len(BODY))


def score_of(f, s, rules):
    led = A4.grade([f], {s.slot_id: s}, {"S1-t": doc(f.url)}, rules, 2026)
    return led.rows[0]


# ══════════════════════════════════════════════════════════════
print("[②-a] 연도 미표기 면제 — **좁게 열렸는가**")
# ══════════════════════════════════════════════════════════════
r_on, r_off = rules_with(exempt=True), rules_with(exempt=False)

row = score_of(fact(), slot(), r_on)
check("자기 요금 페이지 + 조회시점 → 감점 없음(4점)", row.score == 4, f"score={row.score}")
check("사유에 조회 시점이 찍힌다",
      any("면제" in x and "2026-08-09" in x for x in row.reasons), str(row.reasons))

# **면제해도 확인됨이 되지 않는다** — 이것이 이 예외가 안전한 이유의 본체다.
check("면제해도 라벨은 확인됨이 아니다", row.label == "출처약함", row.label)

row = score_of(fact(retrieved=None), slot(), r_on)
check("조회 시점 없음 → 면제 불가(3점)", row.score == 3, f"score={row.score}")
check("거부 사유를 남긴다", any("면제 불가" in x for x in row.reasons), str(row.reasons))

row = score_of(fact(), slot(ctype="TAM"), r_on)
check("PRICE 아닌 슬롯 → 면제 없음(3점)", row.score == 3, f"score={row.score}")

row = score_of(fact(url="https://www.yna.co.kr/view/1"), slot(), r_on)
check("official_page 아닌 kind → 면제 없음", row.score == 3 - 1 + 1 or row.score < 4,
      f"score={row.score} kind={row.kind}")

row = score_of(fact(), slot(), r_off)
check("enabled=false → 옛 동작(3점)", row.score == 3, f"score={row.score}")

# 교차는 면제하지 않는다 — unknown 버킷 사유가 그대로 남아야 한다
row = score_of(fact(), slot(), r_on)
check("연도 미상 교차 격리는 그대로",
      any("unknown 버킷" in x for x in row.reasons), str(row.reasons))


# ══════════════════════════════════════════════════════════════
print("\n[②-b] 가격 밴드 채택 갈래 — **무엇이 들어오고 무엇이 막히는가**")
# ══════════════════════════════════════════════════════════════
def band(rows_spec, rules, ctype="PRICE"):
    """rows_spec: [(값, kind, score, label, quote_verified)] → 밴드 또는 None"""
    led, slots = Ledger(), [slot("S1", ctype)]
    for i, (v, kind, sc, label, qv) in enumerate(rows_spec, 1):
        fid = f"F{i:03d}"
        led.facts[fid] = fact(fid, value=v, qv=qv)
        led.rows.append(LedgerRow(fact_id=fid, slot_id="S1", url=URL, kind=kind,
                                  kind_by="test", score=sc, label=label, cross=0,
                                  reasons=[], off_slot_reason=None))
    cells = C.build_chain([], led, {}, rules, slots)
    return cells["alt_price_band"].origin if cells["alt_price_band"].source == "ledger" else None

OFF4 = ("official_page", 4, "출처약함", True)
r_on, r_off = rules_with(band_official=True), rules_with(band_official=False)

b = band([(15000.0, *OFF4), (30000.0, *OFF4)], r_on)
check("official_page 4점 2건 → 밴드 형성", b is not None and "15000" in b.replace(".0", ""), str(b))

b = band([(15000.0, *OFF4), (30000.0, *OFF4)], r_off)
check("갈래 끄면 옛 동작(밴드 없음)", b is None, str(b))

b = band([(15000.0, "official_page", 3, "출처약함", True),
          (30000.0, "official_page", 3, "출처약함", True)], r_on)
check("3점은 안 들어온다", b is None, str(b))

b = band([(15000.0, "official_page", 4, "출처약함", False),
          (30000.0, "official_page", 4, "출처약함", False)], r_on)
check("인용 미대조는 안 들어온다", b is None, str(b))

b = band([(15000.0, "aggregate", 4, "출처약함", True),
          (30000.0, "aggregate", 4, "출처약함", True)], r_on)
check("등재 안 된 kind 는 안 들어온다", b is None, str(b))

b = band([(15000.0, *OFF4)], r_on)
check("1건이면 밴드가 아니다", b is None, str(b))

b = band([(15000.0, *OFF4), (30000.0, *OFF4)], r_on, ctype="PAIN")
check("PRICE 밖 슬롯은 안 들어온다", b is None, str(b))

# ⚠ 회귀 방어 — 판 ⑩ 에서 실제로 터진 자리.
# COMP 슬롯의 **전사 매출액**(단위가 「원」이라 통과한다)이 밴드에 섞여
# [15,000 ~ 12,035,007,218,975] 가 나왔고, 그 밴드 안이라는 이유로 가격 가설에
# **「검증됨」 도장**이 찍혔다. 크기로 거르지 않고 **종류로** 닫았다.
b = band([(15000.0, *OFF4), (12_035_007_218_975.0, "public_filing", 5, "확인됨", True)],
         r_on, ctype="COMP")
check("COMP 매출액은 가격 밴드가 아니다", b is None, str(b))

b = band([(15000.0, "public_filing", 5, "확인됨", True),
          (30000.0, "public_filing", 5, "확인됨", True)], r_off)
check("확인됨 갈래는 갈래를 꺼도 그대로 산다", b is not None, str(b))

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
