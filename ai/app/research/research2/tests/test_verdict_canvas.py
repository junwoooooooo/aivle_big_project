# -*- coding: utf-8 -*-
"""판정 층 + 캔버스 매핑기 검증 — **LLM 0회 · 수집 0회.** 기존 원장 `unified-02`(= `unified-01` 재채점) 위에서만 돈다.

    python tests/test_verdict_canvas.py
"""
from __future__ import annotations
import copy, io, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, ROOT)
sys.path.insert(0, os.path.join(ROOT, "service"))
sys.path.insert(0, os.path.join(ROOT, "harness"))

import verdict as V                                                 # noqa: E402
import canvas as C                                                  # noqa: E402

ok, fail = 0, []


def check(name, cond, detail=""):
    global ok
    if cond:
        ok += 1
        print(f"  OK  {name}")
    else:
        fail.append(f"{name} {detail}")
        print(f"  X   {name} {detail}")


# 판 ㉙ — `unified-02` 는 `unified-01` 을 **LLM 0회로 재채점**한 같은 수집이다.
# 기준 v2 의 새 축(`채택`·`등급`)은 판 ㉙ 이후 채점에서만 원장에 실린다 —
# 옛 산출물에는 그 칸이 **아예 없다**. 없는 칸을 「옛 축으로 봐주기」로 메우면 그것이 fail-open 이고,
# 「거부했다」와 「안 봤다」를 같게 만든 백로그 30 과 같은 병이 된다.
# **수집은 그대로이므로 비교 축은 안 흔들린다**(원장 조건 동일 · LLM 0회).
RUN = "unified-02"
# 두 픽스처가 **서로 다른 것을 재기 때문에** 둘 다 필요하다 (판 ㉙):
#   RUN      = 레거시(수집이 조회일 스탬프 이전) · **CHANNEL 슬롯 0개** → 축_부재 갈래를 재는 유일한 원장
#   RUN_POST = 판 ㉙ 이후 수집 · 조회일 있음 → **채택 축이 실제로 사는** 원장
# 레거시 원장은 새 축에서 채택 0 이다. 그것을 「옛 축으로 봐주기」로 메우면 fail-open 이므로
# **봐주지 않고 픽스처를 갈라 놓는다** — 무엇이 왜 필요한지가 이름에 남는다.
RUN_POST = "beauty-12b"
CONCEPT = "data/concept_beauty-noshow.json"

print("\n[1] 유리벽 — 정적 검사 (엔진 import 0 · 원장 쓰기 0 · LLM 0)")
LAYER = ["service/verdict.py", "service/canvas.py",
         "harness/doc_intake.py", "harness/tavily_intake.py"]
for rel in LAYER:
    src = io.open(os.path.join(ROOT, rel), encoding="utf-8").read()
    body = "\n".join(l for l in src.splitlines()
                     if not l.strip().startswith("#"))          # 주석의 파일 언급은 제외
    eng = re.findall(r"^\s*(?:from|import)\s+(a_design|a_desk|b_estimate|c_chain|"
                     r"blocks|adapters|web|kosis|dart|prompts|runlog|run)\b",
                     body, re.M)
    check(f"{rel} 엔진 import 0", not eng, str(eng))
    llm = re.findall(r"openai|OpenAI|responses\.create|meter\.create", body)
    # 적재기는 검색 API 를 부르지만 **LLM 은 부르지 않는다**
    check(f"{rel} LLM 호출 0", not llm, str(llm))

for rel in ("service/verdict.py", "service/canvas.py"):
    src = io.open(os.path.join(ROOT, rel), encoding="utf-8").read()
    writes = re.findall(r"open\([^)]*['\"]w['\"]", src)
    check(f"{rel} 파일 쓰기 0 (원장 불가침)", not writes, str(writes))

print("\n[2] 판정 층 — 도장은 4개뿐이고 원장에서만 파생된다")
v = V.build(RUN, CONCEPT)
check("가설 4개 전부 판정", len(v["판정"]) == 4, str(list(v["판정"])))
check("도장 어휘 밖 없음",
      all(x["도장"] in V.STAMPS for x in v["판정"].values()),
      str([x["도장"] for x in v["판정"].values()]))
check("모든 판정에 사유", all(x.get("why") for x in v["판정"].values()))

print("\n[3] 축_부재 · 판정_불가 · 미검증을 섞지 않는다")
ch = v["판정"]["7_채널"]
check("채널은 축_부재 (이 원장에 CHANNEL 슬롯 0개)", ch["도장"] == "축_부재", ch["도장"])
check("축_부재 사유가 두 문장 — 「성립한다는 뜻이 아니다」 유지",
      "뜻이 아니다" in ch["why"])
check("축_부재 근거가 «슬롯 0개» 다 («확인됨 0건» 이 아니다)",
      "슬롯 0개" in ch["why"] and ch["채널_슬롯"] == [], ch["why"])

# ── 백로그 17 신설의 핵심 분기. **어느 실원장에도 CHANNEL 슬롯이 아직 없으므로**
#    합성 원장으로 잰다 — 안 재면 이 판이 고친 것이 통째로 미검증으로 남는다.
print("\n[3b] CHANNEL 슬롯이 있는데 확인됨 0건 → 축_부재가 아니라 미검증")
_led = V.bm_scorer.load_ledger(RUN)
_led_ch = dict(_led, slots=_led["slots"] + [{"slot_id": "S_CH", "claim_type": "CHANNEL",
                                             "metric": "고객 획득 비용", "unit": "원"}])
_ch2 = V.judge_channel(_led_ch, {}, {})
check("도장이 미검증 (재려다 못 채웠다)", _ch2["도장"] == "미검증", _ch2["도장"])
check("  슬롯을 지목한다", _ch2["채널_슬롯"] == ["S_CH"], str(_ch2["채널_슬롯"]))
check("  «재지 않은 것이 아니다» 를 명시한다",
      "재지 않은 것이 아니다" in _ch2["why"], _ch2["why"])
check("  **뒷문장은 이 갈래에도 붙는다** (경계 표시 불가침)",
      "채널 없이 BM 이 성립한다는 뜻이 아니다" in _ch2["why"], _ch2["why"])
check("  도장 어휘 4개 안", _ch2["도장"] in V.STAMPS)
# 성적표도 같은 원장에서 같은 방향으로 움직여야 한다 — 두 문서가 어긋나면 그게 결함이다
import bm_scorer as _S                                             # noqa: E402
_axis = [a for a in json.load(io.open(os.path.join(ROOT, "rules", "bm_gate.v1.json"),
                                      encoding="utf-8"))["axes"] if a["id"] == "channel"][0]
_led_ch2 = dict(_led_ch, coverage={**_led["coverage"],
                                   "S_CH": {"slot_id": "S_CH", "status": "공백"}})
check("성적표도 같은 원장에서 미충족 — 판정 층과 어긋나지 않는다",
      _S.score_axis(_axis, _led_ch2, {})["state"] == "미충족",
      _S.score_axis(_axis, _led_ch2, {})["state"])
pr = v["판정"]["6_수익_가격"]
check("가격은 판정_불가 (밴드 미형성)", pr["도장"] == "판정_불가", pr["도장"])
check("판정_불가 사유가 엔진 R7 문장 그대로", "밴드" in pr["why"])

print("\n[4] 단위 필드 조인 — fact 는 `unit_norm` 이다 (`unit` 아님)")
import bm_scorer                                                    # noqa: E402
# **채택 축이 사는 원장**으로 잰다 — 레거시는 조회일이 없어 채택 0 이고, 그러면
# 이 검사가 재려는 «조인이 되는가» 대신 «채택이 되는가» 를 재게 된다(다른 물음).
led = bm_scorer.load_ledger(RUN_POST)
rows = V._confirmed(led, {"TAM", "SAM"})
check("확인됨 행이 잡힌다", len(rows) >= 2, str(len(rows)))
check("단위가 None 이 아니다 (조인 성공)", all(r["unit"] for r in rows),
      str([(r["slot_id"], r["unit"]) for r in rows]))
check("단위 출처를 같이 남긴다", all(r["unit_src"] for r in rows))

print("\n[5] SOM — 계산 과정과 가정을 같이 나른다")
v_post = V.build(RUN_POST, CONCEPT)
som = v_post["판정"]["9_SOM_초기점유"]      # SOM 은 채택된 사실에서 파생 → 사후 원장
check("추정 있음", som.get("추정") is not None)
check("식이 있다", "×" in som["추정"]["식"])
check("가정 목록이 있다", len(som["추정"]["가정"]) >= 2)
check("지어낸 값임을 밝힌다",
      any("관측 근거가 없는" in a for a in som["추정"]["가정"]))
check("엔진 SOM 꼬리표를 같이 나른다", "badge" in som)

print("\n[6] 차별점 — 축별 도장, 노출 꼬리표 보존, 새 축 제안 없음")
d = v["판정"]["8_차별점"]
check("축 4개", len(d["축"]) == 4, str(len(d["축"])))
check("축마다 도장", all(a["도장"] in V.STAMPS for a in d["축"]))
check("축 1 에 노출 꼬리표", "노출" in json.dumps(d["축"][0], ensure_ascii=False))
# 「새 축을 제안하지 않는다」는 낱말 검사로는 못 본다 — 컨셉 서술에 '재제안' 같은
# 제품 문구가 들어 있어 오탐이 난다. 실제로 확인할 것은 **축 집합이 늘지 않았는가**다.
_hyp = json.load(io.open(os.path.join(ROOT, CONCEPT), encoding="utf-8"))["_hypotheses_v2"]
_want = [a["축"] for a in _hyp["8_차별점"]["비교축"]]
check("판정 층이 축을 늘리지 않았다 (컨셉의 축 그대로)",
      [a["축"] for a in d["축"]] == _want, str([a["축"] for a in d["축"]]))
check("판정 결과에 '대안 제안' 류 필드가 없다",
      not [k for a in d["축"] for k in a if "제안" in k or "대안" in k])

print("\n[7] 매핑기 — 9칸 · 원천 · 상태 · §7 보존")
doc = C.build(RUN, CONCEPT)
rep = C.audit(doc)
for c in rep["checks"]:
    check(c["name"], c["passed"], json.dumps(c.get("detail"), ensure_ascii=False))
check("칸 9개", len(doc["칸"]) == 9, str(len(doc["칸"])))
check("계획 칸 5개", sum(1 for c in doc["칸"].values()
                      if c["상태"] in ("계획", "공백")) == 5)
check("§7 키를 하나도 안 뺐다",
      set(doc["못_찾은_것"]) == set(led["report"]["not_found"]))

print("\n[8] 매핑기는 값을 만들지 않는다 — 원천 없는 숫자가 없다")
for name, c in doc["칸"].items():
    check(f"{name} 에 원천", bool(c.get("원천")))
check("상태 어휘가 엔진·판정 층 것뿐",
      all(c["상태"] in ("측정", "측정 + 추정", "측정 + 판정", "계획", "공백") + V.STAMPS
          for c in doc["칸"].values()),
      str({k: c["상태"] for k, c in doc["칸"].items()}))

print("\n[9] 판단문 금지 — 넣으면 검사가 잡는다")
bad = copy.deepcopy(doc)
bad["칸"]["채널"]["내용"] = {"평가": "달성 가능"}
check("주입한 판단문 적발", not C.audit(bad)["passed"])

print("\n[10] 적재기 — 코퍼스 모양이 엔진과 같다")
import doc_intake                                                   # noqa: E402
p = doc_intake.document_payload("S16", "t1",
                                {"url": "https://x.kr/a", "text": "본문 12건",
                                 "http_status": "ok", "http_code": 200,
                                 "content_status": "usable"}, "user_doc")
for k in ("slot_id", "trace_id", "url", "text", "content_status", "text_len",
          "digit_count", "channel", "http_code"):
    check(f"payload.{k}", k in p)
check("빈 본문은 usable 로 올리지 않는다",
      doc_intake.fetch.__doc__ and "content_status" in doc_intake.fetch.__doc__)
check("채널 태그가 사양대로", p["channel"] == "user_doc")

intake = os.path.join(ROOT, "runs", "userdocs-pain", "intake_report.json")
if os.path.exists(intake):
    r = json.load(io.open(intake, encoding="utf-8"))
    check("PAIN 적재분에 경계 표시", "미용실 직접 통계 아님" in (r.get("_경계") or ""))
    check("적재 문서 전부 usable",
          all(d["content_status"] == "usable" for d in r["docs"]),
          str([d["content_status"] for d in r["docs"]]))

print("\n[11] Tavily 적재기 — 개방 후에도 거름 사유는 둘뿐 (판 ㉙ S4)")
import tavily_intake as T                                           # noqa: E402
# 핀은 **단일 원천**에서 읽는다 — 여기 파일명을 리터럴로 적었던 것이 v5/v8 분열의 원인이었다.
_pins = json.load(io.open(os.path.join(ROOT, "rules", "rule_pins.json"), encoding="utf-8"))
wl = json.load(io.open(os.path.join(ROOT, "rules", _pins["pins"]["whitelist"]),
                       encoding="utf-8"))
fake = {"results": [
    {"url": "https://www.sedaily.com/x", "raw_content": "본문 있음 12%"},
    {"url": "https://www.sedaily.com/y", "raw_content": ""},          # 요약본만
    {"url": "https://spam.example.com/z", "raw_content": "본문"},      # 미등재 → 이제 적재
    {"url": "https://v.daum.net/v/abc", "raw_content": "본문"},        # 등재 **거부** → 여전히 거름
]}
keep, dropped = T.to_docs("S15", fake, wl, "t")
# ⚠ **이 뒤집힘이 개방의 회귀 증명이다.** 예전에는 미등재가 하드 드롭이라 원장에 흔적조차
#   남지 않았고, 그래서 「미확보」가 자료 부재인지 우리가 안 열어서인지 구분되지 않았다.
check("미등재도 적재한다 — 등재 1 + 미등재 1", len(keep) == 2, str(len(keep)))
check("미등재는 등급 힌트를 남긴다",
      any(d["note"] == "kind=default:unlisted" for d in keep))
check("요약본은 거른다 — 인용 대조 불가는 완화 대상이 아니다",
      any("raw_content 없음" in d["why"] for d in dropped))
check("등재 **거부** 도메인은 여전히 거른다 — 「안 봤다」와 「보고 거부했다」는 다르다",
      sum(1 for d in dropped if "등재 거부" in d["why"]) == 1)
check("거른 것을 버리지 않고 사유와 함께 남긴다", len(dropped) == 2)
check("채널 태그 tavily",
      doc_intake.document_payload("S15", "t", keep[0], "tavily")["channel"] == "tavily")

print(f"\n===== {ok} 통과 / {len(fail)} 실패")
for f in fail:
    print(" 실패:", f)
sys.exit(1 if fail else 0)
