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
print("\n[판 ⑬ ①] 판정층 T7 — **관측만 있으면 TAM 이 선다**")
# ══════════════════════════════════════════════════════════════
import verdict as V2

SPEC = {"template": "T7", "식": "TAM(연) = 시장 거래액 × 추정점유율",
        "관측_metric": "거래액", "점유율_role": "추정점유율",
        "경계": ["⚠ 거래액(GMV) ≠ 매출"]}


def led_c(value=None, metric="거래액", unit="원", claim="TAM"):
    """계열 C 원장 흉내 — 확인됨 1건(또는 0건)."""
    rows, facts = [], {}
    slots = [{"slot_id": "S1", "claim_type": claim, "metric": metric, "unit": unit}]
    if value is not None:
        # 판 ㉙ — 확인됨 행은 새 축에서도 채택이다(픽스처 현실화, 기대값 무변경)
        rows.append({"fact_id": "F001", "slot_id": "S1", "label": "확인됨",
                     "kind": "gov_stat", "score": 5, "url": "https://kosis.kr/x",
                     "채택": True, "등급": "확정",
                     "retrieved_at": "2026-08-09T00:00:00"})
        facts["F001"] = {"value_num": value, "unit_norm": unit, "trace_id": "t",
                         "quote_verified": True}
    return {"slots": slots, "ledger_rows": rows, "facts": facts,
            "report": {"headline_numbers": []}, "violations": {}}


r = V2._judge_market_t7(led_c(2_792_575.0), {}, SPEC)
check("거래액 관측이 있으면 **TAM 값이 선다**",
      (r["TAM_추정"] or {}).get("값") is not None, str(r.get("TAM_추정"))[:160])
check("식이 T7 구조로 적힌다", r["_구조"] == SPEC["식"], str(r["_구조"]))
check("GMV≠매출 경계가 가정에 실린다",
      any("GMV" in x for x in (r["TAM_추정"] or {}).get("가정", [])), "")
check("대조_기반이 붙는다", "대조_기반" in (r["TAM_추정"] or {}), "")

r = V2._judge_market_t7(led_c(None), {}, SPEC)
check("관측 0이면 값은 없다", r["TAM_추정"] is None, str(r["TAM_추정"]))
# ⚠ **여기가 이 배선의 핵심이다.** 옛 코드는 계열 C 에서도 「전국 사업체 수 확인됨 0건」
#   이라 말했다 — 틀린 사유는 다음 판이 엉뚱한 데를 파게 만든다.
check("**사유가 계열 C 구조로 적힌다**",
      "거래액" in r["사유"] and "사업체" not in r["사유"], r["사유"])

# metric 으로 좁히지 않으면 단가·CAC 가 시장 크기 밑동이 된다(종류 오류)
r = V2._judge_market_t7(led_c(39000.0, metric="이용 요금"), {}, SPEC)
check("**다른 금액 계량은 밑동이 되지 않는다**", r["TAM_추정"] is None, str(r["TAM_추정"]))


# ⚠ **배선이 켜져 있는가.** 위 검사들은 `_judge_market_t7` 를 **직접 호출**하므로
#   규칙 플래그가 꺼져 있어도 전부 green 이다 — 판 ⑬ 이 정확히 그렇게 마감됐고
#   (측정 스크립트가 복원에 실패해 `enabled=false` 로 남았다) 판 ⑮ 착수 때 발견됐다.
#   **켜짐 여부는 따로 봐야 한다.**
_su = json.load(io.open(os.path.join(ROOT, "rules", "series_unit.v1.json"), encoding="utf-8"))
_tam = _su.get("계열_TAM_구조") or {}
check("**계열_TAM_구조 배선이 켜져 있다**", _tam.get("enabled") is True,
      f"enabled={_tam.get('enabled')} — 꺼져 있으면 계열 C 가 T2 로 판정된다")
check("계열 C 가 T7 로 등재돼 있다",
      ((_tam.get("map") or {}).get("C") or {}).get("template") == "T7", str(_tam.get("map")))


print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
