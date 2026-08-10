# -*- coding: utf-8 -*-
"""단계 8 검증 — A1 슬롯 검증기 + 기간 창. **LLM 0회.**

full-01 에서 A1 이 만든 슬롯 27개가 이랬다:
  · `period` 27/27 이 2022 (as_of 2026) — 그 연도가 **검색 쿼리에 그대로 박혀** 수집이
    통째로 2022 에 묶였다 (a3_candidate.from_query = "... 매출 2022")
  · `subject` 가 "서비스 침투율" 같은 검색 불가 문자열 — 검색이 반도체 자료를 물어왔다
  · `must_contain` 25/27 빈칸 · 단위 '백분율' 이라 % 값범위를 못 받음 · malformed stat_code

형식만 맞으면 통과하던 자리다. 여기서 내용을 강제한다.

    python tests/test_step8.py
"""
from __future__ import annotations
import os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import a_design as A1
import a_desk as A4
from runlog import load_rules
from schema import Concept, Document, Fact, Formula, FormulaVar, Slot

rules = load_rules()
AS_OF = 2026
ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


CPT = Concept(concept_id="C1", name="카페 원두 재고·발주 자동화 SaaS",
              problem="개인 카페 사장이 원두 재고를 감으로 파악한다",
              target="서울에서 매장 1~3개를 운영하는 개인 카페 사장",
              solution="POS 데이터로 발주를 제안한다", region="대한민국")


def S(sid="S01", **kw):
    base = dict(var_id="V1", formula_id="F1", claim_type="TAM",
                subject="서비스 침투율", metric="비율", period="2022", unit="백분율")
    base.update(kw)
    return Slot(slot_id=sid, **base)


# ══════════════════════════════════════════════════════════════
print("[주제어] concept 에서 뽑는다 — 코드에 업종 상수를 박지 않는다")
topics = A1.topic_words(CPT, rules)
check("주제어가 나온다", bool(topics), str(topics))
check("업종어를 잡는다", any(t in ("카페", "원두", "재고") for t in topics), str(topics))
check("불용어는 뺀다", not any(t in ("서비스", "자동화", "시장") for t in topics), str(topics))

# ══════════════════════════════════════════════════════════════
print("\n[period] 절대연도를 박지 않는다 — 이 값이 검색 쿼리로 나간다")
kept, disc, fixes = A1.enforce_slot_rules([S(metric="비율"), S("S02", metric="사업체 수")],
                                          CPT, rules, AS_OF)
check("2022 를 as_of 기준으로 옮긴다", kept[0].period == "2025", kept[0].period)
check("통계 지연 계열은 as_of-2", kept[1].period == "2024", kept[1].period)
check("무엇을 고쳤는지 남는다", any(f["what"] == "period" for f in fixes))

print("\n  ⚠ 기간 창의 하한이 신선 경계 아래로 내려가면 천장이 되살아난다")
fresh = rules["scoring"]["fresh_years"]
tol = rules["scoring"]["off_slot"]["period_tolerance_years"]
check(f"as_of-1 슬롯: 창이 신선 경계와 맞물린다 (lo={AS_OF - fresh})",
      kept[0].period_min == AS_OF - fresh, f"{kept[0].period_min}~{kept[0].period_max}")
check("as_of-2 슬롯도 하한이 잘린다 (±tol 이면 2022 가 새어든다)",
      kept[1].period_min == AS_OF - fresh,
      f"{kept[1].period_min}~{kept[1].period_max} (tol={tol})")
check("상한은 자르지 않는다", kept[1].period_max == 2024 + tol, str(kept[1].period_max))

# 창이 없으면 무엇도 통과시키지 않는 게 아니라 **판정하지 않는다** (두 번 벌하지 않는다)
bare = S("S99")
bare.period_min = bare.period_max = None
# 인용문에 주제어를 넣어둔다 — off_slot 은 1겹(must_contain)부터 순서대로 보므로
# 그게 걸리면 5겹(기간)까지 오지 않는다. 여기서 재려는 건 기간 겹이다.
f_old = Fact(fact_id="F1", slot_id="S99", var_id="V1", trace_id="T", url="https://x/y",
             quote="카페 원두 침투율은 50%다", value_num=50.0, unit_norm="%", year=2015,
             dedup_key="x", match_key="m", quote_verified=True, content_status="usable")
check("창이 없는 슬롯은 기간으로 벌하지 않는다",
      A4.off_slot_reason(f_old, bare, None, rules) is None,
      str(A4.off_slot_reason(f_old, bare, None, rules)))

kept2, _, _ = A1.enforce_slot_rules([S("S03", metric="비율")], CPT, rules, AS_OF)
f_old.slot_id = "S03"
check("창 밖이면 기간 불일치로 격리",
      "기간 불일치" in str(A4.off_slot_reason(f_old, kept2[0], None, rules)))
f_new = Fact(**{**f_old.__dict__, "year": 2025})
check("창 안이면 통과", A4.off_slot_reason(f_new, kept2[0], None, rules) is None,
      str(A4.off_slot_reason(f_new, kept2[0], None, rules)))

# ══════════════════════════════════════════════════════════════
print("\n[subject] subject 가 곧 검색 쿼리다")
check("주제어를 앞에 붙인다", any(t in kept[0].subject for t in topics), kept[0].subject)
already = A1.enforce_slot_rules([S("S04", subject="카페 사업체 수")], CPT, rules, AS_OF)[0][0]
check("이미 주제어가 있으면 안 건드린다", already.subject == "카페 사업체 수", already.subject)
check("지역을 중복시키지 않는다", "대한민국" not in kept[0].subject, kept[0].subject)
# 쿼리는 subject + metric 을 이어 붙인다 — 주제어 유무도 둘을 합쳐서 봐야 중복이 안 난다
in_metric = A1.enforce_slot_rules([S("S10", subject="상위시장규모", metric="카페 및 커피숍")],
                                  CPT, rules, AS_OF)[0][0]
check("metric 에 주제어가 있으면 subject 에 또 붙이지 않는다",
      in_metric.subject == "상위시장규모", in_metric.subject)

# ══════════════════════════════════════════════════════════════
print("\n[must_contain] 가드 0 을 면하는 하한선이다 (성능 개선이 아니다)")
check("빈칸이면 주제어로 채운다", bool(kept[0].must_contain), str(kept[0].must_contain))
check("2개를 넘기지 않는다", len(kept[0].must_contain) <= 2, str(kept[0].must_contain))
human = A1.enforce_slot_rules([S("S05", must_contain=["커피"])], CPT, rules, AS_OF)[0][0]
check("사람이 적은 것이 이긴다", human.must_contain == ["커피"], str(human.must_contain))
# 남의 시장을 묻는 슬롯에 우리 주제어를 요구하면 멀쩡한 자료를 우리가 버린다.
# (full-03: must_contain 격리 6건이 전부 COMP·PAIN 이었다)
for ct in ("COMP", "COMPARABLE", "PAIN"):
    other = A1.enforce_slot_rules([S("S0X", claim_type=ct)], CPT, rules, AS_OF)[0][0]
    check(f"{ct} 은 must_contain 을 안 채운다", other.must_contain == [],
          str(other.must_contain))
check("TAM 은 그대로 채운다", bool(kept[0].must_contain), str(kept[0].must_contain))

# ══════════════════════════════════════════════════════════════
print("\n[stat_code] 틀린 코드는 있는 것보다 없는 게 낫다")
bad = A1.enforce_slot_rules([S("S06", stat_code="KOSIS/115301")], CPT, rules, AS_OF)[0][0]
good = A1.enforce_slot_rules([S("S07", stat_code="101/DT_1B040B3")], CPT, rules, AS_OF)[0][0]
check("malformed → None", bad.stat_code is None, str(bad.stat_code))
check("형식 맞으면 그대로", good.stat_code == "101/DT_1B040B3", str(good.stat_code))

# ══════════════════════════════════════════════════════════════
print("\n[폐기] 검색할 수 없는 슬롯은 버리고 이유를 남긴다")
kept3, disc3, _ = A1.enforce_slot_rules([S("S08", subject=""), S("S09")], CPT, rules, AS_OF)
check("subject 없으면 폐기", len(kept3) == 1 and len(disc3) == 1, f"{len(kept3)}/{len(disc3)}")
check("왜 버렸는지 남는다", bool(disc3[0].get("why")), str(disc3))

# ══════════════════════════════════════════════════════════════
print("\n[작업 1] 단위를 가드 적용 **앞**에서 정규화한다")
fml = [Formula(formula_id="F1", target="TAM", path="topdown", template="T1",
               vars=[FormulaVar(var_id="V1", var_role="ratio", subject="침투율",
                                metric="비율", period="2022", unit="백분율"),
                     FormulaVar(var_id="V2", var_role="count", subject="사업체 수",
                                metric="사업체 수", period="2022", unit="개")])]
slots, _ = A1.slots_from_formulas(fml, CPT, [], rules)
pct_slot = slots[0]
check("'백분율' 이 '%' 로 정규화된다", pct_slot.unit == "%", pct_slot.unit)
check("% 슬롯이 값범위 [0,100] 을 받는다", pct_slot.value_range == [0, 100],
      str(pct_slot.value_range))
check("% 아닌 슬롯은 claim_type 기본값", slots[1].value_range ==
      rules["guards"]["by_claim_type"]["TAM"]["value_range"], str(slots[1].value_range))

# ══════════════════════════════════════════════════════════════
# 단계 9 — '답이 나올 수 없는 슬롯' 과 주제어 오탐. 둘 다 full-03 에서 실측된 것이다.
# ══════════════════════════════════════════════════════════════
print("\n[폐기 사유] 코드로 남는다 — 무엇을 왜 안 던졌는지가 §7 까지 가야 한다")
kept4, disc4, _ = A1.enforce_slot_rules(
    [S("S15", subject="카페 유사 서비스 통계", metric="값", unit=None),
     S("S17", subject="재고 문제 통계", metric="값", unit="건"),
     S("S20", subject="개인 카페", metric="사업체 수", unit="개")], CPT, rules, AS_OF)
codes = {d["slot_id"]: d.get("code") for d in disc4}
check("metric '값' 은 폐기", codes.get("S17") == "metric_not_indicative", str(codes))
check("단위 없어도 폐기", codes.get("S15") in ("metric_not_indicative", "unit_missing"),
      str(codes))
check("멀쩡한 슬롯은 남는다", [s.slot_id for s in kept4] == ["S20"],
      str([s.slot_id for s in kept4]))
check("사유가 코드와 문장 둘 다", all(d.get("code") and d.get("why") for d in disc4), str(disc4))

unit_only, _, _ = A1.enforce_slot_rules([S("S16", subject="카페 매출", metric="매출",
                                           unit=None)], CPT, rules, AS_OF)
check("단위만 없어도 폐기된다", unit_only == [], str([s.slot_id for s in unit_only]))

print("\n[폐기 자리] '발행 기관 없음' 은 아직 꺼져 있어야 한다")
np = rules["slotcheck"]["discard"]["reasons"]["no_publisher"]
check("no_publisher 는 enabled=false", np["enabled"] is False, str(np.get("enabled")))

print("\n[용어] must_contain 에 통계표가 쓰는 말을 이어 붙인다")
filled = A1.enforce_slot_rules([S("S21", subject="개인 카페", metric="사업체 수",
                                  unit="개", claim_type="TAM")], CPT, rules, AS_OF)[0][0]
check("주제어가 먼저 온다", filled.must_contain[0] == "카페", str(filled.must_contain))
check("'커피전문점' 이 따라 들어간다", "커피전문점" in filled.must_contain,
      str(filled.must_contain))
check("max_filled 를 넘지 않는다",
      len(filled.must_contain) <= rules["slotcheck"]["must_contain"]["max_filled"],
      str(filled.must_contain))

print("\n[오탐] '카페24' 의 '카페' 는 주제어로 세지 않는다")
CAFE24 = ("상품등록 성장하는 비즈니스에 맞춰 유연한 확장 지원. 카페24 쇼핑몰 요금제는 "
          "월 9,900원부터 시작합니다.")
KOSIS = "2024년 커피전문점 사업체수는 10만 3,000개로 전년 대비 증가했다."
d24 = Document(slot_id="S1", trace_id="T1", url="https://www.cafe24.com/pricing",
               text=CAFE24, content_status="usable")
dks = Document(slot_id="S1", trace_id="T2", url="https://kosis.kr/x",
               text=KOSIS, content_status="usable")
sl = S("S01", subject="개인 카페", metric="사업체 수", unit="개",
       must_contain=["카페", "커피전문점"])
def FACT(fid, tid, url, quote, val):
    return Fact(fact_id=fid, slot_id="S01", var_id="V1", trace_id=tid, url=url,
                quote=quote, value_num=val, unit_norm="개", year=2024,
                dedup_key=url, match_key="k", quote_verified=True, content_status="usable")


f24 = FACT("F1", "T1", d24.url, "월 9,900원", 9900.0)
fks = FACT("F2", "T2", dks.url, "10만 3,000개", 103000.0)
r24 = A4.off_slot_reason(f24, sl, d24, rules)
rks = A4.off_slot_reason(fks, sl, dks, rules)
check("카페24 문서는 must_contain 으로 격리", r24 is not None and "must_contain" in r24, str(r24))
check("커피전문점 문서는 통과", rks is None, str(rks))
check("'카페24' 가 지워진다", "카페" not in A4.mask_false_friends("카페24 쇼핑몰", rules),
      A4.mask_false_friends("카페24 쇼핑몰", rules))
check("진짜 '카페' 는 남는다", "카페" in A4.mask_false_friends("동네 카페 사장", rules),
      A4.mask_false_friends("동네 카페 사장", rules))
# must_contain 은 통과하되(진짜 '카페' 가 있다) 금지어가 걸려야 한다 — 가린 말이
# 금지어면 금지가 조용히 풀리는데, 그러면 안 된다.
dmix = Document(slot_id="S1", trace_id="T3", url="https://ex.com/a", content_status="usable",
                text="동네 카페 사장 인터뷰. 쇼핑몰은 카페24 로 만들었다. 매장 3개.")
fmix = FACT("F3", "T3", dmix.url, "매장 3개", 3.0)
sl_ban = S("S02", subject="개인 카페", metric="사업체 수", unit="개",
           must_contain=["카페"], must_not_contain=["카페24"])
rban = A4.off_slot_reason(fmix, sl_ban, dmix, rules)
check("must_not_contain 은 가리지 않는다 — 금지가 조용히 풀리면 안 된다",
      rban is not None and "must_not_contain" in rban, str(rban))

# ══════════════════════════════════════════════════════════════
# 사람 칸 덮어쓰기 — 파생 실행은 슬롯을 원본 result.json 에서 복원하므로
# data/slots.json 이 구조적으로 도달하지 않았다. 사람이 must_contain 을 고쳐도
# 재채점으로 잴 수 없었고, 그건 '재채점은 공짜다' 라는 지렛대가 사람 칸에만 안 걸린 것이다.
# ══════════════════════════════════════════════════════════════
print("\n[overlay] 기본값은 아무것도 바꾸지 않는다")
HUMAN = [{"claim_type": "TAM", "metric": "사업체 수",
          "must_contain": ["커피", "비알콜음료점업"], "must_not_contain": ["송금"],
          "value_range": [10000, 500000], "accept": {"min_facts": 3}}]


def _slot():
    return S("S03", claim_type="TAM", metric="사업체 수", subject="개인 카페", unit="개",
             must_contain=["커피"], must_not_contain=[], value_range=[1, 2],
             accept={"min_score": 5, "min_sources": 2, "min_facts": 2, "min_confirmed": 1})


import copy
base = _slot()
untouched, diff0 = A1.overlay_human_slots([copy.deepcopy(base)], None)
check("사람 슬롯이 없으면 그대로", untouched[0].__dict__ == base.__dict__, str(diff0))
check("diff 도 비어 있다", diff0 == [], str(diff0))

print("\n[overlay] current 면 사람 칸 4개만 바뀐다")
over, diff = A1.overlay_human_slots([_slot()], HUMAN)
o = over[0]
check("must_contain 이 바뀐다", o.must_contain == ["커피", "비알콜음료점업"], str(o.must_contain))
check("must_not_contain 이 바뀐다", o.must_not_contain == ["송금"], str(o.must_not_contain))
check("value_range 가 바뀐다", o.value_range == [10000, 500000], str(o.value_range))
check("accept.min_facts 만 바뀐다",
      o.accept["min_facts"] == 3 and o.accept["min_score"] == 5, str(o.accept))
for f in ("subject", "metric", "period", "unit", "stat_code", "claim_type"):
    check(f"수집 조건 '{f}' 는 안 건드린다", getattr(o, f) == getattr(base, f), str(getattr(o, f)))

print("\n[overlay] 무엇이 바뀌었는지 diff 로 남는다")
ch = diff[0]["changed"]
check("바뀐 슬롯만 diff 에 든다", [d["slot_id"] for d in diff] == ["S03"], str(diff))
check("칸마다 from·to 가 있다",
      all("from" in v and "to" in v for v in ch.values()), str(ch))
check("accept.min_facts 도 기록된다", ch["accept.min_facts"]["to"] == 3, str(ch))
check("안 바뀐 칸은 diff 에 없다", "subject" not in ch and "unit" not in ch, str(list(ch)))

nochange, diff_same = A1.overlay_human_slots(
    [S("S9", claim_type="TAM", metric="사업체 수", must_contain=["커피", "비알콜음료점업"],
       must_not_contain=["송금"], value_range=[10000, 500000],
       accept={"min_facts": 3})], HUMAN)
check("값이 이미 같으면 diff 를 만들지 않는다", diff_same == [], str(diff_same))

# ══════════════════════════════════════════════════════════════
# content_status — 길이·숫자로 못 잡는 껍데기. fixed-01 에서 '로딩중입니다' 616자에
# 숫자 26개인 서울 통계표 4건이 usable 로 통과해 not_found 의 분모를 부풀렸다.
# ══════════════════════════════════════════════════════════════
print("\n[껍데기] 로딩 안내가 맨 앞에 있으면 usable 이 아니다")
SEOUL = ("로딩중입니다.잠시만 기다려주시기 바랍니다. \t\t\t\t\t\t통계표 조회 \t\t\t\t\tSEOUL "
         "주석정보X 닫기 다운로드X 닫기 EXCEL(xlsx) EXCEL(xls) ( 셀 병합 ) " + "항목 " * 60)
check("'로딩중' 616자·숫자 있어도 js_shell",
      A4.classify_content(SEOUL, rules["scoring"])[0] == "js_shell",
      str(A4.classify_content(SEOUL, rules["scoring"])))
XBRL = "잠시만 기다려주세요. 코 카페24 다운로드 영문보기 닫기 XBRL 뷰어 및 파일 다운로드에서 " + "안내 " * 100
check("XBRL 뷰어 껍데기도 js_shell",
      A4.classify_content(XBRL, rules["scoring"])[0] == "js_shell")

print("\n  ⚠ 키워드만 보면 오분류한다 — 위치를 본다")
REAL = ("2024년 커피전문점 사업체수는 10만 3,000개로 전년 대비 2.6% 증가했다. " * 40
        + " loading complete")
check("진짜 문서 뒤쪽의 'loading' 은 무시", A4.classify_content(REAL, rules["scoring"])[0] == "usable",
      str(A4.classify_content(REAL, rules["scoring"])))
check("is_loading_shell 은 앞부분만 본다",
      A4.is_loading_shell("로딩중입니다 " + "x" * 500, rules["scoring"]) is True
      and A4.is_loading_shell("x" * 500 + " 로딩중입니다", rules["scoring"]) is False)

print("\n  ⚠ 짧지만 진짜인 문서를 내리지 않는다 (실측: 388자 기사에 숫자 38개)")
SHORT_REAL = "국내 커피전문점 수는 2024년 10만 3,000개다. 전년 대비 2.6% 늘었다. " * 6
check("짧고 숫자 있는 진짜 본문은 usable",
      A4.classify_content(SHORT_REAL, rules["scoring"])[0] == "usable",
      str(A4.classify_content(SHORT_REAL, rules["scoring"])))

print("\n  규칙이 비면 아무것도 강등하지 않는다 (조용한 오작동 방지)")
import copy as _copy
_r = _copy.deepcopy(rules["scoring"])
_r["content_status"]["js_shell"]["loading_keywords"] = []
check("loading_keywords 가 비면 False", A4.is_loading_shell(SEOUL, _r) is False)

# ══════════════════════════════════════════════════════════════
# DART 계정 겹 — full-04 에서 카페24 '당기법인세자산 2,456,770원' 이
# 「경쟁사의 매출」 확인됨 5점이 됐다. 재무상태표 계정이 매출 슬롯에 들어온 것이다.
# ══════════════════════════════════════════════════════════════
print("\n[계정] 매출 슬롯에 재무상태표 계정이 들어오면 격리한다")


def DFACT(aid, sj, val):
    return Fact(fact_id="F1", slot_id="S01", var_id="V1", trace_id="T", url="https://dart.fss.or.kr/x",
                quote=f'"thstrm_amount": "{val}"', value_num=float(val), unit_norm="원",
                year=2025, dedup_key="d", match_key="m", quote_verified=True,
                content_status="usable", channel="dart_api", account_id=aid, sj_div=sj)


SREV = S("S01", claim_type="COMP", subject="카페 상위N사매출합", metric="경쟁사의 매출",
         unit="원", must_contain=[], value_range=[1, 100000000])
SREV_WIDE = S("S01", claim_type="COMP", subject="카페 상위N사매출합", metric="경쟁사의 매출",
              unit="원", must_contain=[], value_range=[1, 10 ** 15])
TAX = DFACT("ifrs-full_CurrentTaxAssetsCurrent", "BS", 2456770)
REV = DFACT("ifrs-full_Revenue", "CIS", 314764225560)
tax = A4.off_slot_reason(TAX, SREV, None, rules)
check("당기법인세자산(BS) → 계정 불일치", tax is not None and "계정 불일치" in tax, str(tax))
check("영업수익(CIS, ifrs-full_Revenue) → 계정 겹 통과",
      A4._account_mismatch(REV, SREV, rules) is None, str(A4._account_mismatch(REV, SREV, rules)))
check("범위가 맞으면 영업수익은 격리되지 않는다",
      A4.off_slot_reason(REV, SREV_WIDE, None, rules) is None,
      str(A4.off_slot_reason(REV, SREV_WIDE, None, rules)))

print("\n  ⚠ 계정 겹은 값범위보다 **앞**이어야 한다")
# full-04 의 실제 경로: 큰 값(자산총계 415조)은 값범위 밖으로 걸리고 하필 작은 오답만
# 살아남았다. 값범위는 크기만 보므로 **틀린 계정을 크기로 거르면 작은 오답이 통과한다.**
check("틀린 계정은 값범위 **안**이어도 계정으로 걸린다",
      "계정 불일치" in str(A4.off_slot_reason(TAX, SREV, None, rules)))
check("값범위를 넓혀도 틀린 계정은 여전히 걸린다",
      "계정 불일치" in str(A4.off_slot_reason(TAX, SREV_WIDE, None, rules)),
      str(A4.off_slot_reason(TAX, SREV_WIDE, None, rules)))

print("\n  계정 정체가 없으면 판정하지 않는다 (web 경로 — 없는 기준으로 벌하지 않는다)")
web = Fact(fact_id="F2", slot_id="S01", var_id="V1", trace_id="T", url="https://ex.com/a",
           quote="매출 3천억", value_num=300.0, unit_norm="원", year=2025, dedup_key="d",
           match_key="m", quote_verified=True, content_status="usable")
check("account_id 가 비면 계정 겹 없음", A4._account_mismatch(web, SREV, rules) is None)

print("\n  계정을 묻지 않는 슬롯은 계정 겹을 적용하지 않는다")
SCNT = S("S02", claim_type="TAM", subject="개인 카페", metric="사업체 수", unit="개")
check("'사업체 수' 슬롯엔 계정 규칙이 없다",
      A4._account_mismatch(DFACT("ifrs-full_Assets", "BS", 100), SCNT, rules) is None)

print("\n[계정] 표가 metric 을 못 맞추면 **조용히 전부 통과가 아니라** 멈춘다")
sys.path.insert(0, os.path.join(ROOT, "adapters"))
import dart as DART  # noqa: E402
DCFG = rules["adapters"]["dart"]
check("'경쟁사의 매출' 이 규칙에 걸린다 (매칭 방향 — 낱말이 metric 안에)",
      DART._account_rule("경쟁사의 매출", DCFG) is not None)
check("옛 방향('매출액' == metric)이면 못 걸렸을 것",
      "매출액" not in "경쟁사의 매출")
check("못 맞추면 None 이다 — 빈 집합이 아니다(빈 집합이면 가드가 풀린다)",
      DART._account_rule("가입 매장 수", DCFG) is None)
check("unmapped_metric 은 멈춤(stopped)이다",
      rules["adapters"]["failure_map"]["unmapped_metric"]["adapter_state"] == "stopped",
      str(rules["adapters"]["failure_map"].get("unmapped_metric")))
check("계정 매칭은 account_id 기준이다 (한글 계정명 아님 — 카페24는 '영업수익')",
      all("ifrs-full" in i or "dart_" in i
          for r in DCFG["accounts"]["by_metric"] for i in r["account_ids"]),
      str(DCFG["accounts"]["by_metric"]))

# ══════════════════════════════════════════════════════════════
# 값범위와 범위 꼬리표 — 상장사 매출이 들어올 수 있어야 하고,
# 들어오면 '전사 매출' 이라는 사실이 값 옆에 붙어 있어야 한다.
# ══════════════════════════════════════════════════════════════
print("\n[값범위] 화폐 단위 슬롯은 claim_type 기본 상한(1억)을 쓰지 않는다")
fml_won = [Formula(formula_id="F9", target="COMP", path="topdown", template="T1",
                   vars=[FormulaVar(var_id="V1", var_role="revenue", subject="경쟁사",
                                    metric="경쟁사의 매출", period="2025", unit="원"),
                         FormulaVar(var_id="V2", var_role="count", subject="경쟁사",
                                    metric="가입 매장 수", period="2025", unit="곳")])]
won_slots, _ = A1.slots_from_formulas(fml_won, CPT, [], rules)
CUR = rules["guards"]["_currency"]
check("원 슬롯이 화폐 범위를 받는다", won_slots[0].value_range == CUR["value_range"],
      str(won_slots[0].value_range))
check("카페24 영업수익 3,147억이 범위 안",
      won_slots[0].value_range[0] <= 314764225560 <= won_slots[0].value_range[1])
check("화폐가 아닌 슬롯은 claim_type 기본값 그대로",
      won_slots[1].value_range == rules["guards"]["by_claim_type"]["COMP"]["value_range"],
      str(won_slots[1].value_range))
check("상한은 여전히 헛소리를 막는다 (1e15 초과)",
      not (won_slots[0].value_range[0] <= 10 ** 16 <= won_slots[0].value_range[1]))
check("% 가 원보다 먼저다 (단위가 이긴다, 충돌 없음)",
      "%" not in (CUR.get("units") or []))

print("\n[꼬리표] DART 전사 매출은 '시장 매출' 이 아니다 — 값 옆에 붙어 간다")
SWON = S("S01", claim_type="COMP", subject="카페 상위N사매출합", metric="경쟁사의 매출",
         unit="원", must_contain=[], value_range=CUR["value_range"])
rev_fact = Fact(fact_id="F1", slot_id="S01", var_id="V1", trace_id="T",
                url="https://dart.fss.or.kr/x", quote='"thstrm_amount": "314764225560"',
                value_num=314764225560.0, unit_norm="원", year=2025, dedup_key="d",
                match_key="m", quote_verified=True, content_status="usable",
                channel="dart_api", account_id="ifrs-full_Revenue", sj_div="CIS",
                scope="company_total")
led = A4.grade([rev_fact], {"S01": SWON}, {}, rules, 2026)
row = led.rows[0]
check("영업수익이 격리되지 않고 원장에 앉는다", row.label != "off_slot",
      f"{row.label} / {row.off_slot_reason}")
check("확인됨이다 (public_filing 5점)", row.label == "확인됨", f"{row.label} {row.score}")
check("scope 꼬리표가 원장 행에 있다", row.scope == "company_total", str(row.scope))
check("꼬리표 문구가 규칙 파일에서 온다",
      row.scope_note == rules["scoring"]["scope_labels"]["company_total"], row.scope_note)
check("문구가 '상한선' 이라고 말한다", "상한선" in row.scope_note, row.scope_note)

print("\n  꼬리표는 web 사실에는 안 붙는다 (모르는 것을 단정하지 않는다)")
web_fact = Fact(**{**rev_fact.__dict__, "fact_id": "F2", "scope": "",
                   "url": "https://ex.com/a", "channel": "web", "account_id": "", "sj_div": ""})
check("scope 가 비면 꼬리표도 빈다",
      A4.grade([web_fact], {"S01": SWON}, {}, rules, 2026).rows[0].scope_note == "")

# ══════════════════════════════════════════════════════════════
print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print("  X ", f)
sys.exit(1 if fail else 0)


