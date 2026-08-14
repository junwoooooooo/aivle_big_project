# -*- coding: utf-8 -*-
"""단계 3 검증 — B블록.

수용기준 2(B에 LLM 0건) · 3(같은 원장 2회 → 바이트 동일) · 10(diverged 면 adopted 없음)
        + thin → assumption_count 흡수 · as_of 인자화

더미 원장에 **thin=True 슬롯과 공백 슬롯을 반드시 하나씩** 넣는다 — 그게 B블록의 진짜 테스트다.
"""
from __future__ import annotations
import io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "blocks"))

import b_estimate as B
from runlog import load_rules
from schema import (Coverage, Fact, Formula, FormulaVar, Ledger, LedgerRow, Slot, to_dict)

# ── 판 ㉙ 픽스처 현실화 — **기대값을 바꾸는 것이 아니라 빠져 있던 현실을 채운다** ──────
#   실제 수집은 **모든 문서에 조회일을 찍는다**(`adapters/base.py:63,103` · `adapters/web.py:169`).
#   픽스처만 그것을 빼먹고 있었고, 기준 v2 의 새 축(`채택`)은 조회일을 4요건 중 하나로 본다.
#   즉 조회일 없는 픽스처는 **실제 수집이 만들 수 없는 행**이다.
#   ⚠ 여기서 채우는 것은 **입력의 현실성**뿐이다. 어떤 `check()` 의 기대값도 손대지 않는다 —
#     기대값이 바뀌어야 통과하는 상황이 오면 그것은 픽스처 문제가 아니라 **회귀 신호**다.
_RA_FIXTURE = "2026-08-09T00:00:00"
_Fact_real = Fact


def Fact(*a, **k):
    k.setdefault("retrieved_at", _RA_FIXTURE)
    return _Fact_real(*a, **k)


rules = load_rules()
ASSUM = rules["assumptions"]["by_role"]
AS_OF = 2026
ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


# ══════════════════════════════════════════════════════════════
# 더미 원장 — 손으로 만든다.
#   S1 사업체수  : 확인됨 3건 → 충족, thin=False
#   S2 침투율    : 공백        → 계산 불가 (가정으로 채워짐)
#   S3 단가      : 확인됨 1건 → 충족, **thin=True** (min_facts=2)
#   S4 상위시장  : 확인됨 2건 → topdown 용
# ══════════════════════════════════════════════════════════════
def mk_slot(sid, vid, fid, role_metric, unit, min_facts=2):
    return Slot(slot_id=sid, var_id=vid, formula_id=fid, claim_type="SAM",
                subject="커피전문점", subject_code="KSIC-56221", metric=role_metric,
                period="2023", unit=unit,
                accept={"min_score": 5, "min_sources": 2, "min_facts": min_facts,
                        "min_confirmed": 1})


slots = [
    mk_slot("S1", "V1", "F_SAM_BU", "사업체 수", "개"),
    mk_slot("S2", "V2", "F_SAM_BU", "침투율", "비율"),
    mk_slot("S3", "V3", "F_SAM_BU", "월 구독료", "원"),
    mk_slot("S4", "V4", "F_SAM_TD", "상위시장규모", "원"),
    mk_slot("S5", "V5", "F_SAM_TD", "세그먼트비중", "비율"),
]

ledger = Ledger()


def add_fact(fid, slot_id, var_id, value, unit, domain, score=6):
    f = Fact(fact_id=fid, slot_id=slot_id, var_id=var_id, trace_id=f"{slot_id}-t",
             url=f"https://{domain}/p/{fid}", quote="q", value_num=value, unit_norm=unit,
             year=2023, dedup_key=f"{domain}/p/{fid}", match_key=f"KSIC|m|2023",
             quote_verified=True, content_status="usable")
    ledger.facts[fid] = f
    # 판 ㉙ — 실제 `grade()` 가 만드는 «확인됨» 행은 **채택도 True** 다
    # (조회일·인용 대조를 통과해야 확인됨에 닿는다). 픽스처만 그 칸이 비어 있었다.
    ledger.rows.append(LedgerRow(fact_id=fid, slot_id=slot_id, url=f.url, kind="gov_stat",
                                 kind_by="whitelist:x", score=score, label="확인됨", cross=2,
                                 채택=True, 등급="확정", 등급_근거="등급표:gov_stat",
                                 retrieved_at="2026-08-09T00:00:00"))


add_fact("F001", "S1", "V1", 100729, "개", "kosis.kr")
add_fact("F002", "S1", "V1", 100500, "개", "mods.go.kr")
add_fact("F003", "S1", "V1", 100800, "개", "kostat.go.kr")
add_fact("F010", "S3", "V3", 30000, "원", "cafepost.kr")          # 1건뿐 → thin
add_fact("F020", "S4", "V4", 15_500_000_000_000, "원", "kosis.kr")
add_fact("F021", "S4", "V4", 15_000_000_000_000, "원", "mods.go.kr")

coverage = [
    Coverage("S1", "충족", 3, 3, ["F001", "F002", "F003"], min_facts=2),
    Coverage("S2", "공백", 0, 0, [], min_facts=2),                 # ← 공백 슬롯
    Coverage("S3", "충족", 1, 1, ["F010"], min_facts=2),           # ← thin=True
    Coverage("S4", "충족", 2, 2, ["F020", "F021"], min_facts=2),
    Coverage("S5", "공백", 0, 0, [], min_facts=2),
]

F_BU = Formula(formula_id="F_SAM_BU", target="SAM", path="bottomup", template="T2", vars=[
    FormulaVar("V1", "사업체수", "커피전문점", "사업체 수", "2023", "개"),
    FormulaVar("V2", "침투율", "커피전문점", "SW 도입률", "2023", "%"),
    FormulaVar("V3", "단가", "카페 SaaS", "월 구독료", "2023", "원"),
])
F_TD = Formula(formula_id="F_SAM_TD", target="SAM", path="topdown", template="T1", vars=[
    FormulaVar("V4", "상위시장규모", "소상공인 SaaS", "시장규모", "2023", "원"),
    FormulaVar("V5", "세그먼트비중", "카페", "비중", "2023", "%"),
])

print("[더미 원장] thin 슬롯 1개(S3) · 공백 슬롯 2개(S2·S5) 포함")
check("thin 슬롯 존재", any(c.thin for c in coverage))
check("공백 슬롯 존재", any(c.status == "공백" for c in coverage))

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 2] B블록에 LLM 호출이 0건")
src = io.open(os.path.join(ROOT, "blocks", "b_estimate.py"), encoding="utf-8").read()
for bad in ("openai", "OpenAI", "responses.create", "Meter", "prompts"):
    check(f"'{bad}' 없음", bad not in src)

print("\n[as_of] 오늘 날짜를 읽지 않는다")
check("date.today 없음", "date.today" not in src and "datetime.now" not in src)
check("'2023' → (2023, 2023)", B.resolve_period("2023", AS_OF) == (2023, 2023))
check("'최근 3년' → as_of 기준", B.resolve_period("최근 3년", AS_OF) == (2024, 2026),
      str(B.resolve_period("최근 3년", AS_OF)))
check("as_of 가 바뀌면 구간도 바뀐다", B.resolve_period("최근 3년", 2030) == (2028, 2030))
check("해석 불가는 (None, None)", B.resolve_period("상시", AS_OF) == (None, None))

# ══════════════════════════════════════════════════════════════
print("\n[thin 흡수] 충족+thin → assumption_count +1, 보강필요·공백 → 계산 불가")
smap = {s.slot_id: s for s in slots}
cmap = {c.slot_id: c for c in coverage}

inputs_bu = B.substitute(F_BU, ledger, cmap, smap, ASSUM, rules)
est_bu = B.estimate(F_BU, inputs_bu, ledger, cmap, smap, ASSUM, rules)
by_var = {i.var_id: i for i in inputs_bu}
check("S1(충족·thin아님) → 사실 사용", by_var["V1"].from_fact == "F001", str(by_var["V1"]))
check("S2(공백) → 가정으로 대체", by_var["V2"].assumption is not None)
check("S2 가정에 basis 기록", bool(by_var["V2"].basis))
check("S3(충족·thin) → 사실은 쓰되", by_var["V3"].from_fact == "F010")
check("assumption_count = 가정1 + thin1 = 2", est_bu.assumption_count == 2,
      str(est_bu.assumption_count))
check("배지 '추정'", est_bu.badge == "추정", est_bu.badge)
check("falsified_if 에 표본 부족 명시", "표본" in est_bu.falsified_if, est_bu.falsified_if)
check("민감도에 침투율", any(s["var_id"] == "V2" for s in est_bu.sensitivity),
      str(est_bu.sensitivity))

print("\n  가정값이 코드가 아니라 rules/assumptions.v1.json 에서 온다")
check("assumptions 규칙 로드됨", "침투율" in ASSUM)
check("basis 가 규칙 파일 문구", ASSUM["침투율"]["basis"] in by_var["V2"].basis)

# ══════════════════════════════════════════════════════════════
print("\n[시끄러운 정지] 단위가 어긋나면 변환하지 않고 멈춘다")
slots_bad = [mk_slot("S1", "V1", "F_SAM_BU", "사업체 수", "개"),
             mk_slot("S2", "V2", "F_SAM_BU", "침투율", "%"),      # 가정은 '비율' -> 100배 어긋남
             mk_slot("S3", "V3", "F_SAM_BU", "월 구독료", "원")]
smap_bad = {x.slot_id: x for x in slots_bad}
in_bad = B.substitute(F_BU, ledger, cmap, smap_bad, ASSUM, rules)
est_bad = B.estimate(F_BU, in_bad, ledger, cmap, smap_bad, ASSUM, rules)
check("status=unit_mismatch", est_bad.status == "unit_mismatch", est_bad.status)
check("값을 만들지 않는다", est_bad.value is None)
check("어느 변수인지 기록", "V2" in est_bad.unit_note, est_bad.unit_note)

print("\n[잘림도 정보] 상한에 부딪히면 range_capped 가 남는다")
many = ([B.EstimateInput(var_id="V1", from_fact="F001", confirmed=True)] +
        [B.EstimateInput(var_id="VA%d" % i, assumption=1.0, basis="t") for i in range(6)])
est_cap = B.estimate(F_BU, many, ledger, cmap, smap, ASSUM, rules)
check("가정 6개 -> range_capped=True", est_cap.range_capped is True,
      "max_factor=%s" % rules["scoring"]["estimate_band"]["max_factor"])
est_small = B.estimate(F_BU, many[:3], ledger, cmap, smap, ASSUM, rules)
check("가정 2개 -> range_capped=False", est_small.range_capped is False)

print("\n[수용기준 10] diverged 면 adopted 가 비어 있다")
inputs_td = B.substitute(F_TD, ledger, cmap, smap, ASSUM, rules)
est_td = B.estimate(F_TD, inputs_td, ledger, cmap, smap, ASSUM, rules)
rec = B.reconcile("SAM", est_td, est_bu, rules)
print(f"   topdown={est_td.value}  bottomup={est_bu.value}  gap={rec.gap_ratio}  {rec.status}")
check("두 경로 값 존재", est_td.value and est_bu.value)
if rec.status == "diverged":
    check("diverged → adopted 없음", rec.adopted is None)
else:
    check("겹치면 adopted 존재", rec.adopted is not None, rec.status)

print("\n  강제 diverged 케이스 (3배 이상 격차)")
far_a = B.Estimate(formula_id="X", target="SAM", path="topdown", value=[100, 200],
                   assumption_count=0)
far_b = B.Estimate(formula_id="Y", target="SAM", path="bottomup", value=[900, 1200],
                   assumption_count=0)
rec2 = B.reconcile("SAM", far_a, far_b, rules)
check("status=diverged", rec2.status == "diverged", rec2.status)
check("adopted 비어 있음", rec2.adopted is None)
check("gap_ratio 보고됨", rec2.gap_ratio and rec2.gap_ratio >= 3, str(rec2.gap_ratio))
try:
    from schema import Reconciliation
    Reconciliation(target="SAM", topdown=[1, 2], bottomup=[9, 10], overlap=None,
                   gap_ratio=5.0, status="diverged", adopted=[1, 2])
    check("타입이 diverged+adopted 를 막는다", False, "예외가 안 났다")
except ValueError:
    check("타입이 diverged+adopted 를 막는다", True)

print("\n  한쪽만 있으면 single_path — **채택하되 꼬리표를 단다**")
# 버리면 사슬이 통째로 비어 R1~R3 가 영영 안 켜지고, 조용히 채택하면 삼각측량이 사라진다.
rec3 = B.reconcile("SAM", far_a, None, rules)
check("single_path", rec3.status == "single_path", rec3.status)
check("그 경로를 채택한다", rec3.adopted == far_a.value, str(rec3.adopted))
check("문구가 규칙 파일에 있다",
      "교차검증되지 않은" in rules["consistency"]["report_notes"]["single_path"])

print("\n  둘 다 없으면 insufficient (정말로 계산 불가)")
rec4 = B.reconcile("SAM", None, None, rules)
check("insufficient", rec4.status == "insufficient", rec4.status)
check("adopted 없음", rec4.adopted is None)

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 3] 같은 원장 2회 → 바이트 동일 (thin·가정 포함)")


def once():
    e, r = B.run_block_b([F_BU, F_TD], ledger, coverage, slots, ASSUM, rules, AS_OF)
    return json.dumps({"est": to_dict(e), "rec": to_dict(r)},
                      ensure_ascii=False, sort_keys=True).encode()


a, b = once(), once()
check("바이트 동일", a == b, f"{len(a)} vs {len(b)}")
check("assumption_count 포함", b'"assumption_count"' in a)
check("suspect_var 포함", b'"suspect_var"' in a)

print("\n[범위] 가정이 늘어도 하한이 0·음수가 되지 않는다")
for n in (0, 1, 2, 3, 5):
    e = B.estimate(F_BU,
                   [B.EstimateInput(var_id="V1", from_fact="F001", confirmed=True)] +
                   [B.EstimateInput(var_id=f"VA{i}", assumption=1.0, basis="t") for i in range(n)],
                   ledger, cmap, smap, ASSUM, rules)
    lo = e.value[0] if e.value else None
    check(f"가정 {n}개 → 하한 > 0", lo is not None and lo > 0, str(e.value))

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
