# -*- coding: utf-8 -*-
"""단계 5 검증 — A2 라우팅 + kosis·dart 어댑터.

확인할 것 넷 (외부가 들어오는 첫 단계):
  1. 어댑터 출력이 진짜 Fact 인가 — dedup_key·match_key·unit_norm·year 가 웹과 같은 규칙으로
  2. kind·score 는 여전히 도메인 규칙으로 (어댑터가 점수를 박지 않는다)
  3. stat_code 는 **손으로** 넣는다 (A1 검증은 단계 7 에서 별도)
  4. 실패 처리 — 키만료 / 잘못된 통계표 ID / 빈 결과 / 타임아웃이 각각 다른 값

    python tests/test_step5.py          # 오프라인 검사만
    python tests/test_step5.py --live   # 실제 API 호출 포함
"""
from __future__ import annotations
import io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
for p in (ROOT, os.path.join(ROOT, "blocks"), os.path.join(ROOT, "adapters")):
    sys.path.insert(0, p)

import a_desk as A
import base as ADP
import dart, kosis
from runlog import load_rules
from schema import Slot

rules = load_rules()
LIVE = "--live" in sys.argv
ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


def S(slot_id="S1", **kw):
    base = dict(var_id="V1", formula_id="F1", claim_type="TAM", subject="주민등록세대수",
                metric="세대수", period="2023", unit="세대")
    base.update(kw)
    return Slot(slot_id=slot_id, **base)


# ══════════════════════════════════════════════════════════════
print("[A2 라우팅] 전부 웹으로 보내지 않는다")
routes = {r.slot_id: r for r in A.route_sources([
    S("S1", stat_code="101/DT_1B040B3"), S("S2", corp_name="카카오"),
    S("S3", claim_type="PRICE", metric="월 구독료"),
    S("S4", claim_type="LEGAL", metric="영업 신고 요건"),
    S("S5", claim_type="COMPARABLE", metric="누적 가입자 수"),
    S("S6", claim_type="TAM", stat_code=None, metric="사업체 수"),
    S("S7", claim_type="SAM", stat_code=None, metric="서비스 침투율")],
    rules)}
check("stat_code → kosis", routes["S1"].adapter == "kosis")
check("corp_name → dart", routes["S2"].adapter == "dart")
check("PRICE → web(공식 우선)", routes["S3"].adapter == "web" and "공식" in routes["S3"].why)
check("LEGAL → web(법령정보센터)", routes["S4"].adapter == "web" and "법령" in routes["S4"].why)
check("그 외 → web", routes["S5"].adapter == "web")
check("왜 그 경로인지 기록", all(r.why for r in routes.values()))

# ── 작업 12-1 — kosis 라우팅 재도입 ──────────────────────────────
# 한때 TAM/SAM 이면 코드 없이도 kosis 로 보냈다가 되돌렸다 (full-02: 10슬롯 전부
# not_found, 폴백이 없어 그대로 죽음). 통계표에 없는 값('카페 침투율')까지 보낸 게 문제다.
# 재도입 조건 둘: **metric 기준**으로 좁히고 **폴백과 함께** 넣는다.
check("통계 계량(사업체 수) → 코드 없어도 kosis",
      routes["S6"].adapter == "kosis" and "route_metric" in routes["S6"].why,
      routes["S6"].why)
check("kosis 로 보낸 슬롯에는 폴백이 달려 있다 (full-02 가 죽은 자리)",
      routes["S6"].fallback_to == "web")
check("침투율은 통계 계량이 아니다 → web (full-02 가 삼킨 그 슬롯)",
      routes["S7"].adapter == "web")
check("claim_type 이 기준이 아니다 — 같은 TAM 이라도 metric 으로 갈린다",
      routes["S6"].adapter != routes["S7"].adapter)
check("PRICE·LEGAL 은 통계 계량이어도 가로채지 않는다",
      all(r.adapter == "web" for r in A.route_sources(
          [S("P1", claim_type="PRICE", metric="사업체 수"),
           S("L1", claim_type="LEGAL", metric="인구")], rules)))
check("web 기본 경로에는 폴백이 없다", routes["S7"].fallback_to == "")
# 부분문자열 매칭의 구멍 — 「**가입** 매장 수」는 국가통계가 아니라 그 회사의 고객 수다.
# 실측: 이 예외가 없으면 data/slots.json 의 S3 이 kosis 로 새서 kosis 3 · web 3 이 된다.
check("「가입 매장 수」는 '매장 수' 에 걸려도 kosis 로 가지 않는다",
      A.route_sources([S("C1", claim_type="COMP", metric="가입 매장 수")],
                      rules)[0].adapter == "web")
check("  다만 '매장 수' 자체는 살아 있다",
      A.route_sources([S("C2", metric="매장 수")], rules)[0].adapter == "kosis")

_fb_slot = S("S6", claim_type="TAM", stat_code=None, metric="사업체 수")
_r = A.route_sources([_fb_slot], rules)[0]


def _F(status):
    from schema import Finding
    return Finding(slot_id="S6", trace_id="t", status=status, findings=[])


check("폴백은 not_found 에만 — 못 찾은 것은 조사 결과다",
      A.should_fallback(_r, _F("not_found"), rules))
check("not_configured 는 폴백 금지 — 키 없음을 web 성공으로 덮으면 §7 이 장님이 된다",
      not A.should_fallback(_r, _F("not_configured"), rules))
check("fetch_failed(인증·네트워크)도 폴백 금지",
      not A.should_fallback(_r, _F("fetch_failed"), rules))
check("찾았으면 폴백하지 않는다", not A.should_fallback(_r, _F("found"), rules))
check("폴백 없는 경로는 무슨 status 여도 폴백하지 않는다",
      not A.should_fallback(A.route_sources([S("S7", metric="침투율")], rules)[0],
                            _F("not_found"), rules))

# ── 폴백 배선 — 판정이 아니라 **실제로 web 이 받는지**. full-02 가 죽은 자리다 ──
print("\n[12-1] 폴백 배선 (가짜 어댑터, 네트워크·LLM 0회)")
import types

import run as RUN
from schema import Document, Finding


def _fake(status, note="", state="ok"):
    fin = Finding(slot_id="S6", trace_id="k", status=status, findings=[], note=note)
    doc = Document(slot_id="S6", trace_id="k", url="", text="", http_status="error",
                   content_status="empty", channel="kosis_api")
    return ADP.AdapterResult(fin, doc, adapter_state=state)


def _run_collect(kosis_status, kosis_state="ok"):
    """run.collect_slot 을 가짜 kosis·web 으로 돌린다. web 이 몇 번 불렸는지도 센다."""
    called = []
    web_doc = Document(slot_id="S6", trace_id="w", url="https://example.com", text="본문",
                       http_status="200", content_status="usable", channel="web")

    def _web_collect(slot, rules_, meter_, trace):
        called.append(trace)
        return (Finding(slot_id="S6", trace_id="w", status="found", findings=[]),
                {"w": web_doc}, [], "ok")

    old_k, old_w = RUN.kosis, RUN.web
    RUN.kosis = types.SimpleNamespace(
        collect=lambda slot, rules_: _fake(kosis_status, "why", kosis_state))
    RUN.web = types.SimpleNamespace(collect=_web_collect)
    try:
        route = A.route_sources([S("S6", metric="사업체 수")], rules)[0]
        return RUN.collect_slot(S("S6", metric="사업체 수"), route, rules, None), called
    finally:
        RUN.kosis, RUN.web = old_k, old_w


(ad, f, dmap, cands, res, extra, ev), called = _run_collect("not_found")
check("kosis not_found → web 이 실제로 불린다", len(called) == 1)
check("  결과 어댑터가 web 으로 바뀐다", ad == "web" and f.status == "found")
check("  kosis 실패 문서를 버리지 않는다 (실패는 값이다)", set(dmap) == {"k", "w"}, str(set(dmap)))
check("  kosis 상태를 따로 남긴다 — web 성공이 kosis 를 덮지 않는다", extra == [("kosis", "ok")])
check("  a3_fallback 이벤트를 남긴다 (몇 번 돌았나를 세려면)",
      ev and ev["from"] == "kosis" and ev["to"] == "web" and ev["slot_id"] == "S6")

(ad, f, dmap, _, _, extra, ev), called = _run_collect("not_configured", "not_configured")
check("kosis not_configured → 폴백 안 함 (web 호출 0회)", not called)
check("  §7 이 볼 수 있게 not_configured 가 그대로 남는다",
      ad == "kosis" and f.status == "not_configured" and ev is None)

(ad, f, _, _, _, _, ev), called = _run_collect("found")
check("kosis 가 찾았으면 web 을 부르지 않는다", not called and ad == "kosis" and ev is None)

# ══════════════════════════════════════════════════════════════
print("\n[2] 어댑터가 점수를 박지 않는다 (등급은 도메인 규칙으로)")
import re as _re


def code_only(path):
    """주석과 독스트링을 뺀 실행 코드만. (문서에 적힌 'score=6' 을 위반으로 잡지 않게)"""
    src = io.open(os.path.join(ROOT, path), encoding="utf-8").read()
    src = _re.sub(r'"""[\s\S]*?"""', "", src)
    return "\n".join(l.split("#")[0] for l in src.split("\n"))


for mod, path in (("kosis", "adapters/kosis.py"), ("dart", "adapters/dart.py"),
                  ("base", "adapters/base.py")):
    code = code_only(path)
    check(f"{mod}: score 대입 없음", not _re.search(r"score\s*=", code))
    check(f"{mod}: label/kind 대입 없음",
          not _re.search(r"label\s*=", code) and not _re.search(r'kind\s*=\s*"', code))
    check(f"{mod}: LLM 없음", "openai" not in code.lower() and "prompts" not in code)

# ══════════════════════════════════════════════════════════════
print("\n[4] 실패가 전부 다른 값이 된다")
fm = rules["adapters"]["failure_map"]
check("no_key ≠ auth_failed",
      fm["no_key"]["adapter_state"] != fm["auth_failed"]["adapter_state"],
      f"{fm['no_key']} vs {fm['auth_failed']}")
check("bad_stat_code → not_found (에러 아님)", fm["bad_stat_code"]["finding_status"] == "not_found")
check("bad_stat_code 는 어댑터 정상", fm["bad_stat_code"]["adapter_state"] == "ok")
check("empty_result → not_found", fm["empty_result"]["finding_status"] == "not_found")
check("timeout → fetch_failed", fm["timeout"]["finding_status"] == "fetch_failed")

print("\n  실제 호출로 확인")
r_nokey = kosis.collect(S("S1", stat_code="101/DT_1B040B3"), rules, key="")
check("키 없음 → not_configured", r_nokey.adapter_state == "not_configured",
      r_nokey.adapter_state)
check("  Finding 도 not_configured", r_nokey.finding.status == "not_configured")

r_badfmt = kosis.collect(S("S1", stat_code="이상한값"), rules, key="dummy")
check("stat_code 형식 오류 → not_found", r_badfmt.finding.status == "not_found",
      r_badfmt.finding.note[:60])
check("  예외로 죽지 않는다", r_badfmt.document.slot_id == "S1")

r_nocorp = dart.collect(S("S2", corp_name=""), rules, key="dummy")
check("corp_name 없음 → not_found", r_nocorp.finding.status == "not_found")

# ── 버그 H (2026-08-07): `_year_of` 미정의로 **조회 성공 경로가 NameError 로 즉사**했다.
#    전 코퍼스 DART found 가 1건뿐이라 그 줄 앞에서 멈춰 노출되지 않았다.
#    네트워크 0회로 성공 경로를 통과시켜 고정한다.
print("\n  [버그 H] 조회 성공 경로가 죽지 않는다 (모의 응답 · 네트워크 0회)")
check("_year_of 가 정의돼 있다", callable(getattr(dart, "_year_of", None)))
check("  '2023' → 2023", dart._year_of("2023") == 2023)
check("  값 안의 숫자를 연도로 집지 않는다", dart._year_of("20264") is None)
check("  연도 없으면 None (추측 금지)", dart._year_of("최근") is None)

_orig_idx, _orig_get = dart._corp_index, dart.get_json
dart._corp_index = lambda key, rules: ({"카페24": "00000000"}, None, "")
dart.get_json = lambda url, params, rules: (
    {"status": "000", "list": [
        {"account_id": "ifrs-full_Revenue", "sj_div": "CIS", "account_nm": "영업수익",
         "thstrm_amount": "314,764,225,560"}]}, None, "")
try:
    r_found = dart.collect(S("S2", corp_name="카페24", metric="경쟁사의 매출",
                             claim_type="COMP", unit="원"), rules, key="dummy")
finally:
    dart._corp_index, dart.get_json = _orig_idx, _orig_get
check("성공 경로가 NameError 로 죽지 않는다", r_found.finding.status == "found",
      f"{r_found.finding.status} · {r_found.finding.note[:70]}")
check("  bsns_year 가 slot.period 에서 나온다 (사실 1건)",
      len(r_found.finding.findings) == 1, str(len(r_found.finding.findings)))
check("  계정 필터는 그대로 산다 (account_id 운반)",
      r_found.finding.findings[0].account_id == "ifrs-full_Revenue")

if LIVE:
    print("\n  잘못된 인증키 (실호출)")
    r_auth = kosis.collect(S("S1", stat_code="101/DT_1B040B3"), rules, key="INVALID_KEY_TEST")
    check("가짜 키 → auth_failed (not_configured 와 구분)",
          r_auth.adapter_state == "auth_failed", r_auth.finding.note[:80])
    check("  키 없음(not_configured) 과 다른 상태",
          r_auth.adapter_state != r_nokey.adapter_state,
          f"{r_auth.adapter_state} vs {r_nokey.adapter_state}")
    print(f"     (실제: state={r_auth.adapter_state} status={r_auth.finding.status})")

    print("\n  존재하지 않는 통계표 ID (실호출)")
    r_badtbl = kosis.collect(S("S1", stat_code="101/DT_NO_SUCH_TABLE"), rules)
    check("→ not_found (예외 아님)", r_badtbl.finding.status == "not_found",
          r_badtbl.finding.note[:80])
    check("  어댑터 자체는 ok", r_badtbl.adapter_state == "ok", r_badtbl.adapter_state)

# ══════════════════════════════════════════════════════════════
if not LIVE:
    print("\n[1·3] 실호출 검사는 --live 에서만 (지금은 건너뜀)")
else:
    print("\n[1] KOSIS 출력이 A4 를 그대로 통과해 Fact 가 된다")
    slot_k = S("S1", stat_code="101/DT_1B040B3", subject="주민등록세대수",
               subject_code="KOSIS-DT_1B040B3", metric="세대수", unit="세대",
               value_range=[1000, 100_000_000], must_contain=["세대"])
    res_k = kosis.collect(slot_k, rules)
    check("수집 성공", res_k.finding.status == "found", res_k.finding.note[:80])

    facts = A.normalize([res_k.finding], {res_k.document.trace_id: res_k.document},
                        {"S1": slot_k}, rules)
    check("Fact 생성", len(facts) > 0, f"{len(facts)}건")
    f0 = facts[0]
    check("dedup_key 가 웹과 같은 규칙", f0.dedup_key.startswith("kosis.kr/statHtml"), f0.dedup_key)
    check("match_key 가 slot 코드로", f0.match_key.startswith("KOSIS-DT_1B040B3|"), f0.match_key)
    check("unit_norm 정규화됨", f0.unit_norm is not None, str(f0.unit_norm))
    check("year 를 API 필드가 아니라 파서가 뽑았다", isinstance(f0.year, int), str(f0.year))
    check("quote_verified (응답 안에 실재)", f0.quote_verified is True)
    check("channel=kosis_api", f0.channel == "kosis_api")

    led = A.grade(facts, {"S1": slot_k}, {res_k.document.trace_id: res_k.document}, rules, 2026)
    row = led.rows[0]
    check("kind 를 화이트리스트가 정함", row.kind == "gov_stat" and "whitelist" in row.kind_by,
          f"{row.kind} / {row.kind_by}")
    check("score 는 규칙 파일 base_score 기반",
          row.score >= rules["scoring"]["base_score"]["gov_stat"] - 1, str(row.score))
    print(f"     예시: {f0.value_num:,.0f} {f0.unit_norm} ({f0.year}) → {row.kind} {row.score}점 {row.label}")

    print("\n[1] DART 출력도 같은 경로를 탄다")
    slot_d = S("S2", claim_type="COMP", subject="카카오", subject_code="DART-00258801",
               metric="매출액", unit="원", corp_name="카카오", period="2023",
               value_range=[1_000_000, 1e15], must_contain=["수익"])
    res_d = dart.collect(slot_d, rules)
    check("수집 성공", res_d.finding.status == "found", res_d.finding.note[:80])
    facts_d = A.normalize([res_d.finding], {res_d.document.trace_id: res_d.document},
                          {"S2": slot_d}, rules)
    check("Fact 생성", len(facts_d) > 0)
    if facts_d:
        fd = facts_d[0]
        led_d = A.grade(facts_d, {"S2": slot_d}, {res_d.document.trace_id: res_d.document},
                        rules, 2026)
        rd = led_d.rows[0]
        check("dedup_key", rd.url and fd.dedup_key.startswith("dart.fss.or.kr"), fd.dedup_key)
        check("kind=public_filing", rd.kind == "public_filing", rd.kind)
        check("year 파서 결과", fd.year == 2023, str(fd.year))
        print(f"     예시: {fd.value_num:,.0f} {fd.unit_norm} ({fd.year}) → {rd.kind} {rd.score}점 {rd.label}")

    print("\n[교차확인] 두 경로가 같은 year 를 만들면 match_key 가 일치한다")
    check("KOSIS year 는 문맥에서 나온다", isinstance(facts[0].year, int))
    check("DART year 는 문맥에서 나온다", facts_d and facts_d[0].year == 2023)

print(f"\n===== {ok} 통과 / {len(fail)} 실패" + ("" if LIVE else "  (오프라인 모드)"))
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
