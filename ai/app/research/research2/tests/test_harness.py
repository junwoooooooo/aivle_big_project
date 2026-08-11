# -*- coding: utf-8 -*-
"""슬롯 하네스 게이트 검증 — **LLM 0회.** 가짜 응답으로 게이트가 실제로 막는지 본다.

게이트가 무엇을 통과시키는지가 아니라 **무엇을 막는지**가 이 테스트의 내용이다.
막지 못하면 하네스는 자유 자동 생성과 같아지고, 측정이 붕괴한다(백로그 12의 이유).

    python tests/test_harness.py
"""
from __future__ import annotations
import copy, io, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "harness"))

import gate as G                                                    # noqa: E402
from slot_harness import wire, build_prompt                         # noqa: E402

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
        print(f"  OK  {name}")
    else:
        fail.append(f"{name} {detail}")
        print(f"  X   {name} {detail}")


VOCAB = json.load(io.open(os.path.join(ROOT, "harness", "vocab.json"), encoding="utf-8"))
ADAPTERS = json.load(io.open(os.path.join(ROOT, "rules", "adapters.v1.json"), encoding="utf-8"))
CONCEPT = json.load(io.open(os.path.join(ROOT, "data", "concept_beauty-noshow.json"),
                            encoding="utf-8"))
HYP = CONCEPT["_hypotheses_v2"]


SLOTCHECK = json.load(io.open(os.path.join(ROOT, "rules", "slotcheck.v1.json"), encoding="utf-8"))
CORPCODE = json.load(io.open(os.path.join(ROOT, "adapters", "_cache_corpcode.json"),
                             encoding="utf-8"))
AS_OF = 2026
CAT = VOCAB["metric"]["catalog"]


def _var(role, subject, metric, ct, cell, **kw):
    """단위·기간은 규칙이 정한다 — 테스트에서도 손으로 적지 않는다."""
    lagged = SLOTCHECK["period"]["lagged_metrics"]
    off = 2 if any(m in metric for m in lagged) else 1
    v = {"var_role": role, "subject": subject, "metric": metric, "period": str(AS_OF - off),
         "unit": CAT[metric]["unit"], "region": "대한민국", "subject_code": None,
         "stat_code": None, "corp_name": None, "claim_type": ct, "canvas_cell": cell,
         "observable": True, "must_contain": ["미용"], "must_not_contain": ["해외"],
         "value_range": [1, 10 ** 9]}
    v.update(kw)
    return v


def _assume(role, metric):
    """관측하지 않는 변수. metric 은 통제 어휘 밖이어도 된다 — 슬롯이 되지 않으므로
    수집으로 나가지 않는다(연환산의 '연 결제 개월' 처럼)."""
    v = _var(role, "두발 미용업", "도입률", "TAM", "고객 세그먼트", observable=False)
    v["metric"], v["unit"] = metric, ""
    return v


GOOD = {"formulas": [
    {"formula_id": "F_TAM", "vars": [
        _var("사업체수", "두발 미용업", "사업체 수", "TAM", "고객 세그먼트"),
        _var("세그먼트비중", "두발 미용업", "종사자 1인 사업체 비율", "TAM", "고객 세그먼트"),
        _assume("침투율", "도입률"),
        _var("단가", "미용실 예약 서비스", "월 구독료", "PRICE", "수익원"),
        _assume("연환산", "연 결제 개월")]},
    # ⚠ **판 ㉖ claim_type 정합으로 의도 갱신** (도장 조건 ①, 조용한 갱신 금지).
    #   옛 형태는 `("성장률", …, "연평균 성장률", "TAM", …)` 한 줄이었다. 둘 다 틀렸다:
    #   ① 「연평균 성장률」은 **계산값**이고 우리가 관측할 것은 **두 해의 LEVEL 값**이다
    #      (판 ㉓ 정본 — 계산은 판정 층이 한다)
    #   ② `claim_type` 이 `TAM` 이면 `judge_growth` 가 **성장률 축에서 세지 못한다** —
    #      확인됨이 원장에 있어도 성장률이 0건이 된다(`ledger-02` 실측).
    {"formula_id": "F_GROWTH", "vars": [
        _var("성장률", "두발 미용업", "사업체 수", "GROWTH", "고객 세그먼트"),
        _var("성장률", "두발 미용업", "사업체 수", "GROWTH", "고객 세그먼트")]},
    {"formula_id": "F_COMP", "vars": [
        _var("경쟁사규모", "미용실 예약 서비스", "가입 매장 수", "COMP", "가치 제안"),
        _var("경쟁사규모", "네이버 예약", "매출액", "COMP", "가치 제안", corp_name="NAVER")]},
    {"formula_id": "F_DIFF", "vars": [
        _var("비교축", "예약 노쇼 방지 서비스", "누적 가입자 수", "COMPARABLE", "가치 제안")]},
    {"formula_id": "F_CHANNEL", "vars": [
        _var("채널벤치마크", "지역 소상공인 SaaS", "고객 획득 비용", "CHANNEL", "채널")]},
    {"formula_id": "F_PAIN", "vars": [
        _var("문제근거", "미용실 예약 부도", "문제 경험률", "PAIN", "가치 제안",
             **{"추출_힌트": ["노쇼", "예약 부도"]})]},
    {"formula_id": "F_PRICE", "vars": [
        _var("대체재가격", "미용실 예약 관리 서비스", "이용 요금", "PRICE", "수익원")]},
]}


def gate_of(raw):
    slots, formulas, _ = wire(raw, VOCAB)
    # kosis_key=None → stat_code 대조는 not_configured 로 남고 네트워크를 타지 않는다
    rep = G.run_gate(raw, slots, formulas, VOCAB, ADAPTERS, HYP, None,
                     SLOTCHECK, AS_OF, CORPCODE, concept=CONCEPT)
    return rep, slots, formulas


print("\n[1] 정상 초안은 통과한다")
rep, slots, formulas = gate_of(GOOD)
check("게이트 통과", rep["passed"], json.dumps(rep["요약"], ensure_ascii=False))
check("슬롯 10개 (observable=false 는 슬롯 없음)", len(slots) == 10, str(len(slots)))
check("식 7개", len(formulas) == 7, str(len(formulas)))

print("\n[2] 코드 칸은 코드가 잡는다 — B 조인이 성립한다")
by_f = {f["formula_id"]: {v["var_id"] for v in f["vars"]} for f in formulas}
check("모든 슬롯의 (formula_id, var_id) 가 그 식의 변수",
      all(s["var_id"] in by_f[s["formula_id"]] for s in slots))
check("slot_id 유일", len({s["slot_id"] for s in slots}) == len(slots))
check("accept 기본값 주입", all(s["accept"]["min_score"] == 5 for s in slots))

print("\n[3] 자유 서술 metric 은 막힌다 (통제 어휘)")
bad = copy.deepcopy(GOOD)
bad["formulas"][0]["vars"][0]["metric"] = "미용실 사업체 수"      # 한정어를 metric 에 넣음
rep2, _, _ = gate_of(bad)
check("게이트 실패", not rep2["passed"])
check("사유가 통제 어휘",
      any(c["name"] == "통제 어휘" and not c["passed"] for c in rep2["checks"]))

print("\n[4] 라우팅 — 한정어가 붙으면 kosis 가 아니라 web 으로 샌다")
r_ok, _ = G.route_of({"metric": "사업체 수"}, ADAPTERS)
r_bad, _ = G.route_of({"metric": "미용실 사업체 수"}, ADAPTERS)
check("'사업체 수' → kosis", r_ok == "kosis", r_ok)
check("'미용실 사업체 수' → 여전히 kosis(부분문자열)", r_bad == "kosis", r_bad)
r_web, _ = G.route_of({"metric": "월 구독료"}, ADAPTERS)
check("'월 구독료' → web", r_web == "web", r_web)
r_dart, _ = G.route_of({"metric": "매출액", "corp_name": "○○"}, ADAPTERS)
check("corp_name 있으면 dart", r_dart == "dart", r_dart)

print("\n[5] 유령 식 참조는 막힌다 (기존 카페 스냅샷의 S7~S9 가 이 병이었다)")
ghost = {"slots": [{"slot_id": "S1", "var_id": "V1", "formula_id": "F_없음"}]}
rep3 = G.check_formula_join(ghost["slots"], [{"formula_id": "F_TAM", "vars": []}])
check("실재하지 않는 formula_id 적발", not rep3["passed"])
rep4 = G.check_formula_join([{"slot_id": "S1", "var_id": "V9", "formula_id": "F_TAM"}],
                            [{"formula_id": "F_TAM", "vars": [{"var_id": "V1"}]}])
check("var_id 가 그 식에 없으면 적발", not rep4["passed"])

print("\n[6] 캔버스 커버리지 — 측정 칸 4개는 슬롯 필수, 계획 칸 5개는 슬롯 불필요")
cov = G.check_coverage(slots, VOCAB)
check("통과", cov["passed"], json.dumps(cov["미충족_칸"], ensure_ascii=False))
check("계획 칸 5개가 슬롯_불필요로 기록",
      sum(1 for c in cov["cells"].values() if c["상태"] == "슬롯_불필요") == 5)
check("계획 칸에 원천이 적혀 있다",
      all(c.get("원천") for c in cov["cells"].values() if c["상태"] == "슬롯_불필요"))
check("채널은 충족 (잠정 발급 해소 — 백로그 17 신설)",
      cov["cells"]["채널"]["상태"] == "충족", cov["cells"]["채널"]["상태"])
check("채널 칸에 잠정 꼬리표가 남아 있지 않다", "_잠정" not in cov["cells"]["채널"])
check("채널 칸의 claim_type 은 CHANNEL — 남의 어휘를 빌리지 않는다",
      VOCAB["canvas"]["측정판정"]["cells"]["채널"]["claim_types"] == ["CHANNEL"])
no_price = [s for s in slots if s["_canvas_cell"] != "수익원"]
check("수익원 칸이 비면 실패", not G.check_coverage(no_price, VOCAB)["passed"])
orphan = copy.deepcopy(slots)
orphan[0]["_canvas_cell"] = "핵심 파트너"          # 계획 칸에 슬롯을 붙임 = 고아
check("어느 측정 칸에도 안 붙는 슬롯은 고아로 적발",
      not G.check_coverage(orphan, VOCAB)["passed"])

print("\n[7] 절대 규칙 2 — 등급 칸이 있으면 그 자리에서 탈락")
graded = copy.deepcopy(GOOD)
graded["formulas"][0]["vars"][0]["tier"] = "TIER_1"
check("tier 적발", not G.check_forbidden_fields(graded)["passed"])
check("var_role 은 금지어가 아니다", G.check_forbidden_fields(GOOD)["passed"])

print("\n[8] 절대 규칙 6 — 가설 값이 슬롯·식에 새면 탈락")
leak = copy.deepcopy(slots)
leak[0]["value_range"] = [39000, 39000]           # 가격 가설 숫자
rep5 = G.check_hypothesis_leak(leak, formulas, HYP)
check("가격 가설 누출 적발", not rep5["passed"], json.dumps(rep5["violations"], ensure_ascii=False))
leak2 = copy.deepcopy(slots)
leak2[0]["subject"] = HYP["7_채널"]["제안값"][0]
check("채널 가설 누출 적발", not G.check_hypothesis_leak(leak2, formulas, HYP)["passed"])
check("정상 초안은 누출 없음", G.check_hypothesis_leak(slots, formulas, HYP)["passed"])

print("\n[9] stat_code — 추측 금지, 못 찾으면 빈칸 + 보고")
sc = G.check_stat_code([{"slot_id": "S1", "stat_code": None},
                        {"slot_id": "S2", "stat_code": "101/DT_1234"}], ADAPTERS, None)
states = {r["slot_id"]: r["state"] for r in sc["rows"]}
check("미기재는 정직으로 기록", states["S1"] == "미기재")
check("키 없으면 not_configured (가짜 통과 없음)", states["S2"] == "not_configured")
bad_fmt = G.check_stat_code([{"slot_id": "S1", "stat_code": "엉터리"}], ADAPTERS, None)
check("형식 틀린 코드는 실패", not bad_fmt["passed"])

print("\n[10] 프롬프트에 가설·제약이 들어가지 않는다 (규칙 6, 생성 쪽)")
body = build_prompt(CONCEPT, VOCAB, 2026)
check("가격 숫자 없음", "39000" not in body and "39,000" not in body)
check("침투율 가정 없음", "0.005" not in body)
check("채널 가설 없음", HYP["7_채널"]["제안값"][0] not in body)
check("컨셉 본문은 들어간다", CONCEPT["problem"][:12] in body)
check("업종 분류는 들어간다", "96112" in body)

print("\n[11] var_role↔계량 종류 — 개수 자리에 비율이 들어가면 탈락 (1차 초안의 F_SAM)")
mixed = copy.deepcopy(GOOD)
mixed["formulas"][0]["vars"][0]["metric"] = "종사자 1인 사업체 비율"   # 사업체수 자리에 비율
mixed["formulas"][0]["vars"][0]["unit"] = "%"
_, _, f_mixed = gate_of(mixed)
rk = G.check_role_kind(f_mixed, VOCAB, CAT)
check("적발", not rk["passed"], json.dumps(rk["violations"], ensure_ascii=False)[:120])
check("정상 초안은 통과", G.check_role_kind(formulas, VOCAB, CAT)["passed"])
check("var_role 통제 어휘 밖도 적발",
      not G.check_role_kind([{"formula_id": "F", "vars": [{"var_role": "아무거나"}]}],
                            VOCAB, CAT)["passed"])

print("\n[12] 템플릿 필수 자리 — T2 에 연환산이 빠지면 탈락 (월 매출을 연 매출로 읽는다)")
noann = copy.deepcopy(GOOD)
noann["formulas"][0]["vars"] = [v for v in noann["formulas"][0]["vars"]
                                if v["var_role"] != "연환산"]
_, _, f_noann = gate_of(noann)
tr = G.check_template_roles(f_noann, VOCAB)
check("적발", not tr["passed"], json.dumps(tr["violations"], ensure_ascii=False)[:120])
check("정상 초안은 통과", G.check_template_roles(formulas, VOCAB)["passed"])
check("가정 역할이 assumptions 밖이면 적발",
      not G.check_template_roles(
          [{"formula_id": "F", "template": "T5",
            "vars": [{"var_role": "비교축", "_observable": False}]}], VOCAB)["passed"])

print("\n[13] 가격 계량은 수익원 칸 (1차 초안은 고객 세그먼트에 TAM 으로 달았다)")
misplaced = copy.deepcopy(slots)
for s in misplaced:
    if s["metric"] == "월 구독료":
        s["_canvas_cell"], s["claim_type"] = "고객 세그먼트", "TAM"
pc = G.check_price_cell(misplaced, VOCAB)
check("적발", not pc["passed"], json.dumps(pc["violations"], ensure_ascii=False)[:120])
check("칸-claim_type 정합만으로는 못 잡는다 (짝은 맞으니까)",
      G.check_cell_fit(misplaced, VOCAB)["passed"])
check("정상 초안은 통과", G.check_price_cell(slots, VOCAB)["passed"])

print("\n[14] period 는 slotcheck 기간 규칙과 대조한다")
pr = G.check_period(slots, SLOTCHECK, AS_OF)
check("정상 초안 통과", pr["passed"], json.dumps(pr["violations"], ensure_ascii=False)[:120])
check("사업체 수는 as_of-2",
      next(r["기대"] for r in pr["rows"] if r["metric"] == "사업체 수") == str(AS_OF - 2))
check("나머지는 as_of-1",
      next(r["기대"] for r in pr["rows"] if r["metric"] == "문제 경험률") == str(AS_OF - 1))
old = copy.deepcopy(slots)
old[0]["period"] = "2023"
check("어긋난 연도 적발", not G.check_period(old, SLOTCHECK, AS_OF)["passed"])

print("\n[15] corp_name 은 DART 공시 법인 사전과 대조한다 — **실재이지 적합이 아니다**")
ce = G.check_corp_exists([{"slot_id": "S1", "corp_name": "왓챠"},
                          {"slot_id": "S2", "corp_name": "스포카"}], CORPCODE)
st = {r["slot_id"]: r["state"] for r in ce["rows"]}
check("엉뚱한 경쟁사(왓챠)도 공시 법인이면 통과한다", st["S1"] == "공시법인")
check("타당한 경쟁사(스포카)는 비상장이라 걸린다", st["S2"] == "사전에 없음")
check("걸린 것이 위반으로 보고된다", not ce["passed"])
check("업종 적합성은 사람 확인으로 남는다",
      all(r["업종_적합성"].startswith("사람 확인") for r in ce["rows"]))
check("corp_name 없으면 검사 대상 아님", G.check_corp_exists(slots, CORPCODE)["passed"])

print("\n[16] 자리표시자 subject 는 막힌다 (재시도가 실명을 포기한 자리)")
for subj in ("A미용 예약 SaaS", "B사", "○○ 예약", "예시 서비스", "우리 서비스 차별점"):
    check(f"'{subj}' 적발",
          not G.check_placeholder([{"slot_id": "S1", "subject": subj}])["passed"])
check("실명 씨앗은 통과",
      G.check_placeholder([{"slot_id": "S1", "subject": "공비서"},
                           {"slot_id": "S2", "subject": "네이버 예약"}])["passed"])
check("정상 초안은 통과", G.check_placeholder(slots)["passed"])

print("\n[17] 식별 지정 계량 — F_DIFF·F_CHANNEL 은 정해진 계량만")
check("F_DIFF 에 엉뚱한 계량이면 적발",
      not G.check_fixed_metrics([{"slot_id": "S1", "formula_id": "F_DIFF",
                                  "metric": "예약 부도율"}], VOCAB)["passed"])
check("F_CHANNEL 은 고객 획득 비용만",
      not G.check_fixed_metrics([{"slot_id": "S1", "formula_id": "F_CHANNEL",
                                  "metric": "도입률"}], VOCAB)["passed"])
check("정상 초안은 통과", G.check_fixed_metrics(slots, VOCAB)["passed"])

print("\n[18] 경쟁 씨앗이 컨셉에 있고 프롬프트로 전달된다")
seeds = (CONCEPT.get("_경쟁_씨앗") or {}).get("seeds") or []
check("씨앗 3개", len(seeds) == 3, str(len(seeds)))
check("씨앗은 진실이 아니라고 적혀 있다", "진실" in CONCEPT["_경쟁_씨앗"]["_설명"])
check("프롬프트에 씨앗 이름이 들어간다", all(s["이름"] in body for s in seeds))

print("\n[19] 규칙끼리 부딪치지 않는다 — 게이트가 통과 불가능해지는 자리")
fixed = {k: v for k, v in VOCAB["metric"]["_식별_계량"].items() if not k.startswith("_")}
price = set(VOCAB["metric"]["_가격_계량"])
overlap = {k: [m for m in v if m in price] for k, v in fixed.items()}
check("가격 계량은 식별 지정 계량에 없다 (있으면 칸 규칙과 정면 충돌)",
      not any(overlap.values()), json.dumps(overlap, ensure_ascii=False))
check("가격 계량은 수익원 칸 claim_type 안에 든다",
      "PRICE" in VOCAB["canvas"]["측정판정"]["cells"]["수익원"]["claim_types"])

print("\n[20] var_role↔계량 1:1 · value_range 폭 · 역방향 corp · DART 검증 슬롯")
seg = copy.deepcopy(GOOD)
seg["formulas"][0]["vars"][2] = _var("침투율", "두발 미용업", "종사자 1인 사업체 비율",
                                     "TAM", "고객 세그먼트")
_, _, f_seg = gate_of(seg)
check("침투율 자리에 세그먼트 비율이 오면 적발 (종류는 맞지만 뜻이 다르다)",
      not G.check_role_kind(f_seg, VOCAB, CAT)["passed"])
check("T2 는 세그먼트비중 자리를 요구한다",
      "세그먼트비중" in VOCAB["template"]["required_roles"]["T2"])

zero = copy.deepcopy(slots)
zero[0]["value_range"] = [0, 0]
check("[0,0] 적발 (모든 값을 격리한다)", not G.check_value_range(zero, VOCAB)["passed"])
check("정상 초안은 통과", G.check_value_range(slots, VOCAB)["passed"])

rev = [{"slot_id": "S1", "metric": "월 구독료", "corp_name": "NAVER"}]
check("web 계량 + corp_name 적발", not G.check_reverse_corp(rev, VOCAB)["passed"])
check("dart 계량 + corp_name 은 통과",
      G.check_reverse_corp([{"slot_id": "S1", "metric": "매출액", "corp_name": "NAVER"}],
                           VOCAB)["passed"])

probe = G.check_dart_probe(slots, VOCAB, CONCEPT)   # CONCEPT 은 씨앗 3개 보유
check("DART 경로 슬롯이 있다", probe["passed"], json.dumps(probe["violations"], ensure_ascii=False))
check("경계 표시가 코드로 붙는다",
      all("시장 매출 아님" in s.get("_경계", "") for s in slots if s["metric"] == "매출액"))
check("DART 슬롯이 없으면 적발",
      not G.check_dart_probe([s for s in slots if s["metric"] != "매출액"],
                             VOCAB, CONCEPT)["passed"])

# 백로그 39 수리 — 요구는 **조건부**다. 씨앗이 없으면 정당한 corp_name 이 존재할 수 없으므로
# 요구 자체를 끈다(그러지 않으면 모델이 이름을 지어낸다 — 판 ⑤ 3/3 실측).
_씨앗없음 = {"name": "x", "problem": "x", "target": "x", "solution": "x", "_경쟁_씨앗": {"seeds": []}}
check("씨앗이 없으면 DART 슬롯 요구가 꺼진다 (백로그 39 수리)",
      G.check_dart_probe([s for s in slots if s["metric"] != "매출액"],
                         VOCAB, _씨앗없음)["passed"])
check("끄되 **사유를 값으로 남긴다** — 조용히 통과하지 않는다",
      "씨앗 미제공" in str(G.check_dart_probe(
          [s for s in slots if s["metric"] != "매출액"], VOCAB, _씨앗없음).get("note")))
check("씨앗이 있으면 요구가 그대로다 — 기존 컨셉 회귀 0",
      not G.check_dart_probe([s for s in slots if s["metric"] != "매출액"],
                             VOCAB, CONCEPT)["passed"])

print("\n[21] 재시도 상한이 규칙에 있다")
check("max_attempts = 3", VOCAB["재시도"]["max_attempts"] == 3)

# ── P2 배선 공사 (판 ⑥-0, 백로그 40) ─────────────────────────────
# 업종 낱말이 통제 어휘에 **하나도** 없어야 한다. 판 ⑤ 실측: 미용실 계량이 strict enum 에
# 박혀 있어 필라테스 PAIN 슬롯이 3/3 「노쇼 피해 경험률」로 채워졌다 — 다른 업종은
# 자기 문제를 **표현조차 못 했다.**
print("\n[22] 업종 상수 하드코딩 0 (P2)")
_금지 = ["노쇼", "예약 부도", "미용", "두발", "필라테스", "요가", "체력 단련", "반려동물", "네일"]



def _산_어휘(o):
    """`_` 로 시작하는 키는 **설명**이다 — 「왜 바꿨나」는 남아야 하므로 검사에서 뺀다.
    검사 대상은 LLM 에게 내려가는 **살아 있는 값**뿐이다(enum·목록·짝표)."""
    if isinstance(o, dict):
        return {k: _산_어휘(v) for k, v in o.items() if not k.startswith("_")}
    if isinstance(o, list):
        return [_산_어휘(x) for x in o]
    return o


_어휘 = json.dumps(_산_어휘(VOCAB), ensure_ascii=False)
_박힘 = [w for w in _금지 if w in _어휘]
check(f"vocab 통제 어휘에 업종 낱말 0 (발견: {_박힘})", not _박힘)
check("폐기 계량이 catalog 에서 빠졌다",
      "예약 부도율" not in CAT and "노쇼 피해 경험률" not in CAT)
check("폐기 사유가 값으로 남아 있다 — 옛 원장을 읽을 때 본다",
      set(VOCAB["_폐기_계량"]) >= {"예약 부도율", "노쇼 피해 경험률"})
check("업종 중립 PAIN 계량이 있다", CAT.get("문제 경험률", {}).get("kind") == "비율")
check("문제근거 자리가 업종 중립 계량만 쓴다",
      VOCAB["var_role"]["catalog"]["문제근거"]["metrics"] == ["문제 경험률"])

# 코드 **분기**에 업종 계량이 박혀 있으면 규칙에서 뺀 의미가 없다.
# 주석·독스트링의 「왜」 설명은 남아야 하므로 실행줄만 본다.
_코드 = "".join(io.open(os.path.join(ROOT, f), encoding="utf-8").read()
              for f in ("harness/gate.py", "harness/slot_harness.py", "tools/slot_dryrun.py"))
_실행줄 = [ln for ln in _코드.splitlines()
         if not ln.lstrip().startswith("#") and ("==" in ln or " in " in ln)]
_코드박힘 = [w for w in ("노쇼 피해 경험률", "예약 부도율")
          if any(f'"{w}"' in ln or f"'{w}'" in ln for ln in _실행줄)]
check(f"코드 분기에 업종 계량 0 (발견: {_코드박힘})", not _코드박힘)

print("\n[23] 추출 힌트 — **컨셉 유래**여야 한다 (P2)")
_pain = [s_ for s_ in slots if s_["claim_type"] == "PAIN"]
check("PAIN 슬롯이 _추출_힌트 를 싣고 있다",
      bool(_pain) and all(len(s_.get("_추출_힌트") or []) >= 2 for s_ in _pain))
check("힌트가 없으면 막는다",
      not G.check_extract_hints(
          [{"slot_id": "S1", "claim_type": "PAIN", "_추출_힌트": []}], VOCAB, CONCEPT)["passed"])
check("컨셉에 없는 말뿐이면 막는다 — 상수를 LLM 기억으로 옮긴 것뿐이다",
      not G.check_extract_hints(
          [{"slot_id": "S1", "claim_type": "PAIN", "_추출_힌트": ["반품률", "재고 회전"]}],
          VOCAB, CONCEPT)["passed"])
check("컨셉 유래 1개 + 동의어면 통과 — 동의어까지 죽이지 않는다",
      G.check_extract_hints(
          [{"slot_id": "S1", "claim_type": "PAIN", "_추출_힌트": ["노쇼", "예약 부도율"]}],
          VOCAB, CONCEPT)["passed"])
check("PAIN 이 아닌 축은 힌트를 요구하지 않는다 — 채울 수 없는 칸을 강제하지 않는다",
      G.check_extract_hints(
          [{"slot_id": "S9", "claim_type": "TAM", "_추출_힌트": []}], VOCAB, CONCEPT)["passed"])
check("컨셉을 못 받으면 통과시키지 않는다 (fail-closed)",
      not G.check_extract_hints(
          [{"slot_id": "S1", "claim_type": "PAIN", "_추출_힌트": ["노쇼", "예약 부도"]}],
          VOCAB, None)["passed"])

# ── 고객 단위 정합 + proxy 선언 (판 ⑥-1·⑥-2) ────────────────────
print("\n[24] 고객 단위 정합 — 조용한 오염은 막고 «말한» 대리 관측은 통과시킨다")
SU = json.load(io.open(os.path.join(ROOT, "rules", "series_unit.v1.json"), encoding="utf-8"))
_B = {"name": "가계부 앱", "problem": "지출을 못 본다", "target": "직장인 개인",
      "solution": "자동 분류", "_계열": {"계열": "B"}}
_A = {"name": "미용실 SaaS", "problem": "노쇼", "target": "1인 미용실 원장",
      "solution": "예치금", "_계열": {"계열": "A"}}


def _tam(metric, decl=None):
    return [{"slot_id": "S1", "claim_type": "TAM", "metric": metric, "subject": "x",
             "_proxy_선언": decl or {"대상": "", "사유": ""}}]


check("계열 B 가 사업체 수를 세면 **선언 없이는 탈락** (판 ⑥ 실측 오염)",
      not G.check_unit_subject(_tam("사업체 수"), SU, _B)["passed"])
check("계열 A 가 사업체 수를 세면 통과 — 고객이 실제로 사업체다",
      G.check_unit_subject(_tam("사업체 수"), SU, _A)["passed"])
check("계열 B 가 인구를 세면 통과",
      G.check_unit_subject(_tam("인구"), SU, _B)["passed"])
_decl = {"대상": "공급자 사업체 수", "사유": "개인 이용자 통계가 없어 공급 측으로 대신 잰다"}
_r = G.check_unit_subject(_tam("사업체 수", _decl), SU, _B)
# 판 ㉔ 사양 변경 — **선언해도 고객 단위는 못 바꾼다.** 옛 사양은 「선언하면 통과」였고
# 그것이 계열 B 초안에 「사업체 수 · 개인 금융 서비스」를 통과시켰다(실측). proxy 는
# **같은 고객 단위 안의 인접 구간**용이지 계열을 갈아치우는 열쇠가 아니다.
check("계열 B 는 **선언해도** 사업체를 셀 수 없다 (고객 단위 대체 금지, 판 ㉔)",
      not _r["passed"])
check("  그 사유가 「인접 구간」이 아니라 「고객 단위」를 짚는다",
      "고객 단위" in _r["violations"][0]["why"])
# ⚠ **사다리 2·3단은 여전히 열려 있다** — 보장의 자리를 옮겨 못박는다.
#   사다리는 「1인 → 1~4명」처럼 **같은 class 안에서** 구간을 넓히는 것이라
#   애초에 G22 의 위반이 아니다. 막힌 것은 class 를 **건너뛰는** 선언뿐이다.
check("사다리 2단(같은 고객 단위 안 인접 구간)은 선언 없이도 통과 — 금지되지 않았다",
      G.check_unit_subject(_tam("종사자 수"), SU, _A)["passed"])
check("계열 D 는 선언하면 여전히 통과 — 허용 목록이 비어 있어 잠금 대상이 아니다",
      G.check_unit_subject(_tam("사업체 수", _decl), SU,
                           {**_A, "_계열": {"계열": "D"}})["passed"])
check("선언한 값에 **경계 문장이 코드로 붙는다**",
      "대리 관측(proxy)" in (_r["rows"][0].get("경계") or ""))
check("한 낱말 사유는 선언이 아니다",
      not G.check_unit_subject(_tam("사업체 수", {"대상": "x", "사유": "proxy"}),
                               SU, _B)["passed"])
check("대상 없이 사유만은 선언이 아니다",
      not G.check_unit_subject(
          _tam("사업체 수", {"대상": "", "사유": "개인 통계가 없어 공급 측으로 잰다"}),
          SU, _B)["passed"])
check("비율·단가 계량은 고객 단위와 무관 — 검사하지 않는다",
      G.check_unit_subject(_tam("도입률"), SU, _B)["passed"])
check("TAM·SAM 이 아닌 축은 보지 않는다 (경쟁사는 회사가 맞다)",
      G.check_unit_subject(
          [{"slot_id": "S9", "claim_type": "COMP", "metric": "사업체 수", "subject": "x"}],
          SU, _B)["passed"])
check("계열 D 는 **무엇을 세든 선언이 필요하다** (신시장 = 정의상 proxy)",
      not G.check_unit_subject(_tam("인구"), SU, {"_계열": {"계열": "D"}})["passed"])
check("계열 미표기는 검사를 끄고 **그 사실을 기록한다** (이주 도구가 되지 않게)",
      G.check_unit_subject(_tam("사업체 수"), SU, {"name": "x"}).get("_비활성"))

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
