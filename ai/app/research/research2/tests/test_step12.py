# -*- coding: utf-8 -*-
"""단계 12 검증 — 판 ⑪ 의 기반 정비.

    ① 무인 계측기          개입 카운터 · 결정 로그 (엔진 · 하네스 **양쪽**)
    ② 사전등록 검사        부록 앵커 + mtime 델타
    ③ `대조_기반`          출처 수 · 원출처 도메인 · 경계
    ④ 55                   `추출_힌트` 어절 단위 대조

**이 파일이 지키려는 것 한 줄: 「0」과 「미측정」을 구별한다.**
판 ⑧ 의 「개입 0」이 판 ⑩ 에서 「주장 — 디스크 미확인」으로 강등된 이유가 그 구별의 부재였다.
그래서 여기 테스트의 절반은 **값이 맞는가**가 아니라 **칸이 존재하는가**를 본다.
"""
from __future__ import annotations
import io, json, os, shutil, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "blocks"))
sys.path.insert(0, os.path.join(ROOT, "harness"))

import runlog
from runlog import Run

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
    else:
        fail.append(f"{name} — {detail}")
    print(f"  {'OK ' if cond else 'X  '} {name}" + (f"   {detail}" if not cond else ""))


def fresh_run(rid):
    d = os.path.join(runlog.RUNS_DIR, rid)
    shutil.rmtree(d, ignore_errors=True)
    return Run(rid)


# ══════════════════════════════════════════════════════════════
print("[① 엔진 계측기] 「0」과 「미측정」을 가른다")
# ══════════════════════════════════════════════════════════════
r = fresh_run("zz-p11-empty")
res = r.finish(concept=None, slots=[], verdict=None, report=None)

# **가장 중요한 검사.** 아무 일도 없었어도 칸은 있어야 한다.
check("아무 일 없어도 무인_기록 칸이 존재", "무인_기록" in res, list(res)[:6])
m = res.get("무인_기록") or {}
check("개입 0 이 값으로 있다", m.get("개입_횟수") == 0 and m.get("개입") == [], str(m)[:120])
check("결정 0 이 값으로 있다", m.get("결정_횟수") == 0 and m.get("결정") == [], str(m)[:120])
check("규칙 문구가 fail-open 과 개입을 가른다",
      "fail-open" in (m.get("_규칙") or "") and "개입" in (m.get("_규칙") or ""), m.get("_규칙"))

r = fresh_run("zz-p11-filled")
r.decide("어댑터 폴백", "web", rule="adapters.kosis.route_metrics.fallback_on", why="not_found")
r.decide("코드가 그냥 골랐다", "X", rule="")            # 규칙 없는 결정도 **드러나야** 한다
r.intervene("retry_hint", "S1 얇음", blocking=False)
r.intervene("멈춤", "키 없음", blocking=True)
res = r.finish(concept=None, slots=[], verdict=None, report=None)
m = res["무인_기록"]
check("결정 2건이 실린다", m["결정_횟수"] == 2, str(m["결정_횟수"]))
check("**규칙 없는 결정이 따로 세어진다**", m["결정_규칙없음"] == 1, str(m["결정_규칙없음"]))
check("근거 규칙이 값으로 남는다",
      m["결정"][0]["근거_규칙"] == "adapters.kosis.route_metrics.fallback_on")
check("규칙 없으면 「(없음」으로 드러난다", m["결정"][1]["근거_규칙"].startswith("(없음"))
check("개입 2건 · 그중 멈춤 1건", m["개입_횟수"] == 2 and m["개입_멈춤"] == 1, str(m))
check("비차단 개입은 멈춤으로 세지 않는다",
      [x for x in m["개입"] if not x["멈췄나"]][0]["종류"] == "retry_hint")
check("카운터도 같이 오른다",
      res["metrics"].get("decision.total") == 2
      and res["metrics"].get("intervention.total") == 2, str(res["metrics"])[:160])
check("run.jsonl 에 decision·intervention 줄이 남는다",
      {json.loads(l)["node"] for l in io.open(r.jsonl, encoding="utf-8") if l.strip()}
      >= {"decision", "intervention"})

# ══════════════════════════════════════════════════════════════
print("\n[① 하네스 계측기] 엔진과 **같은 어휘**여야 나란히 읽힌다")
# ══════════════════════════════════════════════════════════════
import slot_harness as H

H._기록["개입"].clear()
H._기록["결정"].clear()
hm = H._무인_기록()
check("빈 상태에서도 칸이 다 있다",
      set(hm) >= {"_규칙", "개입_횟수", "개입_멈춤", "개입", "결정_횟수", "결정_규칙없음", "결정"},
      str(sorted(hm)))
# **키가 갈리면 두 산출물을 나란히 못 읽는다.** 이 검사가 실제로 비대칭을 잡았다 —
# 엔진은 `사전등록` 을 최상위에, 하네스는 `무인_기록` 안에 두고 있었다.
check("엔진과 키가 정확히 일치한다", set(hm) == set(m),
      f"harness-engine={sorted(set(hm) - set(m))} / engine-harness={sorted(set(m) - set(hm))}")

H._decide("fail-open 진행", "사람을 부르지 않고 종료 0", rule="failopen:v1", why="미통과")
hm = H._무인_기록()
check("**fail-open 은 결정이지 개입이 아니다**",
      hm["결정_횟수"] == 1 and hm["개입_횟수"] == 0, str(hm)[:160])

H._intervene("멈춤 — 키 없음", "not_configured", blocking=True)
hm = H._무인_기록()
check("차단 개입은 개입으로 센다", hm["개입_멈춤"] == 1, str(hm["개입_멈춤"]))
H._기록["개입"].clear()
H._기록["결정"].clear()

# ══════════════════════════════════════════════════════════════
print("\n[② 사전등록 계측] 델타의 **부호**가 판정이다")
# ══════════════════════════════════════════════════════════════
import time
from runlog import prereg_stamp

tmp = os.path.join(tempfile.gettempdir(), "zz_p11_expected.md")
io.open(tmp, "w", encoding="utf-8").write("# 부록 A — 옛것\n\n## 부록 Z — 지금 판\n")
mt = os.path.getmtime(tmp)

st = prereg_stamp(mt + 60, tmp)          # 사전등록 60초 뒤에 유료 호출
check("사전등록이 먼저면 「사전등록」", st["판정"] == "사전등록", st["판정"])
check("델타가 양수로 남는다", st["델타_초"] == 60.0, str(st["델타_초"]))
check("**마지막 부록**을 현재 판으로 잡는다", st["부록"] == "부록 Z", str(st["부록"]))

st = prereg_stamp(mt - 60, tmp)          # 유료 호출이 먼저 = 이탈
check("실행이 먼저면 「이탈」", st["판정"].startswith("이탈"), st["판정"])
check("델타가 음수로 남는다", st["델타_초"] == -60.0, str(st["델타_초"]))

st = prereg_stamp(time.time(), tmp + ".없음")
check("파일이 없으면 「미측정」 — 이탈이 아니다", st["판정"] == "미측정", st["판정"])
check("한계를 값에도 적어 둔다",
      "차단이 아니라" in (prereg_stamp(mt, tmp).get("_한계") or ""))
os.remove(tmp)

r = fresh_run("zz-p11-nopaid")
res = r.finish(concept=None, slots=[], verdict=None, report=None)
# **유료 0회와 이탈은 다른 값이다.** 같게 두면 무료 재채점이 전부 이탈로 보인다.
check("유료 호출 0회면 사전등록은 None(잴 일이 없었다)",
      res["무인_기록"]["사전등록"] is None, str(res["무인_기록"].get("사전등록")))
check("하네스도 **같은 함수**를 쓴다 — 진입점만 다르다",
      "prereg_stamp" in io.open(os.path.join(ROOT, "harness", "slot_harness.py"),
                                encoding="utf-8").read())


# ══════════════════════════════════════════════════════════════
print("\n[③ 대조_기반] 건수와 화자 수는 **다른 수다**")
# ══════════════════════════════════════════════════════════════
sys.path.insert(0, os.path.join(ROOT, "service"))
import verdict as V

b = V._대조_기반([{"url": "https://gongbiz.kr/a"}, {"url": "https://gongbiz.kr/b"},
                {"url": "https://gongbiz.kr/c"}])
check("3건 1도메인 → 출처 3 · 도메인 1",
      b["출처_수"] == 3 and b["원출처_도메인"] == ["gongbiz.kr"], str(b))
check("**단일 화자 경계가 자동으로 붙는다**",
      bool(b["경계"]) and "같은 화자의 반복" in b["경계"][0], str(b["경계"]))

b = V._대조_기반([{"url": "https://kosis.kr/a"}, {"url": "https://dart.fss.or.kr/b"}])
check("2도메인이면 경계가 안 붙는다", b["경계"] == [] and len(b["원출처_도메인"]) == 2, str(b))

b = V._대조_기반([], 가정=True)
check("가정은 출처 0 + 「관측이 아니다」", b["출처_수"] == 0 and "가정" in b["경계"][0], str(b))

b = V._대조_기반([])
check("빈 목록도 칸은 남는다(미측정과 구별)",
      set(b) == {"출처_수", "원출처_도메인", "경계"}, str(b))


# ══════════════════════════════════════════════════════════════
print("\n[④ 55 어절 대조] **열되, 목적을 잃지 않는가**")
# ══════════════════════════════════════════════════════════════
import gate as G

VOCAB = json.load(io.open(os.path.join(ROOT, "harness", "vocab.json"), encoding="utf-8"))
PET = json.load(io.open(os.path.join(ROOT, "data", "concept_pet-treat.json"), encoding="utf-8"))


def hint_check(hints, vocab=VOCAB, concept=PET):
    slots = [{"slot_id": "S1", "claim_type": "PAIN", "_추출_힌트": hints}]
    return G.check_extract_hints(slots, vocab, concept)


# 판 ⑧ 이 죽은 그 자리. 컨셉 원문은 「성분을 일일이 확인하지만」이다.
r = hint_check(["첨가물 신뢰", "성분 확인"])
check("**판 ⑧ 의 `성분 확인` 이 통과한다**", r["passed"], str(r["violations"])[:200])

# ⚠ 여기가 이 개정의 시험대다 — 목적을 잃으면 안 된다.
r = hint_check(["노쇼 피해 경험률", "예약 부도"])
check("**다른 업종 지식은 여전히 탈락한다**", not r["passed"], str(r["rows"])[:200])

r = hint_check(["성분 노쇼", "원료 확인"])
check("어절 하나라도 컨셉에 없으면 유래가 아니다",
      "성분 노쇼" not in (r["rows"][0]["컨셉_유래"]), str(r["rows"][0]))

r = hint_check(["원료 확인", "성분 신뢰"])
check("둘 다 컨셉 어절이면 둘 다 유래", len(r["rows"][0]["컨셉_유래"]) == 2, str(r["rows"][0]))

# 1글자 어절은 세지 않는다 — 아무 데나 맞아 검사를 무력화한다.
r = hint_check(["가 나", "성분 확인"])
check("1글자뿐인 힌트는 유래로 안 쳐 준다",
      "가 나" not in r["rows"][0]["컨셉_유래"], str(r["rows"][0]))

# 규칙을 끄면 옛 동작으로 정확히 되돌아간다(계측된 개정 규약)
v_old = json.loads(json.dumps(VOCAB))
v_old["요구"]["추출_힌트"]["컨셉_유래_대조"]["방식"] = "정확"
r = hint_check(["첨가물 신뢰", "성분 확인"], vocab=v_old)
check("`방식=정확` 이면 옛 동작(판 ⑧ 그대로 탈락)", not r["passed"], str(r["rows"])[:160])


# ══════════════════════════════════════════════════════════════
print("\n[판 ⑬ ①→㊳ 폐지] 시장 크기 — **계산하지 않는다. 관측 층위를 낸다**")
# ⚠ **이 절은 통째로 뒤집혔다.**
#   판 ⑬ 은 「거래액 관측이 있으면 TAM 값이 선다」를 합격으로 봤다. 그 「선다」가 곧
#   `거래액 × 추정점유율 0.3(출처 0건)` 이었고 화면에 **11.41조원**이 값처럼 떴다 —
#   같은 원장의 엔진은 그 칸을 「추정 불가」로 적어 두었는데도. 옛 기대값 자체가 결함이었다.
#
#   판 ㊳ 에서 **계열별 계산식(T2·T7)을 통째로 들어냈다.** 두 식 다 관측 하나에 가정
#   여럿을 곱했고, 가정 곱셈을 막자 어느 식을 골라도 값이 안 나왔다 — 갈림길이 아무것도
#   안 가르면서 계열이 틀리면 **틀린 사유**만 냈다.
#   새 계약: 시장 크기는 **관측 층위 목록**이다. 식도 없고 값도 없다.
# ══════════════════════════════════════════════════════════════
import verdict as V2


def led_c(value=None, metric="거래액", unit="원", claim="TAM", year="2025"):
    """시장 크기 관측 1건(또는 0건)짜리 원장 흉내."""
    rows, facts = [], {}
    slots = [{"slot_id": "S1", "claim_type": claim, "metric": metric,
              "unit": unit, "subject": "냉동 간편식", "period": year}]
    if value is not None:
        rows.append({"fact_id": "F001", "slot_id": "S1", "label": "확인됨",
                     "kind": "gov_stat", "score": 5, "url": "https://kosis.kr/x",
                     "채택": True, "등급": "확정",
                     "retrieved_at": "2026-08-09T00:00:00"})
        facts["F001"] = {"value_num": value, "unit_norm": unit, "trace_id": "t",
                         "quote_verified": True}
    return {"slots": slots, "ledger_rows": rows, "facts": facts,
            "reference_date": "2026-08-14",
            "report": {"headline_numbers": []}, "violations": {}}


r = V2.judge_market(led_c(2_792_575.0), {})
t = r["TAM_추정"] or {}
check("관측이 있으면 **층위가 선다**", len(r.get("시장_관측") or []) == 1, str(r.get("시장_관측")))
check("**값은 내지 않는다**", t.get("값") is None, str(t.get("값")))
check("**식도 없다** — 계산하지 않으므로", t.get("식") is None, str(t.get("식")))
check("**가정을 하나도 안 쓴다**", t.get("assumption_count") == 0, str(t.get("assumption_count")))
check("요인은 전부 **관측**이다",
      [f["판정"] for f in (t.get("요인") or [])] == ["관측"], str(t.get("요인"))[:140])
check("**계산식의 항이 아니라 층위 목록임을 말한다**",
      any("층위" in x for x in (t.get("해석_경계") or [])), str(t.get("해석_경계"))[:140])
check("관측한 값이 그대로 실린다",
      (t.get("관측된_밑동") or {}).get("값") == 2_792_575.0, str(t.get("관측된_밑동")))

# ── **같은 관측이 두 슬롯에 앉아도 층은 하나다** ──────────────────────────
#   실측: KOSIS 거래액 한 건이 TAM 슬롯과 SAM 슬롯에 함께 채택돼 TAM=SAM 이 됐다.
#   층위로 두 번 세면 **없는 층이 생긴다.** fact_id 로는 안 접힌다(슬롯마다 새 id).
led2 = led_c(2_792_575.0)
led2["slots"].append({"slot_id": "S3", "claim_type": "SAM", "metric": "거래액",
                      "unit": "원", "subject": "냉동 간편식", "period": "2025"})
led2["ledger_rows"].append({**led2["ledger_rows"][0], "fact_id": "F002", "slot_id": "S3"})
led2["facts"]["F002"] = dict(led2["facts"]["F001"])
r2 = V2.judge_market(led2, {})
check("같은 값·단위·연도는 **한 층으로 접힌다**",
      len(r2.get("시장_관측") or []) == 1, str(r2.get("시장_관측")))
check("**겹쳐 앉은 사실을 드러낸다**",
      any("함께 앉아" in (f.get("설명") or "") for f in (r2["TAM_추정"] or {}).get("요인", [])),
      str((r2["TAM_추정"] or {}).get("요인"))[:200])

# ── 관측 0건 ────────────────────────────────────────────────────────
r3 = V2.judge_market(led_c(None), {})
check("관측 0이면 층도 값도 없다", r3["TAM_추정"] is None, str(r3["TAM_추정"]))
check("**사유가 층위 말로 적힌다** — 「사업체 수」가 아니다",
      "층위" in r3["사유"] and "사업체" not in r3["사유"], r3["사유"])

# ── SAM 은 축이 없으면 안 낸다 ──────────────────────────────────────
check("**SAM 을 TAM 층위로 대신하지 않는다**", r["SAM_추정"] is None, str(r["SAM_추정"]))
check("**왜 안 냈는지 말한다**",
      "조사 범위를 좁히는 축" in (r.get("SAM_사유") or ""), str(r.get("SAM_사유"))[:140])

# ── 낡은 관측은 낡았다고 말한다 ────────────────────────────────────
r4 = V2.judge_market(led_c(1_166_600_000_000.0, year="2018"), {})
check("3년 넘은 관측에 **낡음이 붙는다**",
      (r4.get("시장_관측") or [{}])[0].get("낡음") is True, str(r4.get("시장_관측")))
check("낡음이 사람 문장으로도 나간다",
      any("낡음" in (f.get("설명") or "") for f in (r4["TAM_추정"] or {}).get("요인", [])),
      str((r4["TAM_추정"] or {}).get("요인"))[:160])

# ── **계열 계산 경로가 정말 없어졌는가** ─────────────────────────────
#   판 ⑬ 은 「배선이 켜져 있는가」를 물었다. 판 ㊳ 은 반대를 묻는다 — **꺼져 있는가.**
check("`_judge_market_t7` 이 사라졌다", not hasattr(V2, "_judge_market_t7"), "")
check("`_pick_money` 가 사라졌다", not hasattr(V2, "_pick_money"), "")
_su = json.load(io.open(os.path.join(ROOT, "rules", "series_unit.v1.json"), encoding="utf-8"))
_tam = _su.get("계열_TAM_구조") or {}
check("**계열_TAM_구조가 은퇴했다**", _tam.get("enabled") is False,
      f"enabled={_tam.get('enabled')}")
check("은퇴 사유가 파일에 적혀 있다", bool(_tam.get("_은퇴")), str(_tam)[:120])
# ★ **살아 있어야 하는 것** — 고객 단위 정합은 계산 경로가 아니다. 같이 지우면 안 된다.
check("**고객 단위 정합은 그대로 살아 있다**",
      isinstance(_su.get("계열_고객_단위"), dict) and len(_su["계열_고객_단위"]) >= 5,
      str(list((_su.get("계열_고객_단위") or {}).keys())))


print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
