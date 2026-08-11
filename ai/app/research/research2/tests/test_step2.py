# -*- coding: utf-8 -*-
"""단계 2 검증 — A4 normalize_and_grade.

수용기준 4(파싱 50) · 5(중복 1건) · 6(match_key 다르면 가점 0) · 9(off_slot 격리)
        · 14(min_facts 미달 → thin) · 3의 일부(같은 원장 2회 → 바이트 동일)

    python tests/test_step2.py
"""
from __future__ import annotations
import io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "blocks"))

import a_desk as A
from runlog import load_rules
from schema import Document, Finding, FindingItem, Slot, to_dict

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


rules = load_rules()
ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


# ══════════════════════════════════════════════════════════════
print("[수용기준 4] 숫자 파싱 50 케이스")
cases = json.load(io.open(os.path.join(HERE, "cases_numbers.json"), encoding="utf-8"))["cases"]
print(f"  (케이스 {len(cases)}개)")
bad = []
for c in cases:
    got, unit, approx = A.parse_number(c["raw"], c["unit"], rules["units"])
    want = c["want"]
    if want is None:
        good = got is None
    else:
        good = got is not None and abs(got - want) < max(abs(want) * 1e-9, 1e-9)
        if good and c.get("want_unit") is not None:
            good = unit == c["want_unit"]
        if good and c.get("approx"):
            good = approx is True
    if not good:
        bad.append(f"{c['raw']!r}+{c['unit']!r} → {got}/{unit} (기대 {want}/{c.get('want_unit')})")
check(f"파싱 {len(cases)}케이스 전부 통과", not bad, f"실패 {len(bad)}건: " + "; ".join(bad[:5]))

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 5] 같은 URL 2건 → Fact 1건")
slot = Slot(slot_id="S1", var_id="V1", formula_id="F1", claim_type="TAM",
            subject="커피전문점", subject_code="KSIC-56221", metric="사업체 수",
            period="2023", unit="개", value_range=[1000, 500000],
            must_contain=["커피"], must_not_contain=["송금"])
BODY = "2023년 기준 국내 커피전문점 사업체 수는 10만 729개로 집계됐다. " * 30
doc = Document(slot_id="S1", trace_id="S1-q0-u0", url="https://kosis.kr/x?utm_source=openai",
               text=BODY, published_at_raw="2024-06-30", content_status="usable")
doc2 = Document(slot_id="S1", trace_id="S1-q1-u0", url="https://www.kosis.kr/x/",   # 같은 페이지
                text=BODY, published_at_raw="2024-06-30", content_status="usable")
item = FindingItem(quote="2023년 기준 국내 커피전문점 사업체 수는 10만 729개로 집계됐다.",
                   number_raw="10만 729", unit_raw="개")
f1 = Finding(slot_id="S1", trace_id="S1-q0-u0", status="found", findings=[item])
f2 = Finding(slot_id="S1", trace_id="S1-q1-u0", status="found", findings=[item])
facts = A.normalize([f1, f2], {"S1-q0-u0": doc, "S1-q1-u0": doc2}, {"S1": slot}, rules)
check("중복 제거", len(facts) == 1, f"{len(facts)}건")
check("utm·www·끝슬래시 정규화", facts[0].dedup_key == "kosis.kr/x", facts[0].dedup_key)
check("인용문 대조 통과", facts[0].quote_verified is True)
check("값·단위·연도", (facts[0].value_num, facts[0].unit_norm, facts[0].year) == (100729.0, "개", 2023),
      str((facts[0].value_num, facts[0].unit_norm, facts[0].year)))

# ⚠ 연도는 **숫자 경계 안에서만** 연도다. 경계가 없으면 값 20264 안의 '2026' 을 사실 연도로
#   집는다 — route12-02 에서 서울 커피전문점 사업체 수 20,264개가 정확히 그렇게 기간 겹에
#   격리됐다. 조용히 틀리는 종류다: match_key 도 같이 갈려 교차확인이 안 붙는다.
check("값 안의 숫자를 연도로 집지 않는다", A.parse_year('"DT": "20264"') is None,
      str(A.parse_year('"DT": "20264"')))
check("  8자리 날짜도 연도가 아니다", A.parse_year("20230321") is None)
check("  진짜 연도는 여전히 집는다",
      (A.parse_year("2023년 기준"), A.parse_year("PRD_DE: 2023"),
       A.parse_year("2023-03-21")) == (2023, 2023, 2023))

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 6] match_key 가 다르면 교차확인 가점 0")
slot_b = Slot(slot_id="S2", var_id="V2", formula_id="F1", claim_type="TAM",
              subject="커피전문점", subject_code="KSIC-56221", metric="사업체 수",
              period="2023", unit="개", value_range=[1000, 500000], must_contain=["커피"])
BODY_B = "2019년 국내 커피전문점 사업체 수는 8만 3,445개였다. " * 30
docb = Document(slot_id="S2", trace_id="S2-q0-u0", url="https://hankyung.com/a",
                text=BODY_B, published_at_raw="2019-01-01", content_status="usable")
fb = Finding(slot_id="S2", trace_id="S2-q0-u0", status="found", findings=[
    FindingItem(quote="2019년 국내 커피전문점 사업체 수는 8만 3,445개였다.",
                number_raw="8만 3,445", unit_raw="개")])
# 같은 subject·metric 이지만 **연도가 달라 match_key 가 다르다** → 서로를 보증하면 안 된다 (F4)
facts_x = A.normalize([f1, fb], {"S1-q0-u0": doc, "S2-q0-u0": docb},
                      {"S1": slot, "S2": slot_b}, rules)
led_x = A.grade(facts_x, {"S1": slot, "S2": slot_b},
                {"S1-q0-u0": doc, "S2-q0-u0": docb}, rules, 2026)
check("서로 다른 match_key", facts_x[0].match_key != facts_x[1].match_key,
      f"{facts_x[0].match_key} vs {facts_x[1].match_key}")
check("교차확인 가점 0", all(r.cross < 2 for r in led_x.rows), str([r.cross for r in led_x.rows]))

print("\n  (대조) 같은 match_key + 다른 도메인 → 가점 +1")
docc = Document(slot_id="S1", trace_id="S1-q2-u0", url="https://mods.go.kr/b",
                text=BODY, published_at_raw="2024-01-01", content_status="usable")
fc = Finding(slot_id="S1", trace_id="S1-q2-u0", status="found", findings=[item])
facts_c = A.normalize([f1, fc], {"S1-q0-u0": doc, "S1-q2-u0": docc}, {"S1": slot}, rules)
led_c = A.grade(facts_c, {"S1": slot}, {"S1-q0-u0": doc, "S1-q2-u0": docc}, rules, 2026)
check("교차확인 +1 적용", any("교차 확인" in " ".join(r.reasons) for r in led_c.rows),
      str([r.reasons for r in led_c.rows]))

# ── 화자 동일성 가드 (12-2 앞 설계 확인) ──────────────────────────
# 도메인이 다르다고 화자가 다른 것이 아니다. 회사 공식 페이지와 그 회사가 낸 보도자료
# 배포본은 **도메인 둘, 화자 하나**다. 막지 않으면 official_page(4)+press_release 가
# +1 을 받아 **자기 말만으로 확인됨(5)** 이 된다.
print("\n  자기발표끼리는 교차확인을 주고받지 못한다 (화자가 하나다)")


def _pair(url_a, url_b, slot_):
    """같은 값·같은 match_key 를 서로 다른 도메인에서 낸 두 사실을 만든다."""
    da = Document(slot_id=slot_.slot_id, trace_id="A", url=url_a, text=BODY,
                  published_at_raw="2024-01-01", content_status="usable")
    db = Document(slot_id=slot_.slot_id, trace_id="B", url=url_b, text=BODY,
                  published_at_raw="2024-01-01", content_status="usable")
    fa = Finding(slot_id=slot_.slot_id, trace_id="A", status="found", findings=[item])
    fb_ = Finding(slot_id=slot_.slot_id, trace_id="B", status="found", findings=[item])
    fx = A.normalize([fa, fb_], {"A": da, "B": db}, {slot_.slot_id: slot_}, rules)
    return A.grade(fx, {slot_.slot_id: slot_}, {"A": da, "B": db}, rules, 2026)


led_self = _pair("https://about.cafe24wms.com/x", "https://newswire.co.kr/y", slot)
check("official_page + press_release → 화자 1 → 가점 없음",
      all(r.cross < 2 for r in led_self.rows), str([(r.kind, r.cross) for r in led_self.rows]))
check("  그래서 확인됨(5)에 못 닿는다",
      all(r.score < 5 for r in led_self.rows), str([(r.kind, r.score) for r in led_self.rows]))
# ⚠ 기각한 임시안(press_release 를 2점으로): cross_bonus 는 **상대의 kind 를 보지 않으므로**
#   점수를 낮춰도 그 짝인 official_page 는 4+1=5 가 된다. 막을 것은 점수가 아니라 가점 조건이다.
led_mix = _pair("https://about.cafe24wms.com/x", "https://mods.go.kr/y", slot)
check("독립 발행자와의 교차는 살아 있다 (official_page + gov_stat → +1)",
      any("교차 확인" in " ".join(r.reasons) for r in led_mix.rows),
      str([(r.kind, r.cross, r.reasons) for r in led_mix.rows]))
# ── 조인 F — **off_slot 과 교차 묶음이 같은 표를 읽는다** ────────────
# 실측(gate3-01): '매장'·'곳' 이 off_slot 에서는 호환으로 통과했는데 교차 묶음에서는
# 원문 일치로 갈려 화자가 3 → 2 로 줄었다. **한 규칙 표를 두 소비자가 다르게 읽으면
# 그 자체가 조인 버그다.**
print("\n  off_slot 이 호환이라 한 두 단위는 교차 묶음에서도 같은 그룹이다")
_su = Slot(slot_id="U2", var_id="V1", formula_id="F1", claim_type="COMP",
           subject="코케비즈", subject_code="COMP-KOKEBIZ", metric="누적 가입 매장 수",
           period="2026", unit="곳", must_contain=["코케비즈"], value_range=[100, 200000])
_BODY_A = "코케비즈 전국 2만 매장이 선택한 플랫폼이다. 2025년 기준. " * 12
_BODY_B = "코케비즈 누적 가입 매장이 2만 곳을 넘어섰다. 2025년 기준. " * 12
_d1 = Document(slot_id="U2", trace_id="A", url="https://sentv.co.kr/a", text=_BODY_A,
               published_at_raw="2025-10-30", content_status="usable")
_d2 = Document(slot_id="U2", trace_id="B", url="https://platum.kr/b", text=_BODY_B,
               published_at_raw="2025-10-30", content_status="usable")
_f1 = Finding(slot_id="U2", trace_id="A", status="found", findings=[
    FindingItem(quote="전국 2만 매장이 선택한", number_raw="2만", unit_raw="매장",
                url="https://sentv.co.kr/a")])
_f2 = Finding(slot_id="U2", trace_id="B", status="found", findings=[
    FindingItem(quote="누적 가입 매장이 2만 곳을 넘어섰다", number_raw="2만", unit_raw="곳",
                url="https://platum.kr/b")])
_fx = A.normalize([_f1, _f2], {"A": _d1, "B": _d2}, {"U2": _su}, rules)
_lx = A.grade(_fx, {"U2": _su}, {"A": _d1, "B": _d2}, rules, 2026)
check("두 단위가 off_slot 을 통과한다", all(r.label != "off_slot" for r in _lx.rows),
      str([(r.label, r.off_slot_reason) for r in _lx.rows]))
check("그리고 **교차 묶음에서도 같은 그룹**이다 (화자 2 → +1)",
      all(r.cross >= 2 for r in _lx.rows), str([(r.cross, r.reasons) for r in _lx.rows]))

# ── 연도 ⓑ — 본문 스캔. 발행일 fallback 과 다르다 ────────────────
print("\n  context 가 비면 **본문**에서 슬롯 기간 창 안의 연도를 찾는다")
check("본문 연도를 집는다", all(f.year == 2025 for f in _fx), str([(f.year, f.year_source) for f in _fx]))
check("  출처가 구분돼 기록된다",
      all("본문" in (f.year_source or "") for f in _fx), str([f.year_source for f in _fx]))
_BODY_2Y = "코케비즈 2만 매장. 2024년과 2025년 자료가 함께 있다. " * 12
_d3 = Document(slot_id="U2", trace_id="C", url="https://sentv.co.kr/c", text=_BODY_2Y,
               published_at_raw="2025-10-30", content_status="usable")
_f3 = Finding(slot_id="U2", trace_id="C", status="found", findings=[
    FindingItem(quote="코케비즈 2만 매장", number_raw="2만", unit_raw="매장",
                url="https://sentv.co.kr/c")])
_fy = A.normalize([_f3], {"C": _d3}, {"U2": _su}, rules)
check("창 안에 연도가 둘 이상이면 **고르지 않는다** (조용한 추측 금지)",
      _fy[0].year is None, str((_fy[0].year, _fy[0].year_source)))
check("발행일로 메우지 않는다 (published_at 2025 인데 year 는 None)",
      _fy[0].year is None and _fy[0].published_year == 2025)

check("규칙이 코드가 아니라 scoring.cross 에 있다",
      set(rules["scoring"]["cross"]["self_published_kinds"]) == {"official_page", "press_release"})

# ── 백로그 7 — 교차확인이 **값을 본다** (3겹) ──────────────────────
print("\n  값이 갈리면 서로를 보증하지 못한다 (F4 부분 재발을 막는다)")
_xc = rules["scoring"]["cross"]
# 2겹 — 반올림 표기와 정확값은 **같은 값**이다 (실측: full-03 의 95,000 ↔ 95,337)
check("95,000 ↔ 95,337 = 같은 값 (유효숫자 2자리)", A.same_value(95000, 95337, _xc))
check("  대칭이다", A.same_value(95337, 95000, _xc))
check("105,000 ↔ 115,000 = 다른 값 (서로 다른 요금제)", not A.same_value(105000, 115000, _xc))
check("  상대차는 5% 안이지만 유효숫자가 가른다 (두 조건은 상보적)",
      abs(115000 - 105000) / 115000 < 0.09 and not A.same_value(105000, 115000, _xc))
check("100,000 ↔ 149,000 = 다른 값 (유효숫자 1자리만 쓰면 붙어버린다)",
      not A.same_value(100000, 149000, _xc))
check("68.0 ↔ 757,000 = 다른 값", not A.same_value(68.0, 757000.0, _xc))
# 3겹 — 정확히 10^n 배는 같은 값이 아니라 **단위 오독 의심**
check("350 ↔ 350,000 = 스케일 의심 (1,000배)", A.is_scale_suspect(350, 350000, _xc))
check("  같은 값으로 접지 않는다 (조용한 변환 금지)", not A.same_value(350, 350000, _xc))
check("95,000 ↔ 95,337 은 스케일 의심이 아니다", not A.is_scale_suspect(95000, 95337, _xc))

# 1겹 — 단위가 다르면 애초에 같은 사실이 아니다 (실측: 68.0 % ↔ 757,000 개가 한 그룹)
print("\n  단위가 다르면 교차 대상이 아니다 (match_key 에 단위가 없어서 생긴 구멍)")
_slot_u = Slot(slot_id="U1", var_id="V1", formula_id="F1", claim_type="TAM",
               subject="커피전문점", subject_code="KSIC-56221", metric="사업체 수",
               period="2023", unit="개", must_contain=["커피"], value_range=[1, 10 ** 9])
_BODY_U = "2023년 커피전문점 사업체 수는 100,729개다. 침투율은 68%다. " * 20
_du = [Document(slot_id="U1", trace_id=f"U1-{i}", url=u, text=_BODY_U,
                published_at_raw="2023-01-01", content_status="usable")
       for i, u in enumerate(["https://mods.go.kr/a", "https://kostat.go.kr/b"])]
_fu = [Finding(slot_id="U1", trace_id=f"U1-{i}", status="found", findings=[
    FindingItem(quote=q, number_raw=n, unit_raw=u2)])
    for i, (q, n, u2) in enumerate([
        ("2023년 커피전문점 사업체 수는 100,729개다.", "100,729", "개"),
        ("침투율은 68%다.", "68", "%")])]
_fx = A.normalize(_fu, {d.trace_id: d for d in _du}, {"U1": _slot_u}, rules)
_lx = A.grade(_fx, {"U1": _slot_u}, {d.trace_id: d for d in _du}, rules, 2026)
check("단위가 다른 두 사실은 서로 교차확인하지 않는다",
      all(r.cross < 2 for r in _lx.rows), str([(r.cross, r.reasons) for r in _lx.rows]))
check("  그리고 '값 갈림' 으로 오탐하지도 않는다 (같은 그룹이 아니므로)",
      all(not r.conflict for r in _lx.rows), str([r.conflict for r in _lx.rows]))

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 9] off_slot 4겹 — 버리지 않고 격리")
BAD_BODY = "해외송금 앱 누적 가입자 110만 명 달성. " * 30
docd = Document(slot_id="S1", trace_id="S1-q3-u0", url="https://wirebarley.com/about",
                text=BAD_BODY, published_at_raw="2026-01-01", content_status="usable")
fd = Finding(slot_id="S1", trace_id="S1-q3-u0", status="found", findings=[
    FindingItem(quote="해외송금 앱 누적 가입자 110만 명 달성.",
                number_raw="110만", unit_raw="명")])
facts_d = A.normalize([fd], {"S1-q3-u0": docd}, {"S1": slot}, rules)
led_d = A.grade(facts_d, {"S1": slot}, {"S1-q3-u0": docd}, rules, 2026)
row = led_d.rows[0]
check("off_slot 라벨", row.label == "off_slot", row.label)
check("사유 기록", bool(row.off_slot_reason), str(row.off_slot_reason))
check("원장에 격리 보관(사라지지 않음)", row.fact_id in led_d.facts)
check("점수 0", row.score == 0, str(row.score))

print("\n  4겹 각각")
mk = lambda body, num, unit, url="https://kosis.kr/z": (
    Document(slot_id="S1", trace_id="T", url=url, text=body * 30,
             published_at_raw="2024-01-01", content_status="usable"),
    Finding(slot_id="S1", trace_id="T", status="found",
            findings=[FindingItem(quote=body.strip(), number_raw=num, unit_raw=unit)]))
for name, body, num, unit in [
        ("1겹 must_contain 부재", "우유 소비량은 500만 리터다. ", "500만", "개"),
        ("2겹 must_not_contain", "커피 송금 서비스 가입자 5,000개. ", "5,000", "개"),
        ("3겹 단위 불일치", "커피전문점 매출은 5,000억 원이다. ", "5,000억", "원"),
        ("4겹 값범위 — 자릿수 大(단위·축 오류)", "커피전문점 사업체 수는 3개다. ", "3", "개")]:
    d_, f_ = mk(body, num, unit)
    fs = A.normalize([f_], {"T": d_}, {"S1": slot}, rules)
    lg = A.grade(fs, {"S1": slot}, {"T": d_}, rules, 2026)
    if name.startswith("4겹"):
        # ⚠ **판 ⑲ 에서 의미가 바뀌었다.** `value_range` 는 **자릿수 그물**이 됐다 —
        #   차단은 자릿수 차이 > 3 만이고, 이내는 **통과시키되 `기대_밖` 플래그**를 단다.
        #   근거: **기대가 gov_stat 관측을 검열할 수 없다**(판 ⑱ 참값 2.79조 격리 사고).
        #   여기 「3개」는 하한과 자릿수 차이가 3 이내라 **통과하고 플래그가 붙는다.**
        #   ⚠ 「조용한 통과」가 아니라는 것이 이 검사의 요점이다.
        flg = fs[0].기대_밖
        check(name + " → 통과 + 기대_밖 플래그",
              lg.rows[0].label != "off_slot" and bool(flg), str(flg)[:120])
    else:
        check(name, lg.rows[0].label == "off_slot", str(lg.rows[0].off_slot_reason))

# 자릿수가 크게 벌어지면 **여전히 차단**된다 — 단위·축 오류를 놓치지 않는다.
d_, f_ = mk("커피전문점 사업체 수는 3개다. ", "3", "개")
_wide = A.normalize([f_], {"T": d_}, {"S1": slot}, rules)
_s2 = __import__("copy").deepcopy(slot)
_s2.value_range = [1e9, 1e11]          # 값 3 과 자릿수 차이 9 → 차단
lg2 = A.grade(A.normalize([f_], {"T": d_}, {"S1": _s2}, rules), {"S1": _s2},
              {"T": d_}, rules, 2026)
check("자릿수 차이 大 → 여전히 off_slot", lg2.rows[0].label == "off_slot",
      str(lg2.rows[0].off_slot_reason)[:100])

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 8] 본문 미확보(js_shell) → 점수 상한 2")
doce = Document(slot_id="S1", trace_id="S1-q4-u0", url="https://kosis.kr/y",
                text=BODY, published_at_raw="2024-01-01", content_status="js_shell")
fe = Finding(slot_id="S1", trace_id="S1-q4-u0", status="found", findings=[item])
facts_e = A.normalize([fe], {"S1-q4-u0": doce}, {"S1": slot}, rules)
led_e = A.grade(facts_e, {"S1": slot}, {"S1-q4-u0": doce}, rules, 2026)
check("gov_stat 인데도 상한 2", led_e.rows[0].score <= 2, str(led_e.rows[0].score))

print("\n[규칙 3·F7] 인용문이 본문에 없으면 점수 0")
ff = Finding(slot_id="S1", trace_id="S1-q0-u0", status="found", findings=[
    FindingItem(quote="본문에 없는 문장이다. 10만 729개.", number_raw="10만 729", unit_raw="개")])
facts_f = A.normalize([ff], {"S1-q0-u0": doc}, {"S1": slot}, rules)
led_f = A.grade(facts_f, {"S1": slot}, {"S1-q0-u0": doc}, rules, 2026)
check("quote_verified false", facts_f[0].quote_verified is False)
check("점수 0", led_f.rows[0].score == 0, str(led_f.rows[0].score))
check("격리 라벨 '미검증' (점수 매기기 전에 걸러짐)", led_f.rows[0].label == "미검증",
      led_f.rows[0].label)
cov_unver = A.check_coverage(led_f, [slot], rules)[0]
check("커버리지 total 에서 제외 → 공백", cov_unver.status == "공백",
      f"status={cov_unver.status} total={cov_unver.total}")
check("score>=5 필터에 안 딸려옴",
      all(r.score < 5 for r in led_f.rows if r.label in __import__("schema").QUARANTINE_LABELS))

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 14] min_facts 미달 → thin (라벨은 유지)")
slot_thin = Slot(slot_id="S1", var_id="V1", formula_id="F1", claim_type="TAM",
                 subject="커피전문점", subject_code="KSIC-56221", metric="사업체 수",
                 period="2023", unit="개", value_range=[1000, 500000], must_contain=["커피"],
                 accept={"min_score": 5, "min_sources": 2, "min_facts": 2, "min_confirmed": 1})
cov = A.check_coverage(led_c, [slot_thin], rules)[0]
check("확인됨 1건 이상", cov.confirmed >= 1, str(cov.confirmed))
check("status 는 충족 유지", cov.status == "충족", cov.status)
check("thin 표시", cov.thin == (cov.confirmed < 2), f"confirmed={cov.confirmed} thin={cov.thin}")
if cov.thin:
    check("retry_hint 생성", bool(cov.retry_hint), str(cov.retry_hint))
# thin=True 경로를 실제 함수로 통과시킨다 (위 케이스는 confirmed 가 기준을 넘겨 안 걸린다)
slot_strict = Slot(slot_id="S1", var_id="V1", formula_id="F1", claim_type="TAM",
                   subject="커피전문점", subject_code="KSIC-56221", metric="사업체 수",
                   period="2023", unit="개", value_range=[1000, 500000], must_contain=["커피"],
                   accept={"min_score": 5, "min_sources": 2, "min_facts": 3, "min_confirmed": 1})
cov_thin = A.check_coverage(led_c, [slot_strict], rules)[0]
check("min_facts=3 미달 → thin=True", cov_thin.thin is True,
      f"confirmed={cov_thin.confirmed}")
check("thin 이어도 status 는 충족", cov_thin.status == "충족", cov_thin.status)
check("thin retry_hint 문구", "재조사" in (cov_thin.retry_hint or ""), str(cov_thin.retry_hint))

empty_cov = A.check_coverage(A.grade([], {"S1": slot}, {}, rules, 2026), [slot], rules)[0]
check("수집 0건 → 공백 + 힌트", empty_cov.status == "공백" and bool(empty_cov.retry_hint))

# ══════════════════════════════════════════════════════════════
print("\n[수용기준 3] 같은 입력 2회 → 원장·커버리지 바이트 동일 (thin·retry_hint 포함)")
docs_all = {"S1-q0-u0": doc, "S1-q2-u0": docc, "S1-q3-u0": docd}
fnds_all = [f1, fc, fd]


def once():
    fs = A.normalize(fnds_all, docs_all, {"S1": slot_thin}, rules)
    lg = A.grade(fs, {"S1": slot_thin}, docs_all, rules, 2026)
    cv = A.check_coverage(lg, [slot_thin], rules)
    return json.dumps({"ledger": to_dict(lg.rows), "coverage": to_dict(cv)},
                      ensure_ascii=False, sort_keys=True).encode()


a, b = once(), once()
check("바이트 동일", a == b, f"{len(a)} vs {len(b)}")
check("thin 이 결과에 포함됨", b'"thin"' in a)
check("retry_hint 가 결과에 포함됨", b'"retry_hint"' in a)
check("기준연도 인자 의존 (today 미사용)", "date.today" not in
      io.open(os.path.join(ROOT, "blocks", "a_desk.py"), encoding="utf-8").read())

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
