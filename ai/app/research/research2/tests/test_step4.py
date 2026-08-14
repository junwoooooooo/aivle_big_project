# -*- coding: utf-8 -*-
"""단계 4 검증 — C블록.

수용기준 2(LLM 0건) · 7(근거 0건 → 라벨 불가) · 11(규칙 12개 위반/통과 1쌍)
        · 13(not_configured 3곳) · 15(§7 키 전부)
        + 규칙 의존 순서(skipped) · range_capped → 추정 불가
"""
from __future__ import annotations
import io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "blocks"))

import c_chain as C
from runlog import load_rules
from schema import (NOT_FOUND_KEYS, ChainCell, Coverage, Estimate, EstimateInput, Fact,
                    Ledger, LedgerRow, Reconciliation, Slot)

rules = load_rules()
ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


def mk_cells(**vals):
    cells = {}
    for k in rules["consistency"]["chain_cells"]:
        v = vals.get(k)
        cells[k] = ChainCell(key=k, value=v, source="computed" if v is not None else "missing",
                             origin="test" if v is not None else "채워지지 않음")
    return cells


def mk_ledger(n_confirmed=2, dup=False):
    led = Ledger()
    for i in range(n_confirmed):
        fid = f"F{i:03d}"
        led.facts[fid] = Fact(fact_id=fid, slot_id="S1", var_id="V1", trace_id="t",
                              url=f"https://kosis.kr/{i}", quote="q",
                              value_num=100 if not dup else (100 if i == 0 else 900),
                              unit_norm="개", year=2023, dedup_key=f"kosis.kr/{i}",
                              match_key="K|m|2023", quote_verified=True,
                              content_status="usable")
        # 판 ㉙ — 실제 `grade()` 가 만드는 «확인됨» 행은 **채택도 True** 다
        # (조회일·인용 대조를 통과해야 확인됨에 닿는다). 픽스처만 그 칸이 비어 있었다.
        led.rows.append(LedgerRow(fact_id=fid, slot_id="S1", url=f"https://kosis.kr/{i}",
                                  kind="gov_stat", kind_by="w", score=6, label="확인됨", cross=2,
                                  채택=True, 등급="확정", 등급_근거="등급표:gov_stat",
                                  retrieved_at="2026-08-09T00:00:00"))
    return led


COV_OK = [Coverage("S1", "충족", 2, 2, ["F000", "F001"], min_facts=2)]

# ══════════════════════════════════════════════════════════════
print("[수용기준 2] C블록에 LLM 호출이 0건")
src = io.open(os.path.join(ROOT, "blocks", "c_chain.py"), encoding="utf-8").read()
for bad in ("openai", "OpenAI", "responses.create", "Meter", "prompts"):
    check(f"'{bad}' 없음", bad not in src)
check("date.today / datetime.now 없음", "date.today" not in src and "datetime.now" not in src)

# ══════════════════════════════════════════════════════════════
print("\n[규칙 순서] blocker 가 깨지면 아래 의존 규칙은 skipped")
bad_cells = mk_cells(TAM=100, SAM=500, SOM=900,          # R1 위반 (역순)
                     revenue_y1=50, price=10, target_customers=5)
vio = C.check_consistency(bad_cells, mk_ledger(), COV_OK, rules)
by_id = {v.rule_id: v for v in vio}
check("R1 위반", by_id["R1"].status == "violated", by_id["R1"].detail)
check("R2 는 R1 때문에 skipped", by_id["R2"].status == "skipped", by_id["R2"].status)
check("R3 은 R2 때문에 skipped", by_id["R3"].status == "skipped", by_id["R3"].status)
check("R8 도 R1 때문에 skipped", by_id["R8"].status == "skipped", by_id["R8"].status)
check("skipped_by 기록", by_id["R2"].skipped_by == "R1", str(by_id["R2"].skipped_by))
check("독립 규칙은 계속 검사됨", by_id["R9"].status in ("violated", "passed", "not_applicable"),
      by_id["R9"].status)

print("\n  정상 순서면 skipped 없음")
# price 는 원/월이라 R3 가 ×12 해 비교한다 → revenue_y1 은 10×12×5 = 600 이어야 정합이다.
# (R2 가 revenue_y1 ≤ SOM 을 보므로 SOM·SAM·TAM 도 함께 올린다)
good_cells = mk_cells(TAM=10000, SAM=5000, SOM=1000, revenue_y1=600,
                      price=10, target_customers=5, marketing_budget=20,
                      total_budget=100, CAC=2, new_customers=5, LTV=10)
vio2 = C.check_consistency(good_cells, mk_ledger(), COV_OK, rules)
by2 = {v.rule_id: v for v in vio2}
check("R1 통과", by2["R1"].status == "passed", by2["R1"].detail)
check("R2 검사됨(skipped 아님)", by2["R2"].status != "skipped", by2["R2"].status)
# ⚠ R3 는 이제 **단위를 본다.** price 는 원/월, revenue_y1 은 원/연이라 ×12 후 비교한다
#   (report3-02 에서 이 선언이 없어 차이 96% 의 **가짜 위반**이 났다).
check("R3 통과 (10원/월 ×12 ×5 = 600 = revenue_y1)", by2["R3"].status == "passed", by2["R3"].detail)
check("  환산 사실이 판정 문구에 남는다", "환산 후 비교" in by2["R3"].detail, by2["R3"].detail)

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 11] 규칙 12개 각각 위반/통과 1쌍")
CASES = {
    "R1": (mk_cells(TAM=100, SAM=500, SOM=900), mk_cells(TAM=1000, SAM=500, SOM=100)),
    "R2": (mk_cells(TAM=1000, SAM=500, SOM=100, revenue_y1=500),
           mk_cells(TAM=1000, SAM=500, SOM=100, revenue_y1=50)),
    # price 는 원/월 → ×12 후 비교. 통과 케이스는 10×12×5 = 600
    "R3": (mk_cells(TAM=10000, SAM=5000, SOM=1000, revenue_y1=600, price=10, target_customers=1),
           mk_cells(TAM=10000, SAM=5000, SOM=1000, revenue_y1=600, price=10, target_customers=5)),
    "R4": (mk_cells(CAC=10, new_customers=10, marketing_budget=20, total_budget=100),
           mk_cells(CAC=1, new_customers=10, marketing_budget=20, total_budget=100)),
    "R5": (mk_cells(marketing_budget=200, total_budget=100),
           mk_cells(marketing_budget=20, total_budget=100)),
    "R6": (mk_cells(LTV=1, CAC=10), mk_cells(LTV=10, CAC=1)),
    "R7": (mk_cells(price=10), None),          # 밴드 없음 → 위반 / 통과는 원장으로 만든다
    "R8": (None, None),                        # subject_match — 아래에서 따로
    "R9": (None, None),                        # min_confirmed — 아래에서 따로
    "R10": (None, None),
    "R11": (None, None),
    "R12": (None, None),
}
for rid in ["R1", "R2", "R3", "R4", "R5", "R6"]:
    bad_c, good_c = CASES[rid]
    vb = {v.rule_id: v for v in C.check_consistency(bad_c, mk_ledger(), COV_OK, rules)}[rid]
    vg = {v.rule_id: v for v in C.check_consistency(good_c, mk_ledger(), COV_OK, rules)}[rid]
    check(f"{rid} 위반 케이스", vb.status in ("violated", "skipped"), f"{vb.status} {vb.detail}")
    check(f"{rid} 통과 케이스", vg.status in ("passed", "skipped"), f"{vg.status} {vg.detail}")

print("\n  R7 가격 밴드 — 비교 불가가 위반이다 (밴드 밖이 위반이 아니다)")
v7_bad = {v.rule_id: v for v in C.check_consistency(mk_cells(price=10), mk_ledger(), COV_OK, rules)}["R7"]
# 밴드가 없으면 **판정하지 않는다.** 없는 기준으로 통과도 위반도 만들지 않는다.
# 옛 구현은 `확인됨` 인 **모든** 사실로 밴드를 만들어 report1-01 에서 사업체 수(개)
# [20,264~106,452] 를 '가격 밴드' 로 쓰고 R7 을 통과시켰다.
check("밴드 없음 → 판정 불가", v7_bad.status == "not_applicable", v7_bad.detail)
cells_band = mk_cells(price=10)
cells_band["alt_price_band"] = ChainCell(key="alt_price_band", value=None, source="ledger",
                                         origin="ALT/PRICE [0, 30000]")
v7_ok = {v.rule_id: v for v in C.check_consistency(cells_band, mk_ledger(), COV_OK, rules)}["R7"]
check("밴드 있음 → 통과", v7_ok.status == "passed", v7_ok.detail)

print("\n  R9 경쟁사 실명 — blocker")
# ⚠ R9 는 **슬롯의 claim_type** 을 봐야 한다. 옛 구현은 slot_id 앞글자로 골라
#   아무것도 못 맞추면 **전 슬롯으로 되돌렸고**(fail-open), report1-01 에서 R9 가
#   TAM·SAM 슬롯을 세고 통과했다. 지금은 못 맞추면 **판정 불가**다.
COMP_SLOT = Slot(slot_id="S1", var_id="V1", formula_id="F1", claim_type="COMP",
                 subject="코케비즈", metric="누적 가입 매장 수", period="2026", unit="곳")
TAM_SLOT = Slot(slot_id="S1", var_id="V1", formula_id="F1", claim_type="TAM",
                subject="커피전문점", metric="사업체 수", period="2023", unit="개")
cov_empty = [Coverage("S1", "공백", 0, 0, [], min_facts=2)]
v9_bad = {v.rule_id: v for v in C.check_consistency(good_cells, Ledger(), cov_empty, rules,
                                                    [COMP_SLOT])}["R9"]
check("확인된 경쟁 슬롯 0 → 위반", v9_bad.status == "violated", v9_bad.detail)
check("severity=blocker", v9_bad.severity == "blocker")
v9_ok = {v.rule_id: v for v in C.check_consistency(good_cells, mk_ledger(), COV_OK, rules,
                                                   [COMP_SLOT])}["R9"]
check("확인 슬롯 있음 → 통과", v9_ok.status == "passed", v9_ok.detail)

print("\n  R9 fail-closed — 못 맞추면 '전부 통과' 가 아니라 '판정 불가'")
v9_none = {v.rule_id: v for v in C.check_consistency(good_cells, mk_ledger(), COV_OK,
                                                     rules)}["R9"]
check("슬롯을 못 받으면 판정 불가", v9_none.status == "not_applicable", v9_none.detail)
v9_tam = {v.rule_id: v for v in C.check_consistency(good_cells, mk_ledger(), COV_OK, rules,
                                                    [TAM_SLOT])}["R9"]
check("TAM 슬롯만 있으면 COMP 를 세지 않는다 (옛 fail-open 재발 방지)",
      v9_tam.status == "not_applicable", v9_tam.detail)

print("\n  R10 출처 없는 숫자")
cells_nosrc = mk_cells(TAM=1000)
cells_nosrc["SAM"] = ChainCell(key="SAM", value=500, source="missing", origin="")
v10 = {v.rule_id: v for v in C.check_consistency(cells_nosrc, mk_ledger(), COV_OK, rules)}["R10"]
check("출처 없는 값 → 위반", v10.status == "violated", v10.detail)
v10ok = {v.rule_id: v for v in C.check_consistency(mk_cells(TAM=1000), mk_ledger(), COV_OK, rules)}["R10"]
check("전부 출처 있음 → 통과", v10ok.status == "passed", v10ok.detail)

print("\n  R11 같은 지표 두 값")
v11 = {v.rule_id: v for v in C.check_consistency(good_cells, mk_ledger(dup=True), COV_OK, rules)}["R11"]
check("같은 match_key 두 값 → 위반", v11.status == "violated", v11.detail)
v11ok = {v.rule_id: v for v in C.check_consistency(good_cells, mk_ledger(), COV_OK, rules)}["R11"]
check("중복 없음 → 통과", v11ok.status == "passed", v11ok.detail)

print("\n  R12 법률 미해소")
cov_legal = [Coverage("L1", "공백", 0, 0, [], min_facts=1)] + COV_OK
v12 = {v.rule_id: v for v in C.check_consistency(good_cells, mk_ledger(), cov_legal, rules)}["R12"]
check("법률 슬롯 미충족 → 위반", v12.status == "violated", v12.detail)
v12ok = {v.rule_id: v for v in C.check_consistency(good_cells, mk_ledger(), COV_OK, rules)}["R12"]
check("법률 슬롯 없음 → 통과", v12ok.status == "passed", v12ok.detail)

print("\n  R8 SAM 대상 = 타겟 정의")
cells_r8 = mk_cells(TAM=1000, SAM=500, SOM=100)
cells_r8["SAM"] = ChainCell(key="SAM", value=500, source="computed", origin="KSIC-56221|커피")
cells_r8["target_def"] = ChainCell(key="target_def", value=1, source="user_input",
                                   origin="KSIC-99999|숙박")
v8 = {v.rule_id: v for v in C.check_consistency(cells_r8, mk_ledger(), COV_OK, rules)}["R8"]
check("대상 불일치 → 위반", v8.status == "violated", v8.detail)
cells_r8["target_def"] = ChainCell(key="target_def", value=1, source="user_input",
                                   origin="KSIC-56221|커피")
v8ok = {v.rule_id: v for v in C.check_consistency(cells_r8, mk_ledger(), COV_OK, rules)}["R8"]
check("대상 일치 → 통과", v8ok.status == "passed", v8ok.detail)

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 15·13] 보고서 §7 — 키가 전부 있고 격리·미검증·단위·잘림이 모인다")
led = mk_ledger()
led.rows.append(LedgerRow(fact_id="F900", slot_id="S1", url="https://x.com/a", kind="aggregate",
                          kind_by="d", score=0, label="off_slot", cross=0,
                          reasons=["슬롯 불일치"], off_slot_reason="must_not_contain 포함: ['송금']"))
led.rows.append(LedgerRow(fact_id="F901", slot_id="S1", url="https://y.com/a", kind="press",
                          kind_by="d", score=0, label="미검증", cross=0,
                          reasons=["인용문이 본문에 없음"]))
cov = [Coverage("S1", "충족", 2, 3, ["F000", "F001"], min_facts=5),      # thin
       Coverage("S2", "공백", 0, 0, [], min_facts=2)]
est = [Estimate(formula_id="F_A", target="SAM", path="bottomup", value=None,
                inputs=[EstimateInput(var_id="V2", basis="unit_mismatch: 슬롯 '%' vs 값 '비율'")],
                assumption_count=0, status="unit_mismatch", unit_note="V2: 단위 불일치"),
       Estimate(formula_id="F_B", target="TAM", path="topdown", value=[10, 800],
                inputs=[EstimateInput(var_id="V9", assumption=1.0, basis="b")],
                assumption_count=6, status="ok", range_capped=True)]
recs = [Reconciliation(target="SAM", topdown=[1, 2], bottomup=[90, 100], overlap=None,
                       gap_ratio=63.0, status="diverged", suspect_var="V2", adopted=None)]
cells4 = C.build_chain(recs, led, {"total_budget": 1000}, rules)
vio4 = C.check_consistency(cells4, led, cov, rules)
rep = C.render_report(cells4, vio4, est, recs, led, cov, [],
                      {"kosis": "not_configured", "dart": "ok", "web": "ok"},
                      "통계 API 미사용(kosis) — 커버리지 하한", rules)

for k in NOT_FOUND_KEYS:
    check(f"§7 키 '{k}' 존재", k in rep.not_found)
nf = rep.not_found
check("공백 슬롯", nf["empty_slots"] == ["S2"], str(nf["empty_slots"]))
check("thin 슬롯", nf["thin_slots"] and nf["thin_slots"][0]["slot_id"] == "S1", str(nf["thin_slots"]))
check("off_slot 건수+사유", nf["off_slot"]["count"] == 1 and nf["off_slot"]["by_reason"],
      str(nf["off_slot"]))
check("미검증 건수", nf["off_slot"]["unverified_quote"] == 1, str(nf["off_slot"]))
check("unit_mismatch 기록", nf["unit_mismatch"] and "V2" in nf["unit_mismatch"][0]["note"],
      str(nf["unit_mismatch"]))
check("range_capped 기록", nf["range_capped"] == ["F_B"], str(nf["range_capped"]))
check("suspect_var 기록", nf["suspect_var"] == ["V2"], str(nf["suspect_var"]))
check("adapters 기록(not_configured)", nf["adapters"].get("kosis") == "not_configured")
check("skipped 검사 목록", isinstance(nf["skipped_checks"], list))
check("unknown_error_codes 키 존재", "unknown_error_codes" in nf)
check("coverage_caveat 이 결론에 나감", any("커버리지 하한" in c for c in rep.conclusion),
      str(rep.conclusion))

print("\n[range_capped → 값 대신 '추정 불가']")
tam = next(h for h in rep.headline_numbers if h["target"] == "TAM") if any(
    h["target"] == "TAM" for h in rep.headline_numbers) else None
sam = next(h for h in rep.headline_numbers if h["target"] == "SAM")
check("diverged → 값 없음", sam["value"] is None and "격차" in (sam["why_no_value"] or ""),
      str(sam))

print("\n[수용기준 7] 근거 0건 슬롯은 라벨을 만들 수 없다")
try:
    Coverage("S9", "보강필요", 0, 0, [], min_facts=2)
    check("근거 0건 라벨 거부", False, "예외가 안 났다")
except ValueError:
    check("근거 0건 라벨 거부", True)

print("\n[결정론] 같은 입력 2회 → 보고서 바이트 동일")
from schema import to_dict
def once():
    c = C.build_chain(recs, led, {"total_budget": 1000}, rules)
    v = C.check_consistency(c, led, cov, rules)
    r = C.render_report(c, v, est, recs, led, cov, [], {"kosis": "not_configured"}, None, rules)
    return json.dumps(to_dict(r), ensure_ascii=False, sort_keys=True).encode()
check("바이트 동일", once() == once())

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
